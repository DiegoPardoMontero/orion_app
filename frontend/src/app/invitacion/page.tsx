import { redirect } from "next/navigation";

/**
 * Los correos de invitación de antes de la V71 traían `/invitacion?token=…`. Ese mismo token sigue
 * siendo válido, así que se lleva a la pantalla de invitación de ahora, `/invitacion/{token}`. Sin
 * token no hay nada que mostrar: al inicio.
 */
export default async function InvitacionAntigua({
  searchParams,
}: {
  searchParams: Promise<{ token?: string }>;
}) {
  const { token } = await searchParams;
  redirect(token ? `/invitacion/${encodeURIComponent(token)}` : "/");
}
