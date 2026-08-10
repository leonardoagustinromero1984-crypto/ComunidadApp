import { notFound } from "next/navigation";

import {
  PublicCtaPanel,
  PublicFact,
  PublicHeroImage,
  PublicStatusBadge,
} from "@/components/public/PublicPageParts";
import { ShareButton } from "@/components/public/ShareButton";
import { Card } from "@/components/ui/Card";
import { fetchPublicAdoption } from "@/lib/public/api";
import {
  adoptionStatusLabel,
  formatApproxAge,
  speciesLabel,
} from "@/lib/public/format";
import { buildPublicMetadata } from "@/lib/public/metadata";
import {
  canonicalPublicUrl,
  publicAdoptionPath,
  resolvePublicImageUrl,
} from "@/lib/public/urls";

type PageProps = {
  params: Promise<{ publicCode: string }>;
};

export const revalidate = 60;

export async function generateMetadata({ params }: PageProps) {
  const { publicCode } = await params;
  const adoption = await fetchPublicAdoption(publicCode);

  if (!adoption) {
    return buildPublicMetadata({
      title: "Adopción no disponible | LeoVer",
      description: "Esta página no está disponible.",
      path: publicAdoptionPath(publicCode),
      index: false,
    });
  }

  const petName = adoption.name ?? adoption.title ?? "Mascota";
  const title = adoption.is_active
    ? `${petName} busca familia en LeoVer`
    : `${petName} — adopción cerrada en LeoVer`;

  return buildPublicMetadata({
    title,
    description:
      adoption.description ??
      "Conocé esta publicación de adopción compartida de forma segura en LeoVer.",
    path: publicAdoptionPath(publicCode),
    imageUrl: resolvePublicImageUrl(adoption.photo_url),
    index: adoption.is_active,
  });
}

export default async function PublicAdoptionPage({ params }: PageProps) {
  const { publicCode } = await params;
  const adoption = await fetchPublicAdoption(publicCode);

  if (!adoption) {
    notFound();
  }

  const shareUrl = canonicalPublicUrl(publicAdoptionPath(publicCode));
  const imageUrl = resolvePublicImageUrl(adoption.photo_url);
  const petName = adoption.name ?? adoption.title ?? "Mascota en adopción";
  const age = formatApproxAge(adoption.age_years, adoption.age_months);
  const statusLabel = adoptionStatusLabel(adoption.status, adoption.is_active);

  return (
    <article className="space-y-8">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="space-y-3">
          <p className="text-sm font-semibold uppercase tracking-wide text-brand-green">Adopción</p>
          <h1 className="text-3xl font-bold text-brand-text sm:text-4xl">{petName}</h1>
          <PublicStatusBadge
            label={statusLabel}
            tone={adoption.is_active ? "active" : "closed"}
          />
        </div>
        <ShareButton url={shareUrl} title={`${petName} en LeoVer`} text="Compartí esta adopción." />
      </div>

      <PublicHeroImage src={imageUrl} alt={`Foto de ${petName}`} />

      <Card className="space-y-4">
        <dl className="grid gap-4 sm:grid-cols-2">
          <PublicFact label="Especie" value={speciesLabel(adoption.species)} />
          <PublicFact label="Sexo" value={adoption.sex} />
          <PublicFact label="Edad" value={age ?? "Edad no informada"} />
          <PublicFact label="Tamaño" value={adoption.size} />
          <PublicFact label="Ubicación aproximada" value={adoption.location_text} />
          <PublicFact label="Publicado por" value={adoption.publisher_display_name} />
        </dl>
        {adoption.description ? (
          <p className="text-sm leading-relaxed text-brand-text/80">{adoption.description}</p>
        ) : null}
        {adoption.requirements ? (
          <div>
            <h2 className="text-sm font-semibold text-brand-text">Requisitos</h2>
            <p className="mt-2 text-sm text-brand-text/80">{adoption.requirements}</p>
          </div>
        ) : null}
      </Card>

      {adoption.is_active ? (
        <PublicCtaPanel
          title="Quiero adoptar"
          description="El proceso de adopción se gestiona dentro de LeoVer con el flujo seguro de M09. Ingresá para continuar."
          primaryHref={`/acceso?redirect=${encodeURIComponent(publicAdoptionPath(publicCode))}`}
          primaryLabel="Quiero adoptar"
          secondaryHref="/"
          secondaryLabel="Ir al inicio"
        />
      ) : (
        <Card>
          <p className="text-sm text-brand-text/80">
            Esta publicación ya no está disponible para adopción.
          </p>
        </Card>
      )}
    </article>
  );
}
