import { describe, expect, it } from "vitest";
import { canAccess } from "./roles";

const PERFIL = "/profesores/7b85d43e-4d24-4635-9bb9-bfa698783d03";

describe("canAccess: el perfil de un profesor y el catálogo", () => {
  it("el profesor abre el perfil de un profesor (el suyo, para verse como lo ven)", () => {
    expect(canAccess("PROFESSOR", PERFIL)).toBe(true);
  });

  it("pero no el catálogo: ahí se elige a quién reservar", () => {
    expect(canAccess("PROFESSOR", "/profesores")).toBe(false);
  });

  it("el estudiante sigue entrando a los dos", () => {
    expect(canAccess("STUDENT", "/profesores")).toBe(true);
    expect(canAccess("STUDENT", PERFIL)).toBe(true);
  });

  it("el aspirante y el admin, a ninguno de los dos", () => {
    for (const rol of ["TEACHER_APPLICANT", "ADMIN"] as const) {
      expect(canAccess(rol, "/profesores")).toBe(false);
      expect(canAccess(rol, PERFIL)).toBe(false);
    }
  });
});
