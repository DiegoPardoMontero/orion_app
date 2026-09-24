import { describe, expect, it } from "vitest";
import { cifraDePuntos, queDioPuntos } from "./puntos";

describe("los puntos, dichos en palabras", () => {
  it("cuenta con quién fue la clase, el mensaje o la reseña", () => {
    expect(queDioPuntos({ source: "LESSON", detail: "María" })).toBe("Clase con María");
    expect(queDioPuntos({ source: "MESSAGE", detail: "Juan" })).toBe("Primer mensaje a Juan");
    expect(queDioPuntos({ source: "REVIEW", detail: null })).toBe("Calificaste tu clase");
  });

  it("un logro lleva su nombre", () => {
    expect(queDioPuntos({ source: "ACHIEVEMENT", detail: "Ficha completa" })).toBe("Logro · Ficha completa");
  });

  it("las cifras llevan separador de miles, como en Colombia", () => {
    expect(cifraDePuntos(1240)).toBe("1.240");
    expect(cifraDePuntos(25)).toBe("25");
  });
});
