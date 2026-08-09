import Link from "next/link";

import { LoginForm } from "@/features/auth/LoginForm";

type AccesoPageProps = {
  searchParams: Promise<{ redirect?: string; error?: string }>;
};

export default async function AccesoPage({ searchParams }: AccesoPageProps) {
  const params = await searchParams;

  return (
    <div className="space-y-4">
      <Link href="/" className="text-sm font-semibold text-brand-orange">
        ← Volver al inicio
      </Link>
      {params.error === "callback" ? (
        <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">
          No pudimos completar el acceso. Intentá iniciar sesión nuevamente.
        </p>
      ) : null}
      <LoginForm redirectTo={params.redirect} />
    </div>
  );
}
