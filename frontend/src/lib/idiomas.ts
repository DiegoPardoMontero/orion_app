/**
 * El nombre de un idioma enseñado, desde su código del catálogo. Es el espejo de
 * `NombreDelIdioma` del backend: con el francés de vuelta (V82, 14/10/2026), los textos que decían
 * «inglés» a secas pasan a decir el idioma de la clase.
 */
const EN_ESPANOL: Record<string, string> = { EN: "inglés", FR: "francés", ES: "español" };

/** El idioma del sintetizador de voz del navegador para cada código del catálogo. */
const BCP47: Record<string, { prefijo: string; preferido: string }> = {
  EN: { prefijo: "en", preferido: "en-US" },
  FR: { prefijo: "fr", preferido: "fr-FR" },
  ES: { prefijo: "es", preferido: "es-CO" },
};

export function codigoDeIdioma(code: string | null | undefined): string {
  return code && code.trim() !== "" ? code.toUpperCase() : "EN";
}

export function nombreDelIdioma(code: string | null | undefined): string {
  return EN_ESPANOL[codigoDeIdioma(code)] ?? "inglés";
}

export function vozDelIdioma(code: string | null | undefined): { prefijo: string; preferido: string } {
  return BCP47[codigoDeIdioma(code)] ?? BCP47.EN;
}
