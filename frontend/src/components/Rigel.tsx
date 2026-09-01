import type { CSSProperties, ReactNode } from "react";

/**
 * Rigel — la mascota de Orión. Una estrella antropomorfa de cinco puntas (handoff
 * `design_handoff_orion_mascota`). Reglas duras del asset:
 *  - El cuerpo, guantes, piernas y zapatos son idénticos en las cinco poses; solo cambian brazos,
 *    ojos y boca. Nunca se redibuja el personaje.
 *  - Orden de pintado obligatorio: piernas → zapatos → cuerpo → brillo → rubor → ojos → boca →
 *    brazos → guantes → destellos. Dibujar los brazos antes del cuerpo los tapa el stroke de 26 px.
 *  - Dos tonos aprobados; por defecto A · Dorado, con el color como variable (decisión pendiente
 *    del cliente). Nunca recolorear fuera de esos dos.
 *  - Tamaño mínimo 96 px de alto. Nunca como avatar, nunca sobre un formulario, nunca en pantallas
 *    utilitarias (admin, disponibilidad, tablas).
 */

export type RigelPose = "saludo" | "celebracion" | "guia" | "espera" | "animo";
export type RigelTono = "dorado" | "durazno";

const TONOS: Record<RigelTono, Record<string, string>> = {
  dorado: {
    body: "#FFCE3A",
    arm: "#33203B",
    dark: "#2B2430",
    blush: "#FF8E76",
    blushOp: "0.8",
    glove: "#FFFFFF",
    crema: "#FFF6EE",
  },
  durazno: {
    body: "#FFC189",
    arm: "#33203B",
    dark: "#33203B",
    blush: "#E8503A",
    blushOp: "0.55",
    glove: "#FFF6EE",
    crema: "#FFF6EE",
  },
};

const ETIQUETA: Record<RigelPose, string> = {
  saludo: "Rigel, la mascota de Orión, saludando",
  celebracion: "Rigel, la mascota de Orión, celebrando",
  guia: "Rigel, la mascota de Orión, señalando",
  espera: "Rigel, la mascota de Orión, esperando",
  animo: "Rigel, la mascota de Orión, animándote",
};

export function Rigel({
  pose = "saludo",
  tono = "dorado",
  className = "",
  decorativo = false,
}: {
  pose?: RigelPose;
  tono?: RigelTono;
  className?: string;
  /** Cuando el mensaje ya está en el texto de al lado, se oculta a lectores de pantalla. */
  decorativo?: boolean;
}) {
  const t = TONOS[tono];
  const vars = {
    "--rg-body": t.body,
    "--rg-arm": t.arm,
    "--rg-dark": t.dark,
    "--rg-glove": t.glove,
    "--rg-crema": t.crema,
  } as CSSProperties;

  return (
    <svg
      viewBox="0 0 200 210"
      className={`rigel-bob ${className}`}
      style={vars}
      role={decorativo ? undefined : "img"}
      aria-label={decorativo ? undefined : ETIQUETA[pose]}
      aria-hidden={decorativo || undefined}
    >
      {/* piernas y zapatos */}
      <g stroke="var(--rg-arm)" strokeWidth={7} strokeLinecap="round">
        <line x1={86} y1={142} x2={83} y2={167} />
        <line x1={114} y1={142} x2={117} y2={167} />
      </g>
      <ellipse cx={75} cy={176} rx={21} ry={11} fill="var(--rg-dark)" />
      <ellipse cx={125} cy={176} rx={21} ry={11} fill="var(--rg-dark)" />

      {/* cuerpo: el stroke de 26 px del mismo color redondea las puntas */}
      <polygon
        points="100,36 115.3,75 157.1,77.5 124.7,104 135.3,144.5 100,122 64.7,144.5 75.3,104 42.9,77.5 84.7,75"
        fill="var(--rg-body)"
        stroke="var(--rg-body)"
        strokeWidth={26}
        strokeLinejoin="round"
        paintOrder="stroke fill"
      />
      <ellipse cx={80} cy={62} rx={17} ry={9} fill="var(--rg-crema)" opacity={0.45} transform="rotate(-32 80 62)" />

      {/* rubor */}
      <ellipse cx={70} cy={106} rx={10} ry={6.5} fill="var(--rg-blush)" opacity={t.blushOp} />
      <ellipse cx={130} cy={106} rx={10} ry={6.5} fill="var(--rg-blush)" opacity={t.blushOp} />

      {/* ojos, boca, brazos y extras cambian por pose */}
      <Ojos pose={pose} crema={t.crema} />
      <Boca pose={pose} />
      <Brazos pose={pose} />
      <Extras pose={pose} />
    </svg>
  );
}

/* ---- Ojos ---- */

function Ojos({ pose, crema }: { pose: RigelPose; crema: string }) {
  if (pose === "celebracion") {
    // Arcos felices.
    return (
      <g stroke="var(--rg-dark)" strokeWidth={5} strokeLinecap="round" fill="none">
        <path d="M77 93 Q85 82 93 93" />
        <path d="M107 93 Q115 82 123 93" />
      </g>
    );
  }
  if (pose === "espera") {
    // Ojos cerrados (arcos hacia abajo).
    return (
      <g stroke="var(--rg-dark)" strokeWidth={5} strokeLinecap="round" fill="none">
        <path d="M76 91 Q85 98 94 91" />
        <path d="M106 91 Q115 98 124 91" />
      </g>
    );
  }
  // Ojos abiertos. En "guía" las pupilas miran a la derecha (+3).
  const dx = pose === "guia" ? 3 : 0;
  return (
    <g className="rigel-eyes">
      <circle cx={85 + dx} cy={91} r={10} fill="var(--rg-dark)" />
      <circle cx={115 + dx} cy={91} r={10} fill="var(--rg-dark)" />
      <circle cx={81.5 + dx} cy={87} r={3.4} fill={crema} />
      <circle cx={111.5 + dx} cy={87} r={3.4} fill={crema} />
      <path
        d={`M${86.5 + dx} 91.5l1.1 2.6 2.8.2-2.1 1.8.6 2.7-2.4-1.5-2.4 1.5.6-2.7-2.1-1.8 2.8-.2z`}
        fill={crema}
      />
      <path
        d={`M${116.5 + dx} 91.5l1.1 2.6 2.8.2-2.1 1.8.6 2.7-2.4-1.5-2.4 1.5.6-2.7-2.1-1.8 2.8-.2z`}
        fill={crema}
      />
    </g>
  );
}

/* ---- Boca ---- */

function Boca({ pose }: { pose: RigelPose }) {
  if (pose === "celebracion") {
    return (
      <>
        <path d="M83,100 Q100,132 117,100 Q100,111 83,100 Z" fill="#5A2436" />
        <path d="M92,113 Q100,125 108,113 Q100,118 92,113 Z" fill="#FF8FA3" />
      </>
    );
  }
  if (pose === "guia") {
    return (
      <>
        <path d="M90,102 Q100,117 110,102 Q100,107 90,102 Z" fill="#5A2436" />
        <path d="M95,109 Q100,114 105,109 Q100,111 95,109 Z" fill="#FF8FA3" />
      </>
    );
  }
  if (pose === "espera") {
    // Línea suave.
    return <path d="M90,108 Q100,113 110,108" stroke="#5A2436" strokeWidth={4} strokeLinecap="round" fill="none" />;
  }
  if (pose === "animo") {
    // Sonrisa cerrada, sin boca abierta.
    return <path d="M85,104 Q100,119 115,104" stroke="#5A2436" strokeWidth={5} strokeLinecap="round" fill="none" />;
  }
  // saludo (base)
  return (
    <>
      <path d="M86,101 Q100,125 114,101 Q100,108 86,101 Z" fill="#5A2436" />
      <path d="M93,112 Q100,122 107,112 Q100,116 93,112 Z" fill="#FF8FA3" />
    </>
  );
}

/* ---- Guante reutilizable: círculo principal + pulgar + costuras + puño ---- */

function Guante({
  cx,
  cy,
  r = 12,
  tx,
  ty,
  tr = 5,
  children,
}: {
  cx: number;
  cy: number;
  r?: number;
  tx: number;
  ty: number;
  tr?: number;
  children?: ReactNode;
}) {
  return (
    <>
      {children}
      <circle cx={tx} cy={ty} r={tr} fill="var(--rg-glove)" stroke="var(--rg-arm)" strokeWidth={3} />
      <circle cx={cx} cy={cy} r={r} fill="var(--rg-glove)" stroke="var(--rg-arm)" strokeWidth={3} />
      <g stroke="var(--rg-arm)" strokeWidth={2} strokeLinecap="round" fill="none">
        <line x1={cx - 4} y1={cy - 6} x2={cx - 4} y2={cy + 2} />
        <line x1={cx} y1={cy - 7} x2={cx} y2={cy + 2} />
        <line x1={cx + 4} y1={cy - 6} x2={cx + 4} y2={cy + 1} />
        <path d={`M${cx - 8},${cy + 7} Q${cx},${cy + 12} ${cx + 8},${cy + 6}`} strokeWidth={2.4} />
      </g>
    </>
  );
}

/** Brazo izquierdo en reposo — literal del asset base, compartido por varias poses. */
function BrazoIzquierdoReposo() {
  return (
    <>
      <path d="M60,118 L37,139" stroke="var(--rg-arm)" strokeWidth={7} strokeLinecap="round" fill="none" />
      <Guante cx={34} cy={143} r={12} tx={25} ty={152} tr={5} />
    </>
  );
}

/* ---- Brazos por pose ---- */

function Brazos({ pose }: { pose: RigelPose }) {
  if (pose === "celebracion") {
    return (
      <>
        <path d="M58,96 L32,70" stroke="var(--rg-arm)" strokeWidth={7} strokeLinecap="round" fill="none" />
        <Guante cx={28} cy={64} r={12} tx={20} ty={71} tr={5} />
        <path d="M142,96 L168,70" stroke="var(--rg-arm)" strokeWidth={7} strokeLinecap="round" fill="none" />
        <Guante cx={172} cy={64} r={12} tx={180} ty={71} tr={5} />
      </>
    );
  }

  if (pose === "guia") {
    return (
      <>
        <BrazoIzquierdoReposo />
        <path d="M148,96 L175,93" stroke="var(--rg-arm)" strokeWidth={7} strokeLinecap="round" fill="none" />
        {/* dedo índice extendido */}
        <line x1={185} y1={93} x2={197} y2={93} stroke="var(--rg-arm)" strokeWidth={12} strokeLinecap="round" />
        <line x1={185} y1={93} x2={196} y2={93} stroke="var(--rg-glove)" strokeWidth={8} strokeLinecap="round" />
        <Guante cx={180} cy={93} r={12} tx={178} ty={102} tr={5} />
      </>
    );
  }

  if (pose === "espera") {
    return (
      <>
        <path d="M60,118 L46,150" stroke="var(--rg-arm)" strokeWidth={7} strokeLinecap="round" fill="none" />
        <Guante cx={42} cy={158} r={12} tx={34} ty={163} tr={5} />
        <path d="M140,118 L154,150" stroke="var(--rg-arm)" strokeWidth={7} strokeLinecap="round" fill="none" />
        <Guante cx={158} cy={158} r={12} tx={166} ty={163} tr={5} />
      </>
    );
  }

  if (pose === "animo") {
    return (
      <>
        <BrazoIzquierdoReposo />
        <path d="M140,100 L154,84" stroke="var(--rg-arm)" strokeWidth={7} strokeLinecap="round" fill="none" />
        {/* pulgar extendido hacia arriba */}
        <line x1={158} y1={74} x2={158} y2={60} stroke="var(--rg-arm)" strokeWidth={12} strokeLinecap="round" />
        <line x1={158} y1={73} x2={158} y2={61} stroke="var(--rg-glove)" strokeWidth={8} strokeLinecap="round" />
        <Guante cx={158} cy={82} r={12} tx={150} ty={86} tr={5} />
      </>
    );
  }

  // saludo (base): brazo derecho levantado (saluda) + izquierdo en reposo
  return (
    <>
      <g className="rigel-wave">
        <path d="M144,94 L166,71" stroke="var(--rg-arm)" strokeWidth={7} strokeLinecap="round" fill="none" />
        <circle cx={181} cy={77} r={5.5} fill="var(--rg-glove)" stroke="var(--rg-arm)" strokeWidth={3} />
        <circle cx={172} cy={66} r={13} fill="var(--rg-glove)" stroke="var(--rg-arm)" strokeWidth={3} />
        <g stroke="var(--rg-arm)" strokeWidth={2} strokeLinecap="round" fill="none">
          <line x1={168} y1={60} x2={168} y2={68} />
          <line x1={172} y1={59} x2={172} y2={68} />
          <line x1={176} y1={60} x2={176} y2={67} />
          <path d="M163,73 Q172,79 181,72" strokeWidth={2.4} />
        </g>
      </g>
      <BrazoIzquierdoReposo />
    </>
  );
}

/* ---- Destellos (celebración) y «z» de sueño (espera) ---- */

function Extras({ pose }: { pose: RigelPose }) {
  if (pose === "celebracion") {
    return (
      <g fill="var(--color-accent-peach)">
        <Destello cx={38} cy={44} r={7} delay="0s" />
        <Destello cx={165} cy={40} r={6} delay="0.7s" />
        <Destello cx={150} cy={150} r={5} delay="1.3s" />
      </g>
    );
  }
  if (pose === "espera") {
    return (
      <g fill="#B9A7E6" fontFamily="var(--font-display, sans-serif)" fontWeight={800}>
        <text x={150} y={52} fontSize={18} className="rigel-sparkle">
          z
        </text>
        <text x={166} y={38} fontSize={12} className="rigel-sparkle" style={{ animationDelay: "0.8s" }}>
          z
        </text>
      </g>
    );
  }
  return null;
}

/** Estrellita de destello de 4 puntas. */
function Destello({ cx, cy, r, delay }: { cx: number; cy: number; r: number; delay: string }) {
  const d = `M${cx},${cy - r} Q${cx + r * 0.25},${cy - r * 0.25} ${cx + r},${cy} Q${cx + r * 0.25},${cy + r * 0.25} ${cx},${cy + r} Q${cx - r * 0.25},${cy + r * 0.25} ${cx - r},${cy} Q${cx - r * 0.25},${cy - r * 0.25} ${cx},${cy - r} Z`;
  return <path className="rigel-sparkle" d={d} style={{ animationDelay: delay }} />;
}
