import { describe, expect, it } from "vitest";
import { desdeBase64Url } from "./avisosDispositivo";

describe("la clave VAPID en bytes", () => {
  it("decodifica base64url sin relleno, con - y _", () => {
    expect(Array.from(desdeBase64Url("-_8"))).toEqual([0xfb, 0xff]);
    expect(Array.from(desdeBase64Url("AQID"))).toEqual([1, 2, 3]);
  });
});
