import { codigoDeBandera, nombreDePais } from "@/lib/paises";

/**
 * Una bandera como imagen (SVG de `flag-icons`), nunca como emoji. Los emojis de bandera son dos
 * «indicadores regionales» (🇬🇧 = G + B) y Windows no trae fuente para ellos: pinta «GB». Pardo,
 * 15/10/2026: «es fundamental que eso se vea bien».
 *
 * <p>Acepta el código ISO del país («CO») o el emoji que manda el catálogo de idiomas («🇫🇷»),
 * que se traduce al código. Sin código válido no pinta nada: una bandera equivocada es peor que
 * ninguna. Decorativa por defecto; con {@code conNombre} lleva el nombre del país para el lector
 * de pantalla.
 */
export function Bandera({
  pais,
  className = "",
  conNombre = false,
}: {
  /** Código ISO 3166-1 alfa-2 («CO», «GB») o un emoji de bandera («🇨🇴»). */
  pais: string | null | undefined;
  className?: string;
  conNombre?: boolean;
}) {
  const codigo = codigoDeBandera(pais);
  if (!codigo) return null;
  return (
    <span
      className={`fi fi-${codigo.toLowerCase()} inline-block shrink-0 rounded-[2px] align-[-0.125em] ${className}`}
      role={conNombre ? "img" : undefined}
      aria-label={conNombre ? nombreDePais(codigo) : undefined}
      aria-hidden={conNombre ? undefined : true}
    />
  );
}

/** «Bogotá, [bandera] Colombia»: la ciudad y el país, como se muestra en perfiles y tarjetas. */
export function CiudadYPais({ city, countryCode }: { city: string | null | undefined; countryCode: string | null | undefined }) {
  const codigo = codigoDeBandera(countryCode);
  const ciudad = city?.trim() ?? "";
  if (!ciudad && !codigo) return null;
  return (
    <>
      {ciudad}
      {ciudad && codigo ? ", " : ""}
      {codigo && (
        <>
          <Bandera pais={codigo} className="mr-1" /> {nombreDePais(codigo)}
        </>
      )}
    </>
  );
}
