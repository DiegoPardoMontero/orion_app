import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api/fetch";

/**
 * Los adolescentes a cargo del adulto (Bloque 12) y la habilitación de un profe para darles clase.
 * Los tipos espejan los DTOs del backend (`AdolescentProfileResponse`, `MinorsClearanceView`).
 */
export type Adolescente = {
  id: string;
  firstName: string;
  birthYear: number;
  age: number;
  selfDeclaredLevel: "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | null;
  motivation: string | null;
  authorizationVersion: string | null;
  authorizedAt: string | null;
};

export type HabilitacionAdolescentes = {
  active: boolean;
  clearedUntil: string | null;
  clearedAt: string | null;
  conductAccepted: boolean;
  registryCheck: boolean;
  backgroundCheck: boolean;
  missing: string[];
};

export const NIVELES: { code: Adolescente["selfDeclaredLevel"]; label: string }[] = [
  { code: "BEGINNER", label: "Empezando" },
  { code: "INTERMEDIATE", label: "Se defiende" },
  { code: "ADVANCED", label: "Avanzado" },
];

/** Los perfiles vivos del adulto. Solo un estudiante los tiene; para otros roles la consulta no corre. */
export function useAdolescentes(habilitado: boolean) {
  return useQuery({
    queryKey: ["me", "adolescents"],
    queryFn: () => apiFetch<Adolescente[]>("/api/v1/me/adolescents"),
    enabled: habilitado,
    staleTime: 60_000,
  });
}

/** Los años de nacimiento que hoy dan entre 13 y 17: el servidor repite la cuenta con la zona de Bogotá. */
export function anosDeNacimientoPosibles(anoActual: number): number[] {
  const anos: number[] = [];
  for (let edad = 13; edad <= 17; edad++) anos.push(anoActual - edad);
  return anos;
}

/** La habilitación tal como llega del API (campos opcionales en el esquema), completa para la pantalla. */
export function habilitacionDe(h: Partial<HabilitacionAdolescentes> | null | undefined): HabilitacionAdolescentes | null {
  if (!h) return null;
  return {
    active: h.active ?? false,
    clearedUntil: h.clearedUntil ?? null,
    clearedAt: h.clearedAt ?? null,
    conductAccepted: h.conductAccepted ?? false,
    registryCheck: h.registryCheck ?? false,
    backgroundCheck: h.backgroundCheck ?? false,
    missing: h.missing ?? [],
  };
}

/** Lo que dice la fila de un profe en Usuarios sobre su habilitación. */
export function estadoDeAdolescentes(h: HabilitacionAdolescentes | null | undefined): string {
  if (!h) return "";
  if (h.active && h.clearedUntil) return `Habilitado para adolescentes hasta el ${fechaCorta(h.clearedUntil)}`;
  if (h.clearedUntil) return `Habilitación para adolescentes vencida el ${fechaCorta(h.clearedUntil)}`;
  if (h.missing.length === 0) return "Listo para habilitar para adolescentes";
  return "Sin habilitación para adolescentes";
}

function fechaCorta(iso: string): string {
  const [a, m, d] = iso.split("-").map(Number);
  return new Date(a, m - 1, d).toLocaleDateString("es-CO", { day: "numeric", month: "long", year: "numeric" });
}
