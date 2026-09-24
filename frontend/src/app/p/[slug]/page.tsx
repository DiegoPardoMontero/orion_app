import { notFound, redirect } from "next/navigation";
import { serverFetch } from "@/lib/api/server";

/**
 * El enlace que un profesor comparte para invitar estudiantes: orionidiomas.com/p/maria-gomez. Se
 * resuelve en el servidor y lleva directo a su perfil, que se ve sin cuenta. Si el enlace no existe
 * o el perfil ya no está publicado, la página de «no encontrado» de siempre.
 */
export default async function EnlaceDeProfesor({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const profesor = await serverFetch<{ id: string }>(`/api/v1/professors/by-slug/${encodeURIComponent(slug)}`, 60);
  if (!profesor?.id) notFound();
  redirect(`/profesores/${profesor.id}`);
}
