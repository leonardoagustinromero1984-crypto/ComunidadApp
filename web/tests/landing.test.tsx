import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import HomePage from "@/app/(public)/page";

describe("HomePage", () => {
  it("renders LeoVer landing content", () => {
    render(<HomePage />);

    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(
      "Conectamos mascotas, personas y comunidad.",
    );
    expect(screen.getAllByRole("link", { name: "Ingresar" }).length).toBeGreaterThan(0);
  });
});
