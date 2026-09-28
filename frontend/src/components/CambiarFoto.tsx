"use client";

import { useQueryClient } from "@tanstack/react-query";
import { Camera } from "lucide-react";
import { useRef, useState, type ChangeEvent } from "react";
import { ApiError, uploadFoto } from "@/lib/api/fetch";
import { meQueryKey } from "@/lib/auth/session";
import { Avatar } from "./Avatar";

const TIPOS = ["image/jpeg", "image/png", "image/webp"];
const MAX_BYTES = 5 * 1024 * 1024;

/** Por debajo de esto, y sin pasarse de lado, la foto se sube tal cual. */
const SIN_TOCAR_BYTES = 1.5 * 1024 * 1024;
const LADO_MAXIMO = 1600;
const CALIDAD_JPEG = 0.85;

type Decodificada = { fuente: CanvasImageSource; ancho: number; alto: number; soltar: () => void };

/**
 * La imagen lista para dibujar, girada como la muestra el celular (EXIF). `createImageBitmap` con
 * `imageOrientation` lo hace explícito; si el navegador no lo tiene, un `<img>` también respeta la
 * orientación en los navegadores de hoy.
 */
async function decodificar(file: File): Promise<Decodificada | null> {
  if (typeof createImageBitmap === "function") {
    try {
      const bitmap = await createImageBitmap(file, { imageOrientation: "from-image" });
      return { fuente: bitmap, ancho: bitmap.width, alto: bitmap.height, soltar: () => bitmap.close() };
    } catch {
      // Sigue con <img>.
    }
  }
  const url = URL.createObjectURL(file);
  try {
    const img = new Image();
    img.src = url;
    await img.decode();
    return { fuente: img, ancho: img.naturalWidth, alto: img.naturalHeight, soltar: () => URL.revokeObjectURL(url) };
  } catch {
    URL.revokeObjectURL(url);
    return null;
  }
}

/**
 * Las fotos de un celular reciente pesan a veces más de 5 MB (el límite del servidor) y miden 4000 px
 * para verse en un círculo de 96. Si pasa de ~1,5 MB o de 1600 px de lado, se achica en el navegador
 * a 1600 px de lado mayor en JPEG: se sube en segundos y ya no se rechaza. Si no se puede leer, va la
 * original y el servidor decide.
 */
async function prepararFoto(file: File): Promise<File> {
  const imagen = await decodificar(file);
  if (!imagen) return file;
  try {
    const { fuente, ancho, alto } = imagen;
    if (file.size <= SIN_TOCAR_BYTES && Math.max(ancho, alto) <= LADO_MAXIMO) return file;

    const escala = Math.min(1, LADO_MAXIMO / Math.max(ancho, alto));
    const canvas = document.createElement("canvas");
    canvas.width = Math.max(1, Math.round(ancho * escala));
    canvas.height = Math.max(1, Math.round(alto * escala));
    const ctx = canvas.getContext("2d");
    if (!ctx) return file;
    // JPEG no tiene transparencia: sin fondo, lo transparente de un PNG sale negro.
    ctx.fillStyle = "#ffffff";
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.drawImage(fuente, 0, 0, canvas.width, canvas.height);

    const blob = await new Promise<Blob | null>((resolver) =>
      canvas.toBlob(resolver, "image/jpeg", CALIDAD_JPEG),
    );
    if (!blob || blob.size >= file.size) return file;
    const nombre = file.name.replace(/\.[^.]+$/, "") || "foto";
    return new File([blob], `${nombre}.jpg`, { type: "image/jpeg" });
  } finally {
    imagen.soltar();
  }
}

/**
 * Subir/cambiar la foto de perfil (cualquier rol). Valida el tipo, achica en el navegador la que
 * viene grande, sube por POST /me/photo e invalida las cachés que pintan avatares para que la nueva
 * foto aparezca en toda la app. El fallback de iniciales se mantiene si no hay foto.
 *
 * @param id el del botón, para que un formulario pueda llevar el foco aquí y describirlo.
 * @param onSubida avisa en cuanto la foto quedó arriba, sin esperar a que la sesión se refresque.
 * @param enPortada la foto grande con la cámara encima, para la portada de «Mi perfil» del profe.
 */
export function CambiarFoto({
  nombre,
  fotoUrl,
  id,
  describedBy,
  onSubida,
  enPortada = false,
}: {
  nombre: string;
  fotoUrl?: string | null;
  id?: string;
  describedBy?: string;
  onSubida?: (url: string) => void;
  enPortada?: boolean;
}) {
  const input = useRef<HTMLInputElement>(null);
  const queryClient = useQueryClient();
  const [foto, setFoto] = useState<string | null | undefined>(fotoUrl);
  const [subiendo, setSubiendo] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onFile(event: ChangeEvent<HTMLInputElement>) {
    const original = event.target.files?.[0];
    if (!original) return;
    setError(null);

    if (!TIPOS.includes(original.type)) {
      setError("La foto debe ser JPEG, PNG o WEBP.");
      return;
    }

    setSubiendo(true);
    try {
      const file = await prepararFoto(original);
      if (file.size > MAX_BYTES) {
        setError("La imagen no puede superar 5 MB.");
        return;
      }
      const { photoUrl } = await uploadFoto(file);
      setFoto(photoUrl);
      onSubida?.(photoUrl);
      // Todo lo que pinta avatares se refresca: sesión, cuenta, perfil y directorio.
      void queryClient.invalidateQueries({ queryKey: meQueryKey });
      void queryClient.invalidateQueries({ queryKey: ["me"] });
      void queryClient.invalidateQueries({ queryKey: ["professors"] });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "No pudimos subir la foto. Inténtalo de nuevo.");
    } finally {
      setSubiendo(false);
      if (input.current) input.current.value = "";
    }
  }

  const selector = (
    <input ref={input} type="file" accept="image/jpeg,image/png,image/webp" onChange={onFile} className="hidden" />
  );

  if (enPortada) {
    const etiqueta = subiendo ? "Subiendo…" : foto ? "Cambiar foto" : "Subir foto";
    return (
      <div className="flex flex-col items-start">
        <div className="relative">
          <Avatar nombre={nombre} fotoUrl={foto} size="xl" className="ring-4 ring-surface-raised" />
          <button
            id={id}
            type="button"
            onClick={() => input.current?.click()}
            disabled={subiendo}
            aria-label={etiqueta}
            title={`${etiqueta} (JPEG, PNG o WEBP)`}
            aria-describedby={describedBy}
            className="absolute -bottom-0.5 -right-0.5 grid h-9 w-9 place-items-center rounded-full bg-primary text-on-primary shadow-primary ring-4 ring-surface-raised transition-colors hover:bg-primary-strong focus-visible:shadow-focus disabled:opacity-60"
          >
            <Camera size={16} strokeWidth={2} />
          </button>
        </div>
        {subiendo && <p className="mt-2 text-[12px] font-semibold text-text-muted">Subiendo tu foto…</p>}
        {error && <p className="mt-2 max-w-[260px] text-[12px] font-semibold text-error">{error}</p>}
        {selector}
      </div>
    );
  }

  return (
    <div className="flex items-center gap-4">
      <Avatar nombre={nombre} fotoUrl={foto} size="xl" />
      <div>
        <button
          id={id}
          type="button"
          onClick={() => input.current?.click()}
          disabled={subiendo}
          aria-describedby={describedBy}
          className="inline-flex min-h-11 items-center gap-2 rounded-pill border-[1.5px] border-border px-5 text-[14px] font-bold text-text transition-colors hover:bg-surface-sunken focus-visible:shadow-focus disabled:opacity-60"
        >
          <Camera size={16} strokeWidth={1.75} />
          {subiendo ? "Subiendo…" : foto ? "Cambiar foto" : "Subir foto"}
        </button>
        <p className="mt-1.5 text-[12px] text-text-muted">JPEG, PNG o WEBP</p>
        {error && <p className="mt-1 text-[12px] font-semibold text-error">{error}</p>}
        {selector}
      </div>
    </div>
  );
}
