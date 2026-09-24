/**
 * La práctica entre clases (Bloque 10, Parte B). La respuesta esperada solo llega cuando el
 * ejercicio ya está cerrado —acertado o sin intentos—, para mostrarla con su explicación.
 */

export type TipoDeEjercicio =
  | "FILL_BLANK"
  | "FIX_SENTENCE"
  | "MATCH_MEANING"
  | "ORDER_DIALOGUE"
  | "WRITE_SENTENCE";

export type Ejercicio = {
  id: string;
  index: number;
  type: TipoDeEjercicio;
  prompt: string;
  /** JSON en texto: las opciones, las piezas o los pares, según el tipo. */
  payload: string;
  attempts: number;
  correct: boolean | null;
  closed: boolean;
  explanation: string | null;
  expected: string | null;
  answer: string | null;
};

export type SetDePractica = {
  id: string;
  status: "READY" | "IN_PROGRESS" | "COMPLETED" | "EXPIRED";
  lessonNoteId: string;
  bookingId: string | null;
  classStartsAt: string | null;
  professorName: string | null;
  workedOn: string | null;
  vocabularyCount: number;
  estimatedMinutes: number | null;
  itemCount: number;
  correctCount: number;
  expiresAt: string;
  completedAt: string | null;
  items: Ejercicio[];
};

export type Resultado = {
  correct: boolean;
  closed: boolean;
  attemptsLeft: number;
  item: Ejercicio;
};

/** Lo que el profesor ve: agregado, nunca las respuestas. */
export type ResumenDePractica = {
  ofrecidasEstaSemana: number;
  completadasEstaSemana: number;
  leCosto: string[];
};

/** Un ejercicio tal como lo ve el profesor que escribió el acta: sin nada de lo que hizo el estudiante. */
export type EjercicioDelActa = {
  index: number;
  type: TipoDeEjercicio;
  prompt: string;
  payload: string;
  expected: string | null;
  explanation: string | null;
  sourceTerm: string | null;
};

export type PracticaDelActa = {
  id: string;
  status: "PENDING" | "READY" | "IN_PROGRESS" | "COMPLETED" | "EXPIRED" | "FAILED";
  itemCount: number;
  expiresAt: string;
  items: EjercicioDelActa[];
};

export const NOMBRE_DEL_TIPO: Record<TipoDeEjercicio, string> = {
  FILL_BLANK: "Completar la frase",
  FIX_SENTENCE: "Corregir la frase",
  MATCH_MEANING: "Unir con su significado",
  ORDER_DIALOGUE: "Ordenar el diálogo",
  WRITE_SENTENCE: "Escribir una frase propia",
};

/** La respuesta esperada, legible: los pares y el diálogo llegan como JSON. */
export function mostrarEsperada(tipo: TipoDeEjercicio, esperada: string): string {
  try {
    if (tipo === "MATCH_MEANING") {
      return Object.entries(JSON.parse(esperada) as Record<string, string>).map(([t, m]) => `${t} = ${m}`).join(" · ");
    }
    if (tipo === "ORDER_DIALOGUE") {
      return (JSON.parse(esperada) as string[]).join(" → ");
    }
  } catch {
    // Si no se puede leer, se muestra tal cual.
  }
  return esperada;
}

export function leerPayload<T>(ejercicio: { payload: string }): T {
  try {
    return JSON.parse(ejercicio.payload) as T;
  } catch {
    return {} as T;
  }
}

export const PUNTOS_POR_PRACTICA = 15;

export function primerNombre(nombre: string): string {
  return nombre.trim().split(/\s+/)[0];
}

/** Lo trabajado, en una línea: la primera frase del acta, sin punto final. */
export function resumirLoTrabajado(texto: string | null): string | null {
  if (!texto?.trim()) return null;
  const primera = texto.trim().split(/(?<=[.;!?])\s/)[0].replace(/[.;!?]+$/, "");
  const corta = primera.length > 70 ? `${primera.slice(0, 67).trimEnd()}…` : primera;
  return corta.charAt(0).toLowerCase() + corta.slice(1);
}

export function palabrasNuevas(n: number): string {
  const numeros = ["", "una", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve", "diez", "once", "doce"];
  return n === 1 ? "una palabra nueva" : `${numeros[n] ?? n} palabras nuevas`;
}
