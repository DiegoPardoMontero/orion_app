/**
 * Los países del mundo para elegir de una lista (24/09/2026: «el país, de una lista desplegable, con
 * su emoji»). El nombre sale de `Intl.DisplayNames` en español y la bandera de los dos indicadores
 * regionales del código: no hay que mantener doscientos nombres a mano. Se guarda el código ISO
 * de dos letras, como siempre.
 */

// ISO 3166-1 alfa-2, los que tienen bandera como emoji.
const CODIGOS =
  "AD AE AF AG AI AL AM AO AR AS AT AU AW AX AZ BA BB BD BE BF BG BH BI BJ BL BM BN BO BQ BR BS BT BW BY BZ CA CD CF CG CH CI CK CL CM CN CO CR CU CV CW CY CZ DE DJ DK DM DO DZ EC EE EG ER ES ET FI FJ FK FM FO FR GA GB GD GE GF GG GH GI GL GM GN GP GQ GR GT GU GW GY HK HN HR HT HU ID IE IL IM IN IQ IR IS IT JE JM JO JP KE KG KH KI KM KN KP KR KW KY KZ LA LB LC LI LK LR LS LT LU LV LY MA MC MD ME MF MG MH MK ML MM MN MO MP MQ MR MS MT MU MV MW MX MY MZ NA NC NE NF NG NI NL NO NP NR NU NZ OM PA PE PF PG PH PK PL PM PR PS PT PW PY QA RE RO RS RU RW SA SB SC SD SE SG SH SI SK SL SM SN SO SR SS ST SV SX SY SZ TC TD TG TH TJ TK TL TM TN TO TR TT TV TW TZ UA UG US UY UZ VA VC VE VG VI VN VU WF WS XK YE YT ZA ZM ZW".split(
    " ",
  );

/** Los que más van a elegir los profesores de Orión, arriba de la lista. */
const PRIMERO = ["CO", "US", "GB", "CA", "ES", "MX", "VE", "AR", "PE", "CL", "EC"];

export type Pais = { code: string; nombre: string; bandera: string };

/** 🇨🇴 a partir de «CO»: cada letra es un indicador regional. */
export function banderaDe(code: string): string {
  if (!/^[A-Z]{2}$/.test(code)) return "";
  return String.fromCodePoint(...[...code].map((c) => 0x1f1e6 + c.charCodeAt(0) - 65));
}

let nombres: Intl.DisplayNames | null = null;

/** «Colombia», o el código tal cual si el navegador no sabe nombrarlo. */
export function nombreDePais(code: string): string {
  try {
    nombres ??= new Intl.DisplayNames(["es"], { type: "region" });
    return nombres.of(code) ?? code;
  } catch {
    return code;
  }
}

/** «🇨🇴 Colombia», para mostrarlo junto a la ciudad. Vacío si no hay código. */
export function paisConBandera(code: string | null | undefined): string {
  if (!code) return "";
  const c = code.toUpperCase();
  return `${banderaDe(c)} ${nombreDePais(c)}`.trim();
}

let lista: Pais[] | null = null;

/** Los frecuentes primero y después todos en orden alfabético. */
export function paises(): Pais[] {
  if (lista) return lista;
  const todos = CODIGOS.map((code) => ({ code, nombre: nombreDePais(code), bandera: banderaDe(code) }));
  const frecuentes = PRIMERO.map((c) => todos.find((p) => p.code === c)!).filter(Boolean);
  const resto = todos
    .filter((p) => !PRIMERO.includes(p.code))
    .sort((a, b) => a.nombre.localeCompare(b.nombre, "es"));
  lista = [...frecuentes, ...resto];
  return lista;
}

export const FRECUENTES = PRIMERO.length;
