import type { EstadoTexto } from "@/lib/perfil-profesor";

/**
 * El aviso bajo un campo de la ficha del profesor. Se pinta en rojo solo cuando hay algo que
 * corregir: un campo aún vacío no es un error, es un campo que todavía no se ha escrito, y teñirlo
 * de rojo antes de que nadie escriba nada convierte el formulario en una regañina.
 */
export function ContadorPalabras({ id, estado }: { id: string; estado: EstadoTexto }) {
  const mal = estado.estado === "corto" || estado.estado === "largo";
  return (
    <p
      id={id}
      className={`mt-1.5 text-[12px] ${mal ? "font-semibold text-error" : "text-text-muted"}`}
    >
      {estado.mensaje}
    </p>
  );
}

/** El borde del campo acompaña al contador: rojo solo cuando hay algo que corregir. */
export function bordeSegun(estado: EstadoTexto): string {
  return estado.estado === "corto" || estado.estado === "largo"
    ? "border-error"
    : "border-border focus:border-primary";
}
