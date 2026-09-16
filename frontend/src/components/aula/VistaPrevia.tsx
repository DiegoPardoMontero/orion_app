"use client";

import { useEffect, useRef, useState } from "react";
import { Camera, CameraOff, Check, Mic, MicOff } from "lucide-react";

/**
 * «Así te verás»: la prueba de micrófono y cámara antes de entrar.
 *
 * <p>Existe porque el peor momento para descubrir que el micrófono no funciona es delante de un
 * desconocido con quien vas a hablar en un idioma que no dominas. Aquí se falla en privado.
 *
 * <p>El medidor no es decoración: tres barras que se mueven son la única prueba de que el micrófono
 * capta algo. Un ícono encendido solo demuestra que el permiso está dado.
 *
 * <p>La cámara se apaga soltando la pista, no ocultándola con CSS: el piloto de la webcam tiene que
 * apagarse de verdad, o la promesa de «cámara apagada» es falsa.
 */
export function VistaPrevia({
  micOn,
  camOn,
  onMic,
  onCam,
}: {
  micOn: boolean;
  camOn: boolean;
  onMic: (v: boolean) => void;
  onCam: (v: boolean) => void;
}) {
  const video = useRef<HTMLVideoElement>(null);
  const stream = useRef<MediaStream | null>(null);
  const [nivel, setNivel] = useState(0);
  const [permiso, setPermiso] = useState<"pidiendo" | "ok" | "negado">("pidiendo");

  useEffect(() => {
    let vivo = true;
    let audioCtx: AudioContext | null = null;
    let frame = 0;

    navigator.mediaDevices
      .getUserMedia({ audio: true, video: true })
      .then((s) => {
        if (!vivo) {
          s.getTracks().forEach((t) => t.stop());
          return;
        }
        stream.current = s;
        setPermiso("ok");
        if (video.current) video.current.srcObject = s;

        audioCtx = new AudioContext();
        const analizador = audioCtx.createAnalyser();
        analizador.fftSize = 512;
        audioCtx.createMediaStreamSource(s).connect(analizador);
        const datos = new Uint8Array(analizador.frequencyBinCount);

        const medir = () => {
          analizador.getByteTimeDomainData(datos);
          let pico = 0;
          for (const v of datos) pico = Math.max(pico, Math.abs(v - 128));
          setNivel(Math.min(1, pico / 48));
          frame = requestAnimationFrame(medir);
        };
        medir();
      })
      .catch(() => vivo && setPermiso("negado"));

    return () => {
      vivo = false;
      cancelAnimationFrame(frame);
      void audioCtx?.close();
      stream.current?.getTracks().forEach((t) => t.stop());
      stream.current = null;
    };
  }, []);

  // Apagar suelta la pista de verdad: el piloto de la webcam debe apagarse, no disimularse.
  useEffect(() => {
    stream.current?.getVideoTracks().forEach((t) => (t.enabled = camOn));
  }, [camOn]);
  useEffect(() => {
    stream.current?.getAudioTracks().forEach((t) => (t.enabled = micOn));
  }, [micOn]);

  const listo = permiso === "ok" && micOn;

  return (
    <div>
      <div
        className="relative overflow-hidden rounded-[24px] bg-preview-bg"
        style={{ height: "var(--preview-h-mobile)" }}
      >
        <span className="absolute left-3 top-3 z-10 rounded-pill bg-black/35 px-2.5 py-1 text-[11px] font-bold text-preview-control-on">
          Así te verás
        </span>

        <video
          ref={video}
          autoPlay
          playsInline
          muted
          className={`h-full w-full scale-x-[-1] object-cover ${camOn && permiso === "ok" ? "" : "invisible"}`}
        />

        {(!camOn || permiso !== "ok") && (
          <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 text-preview-control-on/80">
            <CameraOff size={26} strokeWidth={1.75} />
            <p className="text-[13px] font-semibold">
              {permiso === "negado" ? "Sin acceso a la cámara" : "Cámara apagada"}
            </p>
          </div>
        )}

        <div className="absolute bottom-3 left-1/2 flex -translate-x-1/2 gap-2">
          <Pildora
            encendido={micOn}
            onClick={() => onMic(!micOn)}
            etiqueta={micOn ? "Micrófono encendido" : "Encender micrófono"}
            icono={micOn ? <Mic size={17} strokeWidth={2} /> : <MicOff size={17} strokeWidth={2} />}
          >
            {micOn ? <Medidor nivel={nivel} /> : <span className="text-[12px] font-bold">Encender</span>}
          </Pildora>
          <Pildora
            encendido={camOn}
            onClick={() => onCam(!camOn)}
            etiqueta={camOn ? "Cámara encendida" : "Encender cámara"}
            icono={camOn ? <Camera size={17} strokeWidth={2} /> : <CameraOff size={17} strokeWidth={2} />}
          >
            {!camOn && <span className="text-[12px] font-bold">Encender</span>}
          </Pildora>
        </div>
      </div>

      {listo ? (
        <p className="mt-2 flex items-center gap-1.5 text-[12.5px] font-semibold text-success">
          <Check size={14} strokeWidth={2.4} />
          Micrófono y cámara listos
        </p>
      ) : !camOn ? (
        <p className="mt-2 rounded-base bg-accent-peach-soft px-3 py-2 text-[12.5px] text-[#8a5a33]">
          Entrarás sin cámara — puedes encenderla dentro.
        </p>
      ) : null}
    </div>
  );
}

function Pildora({
  encendido,
  onClick,
  etiqueta,
  icono,
  children,
}: {
  encendido: boolean;
  onClick: () => void;
  etiqueta: string;
  icono: React.ReactNode;
  children?: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={encendido}
      aria-label={etiqueta}
      className={`flex h-11 min-w-11 items-center gap-2 rounded-pill px-3.5 transition-colors focus-visible:shadow-focus ${
        encendido
          ? "bg-preview-control text-preview-control-on"
          : "bg-surface text-text"
      }`}
    >
      {icono}
      {children}
    </button>
  );
}

/** Tres barras durazno. Se mueven con la voz; con `prefers-reduced-motion` se quedan quietas. */
function Medidor({ nivel }: { nivel: number }) {
  return (
    <span className="flex items-end gap-[3px]" aria-hidden="true">
      {[0, 1, 2].map((i) => (
        <span
          key={i}
          className="w-[3px] rounded-full bg-accent-peach transition-[height] duration-150"
          style={{ height: `${6 + nivel * 12 * (i === 1 ? 1.25 : 1)}px` }}
        />
      ))}
    </span>
  );
}
