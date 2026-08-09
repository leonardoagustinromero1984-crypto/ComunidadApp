import Link from "next/link";

import { Card } from "@/components/ui/Card";

export default function BrandPlaceholderPage() {
  return (
    <section className="flex flex-1 flex-col gap-4 py-12">
      <Card>
        <h1 className="text-2xl font-bold">Brand Studio</h1>
        <p className="mt-2 text-brand-text/80">
          Área reservada para Brand Studio M29. Próximamente.
        </p>
        <Link href="/" className="mt-4 inline-block text-sm font-semibold text-brand-orange">
          Volver al inicio
        </Link>
      </Card>
    </section>
  );
}
