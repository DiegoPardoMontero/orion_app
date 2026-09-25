"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, ArrowRight, BadgeCheck, LifeBuoy } from "lucide-react";
import Link from "next/link";
import { useEffect, useRef } from "react";
import { Cargando, ErrorCarga } from "@/components/estados";
import { Rigel } from "@/components/Rigel";
import { apiFetch } from "@/lib/api/fetch";
import type { RigelMessageResponse } from "@/lib/api/types";
import { fechaCorta, horaBogota } from "@/lib/format";
import { rigelKey, useRigel } from "@/lib/rigel";
import { ListaConversaciones } from "../lista";

/**
 * El hilo de Rigel (24/09/2026): los mensajes oficiales de Orión, de solo lectura. Cada uno dice
 * qué pasó y trae a mano los botones de lo que sigue. No hay caja para responder —Rigel no lee
 * respuestas—, y el pie lo dice y lleva a Ayuda, donde sí contesta una persona.
 */
export default function HiloDeRigelPage() {
  return (
    <main className="mx-auto w-full max-w-5xl lg:px-12 lg:py-8">
      <div className="lg:grid lg:grid-cols-[340px_minmax(0,1fr)] lg:gap-8">
        <div className="hidden lg:block">
          <ListaConversaciones activaId="rigel" />
        </div>
        <HiloDeRigel />
      </div>
    </main>
  );
}

function HiloDeRigel() {
  const queryClient = useQueryClient();
  const hilo = useRigel();
  const finRef = useRef<HTMLDivElement>(null);
  const leer = useMutation({
    mutationFn: () => apiFetch<void>("/api/v1/me/rigel/read", { method: "POST" }),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: rigelKey }),
  });

  // Abrir el hilo es leerlo: el número de «Mensajes» se apaga.
  const noLeidos = hilo.data?.unread ?? 0;
  const { mutate: marcarLeidos, isPending: marcando } = leer;
  useEffect(() => {
    if (noLeidos > 0 && !marcando) marcarLeidos();
  }, [noLeidos, marcando, marcarLeidos]);

  const cantidad = hilo.data?.messages?.length ?? 0;
  useEffect(() => {
    finRef.current?.scrollIntoView({ block: "end" });
  }, [cantidad]);

  return (
    <section className="flex h-[calc(100dvh-160px)] flex-col lg:h-[calc(100dvh-64px)] lg:rounded-card lg:bg-surface-raised lg:shadow-sm">
      <header className="flex items-center gap-3 border-b border-surface-sunken bg-surface px-4 py-3 lg:rounded-t-card lg:bg-transparent">
        <Link
          href="/mensajes"
          aria-label="Volver a mensajes"
          className="grid h-10 w-10 shrink-0 place-items-center rounded-full text-text transition-colors hover:bg-surface-sunken focus-visible:shadow-focus lg:hidden"
        >
          <ArrowLeft size={18} strokeWidth={1.75} />
        </Link>
        <span className="grid h-10 w-10 shrink-0 place-items-center rounded-full bg-rigel-soft ring-2 ring-rigel/60">
          <Rigel pose="guia" decorativo className="h-auto w-8" />
        </span>
        <div className="min-w-0">
          <p className="flex items-center gap-1 text-[14.5px] font-bold text-text">
            Rigel · Orión
            <BadgeCheck size={15} strokeWidth={2.2} className="text-rigel-ink" aria-hidden />
          </p>
          <p className="text-[11.5px] text-text-muted">Mensajes oficiales de Orión</p>
        </div>
      </header>

      <div className="flex-1 overflow-y-auto px-4 py-4">
        {hilo.isPending && <Cargando filas={3} />}
        {hilo.isError && (
          <ErrorCarga mensaje="No pudimos cargar los mensajes de Rigel." onReintentar={() => void hilo.refetch()} />
        )}
        <div className="flex flex-col gap-4">
          {hilo.data?.messages?.map((m) => <MensajeDeRigel key={m.id} mensaje={m} />)}
        </div>
        <div ref={finRef} />
      </div>

      <footer className="flex items-center gap-2.5 border-t border-surface-sunken px-4 py-3 text-[12.5px] text-text-secondary lg:rounded-b-card">
        <LifeBuoy size={16} strokeWidth={1.9} className="shrink-0 text-text-muted" />
        <p className="min-w-0 flex-1">
          Rigel no lee respuestas. ¿Necesitas algo?{" "}
          <Link href="/ayuda" className="font-bold text-primary-strong underline-offset-2 hover:underline">
            Escríbenos desde Ayuda
          </Link>
          .
        </p>
      </footer>
    </section>
  );
}

function MensajeDeRigel({ mensaje }: { mensaje: RigelMessageResponse }) {
  const botones = mensaje.buttons ?? [];
  return (
    <article className="max-w-[92%] self-start lg:max-w-[80%]">
      <div className="rounded-[18px] rounded-tl-md bg-rigel-soft/70 px-4 py-3 text-text">
        <h3 className="font-display text-[15px] font-bold leading-snug">{mensaje.title}</h3>
        <p className="mt-1.5 whitespace-pre-line text-[13.5px] leading-relaxed text-text-secondary">{mensaje.body}</p>
        {botones.length > 0 && (
          <div className="mt-3 flex flex-wrap gap-2">
            {botones.map((b, i) => (
              <Link
                key={b.href}
                href={b.href ?? "/"}
                className={`inline-flex min-h-10 items-center gap-1.5 rounded-pill px-3.5 text-[13px] font-bold transition-colors focus-visible:shadow-focus ${
                  i === 0
                    ? "bg-primary text-on-primary shadow-primary hover:bg-primary-strong"
                    : "border-[1.5px] border-border bg-surface-raised text-text hover:bg-surface-sunken"
                }`}
              >
                {b.label}
                {i === 0 && <ArrowRight size={14} strokeWidth={2.2} aria-hidden />}
              </Link>
            ))}
          </div>
        )}
      </div>
      <p className="mt-1 px-1 text-[10.5px] text-text-muted">
        {mensaje.createdAt ? `${fechaCorta(mensaje.createdAt)} · ${horaBogota(mensaje.createdAt)}` : ""}
      </p>
    </article>
  );
}
