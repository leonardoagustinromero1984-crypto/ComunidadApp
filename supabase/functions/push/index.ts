/**
 * LeoVer — Edge Function push (M06 Etapa 4): deliveries gobernados.
 *
 * Secrets (Dashboard → Edge Functions → Secrets):
 *   FIREBASE_PROJECT_ID
 *   FIREBASE_CLIENT_EMAIL
 *   FIREBASE_PRIVATE_KEY   (PKCS8, con \n escapados)
 *   SUPABASE_URL
 *   SUPABASE_SERVICE_ROLE_KEY
 *
 * Invocación:
 *   - Cron / worker con service role: POST {} → claim deliveries pendientes
 *   - Webhook legacy INSERT en notifications: se ignora título/body/user arbitrarios
 *     y solo se reclama la cola M06 (sin dual-send desde device_tokens).
 *
 * No acepta userId/token/title/body arbitrarios desde Android.
 */

import { createClient } from "https://esm.sh/@supabase/supabase-js@2.49.1";
import { create, getNumericDate } from "https://deno.land/x/djwt@v3.0.2/mod.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type",
};

type ClaimedDelivery = {
  delivery_id: string;
  notification_id: string;
  installation_id: string | null;
  attempt_count: number;
  user_id: string;
  category: string;
  priority: string;
  sensitivity: string;
  title: string;
  body: string;
  deep_link_type: string;
  deep_link_resource_type: string | null;
  deep_link_resource_id: string | null;
  organization_id: string | null;
  token_reference: string | null;
  token_fingerprint: string | null;
  expires_at: string | null;
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    if (!authorizeService(req)) {
      return json({ error: "UNAUTHORIZED" }, 401);
    }

    // Ignore arbitrary client payload fields (user_id/title/body/token).
    await req.json().catch(() => ({}));

    const admin = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    const { data: claimed, error } = await admin.rpc("m06_claim_push_deliveries", {
      p_worker_id: "edge-push",
      p_limit: 25,
    });

    if (error) {
      console.error("claim_failed", sanitizeError(error.message));
      return json({ error: "CLAIM_FAILED" }, 500);
    }

    const deliveries = (claimed as ClaimedDelivery[] | null) ?? [];
    if (!deliveries.length) {
      return json({ processed: 0, reason: "empty_queue" });
    }

    const accessToken = await getFcmAccessToken();
    const projectId = Deno.env.get("FIREBASE_PROJECT_ID")!;
    const startedAt = Date.now();
    let delivered = 0;
    let failed = 0;
    let retryable = 0;
    let permanent = 0;
    let invalidTokens = 0;

    for (const item of deliveries) {
      const result = await processDelivery(admin, accessToken, projectId, item);
      if (result === "DELIVERED") delivered += 1;
      else {
        failed += 1;
        if (result === "FAILED_RETRYABLE") retryable += 1;
        if (result === "FAILED_PERMANENT") permanent += 1;
        if (result === "INVALID_TOKEN") invalidTokens += 1;
      }
    }

    const durationMs = Date.now() - startedAt;
    const corr = crypto.randomUUID().replaceAll("-", "").slice(0, 32);

    // M07 Etapa 4: aggregated metrics + health readiness (no tokens/URLs/PII).
    try {
      await admin.rpc("m07_best_effort_audit", {
        p_event_key: "m06.edge.push_invoked",
        p_action: "PUSH_INVOKE",
        p_result: failed > 0 && delivered === 0 ? "FAILURE" : "SUCCESS",
        p_correlation_id: corr,
        p_resource_type: "edge_push",
        p_resource_id: null,
        p_metadata: {
          result: failed > 0 && delivered === 0 ? "FAILURE" : "SUCCESS",
          duration_ms: String(durationMs),
          attempt_count: String(deliveries.length),
        },
      });
      await admin.rpc("m07_record_metric", {
        p_metric_key: "m06.delivery.success_rate",
        p_value_numeric: deliveries.length === 0 ? 1 : delivered / deliveries.length,
        p_unit: "ratio",
        p_dimensions: { module: "M06", channel: "push", result: "AGGREGATE" },
        p_sample_count: deliveries.length,
        p_correlation_id: corr,
        p_source: "EDGE",
      }).catch(() => null);
      await admin.rpc("m07_record_metric", {
        p_metric_key: "m06.delivery.retryable_failure_count",
        p_value_numeric: retryable,
        p_unit: "count",
        p_dimensions: { module: "M06", channel: "push" },
        p_sample_count: 1,
        p_correlation_id: corr,
        p_source: "EDGE",
      }).catch(() => null);
      await admin.rpc("m07_record_health_check", {
        p_check_key: "edge.push.readiness",
        p_status: failed > 0 && delivered === 0 ? "DEGRADED" : "HEALTHY",
        p_severity: "INFO",
        p_latency_ms: durationMs,
        p_details: { reason: "OK", processed: String(deliveries.length) },
        p_correlation_id: corr,
        p_source: "EDGE",
      }).catch(() => null);
    } catch (_) {
      /* never fail push on observability */
    }

    return json({
      processed: deliveries.length,
      delivered,
      failed,
      retryable,
      permanent,
      invalid_tokens: invalidTokens,
      duration_ms: durationMs,
    });
  } catch (e) {
    console.error("push_unhandled", sanitizeError(String(e)));
    return json({ error: "INTERNAL" }, 500);
  }
});

function authorizeService(req: Request): boolean {
  const auth = req.headers.get("Authorization") ?? "";
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
  if (!serviceKey) return false;
  return auth === `Bearer ${serviceKey}`;
}

async function processDelivery(
  admin: ReturnType<typeof createClient>,
  accessToken: string,
  projectId: string,
  item: ClaimedDelivery,
): Promise<string> {
  if (!item.token_reference) {
    await markResult(admin, item.delivery_id, "FAILED_PERMANENT", "MISSING_TOKEN");
    return "FAILED_PERMANENT";
  }
  if (item.expires_at && Date.parse(item.expires_at) <= Date.now()) {
    await markResult(admin, item.delivery_id, "SKIPPED_EXPIRED", "EXPIRED");
    return "SKIPPED_EXPIRED";
  }

  const send = await sendFcm(accessToken, projectId, item.token_reference, item);
  if (send.ok) {
    await markResult(admin, item.delivery_id, "DELIVERED", null, send.providerMessageId);
    return "DELIVERED";
  }

  const permanent = send.failureCode === "INVALID_TOKEN" ||
    send.failureCode === "UNREGISTERED" ||
    send.failureCode === "NOT_FOUND";
  await markResult(
    admin,
    item.delivery_id,
    permanent ? "FAILED_PERMANENT" : "FAILED_RETRYABLE",
    send.failureCode,
  );
  if (send.failureCode === "INVALID_TOKEN" || send.failureCode === "UNREGISTERED") {
    return "INVALID_TOKEN";
  }
  return permanent ? "FAILED_PERMANENT" : "FAILED_RETRYABLE";
}

async function markResult(
  admin: ReturnType<typeof createClient>,
  deliveryId: string,
  status: string,
  failureCode: string | null,
  providerMessageId: string | null = null,
) {
  const { error } = await admin.rpc("m06_mark_delivery_result", {
    p_delivery_id: deliveryId,
    p_status: status,
    p_failure_code: failureCode,
    p_provider_message_id: providerMessageId,
  });
  if (error) {
    console.error("mark_result_failed", sanitizeError(error.message));
  }
}

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

function sanitizeError(raw: string): string {
  return raw
    .replace(/Bearer\s+[A-Za-z0-9._\-]+/gi, "Bearer [redacted]")
    .replace(/-----BEGIN[\s\S]*?-----END[^-]+-----/g, "[redacted-key]")
    .slice(0, 240);
}

async function getFcmAccessToken(): Promise<string> {
  const clientEmail = Deno.env.get("FIREBASE_CLIENT_EMAIL");
  const privateKeyPem = Deno.env.get("FIREBASE_PRIVATE_KEY")?.replace(
    /\\n/g,
    "\n",
  );
  const projectId = Deno.env.get("FIREBASE_PROJECT_ID");
  if (!clientEmail || !privateKeyPem || !projectId) {
    throw new Error("Missing FCM server credentials");
  }

  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToArrayBuffer(privateKeyPem),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );

  const jwt = await create(
    { alg: "RS256", typ: "JWT" },
    {
      iss: clientEmail,
      sub: clientEmail,
      aud: "https://oauth2.googleapis.com/token",
      iat: getNumericDate(0),
      exp: getNumericDate(60 * 60),
      scope: "https://www.googleapis.com/auth/firebase.messaging",
    },
    key,
  );

  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: jwt,
    }),
  });
  const data = await res.json();
  if (!res.ok || !data.access_token) {
    throw new Error("OAuth token failed");
  }
  return data.access_token as string;
}

async function sendFcm(
  accessToken: string,
  projectId: string,
  deviceToken: string,
  item: ClaimedDelivery,
): Promise<{ ok: boolean; failureCode: string | null; providerMessageId: string | null }> {
  const channelId = androidChannelForCategory(item.category);
  const res = await fetch(
    `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        message: {
          token: deviceToken,
          notification: {
            title: item.title,
            body: item.body,
          },
          data: {
            notification_id: item.notification_id,
            category: item.category,
            priority: item.priority,
            sensitivity: item.sensitivity,
            deep_link_type: item.deep_link_type ?? "SAFE_HOME",
            resource_id: item.deep_link_resource_id ?? "",
            organization_id: item.organization_id ?? "",
            delivery_id: item.delivery_id,
          },
          android: {
            priority: item.priority === "URGENT" || item.priority === "HIGH"
              ? "HIGH"
              : "NORMAL",
            notification: {
              channel_id: channelId,
            },
          },
        },
      }),
    },
  );

  if (!res.ok) {
    const errText = await res.text();
    console.error("fcm_send_failed", sanitizeError(errText));
    const upper = errText.toUpperCase();
    if (
      upper.includes("UNREGISTERED") ||
      upper.includes("INVALID") ||
      upper.includes("NOT_FOUND") ||
      upper.includes("REGISTRATION-TOKEN")
    ) {
      return { ok: false, failureCode: "INVALID_TOKEN", providerMessageId: null };
    }
    return { ok: false, failureCode: "TRANSIENT", providerMessageId: null };
  }

  const body = await res.json().catch(() => ({}));
  const name = typeof body?.name === "string" ? body.name.slice(0, 120) : null;
  return { ok: true, failureCode: null, providerMessageId: name };
}

function androidChannelForCategory(category: string): string {
  switch ((category || "").toUpperCase()) {
    case "ACCOUNT":
    case "SECURITY":
      return "leover_security";
    case "ORGANIZATION":
    case "INVITATION":
      return "leover_organizations";
    case "MODERATION":
    case "APPEAL":
    case "VERIFICATION":
    case "SUPPORT":
      return "leover_moderation_support";
    case "PET":
    case "ADOPTION":
    case "FOSTER":
    case "SHELTER":
    case "LOST_FOUND":
      return "leover_pets_adoptions";
    case "SOCIAL":
    case "MESSAGE":
      return "leover_social_messages";
    default:
      return "leover_system";
  }
}

function pemToArrayBuffer(pem: string): ArrayBuffer {
  const b64 = pem
    .replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "")
    .replace(/\s+/g, "");
  const binary = atob(b64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes.buffer;
}
