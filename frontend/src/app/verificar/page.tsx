"use client";

import { CheckCircle2, XCircle } from "lucide-react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useEffect, useRef, useState } from "react";
import { Constelacion, Wordmark } from "@/components/marca";
import { Rigel } from "@/components/Rigel";
import { BotonPrincipal, Spinner } from "@/components/ui";
import { apiFetch, ApiError } from "@/lib/api/fetch";

export default function VerificarPage() {
  // useSearchParams exige una frontera de Suspense.
  return (
    <Suspense fallback={null}>
      <Verificar />
    </Suspense>
  );
}

/**
 * El destino del enlace del correo. Se llega desde el buzón, muchas veces en otro navegador y sin
 * sesión: por eso el endpoint es público y esta pantalla no vive dentro de la app.
 */
function Verificar() {
  const token = useSearchParams().get("token");
  // Un enlace sin código no es un estado que haya que descubrir: se sabe en el primer render, así
  // que se decide aquí y no dentro de un efecto.
  const [estado, setEstado] = useState<"verificando" | "listo" | "error">(
    token ? "verificando" : "error",
  );
  const [mensaje, setMensaje] = useState(
    token ? "" : "Al enlace le falta el código. Ábrelo de nuevo desde el correo.",
  );
  // En desarrollo, React monta dos veces; sin esto el token se consumiría en el primer montaje y
  // el segundo mostraría "ya expiró" sobre una verificación que sí funcionó.
  const yaIntentado = useRef(false);

  useEffect(() => {
    if (!token || yaIntentado.current) return;
    yaIntentado.current = true;

    apiFetch<void>("/api/v1/auth/verify-email", { method: "POST", body: { token } })
      .then(() => setEstado("listo"))
      .catch((error) => {
        setEstado("error");
        setMensaje(
          error instanceof ApiError
            ? error.message
            : "No pudimos confirmar tu correo. Intenta de nuevo en un momento.",
        );
      });
  }, [token]);

  return (
    <main className="grid min-h-dvh place-items-center bg-surface px-6 py-12">
      <div className="w-full max-w-md text-center">
        <Constelacion className="pointer-events-none absolute left-6 top-8 h-[120px] w-[120px] opacity-[0.15]" />
        <Wordmark className="mx-auto text-[18px] text-primary" />

        {estado === "verificando" && (
          <div className="mt-10">
            <Spinner />
            <p className="mt-4 text-[15px] text-text-secondary">Confirmando tu correo…</p>
          </div>
        )}

        {estado === "listo" && (
          <>
            <Rigel pose="saludo" decorativo className="mx-auto mt-8 h-[150px] w-auto" />
            <CheckCircle2 size={36} strokeWidth={1.75} className="mx-auto mt-4 text-success" />
            <h1 className="mt-3 font-display text-[26px] font-bold">¡Listo, tu correo quedó confirmado!</h1>
            <p className="mt-2 text-[15px] leading-relaxed text-text-secondary">
              Ya puedes reservar tus clases. Te enviaremos aquí las confirmaciones y las
              invitaciones de calendario.
            </p>
            <Link href="/profesores" className="mt-7 block">
              <BotonPrincipal type="button">Buscar un profesor</BotonPrincipal>
            </Link>
          </>
        )}

        {estado === "error" && (
          <>
            <XCircle size={36} strokeWidth={1.75} className="mx-auto mt-10 text-error" />
            <h1 className="mt-3 font-display text-[24px] font-bold">No pudimos confirmar tu correo</h1>
            <p className="mt-2 text-[15px] leading-relaxed text-text-secondary">{mensaje}</p>
            <p className="mt-5 text-[14px] text-text-muted">
              Entra a Orión y pide un enlace nuevo desde el aviso de tu perfil.
            </p>
            <Link href="/login" className="mt-6 block">
              <BotonPrincipal type="button">Ir a Orión</BotonPrincipal>
            </Link>
          </>
        )}
      </div>
    </main>
  );
}
