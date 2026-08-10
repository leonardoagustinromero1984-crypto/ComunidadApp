"use client";

import { useCallback, useState } from "react";

import { Button } from "@/components/ui/Button";

type ShareButtonProps = {
  url: string;
  title: string;
  text?: string;
};

export function ShareButton({ url, title, text }: ShareButtonProps) {
  const [copied, setCopied] = useState(false);

  const handleShare = useCallback(async () => {
    if (typeof navigator !== "undefined" && navigator.share) {
      try {
        await navigator.share({ url, title, text });
        return;
      } catch {
        // User cancelled or share failed — fall back to copy.
      }
    }

    try {
      await navigator.clipboard.writeText(url);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 2000);
    } catch {
      setCopied(false);
    }
  }, [text, title, url]);

  return (
    <Button type="button" variant="secondary" onClick={handleShare}>
      {copied ? "Enlace copiado" : "Compartir"}
    </Button>
  );
}
