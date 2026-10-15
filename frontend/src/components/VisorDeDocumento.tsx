"use client";

import { Download, ExternalLink, Eye } from "lucide-react";
import { useEffect, useState } from "react";
import { AvisoError, Cargando } from "@/components/estados";
import { Modal } from "@/components/Modal";

/**
 * El previsualizador de un documento privado (pedido de Pardo del 15/10/2026): el profe ve lo que
 * subió y el admin lo que va a revisar, sin salir de Orión. Los bytes se piden al backend con la
 * sesión y se muestran desde un blob: así no dependen de una URL de Cloudinary ni de que el
 * navegador acepte meter otro origen en un marco. Un PDF va en un marco, una imagen en una etiqueta
 * de imagen; para cualquier otro tipo queda el enlace. «Abrir en otra pestaña» y «Descargar» van
 * siempre, por si el visor del dispositivo no lo muestra.
 */
export function VisorDeDocumento({
  nombre,
  contentType,
  url,
  onCerrar,
}: {
  nombre: string;
  contentType: string;
  /** La ruta de Orión que devuelve los bytes (`…/content`). */
  url: string;
  onCerrar: () => void;
}) {
  const [estado, setEstado] = useState<{ blob?: string; error?: string }>({});

  useEffect(() => {
    let vivo = true;
    let objeto: string | null = null;
    fetch(url, { credentials: "same-origin" })
      .then(async (respuesta) => {
        if (!respuesta.ok) {
          const cuerpo = (await respuesta.json().catch(() => null)) as { error?: string } | null;
          throw new Error(cuerpo?.error ?? "No pudimos abrir el documento. Inténtalo de nuevo.");
        }
        objeto = URL.createObjectURL(await respuesta.blob());
        if (vivo) setEstado({ blob: objeto });
      })
      .catch((e: Error) => {
        if (vivo) setEstado({ error: e.message });
      });
    return () => {
      vivo = false;
      if (objeto) URL.revokeObjectURL(objeto);
    };
  }, [url]);

  const esImagen = contentType.startsWith("image/");
  const esPdf = contentType === "application/pdf";
  const descarga = `${url}${url.includes("?") ? "&" : "?"}descargar=true`;

  return (
    <Modal titulo={nombre} onCerrar={onCerrar} amplio>
      {!estado.blob && !estado.error && <Cargando filas={3} />}
      {estado.error && <AvisoError mensaje={estado.error} />}
      {estado.blob && esImagen && (
        // Un blob local no pasa por next/image: no hay nada que optimizar ni dominio que permitir.
        // eslint-disable-next-line @next/next/no-img-element
        <img src={estado.blob} alt={nombre} className="mx-auto max-h-[70vh] w-auto rounded-base" />
      )}
      {estado.blob && esPdf && (
        <iframe src={estado.blob} title={nombre} className="h-[70vh] w-full rounded-base border border-border bg-surface-sunken" />
      )}
      {estado.blob && !esImagen && !esPdf && (
        <p className="text-[14px] text-text-secondary">Este tipo de archivo no se muestra aquí: ábrelo en otra pestaña o descárgalo.</p>
      )}
      <div className="mt-4 flex flex-wrap gap-2">
        <a
          href={url}
          target="_blank"
          rel="noopener noreferrer"
          className="inline-flex items-center gap-1.5 rounded-pill border-[1.5px] border-border px-3.5 py-2 text-[12.5px] font-bold text-text transition-colors hover:bg-surface-sunken focus-visible:shadow-focus"
        >
          <ExternalLink size={14} strokeWidth={2} /> Abrir en otra pestaña
        </a>
        <a
          href={descarga}
          className="inline-flex items-center gap-1.5 rounded-pill border-[1.5px] border-border px-3.5 py-2 text-[12.5px] font-bold text-text transition-colors hover:bg-surface-sunken focus-visible:shadow-focus"
        >
          <Download size={14} strokeWidth={2} /> Descargar
        </a>
      </div>
    </Modal>
  );
}

/** El botón «Ver» que abre el visor. Va junto al nombre del archivo en cada lista de documentos. */
export function BotonVerDocumento({ nombre, contentType, url, compacto = false }: { nombre: string; contentType: string; url: string; compacto?: boolean }) {
  const [abierto, setAbierto] = useState(false);
  return (
    <>
      <button
        type="button"
        onClick={() => setAbierto(true)}
        aria-label={`Ver ${nombre}`}
        className={
          compacto
            ? "inline-flex shrink-0 items-center gap-1 text-[12.5px] font-semibold text-primary underline-offset-2 hover:underline focus-visible:shadow-focus"
            : "inline-flex shrink-0 items-center gap-1.5 rounded-pill border-[1.5px] border-border px-3.5 py-2 text-[12.5px] font-bold text-text transition-colors hover:bg-surface-raised focus-visible:shadow-focus"
        }
      >
        <Eye size={14} strokeWidth={2} /> Ver
      </button>
      {abierto && <VisorDeDocumento nombre={nombre} contentType={contentType} url={url} onCerrar={() => setAbierto(false)} />}
    </>
  );
}
