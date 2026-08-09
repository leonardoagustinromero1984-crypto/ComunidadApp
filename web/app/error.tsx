"use client";

import { useEffect } from "react";

import { Button } from "@/components/ui/Button";

export default function Error({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4 bg-brand-cream px-4 text-center">
      <h1 className="text-2xl font-bold text-brand-text">Algo salió mal</h1>
      <p className="max-w-md text-brand-text/80">
        Ocurrió un error inesperado. Podés intentar nuevamente.
      </p>
      <Button onClick={reset}>Reintentar</Button>
    </div>
  );
}
