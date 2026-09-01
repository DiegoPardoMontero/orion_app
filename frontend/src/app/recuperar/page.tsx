"use client";

import { useMutation } from "@tanstack/react-query";
import { ArrowLeft, Mail, MailCheck } from "lucide-react";
import Link from "next/link";
import { useState, type FormEvent } from "react";
import { AvisoError } from "@/components/estados";
import { Wordmark } from "@/components/marca";
import { BotonPrincipal, Campo, Spinner } from "@/components/ui";
import { ApiError, apiFetch } from "@/lib/api/fetch";

export default function RecuperarPage() {
  const [email, setEmail] = useState("");

  const enviar = useMutation({
    mutationFn: () =>
      apiFetch<void>("/api/v1/auth/forgot-password", { method: "POST", body: { email: email.trim() } }),
  });

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    enviar.mutate();
  }

  const error = enviar.error instanceof ApiError ? enviar.error.message : null;

  return (
    <main className="flex min-h-dvh flex-col items-center justify-center px-6 py-12">
      <div className="w-full max-w-md">
        <Wordmark className="text-[18px] text-primary" />

        {enviar.isSuccess ? (
          <div className="anim-rise mt-8">
            <div className="grid h-[60px] w-[60px] place-items-center rounded-full bg-success-bg text-success">
              <MailCheck size={26} strokeWidth={1.75} />
            </div>
            <h1 className="mt-4 font-display text-[26px] font-bold">Revisa tu correo</h1>
            <p className="mt-2 text-[14px] leading-relaxed text-text-secondary">
              Si existe una cuenta con <span className="font-semibold text-text">{email.trim()}</span>, te
              enviamos un enlace para crear una contraseña nueva. Vence en 30 minutos.
            </p>
            <Link
              href="/login"
              className="mt-6 inline-flex items-center gap-2 text-[14px] font-bold text-primary-strong hover:underline"
            >
              <ArrowLeft size={16} strokeWidth={1.75} />
              Volver a entrar
            </Link>
          </div>
        ) : (
          <form onSubmit={onSubmit} className="mt-8">
            <h1 className="font-display text-[26px] font-bold">¿Olvidaste tu contraseña?</h1>
            <p className="mt-2 text-[14px] leading-relaxed text-text-secondary">
              Escribe tu correo y te enviamos un enlace para crear una nueva.
            </p>

            <label
              className="mt-6 block text-[12px] font-bold uppercase tracking-[0.04em] text-text-secondary"
              htmlFor="email"
            >
              Correo
            </label>
            <Campo
              id="email"
              type="email"
              required
              autoComplete="email"
              placeholder="tu@correo.com"
              icono={<Mail size={18} strokeWidth={1.75} />}
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              className="mt-1.5"
            />

            {error && (
              <div className="mt-4">
                <AvisoError mensaje={error} />
              </div>
            )}

            <BotonPrincipal type="submit" disabled={!email.trim() || enviar.isPending} className="mt-6">
              {enviar.isPending ? (
                <>
                  <Spinner />
                  Enviando…
                </>
              ) : (
                "Enviar enlace"
              )}
            </BotonPrincipal>

            <Link
              href="/login"
              className="mt-6 flex items-center justify-center gap-2 text-[13px] font-semibold text-text-secondary hover:text-text"
            >
              <ArrowLeft size={15} strokeWidth={1.75} />
              Volver a entrar
            </Link>
          </form>
        )}
      </div>
    </main>
  );
}
