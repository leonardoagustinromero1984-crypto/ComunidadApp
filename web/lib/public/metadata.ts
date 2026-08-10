import type { Metadata } from "next";

import { getAppUrl } from "@/lib/env";

type BuildMetadataInput = {
  title: string;
  description: string;
  path: string;
  imageUrl?: string | null;
  index?: boolean;
};

export function buildPublicMetadata({
  title,
  description,
  path,
  imageUrl,
  index = true,
}: BuildMetadataInput): Metadata {
  const canonical = `${getAppUrl()}${path.startsWith("/") ? path : `/${path}`}`;
  const ogImage = imageUrl ?? `${getAppUrl()}/favicon.ico`;

  return {
    title,
    description,
    alternates: { canonical },
    robots: index
      ? { index: true, follow: true }
      : { index: false, follow: false },
    openGraph: {
      title,
      description,
      url: canonical,
      siteName: "LeoVer",
      locale: "es_AR",
      type: "website",
      images: [{ url: ogImage }],
    },
    twitter: {
      card: imageUrl ? "summary_large_image" : "summary",
      title,
      description,
      images: imageUrl ? [imageUrl] : undefined,
    },
  };
}
