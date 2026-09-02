"use client";

import { useQuery } from "@tanstack/react-query";
import { ArrowRight, ChevronRight } from "lucide-react";
import Link from "next/link";
import { Avatar } from "@/components/Avatar";
import { Cargando } from "@/components/estados";
import { Constelacion, Wordmark } from "@/components/marca";
import { apiFetch } from "@/lib/api/fetch";
import type { LanguageResponse, PagedProfessors, ProfessorCard } from "@/lib/api/types";
import { precioCop } from "@/lib/format";
import { etiquetaNivel } from "@/lib/i18n";

/**
 * Contenido interactivo de la landing por idioma. Isla cliente: consulta el catálogo y hasta 4
 * profesores reales. Los metadatos/JSON-LD los pone el server component padre (`page.tsx`). Datos
 * honestos: sin ratings ni inventos. Recibe el código de idioma ya resuelto por el padre.
 */
export function IdiomaLanding({ codigo }: { codigo: string }) {
  const languages = useQuery({
    queryKey: ["catalog", "languages"],
    queryFn: () => apiFetch<LanguageResponse[]>("/api/v1/catalog/languages"),
    staleTime: 5 * 60_000,
  });

  const profesores = useQuery({
    queryKey: ["professors", "landing", codigo],
    queryFn: () => apiFetch<PagedProfessors>(`/api/v1/professors?language=${codigo}&size=4`),
  });

  const idioma = languages.data?.find((l) => (l.code ?? "").toUpperCase() === codigo);
  const nombre = idioma?.nameEs ?? "idiomas";
  const lista = profesores.data?.content ?? [];

  return (
    <div className="flex-1">
      {/* — Hero — */}
      <header className="gradient-dawn relative overflow-hidden">
        <Constelacion className="pointer-events-none absolute -right-10 top-6 h-[220px] w-[220px] opacity-60 lg:h-[360px] lg:w-[360px]" />
        <div className="relative mx-auto max-w-4xl px-6 py-14 lg:px-8 lg:py-20">
          <Link href="/" aria-label="Ir al inicio">
            <Wordmark className="text-[17px] text-on-primary" />
          </Link>
          <h1 className="mt-6 max-w-[18ch] font-display text-[34px] font-bold leading-[1.08] text-on-primary lg:text-[52px]">
            Aprende {idioma?.flagEmoji ? `${idioma.flagEmoji} ` : ""}
            {nombre} con profesores reales
          </h1>
          <p className="mt-4 max-w-[48ch] text-[15px] leading-relaxed text-on-primary/85 lg:text-[17px]">
            Clases en vivo, a tu ritmo y sin miedo a equivocarte. Elige a tu profesor, mira sus
            horarios y reserva cuando quieras.
          </p>
          <div className="mt-8">
            <Link
              href={`/profesores?language=${codigo}`}
              className="inline-flex h-[52px] items-center justify-center gap-2 rounded-pill bg-primary px-7 text-[15px] font-bold text-on-primary shadow-primary transition-colors hover:bg-primary-strong focus-visible:shadow-focus"
            >
              Ver profesores de {nombre}
              <ArrowRight size={18} strokeWidth={1.75} />
            </Link>
          </div>
        </div>
      </header>

      {/* — Profesores destacados — */}
      <section className="mx-auto max-w-5xl px-6 py-14 lg:px-8 lg:py-20">
        <h2 className="font-display text-h2 font-bold">Algunos de nuestros profesores</h2>

        {profesores.isPending ? (
          <div className="mt-6">
            <Cargando filas={2} />
          </div>
        ) : lista.length === 0 ? (
          <p className="mt-4 rounded-card bg-surface-raised px-5 py-8 text-center text-[14px] text-text-secondary shadow-sm">
            Aún no tenemos profesores de {nombre} disponibles. Vuelve pronto: estamos creciendo.
          </p>
        ) : (
          <ul className="mt-6 grid gap-3 sm:grid-cols-2">
            {lista.map((profesor) => (
              <li key={profesor.id}>
                <TarjetaPublica profesor={profesor} />
              </li>
            ))}
          </ul>
        )}

        <div className="mt-8 flex justify-center">
          <Link
            href={`/profesores?language=${codigo}`}
            className="inline-flex h-12 items-center justify-center gap-1.5 rounded-pill border-[1.5px] border-primary px-7 text-[15px] font-bold text-primary-strong transition-colors hover:bg-primary-soft focus-visible:shadow-focus"
          >
            Ver todos
            <ChevronRight size={18} strokeWidth={1.75} />
          </Link>
        </div>
      </section>
    </div>
  );
}

function TarjetaPublica({ profesor }: { profesor: ProfessorCard }) {
  return (
    <Link
      href={`/profesores/${profesor.id}`}
      className="flex h-full items-start gap-3 rounded-card bg-surface-raised p-5 shadow-md transition-[transform,box-shadow] hover:-translate-y-0.5 hover:shadow-lg"
    >
      <Avatar nombre={profesor.fullName ?? ""} fotoUrl={profesor.photoUrl} size="lg" />
      <div className="min-w-0 flex-1">
        <p className="truncate font-display text-[16px] font-bold">{profesor.fullName}</p>
        {profesor.headline && (
          <p className="mt-0.5 line-clamp-2 text-[13px] font-semibold text-text-secondary">
            {profesor.headline}
          </p>
        )}
        <div className="mt-2 flex flex-wrap items-center gap-1.5">
          {(profesor.levels ?? []).slice(0, 3).map((nivel) => (
            <span
              key={nivel}
              className="rounded-pill bg-accent-lavender-soft px-2 py-0.5 text-[11px] font-semibold text-[#5e4a8a]"
            >
              {etiquetaNivel(nivel)}
            </span>
          ))}
        </div>
        {profesor.hourlyRateCop ? (
          <p className="mt-2 text-[13px] font-bold text-text">
            {precioCop(profesor.hourlyRateCop)}
            <span className="ml-1 text-[11px] font-semibold text-text-muted">/ hora</span>
          </p>
        ) : null}
      </div>
    </Link>
  );
}
