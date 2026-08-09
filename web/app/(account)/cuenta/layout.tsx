import Link from "next/link";

import { PageShell } from "@/components/layout/PageShell";

export default function AccountLayout({
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
            <Link href="/cuenta" className="text-sm font-medium text-brand-text">
              Mi cuenta
            </Link>
          </div>
        </header>
      }
    >
      {children}
    </PageShell>
  );
}
