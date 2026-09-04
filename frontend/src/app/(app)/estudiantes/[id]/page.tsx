"use client";

import { useQuery } from "@tanstack/react-query";
import { Target } from "lucide-react";
import { useParams } from "next/navigation";
import { Suspense } from "react";
import { Cargando, Vacio } from "@/components/estados";
import { AvatarOrion } from "@/components/gamificacion/AvatarOrion";
import { apiFetch } from "@/lib/api/fetch";
import { NIVEL_ESTUDIANTE, type FichaEstudiante } from "@/lib/gamificacion";

/**
 * El perfil de un estudiante visto por otra persona.
 *
 * <p>Las tres capas de visibilidad las aplica el servidor: si no hay derecho a verlo responde 404,
 * y aquí eso se muestra como «no encontramos este perfil» —nunca como «no tienes permiso», que
 * confirmaría que existe—.
 */
export default function PerfilEstudiantePage() {
  return (
    <Suspense fallback={null}>
      <Contenido />
    </Suspense>
  );
}

function Contenido() {
  const { id } = useParams<{ id: string }>();

  const perfil = useQuery({
    queryKey: ["student-profile", id],
    queryFn: () => apiFetch<FichaEstudiante>(`/api/v1/students/${id}/profile`),
    retry: false,
  });

  if (perfil.isPending) {
    return (
      <main className="mx-auto w-full max-w-md px-5 py-6 lg:max-w-2xl lg:px-12">
        <Cargando filas={3} />
      </main>
    );
  }

  if (perfil.isError || !perfil.data) {
    return (
      <main className="mx-auto w-full max-w-md px-5 py-6 lg:max-w-2xl lg:px-12">
        <Vacio
          titulo="No encontramos este perfil"
          texto="Puede que no exista o que su dueño lo tenga en privado."
        />
      </main>
    );
  }

  const ficha = perfil.data;

  return (
    <main className="mx-auto w-full max-w-md px-5 py-6 lg:max-w-2xl lg:px-12 lg:py-8">
      <div className="flex flex-col items-center text-center">
        <AvatarOrion
          nombre={ficha.fullName}
          fotoUrl={ficha.photoUrl}
          frameCode={ficha.frameCode}
          paletteCode={ficha.paletteCode}
          skyCode={ficha.skyCode}
          accesorios={ficha.accessories}
          size={130}
        />
        <h1 className="mt-5 font-display text-h2 font-bold">{ficha.fullName}</h1>
        {ficha.selfDeclaredLevel && (
          <p className="mt-1 text-[14px] text-text-secondary">
            {NIVEL_ESTUDIANTE[ficha.selfDeclaredLevel]}
            {ficha.primaryLanguage ? ` · ${ficha.primaryLanguage}` : ""}
          </p>
        )}
      </div>

      {ficha.motivation && (
        <p className="mt-6 rounded-card border border-border bg-surface-raised p-5 text-[14.5px] leading-relaxed text-text-secondary">
          {ficha.motivation}
        </p>
      )}

      {ficha.goalCodes.length > 0 && (
        <section className="mt-5">
          <h2 className="flex items-center gap-1.5 text-[12px] font-bold uppercase tracking-[0.06em] text-text-secondary">
            <Target size={14} strokeWidth={2} />
            Para qué aprende
          </h2>
          <div className="mt-2 flex flex-wrap gap-2">
            {ficha.goalCodes.map((code) => (
              <span
                key={code}
                className="rounded-pill bg-surface-sunken px-3 py-1.5 text-[12.5px] font-semibold text-text-secondary"
              >
                {code}
              </span>
            ))}
          </div>
        </section>
      )}

      {/* El perfil público NO lleva correo, teléfono, saldo ni con quién ha practicado. No es que
          no se pinten: es que no viajan. */}
    </main>
  );
}
