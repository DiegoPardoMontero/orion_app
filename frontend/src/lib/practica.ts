/**
 * La práctica entre clases (Bloque 10, Parte B). La respuesta esperada solo llega cuando el
 * ejercicio ya está cerrado —acertado o sin intentos—, para mostrarla con su explicación.
 */

export type TipoDeEjercicio =
  | "FILL_BLANK"
  | "FIX_SENTENCE"
  | "MATCH_MEANING"
  | "ORDER_DIALOGUE"
  | "WRITE_SENTENCE"
  | "SPOT_ERROR"
  | "BUILD_SENTENCE"
  | "CHOOSE_REPLY"
  | "LISTEN_CHOOSE"
  | "DICTATION";

/** Las cinco categorías, en el orden en que se recorren: de las palabras a la frase propia. */
export type CategoriaDeEjercicio = "PALABRAS" | "FRASES" | "CONVERSACION" | "ESCUCHA" | "TU_TURNO";

export const NOMBRE_DE_CATEGORIA: Record<CategoriaDeEjercicio, string> = {
  PALABRAS: "Palabras",
  FRASES: "Frases",
  CONVERSACION: "Conversación",
  ESCUCHA: "Escucha",
  TU_TURNO: "Tu turno",
};

/** Los que suenan con la voz del dispositivo: sin voz en inglés, se pueden saltar. */
export const SE_OYEN: TipoDeEjercicio[] = ["LISTEN_CHOOSE", "DICTATION"];

export type Ejercicio = {
  id: string;
  index: number;
  type: TipoDeEjercicio;
  category: CategoriaDeEjercicio;
  prompt: string;
  /** JSON en texto: las opciones, las piezas o los pares, según el tipo. */
  payload: string;
  attempts: number;
  correct: boolean | null;
  closed: boolean;
  /** Saltado porque el dispositivo no tenía voz en inglés: cerrado, pero ni acierto ni fallo. */
  skipped: boolean;
  /** La pista del «Casi…»: llega tras un fallo, con el ejercicio abierto. */
  hint: string | null;
  /** Llega con el ejercicio cerrado: acertado, mostrado o saltado. */
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
  /** Constelación perfecta: todo lo respondido, al primer intento. Da su bono al terminar. */
  perfect: boolean;
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

/**
 * Un ejercicio tal como lo ve el profesor que escribió el acta, con lo que hizo su estudiante
 * (decisión de Pardo, 24/09/2026; el estudiante lo sabe desde la portada de su práctica).
 */
export type EjercicioDelActa = {
  index: number;
  type: TipoDeEjercicio;
  category: CategoriaDeEjercicio;
  prompt: string;
  payload: string;
  expected: string | null;
  explanation: string | null;
  sourceTerm: string | null;
  firstAnswer: string | null;
  secondAnswer: string | null;
  attempts: number;
  correct: boolean | null;
  skipped: boolean;
};

export type PracticaDelActa = {
  id: string;
  status: "PENDING" | "READY" | "IN_PROGRESS" | "COMPLETED" | "EXPIRED" | "FAILED";
  itemCount: number;
  expiresAt: string;
  completedAt: string | null;
  studentName: string | null;
  firstTry: number;
  secondTry: number;
  shown: number;
  skipped: number;
  items: EjercicioDelActa[];
};

/** Una práctica en la ficha del estudiante, para el profesor. */
export type PracticaEnLaFicha = {
  id: string;
  lessonNoteId: string;
  bookingId: string | null;
  classStartsAt: string | null;
  status: "READY" | "IN_PROGRESS" | "COMPLETED" | "EXPIRED";
  itemCount: number;
  correctCount: number;
  completedAt: string | null;
};

/** Cómo le fue en un ejercicio, en palabras para el profesor. */
export type ResultadoDelEjercicio = "primero" | "segundo" | "mostrada" | "saltado" | "sinHacer";

export function resultadoDe(e: EjercicioDelActa): ResultadoDelEjercicio {
  if (e.skipped) return "saltado";
  if (e.correct === true) return e.attempts <= 1 ? "primero" : "segundo";
  if (e.attempts === 0) return "sinHacer";
  return "mostrada";
}

/** Lo que respondió el estudiante, legible: el índice de «caza el error» es una palabra. */
export function mostrarRespuesta(tipo: TipoDeEjercicio, payload: string, respuesta: string): string {
  try {
    if (tipo === "SPOT_ERROR") {
      const fichas = (JSON.parse(payload) as { tokens?: string[] }).tokens ?? [];
      const i = Number.parseInt(respuesta, 10);
      return Number.isInteger(i) && fichas[i] !== undefined ? `tocó «${fichas[i]}»` : respuesta;
    }
    if (tipo === "BUILD_SENTENCE") return unirFichas(JSON.parse(respuesta) as string[]);
  } catch {
    // Si no se puede leer, se muestra tal cual.
  }
  return mostrarEsperada(tipo, respuesta);
}

export const NOMBRE_DEL_TIPO: Record<TipoDeEjercicio, string> = {
  FILL_BLANK: "Completa",
  FIX_SENTENCE: "Corrige",
  MATCH_MEANING: "Parejas",
  ORDER_DIALOGUE: "Ordena la conversación",
  WRITE_SENTENCE: "Tu frase",
  SPOT_ERROR: "Caza el error",
  BUILD_SENTENCE: "Arma la frase",
  CHOOSE_REPLY: "Responde en el chat",
  LISTEN_CHOOSE: "Escucha y elige",
  DICTATION: "Escucha y escribe",
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
    if (tipo === "BUILD_SENTENCE") {
      return unirFichas(JSON.parse(esperada) as string[]);
    }
    if (tipo === "SPOT_ERROR") {
      return (JSON.parse(esperada) as { correction?: string }).correction ?? esperada;
    }
  } catch {
    // Si no se puede leer, se muestra tal cual.
  }
  return esperada;
}

/** Las fichas de una frase, unidas como se escribe: sin espacio antes de la puntuación. */
export function unirFichas(fichas: string[]): string {
  return fichas.join(" ").replace(/\s+([?.!,;:])/g, "$1");
}

export function leerPayload<T>(ejercicio: { payload: string }): T {
  try {
    return JSON.parse(ejercicio.payload) as T;
  } catch {
    return {} as T;
  }
}

export const PUNTOS_POR_PRACTICA = 15;
export const PUNTOS_CONSTELACION_PERFECTA = 5;

/**
 * Cuántos seguidos lleva al primer intento, contando hacia atrás desde el último cerrado. Lo saltado
 * no suma ni corta: no fue un intento.
 */
export function rachaAlPrimerIntento(items: Ejercicio[]): number {
  let racha = 0;
  const cerrados = [...items].sort((a, b) => a.index - b.index).filter((i) => i.closed && !i.skipped);
  for (let k = cerrados.length - 1; k >= 0; k--) {
    const i = cerrados[k];
    if (i.correct === true && i.attempts === 1) racha++;
    else break;
  }
  return racha;
}

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
