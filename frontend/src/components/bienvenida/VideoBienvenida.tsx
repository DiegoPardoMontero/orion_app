"use client";

import { Maximize, Pause, Play } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { reproductorDe } from "@/lib/video";

/**
 * `<VideoPlayer>` del handoff (§6): la portada noche con el play coral, «Sofía · Directora
 * académica» y la duración; al tocarlo, el video.
 *
 * <p>Con un archivo de video, los controles son los del diseño: barra de 56 sobre noche, progreso
 * durazno, tiempo, CC y pantalla completa, que se esconden a los 2,5 s; Espacio o K pausa, ← → mueven
 * 5 s, C subtítulos y F pantalla completa. Con YouTube, Vimeo o Drive el reproductor es el suyo —el
 * de ellos no se puede vestir—, pero la portada es la misma.
 */
export function VideoBienvenida({ url, grande = false, autoplay = false }: { url: string; grande?: boolean; autoplay?: boolean }) {
  const fuente = reproductorDe(url);
  const [reproduciendo, setReproduciendo] = useState(autoplay);
  const [duracion, setDuracion] = useState<number | null>(null);
  const [intento, setIntento] = useState(0);

  const marco = `relative aspect-video w-full max-w-full overflow-hidden rounded-[22px] bg-[#2E1E4E] shadow-[0_18px_44px_rgba(51,32,59,.18)] focus-within:shadow-[0_0_0_4px_rgba(232,80,58,.35)]`;

  if (!fuente) return <NoCargo marco={marco} onReintentar={() => setIntento((n) => n + 1)} />;

  if (!reproduciendo) {
    return (
      <div className={marco}>
        {/* La duración sale del propio archivo; de YouTube o Vimeo no se sabe sin cargarlos, y no se inventa. */}
        {fuente.tipo === "video" && (
          <video
            src={fuente.src}
            preload="metadata"
            className="hidden"
            onLoadedMetadata={(e) => setDuracion(e.currentTarget.duration)}
          />
        )}
        <button
          type="button"
          onClick={() => setReproduciendo(true)}
          aria-label="Reproducir el video de bienvenida de Sofía"
          className="group absolute inset-0 grid place-items-center focus-visible:outline-none"
        >
          <span
            className={`grid place-items-center rounded-full bg-[#E8503A] shadow-[0_10px_24px_rgba(232,80,58,.45)] transition-transform duration-150 group-hover:scale-[1.08] group-hover:bg-[#C0341F] group-focus-visible:shadow-[0_0_0_4px_#FFF6EE] ${
              grande ? "h-[84px] w-[84px]" : "h-16 w-16"
            }`}
          >
            <Play size={grande ? 32 : 26} strokeWidth={0} fill="#FFF6EE" className="translate-x-[2px]" />
          </span>
        </button>
        <div className={`pointer-events-none absolute flex flex-col gap-0.5 ${grande ? "bottom-[22px] left-6" : "bottom-3.5 left-4"}`}>
          <span className={`font-bold text-[#FFF6EE] ${grande ? "text-[16px]" : "text-[14px]"}`}>Sofía</span>
          <span className={`text-[#E9DFF7] ${grande ? "text-[14px]" : "text-[12px]"}`}>Directora académica</span>
        </div>
        {duracion !== null && Number.isFinite(duracion) && (
          <span
            className={`pointer-events-none absolute rounded-pill bg-[rgba(255,246,238,.16)] font-bold text-[#FFF6EE] tabular-nums ${
              grande ? "bottom-[22px] right-[22px] px-3 py-[5px] text-[13px]" : "bottom-3.5 right-3.5 px-2.5 py-1 text-[12px]"
            }`}
          >
            {reloj(duracion)}
          </span>
        )}
      </div>
    );
  }

  if (fuente.tipo === "iframe") {
    const src = fuente.src + (fuente.src.includes("?") ? "&" : "?") + "autoplay=1";
    return (
      <div className={marco}>
        <iframe
          src={src}
          title="Video de bienvenida de Sofía"
          className="absolute inset-0 h-full w-full"
          allow="autoplay; encrypted-media; picture-in-picture; fullscreen"
          allowFullScreen
          referrerPolicy="strict-origin-when-cross-origin"
        />
      </div>
    );
  }

  return <ArchivoConControles key={intento} src={fuente.src} marco={marco} onReintentar={() => setIntento((n) => n + 1)} />;
}

function ArchivoConControles({ src, marco, onReintentar }: { src: string; marco: string; onReintentar: () => void }) {
  const video = useRef<HTMLVideoElement>(null);
  const caja = useRef<HTMLDivElement>(null);
  const [pausado, setPausado] = useState(false);
  const [cargando, setCargando] = useState(true);
  const [fallo, setFallo] = useState(false);
  const [tiempo, setTiempo] = useState(0);
  const [total, setTotal] = useState(0);
  const [controles, setControles] = useState(true);
  const [subtitulos, setSubtitulos] = useState(true);
  const [conPistas, setConPistas] = useState(false);
  const ocultar = useRef<ReturnType<typeof setTimeout> | null>(null);

  const mostrarControles = useCallback(() => {
    setControles(true);
    if (ocultar.current) clearTimeout(ocultar.current);
    ocultar.current = setTimeout(() => setControles(false), 2500);
  }, []);

  useEffect(() => () => {
    if (ocultar.current) clearTimeout(ocultar.current);
  }, []);

  const alternar = useCallback(() => {
    const v = video.current;
    if (!v) return;
    if (v.paused) void v.play().catch(() => setPausado(true));
    else v.pause();
  }, []);

  const mover = (s: number) => {
    const v = video.current;
    if (v) v.currentTime = Math.min(Math.max(0, v.currentTime + s), v.duration || 0);
  };

  const pantallaCompleta = () => {
    const c = caja.current;
    if (!c) return;
    if (document.fullscreenElement) void document.exitFullscreen().catch(() => {});
    else void c.requestFullscreen?.().catch(() => {});
  };

  const cambiarSubtitulos = () => {
    const v = video.current;
    if (!v) return;
    const siguiente = !subtitulos;
    Array.from(v.textTracks).forEach((t) => (t.mode = siguiente ? "showing" : "hidden"));
    setSubtitulos(siguiente);
  };

  if (fallo) return <NoCargo marco={marco} onReintentar={onReintentar} />;

  return (
    <div
      ref={caja}
      className={marco}
      tabIndex={0}
      onMouseMove={mostrarControles}
      onKeyDown={(e) => {
        mostrarControles();
        if (e.key === " " || e.key.toLowerCase() === "k") {
          e.preventDefault();
          alternar();
        } else if (e.key === "ArrowRight") mover(5);
        else if (e.key === "ArrowLeft") mover(-5);
        else if (e.key.toLowerCase() === "c" && conPistas) cambiarSubtitulos();
        else if (e.key.toLowerCase() === "f") pantallaCompleta();
      }}
    >
      <video
        ref={video}
        src={src}
        autoPlay
        playsInline
        className="absolute inset-0 h-full w-full"
        onClick={alternar}
        onPlay={() => {
          setPausado(false);
          mostrarControles();
        }}
        onPause={() => {
          setPausado(true);
          setControles(true);
        }}
        onWaiting={() => setCargando(true)}
        onCanPlay={() => setCargando(false)}
        onError={() => setFallo(true)}
        onLoadedMetadata={(e) => {
          const v = e.currentTarget;
          setTotal(v.duration);
          // Subtítulos en español activos por defecto, si el archivo los trae.
          setConPistas(v.textTracks.length > 0);
          Array.from(v.textTracks).forEach((t) => (t.mode = "showing"));
        }}
        onTimeUpdate={(e) => setTiempo(e.currentTarget.currentTime)}
      />
      {cargando && (
        <span
          aria-label="Cargando"
          className="absolute left-1/2 top-1/2 h-9 w-9 -translate-x-1/2 -translate-y-1/2 animate-spin rounded-full border-[3px] border-[rgba(255,246,238,.25)] border-t-[#FFC189]"
        />
      )}
      <div
        className={`absolute inset-x-0 bottom-0 flex h-14 items-center gap-3.5 bg-[linear-gradient(180deg,rgba(46,30,78,0),rgba(46,30,78,.9))] px-4 transition-opacity duration-200 ${
          controles ? "opacity-100" : "pointer-events-none opacity-0"
        }`}
      >
        <button type="button" onClick={alternar} aria-label={pausado ? "Reproducir" : "Pausar"} className="grid h-11 w-11 place-items-center text-[#FFF6EE]">
          {pausado ? <Play size={20} strokeWidth={0} fill="currentColor" /> : <Pause size={20} strokeWidth={0} fill="currentColor" />}
        </button>
        <input
          type="range"
          min={0}
          max={total || 0}
          step={0.1}
          value={tiempo}
          aria-label="Avance del video"
          onChange={(e) => {
            if (video.current) video.current.currentTime = Number(e.target.value);
          }}
          className="h-6 flex-1 cursor-pointer accent-[#FFC189]"
        />
        <span className="text-[13px] font-semibold text-[#FFF6EE] tabular-nums">
          {reloj(tiempo)} / {reloj(total)}
        </span>
        {conPistas && (
          <button
            type="button"
            onClick={cambiarSubtitulos}
            aria-pressed={subtitulos}
            aria-label="Subtítulos"
            className={`flex h-8 items-center rounded-lg border-[1.5px] border-[#FFF6EE] px-2.5 text-[12px] font-extrabold text-[#FFF6EE] ${subtitulos ? "" : "opacity-60"}`}
          >
            CC
          </button>
        )}
        <button type="button" onClick={pantallaCompleta} aria-label="Pantalla completa" className="grid h-11 w-11 place-items-center text-[#FFF6EE]">
          <Maximize size={18} strokeWidth={1.75} />
        </button>
      </div>
    </div>
  );
}

function NoCargo({ marco, onReintentar }: { marco: string; onReintentar: () => void }) {
  return (
    <div className={`${marco} grid place-items-center`}>
      <div className="flex flex-col items-center gap-2.5 text-center">
        <p className="text-[14px] font-bold text-[#FFF6EE]">El video no cargó.</p>
        <button
          type="button"
          onClick={onReintentar}
          className="h-11 rounded-pill bg-[#FFF6EE] px-4 text-[13px] font-bold text-[#33203B] focus-visible:shadow-[0_0_0_4px_rgba(232,80,58,.35)] focus-visible:outline-none"
        >
          Intentar de nuevo
        </button>
      </div>
    </div>
  );
}

function reloj(segundos: number): string {
  if (!Number.isFinite(segundos) || segundos < 0) return "0:00";
  const s = Math.round(segundos);
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`;
}
