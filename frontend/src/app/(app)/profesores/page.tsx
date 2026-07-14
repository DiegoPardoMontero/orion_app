"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import { Avatar } from "@/components/Avatar";
import { Cargando, ErrorCarga, Vacio } from "@/components/estados";
import { apiFetch } from "@/lib/api/fetch";
import type { ProfessorSummary } from "@/lib/api/types";

export default function ProfesoresPage() {
  const [busqueda, setBusqueda] = useState("");

  const { data, isPending, isError, refetch } = useQuery({
    queryKey: ["professors"],
    queryFn: () => apiFetch<ProfessorSummary[]>("/api/v1/professors"),
  });

  // El filtro es en cliente: el API no tiene búsqueda y a esta escala tampoco la necesita.
  const profesores = (data ?? []).filter((profesor) =>
    (profesor.fullName ?? "").toLowerCase().includes(busqueda.trim().toLowerCase()),
  );

  return (
    <main className="mx-auto max-w-md p-4">
      <h1 className="text-xl font-semibold">Profesores</h1>

      <input
        type="text"
        value={busqueda}
        onChange={(event) => setBusqueda(event.target.value)}
        placeholder="Buscar profesor"
        className="mt-3.5 w-full rounded-orion border border-line bg-card px-3 py-2 text-sm outline-none focus:border-accent"
      />

      <div className="mt-3.5">
        {isPending && <Cargando />}

        {isError && (
          <ErrorCarga
            mensaje="No pudimos cargar los profesores."
            onReintentar={() => void refetch()}
          />
        )}

        {data && profesores.length === 0 && (
          <Vacio
            titulo={busqueda ? "Sin resultados" : "Aún no hay profesores"}
            texto={
              busqueda
                ? "Prueba con otro nombre."
                : "Muy pronto vas a poder agendar tu primera clase."
            }
          />
        )}

        <ul className="space-y-2.5">
          {profesores.map((profesor) => (
            <li key={profesor.id} className="rounded-card border border-line bg-card p-3">
              <div className="flex items-center gap-2.5">
                <Avatar nombre={profesor.fullName ?? ""} fotoUrl={profesor.photoUrl} />
                <div className="min-w-0">
                  <p className="truncate text-sm font-semibold">{profesor.fullName}</p>
                  <p className="truncate text-xs text-ink-soft">{profesor.headline}</p>
                </div>
              </div>

              <div className="mt-2.5 flex justify-end">
                <Link
                  href={`/profesores/${profesor.id}`}
                  className="rounded-orion border border-line px-3 py-1.5 text-xs text-ink"
                >
                  Ver agenda
                </Link>
              </div>
            </li>
          ))}
        </ul>
      </div>
    </main>
  );
}
