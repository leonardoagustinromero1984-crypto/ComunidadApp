import { requireSession } from "@/lib/auth/session";
import { Card } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { logoutAction } from "@/features/auth/actions";

export const dynamic = "force-dynamic";

export default async function CuentaPage() {
  const user = await requireSession();

  return (
    <section className="flex flex-1 flex-col gap-6 py-8">
      <div>
        <h1 className="text-3xl font-bold text-brand-text">LeoVer</h1>
        <p className="mt-2 text-brand-text/80">Sesión iniciada</p>
      </div>

      <Card className="max-w-lg space-y-4">
        <div>
          <p className="text-sm font-medium text-brand-text/70">Email</p>
          <p className="text-brand-text">{user.email ?? "Sin email"}</p>
        </div>
        <div>
          <p className="text-sm font-medium text-brand-text/70">ID de usuario</p>
          <p className="break-all font-mono text-sm text-brand-text">{user.id}</p>
        </div>
        <form action={logoutAction}>
          <Button type="submit" variant="ghost">
            Cerrar sesión
          </Button>
        </form>
      </Card>
    </section>
  );
}
