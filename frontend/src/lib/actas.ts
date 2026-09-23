/**
 * El acta de clase (Bloque 10, Parte A). Los tipos van a mano porque las rutas son nuevas y el
 * esquema generado todavía no las trae; el servidor es quien decide qué vista recibe cada quien.
 */

export type Palabra = { term: string; meaning?: string | null };

/** Lo que ve el profesor autor: todo lo suyo, incluidas sus notas en crudo. */
export type ActaDelProfesor = {
  id: string;
  bookingId: string;
  status: "DRAFT" | "PUBLISHED";
  rawInput: string;
  workedOn: string | null;
  recurringIssues: string | null;
  nextSteps: string | null;
  vocabulary: Palabra[];
  origin: "AI_DRAFT" | "MANUAL";
  draftedByAi: boolean;
  editable: boolean;
  publishedAt: string | null;
  lastEditedAt: string | null;
};

/** Lo que ve el estudiante: sin el crudo, sin el origen, sin cuánto se corrigió. No es que no se
 *  pinten: el servidor no los manda. */
export type ActaDelEstudiante = {
  id: string;
  bookingId: string;
  professorName: string | null;
  workedOn: string | null;
  recurringIssues: string | null;
  nextSteps: string | null;
  vocabulary: Palabra[];
  publishedAt: string | null;
  lastEditedAt: string | null;
};

/**
 * Para la lista de clases: el estado del acta de cada clase. Al profesor también le llegan como
 * `PENDING` las clases cerradas que admiten acta y aún no la tienen — el servidor decide cuáles.
 */
export type ResumenDeActas = {
  since: string;
  byBooking: Record<string, "PENDING" | "DRAFT" | "PUBLISHED">;
};

export const MIN_NOTAS = 20;
export const MAX_NOTAS = 2000;
export const MAX_PALABRAS = 12;
