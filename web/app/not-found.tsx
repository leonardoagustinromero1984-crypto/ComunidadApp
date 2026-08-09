import Link from "next/link";

import { Button } from "@/components/ui/Button";
import { PageShell } from "@/components/layout/PageShell";

export default function NotFound() {
  return (
    <PageShell>
      <section className="flex flex-1 flex-col items-start justify-center gap-4 py-12">
        <h1 className="text-3xl font-bold">Página no encontrada</h1>
        <p className="text-brand-text/80">
          La ruta que buscás no existe o aún no está disponible.
        </p>
        <Link href="/">
          <Button>Volver al inicio</Button>
        </Link>
      </section>
    </PageShell>
  );
}
