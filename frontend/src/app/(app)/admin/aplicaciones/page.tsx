"use client";

import { useQuery } from "@tanstack/react-query";
import { ChevronLeft, ChevronRight } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { Cargando, ErrorCarga, Vacio } from "@/components/estados";
import { Avatar } from "@/components/Avatar";
import { Badge, Boton } from "@/components/ui";
import { apiFetch } from "@/lib/api/fetch";
import type { PagedApplications } from "@/lib/api/types";
import { estadoAplicacion, ESTADOS_ADMIN } from "@/lib/aplicacion";
import { fechaCorta } from "@/lib/format";

/**
 * Bandeja de postulaciones a profesor. El admin filtra por estado, ve la fila y entra a la ficha de
 * revisión. La paginación es la del backend (PagedApplications).
 */
export default function AdminAplicacionesPage() {
  const [estado, setEstado] = useState("");
  const [page, setPage] = useState(0);

  const params = new URLSearchParams();
  if (estado) params.set("status", estado);
  params.set("page", String(page));

  const aplicaciones = useQuery({
    queryKey: ["admin", "applications", estado, page],
    queryFn: () => apiFetch<PagedApplications>(`/api/v1/admin/teacher-applications?${params.toString()}`),
  });

  const data = aplicaciones.data;
  const totalPages = data?.totalPages ?? 0;

  return (
    <main className="mx-auto max-w-5xl px-6 py-6">
      <h1 className="font-display text-h1 font-bold">Solicitudes</h1>
      <p className="mt-1 text-[13px] text-text-secondary">Postulaciones de profesores para revisar.</p>

      <div className="mt-4 flex flex-wrap gap-2">
        {ESTADOS_ADMIN.map((opcion) => (
          <button
            key={opcion.valor || "todas"}
            type="button"
            onClick={() => {
              setEstado(opcion.valor);
              setPage(0);
            }}
            className={`rounded-base px-3.5 py-2 text-[12.5px] transition-colors ${
              estado === opcion.valor
                ? "bg-night font-bold text-on-primary"
                : "border-[1.5px] border-border bg-surface-raised font-semibold text-text-secondary hover:bg-surface-sunken"
            }`}
          >
            {opcion.etiqueta}
          </button>
        ))}
      </div>

      <div className="mt-5">
        {aplicaciones.isPending && <Cargando filas={4} />}

        {aplicaciones.isError && (
          <ErrorCarga mensaje="No pudimos cargar las solicitudes." onReintentar={() => void aplicaciones.refetch()} />
        )}

        {data && data.content?.length === 0 && (
          <Vacio titulo="Sin solicitudes" texto="Cuando alguien postule para enseñar, la verás aquí." />
        )}

        {!!data?.content?.length && (
          <div className="overflow-x-auto rounded-card bg-surface-raised shadow-md">
            <table className="w-full text-[13px]">
              <thead>
                <tr className="bg-surface text-left text-[11px] font-bold uppercase tracking-[0.1em] text-text-muted">
                  <th className="px-4 py-3">Profesor</th>
                  <th className="px-4 py-3">Estado</th>
                  <th className="px-4 py-3">Enviada</th>
                  <th className="px-4 py-3 text-right">Acción</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((sol) => {
                  const cfg = estadoAplicacion(sol.status);
                  const fecha = sol.submittedAt ?? sol.createdAt;
                  return (
                    <tr key={sol.id} className="border-t border-surface-sunken hover:bg-surface">
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-3">
                          <Avatar nombre={sol.fullName ?? ""} size="sm" />
                          <div className="min-w-0">
                            <p className="truncate font-semibold text-text">{sol.fullName}</p>
                            <p className="truncate text-[11.5px] text-text-muted">{sol.email}</p>
                          </div>
                        </div>
                      </td>
                      <td className="px-4 py-3">
                        <Badge tono={cfg.tono} punto={cfg.punto}>
                          {cfg.label}
                        </Badge>
                      </td>
                      <td className="px-4 py-3 text-text-secondary">{fecha ? fechaCorta(fecha) : "—"}</td>
                      <td className="px-4 py-3 text-right">
                        <Link href={`/admin/aplicaciones/${sol.id}`}>
                          <Boton variante="contorno" className="h-9">
                            Revisar
                          </Boton>
                        </Link>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}

        {totalPages > 1 && (
          <div className="mt-4 flex items-center justify-center gap-3">
            <button
              type="button"
              aria-label="Página anterior"
              disabled={page === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              className="grid h-10 w-10 place-items-center rounded-full border-[1.5px] border-border text-text transition-colors hover:bg-surface-sunken focus-visible:shadow-focus disabled:opacity-40"
            >
              <ChevronLeft size={18} strokeWidth={1.75} />
            </button>
            <span className="text-[13px] font-semibold text-text-secondary">
              Página {page + 1} de {totalPages}
            </span>
            <button
              type="button"
              aria-label="Página siguiente"
              disabled={page + 1 >= totalPages}
              onClick={() => setPage((p) => p + 1)}
              className="grid h-10 w-10 place-items-center rounded-full border-[1.5px] border-border text-text transition-colors hover:bg-surface-sunken focus-visible:shadow-focus disabled:opacity-40"
            >
              <ChevronRight size={18} strokeWidth={1.75} />
            </button>
          </div>
        )}
      </div>
    </main>
  );
}
