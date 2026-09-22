"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMutation, useQuery } from "@tanstack/react-query";
import { apiFetch, ApiError } from "@/lib/api/fetch";
import type { Diagnostico, DiagnosticoIniciado } from "@/lib/api/diagnostico";
import type { Me } from "@/lib/auth/session";
import { HOME_BY_ROLE } from "@/lib/auth/roles";
import { useCifras } from "@/lib/cifras";
import { Cargando } from "@/components/estados";
import { Wordmark } from "@/components/marca";
import { Meissa } from "@/components/Meissa";
import { Boton } from "@/components/ui";
import { Conversacion } from "./Conversacion";
import { Puerta } from "./Puerta";
import { Resultado } from "./Resultado";

type Fase = "puerta" | "conversando" | "calculando" | "resultado";

/** Todo lo de esta página se llama sin exigir sesión: sin cuenta también se puede. */
const SIN_REDIRIGIR = { redirectOn401: false } as const;

/**
 * El diagnóstico de confianza, de principio a fin y sin cambiar de dirección.
 *
 * <p><strong>Sin cuenta</strong> (Pardo, 22/09/2026). La página es pública: quien tiene cuenta de
 * estudiante lo hace con ella, y quien no, con su nombre y dos casillas (un «lead» ligado a este
 * dispositivo). Al crear su cuenta, el backend muda sus diagnósticos a ella. Las cuentas que no son
 * de estudiante no lo hacen: no se autoevalúa aquí.
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

  const me = useQuery({
    queryKey: ["auth", "me", "diagnostico"],
    queryFn: () => apiFetch<Me>("/api/v1/auth/me", SIN_REDIRIGIR),
    retry: false,
  });
  const anonimo = me.isError;

  // Quien ya empezó sin cuenta en este dispositivo no tiene que volver a decir su nombre.
  const lead = useQuery({
    queryKey: ["assessment-leads", "me"],
    queryFn: () => apiFetch<{ firstName: string }>("/api/v1/assessment-leads/me", SIN_REDIRIGIR),
    enabled: anonimo,
    retry: false,
  });

  const empezar = useMutation({
    mutationFn: (metas: string[]) =>
      apiFetch<DiagnosticoIniciado>("/api/v1/assessments", {
        method: "POST",
        body: { languageCode: "EN", goals: metas },
        ...SIN_REDIRIGIR,
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
        ...SIN_REDIRIGIR,
      }),
    onSuccess: (d) => {
      setResultado(d);
      setFase("resultado");
    },
    onError: () => setFase("resultado"),
  });

  // Si ya hay uno terminado y el enfriamiento no dejó empezar otro, se muestra ese.
  const puedeTenerHistorial = me.data?.role === "STUDENT" || (anonimo && lead.isSuccess);
  const ultimo = useQuery({
    queryKey: ["me", "assessments"],
    queryFn: () => apiFetch<Diagnostico[]>("/api/v1/me/assessments", SIN_REDIRIGIR),
    enabled: fase === "puerta" && puedeTenerHistorial,
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
          void apiFetch(`/api/v1/assessments/${sesion.assessmentId}/abandon`, {
            method: "POST",
            ...SIN_REDIRIGIR,
          })
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
    return (
      <>
        <Barra />
        {resultado ? (
          <Resultado diagnostico={resultado} sinCuenta={anonimo} />
        ) : (
          <main className="mx-auto w-full max-w-lg px-5 py-10 text-center">
            <p className="font-display text-h3 font-bold">No pudimos cerrar tu diagnóstico.</p>
            <p className="mt-2 text-[14px] text-text-secondary">
              Lo que dijiste está guardado. Inténtalo de nuevo en un momento.
            </p>
            <Boton variante="primario" className="mt-5" onClick={() => cerrar.mutate()}>
              Intentar de nuevo
            </Boton>
          </main>
        )}
      </>
    );
  }

  if (me.isPending || (anonimo && lead.isPending)) {
    return (
      <>
        <Barra />
        <div className="mx-auto w-full max-w-lg px-5 py-8">
          <Cargando filas={2} />
        </div>
      </>
    );
  }

  // Profesores, aspirantes y administración: el diagnóstico no es para ellos.
  if (me.data && me.data.role !== "STUDENT") {
    return (
      <>
        <Barra />
        <main className="mx-auto w-full max-w-lg px-5 py-10 text-center">
          <p className="font-display text-h3 font-bold">El diagnóstico es para estudiantes.</p>
          <Link
            href={HOME_BY_ROLE[me.data.role]}
            className="mt-5 inline-flex h-11 items-center rounded-pill bg-primary px-5 text-[14px] font-bold text-on-primary shadow-primary hover:bg-primary-strong focus-visible:shadow-focus"
          >
            Ir a mi panel
          </Link>
        </main>
      </>
    );
  }

  const yaTiene = (ultimo.data ?? []).find((d) => d.status === "COMPLETED");
  const bloqueado = empezar.error instanceof ApiError ? empezar.error.message : null;

  if (bloqueado && yaTiene) {
    return (
      <>
        <Barra />
        <div className="mx-auto w-full max-w-lg px-5 pt-2 lg:max-w-5xl">
          <div className="rounded-card bg-accent-peach-soft p-4 text-[13.5px] leading-relaxed text-[#8a5a33]">
            {bloqueado}
          </div>
        </div>
        <Resultado diagnostico={yaTiene} sinCuenta={anonimo} />
      </>
    );
  }

  return (
    <>
      <Barra />
      <Puerta
        sinCuenta={anonimo}
        nombreDelLead={lead.data?.firstName ?? null}
        empezando={empezar.isPending}
        error={bloqueado}
        onListo={(metas) => {
          setObjetivos(metas);
          empezar.mutate(metas);
        }}
      />
    </>
  );
}

/** Sin el armazón de la app: la marca, que lleva a la portada. */
function Barra() {
  return (
    <header className="mx-auto w-full max-w-lg px-5 pt-5 lg:max-w-5xl">
      <Link href="/" className="inline-block rounded-base text-primary focus-visible:shadow-focus">
        <Wordmark className="text-[15px]" />
      </Link>
    </header>
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
