"use client";

import { useQuery } from "@tanstack/react-query";
import { ArrowRight } from "lucide-react";
import Link from "next/link";
import { apiFetch } from "@/lib/api/fetch";
import { HOME_BY_ROLE } from "@/lib/auth/roles";
import { type Me } from "@/lib/auth/session";

/**
 * CTA del hero de la landing. Consulta la sesión de forma OPCIONAL (401 no redirige, ver
 * `redirectOn401`): con sesión ofrece "Ir a mi panel"; sin ella, "Crea tu cuenta" + "Ya tengo
 * cuenta". Es una isla cliente dentro de una página por lo demás estática.
 */
export function HeroCta() {
  const { data: me } = useQuery({
    queryKey: ["auth", "me", "landing"],
    queryFn: () => apiFetch<Me>("/api/v1/auth/me", { redirectOn401: false }),
    retry: false,
    staleTime: 60_000,
  });

  if (me) {
    return (
      <div className="flex flex-col gap-3 sm:flex-row">
        <Link href={HOME_BY_ROLE[me.role]} className={PRIMARIO}>
          Ir a mi panel
          <ArrowRight size={18} strokeWidth={1.75} />
        </Link>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-3 sm:flex-row">
      <Link href="/registro" className={PRIMARIO}>
        Crea tu cuenta
        <ArrowRight size={18} strokeWidth={1.75} />
      </Link>
      <Link href="/login" className={SECUNDARIO}>
        Ya tengo cuenta
      </Link>
    </div>
  );
}

/**
 * El mismo alto y el mismo ancho mínimo en los dos: en escritorio, dos botones juntos con textos de
 * distinto largo quedaban de tamaños distintos y el par se veía torcido. `min-w` en vez de un ancho
 * fijo para que un texto más largo pueda crecer en vez de recortarse.
 */
const BASE =
  "inline-flex h-[52px] w-full items-center justify-center rounded-pill px-7 text-[15px] font-bold transition-colors focus-visible:shadow-focus sm:w-auto sm:min-w-[236px]";
const PRIMARIO =
  `${BASE} gap-2 bg-primary text-on-primary shadow-primary hover:bg-primary-strong`;
const SECUNDARIO =
  `${BASE} border-[1.5px] border-on-primary/40 text-on-primary hover:bg-on-primary/10`;
