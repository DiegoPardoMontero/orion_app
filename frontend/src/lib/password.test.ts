import { describe, expect, it } from "vitest";
import { fuerzaClave } from "@/lib/password";

describe("fuerzaClave", () => {
  it("una contraseña vacía es nivel 0 y pide el mínimo", () => {
    const f = fuerzaClave("");
    expect(f.nivel).toBe(0);
    expect(f.mensaje).toMatch(/8 caracteres/i);
  });

  it("sube de nivel con longitud, mayúsc+minúsc, número y símbolo", () => {
    expect(fuerzaClave("abcdefgh").nivel).toBe(1); // solo longitud
    expect(fuerzaClave("Abcdefgh").nivel).toBe(2); // + mayúscula/minúscula
    expect(fuerzaClave("Abcdefg1").nivel).toBe(3); // + número
    expect(fuerzaClave("Abcdefg1!").nivel).toBe(4); // + símbolo
  });

  it("una clave corta pero variada no alcanza el punto de longitud", () => {
    // "Ab1!" tiene mayús/minús, número y símbolo, pero < 8 caracteres.
    expect(fuerzaClave("Ab1!").nivel).toBe(3);
  });
});
