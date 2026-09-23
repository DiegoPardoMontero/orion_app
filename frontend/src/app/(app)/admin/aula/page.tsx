"use client";

import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api/fetch";
import { Cargando, ErrorCarga, Vacio } from "@/components/estados";
import { Tarjeta } from "@/components/ui";

type AulaDelProfesor = {
  profesorId: string;
  nombre: string;
  clases: number;
  parteDelEstudiante: number | null;
  minutosDeRetrasoPromedio: number | null;
  llegadasTarde: number;
};

/**
 * Lo que cuentan las salas en los últimos 90 días, por profesor: qué parte de la palabra tuvieron
 * sus estudiantes y cuánto tardó en entrar. Sale de los webhooks de JaaS.
 *
 * <p>Es informativo (Pardo, 22/09/2026): la tardanza no alimenta strikes ni sanciones. Sirve para
 * conversar con un profesor, no para castigarlo.
 */
export default function AdminAulaPage() {
  const datos = useQuery({
    queryKey: ["admin", "classroom-stats"],
    queryFn: () => apiFetch<AulaDelProfesor[]>("/api/v1/admin/classroom-stats"),
  });

  return (
    <main className="mx-auto w-full max-w-4xl px-5 py-8 lg:px-8">
      <h1 className="font-display text-h1 font-bold">Aula</h1>
      <p className="mt-1 text-[14px] text-text-secondary">
        Últimos 90 días. Cuánto hablaron los estudiantes de cada profesor y su puntualidad. Solo
        informativo: no genera sanciones.
      </p>

      {datos.isPending && <Cargando filas={3} />}
      {datos.isError && (
        <ErrorCarga mensaje="No pudimos cargar los datos del aula." onReintentar={() => datos.refetch()} />
      )}
      {datos.data?.length === 0 && (
        <div className="mt-5">
          <Vacio
            titulo="Todavía no hay datos del aula"
            texto="Aparecen cuando el webhook de JaaS esté configurado y se dicten clases (Sistema lo muestra)."
          />
        </div>
      )}

      <div className="mt-5 grid gap-2">
        {datos.data?.map((p) => (
          <Tarjeta key={p.profesorId} className="border border-border">
            <div className="flex flex-wrap items-baseline justify-between gap-2">
              <p className="font-display text-[15px] font-bold">{p.nombre}</p>
              <p className="text-[12.5px] text-text-muted">
                {p.clases} {p.clases === 1 ? "clase medida" : "clases medidas"}
              </p>
            </div>
            <dl className="mt-3 grid grid-cols-3 gap-3 text-[13px]">
              <div>
                <dt className="text-text-muted">Hablaron sus estudiantes</dt>
                <dd className="mt-0.5 font-display text-[18px] font-bold tabular-nums">
                  {p.parteDelEstudiante == null ? "—" : `${Math.round(p.parteDelEstudiante * 100)} %`}
                </dd>
              </div>
              <div>
                <dt className="text-text-muted">Retraso promedio</dt>
                <dd className="mt-0.5 font-display text-[18px] font-bold tabular-nums">
                  {p.minutosDeRetrasoPromedio == null ? "—" : `${Math.round(p.minutosDeRetrasoPromedio)} min`}
                </dd>
              </div>
              <div>
                <dt className="text-text-muted">Llegó tarde (&gt;5 min)</dt>
                <dd className="mt-0.5 font-display text-[18px] font-bold tabular-nums">{p.llegadasTarde}</dd>
              </div>
            </dl>
          </Tarjeta>
        ))}
      </div>
    </main>
  );
}
