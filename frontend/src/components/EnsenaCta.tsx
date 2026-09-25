"use client";

import { useQuery } from "@tanstack/react-query";
import { ArrowRight } from "lucide-react";
import Link from "next/link";
import { apiFetch } from "@/lib/api/fetch";
import { type Me } from "@/lib/auth/session";

/**
 * CTA de "Enseña en Orión". Isla cliente que decide el destino según la sesión: el aspirante o el
 * profesor van directo al wizard de postulación (/aplicacion); sin sesión, al registro con
 * `?rol=profesor`, que ajusta el copy y deja al recién registrado en su postulación en vez de en el
 * buscador. Postularse siempre exige cuenta: la postulación cuelga de un usuario.
 *
 * <p>Con la sesión de un estudiante no hay botón (Pardo, 25/09/2026): desde esa cuenta no se postula,
 * y un enlace al wizard terminaría en un 403. Se le dice cómo, que es con otra cuenta.
 */
export function EnsenaCta({ className = "", etiqueta }: { className?: string; etiqueta?: string }) {
  const { data: me } = useQuery({
    queryKey: ["auth", "me", "landing"],
    queryFn: () => apiFetch<Me>("/api/v1/auth/me", { redirectOn401: false }),
    retry: false,
    staleTime: 60_000,
  });

  if (me?.role === "STUDENT") {
    return (
      // Sobre superficie propia: la CTA vive tanto en el amanecer como en fondo claro.
      <p className={`max-w-[46ch] rounded-card bg-surface px-5 py-3.5 text-left text-[14px] leading-relaxed text-text shadow-sm ${className}`}>
        Estás con tu cuenta de estudiante, y desde ella no se postula. Para enseñar en Orión, crea una cuenta
        aparte con otro correo desde «Quiero enseñar».
      </p>
    );
  }

  const href = me ? "/aplicacion" : "/registro?rol=profesor";
  const texto = me ? "Empieza tu postulación" : (etiqueta ?? "Crea tu cuenta y postúlate");

  return (
    <Link
      href={href}
      className={`inline-flex h-[52px] w-full items-center justify-center gap-2 rounded-pill bg-primary px-7 text-[15px] font-bold text-on-primary shadow-primary transition-colors hover:bg-primary-strong focus-visible:shadow-focus sm:w-auto sm:min-w-[236px] ${className}`}
    >
      {texto}
      <ArrowRight size={18} strokeWidth={1.9} />
    </Link>
  );
}
