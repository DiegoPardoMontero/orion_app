import { redirect } from "next/navigation";

/**
 * «Mi cielo» dejó de ser una pantalla suelta: vive dentro del perfil, que es de quien es.
 *
 * <p>La ruta se conserva y redirige porque ya circula: hay notificaciones y correos de logros que
 * apuntan aquí, y romperlos por mover una sección sería cobrarle al usuario nuestra reorganización.
 */
export default function LogrosPage() {
  redirect("/cuenta?seccion=cielo");
}
