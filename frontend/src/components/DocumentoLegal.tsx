import Link from "next/link";
import { Fragment } from "react";
import type { Bloque, Inline } from "@/lib/markdownLegal";
import { anclaDe, parseMarkdown } from "@/lib/markdownLegal";

export type DocumentoLegalData = {
  code: string;
  version: string;
  title: string;
  body: string;
  effectiveFrom: string;
};

/**
 * Pinta un documento legal. Server component: no hay nada interactivo y el texto tiene que estar
 * en el HTML que llega, no detrás de un fetch de cliente — un buscador y un lector de pantalla
 * tienen que poder leerlo, y quien lo abra desde el registro no puede quedarse mirando un spinner.
 *
 * La medida de línea es corta a propósito. Un documento legal que nadie puede leer cumple la letra
 * de la ley y falla en lo único que importa, que es que se entienda.
 */
export function DocumentoLegal({ doc }: { doc: DocumentoLegalData }) {
  const bloques = parseMarkdown(doc.body);

  return (
    <article className="mx-auto w-full max-w-[68ch] px-6 py-10 lg:py-16">
      <p className="text-[12px] font-bold uppercase tracking-[0.14em] text-primary-strong">
        Orión · Documento legal
      </p>
      <h1 className="mt-2 font-display text-[30px] font-bold leading-tight lg:text-[38px]">
        {doc.title}
      </h1>
      <p className="mt-3 text-[13px] text-text-muted">
        Versión {doc.version} · vigente desde el {formatearFecha(doc.effectiveFrom)}
      </p>

      <div className="mt-8">
        {bloques.map((bloque, i) => (
          <BloqueLegal key={i} bloque={bloque} />
        ))}
      </div>
    </article>
  );
}

function BloqueLegal({ bloque }: { bloque: Bloque }) {
  switch (bloque.tipo) {
    case "titulo":
      return bloque.nivel === 2 ? (
        <h2 id={anclaDe(bloque.contenido)} className="mt-9 scroll-mt-24 font-display text-[20px] font-bold leading-snug lg:text-[22px]">
          <Inlines contenido={bloque.contenido} />
        </h2>
      ) : (
        <h3 className="mt-6 font-display text-[16px] font-bold">
          <Inlines contenido={bloque.contenido} />
        </h3>
      );

    case "parrafo":
      return (
        <p className="mt-3 text-[15px] leading-relaxed text-text-secondary">
          <Inlines contenido={bloque.contenido} />
        </p>
      );

    case "lista": {
      const clases = "mt-3 grid gap-2 pl-5 text-[15px] leading-relaxed text-text-secondary";
      const items = bloque.items.map((item, i) => (
        <li key={i} className="pl-1">
          <Inlines contenido={item} />
        </li>
      ));
      return bloque.ordenada ? (
        <ol className={`${clases} list-decimal`}>{items}</ol>
      ) : (
        <ul className={`${clases} list-disc`}>{items}</ul>
      );
    }

    case "tabla":
      return (
        // La tabla desborda dentro de su propia caja: en móvil, una tabla de tres columnas no cabe
        // y lo que no puede pasar es que empuje la página entera a lo ancho.
        <div className="mt-4 overflow-x-auto rounded-base border border-border">
          <table className="w-full border-collapse text-[14px]">
            {tieneEncabezados(bloque.encabezados) && (
              <thead>
                <tr>
                  {bloque.encabezados.map((celda, i) => (
                    <th
                      key={i}
                      className="border-b border-border bg-surface-sunken px-4 py-2.5 text-left font-display text-[12px] font-bold uppercase tracking-[0.06em] text-text-muted"
                    >
                      <Inlines contenido={celda} />
                    </th>
                  ))}
                </tr>
              </thead>
            )}
            <tbody>
              {bloque.filas.map((fila, i) => (
                <tr key={i}>
                  {fila.map((celda, j) => (
                    <td
                      key={j}
                      className="border-b border-border px-4 py-2.5 align-top text-text-secondary last:border-b-0"
                    >
                      <Inlines contenido={celda} />
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      );
  }
}

/** Una tabla de dos columnas sin encabezados se usa como ficha de datos; no le pintamos cabecera. */
function tieneEncabezados(encabezados: Inline[][]): boolean {
  return encabezados.some((celda) => celda.some((parte) => parte.texto.trim() !== ""));
}

function Inlines({ contenido }: { contenido: Inline[] }) {
  return (
    <>
      {contenido.map((parte, i) => {
        if (parte.tipo === "fuerte") {
          return (
            <strong key={i} className="font-bold text-text">
              {parte.texto}
            </strong>
          );
        }
        if (parte.tipo === "enlace") {
          const externo = /^https?:\/\//.test(parte.href);
          return externo ? (
            <a
              key={i}
              href={parte.href}
              target="_blank"
              rel="noreferrer noopener"
              className="font-semibold text-primary-strong underline underline-offset-2"
            >
              {parte.texto}
            </a>
          ) : (
            <Link
              key={i}
              href={parte.href}
              className="font-semibold text-primary-strong underline underline-offset-2"
            >
              {parte.texto}
            </Link>
          );
        }
        return <Fragment key={i}>{parte.texto}</Fragment>;
      })}
    </>
  );
}

/** «8 de septiembre de 2026». La fecha llega como ISO desde el backend. */
function formatearFecha(iso: string): string {
  const [anio, mes, dia] = iso.split("-").map(Number);
  const meses = [
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
  ];
  return `${dia} de ${meses[mes - 1]} de ${anio}`;
}
