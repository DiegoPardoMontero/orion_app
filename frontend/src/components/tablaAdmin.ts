/**
 * Las tablas del panel del admin: en escritorio, una tabla; en el celular, cada fila es una tarjeta.
 * Nunca scroll horizontal (Pardo, 25/09: «odio ese scroll»). Por eso el contenedor no lleva
 * `overflow-x-auto`: si algo no cabe, se tiene que ver como un error y no esconderse en un scroll.
 *
 * <p>Sigue siendo un `<table>` también en el celular (solo cambia el `display`), así que las filas se
 * encuentran igual con `locator("tr")` y un lector de pantalla las lee como filas en escritorio.
 */
export const tablaAdmin = {
  contenedor: "rounded-card bg-surface-raised shadow-md",
  tabla: "block w-full text-[13px] lg:table",
  cabecera: "hidden lg:table-header-group",
  filaCabecera: "bg-surface text-left text-[11px] font-bold uppercase tracking-[0.1em] text-text-muted",
  th: "px-3 py-3 first:rounded-tl-card last:rounded-tr-card",
  cuerpo: "block lg:table-row-group",
  fila: "block border-t border-surface-sunken p-4 first:border-t-0 hover:bg-surface lg:table-row lg:p-0 lg:first:border-t",
  celda: "block py-1 lg:table-cell lg:px-3 lg:py-3 lg:align-middle",
  /** La de las acciones: en el celular, una fila de botones debajo de todo; en escritorio, a la derecha. */
  acciones: "block pt-3 lg:table-cell lg:px-3 lg:py-3 lg:text-right lg:align-middle",
} as const;
