"use client";

import { useActionState } from "react";

import { Card } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";
import { loginAction, type LoginState } from "@/features/auth/actions";

type LoginFormProps = {
  redirectTo?: string;
};

const initialState: LoginState = {};

export function LoginForm({ redirectTo }: LoginFormProps) {
  const [state, formAction, pending] = useActionState(loginAction, initialState);

  return (
    <Card>
      <form action={formAction} className="flex flex-col gap-4">
        <div className="space-y-1">
          <h1 className="text-2xl font-bold text-brand-text">Ingresar</h1>
          <p className="text-sm text-brand-text/70">
            Usá tu cuenta LeoVer existente.
          </p>
        </div>

        {redirectTo ? <input type="hidden" name="redirect" value={redirectTo} /> : null}

        <Input
          label="Email"
          name="email"
          type="email"
          autoComplete="email"
          required
        />
        <Input
          label="Contraseña"
          name="password"
          type="password"
          autoComplete="current-password"
          required
        />

        {state.error ? (
          <p className="text-sm text-red-700" role="alert">
            {state.error}
          </p>
        ) : null}

        <Button type="submit" disabled={pending} className="w-full">
          {pending ? "Ingresando..." : "Ingresar"}
        </Button>

        <p className="text-center text-sm text-brand-text/70">
          ¿Todavía no tenés cuenta? Podés registrarte desde la app LeoVer.
        </p>
      </form>
    </Card>
  );
}
