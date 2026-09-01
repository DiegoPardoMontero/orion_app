"use client";

import { useQueryClient } from "@tanstack/react-query";
import { Camera } from "lucide-react";
import { useRef, useState, type ChangeEvent } from "react";
import { ApiError, uploadFoto } from "@/lib/api/fetch";
import { meQueryKey } from "@/lib/auth/session";
import { Avatar } from "./Avatar";

const TIPOS = ["image/jpeg", "image/png", "image/webp"];
const MAX_BYTES = 5 * 1024 * 1024;

/**
 * Subir/cambiar la foto de perfil (cualquier rol). Valida tipo y tamaño en el cliente antes de
 * gastar red, sube por POST /me/photo e invalida las cachés que pintan avatares para que la nueva
 * foto aparezca en toda la app. El fallback de iniciales se mantiene si no hay foto.
 */
export function CambiarFoto({ nombre, fotoUrl }: { nombre: string; fotoUrl?: string | null }) {
  const input = useRef<HTMLInputElement>(null);
  const queryClient = useQueryClient();
  const [foto, setFoto] = useState<string | null | undefined>(fotoUrl);
  const [subiendo, setSubiendo] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!file) return;
    setError(null);

    if (!TIPOS.includes(file.type)) {
      setError("La foto debe ser JPEG, PNG o WEBP.");
      return;
    }
    if (file.size > MAX_BYTES) {
      setError("La imagen no puede superar 5 MB.");
      return;
    }

    setSubiendo(true);
    try {
      const { photoUrl } = await uploadFoto(file);
      setFoto(photoUrl);
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

  return (
    <div className="flex items-center gap-4">
      <Avatar nombre={nombre} fotoUrl={foto} size="xl" />
      <div>
        <button
          type="button"
          onClick={() => input.current?.click()}
          disabled={subiendo}
          className="inline-flex min-h-11 items-center gap-2 rounded-pill border-[1.5px] border-border px-5 text-[14px] font-bold text-text transition-colors hover:bg-surface-sunken focus-visible:shadow-focus disabled:opacity-60"
        >
          <Camera size={16} strokeWidth={1.75} />
          {subiendo ? "Subiendo…" : "Cambiar foto"}
        </button>
        <p className="mt-1.5 text-[12px] text-text-muted">JPEG, PNG o WEBP · máx. 5 MB</p>
        {error && <p className="mt-1 text-[12px] font-semibold text-error">{error}</p>}
        <input
          ref={input}
          type="file"
          accept="image/jpeg,image/png,image/webp"
          onChange={onFile}
          className="hidden"
        />
      </div>
    </div>
  );
}
