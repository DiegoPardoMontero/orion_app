import { describe, expect, it } from "vitest";
import {
  aniosDeExperiencia,
  enumerar,
  faltasDelPaso,
  PASO_REVISION,
  primerPasoIncompleto,
  type BorradorPostulacion,
} from "@/lib/aplicacion";

const COMPLETO: BorradorPostulacion = {
  tieneFoto: true,
  titular: "Conversación en inglés para adultos que ya estudiaron",
  bio: "Doy clases de inglés desde hace ocho años a adultos que ya estudiaron y quieren por fin hablar con soltura en el trabajo y en sus viajes.",
  idiomas: [{ code: "en", levels: ["B1", "B2"] }],
  objetivos: ["CONVERSATION"],
  pais: "CO",
  ciudad: "Bogotá",
  anios: "8",
  formacion: "Licenciatura en Lenguas Modernas",
  tieneCv: true,
  tieneLlave: true,
  aceptoAcuerdo: true,
};

const campos = (paso: number, b: BorradorPostulacion) => faltasDelPaso(paso, b).map((f) => f.campo);

describe("faltasDelPaso", () => {
  it("con todo lleno, ningún paso pide nada y se llega a la revisión", () => {
    for (let paso = 0; paso <= PASO_REVISION; paso++) expect(faltasDelPaso(paso, COMPLETO)).toEqual([]);
    expect(primerPasoIncompleto(COMPLETO)).toBe(PASO_REVISION);
  });

  it("el primer paso pide la foto y un título de cinco palabras", () => {
    expect(campos(0, { ...COMPLETO, tieneFoto: false, titular: "" })).toEqual(["foto", "headline"]);
    const corto = faltasDelPaso(0, { ...COMPLETO, titular: "Profesora de inglés" });
    expect(corto[0].mensaje).toMatch(/Te faltan 2/);
  });

  it("la enseñanza pide presentación en su rango, idiomas con nivel y un objetivo", () => {
    expect(campos(1, { ...COMPLETO, bio: "Muy poco", idiomas: [], objetivos: [] })).toEqual([
      "bio",
      "idiomas",
      "objetivos",
    ]);
    // Cada idioma, su nivel: el backend se conforma con uno en total, el wizard no.
    expect(campos(1, { ...COMPLETO, idiomas: [{ code: "en", levels: ["B1"] }, { code: "fr", levels: [] }] })).toEqual([
      "niveles-fr",
    ]);
  });

  it("la experiencia pide país, ciudad, años (el cero vale) y formación", () => {
    expect(campos(2, { ...COMPLETO, pais: "", ciudad: " ", anios: "", formacion: "" })).toEqual([
      "country",
      "city",
      "years",
      "education",
    ]);
    expect(campos(2, { ...COMPLETO, anios: "0" })).toEqual([]);
    expect(campos(2, { ...COMPLETO, anios: "81" })).toEqual(["years"]);
  });

  it("los documentos piden el CV, los pagos la llave Bre-B y el acuerdo, aceptarlo", () => {
    expect(campos(3, { ...COMPLETO, tieneCv: false })).toEqual(["doc-CV"]);
    expect(campos(4, { ...COMPLETO, tieneLlave: false })).toEqual(["llave"]);
    expect(campos(5, { ...COMPLETO, aceptoAcuerdo: false })).toEqual(["acuerdo"]);
  });

  it("sin llave Bre-B la postulación se abre en «Pagos», antes del acuerdo", () => {
    expect(primerPasoIncompleto({ ...COMPLETO, tieneLlave: false, aceptoAcuerdo: false })).toBe(4);
  });

  it("el primer paso incompleto es el que se abre", () => {
    expect(primerPasoIncompleto({ ...COMPLETO, formacion: "" })).toBe(2);
    expect(primerPasoIncompleto({ ...COMPLETO, tieneFoto: false, tieneCv: false })).toBe(0);
  });
});

describe("aniosDeExperiencia", () => {
  it("acepta enteros de 0 a 80 y nada más", () => {
    expect(aniosDeExperiencia("0")).toBe(0);
    expect(aniosDeExperiencia(" 12 ")).toBe(12);
    expect(aniosDeExperiencia("")).toBeNull();
    expect(aniosDeExperiencia("2.5")).toBeNull();
    expect(aniosDeExperiencia("-1")).toBeNull();
    expect(aniosDeExperiencia("99")).toBeNull();
  });
});

describe("enumerar", () => {
  it("junta los nombres en español y sin repetir", () => {
    expect(enumerar(["tu foto"])).toBe("tu foto");
    expect(enumerar(["tu foto", "el título"])).toBe("tu foto y el título");
    expect(enumerar(["tu país", "tu ciudad", "tu formación"])).toBe("tu país, tu ciudad y tu formación");
    expect(enumerar(["los niveles", "los niveles"])).toBe("los niveles");
  });
});
