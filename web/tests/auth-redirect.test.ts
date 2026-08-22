import { describe, expect, it } from "vitest";

import { getLoginRedirectPath } from "@/lib/auth/session";

describe("getLoginRedirectPath", () => {
  it("returns safe internal redirect paths", () => {
    expect(getLoginRedirectPath("/cuenta")).toBe("/cuenta");
    expect(getLoginRedirectPath("/marca")).toBe("/marca");
  });

  it("rejects external or protocol-relative redirects", () => {
    expect(getLoginRedirectPath("https://evil.example")).toBe("/cuenta");
    expect(getLoginRedirectPath("//evil.example")).toBe("/cuenta");
    expect(getLoginRedirectPath(null)).toBe("/cuenta");
  });

  it("rejects null-like destinations", () => {
    expect(getLoginRedirectPath("null")).toBe("/cuenta");
    expect(getLoginRedirectPath("anull")).toBe("/cuenta");
    expect(getLoginRedirectPath("undefined")).toBe("/cuenta");
  });
});
