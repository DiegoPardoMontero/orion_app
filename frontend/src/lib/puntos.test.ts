import { describe, expect, it } from "vitest";
import { cifraDePuntos } from "./puntos";

describe("los puntos, dichos en cifras", () => {
  it("las cifras llevan separador de miles, como en Colombia", () => {
    expect(cifraDePuntos(1240)).toBe("1.240");
    expect(cifraDePuntos(25)).toBe("25");
  });
});
