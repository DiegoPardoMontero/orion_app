import type { ReactNode } from "react";

/**
 * Logotipo «ORIÓN ✦». El ✦ es el único glifo emoji permitido en toda la interfaz. Toma el color
 * del contexto (coral sobre superficie clara, on-primary sobre el degradado del amanecer).
 */
export function Wordmark({ className = "text-[16px]" }: { className?: string }) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 font-display font-extrabold tracking-[0.02em] ${className}`}
    >
      ORIÓN
      <span aria-hidden="true" className="text-[0.9em] leading-none">
        ✦
      </span>
    </span>
  );
}

/**
 * Constelación de Orión: ocho estrellas del asterismo con sus líneas. Coordenadas literales del
 * entregable (viewBox 120×130). Betelgeuse y Rigel van en durazno y con radio mayor. Las estrellas
 * titilan en opacidad —nunca se mueven de sitio— con fases desfasadas.
 */
const ESTRELLAS = [
  { cx: 58, cy: 8, r: 2.2, peach: false, dur: "3.4s", delay: "0s" }, // Meissa
  { cx: 28, cy: 26, r: 3.4, peach: true, dur: "4.2s", delay: "0.8s" }, // Betelgeuse
  { cx: 88, cy: 20, r: 2.2, peach: false, dur: "3.8s", delay: "1.6s" }, // Bellatrix
  { cx: 50, cy: 58, r: 2.4, peach: false, dur: "4.6s", delay: "0.4s" }, // Alnitak
  { cx: 58, cy: 63, r: 2.4, peach: false, dur: "3.2s", delay: "1.2s" }, // Alnilam
  { cx: 66, cy: 68, r: 2.4, peach: false, dur: "5s", delay: "2s" }, // Mintaka
  { cx: 34, cy: 112, r: 2.2, peach: false, dur: "4s", delay: "0.6s" }, // Saiph
  { cx: 92, cy: 106, r: 3.4, peach: true, dur: "4.8s", delay: "1.4s" }, // Rigel
];

const LINEAS = [
  [58, 8, 28, 26],
  [58, 8, 88, 20],
  [28, 26, 50, 58],
  [88, 20, 66, 68],
  [50, 58, 58, 63],
  [58, 63, 66, 68],
  [50, 58, 34, 112],
  [66, 68, 92, 106],
] as const;

export function Constelacion({ className = "" }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 120 130"
      aria-hidden="true"
      className={className}
      fill="none"
    >
      {LINEAS.map(([x1, y1, x2, y2], i) => (
        <line
          key={i}
          x1={x1}
          y1={y1}
          x2={x2}
          y2={y2}
          stroke="rgba(255,255,255,0.32)"
          strokeWidth={0.9}
        />
      ))}
      {ESTRELLAS.map((estrella, i) => (
        <circle
          key={i}
          className="estrella"
          cx={estrella.cx}
          cy={estrella.cy}
          r={estrella.r}
          fill={estrella.peach ? "var(--color-accent-peach)" : "var(--color-on-primary)"}
          style={{ animationDuration: estrella.dur, animationDelay: estrella.delay }}
        />
      ))}
    </svg>
  );
}

/**
 * Panel de marca con el degradado del amanecer y la constelación. Es el motivo hero de las
 * pantallas de autenticación y de la cabecera de reserva. Nunca detrás de texto de lectura largo.
 */
export function PanelAmanecer({
  children,
  className = "",
  constelacion = "h-[150px] w-[150px]",
}: {
  children?: ReactNode;
  className?: string;
  /** Tamaño/posición de la constelación (arriba a la derecha por defecto). */
  constelacion?: string;
}) {
  return (
    <div className={`gradient-dawn relative overflow-hidden ${className}`}>
      <Constelacion className={`pointer-events-none absolute -right-4 -top-6 ${constelacion}`} />
      <div className="relative">{children}</div>
    </div>
  );
}
