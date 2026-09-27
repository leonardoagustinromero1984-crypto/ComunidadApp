/**
 * LeoVer — Edge Function: admin-staff
 *
 * Creates technical administrative Auth users and resets passwords.
 * Service role SOLO aquí. Android never receives this secret.
 *
 * Actions (JSON body.action):
 *   create — display_name, username, role, active
 *   reset_password — user_id
 *
 * Never logs passwords, JWT, emails, or secrets. Returns temporary_password once.
 */

import { createClient } from "https://esm.sh/@supabase/supabase-js@2.49.1";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const ASSIGNABLE = new Set(["ADMIN", "MODERATOR", "SUPPORT"]);
const USERNAME_RE = /^[a-z0-9._-]{3,64}$/;
/** Same technical mailbox domain as the existing SUPERADMIN root identity. */
const TECHNICAL_EMAIL_DOMAIN = "users.noreply.leover.app";

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
      diag(correlationId, "auth_header", { action: null, auth_header_present: false, http: 401, error: "unauthorized" });
      return json({ error: "unauthorized" }, 401, correlationId);
    }
    const jwt = authHeader.slice(7).trim();

    const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
    const anonKey = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
    if (!supabaseUrl || !anonKey || !serviceKey) {
      console.error("admin-staff misconfigured");
      return json({ error: "configuration_error" }, 500, correlationId);
    }

    const userClient = createClient(supabaseUrl, anonKey, {
      auth: { persistSession: false, autoRefreshToken: false, detectSessionInUrl: false },
      global: { headers: { Authorization: `Bearer ${jwt}` } },
    });
    const { data: userData, error: userError } = await userClient.auth.getUser(jwt);
    const actorResolved = Boolean(!userError && userData?.user?.id);
    if (!actorResolved) {
      diag(correlationId, "get_user", { action: null, auth_header_present: true, actor_resolved: false, http: 401, error: "unauthorized" });
      return json({ error: "unauthorized" }, 401, correlationId);
    }

    const { data: adminAuthState, error: authStateError } = await userClient.rpc(
      "get_admin_auth_state",
    );
    const adminIdentity = isActiveAdminIdentity(adminAuthState);
    if (authStateError || !adminIdentity) {
      diag(correlationId, "get_admin_auth_state", {
        action: null,
        auth_header_present: true,
        actor_resolved: true,
        admin_identity: false,
        http: 403,
        error: "forbidden",
      });
      return json({ error: "forbidden" }, 403, correlationId);
    }
    if (requiresMfa(adminAuthState)) {
      diag(correlationId, "aal2", {
        action: null,
        auth_header_present: true,
        actor_resolved: true,
        admin_identity: true,
        http: 403,
        error: "MFA_REQUIRED",
      });
      return json({ error: "MFA_REQUIRED" }, 403, correlationId);
    }
    const { data: adminSession, error: sessionError } = await userClient.rpc(
      "get_admin_session",
    );
    if (sessionError || !isActiveAdminIdentity(adminSession)) {
      const mfa = isMfaError(sessionError?.message);
      diag(correlationId, "get_admin_session", {
        action: null,
        auth_header_present: true,
        actor_resolved: true,
        admin_identity: true,
        http: 403,
        error: mfa ? "MFA_REQUIRED" : "forbidden",
      });
      return json({ error: mfa ? "MFA_REQUIRED" : "forbidden" }, 403, correlationId);
    }

    const { data: allowed, error: permError } = await userClient.rpc("has_permission", {
      permission_code: "staff.manage",
    });
    const staffManage = allowed === true;
    if (permError || !staffManage) {
      diag(correlationId, "has_permission", {
        action: null,
        auth_header_present: true,
        actor_resolved: true,
        admin_identity: true,
        staff_manage: false,
        http: 403,
        error: "forbidden",
      });
      return json({ error: "forbidden" }, 403, correlationId);
    }

    let body: Record<string, unknown> = {};
    try {
      body = await req.json();
    } catch {
      body = {};
    }
    // Actor role/permission from the client is ignored. JWT + server RPCs decide.
    const action = String(body.action ?? "create").trim().toLowerCase();
    const admin = createClient(supabaseUrl, serviceKey, {
      auth: { persistSession: false, autoRefreshToken: false, detectSessionInUrl: false },
    });

    if (action === "create") {
      return await createStaff(userClient, admin, body, {
        correlation_id: correlationId,
        auth_header_present: true,
        actor_resolved: true,
        admin_identity: true,
        staff_manage: true,
      });
    }
    if (action === "reset_password") {
      return await resetPassword(userClient, admin, body, correlationId);
    }
    if (action === "reset_mfa") {
      return await resetMfa(userClient, body, correlationId);
    }
    diag(correlationId, "action", { action, auth_header_present: true, actor_resolved: true, admin_identity: true, staff_manage: true, http: 400, error: "unknown_action" });
    return json({ error: "unknown_action" }, 400, correlationId);
  } catch (e) {
    console.error("admin-staff unexpected", String(e));
    diag(correlationId, "unexpected", { action: null, auth_header_present: authHeaderPresent, http: 500, error: "internal_error" });
    return json({ error: "internal_error" }, 500, correlationId);
  }
});

async function createStaff(
  userClient: ReturnType<typeof createClient>,
  admin: ReturnType<typeof createClient>,
  body: Record<string, unknown>,
  actor: {
    correlation_id: string;
    auth_header_present: boolean;
    actor_resolved: boolean;
    admin_identity: boolean;
    staff_manage: boolean;
  },
) {
  const cid = actor.correlation_id;
  const displayName = String(body.display_name ?? "").trim();
  const username = String(body.username ?? "").trim().toLowerCase();
  const role = String(body.role ?? "").trim().toUpperCase();
  const active = body.active !== false;
  if (!displayName) return json({ error: "name_required" }, 400, cid);
  if (!USERNAME_RE.test(username)) return json({ error: "username_invalid" }, 400, cid);
  if (!ASSIGNABLE.has(role)) return json({ error: "role_forbidden" }, 400, cid);

  const temporaryPassword = generateTemporaryPassword();
  const email = `${username}@${TECHNICAL_EMAIL_DOMAIN}`;

  const { data: created, error: createErr } = await admin.auth.admin.createUser({
    email,
    password: temporaryPassword,
    email_confirm: true,
    user_metadata: { leover_technical_admin: true },
  });
  if (createErr || !created.user?.id) {
    const classified = classifyCreateUser(createErr);
    diag(cid, "create_user", {
      ...actor,
      action: "create",
      target_role: role,
      http: classified.http,
      error: classified.error,
      auth_error_code: classified.auth_error_code,
    });
    return json({ error: classified.error }, classified.http, cid);
  }
  const userId = created.user.id;

  const { error: registerErr } = await userClient.rpc("staff_register_identity", {
    p_user_id: userId,
    p_username: username,
    p_display_name: displayName,
    p_role_code: role,
  });
  if (registerErr) {
    await admin.auth.admin.deleteUser(userId);
    const mapped = mapRpcError(registerErr.message);
    const http = statusFor(registerErr.message);
    diag(cid, "staff_register_identity", {
      ...actor,
      action: "create",
      target_role: role,
      http,
      error: mapped,
      auth_error_code: registerSqlstate(registerErr.message),
    });
    return json({ error: mapped }, http, cid);
  }

  if (!active) {
    const { error: disableErr } = await userClient.rpc("staff_set_disabled", {
      p_user_id: userId,
      p_disabled: true,
    });
    if (disableErr) {
      return json({ error: mapRpcError(disableErr.message) }, statusFor(disableErr.message), cid);
    }
  }

  diag(cid, "create_ok", { ...actor, action: "create", target_role: role, http: 200, error: null });
  return json({
    ok: true,
    success: true,
    user_id: userId,
    username,
    temporary_password: temporaryPassword,
  }, 200, cid);
}

async function resetPassword(
  userClient: ReturnType<typeof createClient>,
  admin: ReturnType<typeof createClient>,
  body: Record<string, unknown>,
  correlationId: string,
) {
  const userId = String(body.user_id ?? "").trim();
  if (!userId) return json({ error: "target_required" }, 400, correlationId);

  const { data: staff, error: getErr } = await userClient.rpc("get_admin_staff", {
    p_user_id: userId,
  });
  if (getErr) {
    return json({ error: mapRpcError(getErr.message) }, statusFor(getErr.message), correlationId);
  }
  const row = staff as { is_root?: boolean } | null;
  if (row?.is_root === true) {
    return json({ error: "root_protected" }, 403, correlationId);
  }

  const temporaryPassword = generateTemporaryPassword();
  const { error: updateErr } = await admin.auth.admin.updateUserById(userId, {
    password: temporaryPassword,
  });
  if (updateErr) {
    console.error("admin-staff reset failed");
    return json({ error: "reset_failed" }, 500, correlationId);
  }

  const { error: flagErr } = await userClient.rpc("staff_on_password_reset", {
    p_user_id: userId,
  });
  if (flagErr) {
    return json({ error: mapRpcError(flagErr.message) }, statusFor(flagErr.message), correlationId);
  }

  return json({
    ok: true,
    success: true,
    temporary_password: temporaryPassword,
  }, 200, correlationId);
}

async function resetMfa(
  userClient: ReturnType<typeof createClient>,
  body: Record<string, unknown>,
  correlationId: string,
) {
  const userId = String(body.user_id ?? "").trim();
  if (!userId) return json({ error: "target_required" }, 400, correlationId);
  const { error } = await userClient.rpc("staff_reset_mfa", { p_user_id: userId });
  if (error) {
    return json({ error: mapRpcError(error.message) }, statusFor(error.message), correlationId);
  }
  return json({ ok: true, success: true }, 200, correlationId);
}

function requiresMfa(value: unknown): boolean {
  let row = value;
  if (typeof row === "string") {
    try {
      row = JSON.parse(row);
    } catch {
      return true;
    }
  }
  row = Array.isArray(row) ? row[0] : row;
  if (!row || typeof row !== "object") return true;
  const rec = row as { mfa_required?: boolean; aal?: string };
  if (rec.mfa_required === true) return true;
  return String(rec.aal ?? "").toLowerCase() !== "aal2";
}

function isMfaError(message: string | undefined): boolean {
  return String(message ?? "").toUpperCase().includes("MFA_REQUIRED");
}

function isActiveAdminIdentity(value: unknown): boolean {
  let row = value;
  if (typeof row === "string") {
    try {
      row = JSON.parse(row);
    } catch {
      return false;
    }
  }
  row = Array.isArray(row) ? row[0] : row;
  if (!row || typeof row !== "object") return false;
  return (row as { is_admin_identity?: boolean }).is_admin_identity === true;
}

function generateTemporaryPassword(): string {
  const alphabet =
    "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";
  const bytes = new Uint8Array(20);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (b) => alphabet[b % alphabet.length]).join("");
}

function classifyCreateUser(err: { status?: number; code?: string; name?: string; message?: string } | null): {
  error: string;
  http: number;
  auth_error_code: string | null;
} {
  const status = err?.status ?? 500;
  const code = String(err?.code ?? "").toLowerCase();
  const name = String(err?.name ?? "");
  const msg = String(err?.message ?? "").toLowerCase();
  const authErrorCode = code || null;
  if (code === "email_exists" || code === "user_already_exists" || msg.includes("already") || msg.includes("registered")) {
    return { error: "STAFF_AUTH_EMAIL_EXISTS", http: 409, auth_error_code: authErrorCode };
  }
  if (code === "email_address_invalid" || ((msg.includes("invalid") || msg.includes("format")) && msg.includes("email"))) {
    return { error: "STAFF_AUTH_EMAIL_INVALID", http: 400, auth_error_code: authErrorCode };
  }
  if (status === 429 || code.includes("rate") || msg.includes("rate")) {
    return { error: "STAFF_AUTH_RATE_LIMIT", http: 429, auth_error_code: authErrorCode };
  }
  if (
    status >= 500 ||
    msg.includes("database") ||
    msg.includes("trigger") ||
    msg.includes("signup_requires") ||
    code === "unexpected_failure"
  ) {
    return { error: "STAFF_AUTH_DB_TRIGGER_FAILED", http: 500, auth_error_code: authErrorCode };
  }
  console.error("admin-staff createUser failed", JSON.stringify({
    auth_error_status: status || null,
    auth_error_code: authErrorCode,
    auth_error_name: name || null,
  }));
  return { error: "STAFF_AUTH_CREATE_FAILED", http: status >= 400 ? status : 500, auth_error_code: authErrorCode };
}

function mapRpcError(message: string): string {
  const m = message.toUpperCase();
  if (m.includes("ROOT_PROTECTED")) return "root_protected";
  if (m.includes("USERNAME_TAKEN")) return "username_taken";
  if (m.includes("USERNAME_INVALID")) return "username_invalid";
  if (m.includes("ROLE_FORBIDDEN")) return "STAFF_ROLE_ASSIGN_FAILED";
  if (m.includes("RATE_LIMITED")) return "rate_limited";
  if (m.includes("MFA_REQUIRED") || m.includes("MFA_ENROLLMENT") || m.includes("MFA_CHALLENGE")) {
    return "MFA_REQUIRED";
  }
  if (m.includes("FORBIDDEN")) return "forbidden";
  if (m.includes("STAFF_NOT_FOUND")) return "staff_not_found";
  if (m.includes("AMBIGUOUS") || m.includes("42702") || m.includes("COLUMN REFERENCE")) {
    return "STAFF_RPC_REGISTER_FAILED";
  }
  return "STAFF_RPC_REGISTER_FAILED";
}

function registerSqlstate(message: string): string | null {
  const m = message.toUpperCase();
  if (m.includes("42702") || m.includes("AMBIGUOUS")) return "42702";
  if (m.includes("23505")) return "23505";
  if (m.includes("23503")) return "23503";
  return null;
}

function statusFor(message: string): number {
  const code = mapRpcError(message);
  if (code === "forbidden" || code === "root_protected" || code === "STAFF_ROLE_ASSIGN_FAILED" || code === "MFA_REQUIRED") {
    return 403;
  }
  if (code === "username_taken") return 409;
  if (code === "rate_limited") return 429;
  if (code === "staff_not_found") return 404;
  return 400;
}

function diag(
  correlationId: string,
  stage: string,
  extra: {
    action: string | null;
    auth_header_present?: boolean;
    actor_resolved?: boolean;
    admin_identity?: boolean;
    staff_manage?: boolean;
    target_role?: string;
    http: number;
    error: string | null;
    auth_error_code?: string | null;
  },
) {
  console.log(JSON.stringify({
    src: "admin-staff",
    correlation_id: correlationId,
    stage,
    action: extra.action,
    auth_header_present: extra.auth_header_present ?? null,
    actor_resolved: extra.actor_resolved ?? null,
    admin_identity: extra.admin_identity ?? null,
    staff_manage: extra.staff_manage ?? null,
    target_role: extra.target_role ?? null,
    auth_error_code: extra.auth_error_code ?? null,
    http_status: extra.http,
    error: extra.error,
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

