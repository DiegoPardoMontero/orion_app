"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { BadgeCheck, Check, Eye, Plus, Sparkles, X } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { CambiarFoto } from "@/components/CambiarFoto";
import { AvisoError, Cargando, ErrorCarga } from "@/components/estados";
import { Boton, BotonPrincipal, Campo, Spinner, Toggle } from "@/components/ui";
import { ApiError, apiFetch } from "@/lib/api/fetch";
import type {
  GoalResponse,
  LanguageResponse,
  ProfileResponse,
  RateBreakdownResponse,
} from "@/lib/api/types";
import { precioCop } from "@/lib/format";
import { etiquetaNivel, NIVELES } from "@/lib/i18n";

/** Espejo del tope del backend (ProfessorProfileService.MAX_PALABRAS_BIO). */
const MAX_PALABRAS_BIO = 100;

/** El idioma tal como lo edita el profesor: código + si es nativo + niveles que enseña. */
type LangEdit = { code: string; isNative: boolean; levels: string[] };

export default function PerfilPage() {
  const perfil = useQuery({
    queryKey: ["me", "profile"],
    queryFn: () => apiFetch<ProfileResponse>("/api/v1/me/profile"),
  });

  if (perfil.isPending) {
    return (
      <main className="px-5 py-5">
        <Cargando filas={3} />
      </main>
    );
  }

  if (perfil.isError) {
    return (
      <main className="px-5 py-5">
        <ErrorCarga
          mensaje="No pudimos cargar tu perfil."
          onReintentar={() => void perfil.refetch()}
        />
      </main>
    );
  }

  // El formulario se monta ya con los datos, así no hay que sembrarlo desde un efecto: nace
  // con su estado inicial y a partir de ahí es su dueño, sin que un refetch pise lo que escribes.
  return <FormularioPerfil inicial={perfil.data} />;
}

function FormularioPerfil({ inicial }: { inicial: ProfileResponse }) {
  const queryClient = useQueryClient();

  const languages = useQuery({
    queryKey: ["catalog", "languages"],
    queryFn: () => apiFetch<LanguageResponse[]>("/api/v1/catalog/languages"),
    staleTime: 5 * 60_000,
  });
  const goalsCat = useQuery({
    queryKey: ["catalog", "goals"],
    queryFn: () => apiFetch<GoalResponse[]>("/api/v1/catalog/goals"),
    staleTime: 5 * 60_000,
  });

  const [headline, setHeadline] = useState(inicial.headline ?? "");
  const [bio, setBio] = useState(inicial.bio ?? "");
  // La misma cuenta que hace el backend: palabras separadas por espacios.
  const palabrasBio = bio.trim() ? bio.trim().split(/\s+/).length : 0;
  const [city, setCity] = useState(inicial.city ?? "");
  const [countryCode, setCountryCode] = useState(inicial.countryCode ?? "CO");
  const [yearsExperience, setYearsExperience] = useState(
    inicial.yearsExperience != null ? String(inicial.yearsExperience) : "",
  );
  const [education, setEducation] = useState(inicial.education ?? "");
  const [certified, setCertified] = useState(inicial.certified ?? false);
  const [acceptsTrial, setAcceptsTrial] = useState(inicial.acceptsTrial ?? false);
  const [langs, setLangs] = useState<LangEdit[]>(
    (inicial.languages ?? []).map((l) => ({
      code: l.code ?? "",
      isNative: l.isNative ?? false,
      levels: l.levels ?? [],
    })),
  );
  const [goals, setGoals] = useState<string[]>(inicial.goals ?? []);
  const [publicado, setPublicado] = useState(inicial.isPublished ?? false);
  const [canPublish, setCanPublish] = useState(inicial.canPublish ?? false);
  const [guardado, setGuardado] = useState(false);

  const guardar = useMutation({
    mutationFn: () =>
      apiFetch<ProfileResponse>("/api/v1/me/profile", {
        method: "PUT",
        body: {
          headline: headline.trim() || undefined,
          bio: bio.trim() || undefined,
          countryCode: countryCode.trim() || undefined,
          city: city.trim() || undefined,
          nativeLanguage: inicial.nativeLanguage,
          yearsExperience: yearsExperience ? Number(yearsExperience) : undefined,
          education: education.trim() || undefined,
          certified,
          acceptsTrial,
          languages: langs
            .filter((l) => l.code)
            .map((l) => ({ code: l.code, isNative: l.isNative, levels: l.levels })),
          goals,
          isPublished: publicado,
        },
      }),
    onSuccess: (actualizado) => {
      queryClient.setQueryData(["me", "profile"], actualizado);
      setCanPublish(actualizado.canPublish ?? false);
      setPublicado(actualizado.isPublished ?? false);
      // Publicarse o cambiar el perfil altera el directorio que ven los estudiantes.
      void queryClient.invalidateQueries({ queryKey: ["professors"] });
      setGuardado(true);
      setTimeout(() => setGuardado(false), 3000);
    },
  });

  const error = guardar.error instanceof ApiError ? guardar.error.message : null;

  const disponibles = useMemo(
    () => (languages.data ?? []).filter((l) => !langs.some((x) => x.code === l.code)),
    [languages.data, langs],
  );

  const nombreIdioma = (code: string) =>
    languages.data?.find((l) => l.code === code)?.nameEs ?? code;
  const banderaIdioma = (code: string) =>
    languages.data?.find((l) => l.code === code)?.flagEmoji ?? "";

  const agregarIdioma = (code: string) =>
    setLangs((prev) => (prev.some((l) => l.code === code) ? prev : [...prev, { code, isNative: false, levels: [] }]));
  const quitarIdioma = (code: string) => setLangs((prev) => prev.filter((l) => l.code !== code));
  const marcarNativo = (code: string, value: boolean) =>
    setLangs((prev) => prev.map((l) => (l.code === code ? { ...l, isNative: value } : l)));
  const alternarNivel = (code: string, nivel: string) =>
    setLangs((prev) =>
      prev.map((l) =>
        l.code === code
          ? {
              ...l,
              levels: l.levels.includes(nivel)
                ? l.levels.filter((n) => n !== nivel)
                : [...l.levels, nivel],
            }
          : l,
      ),
    );
  const alternarObjetivo = (code: string) =>
    setGoals((prev) => (prev.includes(code) ? prev.filter((c) => c !== code) : [...prev, code]));

  return (
    <main className="mx-auto w-full max-w-md px-5 py-5 lg:max-w-2xl">
      <h1 className="font-display text-h1 font-bold">Mi perfil</h1>
      <p className="mt-1 text-[12.5px] text-text-secondary">Esto es lo que ven los estudiantes.</p>

      <div className="mt-5">
        <CambiarFoto nombre={inicial.fullName ?? ""} fotoUrl={inicial.photoUrl} />
      </div>

      {/* — Tarifa — */}
      <WidgetTarifa inicial={inicial} onGuardada={() => setCanPublish(true)} />

      {/* — Presentación — */}
      <label className="mt-6 block text-[12.5px] font-bold text-text-secondary" htmlFor="headline">
        Titular
      </label>
      <Campo
        id="headline"
        type="text"
        maxLength={120}
        value={headline}
        onChange={(event) => setHeadline(event.target.value)}
        placeholder="Conversación y confianza · A1–B1"
        className="mt-1.5"
      />

      <label className="mt-4 block text-[12.5px] font-bold text-text-secondary" htmlFor="bio">
        Sobre ti
      </label>
      <textarea
        id="bio"
        rows={4}
        value={bio}
        onChange={(event) => setBio(event.target.value)}
        placeholder="Cuéntales cómo son tus clases."
        aria-describedby="bio-contador"
        className={`mt-1.5 w-full rounded-base border-[1.5px] bg-surface-raised px-4 py-3 text-sm placeholder:text-text-muted focus:shadow-focus focus:outline-none ${
          palabrasBio > MAX_PALABRAS_BIO ? "border-error" : "border-border focus:border-primary"
        }`}
      />
      {/* El contador se ve mientras se escribe. El backend rechaza igual, pero enterarse al guardar
          —después de redactar tres párrafos— es la peor forma de conocer un límite. */}
      <p
        id="bio-contador"
        className={`mt-1.5 text-[12px] ${
          palabrasBio > MAX_PALABRAS_BIO ? "font-semibold text-error" : "text-text-muted"
        }`}
      >
        {palabrasBio > MAX_PALABRAS_BIO
          ? `Te pasaste por ${palabrasBio - MAX_PALABRAS_BIO} ${palabrasBio - MAX_PALABRAS_BIO === 1 ? "palabra" : "palabras"}. Máximo ${MAX_PALABRAS_BIO}.`
          : `${palabrasBio} de ${MAX_PALABRAS_BIO} palabras`}
      </p>

      {/* — Idiomas — */}
      <section className="mt-6">
        <h2 className="text-[13.5px] font-bold text-text">Idiomas que enseñas</h2>
        {langs.length === 0 && (
          <p className="mt-1.5 text-[12.5px] text-text-muted">
            Agrega al menos un idioma y marca los niveles que enseñas.
          </p>
        )}

        <div className="mt-3 space-y-3">
          {langs.map((lang) => (
            <div key={lang.code} className="rounded-card bg-surface-raised p-4 shadow-sm">
              <div className="flex items-center justify-between gap-3">
                <p className="flex items-center gap-1.5 text-[14px] font-bold text-text">
                  {banderaIdioma(lang.code) && <span aria-hidden="true">{banderaIdioma(lang.code)}</span>}
                  {nombreIdioma(lang.code)}
                </p>
                <button
                  type="button"
                  aria-label={`Quitar ${nombreIdioma(lang.code)}`}
                  onClick={() => quitarIdioma(lang.code)}
                  className="grid h-8 w-8 place-items-center rounded-full text-text-muted transition-colors hover:bg-surface-sunken hover:text-text focus-visible:shadow-focus"
                >
                  <X size={16} strokeWidth={1.75} />
                </button>
              </div>

              <div className="mt-3 flex flex-wrap gap-2">
                {NIVELES.map((nivel) => (
                  <button
                    key={nivel}
                    type="button"
                    aria-pressed={lang.levels.includes(nivel)}
                    onClick={() => alternarNivel(lang.code, nivel)}
                    className={`min-h-9 rounded-pill px-3.5 py-1.5 text-[12.5px] font-semibold transition-colors focus-visible:shadow-focus ${
                      lang.levels.includes(nivel)
                        ? "bg-primary text-on-primary"
                        : "bg-surface-sunken text-text-secondary hover:bg-border/60 hover:text-text"
                    }`}
                  >
                    {etiquetaNivel(nivel)}
                  </button>
                ))}
              </div>

              <label className="mt-3 flex items-center justify-between gap-3">
                <span className="text-[12.5px] font-semibold text-text-secondary">Es mi lengua materna</span>
                <Toggle
                  activo={lang.isNative}
                  onCambio={(v) => marcarNativo(lang.code, v)}
                  etiqueta="Lengua materna"
                />
              </label>
            </div>
          ))}
        </div>

        {disponibles.length > 0 && (
          <div className="mt-3 flex flex-wrap gap-2">
            {disponibles.map((idioma) => (
              <button
                key={idioma.code}
                type="button"
                onClick={() => idioma.code && agregarIdioma(idioma.code)}
                className="inline-flex min-h-9 items-center gap-1.5 rounded-pill border-[1.5px] border-dashed border-border-strong px-3.5 py-1.5 text-[13px] font-semibold text-text-secondary transition-colors hover:border-primary hover:text-primary-strong focus-visible:shadow-focus"
              >
                <Plus size={14} strokeWidth={2} />
                {idioma.flagEmoji && <span aria-hidden="true">{idioma.flagEmoji}</span>}
                {idioma.nameEs}
              </button>
            ))}
          </div>
        )}
      </section>

      {/* — Objetivos — */}
      {(goalsCat.data ?? []).length > 0 && (
        <section className="mt-6">
          <h2 className="text-[13.5px] font-bold text-text">¿Para qué objetivos preparas?</h2>
          <div className="mt-3 flex flex-wrap gap-2">
            {(goalsCat.data ?? []).map((goal) => (
              <button
                key={goal.code}
                type="button"
                aria-pressed={!!goal.code && goals.includes(goal.code)}
                onClick={() => goal.code && alternarObjetivo(goal.code)}
                className={`min-h-9 rounded-pill px-3.5 py-1.5 text-[13px] font-semibold transition-colors focus-visible:shadow-focus ${
                  goal.code && goals.includes(goal.code)
                    ? "bg-primary text-on-primary"
                    : "bg-surface-sunken text-text-secondary hover:bg-border/60 hover:text-text"
                }`}
              >
                {goal.nameEs}
              </button>
            ))}
          </div>
        </section>
      )}

      {/* — Datos — */}
      <section className="mt-6 grid grid-cols-2 gap-3">
        <div className="col-span-2 sm:col-span-1">
          <label className="block text-[12.5px] font-bold text-text-secondary" htmlFor="city">
            Ciudad
          </label>
          <Campo
            id="city"
            type="text"
            maxLength={80}
            value={city}
            onChange={(e) => setCity(e.target.value)}
            placeholder="Bogotá"
            className="mt-1.5"
          />
        </div>
        <div className="col-span-2 sm:col-span-1">
          <label className="block text-[12.5px] font-bold text-text-secondary" htmlFor="country">
            País
          </label>
          <Campo
            id="country"
            type="text"
            maxLength={2}
            value={countryCode}
            onChange={(e) => setCountryCode(e.target.value.toUpperCase())}
            placeholder="CO"
            className="mt-1.5 uppercase"
          />
        </div>
        <div className="col-span-2 sm:col-span-1">
          <label className="block text-[12.5px] font-bold text-text-secondary" htmlFor="years">
            Años de experiencia
          </label>
          <Campo
            id="years"
            type="number"
            min={0}
            max={80}
            value={yearsExperience}
            onChange={(e) => setYearsExperience(e.target.value)}
            placeholder="5"
            className="mt-1.5"
          />
        </div>
        <div className="col-span-2">
          <label className="block text-[12.5px] font-bold text-text-secondary" htmlFor="education">
            Formación
          </label>
          <Campo
            id="education"
            type="text"
            maxLength={160}
            value={education}
            onChange={(e) => setEducation(e.target.value)}
            placeholder="Licenciatura en Lenguas Modernas"
            className="mt-1.5"
          />
        </div>
      </section>

      <section className="mt-4 space-y-3">
        <label className="flex items-center justify-between gap-3 rounded-card bg-surface-raised p-4 shadow-sm">
          <span className="flex items-center gap-2 text-[13.5px] font-semibold text-text">
            <BadgeCheck size={16} strokeWidth={2} className="text-success" />
            Tengo certificación docente
          </span>
          <Toggle activo={certified} onCambio={setCertified} etiqueta="Certificado" />
        </label>
        <label className="flex items-center justify-between gap-3 rounded-card bg-surface-raised p-4 shadow-sm">
          <span className="flex items-center gap-2 text-[13.5px] font-semibold text-text">
            <Sparkles size={16} strokeWidth={2} className="text-primary-strong" />
            Ofrezco clase de prueba
          </span>
          <Toggle activo={acceptsTrial} onCambio={setAcceptsTrial} etiqueta="Clase de prueba" />
        </label>
      </section>

      {/* — Publicar — */}
      <section className="mt-6 rounded-card bg-success-bg p-4">
        <div className="flex items-center justify-between gap-3">
          <div>
            <p className="flex items-center gap-1.5 text-[13.5px] font-bold text-success">
              <Eye size={15} strokeWidth={2.2} />
              Perfil visible
            </p>
            <p className="mt-0.5 text-[11.5px] text-text-secondary">
              Los estudiantes pueden verte y reservar
            </p>
          </div>
          <Toggle activo={publicado} onCambio={setPublicado} etiqueta="Perfil visible" />
        </div>

        {publicado && !canPublish && (
          <p className="mt-3 rounded-base bg-warning-bg px-3.5 py-2.5 text-[12px] text-warning">
            Fija tu tarifa antes de publicar. Guarda un precio por hora más arriba y podrás salir en
            el directorio.
          </p>
        )}

        {!publicado && (
          <p className="mt-3 rounded-base bg-warning-bg px-3.5 py-2.5 text-[12px] text-warning">
            Los estudiantes dejarán de verte y no podrán reservar contigo. Tus clases ya agendadas
            siguen en pie.
          </p>
        )}
      </section>

      {error && (
        <div className="mt-4">
          <AvisoError mensaje={error} />
        </div>
      )}

      {guardado && (
        <p className="mt-4 flex items-center gap-2 rounded-card bg-success-bg px-4 py-3 text-[13px] font-semibold text-success">
          <Check size={16} strokeWidth={2.4} />
          Listo, tu perfil quedó actualizado.
        </p>
      )}

      <BotonPrincipal disabled={guardar.isPending} onClick={() => guardar.mutate()} className="mt-5">
        {guardar.isPending ? "Guardando…" : "Guardar cambios"}
      </BotonPrincipal>
    </main>
  );
}

/**
 * Widget de tarifa: al escribir un precio, pide en vivo el desglose (con debounce) para que el
 * profesor vea qué recibe y qué retiene Orión antes de guardar. El "Guardar tarifa" persiste solo
 * la tarifa (PUT /me/profile/rate), independiente del resto del formulario.
 */
function WidgetTarifa({
  inicial,
  onGuardada,
}: {
  inicial: ProfileResponse;
  onGuardada: () => void;
}) {
  const queryClient = useQueryClient();
  const rateInicial = inicial.rate?.hourlyRateCop ?? inicial.hourlyRateCop ?? 0;

  const [valor, setValor] = useState(rateInicial ? String(rateInicial) : "");
  const [debounced, setDebounced] = useState(rateInicial);
  const numero = Number(valor);
  const valido = Number.isFinite(numero) && numero >= 20000 && numero <= 500000;

  useEffect(() => {
    const id = setTimeout(() => setDebounced(numero), 350);
    return () => clearTimeout(id);
  }, [numero]);

  const preview = useQuery({
    queryKey: ["rate-preview", debounced],
    queryFn: () =>
      apiFetch<RateBreakdownResponse>(`/api/v1/me/profile/rate/preview?rate=${debounced}`),
    enabled: Number.isFinite(debounced) && debounced >= 20000 && debounced <= 500000,
    staleTime: 60_000,
  });

  const guardarTarifa = useMutation({
    mutationFn: () =>
      apiFetch<RateBreakdownResponse>("/api/v1/me/profile/rate", {
        method: "PUT",
        body: { hourlyRateCop: numero },
      }),
    onSuccess: (breakdown) => {
      // La tarifa habilita publicar y cambia lo que ve el perfil; refrescamos ambos.
      queryClient.setQueryData<ProfileResponse | undefined>(["me", "profile"], (prev) =>
        prev ? { ...prev, rate: breakdown, hourlyRateCop: breakdown.hourlyRateCop, canPublish: true } : prev,
      );
      void queryClient.invalidateQueries({ queryKey: ["professors"] });
      onGuardada();
    },
  });

  const errorTarifa =
    guardarTarifa.error instanceof ApiError ? guardarTarifa.error.message : null;

  // El desglose en vivo viene del preview cuando el valor es válido; si no, muestra el último guardado.
  const desglose: RateBreakdownResponse | undefined =
    valido && preview.data ? preview.data : inicial.rate ?? undefined;

  const pct = desglose?.commissionRateBps != null ? Math.round(desglose.commissionRateBps / 100) : 20;

  return (
    <section className="mt-6 rounded-card bg-accent-peach-soft p-4">
      <h2 className="text-[13.5px] font-bold text-[#8a5a33]">Tu tarifa por hora</h2>
      <p className="mt-0.5 text-[11.5px] text-[#8a5a33]/85">Entre $20.000 y $500.000 por clase de 60 minutos.</p>

      <div className="mt-3 flex items-center gap-2">
        <div className="relative flex-1">
          <span className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-[15px] font-bold text-text-muted">
            $
          </span>
          <input
            type="number"
            inputMode="numeric"
            min={20000}
            max={500000}
            step={1000}
            value={valor}
            onChange={(e) => setValor(e.target.value)}
            aria-label="Tarifa por hora en pesos"
            className="h-[52px] w-full rounded-base border-[1.5px] border-border bg-surface-raised pl-8 pr-4 text-[15px] font-semibold text-text focus:border-primary focus:shadow-focus focus:outline-none"
          />
        </div>
        <Boton
          variante="tinta"
          disabled={!valido || guardarTarifa.isPending || numero === rateInicial}
          onClick={() => guardarTarifa.mutate()}
          className="h-[52px] shrink-0"
        >
          {guardarTarifa.isPending ? <Spinner /> : "Guardar tarifa"}
        </Boton>
      </div>

      {valor && !valido && (
        <p className="mt-2 text-[12px] font-semibold text-error">
          La tarifa debe estar entre $20.000 y $500.000.
        </p>
      )}

      {desglose && (
        <div className="mt-3 rounded-base bg-surface-raised px-4 py-3 text-[13px]">
          <p className="flex items-center justify-between">
            <span className="text-text-secondary">Tú recibes</span>
            <span className="font-display text-[16px] font-bold text-success">
              {precioCop(desglose.earningsCop ?? 0)}
            </span>
          </p>
          <p className="mt-1 flex items-center justify-between text-text-muted">
            <span>Orión retiene ({pct}%)</span>
            <span className="font-semibold">{precioCop(desglose.commissionCop ?? 0)}</span>
          </p>
        </div>
      )}

      {guardarTarifa.isSuccess && !errorTarifa && (
        <p className="mt-2 flex items-center gap-1.5 text-[12.5px] font-semibold text-success">
          <Check size={14} strokeWidth={2.4} />
          Tarifa guardada.
        </p>
      )}
      {errorTarifa && (
        <div className="mt-2">
          <AvisoError mensaje={errorTarifa} />
        </div>
      )}
    </section>
  );
}
