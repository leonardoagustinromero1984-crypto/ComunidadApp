import { createClient } from "@/lib/supabase/server";

import type {
  PublicAdoption,
  PublicLostFoundCase,
  PublicPet,
} from "./types";

const SENSITIVE_MARKERS = [
  "contact_info",
  "author_id",
  "author_name",
  "publisher_id",
  "pet_id",
  "latitude",
  "longitude",
  "email",
  "phone",
  "service_role",
  "@",
  "+54",
];

export function assertNoSensitiveLeak(payload: unknown): void {
  const serialized = JSON.stringify(payload).toLowerCase();
  for (const marker of SENSITIVE_MARKERS) {
    if (serialized.includes(marker.toLowerCase())) {
      throw new Error(`Sensitive marker leaked in public payload: ${marker}`);
    }
  }
}

export function isNotPublicRpcError(error: { message?: string; code?: string } | null): boolean {
  if (!error) {
    return false;
  }
  return (
    error.code === "P0001" ||
    error.message?.includes("NOT_PUBLIC") === true ||
    error.message?.includes("PUBLIC_PASSPORT_NOT_AVAILABLE") === true ||
    error.message?.includes("CANONICAL_SCHEMA_BLOCKER") === true
  );
}

export async function fetchPublicPet(publicCode: string): Promise<PublicPet | null> {
  const supabase = await createClient();
  const { data, error } = await supabase.rpc("canon_public_pet", {
    p_code: publicCode,
  });

  if (error) {
    if (isNotPublicRpcError(error)) {
      return null;
    }
    throw error;
  }

  assertNoSensitiveLeak(data);
  const row = data as PublicPet;
  return {
    ...row,
    display_name: row.display_name || row.name || "Mascota",
    page_kind: row.page_kind ?? "pet",
    status: row.status || "ACTIVE",
  };
}

export async function fetchPublicAdoption(publicCode: string): Promise<PublicAdoption | null> {
  const supabase = await createClient();
  const { data, error } = await supabase.rpc("canon_public_adoption", {
    p_code: publicCode,
  });

  if (error) {
    if (isNotPublicRpcError(error)) {
      return null;
    }
    throw error;
  }

  if (!data) {
    return null;
  }

  assertNoSensitiveLeak(data);
  const row = data as PublicAdoption;
  return {
    ...row,
    status: row.status || "OPEN",
    is_active: row.is_active ?? row.status === "OPEN",
  };
}

export async function fetchPublicLostCase(publicCode: string): Promise<PublicLostFoundCase | null> {
  const supabase = await createClient();
  const { data, error } = await supabase.rpc("canon_public_lost_found", {
    p_code: publicCode,
  });

  if (error) {
    if (isNotPublicRpcError(error)) {
      return null;
    }
    throw error;
  }

  assertNoSensitiveLeak(data);
  return normalizeLostFound(data as PublicLostFoundCase, "LOST");
}

export async function fetchPublicFoundCase(publicCode: string): Promise<PublicLostFoundCase | null> {
  const supabase = await createClient();
  const { data, error } = await supabase.rpc("canon_public_lost_found", {
    p_code: publicCode,
  });

  if (error) {
    if (isNotPublicRpcError(error)) {
      return null;
    }
    throw error;
  }

  assertNoSensitiveLeak(data);
  return normalizeLostFound(data as PublicLostFoundCase, "FOUND");
}

export async function fetchPublicPost(postId: string): Promise<import("./types").PublicPost | null> {
  const supabase = await createClient();
  const { data, error } = await supabase.rpc("canon_get_public_social_post", {
    p_post_id: postId,
  });

  if (error) {
    if (isNotPublicRpcError(error)) {
      return null;
    }
    throw error;
  }

  if (!data) {
    return null;
  }

  assertNoSensitiveLeak(data);
  return data as import("./types").PublicPost;
}

function normalizeLostFound(
  row: PublicLostFoundCase,
  expected: "LOST" | "FOUND",
): PublicLostFoundCase | null {
  const kind = (row.kind || row.case_type || "").toUpperCase();
  if (kind !== expected) {
    return null;
  }
  return {
    ...row,
    case_type: expected,
    kind: expected,
    status: row.status || "OPEN",
    is_active: row.is_active ?? row.status === "OPEN",
  };
}
