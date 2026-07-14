import { iniciales } from "@/lib/format";

/**
 * Iniciales sobre un tinte. El tinte no es aleatorio: se deriva del nombre, así una misma
 * persona siempre tiene el mismo color, en todas las pantallas y entre recargas.
 */
const TINTES = [
  "bg-accent-bg text-on-accent-bg",
  "bg-info-bg text-info",
  "bg-warning-bg text-warning",
  "bg-success-bg text-success",
];

function tinteDe(nombre: string): string {
  const suma = [...nombre].reduce((acc, letra) => acc + letra.charCodeAt(0), 0);
  return TINTES[suma % TINTES.length];
}

const TAMANOS = {
  sm: "h-[34px] w-[34px] text-[12px]",
  md: "h-12 w-12 text-[14px]",
  lg: "h-14 w-14 text-[16px]",
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
      className={`${TAMANOS[size]} ${tinteDe(nombre)} grid shrink-0 place-items-center rounded-full font-bold ${className}`}
    >
      {iniciales(nombre)}
    </span>
  );
}
