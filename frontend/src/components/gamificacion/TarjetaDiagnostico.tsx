"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { ArrowRight, Sparkles } from "lucide-react";
import { apiFetch } from "@/lib/api/fetch";
import type { Diagnostico } from "@/lib/api/diagnostico";
import { fechaCorta } from "@/lib/format";

/**
 * El Confidence Score en el perfil: el número, cuándo se midió y cómo volver a verlo.
 *
 * <p><strong>Con una sola medición no se dibuja curva.</strong> Dos puntos hacen una recta y una
 * recta parece una tendencia; con uno solo, cualquier gráfico sería una afirmación inventada sobre
 * el progreso de alguien. Cuando haya varios, aquí va la curva.
 *
 * <p>Si nunca lo ha hecho, la tarjeta invita. Si acaba de hacerlo, dice cuándo puede repetir —
 * saber que existe un plazo evita que lo intente y choque contra un 409.
 */
export function TarjetaDiagnostico() {
  const historial = useQuery({
    queryKey: ["me", "assessments"],
    queryFn: () => apiFetch<Diagnostico[]>("/api/v1/me/assessments"),
    staleTime: 60_000,
  });

  if (historial.isPending || historial.isError) return null;

  const hechos = (historial.data ?? []).filter((d) => d.status === "COMPLETED");
  const ultimo = hechos[0];

  if (!ultimo) {
    return (
      <Link
        href="/diagnostico/empezar"
        className="mt-4 flex items-center gap-3.5 rounded-card bg-[linear-gradient(140deg,#2E1E4E_0%,#4A2E63_100%)] p-5 text-text-on-night transition-transform hover:-translate-y-0.5 focus-visible:shadow-focus"
      >
        <Sparkles size={22} strokeWidth={1.9} className="shrink-0 text-accent-peach" />
        <span className="min-w-0 flex-1">
          <span className="block font-display text-[16px] font-bold">
            Prueba tu inglés en 2 minutos
          </span>
          <span className="mt-0.5 block text-[13px] text-text-on-night/80">
            Sin examen. Recibes tu Confidence Score y tres profesores.
          </span>
        </span>
        <ArrowRight size={17} strokeWidth={2.2} className="shrink-0" />
      </Link>
    );
  }

  return (
    <div className="mt-4 rounded-card bg-[linear-gradient(140deg,#2E1E4E_0%,#4A2E63_100%)] p-5 text-text-on-night">
      <p className="text-[11px] font-bold uppercase tracking-[0.1em] text-accent-peach">
        Confidence Score
      </p>
      <div className="mt-1.5 flex items-baseline gap-2.5">
        <span className="font-display text-[34px] font-bold leading-none">{ultimo.score}</span>
        <span className="text-[13px] text-text-on-night/70">
          medido el {fechaCorta(ultimo.completedAt ?? ultimo.startedAt)}
        </span>
      </div>

      {ultimo.summary && (
        <p className="mt-3 text-[13.5px] leading-relaxed text-text-on-night/85">{ultimo.summary}</p>
      )}

      {/* Con una sola medición no hay curva que dibujar: dos puntos harían una recta, y una recta
          parece una tendencia. Cuando haya varios, va aquí. */}
      <Link
        href="/diagnostico/empezar"
        className="mt-4 inline-flex items-center gap-1.5 text-[13px] font-bold text-accent-peach hover:underline"
      >
        Ver mi diagnóstico
        <ArrowRight size={14} strokeWidth={2.2} />
      </Link>
    </div>
  );
}
