/**
 * El sello de nivel, bajo la foto. Cambia de FORMA y no solo de color: nivel 1 cuatro puntas en
 * contorno, nivel 2 cinco puntas rellenas, nivel 3 con ocho rayos y la tinta invertida.
 *
 * Cambiar la forma y no el color es lo que hace que el escalón se lea de un vistazo y también en
 * blanco y negro. El nivel es derivado —lo calcula el backend a partir de los logros—, así que aquí
 * solo se dibuja lo que llega.
 */

const CENTRO = 256;

/** Una estrella de n puntas, con su radio exterior e interior. */
function puntas(n: number, exterior: number, interior: number): string {
  const puntos: string[] = [];
  for (let i = 0; i < n * 2; i++) {
    const angulo = ((-90 + (i * 180) / n) * Math.PI) / 180;
    const radio = i % 2 === 0 ? exterior : interior;
    puntos.push(
      `${(CENTRO + radio * Math.cos(angulo)).toFixed(1)},${(CENTRO + radio * Math.sin(angulo)).toFixed(1)}`,
    );
  }
  return puntos.join(" ");
}

const CUATRO_PUNTAS = puntas(4, 210, 84);
const CINCO_PUNTAS = puntas(5, 210, 110);

export function Sello({
  nivel,
  size = 40,
  className = "",
}: {
  nivel: 1 | 2 | 3;
  size?: number;
  className?: string;
}) {
  const etiqueta = `Sello de nivel ${nivel}`;

  return (
    <svg
      viewBox="0 0 512 512"
      width={size}
      height={size}
      className={`shrink-0 ${className}`}
      role="img"
      aria-label={etiqueta}
    >
      {nivel === 3 && (
        <g stroke="var(--color-star-solo)" strokeWidth={16} strokeLinecap="round">
          {Array.from({ length: 8 }, (_, i) => {
            const angulo = ((i * 45 - 90) * Math.PI) / 180;
            return (
              <line
                key={i}
                x1={CENTRO + 230 * Math.cos(angulo)}
                y1={CENTRO + 230 * Math.sin(angulo)}
                x2={CENTRO + 256 * Math.cos(angulo)}
                y2={CENTRO + 256 * Math.sin(angulo)}
              />
            );
          })}
        </g>
      )}

      {nivel === 1 && (
        <polygon
          points={CUATRO_PUNTAS}
          fill="none"
          stroke="var(--color-star-ink)"
          strokeWidth={34}
          strokeLinejoin="round"
        />
      )}

      {nivel === 2 && (
        <polygon
          points={CINCO_PUNTAS}
          fill="var(--color-star-ink)"
          stroke="var(--color-star-ink)"
          strokeWidth={34}
          strokeLinejoin="round"
          paintOrder="stroke fill"
        />
      )}

      {nivel === 3 && (
        <polygon
          points={CINCO_PUNTAS}
          fill="var(--color-star-solo)"
          stroke="var(--color-star-solo)"
          strokeWidth={34}
          strokeLinejoin="round"
          paintOrder="stroke fill"
        />
      )}
    </svg>
  );
}
