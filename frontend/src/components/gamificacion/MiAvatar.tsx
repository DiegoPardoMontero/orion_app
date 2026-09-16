"use client";

import { useQuery } from "@tanstack/react-query";
import { Avatar } from "@/components/Avatar";
import { AvatarOrion } from "@/components/gamificacion/AvatarOrion";
import { apiFetch } from "@/lib/api/fetch";
import { useMe } from "@/lib/auth/session";
import type { Engagement, FichaEstudiante } from "@/lib/gamificacion";

/**
 * Tu avatar, con el marco y los accesorios que te pusiste, en todas partes.
 *
 * <p>El problema que arregla: la personalización solo se veía en «Mi cielo» y en la ficha. Ponerse
 * un marco y no volver a verlo nunca convierte la recompensa en un ajuste que no hace nada — y esa
 * es exactamente la recompensa que la gamificación entera promete.
 *
 * <p>No hace falta tocar el backend para esto: la personalización ya viaja en
 * {@code /me/student-profile}. Se pide una vez y se comparte por caché entre todas las pantallas,
 * con una vida larga porque cambia cuando la persona la cambia y no sola.
 *
 * <p>Solo aplica a estudiantes: la personalización vive en {@code StudentProfile}. Para cualquier
 * otro rol —y mientras la consulta viaja— cae al avatar de siempre, que es la foto y las iniciales.
 */
export function MiAvatar({
  size = 36,
  className = "",
}: {
  size?: number;
  className?: string;
}) {
  const { data: me } = useMe();
  const esEstudiante = me?.role === "STUDENT";

  const ficha = useQuery({
    queryKey: ["me", "student-profile"],
    queryFn: () => apiFetch<FichaEstudiante>("/api/v1/me/student-profile"),
    enabled: esEstudiante,
    staleTime: 10 * 60_000,
  });

  const engagement = useQuery({
    queryKey: ["me", "engagement"],
    queryFn: () => apiFetch<Engagement>("/api/v1/me/engagement"),
    enabled: esEstudiante,
    staleTime: 10 * 60_000,
  });

  if (!me) return null;

  if (!esEstudiante || !ficha.data) {
    return (
      <Avatar nombre={me.fullName} fotoUrl={me.photoUrl} size="sm" className={className} />
    );
  }

  return (
    <AvatarOrion
      nombre={me.fullName}
      fotoUrl={me.photoUrl}
      frameCode={ficha.data.frameCode}
      paletteCode={ficha.data.paletteCode}
      skyCode={ficha.data.skyCode}
      sealLevel={engagement.data?.sealLevel ?? 1}
      accesorios={ficha.data.accessories.map((a) => ({
        zone: a.zone,
        accessoryCode: a.accessoryCode,
      }))}
      size={size}
      className={className}
    />
  );
}
