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
    error.message?.includes("PUBLIC_PASSPORT_NOT_AVAILABLE") === true
  );
}

export async function fetchPublicPet(publicCode: string): Promise<PublicPet | null> {
  const supabase = await createClient();
  const { data, error } = await supabase.rpc("get_public_pet", {
    p_public_code: publicCode,
  });

  if (error) {
    if (isNotPublicRpcError(error)) {
      return null;
    }
    throw error;
  }

  assertNoSensitiveLeak(data);
  return data as PublicPet;
}

export async function fetchPublicAdoption(publicCode: string): Promise<PublicAdoption | null> {
  const supabase = await createClient();
  const { data, error } = await supabase.rpc("get_public_adoption", {
    p_public_code: publicCode,
  });

  if (error) {
    if (isNotPublicRpcError(error)) {
      return null;
    }
    throw error;
  }

  assertNoSensitiveLeak(data);
  return data as PublicAdoption;
}

export async function fetchPublicLostCase(publicCode: string): Promise<PublicLostFoundCase | null> {
  const supabase = await createClient();
  const { data, error } = await supabase.rpc("get_public_lost_case", {
    p_public_code: publicCode,
  });

  if (error) {
    if (isNotPublicRpcError(error)) {
      return null;
    }
    throw error;
  }

  assertNoSensitiveLeak(data);
  return data as PublicLostFoundCase;
}

export async function fetchPublicFoundCase(publicCode: string): Promise<PublicLostFoundCase | null> {
  const supabase = await createClient();
  const { data, error } = await supabase.rpc("get_public_found_case", {
    p_public_code: publicCode,
  });

  if (error) {
    if (isNotPublicRpcError(error)) {
      return null;
    }
    throw error;
  }

  assertNoSensitiveLeak(data);
  return data as PublicLostFoundCase;
}
