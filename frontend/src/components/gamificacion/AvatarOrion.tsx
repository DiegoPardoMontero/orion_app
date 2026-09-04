import { Sello } from "@/components/gamificacion/Sello";
import { iniciales } from "@/lib/format";

/**
 * El avatar del estudiante en la dirección «Sello» del diseño.
 *
 * <p>La foto real <strong>no se toca</strong>: el progreso la rodea. El orden de capas es fijo y no
 * es decorativo — es lo que impide que un accesorio tape la cara: cielo → marco → foto → sello bajo
 * la foto → accesorios z1, z2, z3.
 *
 * Marcos, paletas y cielos son clases CSS (§2e); aquí solo se componen.
 */

export type AccesorioEquipado = { zone: string; accessoryCode: string };

export function AvatarOrion({
  nombre,
  fotoUrl,
  frameCode = "trazo",
  paletteCode = "trazo",
  skyCode = "crema",
  sealLevel = 1,
  accesorios = [],
  size = 120,
  className = "",
}: {
  nombre: string;
  fotoUrl?: string | null;
  frameCode?: string;
  paletteCode?: string;
  skyCode?: string;
  sealLevel?: 1 | 2 | 3;
  accesorios?: AccesorioEquipado[];
  size?: number;
  className?: string;
}) {
  const enZona = (zona: string) => accesorios.some((a) => a.zone === zona);
  const fotoSize = Math.round(size * 0.72);

  return (
    <div
      className={`avatar-orion cielo-${skyCode} marco-${frameCode} paleta-${paletteCode} ${className}`}
      style={{ width: size, height: size }}
    >
      {/* z3 · corona: encima del disco, anclada por su borde inferior. */}
      {enZona("z3") && (
        <span
          aria-hidden="true"
          className="pointer-events-none absolute left-1/2 -translate-x-1/2"
          style={{ top: -size * 0.14, width: size * 0.44, height: size * 0.22 }}
        >
          <svg viewBox="0 0 220 110" width="100%" height="100%">
            <path
              d="M10 100 L55 20 L110 70 L165 20 L210 100"
              fill="none"
              stroke="var(--avatar-tinte, var(--color-accent-peach))"
              strokeWidth={14}
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>
        </span>
      )}

      {/* La foto se dibuja con su medida exacta en vez de usar <Avatar>, que tiene una escala
          fija de cuatro tallas: aquí el tamaño lo manda el disco, y forzarlo con !important
          rompería a la primera que alguien cambie esa escala. */}
      {fotoUrl ? (
        // eslint-disable-next-line @next/next/no-img-element -- misma razón que en <Avatar>
        <img
          src={fotoUrl}
          alt={nombre}
          className="rounded-full object-cover"
          style={{ width: fotoSize, height: fotoSize }}
        />
      ) : (
        <span
          className="gradient-avatar grid place-items-center rounded-full font-display font-extrabold text-on-primary"
          style={{ width: fotoSize, height: fotoSize, fontSize: Math.round(fotoSize * 0.34) }}
        >
          {iniciales(nombre)}
        </span>
      )}

      {/* El sello va bajo la foto, solapando su borde inferior. */}
      <span
        aria-hidden="true"
        className="pointer-events-none absolute left-1/2 -translate-x-1/2"
        style={{ bottom: -size * 0.06 }}
      >
        <Sello nivel={sealLevel} size={Math.round(size * 0.3)} />
      </span>

      {/* z2 · centro: un monograma discreto sobre el sello. */}
      {enZona("z2") && (
        <span
          aria-hidden="true"
          className="pointer-events-none absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 rounded-full"
          style={{
            width: size * 0.16,
            height: size * 0.16,
            background: "var(--avatar-tinte, var(--color-accent-peach))",
            opacity: 0.85,
          }}
        />
      )}

      {/* z1 · base: un arco bajo el sello, sin salir del disco. */}
      {enZona("z1") && (
        <span
          aria-hidden="true"
          className="pointer-events-none absolute left-1/2 -translate-x-1/2"
          style={{ bottom: -size * 0.16, width: size * 0.48, height: size * 0.2 }}
        >
          <svg viewBox="0 0 240 100" width="100%" height="100%">
            <path
              d="M10 20 Q120 110 230 20"
              fill="none"
              stroke="var(--avatar-tinte, var(--color-accent-peach))"
              strokeWidth={12}
              strokeLinecap="round"
            />
          </svg>
        </span>
      )}
    </div>
  );
}

/** El tamaño de la foto dentro del disco, por si alguna pantalla lo necesita aparte. */
export const PROPORCION_FOTO = 0.72;
