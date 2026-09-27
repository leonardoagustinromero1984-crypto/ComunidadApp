/**
 * LeoVer — Edge Function: media-signed-url
 *
 * Canonical private-media mint. Service role ONLY here.
 * Android never receives this secret and must not call Storage createSignedUrl.
 *
 * Body: { "asset_id": "<uuid>" }
 * Flow: Bearer → caller → canon_authorize_media_signed_url (ACL + signed_url.request)
 *       → service_role createSignedUrl with bounded TTL.
 * Never logs JWT, signed URL, or object bytes.
 */

import { createClient } from "https://esm.sh/@supabase/supabase-js@2.49.1";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const UUID_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const MAX_TTL_SECONDS = 600;

Deno.serve(async (req) => {
  const correlationId = crypto.randomUUID();
  if (req.method === "OPTIONS") {
    return json({ ok: true }, 200, correlationId);
  }
  if (req.method !== "POST") {
    return json({ error: "method_not_allowed" }, 405, correlationId);
  }

  const authHeader = req.headers.get("Authorization") ?? "";
  const authHeaderPresent = authHeader.toLowerCase().startsWith("bearer ") &&
    authHeader.slice(7).trim().length > 0;

  try {
    if (!authHeaderPresent) {
      diag(correlationId, "auth_header", { http: 401, error: "unauthorized" });
      return json({ error: "unauthorized" }, 401, correlationId);
    }
    const jwt = authHeader.slice(7).trim();

    const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
    const anonKey = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
    if (!supabaseUrl || !anonKey || !serviceKey) {
      console.error("media-signed-url misconfigured");
      return json({ error: "configuration_error" }, 500, correlationId);
    }

    const userClient = createClient(supabaseUrl, anonKey, {
      auth: { persistSession: false, autoRefreshToken: false, detectSessionInUrl: false },
      global: { headers: { Authorization: `Bearer ${jwt}` } },
    });
    const { data: userData, error: userError } = await userClient.auth.getUser(jwt);
    if (userError || !userData?.user?.id) {
      diag(correlationId, "get_user", { http: 401, error: "unauthorized" });
      return json({ error: "unauthorized" }, 401, correlationId);
    }

    const body = await readJson(req);
    const assetId = typeof body.asset_id === "string" ? body.asset_id.trim() : "";
    if (!UUID_RE.test(assetId)) {
      diag(correlationId, "asset_id", { http: 400, error: "validation" });
      return json({ error: "validation" }, 400, correlationId);
    }

    const { data: authorized, error: authzError } = await userClient.rpc(
      "canon_authorize_media_signed_url",
      { p_asset_id: assetId },
    );
    if (authzError) {
      const mapped = mapAuthorizeError(authzError.message ?? "");
      diag(correlationId, "authorize", { http: mapped.http, error: mapped.error });
      return json({ error: mapped.error }, mapped.http, correlationId);
    }

    const grant = asGrant(authorized);
    if (!grant) {
      diag(correlationId, "authorize_shape", { http: 403, error: "forbidden" });
      return json({ error: "forbidden" }, 403, correlationId);
    }

    const ttl = boundTtl(grant.ttlSeconds);
    const admin = createClient(supabaseUrl, serviceKey, {
      auth: { persistSession: false, autoRefreshToken: false, detectSessionInUrl: false },
    });
    const { data: signed, error: signError } = await admin.storage
      .from(grant.bucket)
      .createSignedUrl(grant.path, ttl);
    if (signError || !signed?.signedUrl) {
      diag(correlationId, "sign", { http: 500, error: "sign_failed" });
      return json({ error: "sign_failed" }, 500, correlationId);
    }

    diag(correlationId, "ok", { http: 200, error: null, bucket: grant.bucket });
    return json({
      ok: true,
      bucket: grant.bucket,
      path: grant.path,
      expires_in: ttl,
      signed_url: signed.signedUrl,
    }, 200, correlationId);
  } catch (error) {
    const message = error instanceof Error ? error.message : "unknown";
    diag(correlationId, "unhandled", { http: 500, error: message.slice(0, 80) });
    return json({ error: "server_error" }, 500, correlationId);
  }
});

function boundTtl(raw: number | null): number {
  if (!Number.isFinite(raw) || raw === null || raw <= 0) return MAX_TTL_SECONDS;
  return Math.min(Math.floor(raw), MAX_TTL_SECONDS);
}

function asGrant(raw: unknown): { bucket: string; path: string; ttlSeconds: number } | null {
  if (!raw || typeof raw !== "object") return null;
  const row = raw as Record<string, unknown>;
  const bucket = typeof row.bucket === "string" ? row.bucket.trim() : "";
  const path = typeof row.path === "string" ? row.path.trim() : "";
  const ttlSeconds = typeof row.ttl_seconds === "number" ? row.ttl_seconds : MAX_TTL_SECONDS;
  if (!bucket || !path || path.includes("..") || path.startsWith("/")) return null;
  return { bucket, path, ttlSeconds };
}

function mapAuthorizeError(message: string): { http: number; error: string } {
  const upper = message.toUpperCase();
  if (upper.includes("RATE_LIMITED") || upper.includes("SIGNED_URL_THROTTLED")) {
    return { http: 429, error: "RATE_LIMITED" };
  }
  if (upper.includes("FORBIDDEN")) return { http: 403, error: "forbidden" };
  if (upper.includes("NOT_FOUND")) return { http: 404, error: "not_found" };
  if (upper.includes("NOT_AUTHENTICATED")) return { http: 401, error: "unauthorized" };
  if (upper.includes("VALIDATION")) return { http: 400, error: "validation" };
  return { http: 403, error: "forbidden" };
}

async function readJson(req: Request): Promise<Record<string, unknown>> {
  const text = await req.text();
  if (!text.trim()) return {};
  const parsed = JSON.parse(text);
  return parsed && typeof parsed === "object" && !Array.isArray(parsed)
    ? parsed as Record<string, unknown>
    : {};
}

function diag(
  correlationId: string,
  stage: string,
  extra: { http: number; error: string | null; bucket?: string },
) {
  console.log(JSON.stringify({
    src: "media-signed-url",
    correlation_id: correlationId,
    stage,
    http_status: extra.http,
    error: extra.error,
    bucket: extra.bucket ?? null,
  }));
}

function json(body: unknown, status = 200, correlationId?: string) {
  const payload = (body && typeof body === "object" && !Array.isArray(body) && correlationId)
    ? { ...(body as Record<string, unknown>), correlation_id: correlationId }
    : body;
  return new Response(JSON.stringify(payload), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}
