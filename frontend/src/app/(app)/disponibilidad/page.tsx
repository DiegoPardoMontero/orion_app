import { redirect } from "next/navigation";

/** Los horarios viven dentro de «Mi perfil» desde el 24/09/2026; los enlaces viejos llegan igual. */
export default function DisponibilidadPage() {
  redirect("/perfil?seccion=horarios");
}
