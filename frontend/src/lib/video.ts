/**
 * De un enlace pegado en Ajustes a algo que se puede reproducir dentro de Orión. Pardo pega lo que
 * le da la plataforma donde subió el video —el enlace de «Compartir» de YouTube, el de Vimeo, el de
 * Google Drive—, no el de inserción; aquí se traduce. Lo que no se reconoce se intenta como archivo
 * de video directo (un .mp4 de Cloudinary, por ejemplo).
 */
export type Reproductor = { tipo: "iframe"; src: string } | { tipo: "video"; src: string };

export function reproductorDe(enlace: string): Reproductor | null {
  let url: URL;
  try {
    url = new URL(enlace.trim());
  } catch {
    return null;
  }
  if (url.protocol !== "https:") return null;
  const host = url.hostname.replace(/^www\.|^m\./, "");
  const partes = url.pathname.split("/").filter(Boolean);

  const youtube = (id: string | null | undefined): Reproductor | null =>
    id && /^[\w-]{6,20}$/.test(id)
      ? { tipo: "iframe", src: `https://www.youtube-nocookie.com/embed/${id}?rel=0&modestbranding=1` }
      : null;

  if (host === "youtu.be") return youtube(partes[0]);
  if (host === "youtube.com" || host === "youtube-nocookie.com") {
    if (partes[0] === "watch") return youtube(url.searchParams.get("v"));
    if (["embed", "shorts", "live"].includes(partes[0])) return youtube(partes[1]);
    return null;
  }

  if (host === "vimeo.com" || host === "player.vimeo.com") {
    const i = partes.findIndex((p) => /^\d+$/.test(p));
    if (i < 0) return null;
    // Un video oculto de Vimeo lleva su llave como siguiente segmento (vimeo.com/123/abc) o en ?h=.
    const llave = url.searchParams.get("h") ?? (partes[i + 1] && /^[\da-f]+$/i.test(partes[i + 1]) ? partes[i + 1] : null);
    return { tipo: "iframe", src: `https://player.vimeo.com/video/${partes[i]}${llave ? `?h=${llave}` : ""}` };
  }

  if (host === "drive.google.com") {
    const i = partes.indexOf("d");
    const id = i >= 0 ? partes[i + 1] : url.searchParams.get("id");
    return id ? { tipo: "iframe", src: `https://drive.google.com/file/d/${id}/preview` } : null;
  }

  return { tipo: "video", src: url.toString() };
}
