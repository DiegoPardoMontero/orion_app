import { describe, expect, it } from "vitest";
import { parseInline, parseMarkdown } from "./markdownLegal";

describe("parseInline", () => {
  it("deja el texto plano intacto", () => {
    expect(parseInline("Orión es un portal de contacto.")).toEqual([
      { tipo: "texto", texto: "Orión es un portal de contacto." },
    ]);
  });

  it("reconoce la negrita en medio de una frase", () => {
    expect(parseInline("El plazo es de **15 días** calendario.")).toEqual([
      { tipo: "texto", texto: "El plazo es de " },
      { tipo: "fuerte", texto: "15 días" },
      { tipo: "texto", texto: " calendario." },
    ]);
  });

  it("reconoce enlaces", () => {
    expect(parseInline("Ver la [Política](/privacidad) completa.")).toEqual([
      { tipo: "texto", texto: "Ver la " },
      { tipo: "enlace", texto: "Política", href: "/privacidad" },
      { tipo: "texto", texto: " completa." },
    ]);
  });

  /** Los corchetes de un enlace mandan sobre los asteriscos: si no, se parte el enlace. */
  it("no deja que la negrita rompa un enlace", () => {
    const salida = parseInline("[**Superintendencia**](https://sic.gov.co)");
    expect(salida).toHaveLength(1);
    expect(salida[0]).toMatchObject({ tipo: "enlace", href: "https://sic.gov.co" });
  });
});

describe("parseMarkdown", () => {
  it("separa títulos de párrafos", () => {
    const bloques = parseMarkdown("## Uno\n\nTexto del uno.\n\n### Sub\n\nOtro.");
    expect(bloques.map((b) => b.tipo)).toEqual(["titulo", "parrafo", "titulo", "parrafo"]);
    expect(bloques[0]).toMatchObject({ nivel: 2 });
    expect(bloques[2]).toMatchObject({ nivel: 3 });
  });

  it("junta las líneas de un mismo párrafo", () => {
    const bloques = parseMarkdown("Una frase larga\nque sigue en la línea siguiente.");
    expect(bloques).toHaveLength(1);
    expect(bloques[0]).toMatchObject({
      tipo: "parrafo",
      contenido: [{ tipo: "texto", texto: "Una frase larga que sigue en la línea siguiente." }],
    });
  });

  it("lee listas con viñeta y numeradas", () => {
    const bloques = parseMarkdown("- uno\n- dos\n\n1. primero\n2. segundo");
    expect(bloques[0]).toMatchObject({ tipo: "lista", ordenada: false });
    expect(bloques[1]).toMatchObject({ tipo: "lista", ordenada: true });
    expect((bloques[0] as { items: unknown[] }).items).toHaveLength(2);
  });

  it("lee una tabla con sus encabezados", () => {
    const bloques = parseMarkdown(
      "| Situación | Qué pasa |\n|---|---|\n| Cancelas | Saldo |\n| No vienes | Nada |",
    );
    expect(bloques).toHaveLength(1);
    const tabla = bloques[0] as {
      tipo: string;
      encabezados: unknown[];
      filas: unknown[];
    };
    expect(tabla.tipo).toBe("tabla");
    expect(tabla.encabezados).toHaveLength(2);
    expect(tabla.filas).toHaveLength(2);
  });

  /** La tabla de identidad del responsable no lleva encabezados visibles: dos columnas vacías. */
  it("acepta una tabla con encabezados vacíos", () => {
    const bloques = parseMarkdown("| | |\n|---|---|\n| **Responsable** | Alguien |");
    expect(bloques[0]).toMatchObject({ tipo: "tabla" });
    expect((bloques[0] as { filas: unknown[] }).filas).toHaveLength(1);
  });

  /** Sin la fila separadora no es una tabla: es un párrafo que casualmente lleva pipes. */
  it("no confunde un párrafo con pipes con una tabla", () => {
    const bloques = parseMarkdown("Escríbenos a a@b.co | también por WhatsApp.");
    expect(bloques[0].tipo).toBe("parrafo");
  });

  it("ignora las líneas en blanco sobrantes", () => {
    expect(parseMarkdown("\n\n\n## Solo\n\n\n")).toHaveLength(1);
  });
});
