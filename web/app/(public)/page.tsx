import Link from "next/link";

import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";

export default function HomePage() {
  return (
    <section className="flex flex-1 flex-col justify-center gap-8 py-12">
      <div className="space-y-4">
        <p className="text-sm font-semibold uppercase tracking-wide text-brand-green">
          LeoVer
        </p>
        <h1 className="max-w-2xl text-4xl font-bold leading-tight text-brand-text sm:text-5xl">
          Conectamos mascotas, personas y comunidad.
        </h1>
        <p className="max-w-xl text-lg text-brand-text/80">
          Fundación web oficial de LeoVer. Una sola plataforma para la web
          pública, organizaciones, profesionales y Brand Studio.
        </p>
      </div>

      <Card className="max-w-md">
        <div className="flex flex-col gap-4">
          <p className="text-sm text-brand-text/80">
            Ingresá con la misma cuenta que usás en la app LeoVer.
          </p>
          <Link href="/acceso">
            <Button className="w-full">Ingresar</Button>
          </Link>
        </div>
      </Card>
    </section>
  );
}
