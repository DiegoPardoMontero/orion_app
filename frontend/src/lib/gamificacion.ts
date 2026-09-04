import type { FamiliaLogro } from "@/components/gamificacion/EstrellaLogro";

/** Lo que devuelve `/api/v1/me/engagement`. */
export type Engagement = {
  points: number;
  currentStreakWeeks: number;
  bestStreakWeeks: number;
  protectedWeeks: number;
  sealLevel: 1 | 2 | 3;
  unlockedCount: number;
  totalCount: number;
};

export type Logro = {
  code: string;
  family: FamiliaLogro;
  name: string;
  description: string;
  progress: number;
  target: number;
  glow: 1 | 2 | 3;
  points: number;
  unlocked: boolean;
  unlockedAt: string | null;
};

export type Cosmetico = {
  kind: "FRAME" | "PALETTE" | "SKY" | "ACCESSORY";
  code: string;
  name: string;
  zone: string | null;
  unlocked: boolean;
  unlockCondition: string;
  equipped: boolean;
};

export type SemanaRacha = {
  weekStart: string;
  status: "CUMPLIDA" | "EN_CURSO" | "PROTEGIDA" | "VACIA";
};

export type MapaRacha = { weeks: SemanaRacha[] };

export type FichaEstudiante = {
  id: string;
  fullName: string;
  photoUrl: string | null;
  selfDeclaredLevel: "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | null;
  primaryLanguage: string | null;
  motivation: string | null;
  goalCodes: string[];
  frameCode: string;
  paletteCode: string;
  skyCode: string;
  accessories: { zone: string; accessoryCode: string }[];
  isPublic: boolean | null;
  birthDate: string | null;
  ownView: boolean;
};

/** El estado visual de una estrella, derivado de su progreso. */
export function estadoDe(logro: Logro): "apagada" | "progreso" | "encendida" {
  if (logro.unlocked) return "encendida";
  return logro.progress > 0 ? "progreso" : "apagada";
}

/**
 * El numeral que va dentro de la estrella. Los logros de cuenta lo llevan; los demás usan glifo.
 * Se saca del propio código —`volumen-25-clases` → «25»— en vez de una tabla aparte, para que un
 * logro nuevo del mismo tipo no obligue a tocar el frontend.
 */
export function numeralDe(code: string): string | undefined {
  const m = code.match(/-(\d+)-/);
  return m ? m[1] : undefined;
}

export const NOMBRE_FAMILIA: Record<FamiliaLogro, string> = {
  PRIMEROS: "Primeros pasos",
  CONSTANCIA: "Constancia",
  VOLUMEN: "Volumen",
  AMPLITUD: "Amplitud",
  COMPROMISO: "Compromiso",
};

export const ORDEN_FAMILIAS: FamiliaLogro[] = [
  "PRIMEROS",
  "CONSTANCIA",
  "VOLUMEN",
  "AMPLITUD",
  "COMPROMISO",
];

export const NIVEL_ESTUDIANTE: Record<string, string> = {
  BEGINNER: "Principiante",
  INTERMEDIATE: "Intermedio",
  ADVANCED: "Avanzado",
};
