import { redirect } from "next/navigation";

import { createClient } from "@/lib/supabase/server";

export function getLoginRedirectPath(redirectTo?: string | null): string {
  if (!redirectTo) {
    return "/cuenta";
  }
  const trimmed = redirectTo.trim();
  const lower = trimmed.toLowerCase();
  if (
    !trimmed ||
    lower === "null" ||
    lower === "anull" ||
    lower === "undefined" ||
    !trimmed.startsWith("/") ||
    trimmed.startsWith("//")
  ) {
    return "/cuenta";
  }
  return trimmed;
}

export async function requireSession(redirectTo = "/acceso") {
  const safeRedirect =
    redirectTo.startsWith("/") &&
    !redirectTo.startsWith("//") &&
    redirectTo.toLowerCase() !== "null" &&
    redirectTo.toLowerCase() !== "anull"
      ? redirectTo
      : "/acceso";
  const supabase = await createClient();
  const {
    data: { user },
  } = await supabase.auth.getUser();

  if (!user) {
    redirect(safeRedirect);
  }

  return user;
}
