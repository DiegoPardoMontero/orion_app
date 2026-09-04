/**
 * Una estrella de logro, dibujada desde datos.
 *
 * El diseño entrega 20 logros × 3 estados = 60 SVG. No son 60 archivos: son un mismo `path` de
 * estrella, un halo, cuatro rayos, un glifo y un color por familia — y todo eso ya viaja en la
 * respuesta de `/me/achievements`. Sesenta archivos de 8 KB son casi medio megabyte en una PWA
 * instalable, más un archivo nuevo por cada logro futuro; esto son tres kilobytes y un logro nuevo
 * es una fila en la base.
 *
 * Las medidas son las del contrato visual y no se tocan: radio exterior 220, interior 118, trazo 50
 * con `paint-order: stroke fill` (es lo que redondea las puntas), halo r 246, órbita de progreso
 * r 240 con circunferencia 1508.
 */

const CENTRO = 256;
const RADIO_EXTERIOR = 220;
const RADIO_INTERIOR = 118;
const RADIO_HALO = 246;
const RADIO_ORBITA = 240;
/** 2·π·240, redondeado como en el entregable. */
const CIRCUNFERENCIA = 1508;

export type FamiliaLogro = "PRIMEROS" | "CONSTANCIA" | "VOLUMEN" | "AMPLITUD" | "COMPROMISO";
export type EstadoLogro = "apagada" | "progreso" | "encendida";

/** Durazno para los primeros pasos, la constancia y el volumen; lavanda para amplitud y compromiso. */
function colorDe(familia: FamiliaLogro): string {
  return familia === "AMPLITUD" || familia === "COMPROMISO"
    ? "var(--color-star-social)"
    : "var(--color-star-solo)";
}

/** Los diez vértices de una estrella de cinco puntas, empezando arriba. */
function puntosDeEstrella(): string {
  const puntos: string[] = [];
  for (let i = 0; i < 10; i++) {
    const angulo = ((-90 + i * 36) * Math.PI) / 180;
    const radio = i % 2 === 0 ? RADIO_EXTERIOR : RADIO_INTERIOR;
    puntos.push(
      `${(CENTRO + radio * Math.cos(angulo)).toFixed(1)},${(CENTRO + radio * Math.sin(angulo)).toFixed(1)}`,
    );
  }
  return puntos.join(" ");
}

const ESTRELLA = puntosDeEstrella();

/** Los cuatro rayos cardinales del brillo 3: de 4 a 30 y de 482 a 508. */
const RAYOS = [
  { x1: 4, y1: CENTRO, x2: 30, y2: CENTRO },
  { x1: 482, y1: CENTRO, x2: 508, y2: CENTRO },
  { x1: CENTRO, y1: 4, x2: CENTRO, y2: 30 },
  { x1: CENTRO, y1: 482, x2: CENTRO, y2: 508 },
];

export function EstrellaLogro({
  familia,
  brillo,
  estado,
  progreso,
  numeral,
  glifo,
  size = 64,
  sobreCielo = false,
  className = "",
  titulo,
}: {
  familia: FamiliaLogro;
  /** 1 estrella · 2 con halo · 3 con halo y rayos. */
  brillo: 1 | 2 | 3;
  estado: EstadoLogro;
  progreso?: { hecho: number; total: number };
  /** Un numeral («8») en vez de glifo, para los logros de cuenta. */
  numeral?: string;
  /** Un icono ya renderizado, centrado por el propio componente. */
  glifo?: React.ReactNode;
  size?: number;
  /** Sobre el cielo las apagadas van en crema, no en tinta. */
  sobreCielo?: boolean;
  className?: string;
  titulo?: string;
}) {
  const color = colorDe(familia);
  const apagada = estado === "apagada";
  const encendida = estado === "encendida";
  const tenue = sobreCielo ? "var(--color-star-dim-on-sky)" : "var(--color-star-dim)";
  const id = `${familia}-${brillo}-${estado}`;

  const avance = progreso && progreso.total > 0
    ? Math.min(1, progreso.hecho / progreso.total)
    : 0;

  return (
    <svg
      viewBox="0 0 512 512"
      width={size}
      height={size}
      className={`shrink-0 ${className}`}
      role="img"
      aria-label={titulo}
    >
      <defs>
        <radialGradient id={`resplandor-${id}`}>
          <stop offset="0%" stopColor={color} stopOpacity="0.55" />
          <stop offset="100%" stopColor={color} stopOpacity="0" />
        </radialGradient>
      </defs>

      {/* El resplandor va detrás de todo y solo cuando está encendida. */}
      {encendida && <circle cx={CENTRO} cy={CENTRO} r={250} fill={`url(#resplandor-${id})`} />}

      {/* Halo y rayos: el escalón se lee POR LA FORMA, así que se dibujan también apagados —
          discontinuos— en vez de desaparecer. Así se ve qué logro es antes de conseguirlo. */}
      {brillo >= 2 && (
        <circle
          cx={CENTRO}
          cy={CENTRO}
          r={RADIO_HALO}
          fill="none"
          stroke={encendida ? color : tenue}
          strokeWidth={8}
          strokeDasharray={encendida ? undefined : "16 14"}
        />
      )}
      {brillo === 3 &&
        RAYOS.map((rayo, i) => (
          <line
            key={i}
            {...rayo}
            stroke={encendida ? color : tenue}
            strokeWidth={8}
            strokeLinecap="round"
            strokeDasharray={encendida ? undefined : "16 14"}
          />
        ))}

      {/* La órbita de progreso: pista completa y arco del avance, girado para empezar arriba. */}
      {estado === "progreso" && (
        <g transform={`rotate(-90 ${CENTRO} ${CENTRO})`}>
          <circle
            cx={CENTRO}
            cy={CENTRO}
            r={RADIO_ORBITA}
            fill="none"
            stroke="var(--color-progress-track)"
            strokeWidth={10}
          />
          <circle
            cx={CENTRO}
            cy={CENTRO}
            r={RADIO_ORBITA}
            fill="none"
            stroke="var(--color-progress-fill)"
            strokeWidth={10}
            strokeLinecap="round"
            strokeDasharray={`${Math.round(avance * CIRCUNFERENCIA)} ${CIRCUNFERENCIA}`}
          />
        </g>
      )}

      {/*
        La estrella. Encendida es la silueta maciza: el trazo de 50 del mismo color que el relleno
        es lo que redondea las puntas. Sin encender es un CONTORNO fino —10 px, discontinuo mientras
        está apagada y continuo mientras avanza—, no la silueta gruesa en gris: dibujar guiones
        sobre un trazo de 50 la convierte en un código de barras.
      */}
      <polygon
        points={ESTRELLA}
        fill={encendida ? color : "none"}
        stroke={encendida ? color : tenue}
        strokeWidth={encendida ? 50 : 10}
        strokeLinejoin="round"
        strokeDasharray={apagada ? "16 14" : undefined}
        paintOrder="stroke fill"
        opacity={estado === "progreso" ? 0.8 : 1}
      />

      {/* Glifo o numeral, centrados según el contrato. */}
      {numeral ? (
        <text
          x={CENTRO}
          y={CENTRO}
          textAnchor="middle"
          dominantBaseline="central"
          fontFamily="var(--font-display, sans-serif)"
          fontWeight={800}
          fontSize={numeral.length >= 3 ? 130 : 165}
          fill="var(--color-star-ink)"
          opacity={apagada ? 0.3 : 1}
        >
          {numeral}
        </text>
      ) : glifo ? (
        <g
          transform="translate(172 172) scale(7)"
          stroke="var(--color-star-ink)"
          strokeWidth={1.75}
          fill="none"
          opacity={apagada ? 0.3 : 1}
        >
          {glifo}
        </g>
      ) : null}

      {/* El candado de las apagadas, abajo a la derecha. */}
      {apagada && (
        <g>
          <circle cx={416} cy={416} r={60} fill={tenue} />
          <g transform="translate(392 392) scale(2)" stroke="var(--color-surface)" strokeWidth={2} fill="none">
            <rect x={3} y={11} width={18} height={11} rx={2} />
            <path d="M7 11V7a5 5 0 0 1 10 0v4" />
          </g>
        </g>
      )}
    </svg>
  );
}
