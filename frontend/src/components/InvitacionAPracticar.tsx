"use client";

import { useQuery } from "@tanstack/react-query";
import { X } from "lucide-react";
import Link from "next/link";
import { useState, useSyncExternalStore } from "react";
import { Constelacion, formaDe } from "@/components/practica/Constelacion";
import { apiFetch } from "@/lib/api/fetch";
import {
  diaCorto,
  diaDeLaSemana,
  estrellasDe,
  nombreDelProfe,
  ordinalFemenino,
  palabrasNuevas,
  resumirLoTrabajado,
  tituloDelSet,
  type SetDePractica,
} from "@/lib/practica";

/**
 * La invitación a practicar (handoff `design_handoff_orion_practica`, §10.1): un pedazo de cielo
 * noche con la constelación por encender. Va en el perfil y, en su versión compacta, al pie del
 * resumen de la clase. Si no hay un set, no aparece —nada de «no tienes práctica disponible»—.
 */

function usePracticaViva() {
  return useQuery({
    queryKey: ["me", "practice"],
    // Sin set vivo el endpoint responde 204, y TanStack no admite `undefined` como dato: lo toma por
    // error y se queda con el set anterior, ya terminado. `null` sí es un «no hay».
    queryFn: async () =>
      (await apiFetch<SetDePractica | undefined>("/api/v1/me/practice")) ??
      null,
    staleTime: 60_000,
    // Mientras se prepara (cerca de un minuto), se vuelve a mirar para cambiar a «Practicar».
    refetchInterval: (q) => (q.state.data?.status === "PENDING" ? 5000 : false),
  });
}

/** «Del miércoles con María». */
function deQuien(s: SetDePractica): string {
  const dia = s.classStartsAt
    ? `Del ${diaDeLaSemana(s.classStartsAt)}`
    : "De tu última clase";
  return s.professorName ? `${dia} con ${nombreDelProfe(s)}` : dia;
}

/** «Check-in en el hotel y cuatro palabras nuevas.» */
function queTrae(s: SetDePractica): string | null {
  const partes = [
    resumirLoTrabajado(s.workedOn),
    s.vocabularyCount > 0 ? palabrasNuevas(s.vocabularyCount) : null,
  ].filter(Boolean);
  if (partes.length === 0) return null;
  const t = partes.join(" y ");
  return `${t.charAt(0).toUpperCase()}${t.slice(1)}.`;
}

function estrellas(s: SetDePractica) {
  const items = [...s.items].sort((a, b) => a.index - b.index);
  if (items.length === 0)
    return Array.from(
      { length: Math.max(1, Math.min(s.itemCount || 5, 5)) },
      () => "off" as const,
    );
  const actual =
    s.status === "IN_PROGRESS" ? items.find((i) => !i.closed)?.id : null;
  return estrellasDe(items, actual);
}

const SOMBRA = "shadow-[0_18px_36px_-18px_rgba(46,30,78,.7)]";

/** En el perfil: la tarjeta grande. Mientras se prepara, la compacta. */
export function InvitacionAPracticar() {
  const practica = usePracticaViva();
  const s = practica.data;
  if (!s) return null;
  if (s.status === "PENDING") {
    return (
      <div className="mt-6">
        <Compacta set={s} />
      </div>
    );
  }

  const enCurso = s.status === "IN_PROGRESS";
  const minutos = s.estimatedMinutes ?? s.itemCount;
  const vence = `Vence el ${diaCorto(s.expiresAt)}`;
  const que = queTrae(s);
  const est = estrellas(s);
  const forma = formaDe(s.id);
  const boton = enCurso ? "Seguir" : "Practicar";

  // Se acomoda al ancho que tiene, no al de la pantalla: en el perfil de escritorio la columna es
  // angosta y la versión ancha (texto a la izquierda, cielo a la derecha) no cabe.
  return (
    <div className="@container mt-6">
      <article
        aria-labelledby="titulo-practica"
        className={`flex flex-col gap-3.5 rounded-tarjeta bg-noche p-5 text-crema @2xl:grid @2xl:grid-cols-[minmax(0,1fr)_380px] @2xl:items-center @2xl:gap-8 @2xl:px-8 @2xl:py-7 ${SOMBRA}`}
      >
        <div className="flex items-center justify-between @2xl:hidden">
          <span className="inline-flex h-7 items-center rounded-pill bg-durazno px-3 text-[12px] font-extrabold text-ink">
            Para esta semana · {minutos} min
          </span>
          <span className="text-[12px] font-semibold text-[#E9DEF5]">
            {vence}
          </span>
        </div>
        <div className="flex justify-center @2xl:hidden">
          <Constelacion
            estados={est}
            forma={forma}
            ancho={300}
            r={10}
            guias
            fondo="noche"
          />
        </div>
        <div className="flex flex-col gap-1 @2xl:items-start @2xl:gap-3.5">
          <span className="hidden h-[30px] items-center rounded-pill bg-durazno px-3 text-[13px] font-extrabold text-ink @2xl:inline-flex">
            Para esta semana · {minutos} min
          </span>
          <h3
            id="titulo-practica"
            className="m-0 font-display text-[22px] leading-[30px] font-bold @2xl:text-[30px] @2xl:leading-[1.2]"
          >
            {deQuien(s)}
          </h3>
          <p className="m-0 text-[15px] leading-[1.5] text-[#E9DEF5] @2xl:text-[16px]">
            {que}
            <span className="hidden @2xl:inline">
              {que ? " " : ""}
              {vence}.
            </span>
          </p>
          <Link
            href={`/practica/${s.id}`}
            className="mt-2.5 hidden h-[52px] items-center justify-center rounded-pill bg-coral px-8 text-[16px] font-bold text-crema transition-colors hover:bg-coral-hover @2xl:inline-flex"
          >
            {boton}
          </Link>
        </div>
        <div className="hidden justify-center @2xl:flex">
          <Constelacion
            estados={est}
            forma={forma}
            ancho={380}
            r={11}
            guias
            fondo="noche"
          />
        </div>
        <Link
          href={`/practica/${s.id}`}
          className="flex h-[52px] items-center justify-center rounded-pill bg-coral text-[16px] font-bold text-crema transition-colors hover:bg-coral-hover @2xl:hidden"
        >
          {boton}
        </Link>
      </article>
    </div>
  );
}

/**
 * Al pie del resumen de la clase: la de esta acta, en cualquiera de sus momentos —preparándose,
 * lista, en curso o completada—. De otra clase no se muestra nada.
 */
export function InvitacionDelActa({ actaId }: { actaId: string }) {
  const viva = usePracticaViva();
  const historial = useQuery({
    queryKey: ["me", "practice", "history"],
    queryFn: () => apiFetch<SetDePractica[]>("/api/v1/me/practice/history"),
    staleTime: 60_000,
  });
  const s =
    (viva.data?.lessonNoteId === actaId ? viva.data : null) ??
    historial.data?.find(
      (h) => h.lessonNoteId === actaId && h.status === "COMPLETED",
    ) ??
    null;
  if (!s) return null;
  return <Compacta set={s} />;
}

/**
 * La práctica pendiente, recordada fuera del perfil (24/09/2026: «en otras partes, no solo cuando
 * entra a sus clases; no TAN invasivo»): la versión compacta arriba de «Mis clases» y de «Buscar
 * profesor», solo si está lista o a medias. Se cierra con la X hasta que llegue la siguiente.
 */
export function RecordatorioDePractica({ className = "" }: { className?: string }) {
  const practica = usePracticaViva();
  const cerrada = useSyncExternalStore(suscribirCierre, leerCierre, () => "cerrada-en-servidor");
  const [cerradaAhora, setCerradaAhora] = useState<string | null>(null);
  const s = practica.data;
  if (!s || (s.status !== "READY" && s.status !== "IN_PROGRESS")) return null;
  if (cerrada === s.id || cerrada === "cerrada-en-servidor" || cerradaAhora === s.id) return null;

  const cerrar = () => {
    try {
      window.localStorage.setItem(CIERRE, s.id);
    } catch {
      // Sin almacenamiento se cierra solo en esta visita.
    }
    setCerradaAhora(s.id);
  };

  return (
    <div className={`relative ${className}`}>
      <Compacta set={s} />
      <button
        type="button"
        onClick={cerrar}
        aria-label="Ocultar hasta la próxima práctica"
        title="Ocultar hasta la próxima práctica"
        className="absolute -right-1.5 -top-1.5 grid h-8 w-8 place-items-center rounded-full bg-surface-raised text-text-secondary shadow-sm hover:text-text focus-visible:shadow-focus"
      >
        <X size={15} strokeWidth={2.2} />
      </button>
    </div>
  );
}

const CIERRE = "orion.practica.recordatorio-cerrado";

function suscribirCierre(): () => void {
  return () => {};
}

function leerCierre(): string | null {
  try {
    return window.localStorage.getItem(CIERRE);
  } catch {
    return null;
  }
}

function Compacta({ set: s }: { set: SetDePractica }) {
  let ceja: string;
  let titulo = deQuien(s);
  let sub: string | null;
  let boton: { texto: string; claro: boolean } | null = {
    texto: "Practicar",
    claro: false,
  };
  switch (s.status) {
    case "PENDING":
      ceja = "Estamos preparando tu práctica";
      sub = "Tarda cerca de un minuto.";
      boton = null;
      break;
    case "IN_PROGRESS": {
      const siguiente = [...s.items]
        .sort((a, b) => a.index - b.index)
        .findIndex((i) => !i.closed);
      ceja = `Vas por la ${ordinalFemenino((siguiente < 0 ? s.items.length : siguiente) + 1)} estrella`;
      sub = "Sigues donde ibas.";
      boton = { texto: "Seguir", claro: false };
      break;
    }
    case "COMPLETED":
      ceja = `Completada${s.completedAt ? ` · ${diaCorto(s.completedAt)}` : ""}`;
      titulo = tituloDelSet(s) ?? titulo;
      sub = "Ya brilla en tu cielo.";
      boton = { texto: "Ver", claro: true };
      break;
    default:
      ceja = `Para esta semana · ${s.estimatedMinutes ?? s.itemCount} min`;
      sub = queTrae(s);
  }
  const completa = s.status === "COMPLETED";
  return (
    <div className="flex items-center gap-3.5 rounded-tarjeta bg-noche px-[18px] py-4 text-crema">
      <Constelacion
        estados={estrellas(s)}
        forma={formaDe(s.id)}
        ancho={110}
        r={8}
        guias={!completa}
        lineas={completa}
        perfecta={completa && s.perfect}
        fondo="noche"
        className="shrink-0"
      />
      <div className="flex min-w-0 flex-1 flex-col gap-[3px]">
        <span className="text-[12px] font-extrabold text-durazno">{ceja}</span>
        <span className="text-[15px] font-bold">{titulo}</span>
        {sub && <span className="text-[13px] text-[#E9DEF5]">{sub}</span>}
      </div>
      {boton && (
        <Link
          href={`/practica/${s.id}`}
          className={`flex h-11 shrink-0 items-center rounded-pill px-4 text-[14px] font-bold transition-colors ${
            boton.claro
              ? "bg-crema text-ink hover:bg-arena"
              : "bg-coral text-crema hover:bg-coral-hover"
          }`}
        >
          {boton.texto}
        </Link>
      )}
    </div>
  );
}
