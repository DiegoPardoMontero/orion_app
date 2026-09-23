"use client";

import { ArrowRight, CalendarDays, LockOpen, Mic, Share2, Sparkles, Target, UserRoundSearch, Wallet, type LucideIcon } from "lucide-react";
import Link from "next/link";
import { useId, useState } from "react";
import { EnsenaCta } from "@/components/EnsenaCta";

type Razon = { icono: LucideIcon; titulo: string; texto: string };

/**
 * «Aquí las reglas juegan a tu favor» (Sofía, 23/09/2026): cuatro razones para cada lado, en dos
 * pestañas. Cada tarjeta es un ícono, una frase y una línea; nada más. Todo lo que dicen lo hace la
 * plataforma hoy: no hay paquetes (se paga clase por clase), el profesor ve el punto de partida y
 * el objetivo de su estudiante, y el enlace del perfil se abre sin cuenta.
 */
const APRENDER: Razon[] = [
  { icono: LockOpen, titulo: "Nada se cobra solo.", texto: "Pagas clase por clase, sin suscripción. Tú decides, siempre." },
  { icono: Target, titulo: "Tu profe ya sabe quién eres.", texto: "Llega conociendo tu punto de partida y tu objetivo." },
  {
    icono: UserRoundSearch,
    titulo: "Eliges con quién, cuándo y por cuánto.",
    texto: "Todos los profesores están verificados.",
  },
  { icono: Sparkles, titulo: "Cada clase te deja tarea con sentido.", texto: "Ejercicios hechos para ti, no genéricos." },
];

const ENSENAR: Razon[] = [
  { icono: Wallet, titulo: "Tu tarifa, tu comisión a la vista.", texto: "Sabes cuánto recibes desde el día uno." },
  { icono: CalendarDays, titulo: "Tus horarios, tus reglas.", texto: "Sin mínimos ni permanencia." },
  {
    icono: Mic,
    titulo: "Método ORION™: un minuto de audio y tu seguimiento está listo.",
    texto: "Dictas tus observaciones al terminar, revisas el resumen que armamos y de ahí salen los ejercicios de tu estudiante.",
  },
  {
    icono: Share2,
    titulo: "Tu propio enlace para traer estudiantes.",
    texto: "Tu perfil tiene su página: compártela en Instagram, WhatsApp o tu hoja de vida, y quien llegue puede escribirte y reservar contigo.",
  },
];

const PESTANAS = [
  { id: "aprender", etiqueta: "Quiero aprender", razones: APRENDER },
  { id: "ensenar", etiqueta: "Quiero enseñar", razones: ENSENAR },
] as const;

export function PorQueOrion() {
  const [activa, setActiva] = useState<"aprender" | "ensenar">("aprender");
  const base = useId();
  const pestana = PESTANAS.find((p) => p.id === activa)!;

  return (
    <div>
      <div role="tablist" aria-label="Por qué Orión" className="mx-auto flex w-fit gap-1 rounded-pill bg-surface-sunken p-1">
        {PESTANAS.map((p) => (
          <button
            key={p.id}
            type="button"
            role="tab"
            id={`${base}-${p.id}`}
            aria-selected={p.id === activa}
            aria-controls={`${base}-panel`}
            onClick={() => setActiva(p.id)}
            className={`min-h-11 rounded-pill px-5 text-[14.5px] font-bold transition-colors focus-visible:shadow-focus ${
              p.id === activa ? "bg-surface-raised text-text shadow-sm" : "text-text-secondary hover:text-text"
            }`}
          >
            {p.etiqueta}
          </button>
        ))}
      </div>

      <div role="tabpanel" id={`${base}-panel`} aria-labelledby={`${base}-${activa}`} className="mt-7">
        <ul className="grid gap-3 sm:grid-cols-2">
          {pestana.razones.map((razon) => {
            const Icono = razon.icono;
            return (
              <li key={razon.titulo} className="flex items-start gap-4 rounded-card bg-surface-raised p-5 shadow-sm">
                <span className="grid h-11 w-11 shrink-0 place-items-center rounded-base bg-accent-peach-soft text-[#8a5a33]">
                  <Icono size={20} strokeWidth={1.9} />
                </span>
                <div>
                  <p className="font-display text-[16px] font-bold leading-snug">{razon.titulo}</p>
                  <p className="mt-1 text-[14px] leading-relaxed text-text-secondary">{razon.texto}</p>
                </div>
              </li>
            );
          })}
        </ul>
        <div className="mt-7 flex justify-center">
          {activa === "aprender" ? (
            <Link
              href="/profesores"
              className="inline-flex h-[52px] items-center justify-center gap-2 rounded-pill bg-primary px-7 text-[15px] font-bold text-on-primary shadow-primary transition-colors hover:bg-primary-strong focus-visible:shadow-focus"
            >
              Encuentra tu profesor
              <ArrowRight size={18} strokeWidth={1.9} />
            </Link>
          ) : (
            <EnsenaCta etiqueta="Postúlate como profesor" />
          )}
        </div>
      </div>
    </div>
  );
}
