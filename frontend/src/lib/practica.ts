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
  /** PENDING: el acta ya salió y la práctica se está generando (tarda cerca de un minuto). */
  status: "PENDING" | "READY" | "IN_PROGRESS" | "COMPLETED" | "EXPIRED";
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
  ofrecidasEsteMes: number;
  completadasEsteMes: number;
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
  /** Cerrado: acertado, sin intentos o saltado. Abierto con un intento es «va en ese». */
  closed: boolean;
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
  workedOn: string | null;
  /** Cómo quedó cada estrella, en orden. */
  stars: ("primero" | "segundo" | "mostrada" | "saltada" | "off")[];
};

/** Cómo le fue en un ejercicio, en palabras para el profesor. */
export type ResultadoDelEjercicio = "primero" | "segundo" | "mostrada" | "saltado" | "sinHacer";

export function resultadoDe(e: EjercicioDelActa): ResultadoDelEjercicio {
  if (e.skipped) return "saltado";
  if (e.correct === true) return e.attempts <= 1 ? "primero" : "segundo";
  // Abierto con un intento fallado todavía no es «se le mostró»: le queda otro.
  if (e.attempts === 0 || e.closed === false) return "sinHacer";
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

/* ------------------------------------------------------------------------------------------------
 * La pantalla de ejercicio (handoff design_handoff_orion_practica): la constelación, la racha, las
 * categorías y los textos fijos de la interfaz. Lo que cambia de un set a otro —frases, opciones,
 * pistas, explicaciones— sale del acta; esto es solo lo que la interfaz dice siempre igual.
 * ---------------------------------------------------------------------------------------------- */

/** Cómo quedó un ejercicio en la constelación. */
export type EstrellaDelEjercicio = "off" | "actual" | "primero" | "segundo" | "mostrada" | "saltada";

export function estrellaDe(e: Pick<Ejercicio, "closed" | "correct" | "attempts" | "skipped">): EstrellaDelEjercicio {
  if (e.skipped) return "saltada";
  if (e.correct === true && e.closed) return e.attempts <= 1 ? "primero" : "segundo";
  if (e.closed) return "mostrada";
  return "off";
}

/** Las estrellas del set, en orden; la del ejercicio en pantalla va como «actual» si sigue abierto. */
export function estrellasDe(items: Ejercicio[], actualId?: string | null): EstrellaDelEjercicio[] {
  return [...items]
    .sort((a, b) => a.index - b.index)
    .map((i) => {
      const e = estrellaDe(i);
      return e === "off" && i.id === actualId ? "actual" : e;
    });
}

/**
 * La racha dentro del set, leída en la constelación: suma cada estrella al primer intento; una al
 * segundo o una mostrada la cortan; una saltada no suma ni corta (§9.7).
 */
export function rachaDe(estrellas: EstrellaDelEjercicio[]): number {
  let racha = 0;
  for (const e of estrellas) {
    if (e === "primero") racha++;
    else if (e === "segundo" || e === "mostrada") racha = 0;
  }
  return racha;
}

export const TEXTO_RACHA: Record<number, string> = { 3: "¡Tres seguidas!", 4: "¡Cuatro seguidas!", 5: "¡Las cinco seguidas!" };

/** La instrucción de cada tipo, la que dice Rigel (o Meissa) al empezar. */
export function instruccionDe(e: Ejercicio): string {
  switch (e.type) {
    case "MATCH_MEANING":
      return "Une cada palabra con su significado.";
    case "FILL_BLANK":
      return "Elige la palabra que va en el hueco.";
    case "FIX_SENTENCE":
      return "Esta frase salió en tu clase. Escríbela bien.";
    case "SPOT_ERROR":
      return "Toca la palabra que está mal.";
    case "BUILD_SENTENCE":
      return "Arma la frase en inglés tocando las fichas en orden.";
    case "ORDER_DIALOGUE":
      return "Pon la conversación en orden. Arrastra o usa las flechas.";
    case "CHOOSE_REPLY":
      return "Te escribieron. Elige qué responder.";
    case "LISTEN_CHOOSE":
      return "Escúchame y elige qué significa.";
    case "DICTATION":
      return "Escúchame y escribe la frase.";
    case "WRITE_SENTENCE": {
      const termino = leerPayload<{ term?: string }>(e).term;
      return termino ? `Escribe una frase tuya con «${termino}».` : "Escribe una frase tuya en inglés.";
    }
  }
}

/** Lo que va en la pastilla de «Sigue: …» y en la lista del inicio. */
export const NOMBRE_DE_TIPO = NOMBRE_DEL_TIPO;

/** Un número del 1 al 5 en palabras, para los textos que se leen en voz alta. */
const NUMERO = ["cero", "una", "dos", "tres", "cuatro", "cinco", "seis", "siete"];
const ORDINAL = ["", "primera", "segunda", "tercera", "cuarta", "quinta", "sexta", "séptima"];

export function numeroEnPalabras(n: number): string {
  return NUMERO[n] ?? String(n);
}

export function ordinalFemenino(n: number): string {
  return ORDINAL[n] ?? `${n}.ª`;
}

/**
 * El toast del regreso (§10.7): cuántas estrellas ya estaban encendidas. «Primeras» solo si lo son:
 * con una saltada en medio, ya no son las primeras.
 */
export function textoDelRegreso(items: Ejercicio[]): string {
  const cerrados = items.filter((i) => i.closed);
  const encendidas = cerrados.filter((i) => !i.skipped).length;
  if (encendidas === 0) return "Arrancas en este mismo ejercicio.";
  const seguidas = encendidas === cerrados.length;
  if (encendidas === 1) return seguidas ? "Tu primera estrella ya está encendida." : "Ya tienes una estrella encendida.";
  return seguidas
    ? `Tus ${numeroEnPalabras(encendidas)} primeras estrellas ya están encendidas.`
    : `Ya tienes ${numeroEnPalabras(encendidas)} estrellas encendidas.`;
}

const MES_CORTO = ["ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"];

function partesBogota(iso: string, dia: "long" | "short") {
  const partes = new Intl.DateTimeFormat("es-CO", {
    timeZone: "America/Bogota",
    weekday: dia,
    day: "numeric",
    month: "numeric",
  }).formatToParts(new Date(iso));
  const valor = (t: string) => partes.find((p) => p.type === t)?.value ?? "";
  return { semana: valor("weekday").replace(".", ""), dia: valor("day"), mes: MES_CORTO[Number(valor("month")) - 1] ?? "" };
}

/** «miércoles 23 sep»: como el handoff escribe el día de la clase. */
export function diaDeLaClase(iso: string): string {
  const p = partesBogota(iso, "long");
  return `${p.semana} ${p.dia} ${p.mes}`;
}

/** «jue 1 oct»: el vencimiento, en corto. */
export function diaCorto(iso: string): string {
  const p = partesBogota(iso, "short");
  return `${p.semana} ${p.dia} ${p.mes}`;
}

/** «miércoles»: solo el día de la semana, para «Del miércoles con María». */
export function diaDeLaSemana(iso: string): string {
  return partesBogota(iso, "long").semana;
}

/** Lo trabajado como título: «Check-in en el hotel». */
export function tituloDelSet(s: Pick<SetDePractica, "workedOn">): string | null {
  const t = resumirLoTrabajado(s.workedOn);
  return t ? t.charAt(0).toUpperCase() + t.slice(1) : null;
}

/** «María», o «tu profe» cuando no se sabe quién. */
export function nombreDelProfe(s: Pick<SetDePractica, "professorName">, mayuscula = false): string {
  if (s.professorName) return primerNombre(s.professorName);
  return mayuscula ? "Tu profe" : "tu profe";
}

/** Una línea de diálogo «Sam: Hi!» partida en quién habla y qué dice. */
export function partirLinea(linea: string): { quien: string | null; texto: string } {
  const m = /^\s*([^:]{1,24}):\s*(.+)$/.exec(linea);
  return m ? { quien: m[1].trim(), texto: m[2].trim() } : { quien: null, texto: linea };
}

/** Las palabras de la frase bien dicha que no estaban en la original: van resaltadas (§4 del copy). */
export function palabrasNuevasDe(original: string[], corregida: string): Set<number> {
  const limpia = (w: string) => w.toLowerCase().replace(/[^\p{L}\p{N}']/gu, "");
  const antes = new Set(original.map(limpia).filter(Boolean));
  const nuevas = new Set<number>();
  corregida.split(/\s+/).forEach((w, i) => {
    const l = limpia(w);
    if (l && !antes.has(l)) nuevas.add(i);
  });
  return nuevas;
}
