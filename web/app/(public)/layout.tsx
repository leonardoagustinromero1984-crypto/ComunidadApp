import Link from "next/link";

import { PageShell } from "@/components/layout/PageShell";
import { Button } from "@/components/ui/Button";

export default function PublicLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <PageShell
      header={
        <header className="border-b border-black/5 bg-brand-white">
          <div className="mx-auto flex w-full max-w-5xl items-center justify-between px-4 py-4 sm:px-6 lg:px-8">
            <Link href="/" className="text-xl font-bold text-brand-orange">
              LeoVer
            </Link>
            <Link href="/acceso">
              <Button variant="secondary">Ingresar</Button>
            </Link>
          </div>
        </header>
      }
      footer={
        <footer className="border-t border-black/5 bg-brand-white py-6 text-center text-sm text-brand-text/70">
          LeoVer — Conectamos mascotas, personas y comunidad.
        </footer>
      }
    >
      {children}
    </PageShell>
  );
}
