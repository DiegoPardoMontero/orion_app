import { describe, expect, it } from "vitest";
import { componerE164, leerNumero, parseTelefono, whatsappValido } from "@/lib/phone";

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

describe("whatsappValido", () => {
  it("acepta un celular colombiano y números extranjeros completos", () => {
    expect(whatsappValido("+573001112233")).toBe(true);
    expect(whatsappValido("+34600123456")).toBe(true);
    expect(whatsappValido("+15551234567")).toBe(true);
  });

  it("rechaza un fijo de Colombia, un celular incompleto y el vacío", () => {
    expect(whatsappValido("+576012345678")).toBe(false);
    expect(whatsappValido("+57300111223")).toBe(false);
    expect(whatsappValido("+5712")).toBe(false);
    expect(whatsappValido("")).toBe(false);
  });
});

describe("leerNumero", () => {
  it("pegar el número con su indicativo no lo duplica", () => {
    expect(leerNumero("57", "+57 300 123 4567")).toEqual({ dial: "57", local: "3001234567" });
    expect(leerNumero("57", "57 300 123 4567")).toEqual({ dial: "57", local: "3001234567" });
    expect(leerNumero("593", "593 98 765 4321")).toEqual({ dial: "593", local: "987654321" });
    expect(leerNumero("1", "1 (555) 123-4567")).toEqual({ dial: "1", local: "5551234567" });
  });

  it("con «+» y el indicativo de otro país de la lista, cambia de país", () => {
    expect(leerNumero("57", "+34 600 123 456")).toEqual({ dial: "34", local: "600123456" });
    expect(leerNumero("57", "+1 555 123 4567")).toEqual({ dial: "1", local: "5551234567" });
  });

  it("no toca un número local que empieza como el indicativo si no sobran dígitos", () => {
    expect(leerNumero("52", "5512345678")).toEqual({ dial: "52", local: "5512345678" });
    expect(leerNumero("57", "3001234567")).toEqual({ dial: "57", local: "3001234567" });
  });

  it("quita los separadores y deja escribir de a un dígito", () => {
    expect(leerNumero("57", "300-123")).toEqual({ dial: "57", local: "300123" });
    expect(leerNumero("57", "")).toEqual({ dial: "57", local: "" });
  });
});
