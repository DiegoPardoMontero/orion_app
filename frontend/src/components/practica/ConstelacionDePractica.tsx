/**
 * El progreso de un set como una constelación: una estrella por ejercicio, que se enciende al
 * cerrarlo. Completo, se dibujan las líneas entre ellas. Orión es una constelación, y cada práctica
 * terminada es una más en el cielo del estudiante.
 *
 * <p>Las estrellas van en zigzag, como una constelación y no como una barra de progreso: arriba y
 * abajo, con un leve desorden fijo para que no parezca una gráfica.
 */
const ALTURAS = [62, 26, 50, 18, 56, 30, 60];

export function ConstelacionDePractica({
  total,
  encendidas,
  completa = false,
  className = "",
}: {
  total: number;
  encendidas: number;
  completa?: boolean;
  className?: string;
}) {
  const ancho = 40 + Math.max(0, total - 1) * 48;
  const puntos = Array.from({ length: total }, (_, i) => ({ x: 20 + i * 48, y: ALTURAS[i % ALTURAS.length] }));
  return (
    <svg
      viewBox={`0 0 ${ancho} 80`}
      className={className}
      role="img"
      aria-label={completa ? "Constelación completa" : `${encendidas} de ${total} estrellas encendidas`}
    >
      {completa &&
        puntos.slice(1).map((p, i) => (
          <line
            key={i}
            x1={puntos[i].x}
            y1={puntos[i].y}
            x2={p.x}
            y2={p.y}
            stroke="#FFC189"
            strokeWidth={1.6}
            strokeLinecap="round"
            className="constelacion-linea"
            style={{ animationDelay: `${i * 120}ms` }}
          />
        ))}
      {puntos.map((p, i) => {
        const encendida = i < encendidas || completa;
        return (
          <g key={i} className={encendida ? "constelacion-estrella" : undefined}>
            {encendida && <circle cx={p.x} cy={p.y} r={11} fill="#FFC189" opacity={0.28} />}
            <path
              d={estrella(p.x, p.y, encendida ? 8 : 6)}
              fill={encendida ? "#FFC189" : "none"}
              stroke={encendida ? "#E8503A" : "#B9A7E6"}
              strokeWidth={1.4}
              strokeLinejoin="round"
            />
          </g>
        );
      })}
    </svg>
  );
}

/** Una estrella de cinco puntas centrada en (x, y). */
function estrella(x: number, y: number, radio: number): string {
  const puntos: string[] = [];
  for (let i = 0; i < 10; i++) {
    const r = i % 2 === 0 ? radio : radio * 0.45;
    const a = -Math.PI / 2 + (i * Math.PI) / 5;
    puntos.push(`${(x + r * Math.cos(a)).toFixed(1)},${(y + r * Math.sin(a)).toFixed(1)}`);
  }
  return `M${puntos.join("L")}Z`;
}
