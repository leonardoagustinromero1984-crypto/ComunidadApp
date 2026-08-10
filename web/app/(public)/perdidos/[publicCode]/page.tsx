import { notFound } from "next/navigation";

import {
  PublicCtaPanel,
  PublicFact,
  PublicHeroImage,
  PublicStatusBadge,
} from "@/components/public/PublicPageParts";
import { ShareButton } from "@/components/public/ShareButton";
import { Card } from "@/components/ui/Card";
import { fetchPublicLostCase } from "@/lib/public/api";
import { formatDateEs, lostFoundStatusLabel, speciesLabel } from "@/lib/public/format";
import { buildPublicMetadata } from "@/lib/public/metadata";
import {
  canonicalPublicUrl,
  publicLostPath,
  resolvePublicImageUrl,
} from "@/lib/public/urls";

type PageProps = {
  params: Promise<{ publicCode: string }>;
};

export const revalidate = 60;

export async function generateMetadata({ params }: PageProps) {
  const { publicCode } = await params;
  const lostCase = await fetchPublicLostCase(publicCode);

  if (!lostCase) {
    return buildPublicMetadata({
      title: "Caso no disponible | LeoVer",
      description: "Esta página no está disponible.",
      path: publicLostPath(publicCode),
      index: false,
    });
  }

  const name = lostCase.pet_name ?? "Mascota";
  const title = lostCase.is_active
    ? `${name} está perdida en LeoVer`
    : `${name} — caso cerrado en LeoVer`;

  return buildPublicMetadata({
    title,
    description: `Ayudá a reencontrar a ${name}. Zona aproximada y características compartidas de forma segura.`,
    path: publicLostPath(publicCode),
    imageUrl: resolvePublicImageUrl(lostCase.photo_url),
    index: lostCase.is_active,
  });
}

export default async function PublicLostPage({ params }: PageProps) {
  const { publicCode } = await params;
  const lostCase = await fetchPublicLostCase(publicCode);

  if (!lostCase) {
    notFound();
  }

  const shareUrl = canonicalPublicUrl(publicLostPath(publicCode));
  const imageUrl = resolvePublicImageUrl(lostCase.photo_url);
  const title = lostCase.pet_name ?? "Mascota perdida";
  const statusLabel = lostFoundStatusLabel("LOST", lostCase.status, lostCase.is_active);

  return (
    <article className="space-y-8">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="space-y-3">
          <p className="text-sm font-semibold uppercase tracking-wide text-brand-green">Perdidos</p>
          <h1 className="text-3xl font-bold text-brand-text sm:text-4xl">{title}</h1>
          <PublicStatusBadge
            label={statusLabel}
            tone={lostCase.is_active ? "active" : "closed"}
          />
        </div>
        <ShareButton
          url={shareUrl}
          title={`${title} en LeoVer`}
          text="Compartí este aviso para ayudar al reencuentro."
        />
      </div>

      <PublicHeroImage src={imageUrl} alt={`Foto de ${title}`} />

      <Card className="space-y-4">
        <dl className="grid gap-4 sm:grid-cols-2">
          <PublicFact label="Especie" value={speciesLabel(lostCase.species)} />
          <PublicFact label="Zona aproximada" value={lostCase.zone_text ?? "Zona no informada"} />
          <PublicFact label="Publicado" value={formatDateEs(lostCase.created_at)} />
        </dl>
        {lostCase.description ? (
          <p className="text-sm leading-relaxed text-brand-text/80">{lostCase.description}</p>
        ) : null}
      </Card>

      {lostCase.is_active ? (
        <PublicCtaPanel
          title="¿La viste?"
          description="Ingresá a LeoVer para reportar un avistamiento de forma segura, sin exponer tu contacto personal en esta página."
          primaryHref={`/acceso?redirect=${encodeURIComponent(publicLostPath(publicCode))}`}
          primaryLabel="Reportar avistamiento"
          secondaryHref="/"
          secondaryLabel="Ir al inicio"
        />
      ) : (
        <Card>
          <p className="text-sm text-brand-text/80">
            Este caso ya no está activo. Gracias por haber compartido y ayudado.
          </p>
        </Card>
      )}
    </article>
  );
}
