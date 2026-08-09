import type { ReactNode } from "react";

type PageShellProps = {
  children: ReactNode;
  header?: ReactNode;
  footer?: ReactNode;
  mainClassName?: string;
};

export function PageShell({
  children,
  header,
  footer,
  mainClassName = "",
}: PageShellProps) {
  return (
    <div className="flex min-h-screen flex-col bg-brand-cream text-brand-text">
      {header}
      <main className={`mx-auto flex w-full max-w-5xl flex-1 flex-col px-4 py-8 sm:px-6 lg:px-8 ${mainClassName}`}>
        {children}
      </main>
      {footer}
    </div>
  );
}
