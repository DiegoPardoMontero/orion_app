"use client";

import { useMutation } from "@tanstack/react-query";
import { ArrowRight, CheckCircle2, Eye, EyeOff, Lock } from "lucide-react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useState, type FormEvent } from "react";
import { AvisoError } from "@/components/estados";
import { Wordmark } from "@/components/marca";
import { BotonPrincipal, Campo, Spinner } from "@/components/ui";
import { ApiError, apiFetch } from "@/lib/api/fetch";
import { fuerzaClave } from "@/lib/password";

export default function RestablecerPage() {
  return (
    <Suspense fallback={null}>
      <Restablecer />
    </Suspense>
  );
}

function Restablecer() {
  const token = useSearchParams().get("token");
  const [password, setPassword] = useState("");
  const [verClave, setVerClave] = useState(false);

  const restablecer = useMutation({
    mutationFn: () =>
      apiFetch<void>("/api/v1/auth/reset-password", {
        method: "POST",
        body: { token, newPassword: password },
      }),
  });

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (password.length >= 8) restablecer.mutate();
  }

  const fuerza = fuerzaClave(password);
  const error = restablecer.error instanceof ApiError ? restablecer.error.message : null;

  return (
    <main className="flex min-h-dvh flex-col items-center justify-center px-6 py-12">
      <div className="w-full max-w-md">
        <Wordmark className="text-[18px] text-primary" />

        {!token ? (
          <div className="mt-8">
            <h1 className="font-display text-[26px] font-bold">Enlace inválido</h1>
            <p className="mt-2 text-[14px] leading-relaxed text-text-secondary">
              Este enlace no trae la información necesaria. Pide uno nuevo y vuelve a intentarlo.
            </p>
            <Link
              href="/recuperar"
              className="mt-6 inline-flex items-center gap-2 text-[14px] font-bold text-primary-strong hover:underline"
            >
              Pedir un enlace nuevo
              <ArrowRight size={16} strokeWidth={1.75} />
            </Link>
          </div>
        ) : restablecer.isSuccess ? (
          <div className="anim-rise mt-8">
            <div className="grid h-[60px] w-[60px] place-items-center rounded-full bg-success-bg text-success">
              <CheckCircle2 size={26} strokeWidth={1.75} />
            </div>
            <h1 className="mt-4 font-display text-[26px] font-bold">Contraseña lista</h1>
            <p className="mt-2 text-[14px] leading-relaxed text-text-secondary">
              Tu contraseña quedó actualizada. Entra con ella.
            </p>
            <Link href="/login" className="mt-6 block">
              <BotonPrincipal>
                Entrar
                <ArrowRight size={18} strokeWidth={1.75} />
              </BotonPrincipal>
            </Link>
          </div>
        ) : (
          <form onSubmit={onSubmit} className="mt-8">
            <h1 className="font-display text-[26px] font-bold">Crea una contraseña nueva</h1>
            <p className="mt-2 text-[14px] leading-relaxed text-text-secondary">
              Elige una contraseña que recuerdes; con ella entrarás a partir de ahora.
            </p>

            <label
              className="mt-6 block text-[12px] font-bold uppercase tracking-[0.04em] text-text-secondary"
              htmlFor="password"
            >
              Contraseña nueva
            </label>
            <div className="relative">
              <Campo
                id="password"
                type={verClave ? "text" : "password"}
                required
                autoComplete="new-password"
                placeholder="Mínimo 8 caracteres"
                icono={<Lock size={18} strokeWidth={1.75} />}
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                className="mt-1.5 pr-12"
              />
              <button
                type="button"
                aria-label={verClave ? "Ocultar contraseña" : "Mostrar contraseña"}
                onClick={() => setVerClave((valor) => !valor)}
                className="absolute right-3 top-1/2 mt-[3px] grid h-9 w-9 -translate-y-1/2 place-items-center rounded-full text-text-muted transition-colors hover:text-text focus-visible:shadow-focus"
              >
                {verClave ? <EyeOff size={18} strokeWidth={1.75} /> : <Eye size={18} strokeWidth={1.75} />}
              </button>
            </div>

            <div className="mt-2.5" aria-hidden="true">
              <div className="flex gap-1.5">
                {[0, 1, 2, 3].map((i) => (
                  <span
                    key={i}
                    className={`h-[5px] flex-1 rounded-pill transition-colors ${
                      i < fuerza.nivel ? "bg-success" : "bg-border"
                    }`}
                  />
                ))}
              </div>
              <p
                className={`mt-1.5 text-[12px] ${
                  fuerza.nivel >= 3 ? "text-success" : password ? "text-text-secondary" : "text-text-muted"
                }`}
              >
                {fuerza.mensaje}
              </p>
            </div>

            {error && (
              <div className="mt-4">
                <AvisoError mensaje={error} />
              </div>
            )}

            <BotonPrincipal
              type="submit"
              disabled={password.length < 8 || restablecer.isPending}
              className="mt-4"
            >
              {restablecer.isPending ? (
                <>
                  <Spinner />
                  Guardando…
                </>
              ) : (
                "Guardar contraseña"
              )}
            </BotonPrincipal>
          </form>
        )}
      </div>
    </main>
  );
}
