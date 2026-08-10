import Link from "next/link";

import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";

type PublicHeroImageProps = {
  src?: string | null;
  alt: string;
};

export function PublicHeroImage({ src, alt }: PublicHeroImageProps) {
  if (src) {
    return (
      <div className="overflow-hidden rounded-2xl border border-black/5 bg-brand-white shadow-sm">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src={src} alt={alt} className="aspect-[4/3] w-full object-cover" />
      </div>
    );
  }

  return (
    <div
      aria-label={alt}
      className="flex aspect-[4/3] w-full items-center justify-center rounded-2xl border border-dashed border-brand-orange/30 bg-brand-white text-sm font-semibold text-brand-orange"
    >
      LeoVer
    </div>
  );
}

type PublicStatusBadgeProps = {
  label: string;
  tone?: "active" | "closed";
};

export function PublicStatusBadge({ label, tone = "active" }: PublicStatusBadgeProps) {
  const classes =
    tone === "active"
      ? "bg-brand-green/15 text-brand-green-dark"
      : "bg-brand-text/10 text-brand-text/80";

  return (
    <span className={`inline-flex rounded-full px-3 py-1 text-xs font-semibold ${classes}`}>
      {label}
    </span>
  );
}

type PublicFactProps = {
  label: string;
  value?: string | null;
};

export function PublicFact({ label, value }: PublicFactProps) {
  if (!value) {
    return null;
  }

  return (
    <div>
      <dt className="text-xs font-semibold uppercase tracking-wide text-brand-text/60">{label}</dt>
      <dd className="mt-1 text-sm text-brand-text">{value}</dd>
    </div>
  );
}

type PublicCtaPanelProps = {
  title: string;
  description: string;
  primaryHref: string;
  primaryLabel: string;
  secondaryHref?: string;
  secondaryLabel?: string;
};

export function PublicCtaPanel({
  title,
  description,
  primaryHref,
  primaryLabel,
  secondaryHref,
  secondaryLabel,
}: PublicCtaPanelProps) {
  return (
    <Card className="space-y-4">
      <div className="space-y-2">
        <h2 className="text-lg font-semibold text-brand-text">{title}</h2>
        <p className="text-sm text-brand-text/80">{description}</p>
      </div>
      <div className="flex flex-col gap-3 sm:flex-row">
        <Link href={primaryHref} className="w-full sm:w-auto">
          <Button className="w-full">{primaryLabel}</Button>
        </Link>
        {secondaryHref && secondaryLabel ? (
          <Link href={secondaryHref} className="w-full sm:w-auto">
            <Button variant="ghost" className="w-full">
              {secondaryLabel}
            </Button>
          </Link>
        ) : null}
      </div>
    </Card>
  );
}
