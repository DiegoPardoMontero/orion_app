"use client";

import { useState } from "react";
import { Modal } from "@/components/Modal";
import { Boton } from "@/components/ui";
import type { Me } from "@/lib/auth/session";
import { useBienvenida, useCompletarPaso } from "@/lib/bienvenida";
import {
  cerrarRecorridoPedido,
  RECORRIDO_ESTUDIANTE,
  RECORRIDO_PROFESOR,
  useRecorridoPedido,
} from "@/lib/recorrido";
import { Recorrido } from "./Recorrido";
import { Reproductor } from "./Reproductor";

const APLAZADA_EN_ESTA_SESION = "orion.bienvenida.aplazada";

function aplazadaEnEstaSesion(): boolean {
  try {
    return sessionStorage.getItem(APLAZADA_EN_ESTA_SESION) === "1";
  } catch {
    return false;
  }
}

/**
 * La bienvenida al entrar: el video de Sofía para el profesor recién aprobado y, después, el
 * recorrido guiado; al estudiante, el recorrido directamente. Cada cosa una vez —el servidor
 * recuerda qué vio cada quien— y las dos se vuelven a abrir desde Ayuda.
 *
 * <p>«Lo veo después» no marca nada: lo aplaza por esta sesión, para que quien entró con prisa lo
 * encuentre la próxima vez en vez de perderlo para siempre. Y sin video configurado, el profesor
 * va directo al recorrido.
 */
export function Bienvenida({ me }: { me: Me }) {
  const conBienvenida = me.role === "PROFESSOR" || me.role === "STUDENT";
  const bienvenida = useBienvenida(conBienvenida);
  const completar = useCompletarPaso();
  const pedido = useRecorridoPedido();
  const [aplazada, setAplazada] = useState(aplazadaEnEstaSesion);
  const nombre = me.fullName.trim().split(/\s+/)[0];

  if (!conBienvenida) return null;

  // Abierto desde Ayuda: se muestra aunque ya se haya visto.
  if (pedido) {
    const definicion = pedido === "TOUR_PROFESSOR" ? RECORRIDO_PROFESOR : RECORRIDO_ESTUDIANTE;
    return (
      <Recorrido
        definicion={definicion}
        nombre={nombre}
        onTerminar={() => {
          cerrarRecorridoPedido();
          completar.mutate(definicion.paso);
        }}
      />
    );
  }

  const b = bienvenida.data;
  if (!b || aplazada) return null;

  const aplazar = () => {
    try {
      sessionStorage.setItem(APLAZADA_EN_ESTA_SESION, "1");
    } catch {
      // Sin almacenamiento, se cierra igual; volverá a aparecer al recargar.
    }
    setAplazada(true);
  };

  if (b.welcomeVideo && !b.welcomeVideo.seen) {
    return (
      <Modal titulo={`Te damos la bienvenida, ${nombre}`} onCerrar={aplazar} amplio>
        <p className="text-[14px] leading-relaxed text-text-secondary">
          Sofía, directora académica de Orión, te cuenta en dos minutos cómo funciona todo y qué esperar de tus primeras
          clases.
        </p>
        <div className="mt-4">
          <Reproductor url={b.welcomeVideo.url} titulo="Video de bienvenida de Orión" />
        </div>
        <div className="mt-5 flex flex-wrap items-center justify-between gap-3">
          <p className="text-[12.5px] text-text-muted">Lo puedes volver a ver cuando quieras desde Ayuda.</p>
          <div className="flex flex-wrap gap-2">
            <Boton variante="fantasma" onClick={aplazar}>
              Lo veo después
            </Boton>
            <Boton onClick={() => completar.mutate("WELCOME_VIDEO")}>
              {b.pendingTour ? "Empezar el recorrido" : "Empezar"}
            </Boton>
          </div>
        </div>
      </Modal>
    );
  }

  if (b.pendingTour) {
    const definicion = b.pendingTour === "TOUR_PROFESSOR" ? RECORRIDO_PROFESOR : RECORRIDO_ESTUDIANTE;
    return <Recorrido definicion={definicion} nombre={nombre} onTerminar={() => completar.mutate(definicion.paso)} />;
  }

  return null;
}
