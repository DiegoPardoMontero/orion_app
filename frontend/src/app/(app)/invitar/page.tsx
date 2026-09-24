"use client";

import { useQuery } from "@tanstack/react-query";
import { AlertTriangle, Check, Copy, Link2, Mail, MessageCircle, Send, Share2 } from "lucide-react";
import Link from "next/link";
import { useState, useSyncExternalStore } from "react";
import { Cargando } from "@/components/estados";
import { Rigel } from "@/components/Rigel";
import { apiFetch } from "@/lib/api/fetch";
import type { ProfileResponse } from "@/lib/api/types";
import { useMe } from "@/lib/auth/session";
import { esGratis, precioCop } from "@/lib/format";

/**
 * Invitar estudiantes (24/09/2026: «que me genere un link para compartir en redes sociales o
 * directamente a mis estudiantes»). El enlace es su perfil público: ahí están sus horarios, su tarifa
 * y sus reseñas, se ve sin cuenta y, al crearla para reservar, se vuelve a él. Con un mensaje ya
 * escrito —que menciona la clase de prueba si la ofrece— y un botón por cada lugar donde compartirlo.
 */
export default function InvitarPage() {
  const { data: me } = useMe();
  const perfil = useQuery({
    queryKey: ["me", "profile"],
    queryFn: () => apiFetch<ProfileResponse>("/api/v1/me/profile"),
  });
  // El enlace corto (V63): /p/maria-gomez. Se crea la primera vez que se pide.
  const corto = useQuery({
    queryKey: ["me", "profile", "invite-link"],
    queryFn: () => apiFetch<{ slug: string }>("/api/v1/me/profile/invite-link"),
    staleTime: Infinity,
  });
  const origen = useSyncExternalStore(
    () => () => {},
    () => window.location.origin,
    () => "",
  );

  if (!me || perfil.isPending || corto.isPending) {
    return (
      <main className="mx-auto w-full max-w-md px-5 py-6 lg:max-w-3xl lg:px-12">
        <Cargando filas={3} />
      </main>
    );
  }

  // Sin el corto (un fallo al crearlo), el del perfil de siempre: más largo, pero funciona igual.
  const enlace = corto.data?.slug ? `${origen}/p/${corto.data.slug}` : `${origen}/profesores/${me.id}`;
  const p = perfil.data;
  const prueba =
    p?.acceptsTrial && p.trialPriceCop != null
      ? esGratis(p.trialPriceCop)
        ? " La primera clase de prueba es gratis."
        : ` La clase de prueba cuesta ${precioCop(p.trialPriceCop)}.`
      : "";
  const mensajeInicial = `¡Hola! Ya doy clases de inglés en Orión. Ahí ves mis horarios y reservas tu clase conmigo en un minuto.${prueba}`;

  return <Invitar enlace={enlace} publicado={p?.isPublished ?? false} mensajeInicial={mensajeInicial} />;
}

function Invitar({ enlace, publicado, mensajeInicial }: { enlace: string; publicado: boolean; mensajeInicial: string }) {
  const [mensaje, setMensaje] = useState(mensajeInicial);
  const [copiado, setCopiado] = useState<"enlace" | "mensaje" | null>(null);
  const completo = `${mensaje.trim()} ${enlace}`;
  const puedeCompartir = typeof navigator !== "undefined" && "share" in navigator;

  async function copiar(que: "enlace" | "mensaje") {
    try {
      await navigator.clipboard.writeText(que === "enlace" ? enlace : completo);
      setCopiado(que);
      setTimeout(() => setCopiado(null), 2200);
    } catch {
      setCopiado(null);
    }
  }

  const t = encodeURIComponent(mensaje.trim());
  const u = encodeURIComponent(enlace);
  const redes = [
    { nombre: "WhatsApp", href: `https://wa.me/?text=${encodeURIComponent(completo)}`, icono: <MessageCircle size={18} strokeWidth={1.9} /> },
    { nombre: "Facebook", href: `https://www.facebook.com/sharer/sharer.php?u=${u}`, icono: <Share2 size={18} strokeWidth={1.9} /> },
    { nombre: "LinkedIn", href: `https://www.linkedin.com/sharing/share-offsite/?url=${u}`, icono: <Share2 size={18} strokeWidth={1.9} /> },
    { nombre: "X", href: `https://x.com/intent/post?text=${t}&url=${u}`, icono: <Share2 size={18} strokeWidth={1.9} /> },
    { nombre: "Telegram", href: `https://t.me/share/url?url=${u}&text=${t}`, icono: <Send size={18} strokeWidth={1.9} /> },
    {
      nombre: "Correo",
      href: `mailto:?subject=${encodeURIComponent("Clases de inglés conmigo en Orión")}&body=${encodeURIComponent(completo)}`,
      icono: <Mail size={18} strokeWidth={1.9} />,
    },
  ];

  return (
    <main className="mx-auto w-full max-w-md px-5 py-6 lg:max-w-3xl lg:px-12 lg:py-8">
      <h1 className="font-display text-h1 font-bold">Invitar estudiantes</h1>
      <p className="mt-1 text-[14px] text-text-secondary">
        Tu enlace lleva a tu perfil: tus horarios, tu tarifa y tus reseñas. Quien llega por él crea su cuenta y reserva
        contigo sin buscarte.
      </p>

      {!publicado && (
        <div className="mt-4 flex items-start gap-2.5 rounded-card bg-accent-peach-soft p-4 text-[13.5px] text-[#8a5a33]">
          <AlertTriangle size={17} strokeWidth={2} className="mt-0.5 shrink-0" />
          <p>
            Tu perfil todavía no está publicado: quien abra el enlace no te va a encontrar.{" "}
            <Link href="/perfil" className="font-bold underline">
              Publícalo en tu perfil
            </Link>
            .
          </p>
        </div>
      )}

      <section className="mt-5 rounded-card bg-surface-raised p-5 shadow-sm">
        <div className="flex items-center gap-3">
          <Rigel pose="guia" decorativo className="h-auto w-12 shrink-0" />
          <h2 className="font-display text-[17px] font-bold">Tu enlace</h2>
        </div>
        <div className="mt-3 flex flex-col gap-2 sm:flex-row sm:items-center">
          <p className="flex min-h-11 min-w-0 flex-1 items-center gap-2 rounded-base bg-surface-sunken px-3.5 text-[13.5px] font-semibold text-text">
            <Link2 size={16} strokeWidth={2} className="shrink-0 text-text-muted" aria-hidden />
            <span className="min-w-0 select-all break-all">{enlace}</span>
          </p>
          <button
            type="button"
            onClick={() => void copiar("enlace")}
            className="inline-flex min-h-11 shrink-0 items-center justify-center gap-2 rounded-pill bg-primary px-5 text-[14px] font-bold text-on-primary shadow-primary transition-colors hover:bg-primary-strong focus-visible:shadow-focus"
          >
            {copiado === "enlace" ? <Check size={16} strokeWidth={2.2} /> : <Copy size={16} strokeWidth={2} />}
            {copiado === "enlace" ? "Copiado" : "Copiar enlace"}
          </button>
        </div>
      </section>

      <section className="mt-4 rounded-card bg-surface-raised p-5 shadow-sm">
        <label htmlFor="mensaje" className="font-display text-[17px] font-bold">
          El mensaje
        </label>
        <p className="mt-0.5 text-[13px] text-text-muted">Cámbialo si quieres: el enlace va al final.</p>
        <textarea
          id="mensaje"
          value={mensaje}
          onChange={(e) => setMensaje(e.target.value)}
          rows={3}
          maxLength={400}
          className="mt-2.5 w-full resize-y rounded-base border-[1.5px] border-border bg-surface-raised px-4 py-3 text-[14.5px] leading-relaxed text-text focus:border-primary focus:shadow-focus focus:outline-none"
        />

        <h3 className="mt-4 text-[12px] font-bold uppercase tracking-[0.06em] text-text-secondary">Compártelo</h3>
        <div className="mt-2 grid grid-cols-2 gap-2 sm:grid-cols-3">
          {puedeCompartir && (
            <button
              type="button"
              onClick={() => void navigator.share({ text: mensaje.trim(), url: enlace }).catch(() => {})}
              className="col-span-2 inline-flex min-h-11 items-center justify-center gap-2 rounded-pill bg-night px-4 text-[14px] font-bold text-on-primary focus-visible:shadow-focus sm:col-span-3"
            >
              <Share2 size={17} strokeWidth={2} />
              Compartir desde el celular
            </button>
          )}
          {redes.map((r) => (
            <a
              key={r.nombre}
              href={r.href}
              target="_blank"
              rel="noreferrer noopener"
              className="inline-flex min-h-11 items-center justify-center gap-2 rounded-pill border-[1.5px] border-border bg-surface-raised px-4 text-[14px] font-semibold text-text transition-colors hover:bg-surface-sunken focus-visible:shadow-focus"
            >
              {r.icono}
              {r.nombre}
            </a>
          ))}
          <button
            type="button"
            onClick={() => void copiar("mensaje")}
            className="inline-flex min-h-11 items-center justify-center gap-2 rounded-pill border-[1.5px] border-border bg-surface-raised px-4 text-[14px] font-semibold text-text transition-colors hover:bg-surface-sunken focus-visible:shadow-focus"
          >
            {copiado === "mensaje" ? <Check size={16} strokeWidth={2.2} /> : <Copy size={16} strokeWidth={2} />}
            {copiado === "mensaje" ? "Copiado" : "Copiar mensaje"}
          </button>
        </div>
        <p className="mt-3 text-[12.5px] text-text-muted">
          Instagram y TikTok no dejan compartir un enlace desde afuera: copia el mensaje y pégalo en tu historia o en
          tu biografía.
        </p>
      </section>
    </main>
  );
}
