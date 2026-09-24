"use client";

import { useQuery } from "@tanstack/react-query";
import { ArrowRight, X } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState, useSyncExternalStore } from "react";
import { Rigel } from "@/components/Rigel";
import { apiFetch } from "@/lib/api/fetch";

/**
 * La franja de Rigel para el profesor aprobado que todavía no recibe estudiantes (24/09/2026: «lo
 * mismo que con la ficha del estudiante, hasta que llene su disponibilidad, complete su perfil y lo
 * publique»). En todas las pantallas salvo en su propio perfil y el aula; se cierra por un día. Lo que
 * falta lo dice el backend, que es el mismo que manda los avisos y el correo.
 */
const EN_PALABRAS: Record<string, string> = {
  FOTO: "tu foto",
  TITULAR: "tu titular",
  DESCRIPCION: "tu descripción",
  TARIFA: "tu tarifa",
  IDIOMAS: "los idiomas que enseñas",
  HORARIOS: "tus horarios",
  PUBLICAR: "publicar tu perfil",
};

export function FranjaDelPerfil() {
  const pathname = usePathname();
  const pendiente = useQuery({
    queryKey: ["me", "profile", "pending"],
    queryFn: () => apiFetch<{ missing: string[] }>("/api/v1/me/profile/pending"),
    staleTime: 60_000,
  });
  const cerradaHoy = useSyncExternalStore(suscribir, estaCerrada, () => true);
  const [cerradaAhora, setCerradaAhora] = useState(false);

  const faltan = pendiente.data?.missing ?? [];
  if (faltan.length === 0 || cerradaAhora || cerradaHoy) return null;
  if (pathname.startsWith("/perfil") || /\/aula$/.test(pathname)) return null;

  // Publicado y con horarios ya recibe reservas: lo que falta (la foto, por ejemplo) lo mejora, no lo bloquea.
  const publicado = !faltan.includes("PUBLICAR") && !faltan.includes("HORARIOS");
  const soloHorarios = faltan.length === 1 && faltan[0] === "HORARIOS";
  const destino = soloHorarios ? "/perfil?seccion=horarios" : "/perfil";
  const lista = faltan.map((f) => EN_PALABRAS[f] ?? f);

  const cerrar = () => {
    try {
      window.localStorage.setItem(CLAVE, String(Date.now() + UN_DIA));
    } catch {
      // Sin almacenamiento se cierra solo en esta visita.
    }
    setCerradaAhora(true);
  };

  return (
    <aside
      aria-label="Completa tu perfil"
      className="mx-5 mt-3 flex items-center gap-3 rounded-card bg-rigel-soft py-2.5 pl-3 pr-2 lg:mx-12 lg:mt-5"
    >
      <Rigel pose="guia" decorativo className="h-auto w-11 shrink-0" />
      <p className="min-w-0 flex-1 text-[13px] leading-snug text-rigel-ink">
        <strong>{publicado ? "Termina tu perfil." : "Tu perfil todavía no recibe estudiantes."}</strong>{" "}
        <span className="hidden sm:inline">
          Te falta {lista.join(", ")}.{" "}
          {publicado
            ? "Un perfil completo da confianza y recibe más reservas."
            : "Con tus horarios abiertos y tu perfil completo y publicado, apareces en el buscador."}
        </span>
        <span className="sm:hidden">Te falta {lista.length === 1 ? lista[0] : `${lista.length} cosas`}.</span>
      </p>
      <Link
        href={destino}
        className="inline-flex min-h-11 shrink-0 items-center gap-1.5 rounded-pill bg-primary px-4 text-[13px] font-bold text-on-primary shadow-primary transition-colors hover:bg-primary-strong focus-visible:shadow-focus"
      >
        <span className="hidden sm:inline">Terminar mi perfil</span>
        <span className="sm:hidden">Terminar</span>
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

const CLAVE = "orion.franja-perfil.cerrada-hasta";
const UN_DIA = 24 * 60 * 60 * 1000;

function suscribir(): () => void {
  return () => {};
}

function estaCerrada(): boolean {
  try {
    return Date.now() < (Number(window.localStorage.getItem(CLAVE) ?? 0) || 0);
  } catch {
    return false;
  }
}
