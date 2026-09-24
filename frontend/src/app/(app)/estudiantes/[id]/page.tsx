"use client";

import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, Check, ChevronRight, Clock, PlayCircle, Target, type LucideIcon } from "lucide-react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { Suspense } from "react";
import { Cargando, Vacio } from "@/components/estados";
import { AvatarOrion } from "@/components/gamificacion/AvatarOrion";
import { ChipPuntos } from "@/components/Puntos";
import { apiFetch } from "@/lib/api/fetch";
import type { GoalResponse } from "@/lib/api/types";
import { etiquetaObjetivo } from "@/lib/i18n";
import { NIVEL_ESTUDIANTE, type FichaEstudiante } from "@/lib/gamificacion";
import { useMe } from "@/lib/auth/session";
import { fechaCorta } from "@/lib/format";
import { Constelacion, formaDe } from "@/components/practica/Constelacion";
import { diaCorto, tituloDelSet, type PracticaEnLaFicha, type ResumenDePractica } from "@/lib/practica";

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

  // Sus puntos, con la misma regla de visibilidad que la ficha: si no se ve la ficha, tampoco esto.
  const puntos = useQuery({
    queryKey: ["student-points", id],
    queryFn: () => apiFetch<{ total: number }>(`/api/v1/students/${id}/points`),
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
        {puntos.data && <ChipPuntos total={puntos.data.total} className="mt-2.5" />}
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

/** El chip de cada práctica en el historial (§10.12): verde completada, durazno en curso, lavanda lista. */
function chipDe(p: PracticaEnLaFicha): { texto: string; fondo: string; tinta: string; borde?: string; I: LucideIcon } {
  switch (p.status) {
    case "COMPLETED":
      return { texto: `Completada${p.completedAt ? ` · ${diaCorto(p.completedAt)}` : ""}`, fondo: "#DEF3E7", tinta: "#1F5238", I: Check };
    case "IN_PROGRESS":
      return { texto: "En curso", fondo: "#FFE9D6", tinta: "#6B3E1A", I: PlayCircle };
    case "EXPIRED":
      return {
        texto: p.stars.some((e) => e !== "off") ? "Vencida a medias" : "Vencida sin hacer",
        fondo: "#FFFFFF",
        tinta: "#5E4E6B",
        borde: "1.5px dashed #C9B8A8",
        I: Clock,
      };
    default:
      return { texto: "Lista", fondo: "#EFE9F9", tinta: "#4A3A75", I: PlayCircle };
  }
}

/**
 * Cuánto practicó entre clases este mes y, práctica por práctica, su constelación (handoff
 * `design_handoff_orion_practica`, §10.12). Cada fila abre el acta de esa clase, donde está «Cómo le
 * fue» con cada ejercicio: el profesor lo ve todo, y el estudiante lo sabe desde el inicio del set.
 */
function SuPractica({ estudianteId }: { estudianteId: string }) {
  const { data: me } = useMe();
  const esProfesor = me?.role === "PROFESSOR";
  const resumen = useQuery({
    queryKey: ["professors", "me", "students", estudianteId, "practice"],
    queryFn: () => apiFetch<ResumenDePractica>(`/api/v1/professors/me/students/${estudianteId}/practice`),
    enabled: esProfesor,
    retry: false,
  });
  const historial = useQuery({
    queryKey: ["professors", "me", "students", estudianteId, "practice-sets"],
    queryFn: () => apiFetch<PracticaEnLaFicha[]>(`/api/v1/professors/me/students/${estudianteId}/practice-sets`),
    enabled: esProfesor,
    retry: false,
  });
  const d = resumen.data;
  const sets = historial.data ?? [];
  const hayResumen = d && (d.ofrecidasEsteMes > 0 || d.leCosto.length > 0);
  if (!hayResumen && sets.length === 0) return null;

  return (
    <section className="practica mt-5 flex flex-col gap-4">
      {d && hayResumen && (
        <div className="flex flex-col gap-2.5 rounded-tarjeta bg-white px-5 py-[18px]">
          <h2 className="m-0 text-[13px] font-extrabold tracking-[.08em] text-lavanda-ink uppercase">Práctica</h2>
          <p className="m-0 text-[17px] leading-[1.5]">
            {d.ofrecidasEsteMes > 0 && (
              <strong>
                Practicó {d.completadasEsteMes} de {d.ofrecidasEsteMes} {d.ofrecidasEsteMes === 1 ? "vez" : "veces"} este mes.
              </strong>
            )}
            {d.ofrecidasEsteMes > 0 && d.leCosto.length > 0 && " "}
            {d.leCosto.length > 0 && <>Le costó: {d.leCosto.join(", ")}.</>}
          </p>
        </div>
      )}
      {sets.length > 0 && (
        <ul className="m-0 flex list-none flex-col rounded-tarjeta bg-white px-2 py-1.5">
          {sets.map((p, k) => {
            const chip = chipDe(p);
            const titulo = tituloDelSet(p) ?? "Práctica";
            return (
              <li key={p.id} className={k < sets.length - 1 ? "border-b border-arena" : undefined}>
                <Link
                  href={p.bookingId ? `/mis-clases/${p.bookingId}/acta#como-le-fue` : "#"}
                  className="flex min-h-[72px] items-center gap-3.5 rounded-pareja px-3 py-2.5 text-ink transition-colors hover:bg-crema"
                >
                  <span className="shrink-0" style={{ opacity: p.status === "EXPIRED" ? 0.45 : 1 }}>
                    <Constelacion
                      estados={p.stars}
                      forma={formaDe(p.id)}
                      ancho={76}
                      r={6}
                      guias={p.status !== "COMPLETED"}
                      lineas={p.status === "COMPLETED"}
                      etiqueta={`${titulo}: ${p.stars.filter((e) => e !== "off").length} de ${p.stars.length} estrellas`}
                    />
                  </span>
                  {/* En el celular el chip va bajo el título: al lado, el título se parte en una
                      palabra por renglón. */}
                  <span className="flex min-w-0 flex-1 flex-col gap-0.5 sm:flex-row sm:items-center sm:gap-3.5">
                    <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                      <strong className="text-[15px]">{titulo}</strong>
                      {p.classStartsAt && <span className="text-[13px] text-ink-2">De la clase del {diaCorto(p.classStartsAt)}</span>}
                    </span>
                    <span
                      className="mt-1 inline-flex h-7 shrink-0 items-center gap-[5px] self-start rounded-pill px-2.5 text-[12px] font-extrabold whitespace-nowrap sm:mt-0 sm:self-auto"
                      style={{ background: chip.fondo, color: chip.tinta, border: chip.borde ?? "none" }}
                    >
                      <chip.I size={13} strokeWidth={2.2} aria-hidden />
                      {chip.texto}
                    </span>
                  </span>
                  <ChevronRight size={18} strokeWidth={1.75} className="shrink-0 text-ink-3" aria-hidden />
                </Link>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
