import { iniciales } from "@/lib/format";

/** Foto si la hay; si no, iniciales sobre el azul de la marca. */
export function Avatar({
  nombre,
  fotoUrl,
  size = "md",
}: {
  nombre: string;
  fotoUrl?: string | null;
  size?: "sm" | "md";
}) {
  const dimensions = size === "sm" ? "h-8 w-8 text-[11px]" : "h-10 w-10 text-[13px]";

  if (fotoUrl) {
    return (
      // eslint-disable-next-line @next/next/no-img-element -- la URL es libre (campo de texto en el MVP)
      <img
        src={fotoUrl}
        alt={nombre}
        className={`${dimensions} shrink-0 rounded-full object-cover`}
      />
    );
  }

  return (
    <span
      className={`${dimensions} grid shrink-0 place-items-center rounded-full bg-accent-soft font-semibold text-accent-ink`}
    >
      {iniciales(nombre)}
    </span>
  );
}
