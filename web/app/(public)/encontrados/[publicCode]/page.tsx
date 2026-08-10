import { notFound } from "next/navigation";

import {
  PublicCtaPanel,
  PublicFact,
  PublicHeroImage,
  PublicStatusBadge,
} from "@/components/public/PublicPageParts";
import { ShareButton } from "@/components/public/ShareButton";
import { Card } from "@/components/ui/Card";
import { fetchPublicFoundCase } from "@/lib/public/api";
import { formatDateEs, lostFoundStatusLabel, speciesLabel } from "@/lib/public/format";
import { buildPublicMetadata } from "@/lib/public/metadata";
import {
  canonicalPublicUrl,
  publicFoundPath,
  resolvePublicImageUrl,
} from "@/lib/public/urls";

type PageProps = {
  params: Promise<{ publicCode: string }>;
};

export const revalidate = 60;

export async function generateMetadata({ params }: PageProps) {
  const { publicCode } = await params;
  const foundCase = await fetchPublicFoundCase(publicCode);

  if (!foundCase) {
    return buildPublicMetadata({
      title: "Caso no disponible | LeoVer",
      description: "Esta página no está disponible.",
      path: publicFoundPath(publicCode),
      index: false,
    });
  }

  const title = foundCase.is_active
    ? "Animal encontrado en LeoVer"
    : "Animal encontrado — caso cerrado";

  return buildPublicMetadata({
    title,
    description: "Ayudá a reencontrar a su familia. Información compartida de forma segura.",
    path: publicFoundPath(publicCode),
    imageUrl: resolvePublicImageUrl(foundCase.photo_url),
    index: foundCase.is_active,
  });
}

export default async function PublicFoundPage({ params }: PageProps) {
  const { publicCode } = await params;
  const foundCase = await fetchPublicFoundCase(publicCode);

  if (!foundCase) {
    notFound();
  }

  const shareUrl = canonicalPublicUrl(publicFoundPath(publicCode));
  const imageUrl = resolvePublicImageUrl(foundCase.photo_url);
  const statusLabel = lostFoundStatusLabel("FOUND", foundCase.status, foundCase.is_active);

  return (
    <article className="space-y-8">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="space-y-3">
          <p className="text-sm font-semibold uppercase tracking-wide text-brand-green">Encontrados</p>
          <h1 className="text-3xl font-bold text-brand-text sm:text-4xl">Animal encontrado</h1>
          <PublicStatusBadge
            label={statusLabel}
            tone={foundCase.is_active ? "active" : "closed"}
          />
        </div>
        <ShareButton
          url={shareUrl}
          title="Animal encontrado en LeoVer"
          text="Compartí este aviso para ayudar al reencuentro."
        />
      </div>

      <PublicHeroImage src={imageUrl} alt="Foto del animal encontrado" />

      <Card className="space-y-4">
        <dl className="grid gap-4 sm:grid-cols-2">
          <PublicFact label="Especie" value={speciesLabel(foundCase.species)} />
          <PublicFact label="Zona aproximada" value={foundCase.zone_text ?? "Zona no informada"} />
          <PublicFact label="Registrado" value={formatDateEs(foundCase.created_at)} />
        </dl>
        {foundCase.description ? (
          <p className="text-sm leading-relaxed text-brand-text/80">{foundCase.description}</p>
        ) : null}
      </Card>

      {foundCase.is_active ? (
        <PublicCtaPanel
          title="¿Reconocés a este animal?"
          description="Ingresá a LeoVer para aportar información de forma segura. No publicamos teléfonos ni ubicaciones exactas en esta página."
          primaryHref={`/acceso?redirect=${encodeURIComponent(publicFoundPath(publicCode))}`}
          primaryLabel="Aportar información"
          secondaryHref="/"
          secondaryLabel="Ir al inicio"
        />
      ) : (
        <Card>
          <p className="text-sm text-brand-text/80">
            Este caso ya fue resuelto. Gracias por haber ayudado.
          </p>
        </Card>
      )}
    </article>
  );
}
