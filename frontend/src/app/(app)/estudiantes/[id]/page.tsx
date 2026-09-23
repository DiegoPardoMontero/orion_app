"use client";

import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, Target } from "lucide-react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { Suspense } from "react";
import { Cargando, Vacio } from "@/components/estados";
import { AvatarOrion } from "@/components/gamificacion/AvatarOrion";
import { apiFetch } from "@/lib/api/fetch";
import type { GoalResponse } from "@/lib/api/types";
import { etiquetaObjetivo } from "@/lib/i18n";
import { NIVEL_ESTUDIANTE, type FichaEstudiante } from "@/lib/gamificacion";
import { useMe } from "@/lib/auth/session";
import { fechaCorta } from "@/lib/format";
import type { ResumenDePractica } from "@/lib/practica";

/**
 * El perfil de un estudiante visto por otra persona.
 *
 * <p>Las tres capas de visibilidad las aplica el servidor: si no hay derecho a verlo responde 404,
 * y aquí eso se muestra como «no encontramos este perfil» —nunca como «no tienes permiso», que
 * confirmaría que existe—.
 */
export default function PerfilEstudiantePage() {
  return (
    <Suspense fallback={null}>
      <Contenido />
    </Suspense>
  );
}

function Contenido() {
  const { id } = useParams<{ id: string }>();

  const perfil = useQuery({
    queryKey: ["student-profile", id],
    queryFn: () => apiFetch<FichaEstudiante>(`/api/v1/students/${id}/profile`),
    retry: false,
  });

  // Los objetivos llegan como código (`CONVERSATION`); el catálogo es quien sabe cómo se llaman en
  // español. Sin él, el profesor leía el nombre de una columna de base de datos.
  const objetivos = useQuery({
    queryKey: ["catalog", "goals"],
    queryFn: () => apiFetch<GoalResponse[]>("/api/v1/catalog/goals"),
    staleTime: Infinity,
  });

  if (perfil.isPending) {
    return (
      <main className="mx-auto w-full max-w-md px-5 py-6 lg:max-w-2xl lg:px-12">
        <Cargando filas={3} />
      </main>
    );
  }

  if (perfil.isError || !perfil.data) {
    return (
      <main className="mx-auto w-full max-w-md px-5 py-6 lg:max-w-2xl lg:px-12">
        <Vacio
          titulo="No encontramos este perfil"
          texto="Puede que no exista o que su dueño lo tenga en privado."
        />
      </main>
    );
  }

  const ficha = perfil.data;

  return (
    <main className="mx-auto w-full max-w-md px-5 py-6 lg:max-w-2xl lg:px-12 lg:py-8">
      <Link
        href="/mis-clases"
        className="inline-flex items-center gap-1.5 text-[13px] font-semibold text-text-secondary transition-colors hover:text-text focus-visible:shadow-focus"
      >
        <ArrowLeft size={15} strokeWidth={2} />
        Volver a mis clases
      </Link>

      <div className="mt-5 flex flex-col items-center text-center">
        <AvatarOrion
          nombre={ficha.fullName}
          fotoUrl={ficha.photoUrl}
          frameCode={ficha.frameCode}
          paletteCode={ficha.paletteCode}
          skyCode={ficha.skyCode}
          accesorios={ficha.accessories}
          size={130}
        />
        <h1 className="mt-5 font-display text-h2 font-bold">{ficha.fullName}</h1>
        {ficha.selfDeclaredLevel && (
          <p className="mt-1 text-[14px] text-text-secondary">
            {NIVEL_ESTUDIANTE[ficha.selfDeclaredLevel]}
            {ficha.primaryLanguage ? ` · ${ficha.primaryLanguage}` : ""}
          </p>
        )}
      </div>

      {ficha.motivation && (
        <p className="mt-6 rounded-card border border-border bg-surface-raised p-5 text-[14.5px] leading-relaxed text-text-secondary">
          {ficha.motivation}
        </p>
      )}

      {ficha.goalCodes.length > 0 && (
        <section className="mt-5">
          <h2 className="flex items-center gap-1.5 text-[12px] font-bold uppercase tracking-[0.06em] text-text-secondary">
            <Target size={14} strokeWidth={2} />
            Para qué aprende
          </h2>
          <div className="mt-2 flex flex-wrap gap-2">
            {ficha.goalCodes.map((code) => (
              <span
                key={code}
                className="rounded-pill bg-surface-sunken px-3 py-1.5 text-[12.5px] font-semibold text-text-secondary"
              >
                {etiquetaObjetivo(code, objetivos.data)}
              </span>
            ))}
          </div>
        </section>
      )}

      {/* Una ficha casi vacía no puede parecer una pantalla rota: se dice que está vacía y por qué
          no pasa nada. */}
      {!ficha.motivation && ficha.goalCodes.length === 0 && !ficha.selfDeclaredLevel && (
        <p className="mt-6 rounded-card border border-border bg-surface-sunken p-5 text-center text-[14px] leading-relaxed text-text-secondary">
          Todavía no ha contado nada de sí. Puedes preguntarle en la primera clase qué quiere lograr.
        </p>
      )}

      <EnClaseContigo estudianteId={id} />
      <SuPractica estudianteId={id} />

      {/* El perfil público NO lleva correo, teléfono, saldo ni con quién ha practicado. No es que
          no se pinten: es que no viajan. */}
    </main>
  );
}

type ClaseMedida = {
  startsAt: string;
  studentSpeakingMs: number | null;
  professorSpeakingMs: number | null;
};

/**
 * Cuánto habló en sus clases contigo, contado por la sala (webhook de JaaS). Solo lo ve el profesor
 * que dio esas clases, para preparar la siguiente: si el estudiante casi no habla, la clase es una
 * exposición y no una conversación. El estudiante no ve esto, y no alimenta nada más.
 */
function EnClaseContigo({ estudianteId }: { estudianteId: string }) {
  const { data: me } = useMe();
  const clases = useQuery({
    queryKey: ["professors", "me", "students", estudianteId, "classroom"],
    queryFn: () => apiFetch<ClaseMedida[]>(`/api/v1/professors/me/students/${estudianteId}/classroom`),
    enabled: me?.role === "PROFESSOR",
    retry: false,
  });

  const medidas = (clases.data ?? []).filter(
    (c) => c.studentSpeakingMs != null && c.professorSpeakingMs != null && c.studentSpeakingMs + c.professorSpeakingMs > 0,
  );
  if (medidas.length === 0) return null;

  const parte = (c: ClaseMedida) =>
    Math.round((c.studentSpeakingMs! / (c.studentSpeakingMs! + c.professorSpeakingMs!)) * 100);
  const promedio = Math.round(medidas.reduce((suma, c) => suma + parte(c), 0) / medidas.length);

  return (
    <section className="mt-5 rounded-card border border-border bg-surface-raised p-5">
      <h2 className="text-[12px] font-bold uppercase tracking-[0.06em] text-text-secondary">
        En sus clases contigo
      </h2>
      <p className="mt-2 text-[14.5px] leading-relaxed text-text">
        En {medidas.length === 1 ? "su última clase" : `sus últimas ${medidas.length} clases`} habló, en
        promedio, <strong>el {promedio} %</strong> del tiempo que se habló.
      </p>
      <ul className="mt-3 grid gap-2">
        {medidas.map((c) => (
          <li key={c.startsAt} className="flex items-center gap-3 text-[12.5px] text-text-secondary">
            <span className="w-20 shrink-0">{fechaCorta(c.startsAt)}</span>
            <span
              className="h-2 flex-1 overflow-hidden rounded-full bg-surface-sunken"
              role="img"
              aria-label={`Habló el ${parte(c)} %`}
            >
              <span className="block h-full rounded-full bg-accent-lavender" style={{ width: `${parte(c)}%` }} />
            </span>
            <span className="w-10 shrink-0 text-right tabular-nums">{parte(c)} %</span>
          </li>
        ))}
      </ul>
    </section>
  );
}

/**
 * Cuánto practicó entre clases (Bloque 10, paso B5.4): un resumen agregado, nunca las respuestas
 * una por una. Si el estudiante siente que sus ejercicios son vigilados deja de arriesgarse a
 * equivocarse, y equivocarse en privado es justamente el valor de la práctica.
 */
function SuPractica({ estudianteId }: { estudianteId: string }) {
  const { data: me } = useMe();
  const resumen = useQuery({
    queryKey: ["professors", "me", "students", estudianteId, "practice"],
    queryFn: () => apiFetch<ResumenDePractica>(`/api/v1/professors/me/students/${estudianteId}/practice`),
    enabled: me?.role === "PROFESSOR",
    retry: false,
  });
  const d = resumen.data;
  if (!d || (d.ofrecidasEstaSemana === 0 && d.leCosto.length === 0)) return null;

  return (
    <section className="mt-5 rounded-card border border-border bg-surface-raised p-5">
      <h2 className="text-[12px] font-bold uppercase tracking-[0.06em] text-text-secondary">Entre clases</h2>
      <p className="mt-2 text-[14.5px] leading-relaxed text-text">
        {d.ofrecidasEstaSemana > 0 ? (
          <>
            <strong>
              Practicó {d.completadasEstaSemana} de {d.ofrecidasEstaSemana}{" "}
              {d.ofrecidasEstaSemana === 1 ? "vez" : "veces"} esta semana.
            </strong>{" "}
          </>
        ) : null}
        {d.leCosto.length > 0 && <>Le costó: {d.leCosto.join(", ")}.</>}
      </p>
    </section>
  );
}
