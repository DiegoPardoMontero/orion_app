"use client";

import { createContext, useContext } from "react";
import { nombreDelIdioma } from "@/lib/idiomas";

/**
 * El idioma de la clase que se practica, para las piezas que lo nombran («Palabras en inglés», «Tu
 * dispositivo no tiene voz en francés»). Va en un contexto y no en props porque lo leen hojas muy
 * hondas del árbol de ejercicios; sin proveedor, es inglés, como siempre fue.
 */
const Contexto = createContext<string>("EN");

export const IdiomaDeLaClase = Contexto.Provider;

/** El código del idioma de la clase (EN, FR): para la voz del dispositivo. */
export function useCodigoDeLaClase(): string {
  return useContext(Contexto);
}

/** El nombre del idioma de la clase, en español: «inglés», «francés». */
export function useIdiomaDeLaClase(): string {
  return nombreDelIdioma(useContext(Contexto));
}
