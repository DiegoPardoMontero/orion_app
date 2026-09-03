"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AlertTriangle, Gavel, ShieldCheck } from "lucide-react";
import { useState } from "react";
import { AvisoError, Cargando, ErrorCarga, Vacio } from "@/components/estados";
import { Badge, Boton, Segmento, Spinner, Tarjeta } from "@/components/ui";
import { ApiError, apiFetch } from "@/lib/api/fetch";
import type { DisputeResponse, SanctionView } from "@/lib/api/types";
import { fechaCorta, horaBogota, precioCop } from "@/lib/format";

type Pestana = "reclamos" | "sanciones";

/** El motivo del reclamo, en el idioma de quien lo va a leer. */
const MOTIVO: Record<string, string> = {
  PROFESSOR_NO_SHOW: "El profesor no se presentó",
  PROFESSOR_LATE: "El profesor llegó tarde",
  TECHNICAL_PROBLEM: "Problema técnico",
  LESSON_NOT_HELD: "La clase no se dio",
  OTHER: "Otro",
};

const SANCION: Record<string, string> = {
  WARNING: "Aviso",
  VISIBILITY_REDUCED: "Menos visibilidad (14 días)",
  BOOKINGS_SUSPENDED: "Sin reservas nuevas (7 días)",
  PROFILE_HIDDEN: "Perfil oculto",
  ACCOUNT_SUSPENDED: "Cuenta suspendida",
};

export default function AdminReclamosPage() {
  const [pestana, setPestana] = useState<Pestana>("reclamos");

  return (
    <main className="mx-auto max-w-4xl px-6 py-6">
      <h1 className="font-display text-h1 font-bold">Reclamos</h1>
      <p className="mt-1 text-[13.5px] text-text-secondary">
        Mientras un reclamo está abierto, ese dinero no se mueve: ni se le paga al profesor ni se le
        devuelve al estudiante.
      </p>

      <div className="mt-4">
        <Segmento<Pestana>
          valor={pestana}
          onCambio={setPestana}
          opciones={[
            { valor: "reclamos", etiqueta: "Reclamos" },
            { valor: "sanciones", etiqueta: "Sanciones propuestas" },
          ]}
        />
      </div>

      {pestana === "reclamos" ? <Reclamos /> : <SancionesPropuestas />}
    </main>
  );
}

function Reclamos() {
  const [estado, setEstado] = useState("OPEN");

  const reclamos = useQuery({
    queryKey: ["admin", "disputes", estado],
    queryFn: () => apiFetch<DisputeResponse[]>(`/api/v1/admin/disputes?status=${estado}`),
  });

  return (
    <>
      <div className="mt-4 flex flex-wrap gap-2">
        {[
          { valor: "OPEN", etiqueta: "Abiertos" },
          { valor: "RESOLVED_FOR_STUDENT", etiqueta: "A favor del estudiante" },
          { valor: "RESOLVED_FOR_PROFESSOR", etiqueta: "A favor del profesor" },
          { valor: "", etiqueta: "Todos" },
        ].map((opcion) => (
          <Boton
            key={opcion.etiqueta}
            variante={estado === opcion.valor ? "primario" : "contorno"}
            className="h-9 px-4 text-[13px]"
            onClick={() => setEstado(opcion.valor)}
          >
            {opcion.etiqueta}
          </Boton>
        ))}
      </div>

      {reclamos.isPending ? (
        <div className="mt-5">
          <Cargando filas={3} />
        </div>
      ) : reclamos.isError ? (
        <div className="mt-5">
          <ErrorCarga mensaje="No pudimos cargar los reclamos." onReintentar={() => void reclamos.refetch()} />
        </div>
      ) : reclamos.data.length === 0 ? (
        <div className="mt-5">
          <Vacio titulo="Nada por resolver" texto="No hay reclamos en este filtro." />
        </div>
      ) : (
        <ul className="mt-5 grid gap-3">
          {reclamos.data.map((reclamo) => (
            <li key={reclamo.id}>
              <FichaReclamo reclamo={reclamo} />
            </li>
          ))}
        </ul>
      )}
    </>
  );
}

function FichaReclamo({ reclamo }: { reclamo: DisputeResponse }) {
  const queryClient = useQueryClient();
  const [nota, setNota] = useState("");
  const abierto = reclamo.status === "OPEN" || reclamo.status === "UNDER_REVIEW";

  const resolver = useMutation({
    mutationFn: (outcome: string) =>
      apiFetch(`/api/v1/admin/disputes/${reclamo.id}/resolve`, {
        method: "POST",
        body: { outcome, note: nota.trim() },
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "disputes"] });
      void queryClient.invalidateQueries({ queryKey: ["admin", "dashboard"] });
    },
  });

  const error = resolver.error instanceof ApiError ? resolver.error.message : null;
  const listo = nota.trim().length > 0;

  return (
    <Tarjeta>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="font-semibold text-text">{MOTIVO[reclamo.reasonCode] ?? reclamo.reasonCode}</p>
          <p className="text-[12.5px] text-text-secondary">
            {reclamo.studentName ?? "—"} con {reclamo.professorName ?? "—"}
            {reclamo.classAt && ` · ${fechaCorta(reclamo.classAt)} ${horaBogota(reclamo.classAt)}`}
          </p>
        </div>
        <div className="text-right">
          <p className="font-semibold tabular-nums text-text">{precioCop(reclamo.amountCop)}</p>
          <Badge tono={abierto ? "melocoton" : "menta"} punto>
            {abierto ? "Por resolver" : "Resuelto"}
          </Badge>
        </div>
      </div>

      {reclamo.description && (
        <p className="mt-3 rounded-base bg-surface-sunken px-4 py-3 text-[13px] text-text-secondary">
          “{reclamo.description}”
        </p>
      )}

      {reclamo.resolutionNote && (
        <p className="mt-3 text-[12.5px] text-text-muted">
          <span className="font-semibold">Resolución:</span> {reclamo.resolutionNote}
        </p>
      )}

      {abierto && (
        <div className="mt-4 border-t border-border pt-3">
          {error && (
            <div className="mb-2">
              <AvisoError mensaje={error} />
            </div>
          )}
          <label className="block text-[12.5px] font-bold text-text-secondary" htmlFor={`nota-${reclamo.id}`}>
            Qué decidiste y por qué
          </label>
          <textarea
            id={`nota-${reclamo.id}`}
            rows={2}
            maxLength={1000}
            value={nota}
            onChange={(event) => setNota(event.target.value)}
            placeholder="Esta nota queda registrada: alguien tiene que poder entenderla en seis meses."
            className="mt-1.5 w-full rounded-base border-[1.5px] border-border bg-surface-raised px-4 py-3 text-sm focus:border-primary focus:shadow-focus focus:outline-none"
          />
          <div className="mt-3 flex flex-wrap gap-2">
            <Boton
              variante="secundario"
              disabled={!listo || resolver.isPending}
              onClick={() => resolver.mutate("RESOLVED_FOR_STUDENT")}
              className="h-10 text-[13px]"
            >
              {resolver.isPending ? <Spinner /> : <ShieldCheck size={15} strokeWidth={1.75} />}
              A favor del estudiante · le devolvemos {precioCop(reclamo.amountCop)}
            </Boton>
            <Boton
              variante="contorno"
              disabled={!listo || resolver.isPending}
              onClick={() => resolver.mutate("RESOLVED_FOR_PROFESSOR")}
              className="h-10 text-[13px]"
            >
              <Gavel size={15} strokeWidth={1.75} />
              A favor del profesor · la clase contó
            </Boton>
          </div>
          <p className="mt-2 text-[11.5px] text-text-muted">
            A favor del estudiante también queda registrada una ausencia del profesor, que es lo que
            evalúan las sanciones.
          </p>
        </div>
      )}
    </Tarjeta>
  );
}

/**
 * Las sanciones están en modo observación: el sistema calcula la que corresponde y la deja
 * propuesta. Esta pantalla es el paso que falta — una persona confirma o la deja pasar.
 */
function SancionesPropuestas() {
  const queryClient = useQueryClient();

  const propuestas = useQuery({
    queryKey: ["admin", "sanctions", "proposed"],
    queryFn: () => apiFetch<SanctionView[]>("/api/v1/admin/sanctions/proposed"),
  });

  const confirmar = useMutation({
    mutationFn: (id: string) =>
      apiFetch(`/api/v1/admin/sanctions/${id}/confirm`, { method: "POST" }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "sanctions"] });
      void queryClient.invalidateQueries({ queryKey: ["admin", "dashboard"] });
    },
  });

  const descartar = useMutation({
    mutationFn: (id: string) => apiFetch(`/api/v1/admin/sanctions/${id}`, { method: "DELETE" }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "sanctions"] });
      void queryClient.invalidateQueries({ queryKey: ["admin", "dashboard"] });
    },
  });

  if (propuestas.isPending || propuestas.isError) {
    return (
      <div className="mt-5">
        {propuestas.isError ? (
          <ErrorCarga
            mensaje="No pudimos cargar las sanciones propuestas."
            onReintentar={() => void propuestas.refetch()}
          />
        ) : (
          <Cargando filas={2} />
        )}
      </div>
    );
  }

  return (
    <>
      <p className="mt-4 flex items-start gap-2 rounded-base bg-info-bg px-4 py-3 text-[13px] text-info">
        <AlertTriangle size={16} strokeWidth={1.75} className="mt-0.5 shrink-0" />
        <span>
          Las sanciones están en <strong>modo observación</strong>: el sistema calcula la que
          corresponde por las ausencias confirmadas, pero no la aplica. Aquí decides tú. Para que se
          apliquen solas, cambia <code>sanctions_mode</code> a <code>ENFORCE</code> en los ajustes.
        </span>
      </p>

      {propuestas.data.length === 0 ? (
        <div className="mt-4">
          <Vacio titulo="Ninguna sanción propuesta" texto="Nadie ha acumulado ausencias confirmadas." />
        </div>
      ) : (
        <ul className="mt-4 grid gap-3">
          {propuestas.data.map((sancion) => (
            <li key={sancion.id}>
              <Tarjeta>
                <p className="font-semibold text-text">{SANCION[sancion.type] ?? sancion.type}</p>
                <p className="mt-1 text-[13px] text-text-secondary">{sancion.reason}</p>
                <div className="mt-3 flex flex-wrap gap-2 border-t border-border pt-3">
                  <Boton
                    className="h-10 text-[13px]"
                    disabled={confirmar.isPending}
                    onClick={() => confirmar.mutate(sancion.id)}
                  >
                    {confirmar.isPending ? <Spinner /> : null}
                    Aplicarla
                  </Boton>
                  <Boton
                    variante="contorno"
                    className="h-10 text-[13px]"
                    disabled={descartar.isPending}
                    onClick={() => descartar.mutate(sancion.id)}
                  >
                    Descartar
                  </Boton>
                </div>
              </Tarjeta>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
