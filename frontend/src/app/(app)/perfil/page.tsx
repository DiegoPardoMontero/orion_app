"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import { BadgeCheck, Eye, Plus, Sparkles, UserPlus, X } from "lucide-react";
import Link from "next/link";
import { Suspense, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";
import { CambiarFoto } from "@/components/CambiarFoto";
import { MisHorarios } from "@/components/profesor/MisHorarios";
import { bordeSegun, ContadorPalabras } from "@/components/ContadorPalabras";
import { Cargando, ErrorCarga } from "@/components/estados";
import { Campo, Toggle } from "@/components/ui";
import { EdicionEnPagina, useFormularioEditable } from "@/components/edicion/EdicionEnPagina";
import { DiscoIdioma } from "@/components/DiscoIdioma";
import { apiFetch } from "@/lib/api/fetch";
import type {
  GoalResponse,
  LanguageResponse,
  ProfileResponse,
  RateBreakdownResponse,
} from "@/lib/api/types";
import { precioCop } from "@/lib/format";
import { etiquetaNivel, NIVELES } from "@/lib/i18n";
import { estadoBio, estadoTitular } from "@/lib/perfil-profesor";
import { minutos, useCifras } from "@/lib/cifras";
import { SelectorDePais } from "@/components/SelectorDePais";

/** El idioma tal como lo edita el profesor: código + si es nativo + niveles que enseña. */
type LangEdit = { code: string; isNative: boolean; levels: string[] };

type SeccionPerfil = "perfil" | "horarios";

/**
 * «Mi perfil» del profesor: lo que ven los estudiantes y sus horarios, en dos secciones de una misma
 * pantalla (24/09/2026: «fusiona disponibilidad con lo demás del profesor»). La sección va en la
 * dirección —`?seccion=horarios`— para poder enlazarla desde un correo o el recorrido.
 */
export default function PerfilPage() {
  return (
    <Suspense fallback={null}>
      <Perfil />
    </Suspense>
  );
}

function Perfil() {
  const seccion: SeccionPerfil = useSearchParams().get("seccion") === "horarios" ? "horarios" : "perfil";
  const perfil = useQuery({
    queryKey: ["me", "profile"],
    queryFn: () => apiFetch<ProfileResponse>("/api/v1/me/profile"),
  });

  // Las dos pestañas con el mismo ancho, para que la cabecera no salte al cambiar de una a otra.
  if (seccion === "horarios") {
    return (
      <main className="mx-auto w-full max-w-md px-5 py-5 lg:max-w-[1180px] lg:px-12 lg:py-8">
        <Cabecera seccion="horarios" />
        <MisHorarios />
      </main>
    );
  }

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

function Cabecera({ seccion }: { seccion: SeccionPerfil }) {
  const secciones: { clave: SeccionPerfil; label: string; href: string }[] = [
    { clave: "perfil", label: "Perfil público", href: "/perfil" },
    { clave: "horarios", label: "Mis horarios", href: "/perfil?seccion=horarios" },
  ];
  return (
    <>
      <h1 className="font-display text-h1 font-bold">Mi perfil</h1>
      <nav className="mt-4 -mx-5 overflow-x-auto px-5 lg:mx-0 lg:px-0" aria-label="Secciones de tu perfil">
        <ul className="flex w-max gap-1.5">
          {secciones.map((s) => (
            <li key={s.clave}>
              <Link
                href={s.href}
                scroll={false}
                aria-current={seccion === s.clave ? "page" : undefined}
                className={`inline-flex h-9 items-center rounded-pill px-3.5 text-[13px] font-semibold transition-colors focus-visible:shadow-focus ${
                  seccion === s.clave
                    ? "bg-primary text-on-primary"
                    : "bg-surface-sunken text-text-secondary hover:bg-border/60 hover:text-text"
                }`}
              >
                {s.label}
              </Link>
            </li>
          ))}
        </ul>
      </nav>
    </>
  );
}

function FormularioPerfil({ inicial }: { inicial: ProfileResponse }) {
  return (
    <main className="mx-auto w-full max-w-md px-5 py-5 lg:max-w-[1180px] lg:px-12 lg:py-8">
      <Cabecera seccion="perfil" />
      <p className="mt-4 text-[12.5px] text-text-secondary">
        Esto es lo que ven los estudiantes. Cambia lo que quieras y guarda con la barra que aparece abajo.
      </p>
      <Link
        href="/invitar"
        className="mt-4 flex min-h-11 items-center justify-between gap-3 rounded-card bg-accent-lavender-soft px-4 py-3 text-[13.5px] transition-colors hover:bg-info-bg focus-visible:shadow-focus"
      >
        <span className="flex items-center gap-2 font-semibold text-text">
          <UserPlus size={16} strokeWidth={2} className="shrink-0 text-[#5e4a8a]" />
          Invita a tus estudiantes con tu enlace
        </span>
        <span className="shrink-0 text-[12.5px] font-bold text-primary-strong">Compartir</span>
      </Link>

      {/* Las reglas de cancelación y las preguntas frecuentes viven en Ayuda (24/09/2026): son
          documentación, y en el perfil obligaban a pasar por ellas para cambiar la tarifa. */}
      <div className="mt-5">
        <CambiarFoto nombre={inicial.fullName ?? ""} fotoUrl={inicial.photoUrl} />
      </div>

      <EdicionEnPagina>
        <CamposDelPerfil inicial={inicial} />
      </EdicionEnPagina>
    </main>
  );
}

/** Los idiomas como los edita el profesor, desde lo que devuelve el servidor. */
function idiomasDe(perfil: ProfileResponse): LangEdit[] {
  return (perfil.languages ?? []).map((l) => ({
    code: l.code ?? "",
    isNative: l.isNative ?? false,
    levels: [...(l.levels ?? [])].sort(),
  }));
}

/** Para comparar si algo cambió: el mismo orden y sin espacios de sobra. */
const huella = (valor: unknown) => JSON.stringify(valor);

/**
 * Los campos del perfil, editables directo (24/09/2026: sin «Editar mi perfil» abajo del todo). La
 * tarifa entra en el mismo guardado —antes tenía su propio botón—, y se guarda antes que el resto:
 * publicar exige tener tarifa.
 */
function CamposDelPerfil({ inicial }: { inicial: ProfileResponse }) {
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

  const tarifaInicial = inicial.rate?.hourlyRateCop ?? inicial.hourlyRateCop ?? null;
  const [tarifa, setTarifa] = useState(tarifaInicial != null ? String(tarifaInicial) : "");
  const [headline, setHeadline] = useState(inicial.headline ?? "");
  const [bio, setBio] = useState(inicial.bio ?? "");
  const estadoDelTitular = estadoTitular(headline);
  const estadoDeLaBio = estadoBio(bio);
  const [city, setCity] = useState(inicial.city ?? "");
  const [countryCode, setCountryCode] = useState(inicial.countryCode ?? "CO");
  const [yearsExperience, setYearsExperience] = useState(
    inicial.yearsExperience != null ? String(inicial.yearsExperience) : "",
  );
  const [education, setEducation] = useState(inicial.education ?? "");
  const [certified, setCertified] = useState(inicial.certified ?? false);
  // La primera clase gratis (V65): un interruptor, sin precio.
  const [acceptsTrial, setAcceptsTrial] = useState(inicial.acceptsTrial ?? false);
  const [langs, setLangs] = useState<LangEdit[]>(idiomasDe(inicial));
  const [goals, setGoals] = useState<string[]>(inicial.goals ?? []);
  const [publicado, setPublicado] = useState(inicial.isPublished ?? false);

  /** Vuelve a lo que dice el servidor: al descartar, y después de guardar (ya normalizado). */
  function aplicar(p: ProfileResponse) {
    const t = p.rate?.hourlyRateCop ?? p.hourlyRateCop ?? null;
    setTarifa(t != null ? String(t) : "");
    setHeadline(p.headline ?? "");
    setBio(p.bio ?? "");
    setCity(p.city ?? "");
    setCountryCode(p.countryCode ?? "CO");
    setYearsExperience(p.yearsExperience != null ? String(p.yearsExperience) : "");
    setEducation(p.education ?? "");
    setCertified(p.certified ?? false);
    setAcceptsTrial(p.acceptsTrial ?? false);
    setLangs(idiomasDe(p));
    setGoals(p.goals ?? []);
    setPublicado(p.isPublished ?? false);
  }

  const cuerpo = {
    headline: headline.trim(),
    bio: bio.trim(),
    countryCode: countryCode.trim(),
    city: city.trim(),
    yearsExperience: yearsExperience ? Number(yearsExperience) : null,
    education: education.trim(),
    certified,
    acceptsTrial,
    languages: langs.filter((l) => l.code).map((l) => ({ ...l, levels: [...l.levels].sort() })),
    goals: [...goals].sort(),
    isPublished: publicado,
  };
  const guardadoEnServidor = {
    headline: (inicial.headline ?? "").trim(),
    bio: (inicial.bio ?? "").trim(),
    countryCode: (inicial.countryCode ?? "CO").trim(),
    city: (inicial.city ?? "").trim(),
    yearsExperience: inicial.yearsExperience ?? null,
    education: (inicial.education ?? "").trim(),
    certified: inicial.certified ?? false,
    acceptsTrial: inicial.acceptsTrial ?? false,
    languages: idiomasDe(inicial),
    goals: [...(inicial.goals ?? [])].sort(),
    isPublished: inicial.isPublished ?? false,
  };
  const numeroTarifa = Number(tarifa);
  const cambioLaTarifa = tarifa.trim() !== "" && numeroTarifa !== tarifaInicial;
  const cambioElPerfil = huella(cuerpo) !== huella(guardadoEnServidor);
  // Con tarifa guardada o con una nueva por guardar, se puede publicar.
  const tieneTarifa = tarifaInicial != null || cambioLaTarifa;

  useFormularioEditable(
    cambioLaTarifa || cambioElPerfil,
    async () => {
      if (cambioLaTarifa) {
        if (!Number.isFinite(numeroTarifa) || numeroTarifa < 20000 || numeroTarifa > 500000) {
          throw new Error("La tarifa debe estar entre $20.000 y $500.000.");
        }
        const desglose = await apiFetch<RateBreakdownResponse>("/api/v1/me/profile/rate", {
          method: "PUT",
          body: { hourlyRateCop: numeroTarifa },
        });
        queryClient.setQueryData<ProfileResponse | undefined>(["me", "profile"], (prev) =>
          prev ? { ...prev, rate: desglose, hourlyRateCop: desglose.hourlyRateCop, canPublish: true } : prev,
        );
      }
      if (cambioElPerfil) {
        const actualizado = await apiFetch<ProfileResponse>("/api/v1/me/profile", {
          method: "PUT",
          body: {
            ...cuerpo,
            headline: cuerpo.headline || undefined,
            bio: cuerpo.bio || undefined,
            countryCode: cuerpo.countryCode || undefined,
            city: cuerpo.city || undefined,
            yearsExperience: cuerpo.yearsExperience ?? undefined,
            education: cuerpo.education || undefined,
            nativeLanguage: inicial.nativeLanguage,
          },
        });
        queryClient.setQueryData(["me", "profile"], actualizado);
        aplicar(actualizado);
      }
      // Publicarse o cambiar el perfil altera el directorio que ven los estudiantes y lo que le falta.
      void queryClient.invalidateQueries({ queryKey: ["professors"] });
      void queryClient.invalidateQueries({ queryKey: ["me", "profile", "pending"] });
    },
    () => aplicar(inicial),
  );

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
    <>
      {/* — Tarifa — */}
      <WidgetTarifa valor={tarifa} onValor={setTarifa} guardada={inicial.rate ?? undefined} />

      {/* — Presentación — */}
      <label className="mt-6 block text-[12.5px] font-bold text-text-secondary" htmlFor="headline">
        Título
      </label>
      <p className="mt-0.5 text-[12px] text-text-muted">
        Atrae estudiantes con una frase que muestre tu experiencia.
      </p>
      <Campo
        id="headline"
        type="text"
        maxLength={120}
        value={headline}
        onChange={(event) => setHeadline(event.target.value)}
        placeholder="Conversación en inglés para adultos que ya estudiaron"
        aria-describedby="headline-contador"
        className={`mt-1.5 ${bordeSegun(estadoDelTitular)}`}
      />
      <ContadorPalabras id="headline-contador" estado={estadoDelTitular} />

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
        className={`mt-1.5 w-full rounded-base border-[1.5px] bg-surface-raised px-4 py-3 text-sm placeholder:text-text-muted focus:shadow-focus focus:outline-none ${bordeSegun(estadoDeLaBio)}`}
      />
      <ContadorPalabras id="bio-contador" estado={estadoDeLaBio} />

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
                <DiscoIdioma code={idioma.code ?? ""} size={18} />
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
          <SelectorDePais id="country" value={countryCode} onChange={setCountryCode} className="mt-1.5" />
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
        <div className="rounded-card bg-surface-raised p-4 shadow-sm">
          <label className="flex items-center justify-between gap-3">
            <span className="flex items-center gap-2 text-[13.5px] font-semibold text-text">
              <Sparkles size={16} strokeWidth={2} className="text-primary-strong" />
              Ofrezco la primera clase gratis
            </span>
            <Toggle activo={acceptsTrial} onCambio={setAcceptsTrial} etiqueta="Primera clase gratis" />
          </label>
          <p className="mt-1.5 text-[12.5px] leading-relaxed text-text-muted">
            Una clase de prueba sin costo para cada estudiante que todavía no ha tomado clases contigo: sirve para
            conocerse. No se cobra ni tiene comisión.
          </p>
        </div>
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

        {publicado && !tieneTarifa && (
          <p className="mt-3 rounded-base bg-warning-bg px-3.5 py-2.5 text-[12px] text-warning">
            Fija tu tarifa antes de publicar: escribe un precio por hora más arriba y guarda.
          </p>
        )}

        {!publicado && (
          <p className="mt-3 rounded-base bg-warning-bg px-3.5 py-2.5 text-[12px] text-warning">
            Los estudiantes dejarán de verte y no podrán reservar contigo. Tus clases ya agendadas
            siguen en pie.
          </p>
        )}
      </section>
    </>
  );
}

/**
 * La tarifa: al escribir un precio, pide en vivo el desglose (con debounce) para que el profesor vea
 * qué recibe y qué retiene Orión antes de guardar. Se guarda con el resto del perfil.
 */
function WidgetTarifa({
  valor,
  onValor,
  guardada,
}: {
  valor: string;
  onValor: (valor: string) => void;
  guardada?: RateBreakdownResponse;
}) {
  const numero = Number(valor);
  const valido = Number.isFinite(numero) && numero >= 20000 && numero <= 500000;
  const [debounced, setDebounced] = useState(numero);

  useEffect(() => {
    const id = setTimeout(() => setDebounced(numero), 350);
    return () => clearTimeout(id);
  }, [numero]);

  const cifras = useCifras();
  const preview = useQuery({
    queryKey: ["rate-preview", debounced],
    queryFn: () =>
      apiFetch<RateBreakdownResponse>(`/api/v1/me/profile/rate/preview?rate=${debounced}`),
    enabled: Number.isFinite(debounced) && debounced >= 20000 && debounced <= 500000,
    staleTime: 60_000,
  });

  // El desglose en vivo viene del preview cuando el valor es válido; si no, muestra el último guardado.
  const desglose: RateBreakdownResponse | undefined = valido && preview.data ? preview.data : guardada;

  return (
    <section className="mt-6 rounded-card bg-accent-peach-soft p-4">
      <label htmlFor="tarifa" className="block text-[13.5px] font-bold text-[#8a5a33]">
        Tu tarifa por hora
      </label>
      <p className="mt-0.5 text-[11.5px] text-[#8a5a33]/85">
        Entre $20.000 y $500.000 por clase de {minutos(cifras.classMinutes)}.
      </p>

      <div className="relative mt-3">
        <span className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-[15px] font-bold text-text-muted">
          $
        </span>
        <input
          id="tarifa"
          type="number"
          inputMode="numeric"
          min={20000}
          max={500000}
          step={1000}
          value={valor}
          onChange={(e) => onValor(e.target.value)}
          aria-label="Tarifa por hora en pesos"
          className="h-[52px] w-full rounded-base border-[1.5px] border-border bg-surface-raised pl-8 pr-4 text-[15px] font-semibold text-text focus:border-primary focus:shadow-focus focus:outline-none"
        />
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
            <span>Comisión de Orión</span>
            <span className="font-semibold">{precioCop(desglose.commissionCop ?? 0)}</span>
          </p>
        </div>
      )}
    </section>
  );
}
