/**
 * Etiquetas de interfaz en español de Colombia. Estructura lista para i18n: el diccionario está
 * indexado por locale y hoy solo trae "es-CO"; añadir otro idioma es agregar una clave, no tocar
 * componentes. Los nombres de idiomas y objetivos NO viven aquí: los envía el catálogo del backend
 * (nameEs), única fuente de verdad. Aquí solo lo que el catálogo no cubre, como los niveles.
 */

import type { GoalResponse } from "./api/types";

/** Niveles del MCER agrupados; el backend los guarda como código, la UI los muestra en español. */
export const NIVEL_LABEL: Record<string, string> = {
  BEGINNER: "Principiante",
  INTERMEDIATE: "Intermedio",
  ADVANCED: "Avanzado",
};

/** El orden canónico de los niveles, para pintarlos siempre de menor a mayor. */
export const NIVELES: readonly string[] = ["BEGINNER", "INTERMEDIATE", "ADVANCED"];

export function etiquetaNivel(code: string): string {
  return NIVEL_LABEL[code] ?? code;
}

/** Resuelve el nombre de un objetivo desde el catálogo; si no está, devuelve el código como respaldo. */
export function etiquetaObjetivo(code: string, goals?: GoalResponse[] | null): string {
  return goals?.find((goal) => goal.code === code)?.nameEs ?? code;
}

/**
 * Diccionario de cadenas de interfaz. Un objeto por locale; el resto de la app lee de `t`, que
 * apunta al locale activo. Cuando exista un segundo idioma, se elige el locale y `t` cambia solo.
 */
const es_CO = {
  filtros: {
    titulo: "Filtros",
    idioma: "Idioma",
    objetivos: "¿Qué quieres lograr?",
    nivel: "Tu nivel",
    precio: "Precio por hora",
    precioMin: "Mínimo",
    precioMax: "Máximo",
    nativo: "Solo profesores nativos",
    certificado: "Solo profesores certificados",
    orden: "Ordenar por",
    limpiar: "Limpiar filtros",
    aplicar: "Ver resultados",
  },
  orden: {
    RELEVANCE: "Relevancia",
    PRICE_ASC: "Menor precio",
    PRICE_DESC: "Mayor precio",
  },
} as const;

export const DICCIONARIO = { "es-CO": es_CO } as const;

export type Locale = keyof typeof DICCIONARIO;

/** Locale activo. Único punto a cambiar el día que haya más de un idioma. */
export const LOCALE_ACTUAL: Locale = "es-CO";

/** Atajo al diccionario del locale activo. */
export const t = DICCIONARIO[LOCALE_ACTUAL];
