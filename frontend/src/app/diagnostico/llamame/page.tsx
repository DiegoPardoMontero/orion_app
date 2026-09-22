"use client";

import { useState } from "react";
import Link from "next/link";
import { useMutation } from "@tanstack/react-query";
import { apiFetch, ApiError } from "@/lib/api/fetch";
import { AvisoError } from "@/components/estados";
import { Wordmark } from "@/components/marca";
import { Boton } from "@/components/ui";

/**
 * «¿Prefieres que te llame una persona?» (handoff de Meissa). Para quien no quiere, o todavía no se
 * anima, a hablar con una voz de IA: deja su nombre y su WhatsApp, y alguien de la academia le
 * escribe. Sin cuenta.
 *
 * <p>La autorización va en su casilla, desmarcada: el número se usa para una sola cosa, escribirle
 * sobre clases, y la persona tiene que haberlo dicho.
 */
export default function LlamamePage() {
  const [nombre, setNombre] = useState("");
  const [whatsapp, setWhatsapp] = useState("");
  const [acepta, setAcepta] = useState(false);

  const pedir = useMutation({
    mutationFn: () =>
      apiFetch<{ firstName: string }>("/api/v1/callback-requests", {
        method: "POST",
        body: { firstName: nombre.trim(), whatsapp, acceptsContact: acepta },
        redirectOn401: false,
      }),
  });

  const listo = nombre.trim().length > 0 && whatsapp.trim().length >= 7 && acepta;

  return (
    <main className="mx-auto w-full max-w-md px-6 py-6">
      <Link href="/" className="inline-block rounded-base text-primary focus-visible:shadow-focus">
        <Wordmark className="text-[15px]" />
      </Link>

      {pedir.isSuccess ? (
        <section className="mt-10">
          <h1 className="font-display text-[28px] font-bold leading-tight">
            Listo, {pedir.data.firstName}.
          </h1>
          <p className="mt-2 text-[15px] leading-relaxed text-text-secondary">
            Alguien de la academia te escribe pronto por WhatsApp. Si mientras tanto te animas, Meissa
            sigue ahí.
          </p>
          <div className="mt-6 grid gap-2.5">
            <Link
              href="/diagnostico"
              className="inline-flex h-[52px] items-center justify-center rounded-pill bg-primary px-6 text-[15px] font-bold text-on-primary shadow-primary hover:bg-primary-strong focus-visible:shadow-focus"
            >
              Hablar con Meissa
            </Link>
            <Link
              href="/"
              className="inline-flex h-11 items-center justify-center rounded-pill text-[14px] font-bold text-text-secondary hover:bg-surface-sunken focus-visible:shadow-focus"
            >
              Volver al inicio
            </Link>
          </div>
        </section>
      ) : (
        <section className="mt-8">
          <h1 className="font-display text-[28px] font-bold leading-tight">Te escribimos nosotros</h1>
          <p className="mt-2 text-[15px] leading-relaxed text-text-secondary">
            Déjanos tu nombre y tu WhatsApp, y alguien de la academia te escribe para contarte cómo
            funcionan las clases.
          </p>

          <label className="mt-6 block">
            <span className="text-[14px] font-bold text-text">¿Cómo te llamas?</span>
            <input
              type="text"
              autoComplete="given-name"
              maxLength={60}
              value={nombre}
              onChange={(e) => setNombre(e.target.value)}
              placeholder="Tu nombre"
              className="mt-1.5 h-12 w-full rounded-base border border-border bg-surface-raised px-4 text-[15px] focus:border-primary focus:outline-none focus-visible:shadow-focus"
            />
          </label>
          <label className="mt-4 block">
            <span className="text-[14px] font-bold text-text">Tu WhatsApp</span>
            <input
              type="tel"
              autoComplete="tel"
              inputMode="tel"
              maxLength={30}
              value={whatsapp}
              onChange={(e) => setWhatsapp(e.target.value)}
              placeholder="300 123 4567"
              className="mt-1.5 h-12 w-full rounded-base border border-border bg-surface-raised px-4 text-[15px] focus:border-primary focus:outline-none focus-visible:shadow-focus"
            />
          </label>

          <label className="mt-4 flex cursor-pointer items-start gap-3 rounded-base bg-surface-raised p-3.5">
            <input
              type="checkbox"
              checked={acepta}
              onChange={(e) => setAcepta(e.target.checked)}
              className="mt-0.5 h-5 w-5 shrink-0 accent-[var(--color-primary)]"
            />
            <span className="text-[13.5px] leading-relaxed text-text">
              Autorizo a Orión a escribirme o llamarme a este número para hablar de las clases.
            </span>
          </label>

          {pedir.error instanceof ApiError && (
            <div className="mt-4">
              <AvisoError mensaje={pedir.error.message} />
            </div>
          )}

          <Boton
            variante="primario"
            className="mt-6 w-full"
            disabled={!listo || pedir.isPending}
            onClick={() => pedir.mutate()}
          >
            {pedir.isPending ? "Un momento…" : "Que me escriban"}
          </Boton>
        </section>
      )}
    </main>
  );
}
