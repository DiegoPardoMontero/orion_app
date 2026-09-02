"use client";

import { useQuery } from "@tanstack/react-query";
import { ArrowRight, Menu, X } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { apiFetch } from "@/lib/api/fetch";
import { HOME_BY_ROLE } from "@/lib/auth/roles";
import { type Me } from "@/lib/auth/session";
import { Wordmark } from "./marca";

/**
 * Cabecera pública del marketplace. Isla cliente porque cambia según la sesión: con sesión ofrece
 * "Ir a mi panel"; sin ella, "Iniciar sesión" + "Crear cuenta". Consulta /auth/me de forma OPCIONAL
 * (401 = anónimo, sin redirigir) reusando la misma queryKey que `HeroCta` para no pedirlo dos veces.
 *
 * "Encuentra un profesor" lleva a /profesores (el marketplace vive en la zona autenticada: un
 * anónimo cae en /login). Las demás entradas son anclas de la portada, salvo "Enseña en Orión".
 */
const ENLACES = [
  { href: "/profesores", label: "Encuentra un profesor" },
  { href: "/#idiomas", label: "Idiomas" },
  { href: "/#como-funciona", label: "Cómo funciona" },
  { href: "/ensena-con-orion", label: "Enseña en Orión" },
  { href: "/#nosotros", label: "Nosotros" },
];

export function NavPublica() {
  const [abierto, setAbierto] = useState(false);

  const { data: me } = useQuery({
    queryKey: ["auth", "me", "landing"],
    queryFn: () => apiFetch<Me>("/api/v1/auth/me", { redirectOn401: false }),
    retry: false,
    staleTime: 60_000,
  });

  return (
    <header className="sticky top-0 z-40 border-b border-border/70 bg-surface/90 backdrop-blur-md">
      <nav className="mx-auto flex h-16 max-w-6xl items-center justify-between gap-4 px-5 lg:px-8">
        <div className="flex items-center gap-7">
          <Link href="/" aria-label="Ir al inicio de Orión">
            <Wordmark className="text-[18px] text-primary" />
          </Link>
          <ul className="hidden items-center gap-1 lg:flex">
            {ENLACES.map((enlace) => (
              <li key={enlace.href}>
                <Link
                  href={enlace.href}
                  className="rounded-pill px-3 py-2 text-[14px] font-semibold text-text-secondary transition-colors hover:bg-surface-sunken hover:text-text"
                >
                  {enlace.label}
                </Link>
              </li>
            ))}
          </ul>
        </div>

        {/* Acciones de sesión (desktop) */}
        <div className="hidden items-center gap-2 lg:flex">
          {me ? (
            <Link href={HOME_BY_ROLE[me.role]} className={PRIMARIO}>
              Ir a mi panel
              <ArrowRight size={16} strokeWidth={2} />
            </Link>
          ) : (
            <>
              <Link
                href="/login"
                className="rounded-pill px-4 py-2 text-[14px] font-bold text-text-secondary transition-colors hover:text-text"
              >
                Iniciar sesión
              </Link>
              <Link href="/registro" className={PRIMARIO}>
                Crear cuenta
              </Link>
            </>
          )}
        </div>

        {/* Botón de menú (móvil) */}
        <button
          type="button"
          aria-label={abierto ? "Cerrar menú" : "Abrir menú"}
          aria-expanded={abierto}
          onClick={() => setAbierto((v) => !v)}
          className="grid h-11 w-11 place-items-center rounded-full text-text transition-colors hover:bg-surface-sunken focus-visible:shadow-focus lg:hidden"
        >
          {abierto ? <X size={22} strokeWidth={1.9} /> : <Menu size={22} strokeWidth={1.9} />}
        </button>
      </nav>

      {/* Panel móvil desplegable */}
      {abierto && (
        <div className="anim-rise border-t border-border/70 bg-surface px-5 py-4 lg:hidden">
          <ul className="flex flex-col gap-1">
            {ENLACES.map((enlace) => (
              <li key={enlace.href}>
                <Link
                  href={enlace.href}
                  onClick={() => setAbierto(false)}
                  className="block rounded-base px-3 py-3 text-[15px] font-semibold text-text transition-colors hover:bg-surface-sunken"
                >
                  {enlace.label}
                </Link>
              </li>
            ))}
          </ul>
          <div className="mt-3 flex flex-col gap-2 border-t border-border/70 pt-4">
            {me ? (
              <Link
                href={HOME_BY_ROLE[me.role]}
                onClick={() => setAbierto(false)}
                className={`${PRIMARIO} justify-center`}
              >
                Ir a mi panel
                <ArrowRight size={16} strokeWidth={2} />
              </Link>
            ) : (
              <>
                <Link
                  href="/login"
                  onClick={() => setAbierto(false)}
                  className="inline-flex h-12 items-center justify-center rounded-pill border-[1.5px] border-border px-5 text-[14px] font-bold text-text transition-colors hover:bg-surface-sunken"
                >
                  Iniciar sesión
                </Link>
                <Link
                  href="/registro"
                  onClick={() => setAbierto(false)}
                  className={`${PRIMARIO} justify-center`}
                >
                  Crear cuenta
                </Link>
              </>
            )}
          </div>
        </div>
      )}
    </header>
  );
}

const PRIMARIO =
  "inline-flex h-11 items-center gap-1.5 rounded-pill bg-primary px-5 text-[14px] font-bold text-on-primary shadow-primary transition-colors hover:bg-primary-strong focus-visible:shadow-focus";
