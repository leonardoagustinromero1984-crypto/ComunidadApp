/**
 * LeoVer — Edge Function: delete-account
 *
 * Elimina la identidad Auth del JWT actual y datos asociados (matriz M01).
 * Service role SOLO aquí. Android nunca recibe este secret.
 *
 * Secrets / env (Dashboard → Edge Functions → Secrets):
 *   SUPABASE_URL
 *   SUPABASE_ANON_KEY
 *   SUPABASE_SERVICE_ROLE_KEY
 *
 * Deploy (manual, no parte de este cierre):
 *   supabase functions deploy delete-account
 */

import { createClient } from "https://esm.sh/@supabase/supabase-js@2.49.1";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type, idempotency-key",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const STORAGE_BUCKET = "leover";

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }
  if (req.method !== "POST") {
    return json({ error: "method_not_allowed" }, 405);
  }

  const correlationId =
    req.headers.get("Idempotency-Key")?.trim() || crypto.randomUUID();

  try {
    const authHeader = req.headers.get("Authorization") ?? "";
    if (!authHeader.toLowerCase().startsWith("bearer ")) {
      return json({ error: "unauthorized", correlation_id: correlationId }, 401);
    }
    const jwt = authHeader.slice(7).trim();
    if (!jwt) {
      return json({ error: "unauthorized", correlation_id: correlationId }, 401);
    }

    const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
    const valAnon = Deno.env.get("SUPABASE_ANON_KEY")!;
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
    if (!supabaseUrl || !valAnon || !serviceKey) {
      console.error("delete-account misconfigured", correlationId);
      return json({ error: "configuration_error", correlation_id: correlationId }, 500);
    }

    // Usuario autenticado: validar JWT con anon; UID solo desde token.
    const userClient = createClient(supabaseUrl, valAnon, {
      global: { headers: { Authorization: `Bearer ${jwt}` } },
    });
    const { data: userData, error: userError } = await userClient.auth.getUser(jwt);
    if (userError || !userData?.user?.id) {
      return json({ error: "unauthorized", correlation_id: correlationId }, 401);
    }
    const uid = userData.user.id;

    // Ignorar / rechazar user_id libre en body (no es autoridad).
    let body: Record<string, unknown> = {};
    try {
      body = await req.json();
    } catch {
      body = {};
    }
    if (body.user_id != null && String(body.user_id) !== uid) {
      return json({ error: "forbidden_user_id", correlation_id: correlationId }, 403);
    }

    const admin = createClient(supabaseUrl, serviceKey);

    // Idempotencia: request previo completed para este usuario reciente.
    const { data: existing } = await admin
      .from("account_deletion_requests")
      .select("id,status")
      .eq("user_id", uid)
      .eq("status", "completed")
      .limit(1);

    if (existing && existing.length > 0) {
      return json({ ok: true, already_deleted: true, correlation_id: correlationId });
    }

    const { data: requestRow, error: insertErr } = await admin
      .from("account_deletion_requests")
      .insert({
        user_id: uid,
        status: "pending",
      })
      .select("id")
      .single();

    if (insertErr) {
      console.error("request insert failed", correlationId, insertErr.message);
      return json({ error: "request_failed", correlation_id: correlationId }, 500);
    }
    const requestId = requestRow.id as string;

    // 1) Storage propietario del usuario (antes de Auth).
    try {
      await deleteStoragePrefix(admin, `users/${uid}`);
    } catch (e) {
      console.error("storage delete failed", correlationId, String(e));
      await admin
        .from("account_deletion_requests")
        .update({ status: "failed", failure_code: "storage" })
        .eq("id", requestId);
      await admin.rpc("m07_client_note_data_access", {
        p_event_key: "m01.account.deletion_failed",
        p_correlation_id: correlationId.replace(/[^A-Za-z0-9_-]/g, "").slice(0, 64) ||
          crypto.randomUUID().replaceAll("-", "").slice(0, 32),
        p_result: "FAILURE",
        p_error_code: "storage",
        p_metadata: { result: "FAILURE", error_code: "storage" },
      }).catch(() => null);
      return json({ error: "storage_failed", correlation_id: correlationId }, 500);
    }

    // 2) device_tokens (cascade también, best-effort).
    await admin.from("device_tokens").delete().eq("user_id", uid);

    // 3) Auth delete → cascade public.users y FKs (matriz M01).
    const { error: deleteAuthErr } = await admin.auth.admin.deleteUser(uid);
    if (deleteAuthErr) {
      console.error("auth delete failed", correlationId, deleteAuthErr.message);
      await admin
        .from("account_deletion_requests")
        .update({ status: "failed", failure_code: "auth_delete" })
        .eq("id", requestId);
      await admin.rpc("m07_client_note_data_access", {
        p_event_key: "m01.account.deletion_failed",
        p_correlation_id: correlationId.replace(/[^A-Za-z0-9_-]/g, "").slice(0, 64) ||
          crypto.randomUUID().replaceAll("-", "").slice(0, 32),
        p_result: "FAILURE",
        p_error_code: "auth_delete",
        p_metadata: { result: "FAILURE", error_code: "auth_delete" },
      }).catch(() => null);
      return json({ error: "auth_delete_failed", correlation_id: correlationId }, 500);
    }

    await admin
      .from("account_deletion_requests")
      .update({
        status: "completed",
        completed_at: new Date().toISOString(),
        user_id: null,
      })
      .eq("id", requestId);

    return json({ ok: true, correlation_id: correlationId, request_id: requestId });
  } catch (e) {
    console.error("delete-account unexpected", correlationId, String(e));
    return json({ error: "internal_error", correlation_id: correlationId }, 500);
  }
});

async function deleteStoragePrefix(
  admin: ReturnType<typeof createClient>,
  prefix: string,
) {
  const { data: files, error } = await admin.storage
    .from(STORAGE_BUCKET)
    .list(prefix, { limit: 1000 });
  if (error) {
    // Bucket vacío / sin folder → no fallar duro si "not found"
    if (String(error.message).toLowerCase().includes("not found")) return;
    throw error;
  }
  if (!files?.length) return;

  const paths: string[] = [];
  for (const entry of files) {
    if (entry.id == null && entry.name) {
      // carpeta: list recursivo superficial (pets)
      const nested = await admin.storage
        .from(STORAGE_BUCKET)
        .list(`${prefix}/${entry.name}`, { limit: 1000 });
      for (const child of nested.data ?? []) {
        if (child.name) paths.push(`${prefix}/${entry.name}/${child.name}`);
      }
    } else if (entry.name) {
      paths.push(`${prefix}/${entry.name}`);
    }
  }
  if (paths.length) {
    const { error: rmErr } = await admin.storage.from(STORAGE_BUCKET).remove(paths);
    if (rmErr) throw rmErr;
  }
}

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}
