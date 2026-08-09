import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";

describe("UI components", () => {
  it("renders button with accessible name", () => {
    render(<Button>Continuar</Button>);
    expect(screen.getByRole("button", { name: "Continuar" })).toBeInTheDocument();
  });

  it("renders labeled input", () => {
    render(<Input label="Email" name="email" />);
    expect(screen.getByLabelText("Email")).toBeInTheDocument();
  });
});
