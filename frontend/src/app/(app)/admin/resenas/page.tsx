"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { EyeOff, Flag } from "lucide-react";
import { useState } from "react";
import { EstrellasFijas } from "@/components/Rating";
import { AvisoError, Cargando, ErrorCarga, Vacio } from "@/components/estados";
import { Boton, Campo, Spinner, Tarjeta } from "@/components/ui";
import { ApiError, apiFetch } from "@/lib/api/fetch";
import { fechaCorta } from "@/lib/format";

type ReportedReview = {
  id: string;
  rating: number;
  comment: string | null;
  studentName: string | null;
  professorName: string | null;
  reportedReason: string | null;
  reportedAt: string | null;
  createdAt: string | null;
};

/**
 * Reseñas que un profesor reportó. El profesor no puede borrarlas ni editarlas —eso convertiría el
 * promedio en publicidad—, pero sí pedir que las miren. Aquí una persona decide, y la reseña nunca
 * se elimina: se oculta con un motivo, y la fila queda.
 */
export default function AdminResenasPage() {
  const reportadas = useQuery({
    queryKey: ["admin", "reviews", "reported"],
    queryFn: () => apiFetch<ReportedReview[]>("/api/v1/admin/reviews/reported"),
  });

  return (
    <main className="mx-auto max-w-3xl px-6 py-6">
      <h1 className="font-display text-h1 font-bold">Reseñas reportadas</h1>
      <p className="mt-1 text-[13.5px] text-text-secondary">
        Ocultar una reseña la saca del perfil público y del promedio, pero no la borra: el histórico
        se conserva.
      </p>

      {reportadas.isPending ? (
        <div className="mt-5">
          <Cargando filas={3} />
        </div>
      ) : reportadas.isError ? (
        <div className="mt-5">
          <ErrorCarga
            mensaje="No pudimos cargar las reseñas reportadas."
            onReintentar={() => void reportadas.refetch()}
          />
        </div>
      ) : reportadas.data.length === 0 ? (
        <div className="mt-5">
          <Vacio
            titulo="Ninguna reseña reportada"
            texto="Cuando un profesor pida revisar una, aparecerá aquí."
          />
        </div>
      ) : (
        <ul className="mt-5 grid gap-3">
          {reportadas.data.map((resena) => (
            <li key={resena.id}>
              <FichaResena resena={resena} />
            </li>
          ))}
        </ul>
      )}
    </main>
  );
}

function FichaResena({ resena }: { resena: ReportedReview }) {
  const queryClient = useQueryClient();
  const [motivo, setMotivo] = useState("");

  const ocultar = useMutation({
    mutationFn: () =>
      apiFetch(`/api/v1/admin/reviews/${resena.id}/hide`, {
        method: "POST",
        body: { reason: motivo.trim() },
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "reviews"] });
      void queryClient.invalidateQueries({ queryKey: ["admin", "dashboard"] });
    },
  });

  const error = ocultar.error instanceof ApiError ? ocultar.error.message : null;

  return (
    <Tarjeta>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <EstrellasFijas rating={resena.rating} />
          <p className="mt-1 text-[12.5px] text-text-secondary">
            {resena.studentName ?? "Estudiante"} sobre {resena.professorName ?? "el profesor"}
            {resena.createdAt && ` · ${fechaCorta(resena.createdAt)}`}
          </p>
        </div>
      </div>

      {resena.comment && (
        <p className="mt-3 rounded-base bg-surface-sunken px-4 py-3 text-[13px] text-text">
          “{resena.comment}”
        </p>
      )}

      {resena.reportedReason && (
        <p className="mt-3 flex items-start gap-2 text-[12.5px] text-text-secondary">
          <Flag size={14} strokeWidth={2} className="mt-0.5 shrink-0 text-warning" />
          <span>
            <span className="font-semibold">El profesor reportó:</span> {resena.reportedReason}
          </span>
        </p>
      )}

      <div className="mt-4 border-t border-border pt-3">
        {error && (
          <div className="mb-2">
            <AvisoError mensaje={error} />
          </div>
        )}
        <label className="block text-[12.5px] font-bold text-text-secondary" htmlFor={`motivo-${resena.id}`}>
          Motivo para ocultarla
        </label>
        <div className="mt-1.5 flex flex-wrap items-center gap-2">
          <Campo
            id={`motivo-${resena.id}`}
            type="text"
            value={motivo}
            onChange={(event) => setMotivo(event.target.value)}
            maxLength={300}
            placeholder="Lenguaje ofensivo, datos personales…"
            className="min-w-[220px] flex-1"
          />
          <Boton
            variante="peligro"
            className="h-11 px-4 text-[13px]"
            disabled={!motivo.trim() || ocultar.isPending}
            onClick={() => ocultar.mutate()}
          >
            {ocultar.isPending ? <Spinner /> : <EyeOff size={15} strokeWidth={1.75} />}
            Ocultar
          </Boton>
        </div>
      </div>
    </Tarjeta>
  );
}
