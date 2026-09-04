/**
 * El disco de idioma del contrato visual (§2h): un círculo con el código ISO 639-1 dentro, en
 * Bricolage 800. Sin banderas — las banderas no se renderizan en Windows y además un idioma no
 * es un país.
 *
 * El diseño lo entrega como tres archivos estáticos. Va como componente porque un disco es un
 * color y dos letras: con archivos, añadir alemán sería exportar un SVG más, que es justo lo que
 * el propio brief evita en los logros. La regla de color del diseño se respeta tal cual, incluido
 * el ciruela reservado para el siguiente idioma.
 */
const COLOR: Record<string, string> = {
  EN: "var(--color-language-en)",
  FR: "var(--color-language-fr)",
  ES: "var(--color-language-es)",
  DE: "#7A4A8C",
};

/** Cualquier idioma que llegue sin color asignado usa el ciruela del siguiente de la lista. */
const SIGUIENTE = "#7A4A8C";

export function DiscoIdioma({
  code,
  size = 20,
  className = "",
}: {
  code: string;
  size?: number;
  className?: string;
}) {
  const codigo = code.toUpperCase().slice(0, 2);
  const fondo = COLOR[codigo] ?? SIGUIENTE;

  return (
    <svg
      viewBox="0 0 512 512"
      width={size}
      height={size}
      className={`shrink-0 ${className}`}
      role="img"
      aria-label={codigo}
    >
      <circle cx={256} cy={256} r={256} fill={fondo} />
      <text
        x={256}
        y={256}
        textAnchor="middle"
        dominantBaseline="central"
        fontFamily="var(--font-display, 'Bricolage Grotesque', sans-serif)"
        fontWeight={800}
        fontSize={190}
        letterSpacing={-6}
        fill="var(--color-surface)"
      >
        {codigo}
      </text>
    </svg>
  );
}
