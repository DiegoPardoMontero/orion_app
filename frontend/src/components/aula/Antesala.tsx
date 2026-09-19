"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Check, Clock, Lock, ShieldCheck } from "lucide-react";
import { Avatar } from "@/components/Avatar";
import { Rigel } from "@/components/Rigel";
import { Boton } from "@/components/ui";
import { VistaPrevia } from "@/components/aula/VistaPrevia";
import type { ClassroomResponse } from "@/lib/api/aula";

/**
 * La antesala: lo que se ve antes de entrar a clase.
 *
 * <p>Es la pantalla con más peso emocional de Orión. Quien la mira está a punto de hablar un idioma
 * que no domina con un desconocido, y llega temprano justo porque está nervioso. Todo aquí está
 * para bajar el pulso: con quién vas a hablar y su cara, cuánto falta exactamente, y la prueba de
 * micrófono para fallar en privado.
 *
 * <p>Cuatro estados de reloj, y cada uno dice qué hacer. El estado nunca va solo por color: chip con
 * ícono, texto y frase. El botón deshabilitado explica por qué lo está, que es la diferencia entre
 * una puerta cerrada y una puerta cerrada con un cartel.
 */
export function Antesala({
  datos,
  onEntrar,
  micOn,
  camOn,
  onMic,
  onCam,
}: {
  datos: ClassroomResponse;
  onEntrar: () => void;
  micOn: boolean;
  camOn: boolean;
  onMic: (v: boolean) => void;
  onCam: (v: boolean) => void;
}) {
  const ahora = useReloj();
  const otro = datos.counterpart;
  const nombre = otro?.firstName || "tu clase";
  const esAnfitrion = datos.moderator;

  const inicio = new Date(datos.startsAt).getTime();
  const faltan = Math.max(0, Math.ceil((inicio - ahora) / 60000));
  const llevan = Math.max(0, Math.floor((ahora - inicio) / 60000));

  const hora = (iso: string) =>
    new Date(iso).toLocaleTimeString("es-CO", {
      hour: "2-digit",
      minute: "2-digit",
      timeZone: "America/Bogota",
    });

  const chip = (() => {
    switch (datos.state) {
      case "CLOSED":
        return {
          icono: <Clock size={15} strokeWidth={2.2} />,
          tono: "bg-surface-sunken text-text-secondary",
          punto: null,
          titulo: `La sala abre a las ${hora(datos.opensAt)}`,
          cifra: `Faltan ${faltan} min`,
        };
      case "OPEN":
        return {
          icono: null,
          tono: "bg-success-bg text-success",
          punto: true,
          titulo: "Sala abierta",
          cifra: `Empieza en ${faltan} min`,
        };
      case "STARTED":
        return datos.counterpartPresent
          ? {
              icono: <Check size={15} strokeWidth={2.4} />,
              tono: "bg-success-bg text-success",
              punto: null,
              titulo: `${nombre} te espera`,
              cifra: `Empezó hace ${llevan} min`,
            }
          : {
              icono: <Clock size={15} strokeWidth={2.2} />,
              tono: "bg-surface-sunken text-text-secondary",
              punto: null,
              titulo: "Aún no ha entrado",
              cifra: `Empezó hace ${llevan} min`,
            };
      default:
        return {
          icono: <Lock size={15} strokeWidth={2.2} />,
          tono: "bg-surface-sunken text-text-muted",
          punto: null,
          titulo: `Esta clase terminó a las ${hora(datos.endsAt)}`,
          cifra: null,
        };
    }
  })();

  const verbo = esAnfitrion
    ? datos.state === "STARTED"
      ? `Entrar con ${nombre}`
      : "Abrir la sala"
    : datos.state === "STARTED"
      ? "Entrar ahora"
      : "Entrar a la sala";

  const puedeEntrar = datos.state === "OPEN" || datos.state === "STARTED";
  const terminada = datos.state === "ENDED";

  return (
    <main
      className="mx-auto w-full px-5 py-6 lg:py-10"
      style={{ maxWidth: "var(--lobby-max-w)" }}
    >
      <div className="lg:grid lg:grid-cols-[1fr_minmax(0,460px)] lg:items-start lg:gap-10">
        <div>
          <p className="text-[12px] font-bold uppercase tracking-[0.1em] text-primary-strong">
            Tu clase
            {esAnfitrion && (
              <span className="ml-2 rounded-pill bg-host-bg px-2 py-0.5 text-[11px] normal-case tracking-normal text-host">
                <ShieldCheck size={11} strokeWidth={2.4} className="mr-1 inline align-[-1px]" />
                Anfitrión
              </span>
            )}
          </p>

          <div className="mt-3 flex items-center gap-3.5">
            <Avatar nombre={otro?.name ?? ""} fotoUrl={otro?.photoUrl} size="lg" />
            <div className="min-w-0">
              <h1 className="font-display text-h2 font-bold">
                {esAnfitrion ? `Clase con ${nombre}` : `Clase con ${nombre}`}
              </h1>
              {otro?.headline && (
                <p className="truncate text-[13px] text-text-secondary">{otro.headline}</p>
              )}
              <p className="mt-0.5 text-[13px] text-text-muted">
                {hora(datos.startsAt)} – {hora(datos.endsAt)} · {datos.classMinutes} minutos
              </p>
            </div>
          </div>

          {/* El estado, con ícono y texto: nunca solo color. */}
          <div
            aria-live="polite"
            className={`mt-5 inline-flex items-center gap-2.5 rounded-pill px-3.5 py-2 text-[13px] font-semibold ${chip.tono}`}
          >
            {chip.punto ? (
              <span
                className="h-2 w-2 rounded-full bg-presence"
                style={{ animation: "breathe var(--duration-breathe, 2400ms) ease-in-out infinite" }}
                aria-hidden="true"
              />
            ) : (
              chip.icono
            )}
            <span>{chip.titulo}</span>
            {chip.cifra && (
              <span className="font-display text-[14px] font-bold">{chip.cifra}</span>
            )}
          </div>

          {esAnfitrion && !terminada && (
            <p className="mt-2.5 text-[12.5px] text-text-secondary">
              Como anfitrión puedes silenciar, sacar a alguien y cerrar la sala.
            </p>
          )}

          {!esAnfitrion && !terminada && (datos.state === "CLOSED" || datos.state === "OPEN") && (
            <div className="mt-6 flex items-start gap-3 rounded-card bg-accent-peach-soft p-4">
              <Rigel pose="espera" decorativo className="h-16 w-auto shrink-0 animate-[bob_3.2s_ease-in-out_infinite]" />
              <p className="text-[13px] leading-relaxed text-[#8a5a33]">
                Respira. {nombre} sabe que estás aprendiendo, y hablar con miedo también es hablar.
              </p>
            </div>
          )}
        </div>

        <div className="mt-7 lg:mt-0">
          {!terminada && (
            <VistaPrevia micOn={micOn} camOn={camOn} onMic={onMic} onCam={onCam} />
          )}

          <div className="mt-5 flex flex-col gap-2.5">
            {terminada ? (
              <>
                {otro && (
                  <Link
                    href="/mensajes"
                    className="inline-flex h-11 items-center justify-center rounded-pill bg-primary px-5 text-[14px] font-bold text-on-primary shadow-primary hover:bg-primary-strong focus-visible:shadow-focus"
                  >
                    Escribirle a {nombre}
                  </Link>
                )}
                <Link
                  href="/mis-clases"
                  className="inline-flex h-11 items-center justify-center rounded-pill border-[1.5px] border-border px-5 text-[14px] font-bold text-text hover:bg-surface-sunken focus-visible:shadow-focus"
                >
                  Ver mis clases
                </Link>
              </>
            ) : (
              <>
                <Boton
                  variante="primario"
                  onClick={onEntrar}
                  disabled={!puedeEntrar}
                  aria-disabled={!puedeEntrar}
                  className={datos.state === "OPEN" ? "animate-[glow_3s_ease-in-out_infinite]" : ""}
                >
                  {verbo}
                </Boton>
                {!puedeEntrar && (
                  <p className="text-center text-[12.5px] text-text-muted">
                    La sala abre 10 minutos antes de la hora.
                  </p>
                )}
                {!esAnfitrion && puedeEntrar && (
                  <p className="text-center text-[12.5px] text-text-muted">
                    No pasa nada. Entra cuando estés listo.
                  </p>
                )}
              </>
            )}
          </div>
        </div>
      </div>
    </main>
  );
}

/** Un tic por segundo para la cuenta atrás. El anuncio a lectores va en el chip, al cambiar estado. */
function useReloj() {
  const [ahora, setAhora] = useState(() => Date.now());
  useEffect(() => {
    const id = setInterval(() => setAhora(Date.now()), 1000);
    return () => clearInterval(id);
  }, []);
  return ahora;
}
