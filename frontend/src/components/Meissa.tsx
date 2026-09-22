/**
 * Meissa — la voz del diagnóstico (handoff `design_handoff_orion_meissa`). Destello de cuatro
 * puntas que flota sobre un aura. Reglas duras del asset:
 *  - El SVG es el del handoff, literal. Cuerpo, brazos y guantes son idénticos en los cuatro
 *    estados; solo cambian ojos, boca y lo que pasa alrededor del aura.
 *  - Orden de pintado obligatorio: aura → cuerpo → brillo → mejillas → ojos → boca → brazos →
 *    guantes → costuras → indicadores (barras, puntos, destello).
 *  - Solo en el flujo del diagnóstico. Nunca junto a Rigel, nunca celebra ni saluda, nunca «bob»:
 *    su movimiento es el aura.
 *  - Mínimo 120 px de alto: el aura necesita aire.
 */

export type MeissaEstado = "escucha" | "habla" | "piensa" | "cierre";

const ETIQUETA: Record<MeissaEstado, string> = {
  escucha: "Meissa te escucha",
  habla: "Meissa está hablando",
  piensa: "Meissa piensa",
  cierre: "Meissa cierra",
};

const TINTA = "#33203B";
const CREMA = "#FFF6EE";

export function Meissa({
  estado = "escucha",
  sobreAmanecer = false,
  className = "",
  decorativo = false,
}: {
  estado?: MeissaEstado;
  /** Sobre el degradado del amanecer, el aura y las barras van en crema. */
  sobreAmanecer?: boolean;
  className?: string;
  /** Cuando la línea de estado ya lo dice al lado, se oculta a lectores de pantalla. */
  decorativo?: boolean;
}) {
  const aura = sobreAmanecer ? CREMA : "#B9A7E6";
  const voz = sobreAmanecer ? CREMA : "#7A4A8C";
  const mejillas = estado === "cierre" ? 0.85 : 0.75;

  return (
    <svg
      viewBox="0 0 200 210"
      className={className}
      role={decorativo ? undefined : "img"}
      aria-label={decorativo ? undefined : ETIQUETA[estado]}
      aria-hidden={decorativo ? true : undefined}
    >
      <g className={estado === "habla" ? "meissa-aura meissa-aura-habla" : "meissa-aura"}>
        <circle cx="100" cy="100" r="90" fill={aura} opacity={sobreAmanecer ? 0.12 : 0.14} />
        <circle cx="100" cy="100" r="74" fill={aura} opacity={sobreAmanecer ? 0.12 : 0.14} />
      </g>
      <path
        d="M100,26 Q118,82 174,100 Q118,118 100,174 Q82,118 26,100 Q82,82 100,26 Z"
        fill="#C9B9F4"
        stroke="#C9B9F4"
        strokeWidth="18"
        strokeLinejoin="round"
        paintOrder="stroke fill"
      />
      <ellipse cx="86" cy="58" rx="5" ry="13" fill={CREMA} opacity=".55" transform="rotate(24 86 58)" />
      <ellipse cx="78" cy="108" rx="8" ry="5.5" fill="#F0A5B9" opacity={mejillas} />
      <ellipse cx="122" cy="108" rx="8" ry="5.5" fill="#F0A5B9" opacity={mejillas} />

      <Ojos estado={estado} />
      <Boca estado={estado} />

      <path d="M68,120 L52,132" stroke={TINTA} strokeWidth="6" strokeLinecap="round" fill="none" />
      <circle cx="46" cy="137" r="11" fill="#FFFFFF" stroke={TINTA} strokeWidth="3" />
      <g stroke={TINTA} strokeWidth="2" strokeLinecap="round">
        <line x1="42" y1="132" x2="42" y2="139" />
        <line x1="46" y1="131" x2="46" y2="139" />
        <line x1="50" y1="132" x2="50" y2="138" />
      </g>
      <path d="M132,120 L148,132" stroke={TINTA} strokeWidth="6" strokeLinecap="round" fill="none" />
      <circle cx="154" cy="137" r="11" fill="#FFFFFF" stroke={TINTA} strokeWidth="3" />
      <g stroke={TINTA} strokeWidth="2" strokeLinecap="round">
        <line x1="150" y1="132" x2="150" y2="139" />
        <line x1="154" y1="131" x2="154" y2="139" />
        <line x1="158" y1="132" x2="158" y2="138" />
      </g>

      {estado === "habla" && (
        <g stroke={voz} strokeWidth="5" strokeLinecap="round">
          <line className="meissa-bar" x1="176" y1="66" x2="176" y2="82" style={{ transformOrigin: "176px 74px" }} />
          <line className="meissa-bar" x1="186" y1="58" x2="186" y2="90" style={{ transformOrigin: "186px 74px", animationDelay: ".15s" }} />
          <line className="meissa-bar" x1="196" y1="64" x2="196" y2="84" style={{ transformOrigin: "196px 74px", animationDelay: ".3s" }} />
        </g>
      )}
      {estado === "piensa" && (
        <g fill={voz}>
          <circle className="meissa-dot" cx="150" cy="50" r="3.2" />
          <circle className="meissa-dot" cx="162" cy="40" r="4.2" style={{ animationDelay: ".3s" }} />
          <circle className="meissa-dot" cx="176" cy="28" r="5.4" style={{ animationDelay: ".6s" }} />
        </g>
      )}
      {estado === "cierre" && (
        <path
          className="meissa-sparkle"
          d="M162 38l1.6 4 4 1.6-4 1.6-1.6 4-1.6-4-4-1.6 4-1.6z"
          fill="#FFC189"
        />
      )}
    </svg>
  );
}

function Ojos({ estado }: { estado: MeissaEstado }) {
  if (estado === "cierre") {
    return (
      <>
        <path d="M80,98 Q88,89 96,98" stroke={TINTA} strokeWidth="4.5" strokeLinecap="round" fill="none" />
        <path d="M104,98 Q112,89 120,98" stroke={TINTA} strokeWidth="4.5" strokeLinecap="round" fill="none" />
      </>
    );
  }
  if (estado === "piensa") {
    // Mirada arriba a la derecha: está buscando, no juzgando.
    return (
      <g>
        <circle cx="91" cy="93" r="8.5" fill={TINTA} />
        <circle cx="115" cy="93" r="8.5" fill={TINTA} />
        <circle cx="89" cy="90" r="3" fill={CREMA} />
        <circle cx="113" cy="90" r="3" fill={CREMA} />
      </g>
    );
  }
  return (
    <g className="meissa-eyes">
      <circle cx="88" cy="96" r="8.5" fill={TINTA} />
      <circle cx="112" cy="96" r="8.5" fill={TINTA} />
      <circle cx="85" cy="93" r="3" fill={CREMA} />
      <circle cx="109" cy="93" r="3" fill={CREMA} />
    </g>
  );
}

function Boca({ estado }: { estado: MeissaEstado }) {
  switch (estado) {
    case "habla":
      // Abierta y fija: no hay lip-sync. Lo que se lee es el subtítulo.
      return (
        <>
          <path d="M90,108 Q100,126 110,108 Q100,113 90,108 Z" fill="#5A2436" />
          <path d="M95,117 Q100,122 105,117 Q100,119 95,117 Z" fill="#FF8FA3" />
        </>
      );
    case "piensa":
      return <circle cx="100" cy="113" r="3.6" fill="#5A2436" />;
    case "cierre":
      return <path d="M90,109 Q100,121 110,109" stroke="#5A2436" strokeWidth="4.5" strokeLinecap="round" fill="none" />;
    default:
      return <path d="M92,110 Q100,118 108,110" stroke="#5A2436" strokeWidth="4.5" strokeLinecap="round" fill="none" />;
  }
}
