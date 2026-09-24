"use client";

import { useId } from "react";

/**
 * El progreso de un set como una constelación (handoff `design_handoff_orion_practica`, §9.6): una
 * estrella por ejercicio que se enciende al cerrarlo y, con el set completo, las líneas que la unen.
 * Orión es una constelación, y cada práctica terminada es una más en el cielo del estudiante.
 *
 * <p>Las coordenadas son las del handoff, copiadas tal cual: cuatro formas sobre un lienzo de
 * 200 × 100, y cada set usa siempre la misma ({@link formaDe}).
 */

export type EstadoEstrella = "off" | "actual" | "primero" | "segundo" | "mostrada" | "saltada";

const FORMAS: [number, number][][] = [
  [
    [16, 70],
    [58, 30],
    [100, 56],
    [144, 20],
    [184, 48],
  ],
  [
    [20, 30],
    [60, 62],
    [104, 40],
    [140, 78],
    [182, 34],
  ],
  [
    [24, 56],
    [70, 22],
    [96, 72],
    [148, 58],
    [176, 18],
  ],
  [
    [18, 44],
    [62, 76],
    [110, 26],
    [132, 64],
    [186, 60],
  ],
];

const NOMBRE: Record<EstadoEstrella, string> = {
  off: "apagada",
  actual: "la que sigue",
  primero: "al primer intento",
  segundo: "al segundo intento",
  mostrada: "te la mostramos",
  saltada: "saltada",
};

/**
 * La forma de un set, estable: sale de su id, así la constelación de «Mi cielo» es la misma que se
 * encendió al practicar y la que ve el profesor.
 */
export function formaDe(id: string): number {
  const hex = id.replace(/-/g, "").slice(-4);
  const n = Number.parseInt(hex, 16);
  return Number.isNaN(n) ? 0 : n % FORMAS.length;
}

export function Constelacion({
  estados,
  forma = 0,
  ancho,
  r = 9,
  guias = false,
  lineas = false,
  fondo = "claro",
  perfecta = false,
  dibujar = false,
  encender = -1,
  etiqueta,
  className = "",
}: {
  estados: EstadoEstrella[];
  forma?: number;
  /** Ancho en px; el alto es la mitad. */
  ancho: number;
  /** Radio exterior de cada estrella, en unidades del lienzo de 200 × 100. */
  r?: number;
  /** Las guías punteadas entre estrellas, mientras se juega. */
  guias?: boolean;
  /** Las líneas de la constelación: solo con el set completo. */
  lineas?: boolean;
  fondo?: "claro" | "noche";
  perfecta?: boolean;
  /** Dibuja las líneas una tras otra, cada 300 ms. */
  dibujar?: boolean;
  /** La estrella que se está encendiendo ahora, con su anillo. */
  encender?: number;
  etiqueta?: string;
  className?: string;
}) {
  const id = useId().replace(/:/g, "");
  const noche = fondo === "noche";
  const puntos = FORMAS[forma % FORMAS.length].slice(0, Math.max(1, Math.min(estados.length, 5)));

  const star: string[] = [];
  for (let i = 0; i < 10; i++) {
    const rr = i % 2 ? r * 0.46 : r;
    const a = ((-90 + 36 * i) * Math.PI) / 180;
    star.push(`${(rr * Math.cos(a)).toFixed(2)},${(rr * Math.sin(a)).toFixed(2)}`);
  }
  const estrella = star.join(" ");

  const segmentos = puntos.slice(1).map(([x2, y2], i) => {
    const [x1, y1] = puntos[i];
    return { x1, y1, x2, y2, largo: Math.hypot(x2 - x1, y2 - y1) };
  });

  const encendidas = estados.filter((e) => e === "primero" || e === "segundo" || e === "mostrada").length;
  const aria =
    etiqueta ??
    `Constelación: ${encendidas} de ${estados.length} estrellas encendidas. ` +
      estados.map((e, i) => `Estrella ${i + 1}, ${NOMBRE[e]}`).join(". ");

  return (
    <svg
      width={ancho}
      height={Math.round(ancho / 2)}
      viewBox="0 0 200 100"
      role="img"
      aria-label={aria}
      className={`block max-w-full overflow-visible ${className}`}
      style={{ height: "auto" }}
    >
      <defs>
        <radialGradient id={`brillo-${id}`}>
          <stop offset="0" stopColor={noche ? "#FFE7A0" : "#FFCE3A"} stopOpacity={noche ? 0.55 : 0.5} />
          <stop offset="1" stopColor={noche ? "#FFE7A0" : "#FFCE3A"} stopOpacity={0} />
        </radialGradient>
      </defs>
      {perfecta && (
        <ellipse
          cx={100}
          cy={50}
          rx={104}
          ry={54}
          fill="none"
          stroke={noche ? "#FFC189" : "#E9A800"}
          strokeWidth={1.2}
          strokeDasharray="2 5"
          opacity={0.7}
        />
      )}
      {guias &&
        !lineas &&
        segmentos.map((g, i) => (
          <line
            key={`g${i}`}
            x1={g.x1}
            y1={g.y1}
            x2={g.x2}
            y2={g.y2}
            stroke={noche ? "rgba(255,246,238,.22)" : "rgba(51,32,59,.18)"}
            strokeWidth={1}
            strokeDasharray="1.5 4"
            strokeLinecap="round"
          />
        ))}
      {lineas &&
        segmentos.map((l, i) => (
          <line
            key={`l${i}`}
            x1={l.x1}
            y1={l.y1}
            x2={l.x2}
            y2={l.y2}
            stroke={noche ? "rgba(255,233,214,.85)" : "#33203B"}
            strokeWidth={noche ? 1.6 : 1.4}
            strokeLinecap="round"
            className={dibujar ? "pr-trazo" : undefined}
            style={
              dibujar
                ? ({
                    "--largo": l.largo.toFixed(1),
                    strokeDasharray: l.largo.toFixed(1),
                    animationDelay: `${i * 300}ms`,
                  } as React.CSSProperties)
                : undefined
            }
          />
        ))}
      {puntos.map(([x, y], i) => {
        const e = estados[i] ?? "off";
        const s = estilo(e, noche);
        return (
          <g key={i} transform={`translate(${x} ${y})`}>
            {e === "primero" && <circle r={17} fill={`url(#brillo-${id})`} />}
            {i === encender && (
              <circle r={12} fill="none" stroke="#FFCE3A" strokeWidth={2} className="pr-estallido" />
            )}
            {e === "actual" && (
              <circle r={13} fill="none" stroke={noche ? "#FFF6EE" : "#33203B"} strokeWidth={1.5} className="pr-pulso" />
            )}
            <polygon
              points={estrella}
              fill={s.fill}
              stroke={s.stroke}
              strokeWidth={s.sw}
              strokeDasharray={s.dash}
              strokeLinejoin="round"
              className={i === encender ? "pr-encender" : undefined}
            />
          </g>
        );
      })}
    </svg>
  );
}

function estilo(e: EstadoEstrella, noche: boolean): { fill: string; stroke: string; sw: number; dash?: string } {
  switch (e) {
    case "actual":
      return { fill: noche ? "#2E1E4E" : "#FFF6EE", stroke: noche ? "#FFF6EE" : "#33203B", sw: 1.8 };
    case "primero":
    case "segundo":
      return { fill: "#FFCE3A", stroke: "#E9A800", sw: 1.4 };
    case "mostrada":
      return { fill: noche ? "#C9B9F4" : "#EFE9F9", stroke: "#9B87D6", sw: 1.6 };
    case "saltada":
      return { fill: "none", stroke: "#9B87D6", sw: 1.4, dash: "1 2.4" };
    default:
      return { fill: "none", stroke: noche ? "rgba(255,246,238,.45)" : "#B8A99B", sw: 1.4, dash: "2 2.2" };
  }
}
