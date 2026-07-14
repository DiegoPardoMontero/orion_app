"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { ApiError } from "@/lib/api/fetch";
import { HOME_BY_ROLE } from "@/lib/auth/roles";
import { useLogin } from "@/lib/auth/session";

export default function LoginPage() {
  const router = useRouter();
  const login = useLogin();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    login.mutate(
      { email, password },
      {
        // Cada rol aterriza donde le sirve: el estudiante a explorar, la profesora a sus clases.
        onSuccess: (me) => router.replace(HOME_BY_ROLE[me.role]),
      },
    );
  }

  const error = login.error instanceof ApiError ? login.error.message : null;

  return (
    <main className="flex-1 grid place-items-center p-6">
      <div className="w-full max-w-md">
        <div className="text-center">
          <h1 className="text-3xl font-semibold text-accent">Orión</h1>
          <p className="mt-1 text-sm text-ink-muted">Language Academy</p>
        </div>

        <form
          onSubmit={onSubmit}
          className="mt-8 rounded-card border border-line bg-card p-6"
        >
          <h2 className="text-lg font-semibold">Hola de nuevo</h2>
          <p className="mt-1 text-sm text-ink-soft">Entra para ver tus clases.</p>

          <label className="mt-6 block text-sm font-semibold text-ink-soft" htmlFor="email">
            Correo
          </label>
          <input
            id="email"
            type="email"
            required
            autoComplete="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            className="mt-1.5 w-full rounded-orion border border-line bg-card px-3 py-2 text-sm outline-none focus:border-accent"
          />

          <label className="mt-4 block text-sm font-semibold text-ink-soft" htmlFor="password">
            Contraseña
          </label>
          <input
            id="password"
            type="password"
            required
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            className="mt-1.5 w-full rounded-orion border border-line bg-card px-3 py-2 text-sm outline-none focus:border-accent"
          />

          {error && (
            <p className="mt-4 rounded-orion bg-danger-soft px-3 py-2 text-sm text-danger">
              {error}
            </p>
          )}

          <button
            type="submit"
            disabled={login.isPending}
            className="mt-6 w-full rounded-orion bg-accent py-2.5 text-sm font-semibold text-white disabled:opacity-60"
          >
            {login.isPending ? "Entrando…" : "Entrar"}
          </button>
        </form>
      </div>
    </main>
  );
}
