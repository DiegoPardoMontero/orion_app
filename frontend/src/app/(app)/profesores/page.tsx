"use client";

import { useInfiniteQuery, useQuery } from "@tanstack/react-query";
import { BadgeCheck, ChevronRight, MapPin, SlidersHorizontal } from "lucide-react";
import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { Avatar } from "@/components/Avatar";
import { Cargando, ErrorCarga, Vacio } from "@/components/estados";
import { Modal } from "@/components/Modal";
import { EstrellaRating } from "@/components/Rating";
import { Boton, Segmento, Toggle } from "@/components/ui";
import { apiFetch } from "@/lib/api/fetch";
import type {
  GoalResponse,
  LanguageResponse,
  PagedProfessors,
  ProfessorCard,
} from "@/lib/api/types";
import { precioCop } from "@/lib/format";
import { etiquetaNivel, NIVELES, t } from "@/lib/i18n";
import { useMediaQuery } from "@/lib/useMediaQuery";

type Orden = "RELEVANCE" | "PRICE_ASC" | "PRICE_DESC";

type Filtros = {
  language: string | null;
  goals: string[];
  levels: string[];
  minPrice: string;
  maxPrice: string;
  native: boolean;
  certified: boolean;
  sort: Orden;
};

const FILTROS_INICIALES: Filtros = {
  language: null,
  goals: [],
  levels: [],
  minPrice: "",
  maxPrice: "",
  native: false,
  certified: false,
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
    (f.certified ? 1 : 0)
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
  if (f.sort !== "RELEVANCE") p.set("sort", f.sort);
  p.set("page", String(page));
  p.set("size", String(TAM_PAGINA));
  return p.toString();
}

export default function ProfesoresPage() {
  const esDesktop = useMediaQuery("(min-width: 1024px)");
  const [filtros, setFiltros] = useState<Filtros>(FILTROS_INICIALES);
  const [hojaAbierta, setHojaAbierta] = useState(false);

  // Filtros iniciales desde la URL (el buscador del hero y las landings por idioma enlazan aquí con
  // ?language=&goal=&level=). Se lee una sola vez al montar, en cliente, para no chocar con la
  // hidratación. El parámetro `schedule` (MORNING/AFTERNOON/EVENING) del hero se ignora a propósito:
  // el backend aún no filtra por franja horaria; se conserva en la URL para conectarlo más adelante.
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const language = params.get("language");
    const goals = params.getAll("goal").filter(Boolean);
    const levels = params.getAll("level").filter(Boolean);
    if (!language && goals.length === 0 && levels.length === 0) return;
    // Siembra única desde la URL al montar; a partir de aquí manda el usuario. El setState en el
    // efecto es deliberado (sincronizar con un sistema externo: la query string) y solo corre una vez.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setFiltros((prev) => ({
      ...prev,
      language: language ?? prev.language,
      goals: goals.length > 0 ? goals : prev.goals,
      levels: levels.length > 0 ? levels : prev.levels,
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

  return (
    <main className="mx-auto w-full max-w-md px-5 py-6 lg:max-w-6xl lg:px-12 lg:py-8">
      <h1 className="font-display text-h1 font-bold">Profesores</h1>
      <p className="mt-1 text-[14px] text-text-secondary">
        Elige con quién quieres practicar y reserva tu clase.
      </p>

      <div className="mt-5 lg:grid lg:grid-cols-[248px_minmax(0,1fr)] lg:gap-10">
        {/* Barra lateral fija en desktop */}
        {esDesktop && (
          <aside className="hidden lg:block">
            <div className="sticky top-8 rounded-card bg-surface-raised p-5 shadow-sm">{panel}</div>
          </aside>
        )}

        <section>
          {/* Controles superiores: orden + botón Filtros (móvil) */}
          <div className="mb-4 flex items-center justify-between gap-3">
            <p className="text-[13px] font-semibold text-text-secondary">
              {profesores.isPending ? "Buscando…" : `${total} ${total === 1 ? "profesor" : "profesores"}`}
            </p>

            {!esDesktop && (
              <Boton
                variante="contorno"
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

function PanelFiltros({
  filtros,
  onCambio,
  languages,
  goals,
}: {
  filtros: Filtros;
  onCambio: (f: Filtros) => void;
  languages: LanguageResponse[];
  goals: GoalResponse[];
}) {
  const toggleEn = (lista: string[], code: string) =>
    lista.includes(code) ? lista.filter((c) => c !== code) : [...lista, code];

  return (
    <div className="space-y-5">
      {/* Orden */}
      <div>
        <p className="mb-2 text-[12px] font-bold uppercase tracking-[0.04em] text-text-muted">
          {t.filtros.orden}
        </p>
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

      {/* Idioma (uno) */}
      {languages.length > 0 && (
        <div>
          <p className="mb-2 text-[12px] font-bold uppercase tracking-[0.04em] text-text-muted">
            {t.filtros.idioma}
          </p>
          <div className="flex flex-wrap gap-2">
            {languages.map((idioma) => (
              <ChipFiltro
                key={idioma.code}
                activo={filtros.language === idioma.code}
                onClick={() =>
                  onCambio({
                    ...filtros,
                    language: filtros.language === idioma.code ? null : (idioma.code ?? null),
                  })
                }
              >
                {idioma.flagEmoji && <span aria-hidden="true">{idioma.flagEmoji}</span>}
                {idioma.nameEs}
              </ChipFiltro>
            ))}
          </div>
        </div>
      )}

      {/* Objetivos (varios) */}
      {goals.length > 0 && (
        <div>
          <p className="mb-2 text-[12px] font-bold uppercase tracking-[0.04em] text-text-muted">
            {t.filtros.objetivos}
          </p>
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
      )}

      {/* Nivel (varios) */}
      <div>
        <p className="mb-2 text-[12px] font-bold uppercase tracking-[0.04em] text-text-muted">
          {t.filtros.nivel}
        </p>
        <div className="flex flex-wrap gap-2">
          {NIVELES.map((nivel) => (
            <ChipFiltro
              key={nivel}
              activo={filtros.levels.includes(nivel)}
              onClick={() => onCambio({ ...filtros, levels: toggleEn(filtros.levels, nivel) })}
            >
              {etiquetaNivel(nivel)}
            </ChipFiltro>
          ))}
        </div>
      </div>

      {/* Precio */}
      <div>
        <p className="mb-2 text-[12px] font-bold uppercase tracking-[0.04em] text-text-muted">
          {t.filtros.precio}
        </p>
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
            className="h-11 w-full rounded-base border-[1.5px] border-border bg-surface-raised px-3 text-[14px] text-text placeholder:text-text-muted focus:border-primary focus:shadow-focus focus:outline-none"
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
            className="h-11 w-full rounded-base border-[1.5px] border-border bg-surface-raised px-3 text-[14px] text-text placeholder:text-text-muted focus:border-primary focus:shadow-focus focus:outline-none"
          />
        </div>
      </div>

      {/* Toggles */}
      <div className="space-y-3">
        <label className="flex items-center justify-between gap-3">
          <span className="text-[13.5px] font-semibold text-text">{t.filtros.nativo}</span>
          <Toggle
            activo={filtros.native}
            onCambio={(native) => onCambio({ ...filtros, native })}
            etiqueta={t.filtros.nativo}
          />
        </label>
        <label className="flex items-center justify-between gap-3">
          <span className="text-[13.5px] font-semibold text-text">{t.filtros.certificado}</span>
          <Toggle
            activo={filtros.certified}
            onCambio={(certified) => onCambio({ ...filtros, certified })}
            etiqueta={t.filtros.certificado}
          />
        </label>
      </div>
    </div>
  );
}

function TarjetaProfesor({ profesor }: { profesor: ProfessorCard }) {
  const ubicacion = [profesor.city, profesor.countryCode].filter(Boolean).join(", ");

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
              {idioma.flagEmoji && <span aria-hidden="true">{idioma.flagEmoji}</span>}
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
          {profesor.hourlyRateCop ? (
            <>
              <p className="font-display text-[19px] font-bold text-text">
                {precioCop(profesor.hourlyRateCop)}
              </p>
              <p className="text-[11.5px] text-text-muted">por hora</p>
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
