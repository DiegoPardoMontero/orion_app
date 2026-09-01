import { iniciales } from "@/lib/format";

/**
 * Iniciales sobre el degradado del amanecer en diagonal (135°, coral → lavanda). El fondo es fijo
 * por diseño —la identidad se lee en las iniciales, no en el color—; si el profesor sube foto,
 * esta manda y el degradado queda de fallback.
 */
const TAMANOS = {
  sm: "h-[34px] w-[34px] text-[13px]",
  md: "h-[46px] w-[46px] text-[15px]",
  lg: "h-14 w-14 text-[17px]",
  xl: "h-[92px] w-[92px] text-[30px]",
} as const;

export function Avatar({
  nombre,
  fotoUrl,
  size = "md",
  className = "",
}: {
  nombre: string;
  fotoUrl?: string | null;
  size?: keyof typeof TAMANOS;
  className?: string;
}) {
  if (fotoUrl) {
    return (
      // eslint-disable-next-line @next/next/no-img-element -- la URL la escribe el profesor (campo de texto en el MVP)
      <img
        src={fotoUrl}
        alt={nombre}
        className={`${TAMANOS[size]} shrink-0 rounded-full object-cover ${className}`}
      />
    );
  }

  return (
    <span
      className={`${TAMANOS[size]} gradient-avatar grid shrink-0 place-items-center rounded-full font-display font-extrabold text-on-primary ${className}`}
    >
      {iniciales(nombre)}
    </span>
  );
}
