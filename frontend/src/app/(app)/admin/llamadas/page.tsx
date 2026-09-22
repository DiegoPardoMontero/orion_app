"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { MessageCircle } from "lucide-react";
import { apiFetch } from "@/lib/api/fetch";
import { Cargando, ErrorCarga, Vacio } from "@/components/estados";
import { Badge, Boton, Tarjeta } from "@/components/ui";
import { fechaRelativa } from "@/lib/format";

type Solicitud = {
  id: string;
  firstName: string;
  whatsapp: string;
  createdAt: string;
  attendedAt: string | null;
};

/**
 * «¿Prefieres que te llame una persona?»: quién pidió que le escribiéramos, con lo pendiente arriba
 * y lo más antiguo primero, que es a quien más se hizo esperar. El chat de WhatsApp se abre con un
 * clic porque el número ya llega limpio.
 */
export default function AdminLlamadasPage() {
  const qc = useQueryClient();
  const bandeja = useQuery({
    queryKey: ["admin", "callback-requests"],
    queryFn: () => apiFetch<Solicitud[]>("/api/v1/admin/callback-requests"),
  });

  const atender = useMutation({
    mutationFn: (id: string) =>
      apiFetch<Solicitud>(`/api/v1/admin/callback-requests/${id}/attend`, { method: "POST" }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["admin", "callback-requests"] }),
  });

  return (
    <main className="mx-auto w-full max-w-4xl px-5 py-8 lg:px-8">
      <h1 className="font-display text-h1 font-bold">Llamadas</h1>
      <p className="mt-1 text-[14px] text-text-secondary">
        Quienes prefirieron que una persona les escribiera antes de hablar con Meissa.
      </p>

      {bandeja.isPending && <Cargando filas={3} />}
      {bandeja.isError && (
        <ErrorCarga mensaje="No pudimos cargar las solicitudes." onReintentar={() => bandeja.refetch()} />
      )}
      {bandeja.data?.length === 0 && (
        <div className="mt-5">
          <Vacio titulo="Nadie esperando" texto="Cuando alguien deje su número, aparece aquí." />
        </div>
      )}

      <div className="mt-5 grid gap-2">
        {bandeja.data?.map((s) => (
          <Tarjeta key={s.id} className="border border-border">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <p className="font-display text-[15px] font-bold">{s.firstName}</p>
                <p className="mt-0.5 text-[12.5px] text-text-muted">
                  <span className="mono">{s.whatsapp}</span> · {fechaRelativa(s.createdAt)}
                </p>
              </div>
              <div className="flex items-center gap-2">
                {s.attendedAt ? (
                  <Badge tono="menta">Atendida</Badge>
                ) : (
                  <>
                    <a
                      href={`https://wa.me/${s.whatsapp.replace("+", "")}`}
                      target="_blank"
                      rel="noreferrer"
                      className="inline-flex min-h-11 items-center gap-1.5 rounded-pill border-[1.5px] border-border px-4 text-[14px] font-bold text-text hover:bg-surface-sunken focus-visible:shadow-focus"
                    >
                      <MessageCircle size={16} strokeWidth={2} />
                      Escribirle
                    </a>
                    <Boton
                      variante="primario"
                      disabled={atender.isPending}
                      onClick={() => atender.mutate(s.id)}
                    >
                      Marcar atendida
                    </Boton>
                  </>
                )}
              </div>
            </div>
          </Tarjeta>
        ))}
      </div>
    </main>
  );
}
