"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  BadgeCheck,
  Eye,
  EyeOff,
  Globe,
  GraduationCap,
  Languages,
  MapPin,
  PenLine,
  Plus,
  Sparkles,
  Target,
  UserPlus,
  Wallet,
  X,
} from "lucide-react";
import Link from "next/link";
import { Suspense, useEffect, useId, useLayoutEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { useSearchParams } from "next/navigation";
import { CambiarFoto } from "@/components/CambiarFoto";
import { DatosDePago } from "@/components/profesor/DatosDePago";
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
  ProfessorCard,
  ProfileResponse,
  RateBreakdownResponse,
} from "@/lib/api/types";
import { precioCop } from "@/lib/format";
import { ayudaDeTarifa, type Fundador } from "@/lib/fundador";
import { etiquetaNivel, NIVELES } from "@/lib/i18n";
import { estadoBio, estadoTitular } from "@/lib/perfil-profesor";
import { minutos, useCifras } from "@/lib/cifras";
import { conMiles, posicionTrasCifras, soloDigitos } from "@/lib/tarifa";
import { paisConBandera } from "@/lib/paises";
import { SelectorDePais } from "@/components/SelectorDePais";
import { SelectorDeCiudad } from "@/components/SelectorDeCiudad";
import { Modal } from "@/components/Modal";
import { TarjetaProfesor } from "@/components/profesor/TarjetaProfesor";

/** El idioma tal como lo edita el profesor: código + si es nativo + niveles que enseña. */
type LangEdit = { code: string; isNative: boolean; levels: string[] };

type SeccionPerfil = "perfil" | "horarios" | "pagos";

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
  const pedida = useSearchParams().get("seccion");
  const seccion: SeccionPerfil = pedida === "horarios" || pedida === "pagos" ? pedida : "perfil";
  const perfil = useQuery({
    queryKey: ["me", "profile"],
    queryFn: () => apiFetch<ProfileResponse>("/api/v1/me/profile"),
  });

  // Las dos pestañas con el mismo ancho, para que la cabecera no salte al cambiar de una a otra.
  // Privada: a dónde le paga Orión (brief de liquidaciones, paso 2). No sale en el perfil público.
  if (seccion === "pagos") {
    return (
      <main className="mx-auto w-full max-w-md px-5 py-5 lg:max-w-[1600px] lg:px-10 xl:px-14 lg:py-8">
        <Cabecera seccion="pagos" />
        <DatosDePago />
      </main>
    );
  }

  if (seccion === "horarios") {
    return (
      <main className="mx-auto w-full max-w-md px-5 py-5 lg:max-w-[1600px] lg:px-10 xl:px-14 lg:py-8">
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
    { clave: "pagos", label: "Datos de pago", href: "/perfil?seccion=pagos" },
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
    <main className="mx-auto w-full max-w-md px-5 py-5 lg:max-w-[1600px] lg:px-10 xl:px-14 lg:py-8">
      <Cabecera seccion="perfil" />
      {/* Una sola columna (Pardo, 28/09/2026: «no uses dobles columnas»), con un tope para que en un
          monitor grande los campos no se estiren de lado a lado. */}
      <div className="lg:max-w-[1200px]">
        <p className="mt-4 text-[13px] text-text-secondary">
          Esto es lo que ven los estudiantes. Cambia lo que quieras y guarda con la barra que aparece abajo.
        </p>
        {/* Las reglas de cancelación y las preguntas frecuentes viven en Ayuda (24/09/2026): son
            documentación, y en el perfil obligaban a pasar por ellas para cambiar la tarifa. */}
        <EdicionEnPagina>
          <CamposDelPerfil inicial={inicial} />
        </EdicionEnPagina>
      </div>
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
  const [viendoVistaPrevia, setViendoVistaPrevia] = useState(false);

  // La ciudad es del catálogo del país: con otro país, la de antes ya no corresponde.
  const cambiarPais = (code: string) => {
    if (code !== countryCode) setCity("");
    setCountryCode(code);
  };

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

  // La tarjeta del buscador con lo que hay escrito ahora, guardado o no. La foto sale de la caché
  // porque CambiarFoto la sube por su lado y actualiza ["me", "profile"], no este formulario.
  const tarjetaBorrador: ProfessorCard = {
    id: inicial.id,
    fullName: inicial.fullName,
    photoUrl: queryClient.getQueryData<ProfileResponse>(["me", "profile"])?.photoUrl ?? inicial.photoUrl,
    headline: headline.trim() || undefined,
    city: city.trim() || undefined,
    countryCode: countryCode || undefined,
    certified,
    hourlyRateCop: tarifa.trim() !== "" && Number.isFinite(numeroTarifa) ? numeroTarifa : undefined,
    languages: langs
      .filter((l) => l.code)
      .map((l) => ({ code: l.code, nameEs: nombreIdioma(l.code), isNative: l.isNative })),
    levels: NIVELES.filter((n) => langs.some((l) => l.levels.includes(n))),
    goals,
  };

  const ubicacion = [city.trim(), paisConBandera(countryCode)].filter(Boolean).join(", ");

  return (
    <>
      <Portada
        inicial={inicial}
        titular={headline.trim()}
        ubicacion={ubicacion}
        idiomas={langs.filter((l) => l.code).map((l) => ({ code: l.code, nombre: nombreIdioma(l.code) }))}
        onVistaPrevia={() => setViendoVistaPrevia(true)}
      />

      {/* — Tarifa — */}
      <WidgetTarifa
        valor={tarifa}
        onValor={setTarifa}
        guardada={inicial.rate ?? undefined}
        gratisPorOrion={tarifaInicial === 0}
        baseBps={inicial.baseRateBps}
        fundador={inicial.founder}
      />

      {/* — Visible u oculto: justo debajo de la tarifa, para saberlo de un vistazo (27/09/2026) — */}
      <section
        className={`mt-4 rounded-card p-5 lg:px-7 ${publicado ? "bg-success-bg" : "bg-warning-bg"}`}
        aria-label="Visibilidad de tu perfil"
      >
        <div className="flex items-center gap-3.5">
          <span
            className={`grid h-10 w-10 shrink-0 place-items-center rounded-full bg-surface-raised ${publicado ? "text-success" : "text-warning"}`}
          >
            {publicado ? <Eye size={18} strokeWidth={2} /> : <EyeOff size={18} strokeWidth={2} />}
          </span>
          <div className="min-w-0 flex-1">
            <p className={`font-display text-[16px] font-bold leading-tight ${publicado ? "text-success" : "text-warning"}`}>
              {publicado ? "Tu perfil está visible" : "Tu perfil está oculto"}
            </p>
            <p className="mt-0.5 text-[12.5px] text-text-secondary">
              {publicado
                ? "Los estudiantes pueden verte y reservar"
                : "Los estudiantes no te ven ni pueden reservar contigo. Tus clases ya agendadas siguen en pie."}
            </p>
          </div>
          <Toggle activo={publicado} onCambio={setPublicado} etiqueta="Perfil visible" />
        </div>

        {publicado !== (inicial.isPublished ?? false) && (
          <p className="mt-3 text-[12px] font-semibold text-text-secondary">
            Guarda con la barra de abajo para que el cambio se aplique.
          </p>
        )}

        {publicado && !tieneTarifa && (
          <p className="mt-3 rounded-base bg-warning-bg px-3.5 py-2.5 text-[12px] text-warning">
            Fija tu tarifa antes de publicar: escribe un precio por hora aquí arriba y guarda.
          </p>
        )}
      </section>

      {viendoVistaPrevia && (
        <Modal titulo="Así te ven los estudiantes" onCerrar={() => setViendoVistaPrevia(false)}>
          <VistaPrevia tarjeta={tarjetaBorrador} bio={bio.trim()} visible={publicado} profesorId={inicial.id} />
        </Modal>
      )}

      {/* — Presentación — */}
      <Seccion
        icono={<PenLine size={18} strokeWidth={2} />}
        tono="coral"
        titulo="Tu presentación"
        descripcion="Lo primero que leen en tu tarjeta y en tu perfil."
      >
        <div className="max-w-4xl">
          <label className="block text-[12.5px] font-bold text-text-secondary" htmlFor="headline">
            Título
          </label>
          <p className="mt-0.5 text-[12px] text-text-muted">Atrae estudiantes con una frase que muestre tu experiencia.</p>
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

          <label className="mt-5 block text-[12.5px] font-bold text-text-secondary" htmlFor="bio">
            Sobre ti
          </label>
          <p className="mt-0.5 text-[12px] text-text-muted">Cuéntales cómo son tus clases y con quién trabajas mejor.</p>
          <textarea
            id="bio"
            rows={5}
            value={bio}
            onChange={(event) => setBio(event.target.value)}
            placeholder="Cuéntales cómo son tus clases."
            aria-describedby="bio-contador"
            className={`mt-1.5 w-full rounded-base border-[1.5px] bg-surface-raised px-4 py-3 text-[15px] leading-relaxed placeholder:text-text-muted focus:shadow-focus focus:outline-none ${bordeSegun(estadoDeLaBio)}`}
          />
          <ContadorPalabras id="bio-contador" estado={estadoDeLaBio} />
        </div>
      </Seccion>

      {/* — Idiomas — */}
      <Seccion
        icono={<Languages size={18} strokeWidth={2} />}
        tono="lavanda"
        titulo="Idiomas que enseñas"
        descripcion={langs.length === 0 ? "Agrega al menos un idioma y marca los niveles que enseñas." : "Marca los niveles que enseñas en cada uno."}
      >
        {langs.length > 0 && (
          <ul className="space-y-2.5">
            {langs.map((lang) => (
              <li key={lang.code} className="rounded-base border border-border bg-surface px-4 py-3.5">
                <div className="flex flex-wrap items-center gap-x-5 gap-y-3">
                  <p className="flex w-full items-center gap-2 text-[14.5px] font-bold text-text sm:w-auto sm:min-w-[130px]">
                    <DiscoIdioma code={lang.code} size={22} />
                    {nombreIdioma(lang.code)}
                  </p>
                  <div className="flex flex-1 flex-wrap gap-2">
                    {NIVELES.map((nivel) => (
                      <button
                        key={nivel}
                        type="button"
                        aria-pressed={lang.levels.includes(nivel)}
                        onClick={() => alternarNivel(lang.code, nivel)}
                        className={`min-h-9 rounded-pill px-3.5 py-1.5 text-[12.5px] font-semibold transition-colors focus-visible:shadow-focus ${
                          lang.levels.includes(nivel)
                            ? "bg-primary text-on-primary"
                            : "bg-surface-raised text-text-secondary ring-1 ring-border hover:ring-border-strong hover:text-text"
                        }`}
                      >
                        {etiquetaNivel(nivel)}
                      </button>
                    ))}
                  </div>
                  <label className="flex items-center gap-2.5">
                    <span className="text-[12.5px] font-semibold text-text-secondary">Es mi lengua materna</span>
                    <Toggle activo={lang.isNative} onCambio={(v) => marcarNativo(lang.code, v)} etiqueta="Lengua materna" />
                  </label>
                  <button
                    type="button"
                    aria-label={`Quitar ${nombreIdioma(lang.code)}`}
                    onClick={() => quitarIdioma(lang.code)}
                    className="grid h-9 w-9 place-items-center rounded-full text-text-muted transition-colors hover:bg-surface-sunken hover:text-text focus-visible:shadow-focus"
                  >
                    <X size={16} strokeWidth={1.75} />
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}

        {disponibles.length > 0 && (
          <div className={langs.length > 0 ? "mt-4" : ""}>
            <p className="text-[12px] font-bold text-text-muted">Agregar otro idioma</p>
            <div className="mt-2 flex flex-wrap gap-2">
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
          </div>
        )}
      </Seccion>

      {/* — Objetivos — */}
      {(goalsCat.data ?? []).length > 0 && (
        <Seccion
          icono={<Target size={18} strokeWidth={2} />}
          tono="durazno"
          titulo="¿Para qué objetivos preparas?"
          descripcion="Los estudiantes filtran por objetivo en el buscador."
        >
          <div className="flex flex-wrap gap-2">
            {(goalsCat.data ?? []).map((goal) => (
              <button
                key={goal.code}
                type="button"
                aria-pressed={!!goal.code && goals.includes(goal.code)}
                onClick={() => goal.code && alternarObjetivo(goal.code)}
                className={`min-h-10 rounded-pill px-4 py-2 text-[13.5px] font-semibold transition-colors focus-visible:shadow-focus ${
                  goal.code && goals.includes(goal.code)
                    ? "bg-primary text-on-primary"
                    : "bg-surface-sunken text-text-secondary hover:bg-border/60 hover:text-text"
                }`}
              >
                {goal.nameEs}
              </button>
            ))}
          </div>
        </Seccion>
      )}

      {/* — Experiencia — */}
      <Seccion
        icono={<GraduationCap size={18} strokeWidth={2} />}
        tono="menta"
        titulo="Dónde estás y tu experiencia"
        descripcion="Tu ciudad aparece en tu tarjeta; los años y la formación, en tu perfil."
      >
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <div>
            <label className="block text-[12.5px] font-bold text-text-secondary" htmlFor="country">
              País
            </label>
            <SelectorDePais id="country" value={countryCode} onChange={cambiarPais} className="mt-1.5" />
          </div>
          <div>
            <label className="block text-[12.5px] font-bold text-text-secondary" htmlFor="city">
              Ciudad
            </label>
            <SelectorDeCiudad id="city" pais={countryCode} value={city} onChange={setCity} className="mt-1.5" />
          </div>
          <div>
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
          <div className="sm:col-span-2 lg:col-span-3">
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
        </div>
      </Seccion>

      {/* — Lo que ofreces — */}
      <Seccion
        icono={<Sparkles size={18} strokeWidth={2} />}
        tono="coral"
        titulo="Lo que ofreces"
        descripcion="Dos detalles que ayudan a que te elijan."
      >
        <div className="divide-y divide-border">
          <label className="flex items-center justify-between gap-4 pb-4">
            <span className="min-w-0">
              <span className="flex items-center gap-2 text-[14px] font-bold text-text">
                <BadgeCheck size={16} strokeWidth={2} className="shrink-0 text-success" />
                Tengo certificación docente
              </span>
              <span className="mt-0.5 block text-[12.5px] text-text-muted">Tu tarjeta lo muestra con la insignia «Certificado».</span>
            </span>
            <Toggle activo={certified} onCambio={setCertified} etiqueta="Certificado" />
          </label>
          <div className="pt-4">
            <label className="flex items-center justify-between gap-4">
              <span className="flex items-center gap-2 text-[14px] font-bold text-text">
                <Sparkles size={16} strokeWidth={2} className="shrink-0 text-primary-strong" />
                Ofrezco la primera clase gratis
              </span>
              <Toggle activo={acceptsTrial} onCambio={setAcceptsTrial} etiqueta="Primera clase gratis" />
            </label>
            <p className="mt-0.5 max-w-3xl text-[12.5px] leading-relaxed text-text-muted">
              Una clase de prueba sin costo para cada estudiante que todavía no ha tomado clases contigo: sirve para
              conocerse. No se cobra ni tiene comisión.
            </p>
          </div>
        </div>
      </Seccion>
    </>
  );
}

/**
 * La portada de «Mi perfil»: la foto, el nombre y el título tal como encabezan el perfil —con lo que
 * está escrito, aunque no esté guardado—, y los tres caminos para verse como lo ven: la vista previa,
 * el perfil público y el enlace para compartir.
 */
function Portada({
  inicial,
  titular,
  ubicacion,
  idiomas,
  onVistaPrevia,
}: {
  inicial: ProfileResponse;
  titular: string;
  ubicacion: string;
  idiomas: { code: string; nombre: string }[];
  onVistaPrevia: () => void;
}) {
  const secundario =
    "inline-flex min-h-11 items-center justify-center gap-2 rounded-pill border-[1.5px] border-border bg-surface-raised px-4 text-[13px] font-bold text-text transition-colors hover:bg-surface-sunken focus-visible:shadow-focus sm:min-h-10";
  return (
    <section aria-label="Tu perfil" className="mt-5 overflow-hidden rounded-card bg-surface-raised shadow-sm">
      <div aria-hidden className="gradient-dawn relative h-24 lg:h-32">
        {/* Unas estrellas en el cielo del amanecer, como las de la marca. */}
        <span className="absolute left-[18%] top-[22%] text-[11px] text-on-primary/70">✦</span>
        <span className="absolute left-[46%] top-[14%] text-[8px] text-on-primary/50">✦</span>
        <span className="absolute left-[63%] top-[38%] text-[13px] text-on-primary/60">✦</span>
        <span className="absolute left-[84%] top-[18%] text-[9px] text-on-primary/55">✦</span>
      </div>
      <div className="px-5 pb-6 lg:px-8 lg:pb-7">
        <div className="-mt-12 lg:-mt-14">
          <CambiarFoto enPortada nombre={inicial.fullName ?? ""} fotoUrl={inicial.photoUrl} />
        </div>
        <div className="mt-3 flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between lg:gap-8">
          <div className="min-w-0">
            <p className="font-display text-[24px] font-bold leading-tight text-balance text-text lg:text-[28px]">
              {inicial.fullName}
            </p>
            {titular ? (
              <p className="mt-1 max-w-3xl text-[15px] leading-snug text-pretty text-text-secondary">{titular}</p>
            ) : (
              <p className="mt-1 text-[14px] text-text-muted">Todavía no tienes título: escríbelo abajo, en «Tu presentación».</p>
            )}
            {(ubicacion || idiomas.length > 0) && (
              <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-2 text-[13px] text-text-muted">
                {ubicacion && (
                  <span className="flex items-center gap-1">
                    <MapPin size={14} strokeWidth={1.75} aria-hidden />
                    {ubicacion}
                  </span>
                )}
                {idiomas.map((idioma) => (
                  <span key={idioma.code} className="flex items-center gap-1.5 font-semibold text-text-secondary">
                    <DiscoIdioma code={idioma.code} size={16} />
                    {idioma.nombre}
                  </span>
                ))}
              </div>
            )}
          </div>
          <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap lg:shrink-0 lg:justify-end">
            <button
              type="button"
              onClick={onVistaPrevia}
              className="inline-flex min-h-11 items-center justify-center gap-2 rounded-pill bg-primary px-4 text-[13px] font-bold text-on-primary shadow-primary transition-colors hover:bg-primary-strong focus-visible:shadow-focus sm:min-h-10"
            >
              <Eye size={15} strokeWidth={2} aria-hidden />
              Vista previa: así te ven
            </button>
            {inicial.id && (
              <Link href={`/profesores/${inicial.id}`} className={secundario}>
                <Globe size={15} strokeWidth={2} aria-hidden />
                Ver mi perfil público
              </Link>
            )}
            <Link href="/invitar" className={secundario}>
              <UserPlus size={15} strokeWidth={2} aria-hidden />
              Compartir mi enlace
            </Link>
          </div>
        </div>
      </div>
    </section>
  );
}

const TONOS_DE_SECCION = {
  coral: "bg-primary-soft text-primary-strong",
  lavanda: "bg-accent-lavender-soft text-info",
  durazno: "bg-accent-peach-soft text-warning",
  menta: "bg-success-bg text-success",
} as const;

/** Una sección de «Mi perfil»: su ícono, su título, una línea que dice para qué sirve, y lo suyo. */
function Seccion({
  icono,
  tono,
  titulo,
  descripcion,
  children,
}: {
  icono: ReactNode;
  tono: keyof typeof TONOS_DE_SECCION;
  titulo: string;
  descripcion?: string;
  children: ReactNode;
}) {
  const tituloId = useId();
  return (
    <section aria-labelledby={tituloId} className="mt-5 rounded-card bg-surface-raised p-5 shadow-sm lg:p-7">
      <div className="flex items-start gap-3.5">
        <span className={`grid h-10 w-10 shrink-0 place-items-center rounded-full ${TONOS_DE_SECCION[tono]}`}>{icono}</span>
        <div className="min-w-0 pt-0.5">
          <h2 id={tituloId} className="font-display text-[18px] font-bold leading-tight text-text">
            {titulo}
          </h2>
          {descripcion && <p className="mt-0.5 text-[13px] leading-snug text-text-muted">{descripcion}</p>}
        </div>
      </div>
      <div className="mt-5">{children}</div>
    </section>
  );
}

/**
 * La vista previa sencilla del perfil (Pardo, 27/09/2026): la tarjeta con la que sale en el buscador
 * y su «Sobre ti», con lo que tiene escrito aunque no lo haya guardado. El perfil completo, con la
 * agenda y las reseñas, está a un enlace, y ese sí muestra lo guardado.
 */
function VistaPrevia({
  tarjeta,
  bio,
  visible,
  profesorId,
}: {
  tarjeta: ProfessorCard;
  bio: string;
  visible: boolean;
  profesorId?: string;
}) {
  return (
    <div className="space-y-4">
      <p className="text-[12.5px] text-text-secondary">
        Así sales en el buscador de profesores, con lo que tienes escrito ahora, aunque todavía no lo hayas guardado.
      </p>
      {!visible && (
        <p className="rounded-base bg-warning-bg px-3.5 py-2.5 text-[12px] text-warning">
          Ahora mismo tu perfil está oculto: nadie lo ve hasta que lo actives y guardes.
        </p>
      )}
      <TarjetaProfesor profesor={tarjeta} vistaPrevia />
      <div className="rounded-card bg-surface-raised p-4 shadow-sm">
        <p className="text-[12.5px] font-bold text-text-secondary">Sobre ti</p>
        <p className="mt-1.5 text-[13.5px] leading-relaxed whitespace-pre-line text-text">
          {bio || <span className="text-text-muted">Todavía no has escrito nada aquí.</span>}
        </p>
      </div>
      {profesorId && (
        <Link
          href={`/profesores/${profesorId}`}
          className="inline-flex min-h-11 items-center gap-2 rounded-pill border-[1.5px] border-border px-4 text-[13.5px] font-bold text-text transition-colors hover:bg-surface-sunken focus-visible:shadow-focus"
        >
          <Globe size={15} strokeWidth={2} aria-hidden />
          Abrir mi perfil público completo
        </Link>
      )}
      {profesorId && (
        <p className="-mt-2 text-[11.5px] text-text-muted">El perfil completo muestra lo que ya guardaste.</p>
      )}
    </div>
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
  gratisPorOrion,
  baseBps,
  fundador,
}: {
  /** Solo las cifras, sin puntos: «50000». */
  valor: string;
  onValor: (valor: string) => void;
  guardada?: RateBreakdownResponse;
  /** La tarifa guardada es 0: el admin dejó sus clases gratis. */
  gratisPorOrion: boolean;
  /** La comisión de Orión, para decir qué recibirá cuando termine su beneficio de fundador. */
  baseBps?: number;
  fundador?: Fundador | null;
}) {
  const numero = Number(valor);
  const valido = valor !== "" && numero >= 20000 && numero <= 500000;
  // El 0 lo pone el admin, no el profe: no es un error que tenga que corregir.
  const sigueGratis = gratisPorOrion && valor === "0";
  const [debounced, setDebounced] = useState(numero);

  // Al reformatear con puntos, el cursor vuelve detrás de las mismas cifras en vez de saltar al final.
  const campo = useRef<HTMLInputElement>(null);
  const cifrasAntesDelCursor = useRef<number | null>(null);
  useLayoutEffect(() => {
    if (cifrasAntesDelCursor.current === null || !campo.current) return;
    const posicion = posicionTrasCifras(campo.current.value, cifrasAntesDelCursor.current);
    campo.current.setSelectionRange(posicion, posicion);
    cifrasAntesDelCursor.current = null;
  });

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
    <section className="mt-4 rounded-card bg-accent-peach-soft p-5 lg:p-7">
      <div className="flex items-start gap-3.5">
        <span className="grid h-10 w-10 shrink-0 place-items-center rounded-full bg-surface-raised text-warning">
          <Wallet size={18} strokeWidth={2} />
        </span>
        <div className="min-w-0 pt-0.5">
          <label htmlFor="tarifa" className="block font-display text-[18px] font-bold leading-tight text-warning">
            Tu tarifa por hora
          </label>
          <p className="mt-0.5 text-[13px] text-warning/85">
            Entre $20.000 y $500.000 por clase de {minutos(cifras.classMinutes)}.
          </p>
        </div>
      </div>

      <div className="mt-5 flex flex-col gap-3 sm:flex-row sm:items-stretch">
        <div className="relative sm:w-[300px] sm:shrink-0">
          <span className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-[17px] font-bold text-text-muted">
            $
          </span>
          {/* Texto y no número: «50.000» en un campo numérico se leía 50. */}
          <input
            ref={campo}
            id="tarifa"
            type="text"
            inputMode="numeric"
            autoComplete="off"
            placeholder="50.000"
            value={conMiles(valor)}
            onChange={(e) => {
              const escrito = e.target.value;
              cifrasAntesDelCursor.current = soloDigitos(escrito.slice(0, e.target.selectionStart ?? escrito.length)).length;
              onValor(soloDigitos(escrito));
            }}
            aria-label="Tarifa por hora en pesos"
            className="h-full min-h-[56px] w-full rounded-base border-[1.5px] border-border bg-surface-raised pl-9 pr-4 font-display text-[20px] font-bold text-text focus:border-primary focus:shadow-focus focus:outline-none"
          />
        </div>

        {desglose && !sigueGratis && (
          <div className="grid flex-1 grid-cols-2 gap-3">
            <div className="rounded-base bg-surface-raised px-4 py-3">
              <p className="text-[12px] font-semibold text-text-secondary">Tú recibes</p>
              <p className="font-display text-[20px] font-bold text-success">{precioCop(desglose.earningsCop ?? 0)}</p>
            </div>
            <div className="rounded-base bg-surface-raised px-4 py-3">
              <p className="text-[12px] font-semibold text-text-secondary">Comisión de Orión</p>
              <p className="font-display text-[20px] font-bold text-text-muted">{precioCop(desglose.commissionCop ?? 0)}</p>
            </div>
          </div>
        )}
      </div>

      {sigueGratis ? (
        <p className="mt-3 text-[12.5px] font-semibold text-warning">Orión dejó tus clases gratis por ahora.</p>
      ) : (
        valor &&
        !valido && (
          <p className="mt-3 text-[12px] font-semibold text-error">
            La tarifa debe estar entre $20.000 y $500.000.
          </p>
        )
      )}

      {/* El profe fundador ve su 15 % y lo que recibirá después (brief del profe fundador, paso 3);
          se recalcula mientras escribe, con el mismo redondeo que el backend. */}
      {valido && baseBps != null && (
        <p className="mt-3 text-[12.5px] leading-relaxed text-pretty text-warning">{ayudaDeTarifa(numero, baseBps, fundador)}</p>
      )}
    </section>
  );
}
