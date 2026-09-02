"use client";

import { Star } from "lucide-react";
import { useState } from "react";

/**
 * Rating honesto de un profesor. El backend solo envía `ratingAvg` cuando hay al menos 3 reseñas;
 * con menos manda `null`. Ese contrato manda aquí: con promedio se pinta la estrella, el promedio a
 * un decimal y el conteo; sin promedio NO se inventan estrellas ni números — se muestra la etiqueta
 * sutil "Nuevo en Orión". El color de la estrella es un oro cálido, fuera de la paleta de tokens a
 * propósito (el `warning` de la marca es un marrón terroso que no lee como "calificación").
 */
export function EstrellaRating({
  ratingAvg,
  ratingCount,
  conteo = "compacto",
  className = "",
}: {
  ratingAvg?: number | null;
  ratingCount?: number;
  /** "compacto" → "(12)"; "largo" → "12 reseñas". */
  conteo?: "compacto" | "largo";
  className?: string;
}) {
  if (ratingAvg == null) {
    return (
      <span
        className={`inline-flex items-center rounded-pill bg-surface-sunken px-2.5 py-1 text-[11.5px] font-semibold text-text-muted ${className}`}
      >
        Nuevo en Orión
      </span>
    );
  }

  const n = ratingCount ?? 0;
  const etiquetaConteo = conteo === "largo" ? `${n} ${n === 1 ? "reseña" : "reseñas"}` : `(${n})`;

  return (
    <span className={`inline-flex items-center gap-1 text-[13px] font-bold text-text ${className}`}>
      <Star size={14} strokeWidth={2} className="text-[#f4a935]" fill="#f4a935" aria-hidden="true" />
      <span className="tabular-nums">{ratingAvg.toFixed(1)}</span>
      {n > 0 && (
        <span className="font-semibold text-text-muted tabular-nums">{etiquetaConteo}</span>
      )}
    </span>
  );
}

/**
 * Fila de 5 estrellas para MOSTRAR el rating de una reseña individual (no editable). Rellena tantas
 * como el entero del rating (1..5). Solo lectura: para elegir estrellas usa `SelectorEstrellas`.
 */
export function EstrellasFijas({ rating, size = 15 }: { rating: number; size?: number }) {
  return (
    <span className="inline-flex items-center gap-0.5" aria-label={`${rating} de 5 estrellas`}>
      {[1, 2, 3, 4, 5].map((i) => (
        <Star
          key={i}
          size={size}
          strokeWidth={2}
          className={i <= rating ? "text-[#f4a935]" : "text-border"}
          fill={i <= rating ? "#f4a935" : "none"}
          aria-hidden="true"
        />
      ))}
    </span>
  );
}

/**
 * Selector interactivo de 1..5 estrellas para calificar. Rellena al pasar el ratón (previsualización)
 * y al hacer clic fija el valor. Accesible como radiogroup.
 */
export function SelectorEstrellas({
  valor,
  onCambio,
}: {
  valor: number;
  onCambio: (n: number) => void;
}) {
  const [hover, setHover] = useState(0);
  const activo = hover || valor;

  return (
    <div className="flex items-center gap-1.5" role="radiogroup" aria-label="Calificación">
      {[1, 2, 3, 4, 5].map((i) => (
        <button
          key={i}
          type="button"
          role="radio"
          aria-checked={valor === i}
          aria-label={`${i} ${i === 1 ? "estrella" : "estrellas"}`}
          onMouseEnter={() => setHover(i)}
          onMouseLeave={() => setHover(0)}
          onClick={() => onCambio(i)}
          className="rounded-full p-1 transition-transform hover:scale-110 focus-visible:shadow-focus"
        >
          <Star
            size={32}
            strokeWidth={1.75}
            className={i <= activo ? "text-[#f4a935]" : "text-border"}
            fill={i <= activo ? "#f4a935" : "none"}
            aria-hidden="true"
          />
        </button>
      ))}
    </div>
  );
}
