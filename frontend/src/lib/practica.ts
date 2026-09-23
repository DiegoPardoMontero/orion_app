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

export function leerPayload<T>(ejercicio: Ejercicio): T {
  try {
    return JSON.parse(ejercicio.payload) as T;
  } catch {
    return {} as T;
  }
}

export const PUNTOS_POR_PRACTICA = 15;
