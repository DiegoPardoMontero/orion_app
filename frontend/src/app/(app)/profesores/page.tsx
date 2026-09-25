"use client";

import { useInfiniteQuery, useQuery } from "@tanstack/react-query";
import { BadgeCheck, ChevronDown, ChevronRight, MapPin, SlidersHorizontal } from "lucide-react";
import Link from "next/link";
import { useEffect, useMemo, useState, type ReactNode } from "react";
import { Avatar } from "@/components/Avatar";
import { Cargando, ErrorCarga, Vacio } from "@/components/estados";
import { Modal } from "@/components/Modal";
import { EstrellaRating } from "@/components/Rating";
import { Boton, Segmento, Toggle } from "@/components/ui";
import { DiscoIdioma } from "@/components/DiscoIdioma";
import { apiFetch } from "@/lib/api/fetch";
import type {
  GoalResponse,
  LanguageResponse,
  PagedProfessors,
  ProfessorCard,
} from "@/lib/api/types";
import { esGratis, tarifaClase } from "@/lib/format";
import { etiquetaNivel, NIVELES, t } from "@/lib/i18n";
import { useMediaQuery } from "@/lib/useMediaQuery";
import { useMe } from "@/lib/auth/session";
import { Rigel } from "@/components/Rigel";
import { paisConBandera } from "@/lib/paises";
import { RecordatorioDePractica } from "@/components/InvitacionAPracticar";

type Orden = "RELEVANCE" | "PRICE_ASC" | "PRICE_DESC";

type Filtros = {
  language: string | null;
  goals: string[];
  levels: string[];
  minPrice: string;
  maxPrice: string;
  native: boolean;
  certified: boolean;
  /** Días de la semana, como los nombra java.time: MONDAY, TUESDAY… */
  days: string[];
  /**
   * Horas de inicio de la clase, en hora de Bogotá (7 = 7:00 AM). Varias a la vez (24/09/2026:
   * «filtrar exactamente por horas y seleccionar varias»); antes era una sola franja.
   */
  hours: number[];
  sort: Orden;
};

type Franja = "MORNING" | "AFTERNOON" | "EVENING";

/**
 * Las horas de inicio que se pueden pedir, agrupadas en franjas. Las franjas ya no filtran: son un
 * atajo que marca (o desmarca) todas sus horas de una vez. La portada y las páginas por idioma
 * siguen mandando `schedule=MORNING`, y eso se traduce a estas mismas horas.
 */
const FRANJAS: { clave: Franja; etiqueta: string; horas: number[] }[] = [
  { clave: "MORNING", etiqueta: "Mañana", horas: [5, 6, 7, 8, 9, 10, 11] },
  { clave: "AFTERNOON", etiqueta: "Tarde", horas: [12, 13, 14, 15, 16, 17] },
  { clave: "EVENING", etiqueta: "Noche", horas: [18, 19, 20, 21, 22, 23] },
];

/** «7 AM», «12 PM», «9 PM»: como el resto de la app. */
function etiquetaHora(h: number): string {
  return `${((h + 11) % 12) + 1} ${h < 12 ? "AM" : "PM"}`;
}

/** Las horas de una franja que viene en la URL (`schedule=`); las de siempre, sin las de madrugada. */
function horasDeLaFranja(clave: string | null): number[] {
  if (clave === "MORNING") return [6, 7, 8, 9, 10, 11];
  if (clave === "AFTERNOON") return [12, 13, 14, 15, 16, 17];
  if (clave === "EVENING") return [18, 19, 20, 21];
  return [];
}

const DIAS: { valor: string; corta: string }[] = [
  { valor: "MONDAY", corta: "L" },
  { valor: "TUESDAY", corta: "M" },
  { valor: "WEDNESDAY", corta: "X" },
  { valor: "THURSDAY", corta: "J" },
  { valor: "FRIDAY", corta: "V" },
  { valor: "SATURDAY", corta: "S" },
  { valor: "SUNDAY", corta: "D" },
];

const FILTROS_INICIALES: Filtros = {
  language: null,
  goals: [],
  levels: [],
  minPrice: "",
  maxPrice: "",
  native: false,
  certified: false,
  days: [],
  hours: [],
  sort: "RELEVANCE",
};

const TAM_PAGINA = 12;

/** Cuenta cuántos filtros están activos, para el contador del botón "Filtros" en móvil. */
function contarActivos(f: Filtros): number {
  return (
    (f.language ? 1 : 0) +
    f.goals.length +
    f.levels.length +
    (f.minPrice ? 1 : 0) +
    (f.maxPrice ? 1 : 0) +
    (f.native ? 1 : 0) +
    (f.certified ? 1 : 0) +
    f.days.length +
    (f.hours.length > 0 ? 1 : 0)
  );
}

/** Traduce los filtros a querystring del backend (level y goal son repetibles). */
function construirQs(f: Filtros, page: number): string {
  const p = new URLSearchParams();
  if (f.language) p.set("language", f.language);
  for (const level of f.levels) p.append("level", level);
  for (const goal of f.goals) p.append("goal", goal);
  if (f.minPrice) p.set("minPrice", f.minPrice);
  if (f.maxPrice) p.set("maxPrice", f.maxPrice);
  if (f.native) p.set("native", "true");
  if (f.certified) p.set("certified", "true");
  for (const day of f.days) p.append("day", day);
  for (const h of [...f.hours].sort((a, b) => a - b)) p.append("hour", `${String(h).padStart(2, "0")}:00`);
  if (f.sort !== "RELEVANCE") p.set("sort", f.sort);
  p.set("page", String(page));
  p.set("size", String(TAM_PAGINA));
  return p.toString();
}

export default function ProfesoresPage() {
  const esDesktop = useMediaQuery("(min-width: 1024px)");
  // El catálogo se ve también sin cuenta: lo de la práctica solo aplica a un estudiante.
  const { data: me } = useMe();
  const [filtros, setFiltros] = useState<Filtros>(FILTROS_INICIALES);
  const [hojaAbierta, setHojaAbierta] = useState(false);

  // Filtros iniciales desde la URL (el buscador de la portada y las landings por idioma enlazan aquí
  // con ?language=&goal=&level=&schedule=&day=; «Fin de semana» llega como sábado y domingo). Se lee una sola vez al montar, en cliente, para no chocar
  // con la hidratación. `schedule` llevaba tiempo viajando sin que nadie lo recogiera, porque el
  // backend no filtraba por franja horaria; ahora sí, y por fin significa algo.
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const language = params.get("language");
    const goals = params.getAll("goal").filter(Boolean);
    const levels = params.getAll("level").filter(Boolean);
    const days = params.getAll("day").filter((d) => DIAS.some((dia) => dia.valor === d));
    // `hour=7` (o `07:00`) repetible, y la franja de la portada traducida a sus horas.
    const pedidas = params
      .getAll("hour")
      .map((h) => Number.parseInt(h, 10))
      .filter((h) => Number.isInteger(h) && h >= 0 && h <= 23);
    const hours = [...new Set([...pedidas, ...horasDeLaFranja(params.get("schedule"))])];
    if (!language && goals.length === 0 && levels.length === 0 && hours.length === 0 && days.length === 0) return;
    // Siembra única desde la URL al montar; a partir de aquí manda el usuario. El setState en el
    // efecto es deliberado (sincronizar con un sistema externo: la query string) y solo corre una vez.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setFiltros((prev) => ({
      ...prev,
      language: language ?? prev.language,
      goals: goals.length > 0 ? goals : prev.goals,
      levels: levels.length > 0 ? levels : prev.levels,
      hours: hours.length > 0 ? hours : prev.hours,
      days: days.length > 0 ? days : prev.days,
    }));
  }, []);

  const languages = useQuery({
    queryKey: ["catalog", "languages"],
    queryFn: () => apiFetch<LanguageResponse[]>("/api/v1/catalog/languages"),
    staleTime: 5 * 60_000,
  });

  const goals = useQuery({
    queryKey: ["catalog", "goals"],
    queryFn: () => apiFetch<GoalResponse[]>("/api/v1/catalog/goals"),
    staleTime: 5 * 60_000,
  });

  const profesores = useInfiniteQuery({
    queryKey: ["professors", filtros],
    queryFn: ({ pageParam }) =>
      apiFetch<PagedProfessors>(`/api/v1/professors?${construirQs(filtros, pageParam)}`),
    initialPageParam: 0,
    getNextPageParam: (ultima) => {
      const siguiente = (ultima.page ?? 0) + 1;
      return siguiente < (ultima.totalPages ?? 0) ? siguiente : undefined;
    },
  });

  const listado = useMemo(
    () => profesores.data?.pages.flatMap((p) => p.content ?? []) ?? [],
    [profesores.data],
  );
  const total = profesores.data?.pages[0]?.totalElements ?? 0;
  const activos = contarActivos(filtros);

  const panel = (
    <PanelFiltros
      filtros={filtros}
      onCambio={setFiltros}
      languages={languages.data ?? []}
      goals={goals.data ?? []}
    />
  );

  const barraFiltros = (
    <PanelFiltros
      horizontal
      filtros={filtros}
      onCambio={setFiltros}
      languages={languages.data ?? []}
      goals={goals.data ?? []}
    />
  );

  return (
    <main className="mx-auto w-full max-w-md px-5 py-6 lg:max-w-6xl lg:px-12 lg:py-8">
      {/*
        Un banner ancho y bajo: ocupa todo el horizontal para que se lea de un vistazo, y poco
        vertical para no empujar a los profesores fuera de la pantalla, que es a lo que se viene.
        Rigel a la derecha, recortado por el borde inferior, sin robar altura.
      */}
      <section className="gradient-dawn relative overflow-hidden rounded-card px-5 py-5 sm:px-7">
        <div className="relative flex items-center gap-4">
          <div className="min-w-0 flex-1">
            <h1 className="font-display text-[24px] font-bold leading-tight text-on-primary sm:text-[28px]">
              Tu próxima clase empieza hoy.
            </h1>
            <p className="mt-1.5 max-w-[46ch] text-[13.5px] leading-relaxed text-on-primary/85">
              El profesor correcto no es el más caro ni el más titulado: es con el que te atreves a
              hablar. Aquí están todos, con su agenda real.
            </p>
          </div>
          <Rigel
            pose="animo"
            decorativo
            className="-mb-5 hidden h-[104px] w-auto shrink-0 self-end drop-shadow-xl sm:block"
          />
        </div>
      </section>

      {/* La lista necesita su propio encabezado: el h1 ahora es el del banner, y sin esto los
          resultados quedaban colgando sin título — para un lector de pantalla, sin nada que los
          nombre. */}
      {/* La práctica pendiente también se recuerda aquí, donde el estudiante entra (24/09/2026). */}
      {me?.role === "STUDENT" && <RecordatorioDePractica className="mt-5" />}

      <h2 className="mt-6 font-display text-h3 font-bold">Profesores</h2>
      <p className="mt-1 text-[14px] text-text-secondary">
        Elige con quién quieres practicar y reserva tu clase.
      </p>

      {/* Barra de filtros horizontal, sobre los resultados (solo desktop). */}
      {esDesktop && (
        <div data-tour="filtros" className="mt-5 hidden rounded-card bg-surface-raised p-5 shadow-sm lg:block">
          <div className="mb-4 flex items-center justify-between gap-3">
            <p className="flex items-center gap-1.5 text-[12px] font-bold uppercase tracking-[0.04em] text-text-muted">
              <SlidersHorizontal size={14} strokeWidth={2} />
              {t.filtros.titulo}
            </p>
            {activos > 0 && (
              <button
                type="button"
                onClick={() => setFiltros(FILTROS_INICIALES)}
                className="rounded-pill px-3 py-1 text-[13px] font-bold text-primary-strong transition-colors hover:bg-primary-soft focus-visible:shadow-focus"
              >
                Limpiar {activos}
              </button>
            )}
          </div>
          {barraFiltros}
        </div>
      )}

      <div className="mt-5">
        <section>
          {/* Controles superiores: orden + botón Filtros (móvil) */}
          <div className="mb-4 flex items-center justify-between gap-3">
            <p className="text-[13px] font-semibold text-text-secondary">
              {profesores.isPending ? "Buscando…" : `${total} ${total === 1 ? "profesor" : "profesores"}`}
            </p>

            {!esDesktop && (
              <Boton
                variante="contorno"
                data-tour="filtros"
                onClick={() => setHojaAbierta(true)}
                className="h-11 shrink-0"
              >
                <SlidersHorizontal size={16} strokeWidth={1.75} />
                {t.filtros.titulo}
                {activos > 0 && (
                  <span className="grid h-5 min-w-5 place-items-center rounded-pill bg-primary px-1.5 text-[11px] font-bold text-on-primary">
                    {activos}
                  </span>
                )}
              </Boton>
            )}
          </div>

          {profesores.isPending && <Cargando />}

          {profesores.isError && (
            <ErrorCarga
              mensaje="No pudimos cargar los profesores. Revisa tu conexión e inténtalo otra vez."
              onReintentar={() => void profesores.refetch()}
            />
          )}

          {profesores.data && listado.length === 0 && (
            <Vacio
              mascota
              titulo={activos > 0 ? "Sin resultados" : "Aún no hay profesores"}
              texto={
                activos > 0
                  ? "Ningún profesor coincide con estos filtros. Prueba a quitar alguno."
                  : "Muy pronto vas a poder agendar tu primera clase. Cada proceso es diferente; lo importante es empezar."
              }
              accion={
                activos > 0 ? (
                  <Boton variante="secundario" onClick={() => setFiltros(FILTROS_INICIALES)}>
                    {t.filtros.limpiar}
                  </Boton>
                ) : undefined
              }
            />
          )}

          {listado.length > 0 && (
            <>
              <ul className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
                {listado.map((profesor, i) => (
                  <li
                    key={profesor.id}
                    className="anim-rise"
                    style={{ animationDelay: `${Math.min(i, 6) * 40}ms` }}
                  >
                    <TarjetaProfesor profesor={profesor} />
                  </li>
                ))}
              </ul>

              {profesores.hasNextPage && (
                <div className="mt-6 flex justify-center">
                  <Boton
                    variante="contorno"
                    disabled={profesores.isFetchingNextPage}
                    onClick={() => void profesores.fetchNextPage()}
                    className="h-12 px-8"
                  >
                    {profesores.isFetchingNextPage ? "Cargando…" : "Ver más profesores"}
                  </Boton>
                </div>
              )}
            </>
          )}
        </section>
      </div>

      {/* Hoja inferior de filtros (móvil) */}
      {!esDesktop && hojaAbierta && (
        <Modal titulo={t.filtros.titulo} onCerrar={() => setHojaAbierta(false)}>
          {panel}
          <div className="mt-5 flex gap-3">
            <Boton
              variante="fantasma"
              onClick={() => setFiltros(FILTROS_INICIALES)}
              className="flex-1"
            >
              {t.filtros.limpiar}
            </Boton>
            <Boton variante="primario" onClick={() => setHojaAbierta(false)} className="flex-1">
              {t.filtros.aplicar}
            </Boton>
          </div>
        </Modal>
      )}
    </main>
  );
}

/** Chip de filtro seleccionable (pill). Coral cuando está activo, neutro cuando no. */
function ChipFiltro({
  activo,
  onClick,
  children,
}: {
  activo: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      aria-pressed={activo}
      onClick={onClick}
      className={`inline-flex min-h-9 items-center gap-1.5 rounded-pill px-3.5 py-1.5 text-[13px] font-semibold transition-colors focus-visible:shadow-focus ${
        activo
          ? "bg-primary text-on-primary"
          : "bg-surface-sunken text-text-secondary hover:bg-border/60 hover:text-text"
      }`}
    >
      {children}
    </button>
  );
}

/** El rótulo de cada grupo de filtros. Fuera del render: definirlo dentro remonta el subárbol. */
function Titulo({ children }: { children: ReactNode }) {
  return (
    <p className="mb-2 text-[12px] font-bold uppercase tracking-[0.04em] text-text-muted">
      {children}
    </p>
  );
}

/**
 * Los mismos filtros en dos maquetaciones.
 *
 * En móvil van apilados dentro de una hoja. En escritorio, en una barra sobre los resultados —
 * horizontal y no en columna lateral, porque la columna le robaba a la retícula de profesores el
 * ancho que necesita para respirar.
 *
 * La barra se organiza en dos bandas y no en una retícula "lista": arriba los controles compactos
 * de ancho acotado (orden, precio, interruptores), abajo los grupos de chips, que crecen con el
 * catálogo y necesitan la fila entera para envolver. Mezclarlos en una sola retícula fue justo lo
 * que hizo que se pisaran: seis grupos de anchos muy distintos cayendo en celdas impredecibles.
 */
function PanelFiltros({
  filtros,
  onCambio,
  languages,
  goals,
  horizontal = false,
}: {
  filtros: Filtros;
  onCambio: (f: Filtros) => void;
  languages: LanguageResponse[];
  goals: GoalResponse[];
  horizontal?: boolean;
}) {
  const toggleEn = <T,>(lista: T[], code: T) =>
    lista.includes(code) ? lista.filter((c) => c !== code) : [...lista, code];

  const avanzadosActivos =
    (filtros.language ? 1 : 0) +
    filtros.levels.length +
    filtros.goals.length +
    (filtros.native ? 1 : 0) +
    (filtros.certified ? 1 : 0) +
    filtros.days.length +
    (filtros.hours.length > 0 ? 1 : 0);

  // Si se llega con filtros avanzados puestos (una URL compartida, o el cajón del móvil), la
  // sección abre sola: si no, los resultados vendrían recortados por controles que no se ven.
  // Nulo mientras nadie lo toque: así también abre cuando los filtros llegan de la URL después del
  // primer render (la portada manda `schedule=`), y no solo si ya estaban al montar.
  const [abiertosAMano, setAbiertosAMano] = useState<boolean | null>(null);
  const avanzadosAbiertos = abiertosAMano ?? avanzadosActivos > 0;

  const orden = (
    <div>
      <Titulo>{t.filtros.orden}</Titulo>
      <Segmento<Orden>
        valor={filtros.sort}
        onCambio={(sort) => onCambio({ ...filtros, sort })}
        opciones={[
          { valor: "RELEVANCE", etiqueta: t.orden.RELEVANCE },
          { valor: "PRICE_ASC", etiqueta: t.orden.PRICE_ASC },
          { valor: "PRICE_DESC", etiqueta: t.orden.PRICE_DESC },
        ]}
      />
    </div>
  );

  const precio = (
    <div>
      <Titulo>{t.filtros.precio}</Titulo>
      <div className="flex items-center gap-2">
        <input
          type="number"
          inputMode="numeric"
          min={0}
          step={1000}
          value={filtros.minPrice}
          onChange={(e) => onCambio({ ...filtros, minPrice: e.target.value })}
          placeholder={t.filtros.precioMin}
          aria-label={t.filtros.precioMin}
          className="h-12 w-full min-w-0 rounded-base border-[1.5px] border-border bg-surface-raised px-3 text-[14px] text-text placeholder:text-text-muted focus:border-primary focus:shadow-focus focus:outline-none"
        />
        <span className="text-text-muted">–</span>
        <input
          type="number"
          inputMode="numeric"
          min={0}
          step={1000}
          value={filtros.maxPrice}
          onChange={(e) => onCambio({ ...filtros, maxPrice: e.target.value })}
          placeholder={t.filtros.precioMax}
          aria-label={t.filtros.precioMax}
          className="h-12 w-full min-w-0 rounded-base border-[1.5px] border-border bg-surface-raised px-3 text-[14px] text-text placeholder:text-text-muted focus:border-primary focus:shadow-focus focus:outline-none"
        />
      </div>
    </div>
  );

  const interruptores = (
    <div>
      {/* En la barra no llevan rótulo: se explican solos, y ocupan su propia fila porque los dos
          juntos nunca caben junto al orden y el precio. Un rótulo invisible solo dejaría un hueco. */}
      {!horizontal && <Titulo>Otros</Titulo>}
      <div className={horizontal ? "flex flex-wrap items-center gap-x-8 gap-y-3" : "space-y-3"}>
        <label className={`flex items-center gap-2.5 ${horizontal ? "" : "justify-between"}`}>
          <Toggle
            activo={filtros.native}
            onCambio={(native) => onCambio({ ...filtros, native })}
            etiqueta={t.filtros.nativo}
          />
          <span className="whitespace-nowrap text-[13.5px] font-semibold text-text">
            {t.filtros.nativo}
          </span>
        </label>
        <label className={`flex items-center gap-2.5 ${horizontal ? "" : "justify-between"}`}>
          <Toggle
            activo={filtros.certified}
            onCambio={(certified) => onCambio({ ...filtros, certified })}
            etiqueta={t.filtros.certificado}
          />
          <span className="whitespace-nowrap text-[13.5px] font-semibold text-text">
            {t.filtros.certificado}
          </span>
        </label>
      </div>
    </div>
  );

  // Un filtro con una sola opción no filtra nada: es ruido que ocupa el sitio de los que sí
  // sirven. Desde que Orión enseña solo inglés (V42) el catálogo devuelve una única fila, así que
  // el bloque desaparece solo. Si algún día se reactiva otro idioma, vuelve solo también.
  const idioma = languages.length > 1 && (
    <div>
      <Titulo>{t.filtros.idioma}</Titulo>
      <div className="flex flex-wrap gap-2">
        {languages.map((lang) => (
          <ChipFiltro
            key={lang.code}
            activo={filtros.language === lang.code}
            onClick={() =>
              onCambio({
                ...filtros,
                language: filtros.language === lang.code ? null : (lang.code ?? null),
              })
            }
          >
            <DiscoIdioma code={lang.code ?? ""} size={16} />
            {lang.nameEs}
          </ChipFiltro>
        ))}
      </div>
    </div>
  );

  const nivel = (
    <div>
      <Titulo>{t.filtros.nivel}</Titulo>
      <div className="flex flex-wrap gap-2">
        {NIVELES.map((n) => (
          <ChipFiltro
            key={n}
            activo={filtros.levels.includes(n)}
            onClick={() => onCambio({ ...filtros, levels: toggleEn(filtros.levels, n) })}
          >
            {etiquetaNivel(n)}
          </ChipFiltro>
        ))}
      </div>
    </div>
  );

  const objetivos = goals.length > 0 && (
    <div>
      <Titulo>{t.filtros.objetivos}</Titulo>
      <div className="flex flex-wrap gap-2">
        {goals.map((goal) => (
          <ChipFiltro
            key={goal.code}
            activo={!!goal.code && filtros.goals.includes(goal.code)}
            onClick={() =>
              goal.code && onCambio({ ...filtros, goals: toggleEn(filtros.goals, goal.code) })
            }
          >
            {goal.nameEs}
          </ChipFiltro>
        ))}
      </div>
    </div>
  );

  /**
   * Cuándo. Es la pregunta que de verdad se hace quien busca clase —«¿quién puede los martes por la
   * noche?»— y hasta ahora no se podía contestar.
   *
   * <p>Filtra sobre los horarios que el profesor publicó, no sobre cupos libres calculados: un cupo
   * libre depende del instante y de las reservas vivas, así que cambiaría entre la búsqueda y el
   * clic. Esto contesta «suele tener martes por la noche», que es lo que se está preguntando.
   */
  const cuando = (
    <div>
      <Titulo>Cuándo</Titulo>
      <div className="flex flex-wrap gap-1.5">
        {DIAS.map((d) => (
          <ChipFiltro
            key={d.valor}
            activo={filtros.days.includes(d.valor)}
            onClick={() => onCambio({ ...filtros, days: toggleEn(filtros.days, d.valor) })}
          >
            <span className="w-3 text-center">{d.corta}</span>
          </ChipFiltro>
        ))}
      </div>
      {/* Horas exactas, varias a la vez. El nombre de cada franja marca o desmarca todas las suyas. */}
      <div className="mt-3 space-y-2.5">
        {FRANJAS.map((franja) => {
          const todas = franja.horas.every((h) => filtros.hours.includes(h));
          return (
            <div key={franja.clave}>
              <button
                type="button"
                aria-pressed={todas}
                onClick={() =>
                  onCambio({
                    ...filtros,
                    hours: todas
                      ? filtros.hours.filter((h) => !franja.horas.includes(h))
                      : [...new Set([...filtros.hours, ...franja.horas])],
                  })
                }
                className="mb-1.5 inline-flex min-h-7 items-center rounded-pill px-1 text-[12px] font-bold text-text-secondary underline-offset-2 hover:text-text hover:underline focus-visible:shadow-focus"
              >
                {franja.etiqueta} {todas ? "· quitar todas" : "· todas"}
              </button>
              <div className="flex flex-wrap gap-1.5">
                {franja.horas.map((h) => (
                  <ChipFiltro
                    key={h}
                    activo={filtros.hours.includes(h)}
                    onClick={() => onCambio({ ...filtros, hours: toggleEn(filtros.hours, h) })}
                  >
                    <span className="tabular-nums">{etiquetaHora(h)}</span>
                  </ChipFiltro>
                ))}
              </div>
            </div>
          );
        })}
      </div>
      <p className="mt-2 text-[12px] leading-relaxed text-text-muted">
        Es la hora en que empieza la clase, en hora de Colombia, según los horarios que cada profesor publicó. La
        disponibilidad exacta se ve en su perfil.
      </p>
    </div>
  );

  const avanzados = (
    <>
      {interruptores}
      {cuando}
      {idioma}
      {nivel}
      {objetivos}
    </>
  );

  // En el panel móvil no hay nada que plegar: es un cajón con scroll y ocultar cosas ahí solo
  // añade un toque de más. El orden sí cambia — primero lo que casi todo el mundo usa.
  if (!horizontal) {
    return (
      <div className="space-y-5">
        {orden}
        {precio}
        {avanzados}
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      {/* De entrada solo orden y precio: son los dos que usa casi todo el mundo y los únicos que
          caben en una fila sin apretarse. El resto vive detrás de "Avanzado". */}
      <div className="flex flex-wrap items-end gap-x-8 gap-y-4">
        <div className="min-w-[280px] flex-1">{orden}</div>
        <div className="min-w-[220px] max-w-[300px] flex-1">{precio}</div>
        <BotonAvanzado
          abierto={avanzadosAbiertos}
          activos={avanzadosActivos}
          onClick={() => setAbiertosAMano(!avanzadosAbiertos)}
        />
      </div>

      {avanzadosAbiertos && (
        <div className="flex flex-col gap-4 border-t border-border pt-4">{avanzados}</div>
      )}
    </div>
  );
}

/**
 * El contador no es adorno: un filtro que sigue aplicándose mientras su control está escondido es
 * un resultado vacío sin explicación. Con el número a la vista, plegar la sección no oculta estado.
 */
function BotonAvanzado({
  abierto,
  activos,
  onClick,
}: {
  abierto: boolean;
  activos: number;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-expanded={abierto}
      className="inline-flex h-12 shrink-0 items-center gap-2 rounded-pill border-[1.5px] border-border px-4 text-[13.5px] font-bold text-text transition-colors hover:bg-surface-sunken focus-visible:shadow-focus"
    >
      <SlidersHorizontal size={15} strokeWidth={2} />
      Avanzado
      {activos > 0 && (
        <span className="grid h-5 min-w-5 place-items-center rounded-pill bg-primary px-1.5 text-[11px] font-bold text-on-primary">
          {activos}
        </span>
      )}
      <ChevronDown
        size={15}
        strokeWidth={2.2}
        className={`transition-transform ${abierto ? "rotate-180" : ""}`}
      />
    </button>
  );
}
function TarjetaProfesor({ profesor }: { profesor: ProfessorCard }) {
  const ubicacion = [profesor.city, paisConBandera(profesor.countryCode)].filter(Boolean).join(", ");

  return (
    <div className="flex h-full flex-col rounded-card bg-surface-raised p-5 shadow-md transition-[transform,box-shadow] hover:-translate-y-0.5 hover:shadow-lg">
      <div className="flex items-start gap-3">
        <Avatar nombre={profesor.fullName ?? ""} fotoUrl={profesor.photoUrl} size="lg" />
        <div className="min-w-0 pt-0.5">
          <p className="truncate font-display text-[17px] font-bold">{profesor.fullName}</p>
          {profesor.headline && (
            <p className="mt-0.5 line-clamp-2 text-[13.5px] font-semibold text-text-secondary">
              {profesor.headline}
            </p>
          )}
          <div className="mt-1.5">
            <EstrellaRating ratingAvg={profesor.ratingAvg} ratingCount={profesor.ratingCount} />
          </div>
        </div>
      </div>

      {/* Idiomas con bandera y "Nativo" */}
      {profesor.languages && profesor.languages.length > 0 && (
        <div className="mt-3 flex flex-wrap gap-1.5">
          {profesor.languages.map((idioma) => (
            <span
              key={idioma.code}
              className="inline-flex items-center gap-1 rounded-pill bg-surface-sunken px-2.5 py-1 text-[12px] font-semibold text-text-secondary"
            >
              <DiscoIdioma code={idioma.code ?? ""} size={16} />
              {idioma.nameEs}
              {idioma.isNative && (
                <span className="rounded-pill bg-primary-soft px-1.5 py-px text-[10px] font-bold text-primary-strong">
                  Nativo
                </span>
              )}
            </span>
          ))}
        </div>
      )}

      {/* Niveles + certificado */}
      <div className="mt-2.5 flex flex-wrap items-center gap-1.5">
        {(profesor.levels ?? []).map((nivel) => (
          <span
            key={nivel}
            className="rounded-pill bg-accent-lavender-soft px-2.5 py-1 text-[11.5px] font-semibold text-[#5e4a8a]"
          >
            {etiquetaNivel(nivel)}
          </span>
        ))}
        {profesor.certified && (
          <span className="inline-flex items-center gap-1 rounded-pill bg-success-bg px-2.5 py-1 text-[11.5px] font-bold text-success">
            <BadgeCheck size={13} strokeWidth={2.2} />
            Certificado
          </span>
        )}
      </div>

      {ubicacion && (
        <p className="mt-3 flex items-center gap-1 text-[12.5px] text-text-muted">
          <MapPin size={13} strokeWidth={1.75} />
          {ubicacion}
        </p>
      )}

      <div className="mt-4 flex items-end justify-between gap-3 border-t border-border pt-4">
        <div>
          {tarifaClase(profesor.hourlyRateCop) ? (
            <>
              <p className="font-display text-[19px] font-bold text-text">
                {tarifaClase(profesor.hourlyRateCop)}
              </p>
              {!esGratis(profesor.hourlyRateCop) && (
                <p className="text-[11.5px] text-text-muted">por hora</p>
              )}
            </>
          ) : (
            <p className="text-[12.5px] text-text-muted">Tarifa por confirmar</p>
          )}
        </div>
        <Link
          href={`/profesores/${profesor.id}`}
          className="inline-flex min-h-11 items-center gap-1 rounded-pill bg-primary px-5 text-[14px] font-bold text-on-primary shadow-primary transition-colors hover:bg-primary-strong focus-visible:shadow-focus"
        >
          Ver agenda
          <ChevronRight size={16} strokeWidth={1.75} />
        </Link>
      </div>
    </div>
  );
}
