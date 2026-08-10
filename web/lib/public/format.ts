const MONTHS_ES = [
  "enero",
  "febrero",
  "marzo",
  "abril",
  "mayo",
  "junio",
  "julio",
  "agosto",
  "septiembre",
  "octubre",
  "noviembre",
  "diciembre",
];

export function formatApproxAge(
  ageYears?: number | null,
  ageMonths?: number | null,
): string | null {
  if (ageYears == null && ageMonths == null) {
    return null;
  }

  const years = ageYears ?? 0;
  const months = ageMonths ?? 0;

  if (years <= 0 && months <= 0) {
    return null;
  }

  if (years > 0 && months > 0) {
    return `${years} año${years === 1 ? "" : "s"} y ${months} mes${months === 1 ? "" : "es"}`;
  }

  if (years > 0) {
    return `${years} año${years === 1 ? "" : "s"}`;
  }

  return `${months} mes${months === 1 ? "" : "es"}`;
}

export function formatDateEs(value?: string | null): string | null {
  if (!value) {
    return null;
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return null;
  }

  return `${date.getDate()} de ${MONTHS_ES[date.getMonth()]} de ${date.getFullYear()}`;
}

export function adoptionStatusLabel(status: string, isActive: boolean): string {
  if (isActive) {
    return "Disponible para adopción";
  }
  if (status === "ADOPTED") {
    return "Adoptada";
  }
  if (status === "CLOSED") {
    return "No disponible";
  }
  return "Cerrada";
}

export function lostFoundStatusLabel(
  caseType: "LOST" | "FOUND",
  status: string,
  isActive: boolean,
): string {
  if (isActive) {
    return caseType === "LOST" ? "Perdida — buscando" : "Encontrada — buscando dueño";
  }

  return caseType === "LOST" ? "Encontrada / reunida" : "Reunida / resuelta";
}

export function speciesLabel(species?: string | null): string | null {
  if (!species) {
    return null;
  }

  const normalized = species.trim().toLowerCase();
  if (normalized === "dog" || normalized === "perro") {
    return "Perro";
  }
  if (normalized === "cat" || normalized === "gato") {
    return "Gato";
  }
  return species;
}
