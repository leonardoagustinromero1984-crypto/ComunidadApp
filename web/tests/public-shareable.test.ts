import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import { describe, expect, it } from "vitest";

import { assertNoSensitiveLeak, isNotPublicRpcError } from "@/lib/public/api";
import {
  adoptionStatusLabel,
  formatApproxAge,
  lostFoundStatusLabel,
} from "@/lib/public/format";
import { buildPublicMetadata } from "@/lib/public/metadata";
import {
  canonicalPublicUrl,
  publicAdoptionPath,
  publicFoundPath,
  publicLostPath,
  publicPetPath,
  resolvePublicImageUrl,
} from "@/lib/public/urls";

describe("public shareable helpers", () => {
  it("builds canonical public URLs", () => {
    expect(publicPetPath("PUB-abc")).toBe("/mascota/PUB-abc");
    expect(publicLostPath("PUB-lost")).toBe("/perdidos/PUB-lost");
    expect(publicFoundPath("PUB-found")).toBe("/encontrados/PUB-found");
    expect(publicAdoptionPath("PUB-adopt")).toBe("/adopciones/PUB-adopt");
    expect(canonicalPublicUrl(publicPetPath("PUB-abc"))).toBe(
      "https://leover.com.ar/mascota/PUB-abc",
    );
  });

  it("resolves storage-backed image URLs", () => {
    expect(resolvePublicImageUrl("https://example.com/a.jpg")).toBe("https://example.com/a.jpg");
    expect(
      resolvePublicImageUrl("storage:public-media/adoptions/abc.jpg"),
    ).toContain("/storage/v1/object/public/public-media/adoptions/abc.jpg");
    expect(resolvePublicImageUrl(null)).toBeNull();
  });

  it("formats age and status labels", () => {
    expect(formatApproxAge(2, 3)).toContain("2 años");
    expect(formatApproxAge(null, null)).toBeNull();
    expect(adoptionStatusLabel("ADOPTED", false)).toBe("Adoptada");
    expect(lostFoundStatusLabel("LOST", "ACTIVE", true)).toContain("Perdida");
  });

  it("builds sanitized metadata", () => {
    const metadata = buildPublicMetadata({
      title: "Luna en LeoVer",
      description: "Compartido de forma segura",
      path: "/mascota/PUB-1",
      index: true,
    });

    expect(metadata.title).toBe("Luna en LeoVer");
    expect(metadata.alternates?.canonical).toBe("https://leover.com.ar/mascota/PUB-1");
    expect(metadata.robots).toEqual({ index: true, follow: true });
  });

  it("detects not-public RPC errors", () => {
    expect(isNotPublicRpcError({ code: "P0001", message: "NOT_PUBLIC" })).toBe(true);
    expect(isNotPublicRpcError({ message: "network down" })).toBe(false);
  });

  it("rejects sensitive markers in public payloads", () => {
    expect(() =>
      assertNoSensitiveLeak({ name: "Luna", species: "dog" }),
    ).not.toThrow();

    expect(() => assertNoSensitiveLeak({ contact_info: "123" })).toThrow();
    expect(() => assertNoSensitiveLeak({ latitude: -34.6 })).toThrow();
    expect(() => assertNoSensitiveLeak({ publisher_id: "uuid" })).toThrow();
  });
});

describe("canonical public RPC static guards", () => {
  const migrationPath = resolve(
    process.cwd(),
    "../infra/supabase-canonical/supabase/migrations/20260815172000_1020_rls_rpc.sql",
  );

  it("defines anon public RPCs with security definer and grants", () => {
    const sql = readFileSync(migrationPath, "utf8");

    expect(sql).toContain("canon_public_pet");
    expect(sql).toContain("canon_public_lost_found");
    expect(sql).not.toContain("canon_public_adoption");
    expect(sql.toLowerCase()).toContain("security definer");
    expect(sql).toContain("set search_path = public");
    expect(sql).toContain("grant execute on function public.canon_public_pet(text) to anon");
    expect(sql).toContain("grant execute on function public.canon_public_lost_found(text) to anon");
    expect(sql.toLowerCase()).not.toContain("service_role");
    expect(sql).not.toMatch(/jsonb_build_object\([\s\S]*'contact_info'/);
    expect(sql).not.toMatch(/jsonb_build_object\([\s\S]*'latitude'/);
  });

  it("defines 03E public adoption RPC without PII", () => {
    const enablementPath = resolve(
      process.cwd(),
      "../infra/supabase-canonical/supabase/migrations/20260815212500_1023_consumer_enablement.sql",
    );
    const sql = readFileSync(enablementPath, "utf8");
    expect(sql).toContain("canon_public_adoption");
    expect(sql).toContain("grant execute on function public.canon_public_adoption(text) to anon");
    expect(sql).not.toMatch(/jsonb_build_object\([\s\S]*'pet_id'/);
    expect(sql).not.toMatch(/jsonb_build_object\([\s\S]*'published_by'/);
    expect(sql).not.toMatch(/jsonb_build_object\([\s\S]*'email'/);
    expect(sql.toLowerCase()).not.toContain("service_role");
  });
});
