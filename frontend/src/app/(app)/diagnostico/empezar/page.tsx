"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useMutation, useQuery } from "@tanstack/react-query";
import { apiFetch, ApiError } from "@/lib/api/fetch";
import type { Diagnostico, DiagnosticoIniciado } from "@/lib/api/diagnostico";
import { useCifras } from "@/lib/cifras";
import { AvisoError, Cargando } from "@/components/estados";
import { Meissa } from "@/components/Meissa";
import { Conversacion } from "./Conversacion";
import { Puerta } from "./Puerta";
import { Resultado } from "./Resultado";

type Fase = "puerta" | "conversando" | "calculando" | "resultado";

/**
 * El diagnóstico de confianza, de principio a fin y sin cambiar de dirección.
 *
 * <p>Las cuatro fases viven aquí porque la conversación no puede sobrevivir a una navegación: el
 * micrófono, la conexión con el proveedor y los turnos que se van empujando se perderían al cambiar
 * de ruta, y la persona se quedaría a medias sin entender por qué.
 *
 * <p>La espera del resultado tiene pantalla propia, con Meissa y sin spinner genérico. Son unos
 * segundos en los que alguien acaba de exponerse hablando un idioma que no domina; un círculo
 * girando dice «el sistema está ocupado» y lo que hay que decir es «lo estamos leyendo».
 */
export default function DiagnosticoPage() {
  const cifras = useCifras();
  const router = useRouter();
  const [fase, setFase] = useState<Fase>("puerta");
  const [sesion, setSesion] = useState<DiagnosticoIniciado | null>(null);
  const [objetivos, setObjetivos] = useState<string[]>([]);
  const [resultado, setResultado] = useState<Diagnostico | null>(null);

  const empezar = useMutation({
    mutationFn: (metas: string[]) =>
      apiFetch<DiagnosticoIniciado>("/api/v1/assessments", {
        method: "POST",
        body: { languageCode: "EN", goals: metas },
      }),
    onSuccess: (iniciada) => {
      setSesion(iniciada);
      setFase("conversando");
    },
  });

  const cerrar = useMutation({
    mutationFn: () =>
      apiFetch<Diagnostico>(`/api/v1/assessments/${sesion?.assessmentId}/complete`, {
        method: "POST",
        body: { goals: objetivos },
      }),
    onSuccess: (d) => {
      setResultado(d);
      setFase("resultado");
    },
    onError: () => setFase("resultado"),
  });

  // Si ya hay uno terminado y el enfriamiento no dejó empezar otro, se muestra ese.
  const ultimo = useQuery({
    queryKey: ["me", "assessments"],
    queryFn: () => apiFetch<Diagnostico[]>("/api/v1/me/assessments"),
    enabled: fase === "puerta",
    staleTime: 0,
  });

  if (fase === "conversando" && sesion) {
    return (
      <Conversacion
        sesion={sesion}
        minutos={cifras.assessmentMinutes}
        onTerminar={() => {
          setFase("calculando");
          cerrar.mutate();
        }}
        onSalir={() => {
          void apiFetch(`/api/v1/assessments/${sesion.assessmentId}/abandon`, { method: "POST" })
            .catch(() => undefined)
            .finally(() => router.push("/diagnostico"));
        }}
        onReintentar={() => empezar.mutate(objetivos)}
      />
    );
  }

  if (fase === "calculando") {
    return <Calculando />;
  }

  if (fase === "resultado") {
    if (resultado) return <Resultado diagnostico={resultado} />;
    return (
      <main className="mx-auto w-full max-w-lg px-5 py-10">
        <AvisoError mensaje="No pudimos cerrar tu diagnóstico. Está guardado en tu perfil." />
      </main>
    );
  }

  const yaTiene = (ultimo.data ?? []).find((d) => d.status === "COMPLETED");
  const bloqueado = empezar.error instanceof ApiError ? empezar.error.message : null;

  if (bloqueado && yaTiene) {
    return (
      <>
        <div className="mx-auto w-full max-w-lg px-5 pt-6 lg:max-w-5xl">
          <div className="rounded-card bg-accent-peach-soft p-4 text-[13.5px] leading-relaxed text-[#8a5a33]">
            {bloqueado}
          </div>
        </div>
        <Resultado diagnostico={yaTiene} />
      </>
    );
  }

  return (
    <>
      <Puerta
        onListo={(metas) => {
          setObjetivos(metas);
          empezar.mutate(metas);
        }}
      />
      {bloqueado && (
        <div className="mx-auto w-full max-w-lg px-5 pb-8">
          <AvisoError mensaje={bloqueado} />
        </div>
      )}
      {(empezar.isPending || ultimo.isPending) && (
        <div className="mx-auto w-full max-w-lg px-5 pb-8">
          <Cargando filas={1} />
        </div>
      )}
    </>
  );
}

/**
 * La espera. Nunca un spinner genérico: acaban de exponerse y merecen una frase, no un círculo.
 * Meissa piensa, con su línea de estado al lado; el dibujo nunca comunica solo.
 */
function Calculando() {
  return (
    <main className="flex min-h-dvh flex-col items-center justify-center px-6 text-center">
      <Meissa estado="piensa" decorativo className="h-[150px] w-auto" />
      <p role="status" aria-live="polite" className="mt-2 text-[14px] font-semibold text-text-secondary">
        <span className="text-[#7A4A8C]">Meissa</span> · un momento…
      </p>
      <p className="mt-4 font-display text-h3 font-bold">Estamos leyendo cómo hablaste.</p>
      <p className="mt-2 max-w-[42ch] text-[14px] leading-relaxed text-text-secondary">
        Unos segundos. No estamos corrigiendo nada: estamos mirando cómo fluiste.
      </p>
    </main>
  );
}
