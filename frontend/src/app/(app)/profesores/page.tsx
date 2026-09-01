"use client";

import { useQuery } from "@tanstack/react-query";
import { ChevronRight, Search } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { Avatar } from "@/components/Avatar";
import { Cargando, ErrorCarga, Vacio } from "@/components/estados";
import { Campo } from "@/components/ui";
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
    <main className="mx-auto w-full max-w-md px-5 py-6 lg:max-w-5xl lg:px-12 lg:py-8">
      <h1 className="font-display text-h1 font-bold">Profesores</h1>
      <p className="mt-1 text-[14px] text-text-secondary">
        Elige con quién quieres practicar y reserva tu clase.
      </p>

      <div className="mt-4 lg:max-w-md">
        <Campo
          type="text"
          value={busqueda}
          onChange={(event) => setBusqueda(event.target.value)}
          placeholder="Buscar profesor"
          icono={<Search size={18} strokeWidth={1.75} />}
        />
      </div>

      <div className="mt-5">
        {isPending && <Cargando />}

        {isError && (
          <ErrorCarga
            mensaje="No pudimos cargar los profesores. Revisa tu conexión e inténtalo otra vez."
            onReintentar={() => void refetch()}
          />
        )}

        {data && profesores.length === 0 && (
          <Vacio
            mascota
            titulo={busqueda ? "Sin resultados" : "Aún no hay profesores"}
            texto={
              busqueda
                ? "Prueba con otro nombre; quizá lo escribiste distinto."
                : "Muy pronto vas a poder agendar tu primera clase. Cada proceso es diferente; lo importante es empezar."
            }
          />
        )}

        <ul className="grid gap-3 lg:grid-cols-2 xl:grid-cols-3">
          {profesores.map((profesor, i) => (
            <li key={profesor.id} className="anim-rise" style={{ animationDelay: `${Math.min(i, 6) * 40}ms` }}>
              <div className="flex h-full flex-col justify-between rounded-card bg-surface-raised p-5 shadow-md transition-[transform,box-shadow] hover:-translate-y-0.5 hover:shadow-lg">
                <div className="flex items-center gap-3">
                  <Avatar nombre={profesor.fullName ?? ""} fotoUrl={profesor.photoUrl} />
                  <div className="min-w-0">
                    <p className="truncate font-display text-[17px] font-bold">{profesor.fullName}</p>
                    <p className="truncate text-[13px] text-text-secondary">{profesor.headline}</p>
                  </div>
                </div>

                <div className="mt-4 flex justify-end">
                  <Link
                    href={`/profesores/${profesor.id}`}
                    className="inline-flex min-h-11 items-center gap-1 rounded-pill bg-primary px-5 text-[14px] font-bold text-on-primary shadow-primary transition-colors hover:bg-primary-strong focus-visible:shadow-focus"
                  >
                    Ver agenda
                    <ChevronRight size={16} strokeWidth={1.75} />
                  </Link>
                </div>
              </div>
            </li>
          ))}
        </ul>
      </div>
    </main>
  );
}
