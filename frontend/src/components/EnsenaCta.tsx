"use client";

import { useQuery } from "@tanstack/react-query";
import { ArrowRight } from "lucide-react";
import Link from "next/link";
import { apiFetch } from "@/lib/api/fetch";
import { type Me } from "@/lib/auth/session";

/**
 * CTA de "Enseña en Orión". Isla cliente que decide el destino según la sesión: con sesión lleva
 * directo al wizard de postulación (/aplicacion); sin sesión, al registro con `?rol=profesor`, que
 * ajusta el copy y deja al recién registrado en su postulación en vez de en el buscador. Postularse
 * siempre exige cuenta: la postulación cuelga de un usuario.
 */
export function EnsenaCta({ className = "" }: { className?: string }) {
  const { data: me } = useQuery({
    queryKey: ["auth", "me", "landing"],
    queryFn: () => apiFetch<Me>("/api/v1/auth/me", { redirectOn401: false }),
    retry: false,
    staleTime: 60_000,
  });

  const href = me ? "/aplicacion" : "/registro?rol=profesor";
  const texto = me ? "Empieza tu postulación" : "Crea tu cuenta y postúlate";

  return (
    <Link
      href={href}
      className={`inline-flex h-[52px] items-center justify-center gap-2 rounded-pill bg-primary px-7 text-[15px] font-bold text-on-primary shadow-primary transition-colors hover:bg-primary-strong focus-visible:shadow-focus ${className}`}
    >
      {texto}
      <ArrowRight size={18} strokeWidth={1.9} />
    </Link>
  );
}
