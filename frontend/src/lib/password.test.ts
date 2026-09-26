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

  it("corta, dice primero el largo y nunca «Fuerte», por variada que sea", () => {
    for (const clave of ["abc", "Ab1!", "Abc12!x"]) {
      const { mensaje } = fuerzaClave(clave);
      expect(mensaje).toMatch(/^Muy corta todavía/);
      expect(mensaje).toMatch(/8 caracteres/);
      expect(mensaje).not.toMatch(/Fuerte|Excelente/);
    }
  });

  it("con el largo, pide la regla que de verdad falta", () => {
    expect(fuerzaClave("abcdefgh").mensaje).toBe("Débil: súmale una mayúscula");
    // Ya tiene número: lo que falta es la mayúscula, no el número.
    expect(fuerzaClave("abcdefg1").mensaje).toBe("Vas bien: añade una mayúscula");
    expect(fuerzaClave("Abcdefgh").mensaje).toBe("Vas bien: añade un número");
    expect(fuerzaClave("ABCDEFG1!").mensaje).toBe("Fuerte: una minúscula la blinda");
    expect(fuerzaClave("Abcdefg!").mensaje).toBe("Fuerte: un número la blinda");
    expect(fuerzaClave("Abcdefg1").mensaje).toBe("Fuerte: un símbolo la blinda");
    expect(fuerzaClave("Abcdefg1!").mensaje).toBe("Excelente contraseña");
  });
});
