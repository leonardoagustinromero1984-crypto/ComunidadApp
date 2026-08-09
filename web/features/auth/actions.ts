"use server";

import { redirect } from "next/navigation";

import { getLoginRedirectPath } from "@/lib/auth/session";
import { createClient } from "@/lib/supabase/server";

export type LoginState = {
  error?: string;
};

export async function loginAction(
  _prevState: LoginState,
  formData: FormData,
): Promise<LoginState> {
  const email = String(formData.get("email") ?? "").trim();
  const password = String(formData.get("password") ?? "");
  const redirectTo = String(formData.get("redirect") ?? "");

  if (!email || !password) {
    return { error: "Completá email y contraseña." };
  }

  const supabase = await createClient();
  const { error } = await supabase.auth.signInWithPassword({ email, password });

  if (error) {
    return { error: "No pudimos iniciar sesión. Verificá tus datos e intentá de nuevo." };
  }

  redirect(getLoginRedirectPath(redirectTo));
}

export async function logoutAction() {
  const supabase = await createClient();
  await supabase.auth.signOut();
  redirect("/");
}
