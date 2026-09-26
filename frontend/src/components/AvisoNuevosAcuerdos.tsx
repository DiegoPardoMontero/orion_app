"use client";

import { useMutation, useQueries, useQueryClient } from "@tanstack/react-query";
import { ChevronDown, FileText } from "lucide-react";
import { useState } from "react";
import { CuerpoLegal, type DocumentoLegalData } from "@/components/DocumentoLegal";
import { AvisoError, ErrorCarga } from "@/components/estados";
import { Modal } from "@/components/Modal";
import { Boton, BotonPrincipal, Spinner } from "@/components/ui";
import { PENDIENTES_KEY } from "@/lib/acuerdo";
import { ApiError, apiFetch } from "@/lib/api/fetch";
import { whatsappSoporte } from "@/lib/config";

/** De qué trata cada documento, en una línea: lo completo se despliega con un botón. */
const RESUMEN: Record<string, string> = {
  TERMS: "Cómo funciona Orión: reservas, pagos, cancelaciones y reclamos.",
  PRIVACY: "Qué datos tuyos tratamos, para qué, y cómo ejercer tus derechos.",
  TEACHER_AGREEMENT:
    "Incluye el mandato de recaudo: Orión recibe en tu nombre lo que pagan tus estudiantes y te lo entrega cada quincena, menos la comisión.",
};

/**
 * Los acuerdos que le faltan a quien entra: los Términos y la política vigentes, y al profe aprobado
 * el acuerdo del profesor (Pardo, 26/09/2026). Un solo «Aceptar los nuevos acuerdos» y un botón que
 * los despliega todos, en vez de un documento metido en una caja con scroll.
 *
 * <p>No se puede cerrar ni aplazar, como el WhatsApp: «todos van a entrar a aceptar los nuevos
 * acuerdos». La autorización de datos lleva su propia casilla: empaquetarla con los términos la
 * viciaría (Decreto 1377 de 2013), igual que en el registro.
 */
export function AvisoNuevosAcuerdos({ documentos }: { documentos: string[] }) {
  const queryClient = useQueryClient();
  const [abiertos, setAbiertos] = useState(false);
  const [autorizaDatos, setAutorizaDatos] = useState(false);
  const docs = useQueries({
    queries: documentos.map((code) => ({
      queryKey: ["legal", code],
      queryFn: () => apiFetch<DocumentoLegalData>(`/api/v1/legal/${code}`, { redirectOn401: false }),
      staleTime: 10 * 60_000,
    })),
  });
  const aceptar = useMutation({
    mutationFn: () => apiFetch<void>("/api/v1/me/legal/accept", { method: "POST", body: { documents: documentos } }),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: PENDIENTES_KEY }),
  });

  const cargados = docs.every((d) => d.data);
  const fallo = docs.find((d) => d.isError);
  const pideDatos = documentos.includes("PRIVACY");
  const listo = cargados && (!pideDatos || autorizaDatos);
  const nombres = docs.map((d) => (d.data ? `${d.data.title} (versión ${d.data.version})` : "")).filter(Boolean);
  const whatsapp = whatsappSoporte("Hola, tengo una duda sobre los acuerdos de Orión.");

  const error =
    aceptar.error instanceof ApiError
      ? aceptar.error.message
      : aceptar.isError
        ? "No pudimos guardar tu aceptación. Inténtalo de nuevo."
        : null;

  return (
    <Modal
      titulo="Acepta los nuevos acuerdos"
      bloqueante
      amplio
      onCerrar={() => {
        /* sin salida a propósito: para seguir usando Orión hay que aceptarlos */
      }}
    >
      <p className="text-[14px] leading-relaxed text-text-secondary">
        Para seguir usando Orión necesitas aceptar {documentos.length === 1 ? "este acuerdo" : "estos acuerdos"}.
      </p>

      <ul className="mt-4 grid gap-2">
        {documentos.map((code, i) => {
          const doc = docs[i]?.data;
          return (
            <li key={code} className="flex items-start gap-3 rounded-base border border-border p-3.5">
              <FileText size={18} strokeWidth={1.8} className="mt-0.5 shrink-0 text-primary-strong" />
              <div className="min-w-0">
                <p className="text-[14px] font-bold text-text">
                  {doc?.title ?? "Cargando…"}
                  {doc && <span className="font-semibold text-text-muted"> · versión {doc.version}</span>}
                </p>
                <p className="mt-0.5 text-[13px] leading-snug text-text-secondary">{RESUMEN[code]}</p>
              </div>
            </li>
          );
        })}
      </ul>

      {fallo ? (
        <div className="mt-4">
          <ErrorCarga mensaje="No pudimos cargar los acuerdos." onReintentar={() => docs.forEach((d) => void d.refetch())} />
        </div>
      ) : (
        <Boton
          variante="contorno"
          aria-expanded={abiertos}
          disabled={!cargados}
          onClick={() => setAbiertos((a) => !a)}
          className="mt-3 h-11 w-full"
        >
          {abiertos ? "Ocultar los acuerdos" : "Ver los acuerdos completos"}
          <ChevronDown size={16} strokeWidth={2} className={`transition-transform ${abiertos ? "rotate-180" : ""}`} />
        </Boton>
      )}

      {abiertos && (
        <div className="mt-4 grid gap-6">
          {docs.map((d) =>
            d.data ? (
              <section key={d.data.code} aria-label={d.data.title} className="rounded-base bg-surface-sunken px-4 pb-4 pt-1">
                <h3 className="pt-3 font-display text-[18px] font-bold">
                  {d.data.title} <span className="text-[14px] font-semibold text-text-muted">· versión {d.data.version}</span>
                </h3>
                <CuerpoLegal body={d.data.body} />
              </section>
            ) : null,
          )}
        </div>
      )}

      {pideDatos && (
        <label
          htmlFor="autoriza-datos"
          className="mt-4 flex cursor-pointer items-start gap-3 rounded-base border border-border p-4 text-[13.5px] leading-relaxed text-text"
        >
          <input
            id="autoriza-datos"
            type="checkbox"
            checked={autorizaDatos}
            onChange={(event) => setAutorizaDatos(event.target.checked)}
            className="mt-[3px] h-[18px] w-[18px] shrink-0 cursor-pointer accent-primary focus-visible:shadow-focus"
          />
          <span>Autorizo el tratamiento de mis datos personales conforme a la Política de tratamiento.</span>
        </label>
      )}

      {error && (
        <div className="mt-4">
          <AvisoError mensaje={error} />
        </div>
      )}

      {nombres.length > 0 && (
        <p className="mt-4 text-[12.5px] leading-relaxed text-text-muted">
          Al tocar «Aceptar los nuevos acuerdos» aceptas {enLista(nombres)}.
        </p>
      )}
      <BotonPrincipal type="button" disabled={!listo || aceptar.isPending} onClick={() => aceptar.mutate()} className="mt-3">
        {aceptar.isPending ? (
          <>
            <Spinner />
            Guardando…
          </>
        ) : (
          "Aceptar los nuevos acuerdos"
        )}
      </BotonPrincipal>

      {whatsapp && (
        <p className="mt-3 text-center text-[12px] text-text-muted">
          ¿Dudas antes de aceptar?{" "}
          <a href={whatsapp} target="_blank" rel="noopener noreferrer" className="font-bold text-primary-strong hover:underline">
            Escríbenos por WhatsApp
          </a>
          .
        </p>
      )}
    </Modal>
  );
}

/** «a», «a y b», «a, b y c». */
function enLista(partes: string[]): string {
  if (partes.length <= 1) return partes.join("");
  return `${partes.slice(0, -1).join(", ")} y ${partes[partes.length - 1]}`;
}
