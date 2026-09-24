"use client";

import { ChevronLeft, Eye, X } from "lucide-react";
import Link from "next/link";
import { Rigel } from "@/components/Rigel";
import { useMe } from "@/lib/auth/session";
import {
  diaDeLaClase,
  NOMBRE_DEL_TIPO,
  nombreDelProfe,
  NOMBRE_DE_CATEGORIA,
  primerNombre,
  tituloDelSet,
  type SetDePractica,
} from "@/lib/practica";
import { Constelacion, formaDe } from "./Constelacion";
import { BotonPrincipal, CirculoCategoria } from "./piezas";

/**
 * Lo que rodea al juego (handoff `design_handoff_orion_practica`, §10.2 y §10.8): el inicio del set,
 * con Rigel que saluda y el aviso de que el profe verá cómo le fue; y los dos estados en que no se
 * puede jugar todavía o ya no: preparándose y vencida.
 */

const VOLVER = "/cuenta?seccion=resumen";

function deLaClase(set: SetDePractica): string {
  const profe = nombreDelProfe(set);
  return set.classStartsAt ? `De tu clase del ${diaDeLaClase(set.classStartsAt)} con ${profe}` : `De tu última clase con ${profe}`;
}

export function Inicio({ set, empezando, onEmpezar }: { set: SetDePractica; empezando: boolean; onEmpezar: () => void }) {
  const { data: me } = useMe();
  const minutos = set.estimatedMinutes ?? set.itemCount;
  const titulo = tituloDelSet(set) ?? "Tu práctica";
  const items = [...set.items].sort((a, b) => a.index - b.index);
  const apagadas = items.map(() => "off" as const);
  const forma = formaDe(set.id);
  const nombre = me ? primerNombre(me.fullName) : null;
  const saludo = `¡Hola${nombre ? `, ${nombre}` : ""}! ${MINUTOS[minutos] ?? `${minutos} minutos`} y encendemos tu constelación.`;
  const aviso = `${nombreDelProfe(set, true)} verá cómo te fue, así prepara tu próxima clase.`;
  const pastilla = `${set.itemCount} ejercicios · ${minutos} minutos`;

  const lista = (grande: boolean) => (
    <ul className={`m-0 flex list-none flex-col p-0 ${grande ? "gap-2" : "gap-1.5"}`}>
      {items.map((i) => (
        <li key={i.id} className={`flex items-center ${grande ? "gap-3 text-[15px]" : "gap-2.5 text-[14px]"}`}>
          <CirculoCategoria categoria={i.category} tam={grande ? 32 : 28} />
          <strong>{NOMBRE_DE_CATEGORIA[i.category]}</strong>
          <span className="text-ink-2">{NOMBRE_DEL_TIPO[i.type]}</span>
        </li>
      ))}
    </ul>
  );
  const transparencia = (grande: boolean) => (
    <div
      className={`flex items-center gap-2.5 rounded-pareja bg-lavanda-soft text-[#3E2E63] ${
        grande ? "px-4 py-3.5 text-[15px]" : "px-3.5 py-3 text-[14px] leading-[1.45]"
      }`}
    >
      <Eye size={20} strokeWidth={1.75} className="shrink-0" aria-hidden />
      <span>{aviso}</span>
    </div>
  );

  return (
    <div className="practica flex min-h-dvh flex-col bg-crema text-ink">
      {/* Celular */}
      <div className="flex flex-1 flex-col lg:hidden">
        <div className="flex h-[60px] shrink-0 items-center px-3">
          <Link href={VOLVER} aria-label="Cerrar" className="flex h-11 w-11 items-center justify-center text-ink">
            <X size={22} strokeWidth={1.75} />
          </Link>
        </div>
        <div className="flex flex-1 flex-col gap-4 px-5 pb-6">
          <div className="flex items-end gap-3">
            <Rigel pose="saludo" decorativo className="h-auto w-[104px] shrink-0" />
            <div className="mb-[34px] rounded-[18px_18px_18px_6px] bg-white px-3.5 py-3 text-[15px] leading-[1.4] font-semibold shadow-bubble">
              {saludo}
            </div>
          </div>
          <div className="flex flex-col gap-1.5">
            <h1 className="m-0 font-display text-[30px] leading-[1.1] font-bold">{titulo}</h1>
            <span className="text-[15px] text-ink-2">{deLaClase(set)}</span>
          </div>
          <div className="flex gap-2">
            <span className="inline-flex h-8 items-center rounded-pill border-[1.5px] border-line bg-white px-3 text-[13px] font-bold">
              {pastilla}
            </span>
          </div>
          <div className="flex justify-center rounded-tarjeta bg-white px-3 pt-3.5 pb-2">
            <Constelacion estados={apagadas} forma={forma} ancho={300} r={11} guias etiqueta="Tu constelación, por encender" />
          </div>
          {lista(false)}
          <div className="flex-1" />
          {transparencia(false)}
          <BotonPrincipal cargando={empezando} onClick={onEmpezar} className="w-full">
            Empezar
          </BotonPrincipal>
        </div>
      </div>

      {/* Escritorio */}
      <div className="hidden flex-1 grid-cols-2 items-center gap-10 px-14 py-10 lg:grid">
        <div className="flex h-[640px] flex-col items-center justify-center gap-7 rounded-[28px] bg-noche p-8">
          <Constelacion estados={apagadas} forma={forma} ancho={400} r={12} guias fondo="noche" etiqueta="Tu constelación, por encender" />
          <Rigel pose="saludo" decorativo className="h-auto w-[170px]" />
        </div>
        <div className="flex max-w-[480px] flex-col gap-[18px]">
          <span className="text-[15px] font-semibold text-ink-2">{deLaClase(set)}</span>
          <h1 className="m-0 font-display text-[44px] leading-[1.05] font-bold">{titulo}</h1>
          <span className="inline-flex h-[34px] items-center self-start rounded-pill border-[1.5px] border-line bg-white px-3.5 text-[14px] font-bold">
            {pastilla}
          </span>
          {lista(true)}
          {transparencia(true)}
          <div className="flex items-center gap-3">
            <BotonPrincipal cargando={empezando} onClick={onEmpezar} className="px-10">
              Empezar
            </BotonPrincipal>
            <Link href={VOLVER} className="flex h-14 items-center rounded-pill px-5 text-[16px] font-bold text-ink hover:bg-arena">
              Ahora no
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}

const MINUTOS: Record<number, string> = {
  1: "Un minuto",
  2: "Dos minutos",
  3: "Tres minutos",
  4: "Cuatro minutos",
  5: "Cinco minutos",
  6: "Seis minutos",
  7: "Siete minutos",
  8: "Ocho minutos",
  10: "Diez minutos",
};

/** Preparándose y vencida (§10.8): Rigel, la constelación atenuada y a dónde ir. */
export function EstadoDelSet({ set }: { set: SetDePractica }) {
  const preparando = set.status === "PENDING";
  const profe = nombreDelProfe(set);
  const titulo = preparando ? "Estamos preparando tu práctica" : "Esta práctica ya venció";
  const cuerpo = preparando
    ? `Sale del acta que ${profe} acaba de publicar. Tarda cerca de un minuto; puedes quedarte aquí o volver luego.`
    : `Era para la semana del ${set.classStartsAt ? diaDeLaClase(set.classStartsAt) : "tu última clase"}. Tu próxima clase con ${profe} trae una nueva.`;
  const apagadas = Array.from({ length: Math.max(set.itemCount, 5) }, () => "off" as const).slice(0, 5);
  const forma = formaDe(set.id);
  const pose = preparando ? "espera" : "animo";
  const opacidad = preparando ? 0.55 : 0.4;

  const estado = preparando && (
    <span role="status" className="flex items-center gap-2 text-[14px] font-bold text-lavanda-ink lg:text-[15px]">
      <span className="pr-late h-2 w-2 rounded-full bg-lavanda" aria-hidden />
      Preparando…
    </span>
  );
  const botones = (ancho: boolean) =>
    preparando ? (
      <Link
        href={VOLVER}
        className={`flex h-14 items-center justify-center rounded-pill border-[1.5px] border-ink bg-white px-7 text-[16px] font-bold text-ink hover:bg-arena ${ancho ? "self-stretch" : ""}`}
      >
        Volver a mi perfil
      </Link>
    ) : (
      <div className={`flex gap-2.5 ${ancho ? "flex-col self-stretch" : ""}`}>
        <Link
          href={VOLVER}
          className="flex h-14 items-center justify-center rounded-pill bg-coral px-7 text-[16px] font-bold text-crema shadow-cta hover:bg-coral-hover"
        >
          Volver a mi perfil
        </Link>
        {set.bookingId && (
          <Link
            href={`/mis-clases/${set.bookingId}/acta`}
            className={`flex items-center justify-center rounded-pill px-5 font-bold text-ink hover:bg-arena ${ancho ? "h-12 text-[15px]" : "h-14 text-[16px]"}`}
          >
            Ver el resumen de la clase
          </Link>
        )}
      </div>
    );

  return (
    <div className="practica flex min-h-dvh flex-col bg-crema text-ink">
      <div className="flex flex-1 flex-col lg:hidden">
        <div className="flex h-[60px] shrink-0 items-center px-3">
          <Link href={VOLVER} aria-label="Volver" className="flex h-11 w-11 items-center justify-center text-ink">
            <ChevronLeft size={22} strokeWidth={1.75} />
          </Link>
        </div>
        <div className="flex flex-1 flex-col items-center justify-center gap-[18px] px-6 pb-7 text-center">
          <div style={{ opacity: opacidad }}>
            <Constelacion estados={apagadas} forma={forma} ancho={260} r={10} guias etiqueta="Constelación apagada" />
          </div>
          <Rigel pose={pose} decorativo className="h-auto w-[140px]" />
          <h1 className="m-0 font-display text-[28px] leading-[1.1] font-bold">{titulo}</h1>
          <p className="m-0 text-[16px] leading-[1.55] text-pretty text-ink-2">{cuerpo}</p>
          {estado}
          <div className="flex-1" />
          {botones(true)}
        </div>
      </div>
      <div className="hidden flex-1 items-center justify-center gap-12 p-10 lg:flex">
        <Rigel pose={pose} decorativo className="h-auto w-[220px]" />
        <div className="flex max-w-[460px] flex-col gap-4">
          <div style={{ opacity: opacidad }}>
            <Constelacion estados={apagadas} forma={forma} ancho={280} r={10} guias etiqueta="Constelación apagada" />
          </div>
          <h1 className="m-0 font-display text-[40px] leading-[1.05] font-bold">{titulo}</h1>
          <p className="m-0 text-[17px] leading-[1.55] text-ink-2">{cuerpo}</p>
          {estado}
          {botones(false)}
        </div>
      </div>
    </div>
  );
}
