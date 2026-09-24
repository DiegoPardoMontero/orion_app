import { describe, expect, it } from "vitest";
import {
  diaCorto,
  diaDeLaClase,
  estrellasDe,
  instruccionDe,
  leerPayload,
  mostrarEsperada,
  mostrarRespuesta,
  palabrasNuevas,
  palabrasNuevasDe,
  partirLinea,
  rachaDe,
  textoDelRegreso,
  tituloDelSet,
  primerNombre,
  rachaAlPrimerIntento,
  resumirLoTrabajado,
  unirFichas,
  type Ejercicio,
} from "./practica";
import { formaDe } from "@/components/practica/Constelacion";

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

describe("la constelación del set", () => {
  const item = (index: number, e: Partial<Ejercicio>): Ejercicio =>
    ({ id: String(index), index, attempts: 0, correct: null, closed: false, skipped: false, ...e }) as Ejercicio;

  it("pinta cada estrella según cómo quedó su ejercicio, y la que sigue como actual", () => {
    const items = [
      item(0, { attempts: 1, correct: true, closed: true }),
      item(1, { attempts: 2, correct: true, closed: true }),
      item(2, { attempts: 2, correct: false, closed: true }),
      item(3, { closed: true, skipped: true }),
      item(4, {}),
    ];
    expect(estrellasDe(items, "4")).toEqual(["primero", "segundo", "mostrada", "saltada", "actual"]);
    expect(estrellasDe([items[4], items[0]], null)).toEqual(["primero", "off"]);
  });

  it("la racha suma al primer intento, la cortan el segundo y la mostrada, y lo saltado no cuenta", () => {
    expect(rachaDe(["primero", "primero", "primero", "actual", "off"])).toBe(3);
    expect(rachaDe(["primero", "segundo", "primero"])).toBe(1);
    expect(rachaDe(["primero", "primero", "saltada", "primero"])).toBe(3);
    expect(rachaDe(["primero", "mostrada"])).toBe(0);
  });

  it("cada set tiene siempre la misma forma, de las cuatro", () => {
    const id = "47a81085-34d3-45af-b6e1-2c6daefb9f4f";
    expect(formaDe(id)).toBe(formaDe(id));
    expect([0, 1, 2, 3]).toContain(formaDe(id));
    expect(formaDe("no-es-un-uuid")).toBeGreaterThanOrEqual(0);
  });

  it("el regreso cuenta las estrellas encendidas y solo dice «primeras» si lo son", () => {
    const cerrado = (i: number, skipped = false) => item(i, { attempts: 1, correct: !skipped, closed: true, skipped });
    expect(textoDelRegreso([cerrado(0), cerrado(1), item(2, {})])).toBe("Tus dos primeras estrellas ya están encendidas.");
    expect(textoDelRegreso([cerrado(0), item(1, {})])).toBe("Tu primera estrella ya está encendida.");
    expect(textoDelRegreso([cerrado(0, true), cerrado(1), cerrado(2), item(3, {})])).toBe("Ya tienes dos estrellas encendidas.");
    expect(textoDelRegreso([cerrado(0, true), item(1, {})])).toBe("Arrancas en este mismo ejercicio.");
  });
});

describe("los textos de la pantalla de ejercicio", () => {
  it("parte la línea del diálogo en quién habla y qué dice", () => {
    expect(partirLinea("Receptionist: Welcome! Can I see your passport, please?")).toEqual({
      quien: "Receptionist",
      texto: "Welcome! Can I see your passport, please?",
    });
    expect(partirLinea("Sure. Here it is.")).toEqual({ quien: null, texto: "Sure. Here it is." });
  });

  it("resalta en la frase bien dicha solo las palabras que cambiaron", () => {
    const nuevas = palabrasNuevasDe(["I", "never", "go", "to", "Canada."], "I have never been to Canada.");
    expect([...nuevas]).toEqual([1, 3]);
  });

  it("la instrucción de «Tu frase» nombra el término", () => {
    expect(instruccionDe({ type: "WRITE_SENTENCE", payload: '{"term":"layover"}' } as Ejercicio)).toBe(
      "Escribe una frase tuya con «layover».",
    );
  });

  it("las fechas van como las escribe el diseño, en hora de Bogotá", () => {
    expect(diaDeLaClase("2026-09-24T01:00:00Z")).toBe("miércoles 23 sep");
    expect(diaCorto("2026-10-01T20:00:00-05:00")).toBe("jue 1 oct");
  });

  it("el título del set es lo trabajado, con mayúscula", () => {
    expect(tituloDelSet({ workedOn: "check-in en el hotel. Y más." })).toBe("Check-in en el hotel");
    expect(tituloDelSet({ workedOn: null })).toBeNull();
  });
});
