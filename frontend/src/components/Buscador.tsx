"use client";

import { ChevronDown } from "lucide-react";
import { useEffect, useId, useMemo, useRef, useState, type ReactNode } from "react";

export type Opcion = {
  valor: string;
  etiqueta: string;
  /** Lo que va a la derecha, más tenue: el departamento de una ciudad. */
  detalle?: string;
  /** Lo que va antes de la etiqueta: la bandera de un país. */
  prefijo?: ReactNode;
};

/** Sin tildes ni mayúsculas: «bogota» encuentra «Bogotá». */
export function normalizar(texto: string): string {
  return texto
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .toLowerCase()
    .trim();
}

const MAXIMO = 60;

/**
 * Un desplegable en el que se escribe para buscar (Pardo, 27/09/2026: «un dropdown donde pueda
 * escribir y se busque automáticamente»). Es un combobox de ARIA: flechas para moverse, Enter para
 * elegir, Escape para cerrar, y el lector de pantalla anuncia la opción activa.
 *
 * <p>Con `libre`, lo escrito que no está en la lista también se puede elegir («Usar «…»»): una ciudad
 * pequeña que no está en el catálogo no puede dejar a nadie sin postularse.
 */
export function Buscador({
  id,
  opciones,
  valor,
  onElegir,
  placeholder,
  libre = false,
  disabled,
  cargando = false,
  className = "",
}: {
  id: string;
  opciones: Opcion[];
  /** El valor elegido; se muestra su etiqueta mientras no se esté escribiendo. */
  valor: string;
  onElegir: (valor: string) => void;
  placeholder?: string;
  libre?: boolean;
  disabled?: boolean;
  cargando?: boolean;
  className?: string;
}) {
  const listaId = useId();
  const input = useRef<HTMLInputElement>(null);
  const [abierto, setAbierto] = useState(false);
  const [texto, setTexto] = useState<string | null>(null);
  const [activa, setActiva] = useState(0);
  // Lo escrito, visto desde el temporizador del blur: si entretanto se eligió una opción con el
  // ratón, ya vale null y el blur no la pisa.
  const textoActual = useRef<string | null>(null);
  useEffect(() => {
    textoActual.current = texto;
  }, [texto]);

  const elegida = opciones.find((o) => o.valor === valor);
  const mostrado = texto ?? elegida?.etiqueta ?? valor;

  const visibles = useMemo(() => {
    const q = normalizar(texto ?? "");
    if (!q) return opciones.slice(0, MAXIMO);
    const empiezan: Opcion[] = [];
    const contienen: Opcion[] = [];
    for (const o of opciones) {
      const e = normalizar(o.etiqueta);
      if (e.startsWith(q) || normalizar(o.valor) === q) empiezan.push(o);
      else if (e.includes(q) || (o.detalle && normalizar(o.detalle).includes(q))) contienen.push(o);
      if (empiezan.length >= MAXIMO) break;
    }
    return [...empiezan, ...contienen].slice(0, MAXIMO);
  }, [opciones, texto]);

  const escrito = (texto ?? "").trim();
  const ofreceLibre =
    libre && escrito !== "" && !visibles.some((o) => normalizar(o.etiqueta) === normalizar(escrito));
  const total = visibles.length + (ofreceLibre ? 1 : 0);

  const elegir = (v: string) => {
    onElegir(v);
    setTexto(null);
    setAbierto(false);
  };

  const onKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "ArrowDown") {
      event.preventDefault();
      setAbierto(true);
      setActiva((a) => Math.min(a + 1, Math.max(total - 1, 0)));
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      setActiva((a) => Math.max(a - 1, 0));
    } else if (event.key === "Enter" && abierto && total > 0) {
      event.preventDefault();
      if (activa < visibles.length) elegir(visibles[activa].valor);
      else elegir(escrito);
    } else if (event.key === "Escape") {
      setAbierto(false);
      setTexto(null);
    }
  };

  return (
    <div className={`relative ${className}`}>
      {elegida?.prefijo && texto === null && (
        <span className="pointer-events-none absolute left-[18px] top-1/2 -translate-y-1/2 text-[17px]" aria-hidden>
          {elegida.prefijo}
        </span>
      )}
      <input
        ref={input}
        id={id}
        role="combobox"
        aria-expanded={abierto}
        aria-controls={listaId}
        aria-autocomplete="list"
        aria-activedescendant={abierto && total > 0 ? `${listaId}-${activa}` : undefined}
        autoComplete="off"
        disabled={disabled}
        placeholder={cargando ? "Cargando…" : placeholder}
        value={mostrado}
        onChange={(event) => {
          setTexto(event.target.value);
          setActiva(0);
          setAbierto(true);
        }}
        onFocus={() => setAbierto(true)}
        onClick={() => setAbierto(true)}
        // Se cierra un poco después para que el clic en una opción alcance a contar. Lo escrito no se
        // pierde al salir del campo: si coincide con una opción, se elige esa; si no y el campo es
        // `libre`, se usa tal cual. Antes se borraba, y la ciudad escrita entera («Chía») quedaba
        // vacía y la postulación decía «Escribe o elige tu ciudad».
        onBlur={() =>
          setTimeout(() => {
            setAbierto(false);
            const escritoAlSalir = (textoActual.current ?? "").trim();
            if (textoActual.current !== null && escritoAlSalir !== "") {
              const igual = opciones.find((o) => normalizar(o.etiqueta) === normalizar(escritoAlSalir));
              if (igual) onElegir(igual.valor);
              else if (libre) onElegir(escritoAlSalir);
            }
            setTexto(null);
          }, 150)
        }
        onKeyDown={onKeyDown}
        className={`h-[52px] w-full rounded-base border-[1.5px] border-border bg-surface-raised pr-11 text-[15px] text-text transition-[border-color,box-shadow] placeholder:text-text-muted focus:border-primary focus:shadow-focus focus:outline-none disabled:opacity-60 ${
          elegida?.prefijo && texto === null ? "pl-[48px]" : "pl-[18px]"
        }`}
      />
      <ChevronDown
        size={18}
        strokeWidth={1.75}
        aria-hidden
        className="pointer-events-none absolute right-4 top-[26px] -translate-y-1/2 text-text-muted"
      />
      {abierto && !disabled && (
        <ul
          id={listaId}
          role="listbox"
          className="absolute left-0 right-0 z-30 mt-1.5 max-h-72 overflow-y-auto rounded-base border border-border bg-surface-raised py-1 shadow-lg"
        >
          {visibles.map((o, i) => (
            <li
              key={o.valor}
              id={`${listaId}-${i}`}
              role="option"
              aria-selected={o.valor === valor}
              onMouseDown={(event) => event.preventDefault()}
              onClick={() => elegir(o.valor)}
              onMouseEnter={() => setActiva(i)}
              className={`flex cursor-pointer items-center gap-2 px-4 py-2.5 text-[14.5px] ${
                i === activa ? "bg-primary-soft text-text" : "text-text"
              }`}
            >
              {o.prefijo && <span aria-hidden>{o.prefijo}</span>}
              <span className="min-w-0 flex-1 truncate">{o.etiqueta}</span>
              {o.detalle && <span className="shrink-0 text-[12.5px] text-text-muted">{o.detalle}</span>}
            </li>
          ))}
          {ofreceLibre && (
            <li
              id={`${listaId}-${visibles.length}`}
              role="option"
              aria-selected={false}
              onMouseDown={(event) => event.preventDefault()}
              onClick={() => elegir(escrito)}
              onMouseEnter={() => setActiva(visibles.length)}
              className={`cursor-pointer px-4 py-2.5 text-[14px] font-semibold text-primary-strong ${
                activa === visibles.length ? "bg-primary-soft" : ""
              }`}
            >
              Usar «{escrito}»
            </li>
          )}
          {total === 0 && (
            <li className="px-4 py-2.5 text-[13.5px] text-text-muted">{cargando ? "Cargando…" : "No encontramos nada con eso."}</li>
          )}
        </ul>
      )}
    </div>
  );
}
