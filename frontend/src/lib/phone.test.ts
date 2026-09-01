import { describe, expect, it } from "vitest";
import { componerE164, parseTelefono } from "@/lib/phone";

describe("parseTelefono", () => {
  it("separa un E.164 colombiano en país + local", () => {
    expect(parseTelefono("+573001112233")).toEqual({ dial: "57", local: "3001112233" });
  });

  it("reconoce un indicativo largo (Ecuador +593) antes que uno corto", () => {
    expect(parseTelefono("+593987654321")).toEqual({ dial: "593", local: "987654321" });
  });

  it("cae a Colombia cuando no hay valor", () => {
    expect(parseTelefono("")).toEqual({ dial: "57", local: "" });
    expect(parseTelefono(undefined)).toEqual({ dial: "57", local: "" });
  });

  it("ignora separadores", () => {
    expect(parseTelefono("+57 300 111-2233")).toEqual({ dial: "57", local: "3001112233" });
  });
});

describe("componerE164", () => {
  it("arma el E.164 con indicativo + local", () => {
    expect(componerE164("57", "3001112233")).toBe("+573001112233");
  });

  it("un número local vacío produce cadena vacía (sin teléfono)", () => {
    expect(componerE164("57", "")).toBe("");
  });

  it("limpia separadores del número local", () => {
    expect(componerE164("34", "600 123 456")).toBe("+34600123456");
  });
});
