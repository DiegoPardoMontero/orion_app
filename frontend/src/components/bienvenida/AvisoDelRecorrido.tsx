"use client";

import { X } from "lucide-react";
import { useEffect, useState, useSyncExternalStore } from "react";
import { Rigel } from "@/components/Rigel";
import { useBienvenida, useCompletarPaso } from "@/lib/bienvenida";
import { abrirRecorrido, CLAVE_APLAZADO, CLAVE_AVISO_MOSTRADO, marca, poner } from "@/lib/recorrido";

/**
 * El recorrido que quedó para después (handoff §6): quien dijo «Lo veo después» en la bienvenida lo
 * encuentra aquí, en la agenda, ofrecido por Rigel <strong>una sola vez</strong>. Desde que se
 * muestra, el recorrido deja de abrirse solo y queda en Ayuda.
 */
export function AvisoDelRecorrido() {
  const bienvenida = useBienvenida();
  const completar = useCompletarPaso();
  const toca = useSyncExternalStore(
    suscribir,
    () => marca(CLAVE_APLAZADO) && !marca(CLAVE_AVISO_MOSTRADO),
    () => false,
  );
  const tour = bienvenida.data?.pendingTour ?? null;
  // Se congela al montar: marcarlo como mostrado no debe esconderlo mientras se está leyendo.
  const [visible, setVisible] = useState<null | "TOUR_PROFESSOR" | "TOUR_STUDENT">(null);
  const mostrar = toca && tour && visible === null ? tour : null;

  useEffect(() => {
    if (!mostrar) return;
    poner(CLAVE_AVISO_MOSTRADO, true);
    poner(CLAVE_APLAZADO, false);
    completar.mutate(mostrar);
    // eslint-disable-next-line react-hooks/set-state-in-effect -- congela el aviso una vez mostrado
    setVisible(mostrar);
  }, [mostrar, completar]);

  const id = visible ?? mostrar;
  if (!id) return null;

  return (
    <aside
      aria-label="El recorrido de Orión"
      className="anim-rise mt-4 flex items-center gap-3 rounded-card bg-rigel-soft py-2.5 pl-3 pr-2"
    >
      <Rigel pose="guia" decorativo className="h-auto w-11 shrink-0" />
      <p className="min-w-0 flex-1 text-[13.5px] leading-snug text-rigel-ink">
        <strong>¿Te muestro Orión ahora?</strong> Son {id === "TOUR_PROFESSOR" ? "8" : "6"} pasos. Si no, queda en
        Ayuda.
      </p>
      <button
        type="button"
        onClick={() => {
          setVisible(null);
          abrirRecorrido(id, "inicio");
        }}
        className="inline-flex min-h-11 shrink-0 items-center rounded-pill bg-primary px-4 text-[13px] font-bold text-on-primary shadow-primary transition-colors hover:bg-primary-strong focus-visible:shadow-focus"
      >
        Empezar el recorrido
      </button>
      <button
        type="button"
        onClick={() => setVisible(null)}
        aria-label="Ahora no"
        title="Ahora no"
        className="grid h-11 w-11 shrink-0 place-items-center rounded-full text-rigel-ink hover:bg-rigel/30 focus-visible:shadow-focus"
      >
        <X size={18} strokeWidth={2} />
      </button>
    </aside>
  );
}

function suscribir(): () => void {
  return () => {};
}
