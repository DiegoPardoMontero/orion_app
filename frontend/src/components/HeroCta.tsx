"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { apiFetch } from "@/lib/api/fetch";
import { HOME_BY_ROLE } from "@/lib/auth/roles";
import { type Me } from "@/lib/auth/session";

/**
 * La fila de acciones secundarias del hero, debajo del diagnóstico. Consulta la sesión de forma
 * OPCIONAL (401 no redirige, ver `redirectOn401`): con sesión ofrece "Ir a mi panel"; sin ella,
 * "Crea tu cuenta" y "Ya tengo cuenta". "Ver profesores" va siempre. Es una isla cliente dentro de
 * una página por lo demás estática.
 *
 * <p>Todos en una fila y del mismo ancho: son alternativas del mismo peso, ninguna compite con el
 * diagnóstico, que es la acción grande de encima.
 */
export function HeroCta() {
  const { data: me } = useQuery({
    queryKey: ["auth", "me", "landing"],
    queryFn: () => apiFetch<Me>("/api/v1/auth/me", { redirectOn401: false }),
    retry: false,
    staleTime: 60_000,
  });

  return (
    <div className={`grid gap-2 ${me ? "grid-cols-2" : "grid-cols-3"}`}>
      <Link href="/profesores" className={BOTON}>
        Ver profesores
      </Link>
      {me ? (
        <Link href={HOME_BY_ROLE[me.role]} className={BOTON}>
          Ir a mi panel
        </Link>
      ) : (
        <>
          <Link href="/registro" className={BOTON}>
            Crea tu cuenta
          </Link>
          <Link href="/login" className={BOTON}>
            Ya tengo cuenta
          </Link>
        </>
      )}
    </div>
  );
}

/** 44 px de alto: el mínimo táctil, y visiblemente menor que los 60 del diagnóstico. */
const BOTON =
  "inline-flex h-11 min-w-0 items-center justify-center whitespace-nowrap rounded-pill border-[1.5px] border-on-primary/45 px-2 text-[13px] font-bold text-on-primary transition-colors hover:bg-on-primary/10 focus-visible:shadow-focus sm:text-[14px]";
