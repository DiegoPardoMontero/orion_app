"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRef, useState } from "react";
import { Check, ExternalLink, ShieldCheck, Upload } from "lucide-react";
import { AvisoError, Cargando } from "@/components/estados";
import { Modal } from "@/components/Modal";
import { BotonVerDocumento } from "@/components/VisorDeDocumento";
import { TextoLegal } from "@/components/cuenta/Adolescentes";
import { Badge, Boton, Spinner } from "@/components/ui";
import { ApiError, apiFetch, uploadFile } from "@/lib/api/fetch";
import type { DocumentView } from "@/lib/api/types";
import type { HabilitacionAdolescentes } from "@/lib/adolescentes";

type DocumentoLegalData = { code: string; version: string; title: string; body: string; effectiveFrom: string };

/**
 * Los dos certificados, cada uno con el enlace a la página oficial donde se consulta (pedido de Pardo
 * del 15/10/2026: que nadie tenga que buscarla en Google). Las dos son de la Policía Nacional y se
 * verificaron el 15/10/2026.
 */
export const DOCUMENTOS: {
  tipo: "REGISTRY_CHECK" | "BACKGROUND_CHECK";
  titulo: string;
  ayuda: string;
  clave: "registryCheck" | "backgroundCheck";
  enlace: { href: string; texto: string };
}[] = [
  {
    tipo: "REGISTRY_CHECK",
    titulo: "Consulta del Registro de inhabilidades",
    ayuda: "El certificado de la Policía Nacional (Ley 1918 de 2018), en PDF o foto. Se saca en línea en minutos.",
    clave: "registryCheck",
    enlace: { href: "https://inhabilidades.policia.gov.co:8080/", texto: "Consultar en la Policía Nacional" },
  },
  {
    tipo: "BACKGROUND_CHECK",
    titulo: "Certificado de antecedentes judiciales",
    ayuda: "El de la Policía Nacional, vigente, en PDF o foto.",
    clave: "backgroundCheck",
    enlace: { href: "https://antecedentes.policia.gov.co:7005/WebJudicial/", texto: "Consultar en la Policía Nacional" },
  },
];

/**
 * «Clases con adolescentes» en Mi perfil (Bloque 12): el profe acepta el código de conducta y sube los
 * dos documentos; el equipo de Orión los revisa y lo habilita por un año. Hasta entonces, no aparece
 * para los perfiles de adolescentes.
 */
export function ClasesConAdolescentes() {
  const qc = useQueryClient();
  const estado = useQuery({
    queryKey: ["me", "profile", "minors"],
    queryFn: () => apiFetch<HabilitacionAdolescentes>("/api/v1/me/profile/minors"),
  });
  const [leyendo, setLeyendo] = useState(false);
  // Los certificados vienen con la habilitación, no con la postulación: un profe aprobado ya no la edita.
  const documentos = ((estado.data as { documents?: DocumentView[] } | undefined)?.documents ?? []) as DocumentView[];

  const refrescar = () => {
    void qc.invalidateQueries({ queryKey: ["me", "profile", "minors"] });
  };

  if (estado.isPending) return <Cargando filas={2} />;
  if (estado.isError) return <AvisoError mensaje="No pudimos cargar tu habilitación." />;
  const h = estado.data;

  return (
    <div className="grid gap-4">
      <div className="flex flex-wrap items-center gap-2">
        {h.active ? (
          <Badge tono="menta"><ShieldCheck size={12} strokeWidth={2.4} /> Habilitado hasta el {fecha(h.clearedUntil!)}</Badge>
        ) : h.clearedUntil ? (
          <Badge tono="melocoton">Habilitación vencida el {fecha(h.clearedUntil)}</Badge>
        ) : h.missing.length === 0 ? (
          <Badge tono="lavanda">Listo: el equipo de Orión lo revisa y te habilita</Badge>
        ) : (
          <Badge tono="neutral">Sin habilitar</Badge>
        )}
      </div>
      <p className="text-[13.5px] leading-relaxed text-text-secondary">
        Para dar clase a alguien de 13 a 17 años hacen falta tres cosas. Cuando estén, el equipo de Orión
        las revisa y te habilita por un año; mientras tanto no apareces para esos perfiles.
      </p>

      <ol className="grid gap-3">
        <Paso listo={h.conductAccepted} titulo="Aceptar el código de conducta para clases con menores">
          {h.conductAccepted ? (
            <span className="text-[12.5px] text-text-muted">Aceptado.</span>
          ) : (
            <Boton variante="contorno" className="h-10" onClick={() => setLeyendo(true)}>Leer y aceptar</Boton>
          )}
        </Paso>
        {DOCUMENTOS.map((d) => (
          <Paso key={d.tipo} listo={h[d.clave]} titulo={d.titulo} ayuda={d.ayuda}>
            <a
              href={d.enlace.href}
              target="_blank"
              rel="noopener noreferrer"
              className="mb-2 inline-flex items-center gap-1.5 text-[13px] font-semibold text-primary underline-offset-2 hover:underline focus-visible:shadow-focus"
            >
              <ExternalLink size={13} strokeWidth={2.2} /> {d.enlace.texto}
            </a>
            <Subida tipo={d.tipo} subidos={documentos.filter((x) => x.docType === d.tipo)} onCambio={refrescar} />
          </Paso>
        ))}
      </ol>

      {leyendo && <ModalCodigo onCerrar={() => setLeyendo(false)} onAceptado={refrescar} />}
    </div>
  );
}

function Paso({ listo, titulo, ayuda, children }: { listo: boolean; titulo: string; ayuda?: string; children: React.ReactNode }) {
  return (
    <li className="flex items-start gap-3 rounded-base border border-border px-4 py-3">
      <span className={`mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full ${listo ? "bg-success/15 text-success" : "bg-surface-sunken text-text-muted"}`}>
        {listo ? <Check size={14} strokeWidth={3} /> : <span className="h-1.5 w-1.5 rounded-full bg-current" />}
      </span>
      <div className="min-w-0 flex-1">
        <p className="font-semibold text-text">{titulo}</p>
        {ayuda && !listo && <p className="mt-0.5 text-[12.5px] text-text-secondary">{ayuda}</p>}
        <div className="mt-2">{children}</div>
      </div>
    </li>
  );
}

function Subida({ tipo, subidos, onCambio }: { tipo: string; subidos: DocumentView[]; onCambio: () => void }) {
  const input = useRef<HTMLInputElement>(null);
  const subir = useMutation({
    // Por Mi perfil, no por la postulación: la postulación aprobada responde 409 (15/10/2026).
    mutationFn: (file: File) => uploadFile<HabilitacionAdolescentes>("/api/v1/me/profile/minors/documents", file, { docType: tipo }),
    onSuccess: onCambio,
  });
  const borrar = useMutation({
    mutationFn: (id: string) => apiFetch<HabilitacionAdolescentes>(`/api/v1/me/profile/minors/documents/${id}`, { method: "DELETE" }),
    onSuccess: onCambio,
  });
  const error = subir.error instanceof ApiError ? subir.error.message : borrar.error instanceof ApiError ? borrar.error.message : null;
  return (
    <div className="grid gap-2">
      {subidos.map((d) => (
        <div key={d.id} className="flex items-center justify-between gap-2 text-[13px]">
          <span className="min-w-0 flex-1 truncate text-text">{d.fileName}</span>
          {d.id && <BotonVerDocumento nombre={d.fileName ?? ""} contentType={d.contentType ?? ""} url={`/api/v1/me/teacher-documents/${d.id}/content`} compacto />}
          <button type="button" onClick={() => d.id && borrar.mutate(d.id)} className="shrink-0 text-text-muted underline underline-offset-2 hover:text-error">
            Quitar
          </button>
        </div>
      ))}
      <div>
        <input
          ref={input}
          type="file"
          accept="application/pdf,image/*"
          className="hidden"
          onChange={(e) => {
            const file = e.target.files?.[0];
            if (file) subir.mutate(file);
            e.target.value = "";
          }}
        />
        <Boton variante="contorno" className="h-10" disabled={subir.isPending} onClick={() => input.current?.click()}>
          {subir.isPending ? <Spinner /> : <Upload size={14} strokeWidth={2} />}
          {subidos.length > 0 ? "Subir otro" : "Subir el documento"}
        </Boton>
      </div>
      {error && <AvisoError mensaje={error} />}
    </div>
  );
}

function ModalCodigo({ onCerrar, onAceptado }: { onCerrar: () => void; onAceptado: () => void }) {
  const texto = useQuery({
    queryKey: ["legal", "MINORS_CODE_OF_CONDUCT"],
    queryFn: () => apiFetch<DocumentoLegalData>("/api/v1/legal/MINORS_CODE_OF_CONDUCT", { redirectOn401: false }),
    staleTime: 10 * 60_000,
  });
  const [acepto, setAcepto] = useState(false);
  const aceptar = useMutation({
    mutationFn: () => apiFetch<HabilitacionAdolescentes>("/api/v1/me/profile/minors/conduct", { method: "POST", body: {} }),
    onSuccess: () => {
      onAceptado();
      onCerrar();
    },
  });
  const error = aceptar.error instanceof ApiError ? aceptar.error.message : null;
  return (
    <Modal titulo="Código de conducta para clases con adolescentes" onCerrar={onCerrar}>
      <div className="max-h-[45vh] overflow-y-auto rounded-base border border-border bg-surface-sunken p-4 text-[13.5px] leading-relaxed text-text-secondary">
        {texto.isPending && <Cargando filas={2} />}
        {texto.isError && <AvisoError mensaje="No pudimos cargar el código." />}
        {texto.data && <TextoLegal body={texto.data.body} version={texto.data.version} />}
      </div>
      <label className="mt-4 flex items-start gap-3 text-[14px]">
        <input type="checkbox" checked={acepto} onChange={(e) => setAcepto(e.target.checked)} className="mt-1 h-5 w-5" />
        <span>Leí el código y me comprometo a cumplirlo en cada clase con un adolescente.</span>
      </label>
      {error && <div className="mt-3"><AvisoError mensaje={error} /></div>}
      <div className="mt-5 flex gap-2.5">
        <Boton variante="contorno" onClick={onCerrar} className="h-11 flex-1">Cancelar</Boton>
        <Boton disabled={!acepto || aceptar.isPending || !texto.data} onClick={() => aceptar.mutate()} className="h-11 flex-1">
          {aceptar.isPending ? <Spinner /> : "Aceptar"}
        </Boton>
      </div>
    </Modal>
  );
}

function fecha(iso: string): string {
  const [a, m, d] = iso.split("-").map(Number);
  return new Date(a, m - 1, d).toLocaleDateString("es-CO", { day: "numeric", month: "long", year: "numeric" });
}
