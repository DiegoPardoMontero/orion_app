"use client";

import { useQuery } from "@tanstack/react-query";
import { ChevronRight, Search, Users } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { Avatar } from "@/components/Avatar";
import { Cargando, ErrorCarga, Vacio } from "@/components/estados";
import { HeroNoche } from "@/components/marca";
import { Campo, Tarjeta } from "@/components/ui";
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
    <main>
      <HeroNoche className="rounded-b-sheet px-5 pb-5 pt-4">
        <h1 className="text-[20px] font-extrabold text-white">Profesores</h1>
        <div className="mt-3.5">
          <Campo
            type="text"
            value={busqueda}
            onChange={(event) => setBusqueda(event.target.value)}
            placeholder="Buscar profesor"
            icono={<Search size={18} strokeWidth={2.2} />}
            className="border-transparent"
          />
        </div>
      </HeroNoche>

      <div className="px-5 py-5">
        {isPending && <Cargando />}

        {isError && (
          <ErrorCarga
            mensaje="No pudimos cargar los profesores. Revisa tu conexión e inténtalo otra vez."
            onReintentar={() => void refetch()}
          />
        )}

        {data && profesores.length === 0 && (
          <Vacio
            icono={<Users size={24} strokeWidth={2.2} />}
            titulo={busqueda ? "Sin resultados" : "Aún no hay profesores"}
            texto={
              busqueda
                ? "Prueba con otro nombre."
                : "Muy pronto vas a poder agendar tu primera clase."
            }
          />
        )}

        <ul className="space-y-3">
          {profesores.map((profesor) => (
            <li key={profesor.id}>
              <Tarjeta>
                <div className="flex items-center gap-3">
                  <Avatar nombre={profesor.fullName ?? ""} fotoUrl={profesor.photoUrl} />
                  <div className="min-w-0">
                    <p className="truncate text-[15px] font-bold">{profesor.fullName}</p>
                    <p className="truncate text-[12.5px] text-text-secondary">
                      {profesor.headline}
                    </p>
                  </div>
                </div>

                <div className="mt-3.5 flex justify-end">
                  <Link
                    href={`/profesores/${profesor.id}`}
                    className="inline-flex items-center gap-1 rounded-base bg-primary px-4 py-2 text-[13px] font-bold text-white hover:bg-primary-strong"
                  >
                    Ver agenda
                    <ChevronRight size={16} strokeWidth={2.2} />
                  </Link>
                </div>
              </Tarjeta>
            </li>
          ))}
        </ul>
      </div>
    </main>
  );
}
