"use client";

import { useState } from "react";
import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AlertTriangle, ExternalLink, RefreshCw } from "lucide-react";
import { apiFetch, ApiError } from "@/lib/api/fetch";
import { AvisoError } from "@/components/estados";
import { Badge, Boton, Campo, Tarjeta } from "@/components/ui";

type Ensayo = {
  bookingId: string;
  cerrada: string;
  estudiante: string;
  estudianteEmail: string;
  profesor: string;
  profesorEmail: string;
  acta: "DRAFT" | "PUBLISHED" | null;
  actaConIa: boolean;
  practicaId: string | null;
  practica: "PENDING" | "READY" | "IN_PROGRESS" | "COMPLETED" | "EXPIRED" | "FAILED" | null;
  ejercicios: number;
  correctos: number;
  intentos: number;
};

type Ensayos = { ensayos: Ensayo[]; avisos: string[] };

const CLAVE_CORREOS = "orion.ensayo.correos";

function correosGuardados(): { estudiante: string; profesor: string } {
  try {
    const crudo = localStorage.getItem(CLAVE_CORREOS);
    if (crudo) return JSON.parse(crudo);
  } catch {
    // Sin almacenamiento el formulario arranca vacío, y ya.
  }
  return { estudiante: "", profesor: "" };
}

/**
 * Ensayar el acta y la práctica (Bloque 10) sin dar una clase de una hora.
 *
 * <p>Crea una clase de prueba que ya se dictó y deja escritos los pasos con sus enlaces: el profesor
 * escribe el acta, el estudiante la lee y resuelve la práctica, y el profesor vuelve a ver los
 * ejercicios. Debajo, en qué va cada ensayo: lo que falla en este recorrido —la práctica que no se
 * genera, una función apagada en Ajustes— no se ve desde ninguna de las pantallas que recorre.
 */
export function EnsayoDelActa() {
  const cliente = useQueryClient();
  const [estudiante, setEstudiante] = useState(() => correosGuardados().estudiante);
  const [profesor, setProfesor] = useState(() => correosGuardados().profesor);

  const lista = useQuery({
    queryKey: ["admin", "ensayos"],
    queryFn: () => apiFetch<Ensayos>("/api/v1/admin/system/rehearsals"),
    // Mientras haya algo en marcha (un acta sin publicar, una práctica generándose) se mira solo.
    refetchInterval: (q) =>
      q.state.data?.ensayos.some((e) => e.acta !== "PUBLISHED" || e.practica === "PENDING") ? 10_000 : false,
  });

  const crear = useMutation({
    mutationFn: () =>
      apiFetch<{ bookingId: string; acta: string }>("/api/v1/admin/system/rehearsal", {
        method: "POST",
        body: { studentEmail: estudiante.trim(), professorEmail: profesor.trim() },
      }),
    onSuccess: () => {
      try {
        localStorage.setItem(CLAVE_CORREOS, JSON.stringify({ estudiante: estudiante.trim(), profesor: profesor.trim() }));
      } catch {
        // Recordar los correos es una comodidad; si no se puede, no pasa nada.
      }
      void cliente.invalidateQueries({ queryKey: ["admin", "ensayos"] });
    },
  });

  const ultimo = lista.data?.ensayos[0];

  return (
    <section className="mt-10" id="ensayo-del-acta">
      <h2 className="font-display text-h3 font-bold">Ensayar el acta y la práctica</h2>
      <p className="mt-1 text-[13px] leading-relaxed text-text-secondary">
        Crea una clase de prueba que ya se dictó: el profesor puede escribir el acta en ese mismo momento. No cobra,
        no manda correos de reserva ni recordatorios, y no cuenta en el porcentaje de actas de nadie.
      </p>

      {lista.data && lista.data.avisos.length > 0 && (
        <div className="mt-3 flex items-start gap-2.5 rounded-card bg-accent-peach-soft p-4 text-[13px] text-[#8a5a33]">
          <AlertTriangle size={17} strokeWidth={2} className="mt-0.5 shrink-0" />
          <ul className="grid gap-1">
            {lista.data.avisos.map((a) => (
              <li key={a}>{a}</li>
            ))}
          </ul>
        </div>
      )}

      <Tarjeta className="mt-3">
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="block">
            <span className="text-[12.5px] font-bold text-text-secondary">Correo del estudiante</span>
            <Campo
              type="email"
              value={estudiante}
              onChange={(e) => setEstudiante(e.target.value)}
              placeholder="estudiante@correo.com"
              className="mt-1.5"
            />
          </label>
          <label className="block">
            <span className="text-[12.5px] font-bold text-text-secondary">Correo del profesor</span>
            <Campo
              type="email"
              value={profesor}
              onChange={(e) => setProfesor(e.target.value)}
              placeholder="profesor@correo.com"
              className="mt-1.5"
            />
          </label>
        </div>

        {crear.error instanceof ApiError && (
          <div className="mt-3">
            <AvisoError mensaje={crear.error.message} />
          </div>
        )}

        <Boton
          variante="primario"
          className="mt-4"
          disabled={!estudiante.trim() || !profesor.trim() || crear.isPending}
          onClick={() => crear.mutate()}
        >
          {crear.isPending ? "Creando…" : "Crear clase ya dictada"}
        </Boton>

        {crear.data && ultimo && ultimo.bookingId === crear.data.bookingId && <Pasos ensayo={ultimo} />}
      </Tarjeta>

      {lista.data && lista.data.ensayos.length > 0 && (
        <div className="mt-6">
          <div className="flex items-center justify-between gap-3">
            <h3 className="text-[14px] font-bold text-text">Ensayos de la última semana</h3>
            <Boton variante="fantasma" onClick={() => void lista.refetch()} disabled={lista.isFetching}>
              <RefreshCw size={14} strokeWidth={2.2} className={lista.isFetching ? "animate-spin" : ""} />
              Actualizar
            </Boton>
          </div>
          <ul className="mt-2 grid gap-2">
            {lista.data.ensayos.map((e) => (
              <FilaDeEnsayo key={e.bookingId} ensayo={e} />
            ))}
          </ul>
        </div>
      )}
    </section>
  );
}

/** Los pasos del recorrido, con los enlaces de esta clase y la cuenta que abre cada uno. */
function Pasos({ ensayo }: { ensayo: Ensayo }) {
  const acta = `/mis-clases/${ensayo.bookingId}/acta`;
  return (
    <div className="mt-4 rounded-card bg-success-bg p-4">
      <p className="text-[13.5px] font-bold text-success">Clase de prueba creada y cerrada.</p>
      <ol className="mt-2.5 grid list-decimal gap-2 pl-5 text-[13px] leading-relaxed text-text-secondary">
        <li>
          Entra como <strong className="text-text">{ensayo.profesorEmail}</strong> y abre{" "}
          <Enlace href={acta}>el acta de la clase</Enlace>. Escribe o dicta tus notas, pide el borrador, edítalo y
          publícalo.
        </li>
        <li>
          En otro navegador (o en incógnito), entra como <strong className="text-text">{ensayo.estudianteEmail}</strong>{" "}
          y abre <Enlace href={acta}>la misma acta</Enlace>: es lo que el estudiante lee.
        </li>
        <li>
          En más o menos un minuto aparece la práctica: el botón «Practicar esto» en el acta y la invitación en su
          inicio. Resuélvela con aciertos y con errores.
        </li>
        <li>
          Vuelve como profesor a <Enlace href={acta}>tu acta</Enlace>: debajo están los ejercicios que salieron de
          ella, sin las respuestas del estudiante.
        </li>
      </ol>
      <p className="mt-2.5 text-[12.5px] text-text-muted">
        El estado de cada paso se ve abajo, en «Ensayos de la última semana», y se actualiza solo.
      </p>
    </div>
  );
}

function FilaDeEnsayo({ ensayo: e }: { ensayo: Ensayo }) {
  return (
    <li>
      <Tarjeta>
        <div className="flex flex-wrap items-start justify-between gap-x-4 gap-y-2">
          <div className="min-w-0">
            <p className="text-[13.5px] font-bold text-text">
              {e.profesor} <span className="font-normal text-text-muted">con</span> {e.estudiante}
            </p>
            <p className="mt-0.5 text-[12.5px] text-text-muted">
              Cerrada{" "}
              {new Date(e.cerrada).toLocaleString("es-CO", {
                timeZone: "America/Bogota",
                dateStyle: "medium",
                timeStyle: "short",
              })}
            </p>
          </div>
          <div className="flex flex-wrap gap-1.5">
            <EstadoDelActa ensayo={e} />
            <EstadoDeLaPractica ensayo={e} />
          </div>
        </div>
        <div className="mt-2.5 flex flex-wrap gap-x-4 gap-y-1">
          <Enlace href={`/mis-clases/${e.bookingId}/acta`}>Abrir el acta</Enlace>
          {e.practicaId && e.practica !== "PENDING" && e.practica !== "FAILED" && (
            <Enlace href={`/practica/${e.practicaId}`}>Abrir la práctica (como estudiante)</Enlace>
          )}
        </div>
      </Tarjeta>
    </li>
  );
}

function EstadoDelActa({ ensayo: e }: { ensayo: Ensayo }) {
  if (e.acta === null) return <Badge tono="neutral">Acta: sin escribir</Badge>;
  if (e.acta === "DRAFT") return <Badge tono="lavanda">Acta: en borrador</Badge>;
  return (
    <Badge tono="menta" punto>
      {/* «Del borrador» y no «con IA»: sin OpenAI, el borrador también existe (lo arma la regla local). */}
      Acta publicada{e.actaConIa ? " · del borrador" : " · a mano"}
    </Badge>
  );
}

function EstadoDeLaPractica({ ensayo: e }: { ensayo: Ensayo }) {
  if (e.acta !== "PUBLISHED") return null;
  switch (e.practica) {
    case null:
      return <Badge tono="neutral">Práctica: no se creó</Badge>;
    case "PENDING":
      return (
        <Badge tono="lavanda">
          Práctica: generándose{e.intentos > 0 ? ` (intento ${e.intentos + 1})` : ""}
        </Badge>
      );
    case "READY":
      return <Badge tono="lavanda">Práctica lista · {e.ejercicios} ejercicios</Badge>;
    case "IN_PROGRESS":
      return <Badge tono="melocoton">Práctica en curso</Badge>;
    case "COMPLETED":
      return (
        <Badge tono="menta" punto>
          Práctica hecha · {e.correctos}/{e.ejercicios}
        </Badge>
      );
    case "EXPIRED":
      return <Badge tono="neutral">Práctica vencida</Badge>;
    case "FAILED":
      return <Badge tono="error">Práctica: la IA no logró ejercicios anclados al acta</Badge>;
  }
}

function Enlace({ href, children }: { href: string; children: React.ReactNode }) {
  return (
    <Link
      href={href}
      className="inline-flex items-center gap-1 text-[13px] font-bold text-primary-strong hover:underline"
    >
      {children}
      <ExternalLink size={12} strokeWidth={2.2} />
    </Link>
  );
}
