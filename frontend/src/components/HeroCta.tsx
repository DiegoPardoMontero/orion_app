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

const PRIMARIO =
  "inline-flex h-[52px] items-center justify-center gap-2 rounded-pill bg-primary px-7 text-[15px] font-bold text-on-primary shadow-primary transition-colors hover:bg-primary-strong focus-visible:shadow-focus";
const SECUNDARIO =
  "inline-flex h-[52px] items-center justify-center rounded-pill border-[1.5px] border-on-primary/40 px-7 text-[15px] font-bold text-on-primary transition-colors hover:bg-on-primary/10 focus-visible:shadow-focus";
