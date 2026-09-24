"use client";

import { useId } from "react";

/**
 * El sello de los logros de Práctica (handoff `design_handoff_orion_practica`, §9.9): la misma
 * estrella de los otros logros de «Mi cielo», en dorado Rigel. Tres estados —encendida, en progreso
 * y apagada— y, al conseguirse, se estampa.
 *
 * <p>El glifo o el número sale del código del logro, como en {@link numeralDe}: un logro nuevo de
 * la misma forma no obliga a tocar esto.
 */

const circulo = (cx: number, cy: number, r: number) => `M${cx - r} ${cy}a${r} ${r} 0 1 0 ${2 * r} 0a${r} ${r} 0 1 0 ${-2 * r} 0`;

function estrella(n: number, ro: number, ri: number): string {
  const p: string[] = [];
  for (let i = 0; i < n * 2; i++) {
    const r = i % 2 ? ri : ro;
    const a = ((-90 + (180 / n) * i) * Math.PI) / 180;
    p.push(`${(256 + r * Math.cos(a)).toFixed(1)} ${(256 + r * Math.sin(a)).toFixed(1)}`);
  }
  return `M${p.join("L")}Z`;
}

const ESTRELLA = estrella(5, 220, 118);
const HALO = circulo(256, 256, 246);
const RAYOS = "M256 4V30M256 482V508M4 256H30M482 256H508";

const GLIFO = {
  constelacion: `M4 17 9 8l6 5 5-9${circulo(4, 17, 1.6)}${circulo(9, 8, 1.6)}${circulo(15, 13, 1.6)}${circulo(20, 4, 1.6)}`,
  sparkles: "M9.94 14.06 8.5 20l-1.44-5.94L1 12.5l6.06-1.44L8.5 5l1.44 6.06L16 12.5zM19 3v4M17 5h4",
  oido: "M6 8.5a6.5 6.5 0 1 1 13 0c0 6-6 6-6 10a3.5 3.5 0 1 1-7 0M15 8.5a2.5 2.5 0 0 0-5 0v1a2 2 0 1 1 0 4",
  otraVez: "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8M3 3v5h5",
};

/** El glifo o el número de cada logro de Práctica, por su código. */
function queLleva(code: string): { glifo?: string; numero?: string } {
  const n = code.match(/-(\d+)-/);
  if (n) return { numero: n[1] };
  if (code.includes("perfecta")) return { glifo: GLIFO.sparkles };
  if (code.includes("oido")) return { glifo: GLIFO.oido };
  if (code.includes("segunda")) return { glifo: GLIFO.otraVez };
  return { glifo: GLIFO.constelacion };
}

export function SelloPractica({
  code,
  nombre,
  brillo,
  estado,
  hecho = 0,
  total = 1,
  size = 120,
  estampar = false,
}: {
  code: string;
  nombre: string;
  brillo: 1 | 2 | 3;
  estado: "apagada" | "progreso" | "encendida";
  hecho?: number;
  total?: number;
  size?: number;
  /** El momento en que se consigue: entra estampándose y un anillo dorado se abre. */
  estampar?: boolean;
}) {
  const id = useId().replace(/:/g, "");
  const { glifo, numero } = queLleva(code);
  const on = estado === "encendida";
  const prog = estado === "progreso";
  const halo = brillo >= 2 ? HALO : "";
  const rayos = brillo >= 3 ? RAYOS : "";
  const aria = `${nombre}, ${on ? "conseguido" : prog ? `${hecho} de ${total}` : "por conseguir"}`;
  const glifoG = (opacidad: number) =>
    glifo ? (
      <g
        transform="translate(172 172) scale(7)"
        fill="none"
        stroke="#33203B"
        strokeWidth={1.75}
        strokeLinecap="round"
        strokeLinejoin="round"
        opacity={opacidad}
      >
        <path d={glifo} />
      </g>
    ) : null;

  return (
    <div className="relative shrink-0" style={{ width: size, height: size }}>
      {estampar && on && (
        <div aria-hidden className="pr-anillo-sello pointer-events-none absolute inset-0 rounded-full border-[3px] border-rigel" />
      )}
      <div className={`relative ${estampar && on ? "pr-estampa" : ""}`}>
        <svg viewBox="0 0 512 512" width={size} height={size} role="img" aria-label={aria} className="block">
          <defs>
            <linearGradient id={`g-${id}`} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0" stopColor="#FFE7A0" />
              <stop offset="1" stopColor="#FFCE3A" />
            </linearGradient>
            <radialGradient id={`r-${id}`}>
              <stop offset="0" stopColor="#FFCE3A" stopOpacity=".5" />
              <stop offset="1" stopColor="#FFCE3A" stopOpacity="0" />
            </radialGradient>
          </defs>
          {on && (
            <>
              <circle cx="256" cy="256" r="250" fill={`url(#r-${id})`} />
              {rayos && <path d={rayos} fill="none" stroke="#33203B" strokeWidth="14" strokeLinecap="round" />}
              {halo && <path d={halo} fill="none" stroke="#33203B" strokeWidth="8" />}
              <path d={ESTRELLA} fill={`url(#g-${id})`} stroke="#FFCE3A" strokeWidth="50" strokeLinejoin="round" paintOrder="stroke fill" />
              {glifoG(1)}
            </>
          )}
          {prog && (
            <>
              <circle cx="256" cy="256" r="240" fill="none" stroke="#33203B" strokeWidth="12" opacity=".1" />
              <circle
                cx="256"
                cy="256"
                r="240"
                fill="none"
                stroke="#E9A800"
                strokeWidth="14"
                strokeLinecap="round"
                strokeDasharray={`${Math.round(1508 * Math.min(1, hecho / Math.max(1, total)))} 1508`}
                transform="rotate(-90 256 256)"
              />
              <g transform="translate(256 256) scale(.8) translate(-256 -256)">
                {halo && <path d={halo} fill="none" stroke="#33203B" strokeWidth="8" opacity=".5" />}
                <path d={ESTRELLA} fill="#FFF6EE" stroke="#33203B" strokeWidth="10" strokeLinejoin="round" opacity=".9" />
                {glifoG(0.75)}
              </g>
            </>
          )}
          {!on && !prog && (
            <>
              {halo && <path d={halo} fill="none" stroke="#33203B" strokeWidth="8" strokeDasharray="14 12" opacity=".3" />}
              <path d={ESTRELLA} fill="none" stroke="#33203B" strokeWidth="10" strokeLinejoin="round" strokeDasharray="16 14" opacity=".34" />
              {glifoG(0.3)}
              <circle cx="416" cy="416" r="60" fill="#FFF6EE" stroke="#33203B" strokeWidth="10" />
              <g transform="translate(389 386) scale(2.25)" fill="none" stroke="#33203B" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <rect x="3" y="11" width="18" height="11" rx="2" />
                <path d="M7 11V7a5 5 0 0 1 10 0v4" />
              </g>
            </>
          )}
        </svg>
        {numero && (
          <div
            aria-hidden
            className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center text-ink"
            style={{ opacity: on ? 1 : prog ? 0.75 : 0.3 }}
          >
            <div className="font-display leading-none font-extrabold" style={{ fontSize: Math.round(size * (on ? 0.23 : 0.19)) }}>
              {numero}
            </div>
            <div className="font-bold tracking-[.1em]" style={{ fontSize: Math.max(6, Math.round(size * 0.058)) }}>
              SETS
            </div>
          </div>
        )}
      </div>
      {prog && (
        <div
          aria-hidden
          className="absolute bottom-0 left-1/2 -translate-x-1/2 rounded-pill bg-ink px-2 py-0.5 font-bold whitespace-nowrap text-crema"
          style={{ fontSize: Math.max(8, Math.round(size * 0.08)) }}
        >
          {hecho} de {total}
        </div>
      )}
    </div>
  );
}
