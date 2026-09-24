import { reproductorDe } from "@/lib/video";

/** El video de un enlace de YouTube, Vimeo, Drive o un archivo directo, en 16:9. */
export function Reproductor({ url, titulo }: { url: string; titulo: string }) {
  const r = reproductorDe(url);
  if (!r) {
    return (
      <p className="rounded-card bg-surface-sunken p-4 text-[13.5px] text-text-secondary">
        No pudimos cargar el video. Si el problema sigue, escríbenos desde Ayuda.
      </p>
    );
  }
  return (
    <div className="relative aspect-video w-full max-w-full overflow-hidden rounded-card bg-[#1d1424]">
      {r.tipo === "iframe" ? (
        <iframe
          src={r.src}
          title={titulo}
          className="absolute inset-0 h-full w-full"
          allow="autoplay; encrypted-media; picture-in-picture; fullscreen"
          allowFullScreen
          referrerPolicy="strict-origin-when-cross-origin"
        />
      ) : (
        <video src={r.src} title={titulo} controls playsInline preload="metadata" className="absolute inset-0 h-full w-full" />
      )}
    </div>
  );
}
