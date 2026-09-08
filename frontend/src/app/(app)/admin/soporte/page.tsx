"use client";

import Link from "next/link";
import { Cargando, ErrorCarga, Vacio } from "@/components/estados";
import { Badge, Tarjeta } from "@/components/ui";
import { fechaRelativa } from "@/lib/format";
import {
  ETIQUETA_ESTADO,
  TONO_ESTADO,
  diasParaVencer,
  useBandejaSoporte,
} from "@/lib/soporte";

/**
 * La bandeja. Ordenada por lo que vence antes y no por lo más reciente: por fecha de creación, un
 * «no me carga la foto» de hace tres días iría por delante de un reclamo de habeas data que vence
 * mañana, y el que sanciona la SIC es el segundo.
 */
export default function AdminSoportePage() {
  const bandeja = useBandejaSoporte();

  return (
    <main className="mx-auto w-full max-w-4xl px-5 py-8 lg:px-8">
      <h1 className="font-display text-h1 font-bold">Soporte</h1>
      <p className="mt-1 text-[14px] text-text-secondary">
        Lo que espera respuesta, con lo que vence antes primero.
      </p>

      {bandeja.isPending && <Cargando filas={3} />}
      {bandeja.isError && (
        <ErrorCarga mensaje="No pudimos cargar la bandeja." onReintentar={() => bandeja.refetch()} />
      )}
      {bandeja.data?.length === 0 && (
        <div className="mt-5">
          <Vacio titulo="Nada por responder" texto="No hay solicitudes abiertas ahora mismo." />
        </div>
      )}

      <div className="mt-5 grid gap-2">
        {bandeja.data?.map((t) => {
          const dias = t.dueAt ? diasParaVencer(t.dueAt) : null;
          return (
            <Link key={t.code} href={`/admin/soporte/${t.code}`} className="block">
              <Tarjeta className="border border-border transition-colors hover:border-border-strong">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <p className="font-display text-[15px] font-bold">{t.subject}</p>
                  <div className="flex items-center gap-2">
                    {dias !== null && (
                      <Badge tono={t.overdue ? "error" : dias <= 2 ? "melocoton" : "neutral"}>
                        {t.overdue
                          ? "Plazo vencido"
                          : dias === 0
                            ? "Vence hoy"
                            : `${dias} día${dias === 1 ? "" : "s"}`}
                      </Badge>
                    )}
                    <Badge tono={TONO_ESTADO[t.status]}>{ETIQUETA_ESTADO[t.status]}</Badge>
                  </div>
                </div>
                <p className="mt-1 text-[12.5px] text-text-muted">
                  <span className="mono">{t.code}</span> · {t.categoryLabel} ·{" "}
                  {fechaRelativa(t.createdAt)}
                </p>
              </Tarjeta>
            </Link>
          );
        })}
      </div>
    </main>
  );
}
