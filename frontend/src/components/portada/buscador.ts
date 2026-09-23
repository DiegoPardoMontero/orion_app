/**
 * Las dos preguntas del buscador de la portada (Sofía, 23/09/2026): para qué y cuándo. El idioma no
 * se pregunta: hoy Orión solo enseña inglés, y un filtro con una sola opción no es un filtro.
 *
 * <p>Cada opción es un filtro real del directorio: «Trabajo» son los objetivos de negocios y de
 * entrevistas, y «Fin de semana» son sábado y domingo en el filtro de días.
 */
export const OBJETIVOS = [
  { id: "trabajo", etiqueta: "Trabajo", goals: ["BUSINESS", "INTERVIEW"] },
  { id: "viaje", etiqueta: "Viaje", goals: ["TRAVEL"] },
  { id: "examen", etiqueta: "Examen certificado", goals: ["EXAMS"] },
  { id: "conversacion", etiqueta: "Conversación", goals: ["CONVERSATION"] },
] as const;

export const HORARIOS = [
  { id: "manana", etiqueta: "Mañana", params: [["schedule", "MORNING"]] },
  { id: "tarde", etiqueta: "Tarde", params: [["schedule", "AFTERNOON"]] },
  { id: "noche", etiqueta: "Noche", params: [["schedule", "EVENING"]] },
  { id: "finde", etiqueta: "Fin de semana", params: [["day", "SATURDAY"], ["day", "SUNDAY"]] },
] as const;

export type ObjetivoId = (typeof OBJETIVOS)[number]["id"];
export type HorarioId = (typeof HORARIOS)[number]["id"];

/** El enlace al directorio con lo elegido; sin nada elegido, el directorio entero. */
export function rutaDelBuscador(objetivo: ObjetivoId | null, horario: HorarioId | null): string {
  const p = new URLSearchParams();
  for (const goal of OBJETIVOS.find((o) => o.id === objetivo)?.goals ?? []) p.append("goal", goal);
  for (const [clave, valor] of HORARIOS.find((h) => h.id === horario)?.params ?? []) p.append(clave, valor);
  const qs = p.toString();
  return qs ? `/profesores?${qs}` : "/profesores";
}
