"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Check, Eye } from "lucide-react";
import { useState } from "react";
import { CambiarFoto } from "@/components/CambiarFoto";
import { AvisoError, Cargando, ErrorCarga } from "@/components/estados";
import { BotonPrincipal, Campo, Toggle } from "@/components/ui";
import { ApiError, apiFetch } from "@/lib/api/fetch";
import type { ProfileResponse } from "@/lib/api/types";

export default function PerfilPage() {
  const perfil = useQuery({
    queryKey: ["me", "profile"],
    queryFn: () => apiFetch<ProfileResponse>("/api/v1/me/profile"),
  });

  if (perfil.isPending) {
    return (
      <main className="px-5 py-5">
        <Cargando filas={3} />
      </main>
    );
  }

  if (perfil.isError) {
    return (
      <main className="px-5 py-5">
        <ErrorCarga
          mensaje="No pudimos cargar tu perfil."
          onReintentar={() => void perfil.refetch()}
        />
      </main>
    );
  }

  // El formulario se monta ya con los datos, así no hay que sembrarlo desde un efecto: nace
  // con su estado inicial y a partir de ahí es su dueño, sin que un refetch pise lo que escribes.
  return <FormularioPerfil inicial={perfil.data} />;
}

function FormularioPerfil({ inicial }: { inicial: ProfileResponse }) {
  const queryClient = useQueryClient();

  const [headline, setHeadline] = useState(inicial.headline ?? "");
  const [bio, setBio] = useState(inicial.bio ?? "");
  const [publicado, setPublicado] = useState(inicial.isPublished ?? false);
  const [guardado, setGuardado] = useState(false);

  const guardar = useMutation({
    mutationFn: () =>
      apiFetch<ProfileResponse>("/api/v1/me/profile", {
        method: "PUT",
        body: {
          headline: headline.trim() || undefined,
          bio: bio.trim() || undefined,
          isPublished: publicado,
        },
      }),
    onSuccess: (actualizado) => {
      queryClient.setQueryData(["me", "profile"], actualizado);
      // Publicarse o despublicarse cambia el directorio que ven los estudiantes.
      void queryClient.invalidateQueries({ queryKey: ["professors"] });
      setGuardado(true);
      setTimeout(() => setGuardado(false), 3000);
    },
  });

  const error = guardar.error instanceof ApiError ? guardar.error.message : null;

  return (
    <main className="px-5 py-5">
      <h1 className="font-display text-h1 font-bold">Mi perfil</h1>
      <p className="mt-1 text-[12.5px] text-text-secondary">Esto es lo que ven los estudiantes.</p>

      <div className="mt-5">
        <CambiarFoto nombre={inicial.fullName ?? ""} fotoUrl={inicial.photoUrl} />
      </div>

      <label className="mt-5 block text-[12.5px] font-bold text-text-secondary" htmlFor="headline">
        Titular
      </label>
      <Campo
        id="headline"
        type="text"
        maxLength={120}
        value={headline}
        onChange={(event) => setHeadline(event.target.value)}
        placeholder="Conversación y confianza · A1–B1"
        className="mt-1.5"
      />

      <label className="mt-4 block text-[12.5px] font-bold text-text-secondary" htmlFor="bio">
        Sobre ti
      </label>
      <textarea
        id="bio"
        rows={4}
        value={bio}
        onChange={(event) => setBio(event.target.value)}
        placeholder="Cuéntales cómo son tus clases."
        className="mt-1.5 w-full rounded-base border-[1.5px] border-border bg-surface-raised px-4 py-3 text-sm placeholder:text-text-muted focus:border-primary focus:shadow-focus focus:outline-none"
      />

      <section className="mt-5 rounded-card bg-success-bg p-4">
        <div className="flex items-center justify-between gap-3">
          <div>
            <p className="flex items-center gap-1.5 text-[13.5px] font-bold text-success">
              <Eye size={15} strokeWidth={2.2} />
              Perfil visible
            </p>
            <p className="mt-0.5 text-[11.5px] text-text-secondary">
              Los estudiantes pueden verte y reservar
            </p>
          </div>
          <Toggle activo={publicado} onCambio={setPublicado} etiqueta="Perfil visible" />
        </div>

        {!publicado && (
          <p className="mt-3 rounded-base bg-warning-bg px-3.5 py-2.5 text-[12px] text-warning">
            Los estudiantes dejarán de verte y no podrán reservar contigo. Tus clases ya agendadas
            siguen en pie.
          </p>
        )}
      </section>

      {error && (
        <div className="mt-4">
          <AvisoError mensaje={error} />
        </div>
      )}

      {guardado && (
        <p className="mt-4 flex items-center gap-2 rounded-card bg-success-bg px-4 py-3 text-[13px] font-semibold text-success">
          <Check size={16} strokeWidth={2.4} />
          Listo, tu perfil quedó actualizado.
        </p>
      )}

      <BotonPrincipal
        disabled={guardar.isPending}
        onClick={() => guardar.mutate()}
        className="mt-5"
      >
        {guardar.isPending ? "Guardando…" : "Guardar cambios"}
      </BotonPrincipal>
    </main>
  );
}
