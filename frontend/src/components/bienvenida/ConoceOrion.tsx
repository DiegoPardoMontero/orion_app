"use client";

import { Compass, PlayCircle } from "lucide-react";
import { useState } from "react";
import { Boton, Tarjeta } from "@/components/ui";
import { useBienvenida } from "@/lib/bienvenida";
import { abrirRecorrido } from "@/lib/recorrido";
import { Reproductor } from "./Reproductor";

/**
 * En Ayuda, la bienvenida para volver a verla: el recorrido de la plataforma y, si al profesor le
 * tocó, el video de Sofía. El video se abre aquí mismo y no en un diálogo: quien vino a Ayuda a
 * buscarlo ya decidió verlo.
 */
export function ConoceOrion({ rol }: { rol: "PROFESSOR" | "STUDENT" | null }) {
  const bienvenida = useBienvenida(rol !== null);
  const [viendo, setViendo] = useState(false);
  if (!rol) return null;
  const video = bienvenida.data?.welcomeVideo;

  return (
    <Tarjeta className="mt-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="min-w-0">
          <p className="text-[14px] font-bold">Conoce Orión</p>
          <p className="mt-0.5 text-[12.5px] text-text-muted">
            {video
              ? "El recorrido por la plataforma y el video de bienvenida de Sofía."
              : "Un recorrido corto por lo que hay en cada sección."}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Boton variante="contorno" onClick={() => abrirRecorrido(rol === "PROFESSOR" ? "TOUR_PROFESSOR" : "TOUR_STUDENT")}>
            <Compass size={16} strokeWidth={2} />
            Ver el recorrido
          </Boton>
          {video && !viendo && (
            <Boton variante="contorno" onClick={() => setViendo(true)}>
              <PlayCircle size={16} strokeWidth={2} />
              Ver el video
            </Boton>
          )}
        </div>
      </div>
      {video && viendo && (
        <div className="mt-4">
          <Reproductor url={video.url} titulo="Video de bienvenida de Orión" />
        </div>
      )}
    </Tarjeta>
  );
}
