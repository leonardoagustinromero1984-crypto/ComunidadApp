import { getAppUrl, getSupabaseUrl } from "@/lib/env";

export function canonicalPublicUrl(path: string): string {
  const base = getAppUrl();
  const normalized = path.startsWith("/") ? path : `/${path}`;
  return `${base}${normalized}`;
}

export function publicPetPath(publicCode: string): string {
  return `/mascota/${encodeURIComponent(publicCode)}`;
}

export function publicLostPath(publicCode: string): string {
  return `/perdidos/${encodeURIComponent(publicCode)}`;
}

export function publicFoundPath(publicCode: string): string {
  return `/encontrados/${encodeURIComponent(publicCode)}`;
}

export function publicAdoptionPath(publicCode: string): string {
  return `/adopciones/${encodeURIComponent(publicCode)}`;
}

export function resolvePublicImageUrl(photoUrl?: string | null): string | null {
  if (!photoUrl) {
    return null;
  }

  if (photoUrl.startsWith("https://")) {
    return photoUrl;
  }

  if (photoUrl.startsWith("storage:")) {
    const [, rest] = photoUrl.split("storage:");
    const [bucket, ...pathParts] = rest.split("/");
    const path = pathParts.join("/");
    if (!bucket || !path) {
      return null;
    }
    return `${getSupabaseUrl()}/storage/v1/object/public/${bucket}/${path}`;
  }

  return null;
}
