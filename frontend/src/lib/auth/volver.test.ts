import { describe, expect, it } from "vitest";
import { destinoSeguro, entrarYVolver } from "./volver";

describe("destinoSeguro", () => {
  it("acepta una ruta de la app", () => {
    expect(destinoSeguro("/profesores/7b85d43e-4d24-4635-9bb9-bfa698783d03")).toBe(
      "/profesores/7b85d43e-4d24-4635-9bb9-bfa698783d03",
    );
    expect(destinoSeguro("/profesores?goal=BUSINESS")).toBe("/profesores?goal=BUSINESS");
  });

  it("rechaza lo que el navegador leería como otro sitio: nada de redirecciones abiertas", () => {
    for (const malo of ["https://otro.sitio", "//otro.sitio", "/\\otro.sitio", "javascript:alert(1)", "profesores", " /x"]) {
      expect(destinoSeguro(malo)).toBeNull();
    }
    expect(destinoSeguro(null)).toBeNull();
    expect(destinoSeguro("/" + "x".repeat(300))).toBeNull();
  });

  it("la ruta de entrada lleva el regreso codificado y vuelve entera", () => {
    const url = entrarYVolver("/login", "/profesores/abc?goal=TRAVEL");
    expect(url).toBe("/login?volver=%2Fprofesores%2Fabc%3Fgoal%3DTRAVEL");
    expect(destinoSeguro(new URLSearchParams(url.split("?")[1]).get("volver"))).toBe("/profesores/abc?goal=TRAVEL");
  });
});
