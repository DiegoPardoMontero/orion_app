"use client";

import { Check } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import type { Me } from "@/lib/auth/session";
import { useMediaQuery } from "@/lib/useMediaQuery";
import { useBienvenida, useCompletarPaso } from "@/lib/bienvenida";
import {
  CLAVE_APLAZADO,
  cerrarRecorridoPedido,
  marca,
  pasoGuardado,
  poner,
  recorridoDe,
  useRecorridoPedido,
} from "@/lib/recorrido";
import { Recorrido } from "./Recorrido";
import { VideoBienvenida } from "./VideoBienvenida";

/**
 * La bienvenida al entrar (handoff §6 y §9). Al profesor recién aprobado, la pantalla completa con
 * el video de Sofía; después —o directo, si no hay video—, el recorrido. Al estudiante, el recorrido.
 *
 * <ul>
 *   <li><strong>Empezar el recorrido</strong>: marca la bienvenida como vista y abre el inicio del recorrido.
 *   <li><strong>Lo veo después</strong>: la marca como vista y lleva a la agenda; el recorrido espera
 *       ahí, en un aviso de Rigel que se ofrece una sola vez.
 *   <li>Cerrar la pestaña sin tocar nada: la bienvenida vuelve en la próxima entrada.
 *   <li>Si se cerró a mitad del recorrido, al volver Rigel pregunta «¿Seguimos donde íbamos?».
 * </ul>
 */
export function Bienvenida({ me }: { me: Me }) {
  const conBienvenida = me.role === "PROFESSOR" || me.role === "STUDENT";
  const bienvenida = useBienvenida(conBienvenida);
  const completar = useCompletarPaso();
  const pedido = useRecorridoPedido();
  const router = useRouter();
  const [aplazado, setAplazado] = useState(() => marca(CLAVE_APLAZADO));
  const nombre = me.fullName.trim().split(/\s+/)[0];

  if (!conBienvenida) return null;

  // Abierto desde Ayuda o desde el aviso de la agenda: se muestra aunque ya se haya visto.
  if (pedido) {
    return (
      <Recorrido
        definicion={recorridoDe(pedido.id)}
        nombre={nombre}
        arranque={pedido.desde}
        onTerminar={() => {
          cerrarRecorridoPedido();
          completar.mutate(pedido.id);
        }}
      />
    );
  }

  const b = bienvenida.data;
  if (!b) return null;

  if (b.welcomeVideo && !b.welcomeVideo.seen) {
    return (
      <PantallaDeBienvenida
        nombre={nombre}
        url={b.welcomeVideo.url}
        onEmpezar={() => completar.mutate("WELCOME_VIDEO")}
        onDespues={() => {
          poner(CLAVE_APLAZADO, true);
          setAplazado(true);
          completar.mutate("WELCOME_VIDEO");
          router.push("/mis-clases");
        }}
      />
    );
  }

  if (b.pendingTour && !aplazado) {
    const guardado = pasoGuardado(b.pendingTour);
    return (
      <Recorrido
        definicion={recorridoDe(b.pendingTour)}
        nombre={nombre}
        arranque={guardado ? { reanudar: guardado } : "inicio"}
        onTerminar={() => completar.mutate(b.pendingTour!)}
      />
    );
  }

  return null;
}

/** La bienvenida del profesor aprobado: pantalla completa, sin navegación (capturas 01 y 02). */
function PantallaDeBienvenida({
  nombre,
  url,
  onEmpezar,
  onDespues,
}: {
  nombre: string;
  url: string;
  onEmpezar: () => void;
  onDespues: () => void;
}) {
  const grande = useMediaQuery("(min-width: 1024px)");
  // Sin navegación detrás: la página no se desplaza bajo la bienvenida.
  useEffect(() => {
    const antes = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = antes;
    };
  }, []);

  if (typeof document === "undefined") return null;

  const chip = (
    <span className="inline-flex items-center gap-1.5 self-start rounded-pill bg-[#DEF3E7] px-3 py-1.5 text-[12px] font-bold text-[#2E6B4A] lg:text-[13px]">
      <Check size={14} strokeWidth={2.2} aria-hidden />
      Tu postulación fue aprobada
    </span>
  );
  const titulo = (
    <h1 className="font-display text-[30px] font-bold leading-[1.1] text-[#33203B] [text-wrap:pretty] lg:text-[40px] lg:leading-[1.08]">
      {nombre}, te damos la bienvenida a Orión.
    </h1>
  );
  const textos = (
    <div className="flex flex-col gap-2 lg:gap-2.5">
      <p className="m-0 text-[16px] leading-[1.55] text-[#33203B] [text-wrap:pretty] lg:text-[17px]">
        Sofía, nuestra directora académica, te cuenta en 2 minutos cómo trabajamos y qué esperan tus estudiantes.
      </p>
      <p className="m-0 text-[15px] leading-[1.55] text-[#5E4E6B] lg:text-[16px]">Después te mostramos la plataforma, paso a paso.</p>
    </div>
  );
  const empezar = (
    <button
      type="button"
      onClick={onEmpezar}
      className="h-[52px] rounded-pill bg-[#E8503A] px-7 text-[15px] font-bold text-[#FFF6EE] shadow-[0_10px_24px_rgba(232,80,58,.35)] transition-colors hover:bg-[#C0341F] focus-visible:shadow-[0_0_0_4px_rgba(232,80,58,.22)] focus-visible:outline-none"
    >
      Empezar el recorrido
    </button>
  );
  const despues = (
    <button
      type="button"
      onClick={onDespues}
      className="h-12 rounded-pill px-5 text-[15px] font-semibold text-[#5E4E6B] transition-colors hover:bg-[#F4EAE0] hover:text-[#33203B] focus-visible:shadow-[0_0_0_4px_rgba(232,80,58,.22)] focus-visible:outline-none lg:h-[52px]"
    >
      Lo veo después
    </button>
  );

  return createPortal(
    <div role="dialog" aria-modal="true" aria-label="Bienvenida a Orión" className="fixed inset-0 z-[85] flex flex-col overflow-y-auto bg-[#FFF6EE]">
      <header className="flex h-16 shrink-0 items-center px-5 lg:h-[76px] lg:px-12">
        <span className="font-display text-[16px] font-extrabold text-[#E8503A] lg:text-[18px]">ORIÓN ✦</span>
      </header>

      {/* Móvil: columna (título, video, texto); escritorio: el video de 760 a la izquierda y el resto a la derecha. */}
      <div className="flex flex-1 flex-col gap-[22px] px-5 pt-2 lg:grid lg:grid-cols-[minmax(0,760px)_1fr] lg:content-center lg:gap-x-14 lg:gap-y-6 lg:px-[72px] lg:pb-12 lg:pt-3">
        <div className="flex flex-col gap-2.5 lg:col-start-2 lg:row-start-1 lg:gap-6">
          {chip}
          {titulo}
        </div>
        <div className="lg:col-start-1 lg:row-span-4 lg:row-start-1 lg:self-center">
          <VideoBienvenida key={grande ? "grande" : "movil"} url={url} grande={grande} />
        </div>
        <div className="lg:col-start-2 lg:row-start-2">{textos}</div>
        <div className="hidden flex-wrap items-center gap-2.5 lg:col-start-2 lg:row-start-3 lg:flex">
          {empezar}
          {despues}
        </div>
        <span className="hidden text-[13px] text-[#7A6B85] lg:col-start-2 lg:row-start-4 lg:block">
          Lo encuentras cuando quieras en Ayuda.
        </span>
      </div>

      <div className="flex flex-col gap-2 px-5 pb-7 pt-4 lg:hidden">
        {empezar}
        {despues}
        <span className="text-center text-[12px] text-[#7A6B85]">Lo encuentras cuando quieras en Ayuda.</span>
      </div>
    </div>,
    document.body,
  );
}
