"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Cargando, ErrorCarga } from "@/components/estados";
import { ApiError, apiFetch } from "@/lib/api/fetch";
import type { ProfileResponse } from "@/lib/api/types";

export default function PerfilPage() {
  const perfil = useQuery({
    queryKey: ["me", "profile"],
    queryFn: () => apiFetch<ProfileResponse>("/api/v1/me/profile"),
  });

  if (perfil.isPending) {
    return (
      <main className="mx-auto max-w-md p-4">
        <Cargando filas={3} />
      </main>
    );
  }

  if (perfil.isError) {
    return (
      <main className="mx-auto max-w-md p-4">
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
  const [fotoUrl, setFotoUrl] = useState(inicial.photoUrl ?? "");
  const [publicado, setPublicado] = useState(inicial.isPublished ?? false);
  const [guardado, setGuardado] = useState(false);

  const guardar = useMutation({
    mutationFn: () =>
      apiFetch<ProfileResponse>("/api/v1/me/profile", {
        method: "PUT",
        body: {
          headline: headline.trim() || undefined,
          bio: bio.trim() || undefined,
          photoUrl: fotoUrl.trim() || undefined,
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
    <main className="mx-auto max-w-md p-4">
      <h1 className="text-xl font-semibold">Mi perfil</h1>
      <p className="mt-0.5 text-xs text-ink-muted">Esto es lo que ven los estudiantes.</p>

      <label className="mt-4 block text-xs font-semibold text-ink-soft" htmlFor="headline">
        Titular
      </label>
      <input
        id="headline"
        type="text"
        maxLength={120}
        value={headline}
        onChange={(event) => setHeadline(event.target.value)}
        placeholder="Conversación y confianza · A1–B1"
        className="mt-1 w-full rounded-orion border border-line bg-card px-3 py-2 text-sm outline-none focus:border-accent"
      />

      <label className="mt-3 block text-xs font-semibold text-ink-soft" htmlFor="bio">
        Sobre ti
      </label>
      <textarea
        id="bio"
        rows={4}
        value={bio}
        onChange={(event) => setBio(event.target.value)}
        placeholder="Cuéntales cómo son tus clases."
        className="mt-1 w-full rounded-orion border border-line bg-card px-3 py-2 text-sm outline-none focus:border-accent"
      />

      <label className="mt-3 block text-xs font-semibold text-ink-soft" htmlFor="foto">
        URL de tu foto
      </label>
      <input
        id="foto"
        type="url"
        maxLength={500}
        value={fotoUrl}
        onChange={(event) => setFotoUrl(event.target.value)}
        placeholder="https://…"
        className="mt-1 w-full rounded-orion border border-line bg-card px-3 py-2 text-sm outline-none focus:border-accent"
      />

      <div className="mt-4 flex items-center justify-between border-t border-line pt-3">
        <div>
          <p className="text-[13px] font-semibold">Perfil visible</p>
          <p className="text-[11px] text-ink-muted">Los estudiantes pueden verte y reservar</p>
        </div>
        <input
          type="checkbox"
          role="switch"
          aria-label="Perfil visible"
          checked={publicado}
          onChange={(event) => setPublicado(event.target.checked)}
          className="h-5 w-5 accent-[var(--color-accent)]"
        />
      </div>

      {!publicado && (
        <p className="mt-2 rounded-orion bg-warning-soft px-3 py-2 text-xs text-warning">
          Los estudiantes dejarán de verte y no podrán reservar contigo. Tus clases ya agendadas
          siguen en pie.
        </p>
      )}

      {error && (
        <p className="mt-3 rounded-orion bg-danger-soft px-3 py-2 text-sm text-danger">{error}</p>
      )}

      {guardado && (
        <p className="mt-3 rounded-orion bg-success-soft px-3 py-2 text-sm text-success">
          Listo, tu perfil quedó actualizado.
        </p>
      )}

      <button
        type="button"
        disabled={guardar.isPending}
        onClick={() => guardar.mutate()}
        className="mt-4 w-full rounded-orion bg-accent py-2.5 text-sm font-semibold text-white disabled:opacity-60"
      >
        {guardar.isPending ? "Guardando…" : "Guardar cambios"}
      </button>
    </main>
  );
}
