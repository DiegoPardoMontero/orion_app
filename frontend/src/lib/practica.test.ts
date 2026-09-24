import { describe, expect, it } from "vitest";
import {
  leerPayload,
  mostrarEsperada,
  mostrarRespuesta,
  palabrasNuevas,
  primerNombre,
  rachaAlPrimerIntento,
  resumirLoTrabajado,
  unirFichas,
  type Ejercicio,
} from "./practica";

describe("la invitación a practicar", () => {
  it("resume lo trabajado en una línea, sin punto final y en minúscula para ir tras los dos puntos", () => {
    expect(resumirLoTrabajado("Trabajamos past simple; sigue diciendo 'I go yesterday'.")).toBe("trabajamos past simple");
    expect(resumirLoTrabajado("Condicionales.")).toBe("condicionales");
    expect(resumirLoTrabajado("   ")).toBeNull();
    expect(resumirLoTrabajado(null)).toBeNull();
  });

  it("corta lo trabajado demasiado largo con puntos suspensivos", () => {
    const largo = "Trabajamos " + "muchas cosas distintas ".repeat(10);
    const r = resumirLoTrabajado(largo)!;
    expect(r.length).toBeLessThanOrEqual(68);
    expect(r.endsWith("…")).toBe(true);
  });

  it("dice las palabras nuevas en letras, como se lee en voz alta", () => {
    expect(palabrasNuevas(1)).toBe("una palabra nueva");
    expect(palabrasNuevas(5)).toBe("cinco palabras nuevas");
    expect(palabrasNuevas(20)).toBe("20 palabras nuevas");
  });

  it("nombra al profesor por su nombre de pila", () => {
    expect(primerNombre("  María  Gómez ")).toBe("María");
  });
});

describe("leerPayload", () => {
  it("un payload ilegible no rompe la pantalla: vuelve vacío", () => {
    const roto = { payload: "{no es json" } as Ejercicio;
    expect(leerPayload<{ term?: string }>(roto)).toEqual({});
  });
});

describe("las respuestas de los tipos nuevos", () => {
  it("une las fichas como se escribe, sin espacio antes de la puntuación", () => {
    expect(unirFichas(["Where", "is", "my", "luggage", "?"])).toBe("Where is my luggage?");
    expect(unirFichas(["Yes", ",", "I", "do", "."])).toBe("Yes, I do.");
  });

  it("muestra la frase armada, y en «caza el error» la frase corregida", () => {
    expect(mostrarEsperada("BUILD_SENTENCE", '["Where","is","my","luggage","?"]')).toBe("Where is my luggage?");
    expect(mostrarEsperada("SPOT_ERROR", '{"index":2,"correction":"I have never been to Canada."}')).toBe(
      "I have never been to Canada.",
    );
    expect(mostrarEsperada("LISTEN_CHOOSE", "escala")).toBe("escala");
  });
});

describe("la racha dentro del set", () => {
  const item = (index: number, attempts: number, correct: boolean | null, skipped = false): Ejercicio =>
    ({ id: String(index), index, attempts, correct, closed: true, skipped }) as unknown as Ejercicio;

  it("cuenta los últimos seguidos al primer intento", () => {
    expect(rachaAlPrimerIntento([item(0, 2, true), item(1, 1, true), item(2, 1, true), item(3, 1, true)])).toBe(3);
  });

  it("un fallo la corta y lo saltado ni suma ni corta", () => {
    expect(rachaAlPrimerIntento([item(0, 1, true), item(1, 2, false)])).toBe(0);
    expect(rachaAlPrimerIntento([item(0, 1, true), item(1, 0, null, true), item(2, 1, true)])).toBe(2);
  });
});

describe("lo que ve el profesor", () => {
  it("muestra la palabra que tocó, no su posición", () => {
    expect(mostrarRespuesta("SPOT_ERROR", '{"tokens":["I","never","go","to","Canada."]}', "2")).toBe("tocó «go»");
    expect(mostrarRespuesta("BUILD_SENTENCE", "{}", '["Where","my","is","luggage","?"]')).toBe("Where my is luggage?");
    expect(mostrarRespuesta("FIX_SENTENCE", "{}", "I am 30")).toBe("I am 30");
  });
});
