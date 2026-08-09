import { redirect } from "next/navigation";

import { createClient } from "@/lib/supabase/server";

export function getLoginRedirectPath(redirectTo?: string | null): string {
  if (redirectTo && redirectTo.startsWith("/") && !redirectTo.startsWith("//")) {
    return redirectTo;
  }
  return "/cuenta";
}

export async function requireSession(redirectTo = "/acceso") {
  const supabase = await createClient();
  const {
    data: { user },
  } = await supabase.auth.getUser();

  if (!user) {
    redirect(redirectTo);
  }

  return user;
}
