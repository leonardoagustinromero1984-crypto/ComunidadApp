/**
 * LeoVer — Edge Function: envía push FCM cuando se inserta una fila en `notifications`.
 *
 * Secrets (Dashboard → Edge Functions → Secrets):
 *   FIREBASE_PROJECT_ID
 *   FIREBASE_CLIENT_EMAIL
 *   FIREBASE_PRIVATE_KEY   (PKCS8, con \n escapados)
 *
 * Webhook (Database → Webhooks):
 *   Table: notifications | Events: INSERT
 *   URL: https://<project>.supabase.co/functions/v1/push
 *   HTTP Header: Authorization = Bearer <service_role_or_anon_key>
 */

import { createClient } from "https://esm.sh/@supabase/supabase-js@2.49.1";
import { create, getNumericDate } from "https://deno.land/x/djwt@v3.0.2/mod.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type",
};

type NotificationRecord = {
  id?: string;
  user_id: string;
  title: string;
  body: string;
  type?: string;
  related_id?: string | null;
  related_type?: string | null;
};

type WebhookPayload = {
  type?: string;
  table?: string;
  record?: NotificationRecord;
  // allow direct invoke for tests
  user_id?: string;
  title?: string;
  body?: string;
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const payload = (await req.json()) as WebhookPayload;
    const record: NotificationRecord | null =
      payload.record ??
      (payload.user_id && payload.title && payload.body
        ? {
            user_id: payload.user_id,
            title: payload.title,
            body: payload.body,
          }
        : null);

    if (!record?.user_id || !record.title || !record.body) {
      return json({ error: "Missing notification record" }, 400);
    }

    const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
    const admin = createClient(supabaseUrl, serviceKey);

    const { data: tokens, error } = await admin
      .from("device_tokens")
      .select("token")
      .eq("user_id", record.user_id);

    if (error) {
      console.error("device_tokens query failed", error);
      return json({ error: error.message }, 500);
    }

    if (!tokens?.length) {
      return json({ sent: 0, reason: "no_tokens" });
    }

    const accessToken = await getFcmAccessToken();
    const projectId = Deno.env.get("FIREBASE_PROJECT_ID")!;
    let sent = 0;
    const failures: string[] = [];

    for (const row of tokens) {
      const ok = await sendFcm(accessToken, projectId, row.token, record);
      if (ok) sent += 1;
      else failures.push(row.token.slice(0, 12));
    }

    return json({ sent, failures: failures.length });
  } catch (e) {
    console.error(e);
    return json({ error: String(e) }, 500);
  }
});

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

async function getFcmAccessToken(): Promise<string> {
  const clientEmail = Deno.env.get("FIREBASE_CLIENT_EMAIL");
  const privateKeyPem = Deno.env.get("FIREBASE_PRIVATE_KEY")?.replace(
    /\\n/g,
    "\n",
  );
  const projectId = Deno.env.get("FIREBASE_PROJECT_ID");
  if (!clientEmail || !privateKeyPem || !projectId) {
    throw new Error(
      "Missing FIREBASE_PROJECT_ID / FIREBASE_CLIENT_EMAIL / FIREBASE_PRIVATE_KEY",
    );
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
    throw new Error(`OAuth token failed: ${JSON.stringify(data)}`);
  }
  return data.access_token as string;
}

async function sendFcm(
  accessToken: string,
  projectId: string,
  deviceToken: string,
  record: NotificationRecord,
): Promise<boolean> {
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
            title: record.title,
            body: record.body,
          },
          data: {
            type: record.type ?? "SYSTEM",
            related_id: record.related_id ?? "",
            related_type: record.related_type ?? "",
            notification_id: record.id ?? "",
          },
          android: {
            priority: "HIGH",
            notification: {
              channel_id: "leover_default",
            },
          },
        },
      }),
    },
  );
  if (!res.ok) {
    const err = await res.text();
    console.error("FCM send failed", err);
    return false;
  }
  return true;
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
