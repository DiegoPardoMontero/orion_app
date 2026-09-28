"use client";

import { BadgeCheck, ChevronRight, MapPin } from "lucide-react";
import Link from "next/link";
import { Avatar } from "@/components/Avatar";
import { DiscoIdioma } from "@/components/DiscoIdioma";
import { EstrellaRating } from "@/components/Rating";
import type { ProfessorCard } from "@/lib/api/types";
import { esGratis, tarifaClase } from "@/lib/format";
import { etiquetaNivel } from "@/lib/i18n";
import { paisConBandera } from "@/lib/paises";

/**
 * La tarjeta de un profe en el buscador de profesores. Vive aparte porque el profe también la ve en
 * «Mi perfil», con lo que está escribiendo, antes de guardar: así sabe cómo lo ven (27/09/2026).
 */
export function TarjetaProfesor({
  profesor,
  vistaPrevia = false,
}: {
  profesor: ProfessorCard;
  /** En la vista previa del propio profe: la misma tarjeta, pero «Ver agenda» no lleva a ningún lado. */
  vistaPrevia?: boolean;
}) {
  const ubicacion = [profesor.city, paisConBandera(profesor.countryCode)].filter(Boolean).join(", ");

  return (
    <div className="flex h-full flex-col rounded-card bg-surface-raised p-5 shadow-md transition-[transform,box-shadow] hover:-translate-y-0.5 hover:shadow-lg">
      <div className="flex items-start gap-3">
        <Avatar nombre={profesor.fullName ?? ""} fotoUrl={profesor.photoUrl} size="lg" />
        <div className="min-w-0 flex-1 pt-0.5">
          {/* El nombre y la descripción corta se leen enteros: nunca se cortan con «…». */}
          <p className="font-display text-[17px] leading-tight font-bold text-balance wrap-break-word">{profesor.fullName}</p>
          {profesor.headline && (
            <p className="mt-1 text-[13.5px] leading-snug font-semibold text-pretty wrap-break-word text-text-secondary">
              {profesor.headline}
            </p>
          )}
          <div className="mt-1.5">
            <EstrellaRating ratingAvg={profesor.ratingAvg} ratingCount={profesor.ratingCount} />
          </div>
        </div>
      </div>

      {/* Idiomas con bandera y "Nativo" */}
      {profesor.languages && profesor.languages.length > 0 && (
        <div className="mt-3 flex flex-wrap gap-1.5">
          {profesor.languages.map((idioma) => (
            <span
              key={idioma.code}
              className="inline-flex items-center gap-1 rounded-pill bg-surface-sunken px-2.5 py-1 text-[12px] font-semibold text-text-secondary"
            >
              <DiscoIdioma code={idioma.code ?? ""} size={16} />
              {idioma.nameEs}
              {idioma.isNative && (
                <span className="rounded-pill bg-primary-soft px-1.5 py-px text-[10px] font-bold text-primary-strong">
                  Nativo
                </span>
              )}
            </span>
          ))}
        </div>
      )}

      {/* Niveles + certificado */}
      <div className="mt-2.5 flex flex-wrap items-center gap-1.5">
        {(profesor.levels ?? []).map((nivel) => (
          <span
            key={nivel}
            className="rounded-pill bg-accent-lavender-soft px-2.5 py-1 text-[11.5px] font-semibold text-[#5e4a8a]"
          >
            {etiquetaNivel(nivel)}
          </span>
        ))}
        {profesor.certified && (
          <span className="inline-flex items-center gap-1 rounded-pill bg-success-bg px-2.5 py-1 text-[11.5px] font-bold text-success">
            <BadgeCheck size={13} strokeWidth={2.2} />
            Certificado
          </span>
        )}
      </div>

      {ubicacion && (
        <p className="mt-3 flex items-center gap-1 text-[12.5px] text-text-muted">
          <MapPin size={13} strokeWidth={1.75} />
          {ubicacion}
        </p>
      )}

      <div className="mt-4 flex items-end justify-between gap-3 border-t border-border pt-4">
        <div>
          {tarifaClase(profesor.hourlyRateCop) ? (
            <>
              <p className="font-display text-[19px] font-bold text-text">
                {tarifaClase(profesor.hourlyRateCop)}
              </p>
              {!esGratis(profesor.hourlyRateCop) && (
                <p className="text-[11.5px] text-text-muted">por hora</p>
              )}
            </>
          ) : (
            <p className="text-[12.5px] text-text-muted">Tarifa por confirmar</p>
          )}
        </div>
        {vistaPrevia ? (
          <span aria-hidden className="inline-flex min-h-11 items-center gap-1 rounded-pill bg-primary px-5 text-[14px] font-bold text-on-primary shadow-primary">
            Ver agenda
            <ChevronRight size={16} strokeWidth={1.75} />
          </span>
        ) : (
          <Link
            href={`/profesores/${profesor.id}`}
            className="inline-flex min-h-11 items-center gap-1 rounded-pill bg-primary px-5 text-[14px] font-bold text-on-primary shadow-primary transition-colors hover:bg-primary-strong focus-visible:shadow-focus"
          >
            Ver agenda
            <ChevronRight size={16} strokeWidth={1.75} />
          </Link>
        )}
      </div>
    </div>
  );
}
