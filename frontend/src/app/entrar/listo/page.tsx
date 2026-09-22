"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api/fetch";
import { destinoAlEntrar } from "@/lib/auth/roles";
import type { Me } from "@/lib/auth/session";
import { Wordmark } from "@/components/marca";

/**
 * La vuelta de Google, Apple o Facebook para quien ya tenía cuenta. El backend ya abrió la sesión;
 * aquí solo se decide a dónde va, con la misma regla que el login con contraseña. Esta primera
 * petición autenticada es además la que muda a la cuenta el diagnóstico hecho sin ella.
 */
export default function EntrarListoPage() {
  const router = useRouter();
  const me = useQuery({
    queryKey: ["auth", "me", "entrar"],
    queryFn: () => apiFetch<Me>("/api/v1/auth/me", { redirectOn401: false }),
    retry: false,
  });

  useEffect(() => {
    if (me.data) router.replace(destinoAlEntrar(me.data.role));
    if (me.isError) router.replace("/login?social=error");
  }, [me.data, me.isError, router]);

  return (
    <main className="flex min-h-dvh flex-col items-center justify-center gap-3 px-6 text-center">
      <Wordmark className="text-[18px] text-primary" />
      <p role="status" className="text-[14px] text-text-secondary">
        Entrando…
      </p>
    </main>
  );
}
