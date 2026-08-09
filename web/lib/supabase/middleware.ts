import { createServerClient, type CookieOptions } from "@supabase/ssr";
import { NextResponse, type NextRequest } from "next/server";

import { getSupabaseAnonKey, getSupabaseUrl, hasSupabaseConfig } from "@/lib/env";

type CookieToSet = {
  name: string;
  value: string;
  options?: CookieOptions;
};

export async function updateSession(request: NextRequest) {
  if (!hasSupabaseConfig()) {
    if (request.nextUrl.pathname.startsWith("/cuenta")) {
      const redirectUrl = request.nextUrl.clone();
      redirectUrl.pathname = "/acceso";
      redirectUrl.searchParams.set("redirect", request.nextUrl.pathname);
      return NextResponse.redirect(redirectUrl);
    }
    return NextResponse.next({ request });
  }

  let supabaseResponse = NextResponse.next({
    request,
  });

  const supabase = createServerClient(getSupabaseUrl(), getSupabaseAnonKey(), {
    cookies: {
      getAll() {
        return request.cookies.getAll();
      },
      setAll(cookiesToSet: CookieToSet[]) {
        cookiesToSet.forEach(({ name, value }) => {
          request.cookies.set(name, value);
        });
        supabaseResponse = NextResponse.next({
          request,
        });
        cookiesToSet.forEach(({ name, value, options }) => {
          supabaseResponse.cookies.set(name, value, options);
        });
      },
    },
  });

  const {
    data: { user },
  } = await supabase.auth.getUser();

  if (!user && request.nextUrl.pathname.startsWith("/cuenta")) {
    const redirectUrl = request.nextUrl.clone();
    redirectUrl.pathname = "/acceso";
    redirectUrl.searchParams.set("redirect", request.nextUrl.pathname);
    return NextResponse.redirect(redirectUrl);
  }

  return supabaseResponse;
}
