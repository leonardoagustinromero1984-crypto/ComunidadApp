import { notFound } from "next/navigation";

import {
  PublicCtaPanel,
  PublicFact,
  PublicHeroImage,
  PublicStatusBadge,
} from "@/components/public/PublicPageParts";
import { ShareButton } from "@/components/public/ShareButton";
import { Card } from "@/components/ui/Card";
import { fetchPublicPet } from "@/lib/public/api";
import { formatDateEs, speciesLabel } from "@/lib/public/format";
import { buildPublicMetadata } from "@/lib/public/metadata";
import { canonicalPublicUrl, publicPetPath, resolvePublicImageUrl } from "@/lib/public/urls";

type PageProps = {
  params: Promise<{ publicCode: string }>;
};

export const revalidate = 60;

export async function generateMetadata({ params }: PageProps) {
  const { publicCode } = await params;
  const pet = await fetchPublicPet(publicCode);

  if (!pet) {
    return buildPublicMetadata({
      title: "Mascota no disponible | LeoVer",
      description: "Esta página no está disponible.",
      path: publicPetPath(publicCode),
      index: false,
    });
  }

  const title = `${pet.display_name} en LeoVer`;
  const description = [
    speciesLabel(pet.species),
    pet.breed_text,
    "Identidad verificable compartida de forma segura en LeoVer.",
  ]
    .filter(Boolean)
    .join(" · ");

  return buildPublicMetadata({
    title,
    description,
    path: publicPetPath(publicCode),
    imageUrl: resolvePublicImageUrl(pet.photo_url),
    index: true,
  });
}

export default async function PublicPetPage({ params }: PageProps) {
  const { publicCode } = await params;
  const pet = await fetchPublicPet(publicCode);

  if (!pet) {
    notFound();
  }

  const shareUrl = canonicalPublicUrl(publicPetPath(publicCode));
  const imageUrl = resolvePublicImageUrl(pet.photo_url);
  const credentials = pet.credentials ?? [];

  return (
    <article className="space-y-8">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="space-y-3">
          <p className="text-sm font-semibold uppercase tracking-wide text-brand-green">Mascota</p>
          <h1 className="text-3xl font-bold text-brand-text sm:text-4xl">{pet.display_name}</h1>
          <PublicStatusBadge label="Pasaporte público" tone="active" />
        </div>
        <ShareButton
          url={shareUrl}
          title={`${pet.display_name} en LeoVer`}
          text="Conocé esta mascota en LeoVer."
        />
      </div>

      <PublicHeroImage src={imageUrl} alt={`Foto de ${pet.display_name}`} />

      <Card>
        <dl className="grid gap-4 sm:grid-cols-2">
          <PublicFact label="Especie" value={speciesLabel(pet.species)} />
          <PublicFact label="Raza" value={pet.breed_text} />
          <PublicFact label="Sexo" value={pet.sex} />
          <PublicFact label="Color principal" value={pet.primary_color} />
          <PublicFact label="Nacimiento" value={formatDateEs(pet.birth_date)} />
          <PublicFact label="Microchip" value={pet.microchip_masked} />
        </dl>
        {pet.distinctive_marks ? (
          <p className="mt-4 text-sm text-brand-text/80">{pet.distinctive_marks}</p>
        ) : null}
      </Card>

      {credentials.length > 0 ? (
        <Card className="space-y-3">
          <h2 className="text-lg font-semibold">Verificaciones públicas</h2>
          <ul className="space-y-3">
            {credentials.map((credential, index) => (
              <li key={`${credential.type}-${index}`} className="rounded-xl bg-brand-cream px-4 py-3 text-sm">
                <p className="font-semibold text-brand-text">{credential.title ?? credential.type}</p>
                {credential.issuer ? (
                  <p className="text-brand-text/70">Emisor: {credential.issuer}</p>
                ) : null}
              </li>
            ))}
          </ul>
        </Card>
      ) : null}

      <PublicCtaPanel
        title="Abrí en LeoVer"
        description="Ingresá para ver más contexto dentro de la app y conectar con la comunidad."
        primaryHref={`/acceso?redirect=${encodeURIComponent(publicPetPath(publicCode))}`}
        primaryLabel="Ingresar"
        secondaryHref="/"
        secondaryLabel="Ir al inicio"
      />
    </article>
  );
}
