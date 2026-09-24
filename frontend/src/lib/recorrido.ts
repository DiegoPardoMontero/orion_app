import { useSyncExternalStore } from "react";

/**
 * El recorrido guiado (handoff `design_handoff_orion_bienvenida_recorrido`, §7). Los textos son los
 * finales del diseño, palabra por palabra.
 *
 * <p>Cada paso <strong>lleva a su pantalla</strong> (`ruta`) y ahí ilumina lo que explica (Pardo,
 * 24/09/2026: «que te muevas entre pantallas, no solamente los iconos»). Las anclas van en orden
 * de preferencia: primero el elemento de la página —«Unirse a la clase», los filtros, los
 * horarios—, y si no existe —el primer día no hay clases que unirse—, el ítem de la navegación, que
 * existe siempre. Sin ninguna a la vista, la tarjeta sale centrada.
 */

export type Guia = "rigel" | "meissa";
export type TourId = "TOUR_PROFESSOR" | "TOUR_STUDENT";

export type PasoDelRecorrido = {
  titulo: string;
  texto: string;
  guia: Guia;
  /** A dónde lleva el paso. `null` = se queda donde está. `"profesor"` = el perfil del primer profe. */
  ruta: string | "profesor" | null;
  /** Valores de `data-tour`, del más específico al más general. */
  anclas: string[];
};

export type Recorrido = {
  id: TourId;
  inicio: { titulo: string; texto: string; pose: "profe" | "saludo" };
  pasos: PasoDelRecorrido[];
  cierre: {
    titulo: (nombre: string) => string;
    texto: string;
    principal: { etiqueta: string; href: string };
    secundaria: { etiqueta: string; href: string };
  };
};

export const RECORRIDO_PROFESOR: Recorrido = {
  id: "TOUR_PROFESSOR",
  inicio: {
    titulo: "Te muestro Orión en 8 pasos",
    texto: "Es rápido. Puedes salirte cuando quieras y volver a verlo desde Ayuda.",
    pose: "profe",
  },
  pasos: [
    {
      titulo: "Tu agenda",
      texto: "Aquí ves tus clases, día por día. La más próxima siempre queda arriba.",
      guia: "rigel",
      ruta: "/mis-clases",
      anclas: ["nav:/mis-clases"],
    },
    {
      titulo: "Tus horarios",
      texto: "Marca las franjas en que das clase cada semana. Si un día no puedes, agrégalo como excepción.",
      guia: "rigel",
      // Desde el 24/09 los horarios viven dentro del perfil.
      ruta: "/perfil?seccion=horarios",
      anclas: ["horarios-semana", "nav:/perfil"],
    },
    {
      titulo: "Tu perfil público",
      texto: "Es lo que ven los estudiantes antes de reservar: tu tarifa, tu bio y tu enlace propio para compartir.",
      guia: "rigel",
      ruta: "/perfil",
      anclas: ["nav:/perfil"],
    },
    {
      titulo: "Entra a tu clase",
      texto: "Cuando se acerque la hora, entras desde aquí. La clase pasa dentro de Orión, sin instalar nada.",
      guia: "rigel",
      ruta: "/mis-clases",
      anclas: ["unirse", "nav:/mis-clases"],
    },
    {
      titulo: "Al terminar, el acta",
      texto: "Marca si tu estudiante asistió y cuéntanos en un minuto qué vieron. Orión lo ordena y tú lo revisas.",
      guia: "rigel",
      ruta: "/mis-clases",
      anclas: ["actas", "nav:/mis-clases"],
    },
    {
      titulo: "La práctica de tu estudiante",
      texto: "De tu acta salen ejercicios cortos. Aquí ves cuánto practicó y en qué le costó.",
      guia: "meissa",
      ruta: "/mis-clases?scope=past",
      anclas: ["clase-pasada", "nav:/mis-clases"],
    },
    {
      titulo: "Mensajes",
      texto: "Habla con tus estudiantes antes y después de cada clase.",
      guia: "rigel",
      ruta: "/mensajes",
      anclas: ["nav:/mensajes"],
    },
    {
      titulo: "Tus ganancias y tu desempeño",
      texto: "Mira lo que has ganado por clase y cómo van tus clases con cada estudiante.",
      guia: "rigel",
      ruta: "/ganancias",
      anclas: ["nav:/ganancias", "nav:/desempeno"],
    },
  ],
  cierre: {
    titulo: (nombre) => `¡Listo, ${nombre}! Ya conoces Orión.`,
    texto:
      "Revisa que tu disponibilidad esté al día: así te encuentran los estudiantes. Si algo se te olvida, el recorrido está en Ayuda.",
    principal: { etiqueta: "Revisar mi disponibilidad", href: "/perfil?seccion=horarios" },
    secundaria: { etiqueta: "Ir a mi agenda", href: "/mis-clases" },
  },
};

export const RECORRIDO_ESTUDIANTE: Recorrido = {
  id: "TOUR_STUDENT",
  inicio: {
    titulo: "Te muestro Orión en 6 pasos",
    texto: "Es rápido. Puedes salirte cuando quieras y volver a verlo desde Ayuda.",
    pose: "saludo",
  },
  pasos: [
    {
      titulo: "Encuentra tu profe",
      texto: "Filtra por horario, precio y lo que quieres practicar.",
      guia: "rigel",
      ruta: "/profesores",
      anclas: ["filtros", "nav:/profesores"],
    },
    {
      titulo: "Reserva un cupo",
      texto: "En el perfil ves sus horarios libres. Eliges uno y pagas solo esa clase.",
      guia: "rigel",
      ruta: "profesor",
      anclas: ["horarios", "nav:/profesores"],
    },
    {
      titulo: "Entra a tu clase",
      texto: "A la hora de la clase, entras desde aquí. Todo pasa dentro de Orión.",
      guia: "rigel",
      ruta: "/mis-clases",
      anclas: ["unirse", "nav:/mis-clases"],
    },
    {
      titulo: "Tu resumen y tu práctica",
      texto: "Después de cada clase te llega lo que vieron y unos ejercicios cortos. Yo te acompaño.",
      guia: "meissa",
      ruta: "/mis-clases",
      anclas: ["actas", "nav:/mis-clases"],
    },
    {
      titulo: "Mi cielo",
      texto: "Cada vez que practicas, se enciende una estrella. Aquí ves tu racha y tus logros.",
      guia: "rigel",
      ruta: "/cuenta?seccion=cielo",
      anclas: ["mi-cielo", "nav:/cuenta"],
    },
    {
      titulo: "Mensajes",
      texto: "Escríbele a tu profe cuando quieras.",
      guia: "rigel",
      ruta: "/mensajes",
      anclas: ["nav:/mensajes"],
    },
  ],
  cierre: {
    titulo: () => "¡Listo! Ya conoces Orión.",
    texto: "Ahora sí, a buscar tu profe. Si algo se te olvida, el recorrido está en Ayuda.",
    principal: { etiqueta: "Buscar profe", href: "/profesores" },
    secundaria: { etiqueta: "Ir al inicio", href: "/mis-clases" },
  },
};

export function recorridoDe(id: TourId): Recorrido {
  return id === "TOUR_PROFESSOR" ? RECORRIDO_PROFESOR : RECORRIDO_ESTUDIANTE;
}

/* ------------------------------------------------------------ dónde va la tarjeta */

export type Caja = { top: number; left: number; width: number; height: number };
export type Lugar = "abajo" | "arriba" | "derecha" | "izquierda" | "centro";
export type Ubicacion = {
  top: number;
  left: number;
  width: number;
  lugar: Lugar;
  /** Dónde asoma la flecha: `x` en arriba/abajo, `y` en los lados. */
  flecha: number | null;
};

const MARGEN = 16;
/** 16 px libres entre el anillo del foco y la tarjeta; la flecha asoma 7 en ese hueco. */
const SEPARACION = 16;
const ANCHO_DESKTOP = 360;
const LADO_MOVIL = 20;
const FLECHA = 16;
const FLECHA_BORDE = 22;

/**
 * Dónde poner la tarjeta junto al foco (§7, `<TourSpotlight>`): abajo → arriba → derecha →
 * izquierda, la primera con 16 px libres del borde. En móvil, solo arriba o abajo y a lo ancho
 * (20 px por lado). Un ítem de la barra lateral lleva la tarjeta al lado, como en la captura 11:
 * debajo taparía justo a los ítems que vienen después.
 *
 * <p>`foco` es la caja del anillo por fuera (el elemento + 6 de margen + 3 de anillo). Pura, para
 * probarla sin navegador.
 */
export function ubicarTarjeta(
  foco: Caja | null,
  altoTarjeta: number,
  pantalla: { width: number; height: number },
  movil: boolean,
): Ubicacion {
  const width = movil ? pantalla.width - LADO_MOVIL * 2 : Math.min(ANCHO_DESKTOP, pantalla.width - MARGEN * 2);
  const centrada: Ubicacion = {
    top: Math.max(MARGEN, (pantalla.height - altoTarjeta) / 2),
    left: movil ? LADO_MOVIL : Math.max(MARGEN, (pantalla.width - width) / 2),
    width,
    lugar: "centro",
    flecha: null,
  };
  if (!foco) return centrada;

  const centroX = foco.left + foco.width / 2;
  const centroY = foco.top + foco.height / 2;
  const leftAlineada = movil
    ? LADO_MOVIL
    : Math.min(Math.max(MARGEN, centroX - width / 2), pantalla.width - width - MARGEN);
  const flechaX = (left: number) =>
    Math.min(Math.max(FLECHA_BORDE, centroX - left - FLECHA / 2), width - FLECHA_BORDE - FLECHA);
  const flechaY = (top: number) =>
    Math.min(Math.max(FLECHA_BORDE, centroY - top - FLECHA / 2), altoTarjeta - FLECHA_BORDE - FLECHA);
  const dentroY = (top: number) => Math.min(Math.max(MARGEN, top), pantalla.height - altoTarjeta - MARGEN);

  const abajo = foco.top + foco.height + SEPARACION;
  const cabeAbajo = abajo + altoTarjeta + MARGEN <= pantalla.height;
  const arriba = foco.top - SEPARACION - altoTarjeta;
  const cabeArriba = arriba >= MARGEN;
  const derecha = foco.left + foco.width + SEPARACION;
  const cabeDerecha = derecha + width + MARGEN <= pantalla.width;
  const izquierda = foco.left - SEPARACION - width;
  const cabeIzquierda = izquierda >= MARGEN;

  if (!movil && foco.left + foco.width <= pantalla.width * 0.3 && cabeDerecha) {
    const top = dentroY(foco.top - 12);
    return { top, left: derecha, width, lugar: "derecha", flecha: flechaY(top) };
  }
  if (cabeAbajo) return { top: abajo, left: leftAlineada, width, lugar: "abajo", flecha: flechaX(leftAlineada) };
  if (cabeArriba) return { top: arriba, left: leftAlineada, width, lugar: "arriba", flecha: flechaX(leftAlineada) };
  if (!movil && cabeDerecha) {
    const top = dentroY(foco.top);
    return { top, left: derecha, width, lugar: "derecha", flecha: flechaY(top) };
  }
  if (!movil && cabeIzquierda) {
    const top = dentroY(foco.top);
    return { top, left: izquierda, width, lugar: "izquierda", flecha: flechaY(top) };
  }
  return centrada;
}

/* ------------------------------------------------ el paso guardado y el aplazado */

const CLAVE_PASO = (id: TourId) => `orion.recorrido.${id}.paso`;
/** «Lo veo después» en la bienvenida: el recorrido espera al aviso de Rigel en la agenda. */
export const CLAVE_APLAZADO = "orion.recorrido.aplazado";
/** Ese aviso se ofrece una sola vez. */
export const CLAVE_AVISO_MOSTRADO = "orion.recorrido.aviso-mostrado";

function leer(clave: string): string | null {
  try {
    return window.localStorage.getItem(clave);
  } catch {
    return null;
  }
}

function escribir(clave: string, valor: string | null) {
  try {
    if (valor === null) window.localStorage.removeItem(clave);
    else window.localStorage.setItem(clave, valor);
  } catch {
    // Sin almacenamiento, el recorrido funciona igual; solo no se puede retomar.
  }
}

/** El paso en que se quedó, si cerró la pestaña a mitad del recorrido. */
export function pasoGuardado(id: TourId): number | null {
  const v = Number(leer(CLAVE_PASO(id)));
  return Number.isInteger(v) && v >= 1 ? v : null;
}

export function guardarPaso(id: TourId, paso: number | null) {
  escribir(CLAVE_PASO(id), paso === null ? null : String(paso));
}

export function marca(clave: string): boolean {
  return leer(clave) === "1";
}

export function poner(clave: string, si: boolean) {
  escribir(clave, si ? "1" : null);
}

/* ------------------------------------------------- abrirlo a pedido (desde Ayuda) */

export type Pedido = { id: TourId; desde: "inicio" | "paso1" };

let pedido: Pedido | null = null;
const oyentes = new Set<() => void>();

/** Abre un recorrido ahora, aunque ya se haya visto. Desde Ayuda empieza en el paso 1 (§7). */
export function abrirRecorrido(id: TourId, desde: Pedido["desde"] = "paso1") {
  pedido = { id, desde };
  oyentes.forEach((o) => o());
}

export function cerrarRecorridoPedido() {
  pedido = null;
  oyentes.forEach((o) => o());
}

export function useRecorridoPedido(): Pedido | null {
  return useSyncExternalStore(
    (o) => {
      oyentes.add(o);
      return () => oyentes.delete(o);
    },
    () => pedido,
    () => null,
  );
}
