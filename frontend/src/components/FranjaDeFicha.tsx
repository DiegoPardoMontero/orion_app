"use client";

import { useQuery } from "@tanstack/react-query";
import { ArrowRight, X } from "lucide-react";
import Link from "next/link";
import { usePathname, useSearchParams } from "next/navigation";
import { useState, useSyncExternalStore } from "react";
import { Rigel } from "@/components/Rigel";
import { apiFetch } from "@/lib/api/fetch";
import type { FichaEstudiante } from "@/lib/gamificacion";
import { faltanDeLaFicha, PUNTOS_FICHA_COMPLETA } from "@/lib/ficha";

/**
 * La franja de Rigel que pide completar la ficha (decisión de Pardo, 24/09/2026): en todas las
 * pantallas del estudiante hasta que la complete, con el logro que gana al hacerlo. Se puede cerrar,
 * pero solo por un día. Relativamente insistente a propósito: con la ficha completa los profes
 * preparan mejor la clase, y eso es lo que el estudiante vino a buscar.
 *
 * <p>No sale en la propia ficha (ya está ahí), ni en los flujos de pantalla completa.
 */
export function FranjaDeFicha() {
  const pathname = usePathname();
  const seccion = useSearchParams().get("seccion");
  const ficha = useQuery({
    queryKey: ["me", "student-profile"],
    queryFn: () => apiFetch<FichaEstudiante>("/api/v1/me/student-profile"),
    staleTime: 60_000,
  });
  // En el servidor, cerrada: mejor que aparezca después que un parpadeo que se va.
  const cerradaHoy = useSyncExternalStore(suscribir, estaCerrada, () => true);
  const [cerradaAhora, setCerradaAhora] = useState(false);

  if (!ficha.data || cerradaAhora || cerradaHoy) return null;
  if (pathname === "/cuenta" && (seccion === "ficha" || seccion === "datos")) return null;
  if (/^\/practica\//.test(pathname) || /\/aula$/.test(pathname)) return null;

  const faltan = faltanDeLaFicha(ficha.data);
  if (faltan.length === 0) return null;

  const cerrar = () => {
    try {
      window.localStorage.setItem(CLAVE, String(Date.now() + UN_DIA));
    } catch {
      // Sin almacenamiento se cierra solo en esta visita: vuelve la próxima vez. Está bien.
    }
    setCerradaAhora(true);
  };

  return (
    <aside
      aria-label="Completa tu ficha"
      className="mx-5 mt-3 flex items-center gap-3 rounded-card bg-rigel-soft py-2.5 pr-2 pl-3 lg:mx-12 lg:mt-5"
    >
      <Rigel pose="guia" decorativo className="h-auto w-11 shrink-0" />
      <p className="min-w-0 flex-1 text-[13px] leading-snug text-rigel-ink">
        <strong>Completa tu ficha y gana «Ficha completa» (+{PUNTOS_FICHA_COMPLETA} puntos).</strong>{" "}
        <span className="hidden sm:inline">
          Te falta {faltan.join(", ")}. Con tu ficha completa —y visible, te lo recomiendo— los profes preparan tu
          clase sabiendo qué buscas.
        </span>
        <span className="sm:hidden">Te falta {faltan.length === 1 ? faltan[0] : `${faltan.length} cosas`}.</span>
      </p>
      <Link
        href="/cuenta?seccion=ficha"
        className="inline-flex min-h-11 shrink-0 items-center gap-1.5 rounded-pill bg-primary px-4 text-[13px] font-bold text-on-primary shadow-primary transition-colors hover:bg-primary-strong focus-visible:shadow-focus"
      >
        <span className="hidden sm:inline">Completar mi ficha</span>
        <span className="sm:hidden">Completar</span>
        <ArrowRight size={15} strokeWidth={2} aria-hidden />
      </Link>
      <button
        type="button"
        onClick={cerrar}
        aria-label="Recuérdamelo mañana"
        title="Recuérdamelo mañana"
        className="flex h-11 w-11 shrink-0 cursor-pointer items-center justify-center rounded-full text-rigel-ink hover:bg-rigel/30 focus-visible:shadow-focus"
      >
        <X size={18} strokeWidth={2} />
      </button>
    </aside>
  );
}

const CLAVE = "orion.franja-ficha.cerrada-hasta";
const UN_DIA = 24 * 60 * 60 * 1000;

function suscribir(): () => void {
  return () => {};
}

/** Si «Recuérdamelo mañana» sigue vigente. */
function estaCerrada(): boolean {
  try {
    return Date.now() < (Number(window.localStorage.getItem(CLAVE) ?? 0) || 0);
  } catch {
    return false;
  }
}
