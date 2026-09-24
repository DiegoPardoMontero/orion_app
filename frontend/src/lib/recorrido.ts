import { useSyncExternalStore } from "react";

/**
 * El recorrido guiado de la plataforma: los pasos de cada rol y dónde va la tarjeta.
 *
 * <p>Los pasos se anclan a la navegación (`data-tour="nav:/ruta"`), que existe en todas las
 * pantallas: un recorrido que dependiera de que el profesor ya tenga una clase en la agenda no
 * se podría mostrar el primer día, que es justo cuando hace falta. Si el ancla no está a la vista
 * —en móvil la barra inferior no lleva todas las secciones—, la tarjeta sale centrada y sin foco.
 */

export type Guia = "rigel" | "meissa";

export type PasoDelRecorrido = {
  /** Valor de `data-tour` del elemento a iluminar; sin ancla, la tarjeta va centrada. */
  ancla?: string;
  titulo: string;
  texto: string;
  guia: Guia;
};

export type Recorrido = {
  paso: "TOUR_PROFESSOR" | "TOUR_STUDENT";
  inicio: { titulo: (nombre: string) => string; texto: string };
  pasos: PasoDelRecorrido[];
  cierre: { titulo: string; texto: string; accion: { etiqueta: string; href: string } };
};

export const RECORRIDO_PROFESOR: Recorrido = {
  paso: "TOUR_PROFESSOR",
  inicio: {
    titulo: (nombre) => `¡Hola, ${nombre}! Te muestro Orión en un minuto`,
    texto: "Son unas pocas paradas. Puedes saltarlo cuando quieras y volver a verlo desde Ayuda.",
  },
  pasos: [
    {
      ancla: "nav:/mis-clases",
      titulo: "Tu agenda",
      texto: "Aquí ves tus clases día por día. A la hora de la clase entras desde aquí: el aula está dentro de Orión.",
      guia: "rigel",
    },
    {
      ancla: "nav:/disponibilidad",
      titulo: "Tu disponibilidad",
      texto: "Marca tus franjas de cada semana y las excepciones. Los estudiantes solo reservan dentro de ellas.",
      guia: "rigel",
    },
    {
      ancla: "nav:/perfil",
      titulo: "Tu perfil público",
      texto: "Tu tarifa, tu presentación y tu enlace propio para compartir. Es lo que ve un estudiante antes de reservar.",
      guia: "rigel",
    },
    {
      ancla: "nav:/mis-clases",
      titulo: "Después de cada clase",
      texto:
        "Marca la asistencia y cuéntanos cómo estuvo: escríbelo o díctalo en un minuto. Orión lo ordena en un acta que tú revisas y publicas.",
      guia: "rigel",
    },
    {
      titulo: "La práctica de tu estudiante",
      texto:
        "De cada acta salen ejercicios cortos para tu estudiante. Los ves debajo del acta, y en su ficha, cuánto practicó y dónde le costó.",
      guia: "meissa",
    },
    {
      ancla: "nav:/mensajes",
      titulo: "Mensajes",
      texto: "Habla con tus estudiantes antes y después de clase, sin salir de Orión.",
      guia: "rigel",
    },
    {
      ancla: "nav:/ganancias",
      titulo: "Tus ganancias",
      texto: "Lo que llevas ganado y lo que está por pagarse. Orión te liquida cuando la clase ya se dictó.",
      guia: "rigel",
    },
    {
      ancla: "nav:/desempeno",
      titulo: "Tu desempeño",
      texto: "Tu calificación, tu cumplimiento y cuántas de tus clases tienen acta.",
      guia: "rigel",
    },
  ],
  cierre: {
    titulo: "¡Listo! Ya conoces Orión",
    texto: "Empieza por tu disponibilidad: sin franjas abiertas, nadie puede reservar contigo.",
    accion: { etiqueta: "Abrir mi disponibilidad", href: "/disponibilidad" },
  },
};

export const RECORRIDO_ESTUDIANTE: Recorrido = {
  paso: "TOUR_STUDENT",
  inicio: {
    titulo: (nombre) => `¡Hola, ${nombre}! Te muestro cómo funciona Orión`,
    texto: "Son unas pocas paradas. Puedes saltarlo cuando quieras y volver a verlo desde Ayuda.",
  },
  pasos: [
    {
      ancla: "nav:/profesores",
      titulo: "Busca tu profesor",
      texto: "Filtra por lo que necesitas y abre su perfil: ahí ves su presentación y sus horarios libres.",
      guia: "rigel",
    },
    {
      ancla: "nav:/profesores",
      titulo: "Reserva y paga",
      texto: "Eliges una hora libre y pagas la clase dentro de Orión. Si después no puedes ir, la cancelas desde tu agenda.",
      guia: "rigel",
    },
    {
      ancla: "nav:/mis-clases",
      titulo: "Tu agenda",
      texto: "Tus clases reservadas. A la hora de la clase entras desde aquí: la videollamada es dentro de Orión.",
      guia: "rigel",
    },
    {
      titulo: "Después de la clase",
      texto: "Tu profesor te deja un resumen de lo que vieron, y de ahí salen unos ejercicios cortos para practicar antes de la siguiente.",
      guia: "meissa",
    },
    {
      ancla: "nav:/cuenta",
      titulo: "Tu perfil y tu cielo",
      texto: "Tu progreso, tu racha y tus logros: cada clase y cada práctica encienden una estrella.",
      guia: "rigel",
    },
    {
      ancla: "nav:/mensajes",
      titulo: "Mensajes",
      texto: "Escríbele a tu profesor antes o después de la clase.",
      guia: "rigel",
    },
  ],
  cierre: {
    titulo: "¡Listo! Ya sabes moverte por Orión",
    texto: "Lo siguiente es encontrar a tu profesor.",
    accion: { etiqueta: "Buscar profesor", href: "/profesores" },
  },
};

/* ------------------------------------------------------------ dónde va la tarjeta */

export type Caja = { top: number; left: number; width: number; height: number };
export type Lugar = "abajo" | "arriba" | "derecha" | "izquierda" | "centro";

const MARGEN = 16;
const SEPARACION = 14;

/**
 * Dónde poner la tarjeta junto al foco: al lado si es un ítem de la barra lateral; si no, abajo si
 * cabe, si no arriba, si no al lado; y dentro de la pantalla siempre. Pura, para poder probarla sin navegador.
 */
export function ubicarTarjeta(
  foco: Caja | null,
  tarjeta: { width: number; height: number },
  pantalla: { width: number; height: number },
): { top: number; left: number; lugar: Lugar } {
  const centrada = {
    top: Math.max(MARGEN, (pantalla.height - tarjeta.height) / 2),
    left: Math.max(MARGEN, (pantalla.width - tarjeta.width) / 2),
    lugar: "centro" as const,
  };
  if (!foco) return centrada;

  const dentroX = (left: number) => Math.min(Math.max(MARGEN, left), pantalla.width - tarjeta.width - MARGEN);
  const dentroY = (top: number) => Math.min(Math.max(MARGEN, top), pantalla.height - tarjeta.height - MARGEN);
  const alineadaX = dentroX(foco.left + foco.width / 2 - tarjeta.width / 2);
  const cabeALaDerecha = foco.left + foco.width + SEPARACION + tarjeta.width + MARGEN <= pantalla.width;

  // Un foco pegado al borde izquierdo es un ítem de la barra lateral: debajo, la tarjeta taparía
  // justo a sus vecinos, que son los siguientes pasos. Al lado no tapa nada.
  if (foco.left + foco.width <= pantalla.width * 0.3 && cabeALaDerecha) {
    return { top: dentroY(foco.top - 12), left: foco.left + foco.width + SEPARACION, lugar: "derecha" };
  }
  if (foco.top + foco.height + SEPARACION + tarjeta.height + MARGEN <= pantalla.height) {
    return { top: foco.top + foco.height + SEPARACION, left: alineadaX, lugar: "abajo" };
  }
  if (foco.top - SEPARACION - tarjeta.height >= MARGEN) {
    return { top: foco.top - SEPARACION - tarjeta.height, left: alineadaX, lugar: "arriba" };
  }
  if (cabeALaDerecha) {
    return { top: dentroY(foco.top), left: foco.left + foco.width + SEPARACION, lugar: "derecha" };
  }
  if (foco.left - SEPARACION - tarjeta.width >= MARGEN) {
    return { top: dentroY(foco.top), left: foco.left - SEPARACION - tarjeta.width, lugar: "izquierda" };
  }
  return centrada;
}

/* ------------------------------------------------- abrirlo a pedido (desde Ayuda) */

let pedido: Recorrido["paso"] | null = null;
const oyentes = new Set<() => void>();

/** Pide abrir un recorrido ahora, aunque ya se haya visto. */
export function abrirRecorrido(paso: Recorrido["paso"]) {
  pedido = paso;
  oyentes.forEach((o) => o());
}

export function cerrarRecorridoPedido() {
  pedido = null;
  oyentes.forEach((o) => o());
}

export function useRecorridoPedido(): Recorrido["paso"] | null {
  return useSyncExternalStore(
    (o) => {
      oyentes.add(o);
      return () => oyentes.delete(o);
    },
    () => pedido,
    () => null,
  );
}
