"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  AlertCircle,
  ArrowLeft,
  ArrowRight,
  BadgeCheck,
  CheckCircle2,
  Circle,
  FileText,
  MessageSquare,
  Pencil,
  Plus,
  Send,
  ShieldCheck,
  Sparkles,
  Trash2,
  Upload,
  X,
} from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { Avatar } from "@/components/Avatar";
import { CambiarFoto } from "@/components/CambiarFoto";
import { bordeSegun, ContadorPalabras } from "@/components/ContadorPalabras";
import { CuerpoLegal } from "@/components/DocumentoLegal";
import { DiscoIdioma } from "@/components/DiscoIdioma";
import { AvisoError, Cargando, ErrorCarga } from "@/components/estados";
import { Rigel } from "@/components/Rigel";
import { SelectorDeCiudad } from "@/components/SelectorDeCiudad";
import { SelectorDePais } from "@/components/SelectorDePais";
import { Badge, Boton, Campo, Spinner, Toggle } from "@/components/ui";
import { useAcuerdoDelProfesor } from "@/lib/acuerdo";
import { ApiError, apiFetch, uploadFile } from "@/lib/api/fetch";
import type {
  DocumentView,
  GoalResponse,
  LanguageResponse,
  ProfileResponse,
  TeacherApplicationView,
} from "@/lib/api/types";
import {
  aniosDeExperiencia,
  ANIOS_MAXIMO,
  DOC_TIPOS,
  enumerar,
  etiquetaDocumento,
  etiquetaFaltante,
  faltasDelPaso,
  MI_APLICACION_KEY,
  PASO_REVISION,
  PASOS_POSTULACION as PASOS,
  primerPasoIncompleto,
  type BorradorPostulacion,
  type Falta,
} from "@/lib/aplicacion";
import { useMe } from "@/lib/auth/session";
import { etiquetaNivel, etiquetaObjetivo, NIVELES } from "@/lib/i18n";
import { paisConBandera } from "@/lib/paises";
import { estadoBio, estadoTitular } from "@/lib/perfil-profesor";

type LangEdit = { code: string; isNative: boolean; levels: string[] };

/** Lo que el PUT de la postulación acepta. Un campo ausente es «no lo tocó» (el backend fusiona). */
type Cuerpo = {
  headline?: string;
  bio?: string;
  countryCode?: string;
  city?: string;
  nativeLanguage?: string;
  yearsExperience?: number;
  education?: string;
  certified: boolean;
  acceptsTrial: boolean;
  languages?: { code: string; isNative: boolean; levels: string[] }[];
  goals?: string[];
  isPublished: false;
};

/**
 * En escritorio la pantalla usa casi todo el ancho junto al menú (Pardo, 27/09/2026): el formulario
 * en dos columnas y la revisión en rejilla. En el teléfono, una columna como siempre.
 */
const CONTENEDOR = "mx-auto w-full max-w-lg px-5 py-6 lg:max-w-[1600px] lg:px-10 lg:py-8 xl:px-14";

/**
 * Wizard de postulación a profesor. Asegura un borrador editable (crea uno solo si hace falta),
 * guarda el avance al moverse entre pasos y termina con el envío a revisión. Un profesor ya
 * aprobado no debería estar aquí: se le redirige a su perfil; uno en revisión, a su estado.
 */
export default function AplicacionPage() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const { data: me } = useMe();

  // GET primero: así no fabricamos un borrador nuevo a quien ya tiene una postulación viva o
  // aprobada. Un 404 (aún no aplica) sí abre uno.
  const app = useQuery({
    queryKey: MI_APLICACION_KEY,
    queryFn: () => apiFetch<TeacherApplicationView>("/api/v1/me/teacher-application"),
    retry: false,
  });

  const noAplico = app.error instanceof ApiError && app.error.status === 404;
  const status = app.data?.status;
  const editable = status === "DRAFT" || status === "CHANGES_REQUESTED";
  const debeRedirigir =
    status === "APPROVED" || status === "PENDING_REVIEW" || status === "UNDER_REVIEW";
  const debeCrear = noAplico || status === "REJECTED";

  const crear = useMutation({
    mutationFn: () =>
      apiFetch<TeacherApplicationView>("/api/v1/teacher-applications", { method: "POST" }),
    onSuccess: (vista) => queryClient.setQueryData(MI_APLICACION_KEY, vista),
  });

  // Redirección y creación del borrador, una sola vez cada una.
  const yaCreado = useRef(false);
  useEffect(() => {
    if (debeRedirigir) {
      router.replace(status === "APPROVED" ? "/perfil" : "/aplicacion/estado");
      return;
    }
    if (debeCrear && !yaCreado.current && !crear.isPending) {
      yaCreado.current = true;
      crear.mutate();
    }
  }, [debeRedirigir, debeCrear, status, router, crear]);

  const vista = app.data && editable ? app.data : crear.data;

  if (app.isError && !noAplico) {
    return (
      <main className={CONTENEDOR}>
        <ErrorCarga mensaje="No pudimos cargar tu postulación." onReintentar={() => void app.refetch()} />
      </main>
    );
  }

  if (app.isPending || debeRedirigir || !vista) {
    return (
      <main className={CONTENEDOR}>
        <Cargando filas={4} />
      </main>
    );
  }

  // La semilla viaja DENTRO de la postulación. Antes venía de /me/profile, que exige rol
  // PROFESSOR: un aspirante nunca la recibía, así que el wizard se dibujaba vacío y al avanzar de
  // paso mandaba ese vacío al servidor.
  return (
    <Wizard
      key={vista.id}
      vista={vista}
      seed={vista.answers ?? null}
      nombre={me?.fullName ?? ""}
      foto={me?.photoUrl}
    />
  );
}

function Wizard({
  vista,
  seed,
  nombre,
  foto,
}: {
  vista: TeacherApplicationView;
  seed: ProfileResponse | null;
  nombre: string;
  foto?: string | null;
}) {
  const router = useRouter();
  const queryClient = useQueryClient();

  const [headline, setHeadline] = useState(seed?.headline ?? "");
  const [bio, setBio] = useState(seed?.bio ?? "");
  const estadoDelTitular = estadoTitular(headline);
  const estadoDeLaBio = estadoBio(bio);
  const [city, setCity] = useState(seed?.city ?? "");
  const [countryCode, setCountryCode] = useState(seed?.countryCode?.toUpperCase() ?? "CO");
  const [yearsExperience, setYearsExperience] = useState(
    seed?.yearsExperience != null ? String(seed.yearsExperience) : "",
  );
  const [education, setEducation] = useState(seed?.education ?? "");
  const [certified, setCertified] = useState(seed?.certified ?? false);
  const [acceptsTrial, setAcceptsTrial] = useState(seed?.acceptsTrial ?? false);
  const [langs, setLangs] = useState<LangEdit[]>(
    (seed?.languages ?? []).map((l) => ({
      code: l.code ?? "",
      isNative: l.isNative ?? false,
      levels: l.levels ?? [],
    })),
  );
  const [goals, setGoals] = useState<string[]>(seed?.goals ?? []);
  // La foto se sube sola al elegirla; esto la cuenta en cuanto sube, sin esperar a la sesión.
  const [fotoSubida, setFotoSubida] = useState<string | null>(null);
  const fotoActual = fotoSubida ?? foto ?? null;

  const documentos = vista.documents ?? [];
  const borrador: BorradorPostulacion = {
    tieneFoto: !!fotoActual,
    titular: headline,
    bio,
    idiomas: langs,
    objetivos: goals,
    pais: countryCode,
    ciudad: city,
    anios: yearsExperience,
    formacion: education,
    tieneCv: documentos.some((d) => d.docType === "CV"),
    aceptoAcuerdo: vista.agreementAccepted ?? false,
  };
  const faltas = PASOS.map((_, i) => faltasDelPaso(i, borrador));

  // Se entra donde se quedó: el primer paso con algo por completar, o la revisión si ya está todo.
  // Quien vuelve porque le pidieron cambios entra a la revisión, con la nota arriba.
  const [paso, setPaso] = useState(() =>
    vista.status === "CHANGES_REQUESTED" ? PASO_REVISION : primerPasoIncompleto(borrador),
  );
  // Llegó a un paso con «Editar» desde la revisión: su botón es «Guardar y volver a revisar».
  const [revisando, setRevisando] = useState(false);
  // Los pasos en los que ya intentó seguir: solo ahí se marcan en rojo los campos que faltan.
  const [intentados, setIntentados] = useState<number[]>([]);
  const [foco, setFoco] = useState<{ campo: string; vez: number } | null>(null);

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

  /**
   * Solo va lo que ya es válido. El backend fusiona (lo ausente no se toca) y rechaza un título o una
   * presentación cortos: mandar el campo a medias de otro paso bloqueaba el guardado del que sí
   * estaba listo.
   */
  function construirCuerpo(): Cuerpo {
    const idiomasOk = langs.length > 0 && langs.every((l) => l.code && l.levels.length > 0);
    const nativo = langs.find((l) => l.isNative)?.code;
    return {
      headline: estadoTitular(headline).estado === "ok" ? headline.trim() : undefined,
      bio: estadoBio(bio).estado === "ok" ? bio.trim() : undefined,
      countryCode: countryCode || undefined,
      city: city.trim() || undefined,
      nativeLanguage: idiomasOk ? (nativo ?? seed?.nativeLanguage ?? undefined) : undefined,
      yearsExperience: aniosDeExperiencia(yearsExperience) ?? undefined,
      education: education.trim() || undefined,
      certified,
      acceptsTrial,
      languages: idiomasOk
        ? langs.map((l) => ({ code: l.code, isNative: l.isNative, levels: l.levels }))
        : undefined,
      goals: goals.length > 0 ? goals : undefined,
      isPublished: false,
    };
  }

  // Lo último que quedó en el servidor, para no repetir un PUT idéntico en cada paso.
  const guardado = useRef(JSON.stringify(construirCuerpo()));

  const guardar = useMutation({
    mutationFn: (cuerpo: Cuerpo) =>
      apiFetch<TeacherApplicationView>("/api/v1/me/teacher-application", { method: "PUT", body: cuerpo }),
    onSuccess: (actualizada, cuerpo) => {
      guardado.current = JSON.stringify(cuerpo);
      queryClient.setQueryData(MI_APLICACION_KEY, actualizada);
    },
  });
  const errorGuardar = guardar.error instanceof ApiError ? guardar.error.message : null;

  async function sincronizar(): Promise<boolean> {
    const cuerpo = construirCuerpo();
    if (JSON.stringify(cuerpo) === guardado.current) return true;
    try {
      await guardar.mutateAsync(cuerpo);
      return true;
    } catch {
      return false; // el error queda visible; quien llama decide si se queda
    }
  }

  // Tras un intento fallido, el foco va al primer campo que falta (y la pantalla lo muestra).
  useEffect(() => {
    if (!foco) return;
    const el = document.getElementById(foco.campo);
    if (!el) return;
    el.scrollIntoView({ block: "center", behavior: "smooth" });
    el.focus({ preventScroll: true });
  }, [foco]);

  function mostrarFaltas(p: number) {
    setIntentados((antes) => (antes.includes(p) ? antes : [...antes, p]));
    const primera = faltas[p][0];
    if (primera) setFoco((antes) => ({ campo: primera.campo, vez: (antes?.vez ?? 0) + 1 }));
  }

  function mover(destino: number) {
    setPaso(destino);
    if (destino === PASO_REVISION) setRevisando(false);
    if (typeof window !== "undefined") window.scrollTo({ top: 0 });
  }

  /** Un paso adelante solo si todos los anteriores están completos; atrás, siempre. */
  const alcanzable = (destino: number) =>
    destino <= paso || faltas.slice(0, destino).every((f) => f.length === 0);

  async function irA(destino: number) {
    if (destino === paso || guardar.isPending) return;
    if (destino < paso) {
      // Atrás no se bloquea: si el guardado falla, el aviso queda y lo escrito sigue en pantalla.
      await sincronizar();
      mover(destino);
      return;
    }
    const bloqueo = faltas.slice(0, destino).findIndex((f) => f.length > 0);
    if (bloqueo !== -1) {
      // Se lleva a quien quería saltar al primer paso incompleto, con lo que le falta a la vista.
      if (bloqueo !== paso) {
        if (!(await sincronizar())) return;
        mover(bloqueo);
      }
      mostrarFaltas(bloqueo);
      return;
    }
    if (!(await sincronizar())) return;
    mover(destino);
  }

  async function guardarYVolver() {
    if (faltas[paso].length > 0) {
      mostrarFaltas(paso);
      return;
    }
    if (!(await sincronizar())) return;
    mover(PASO_REVISION);
  }

  function editar(destino: number) {
    setRevisando(true);
    mover(destino);
    if (faltas[destino].length > 0) mostrarFaltas(destino);
  }

  const catalogoIdioma = (code: string) => languages.data?.find((l) => l.code === code);
  const nombreIdioma = (code: string) => catalogoIdioma(code)?.nameEs ?? code;
  const banderaIdioma = (code: string) => catalogoIdioma(code)?.flagEmoji ?? "";
  const disponibles = useMemo(
    () => (languages.data ?? []).filter((l) => !langs.some((x) => x.code === l.code)),
    [languages.data, langs],
  );

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

  const cambiarPais = (code: string) => {
    // La ciudad es del catálogo del país: con otro país, la de antes ya no corresponde.
    if (code !== countryCode) setCity("");
    setCountryCode(code);
  };

  // Lo que se dice junto a cada campo, solo después de intentar seguir.
  const conErrores = intentados.includes(paso);
  const errorDe = (campo: string) =>
    conErrores ? (faltas[paso].find((f) => f.campo === campo)?.mensaje ?? null) : null;

  const faltasAqui = paso < PASO_REVISION ? faltas[paso] : [];

  return (
    <main className={CONTENEDOR}>
      <header>
        <p className="text-[12px] font-bold uppercase tracking-[0.1em] text-primary-strong">
          Postulación a profesor
        </p>
        <div className="mt-2 flex items-baseline justify-between gap-3">
          <h1 className="font-display text-h1 font-bold">{PASOS[paso]}</h1>
          <span className="shrink-0 text-[12.5px] font-bold text-text-muted">
            Paso {paso + 1} de {PASOS.length}
          </span>
        </div>
        <IndicadorPasos paso={paso} faltas={faltas} alcanzable={alcanzable} onIr={(p) => void irA(p)} />
      </header>

      <div className="mt-6 lg:mt-8">
        {/* Una columna (Pardo, 28/09/2026: «no uses dobles columnas»), con un tope para que en un
            monitor grande los campos no se estiren de lado a lado. */}
        {paso === 0 && (
          <section className="grid gap-6 lg:max-w-4xl">
            <div className="space-y-5">
              <PanelRigel pose="saludo" texto="Cuéntanos quién eres. Empieza por tu foto y un título que enganche a tus estudiantes." />
              <div>
                <p className="text-[12.5px] font-bold text-text-secondary">Foto de perfil</p>
                <p className="mb-3 mt-0.5 text-[12px] text-text-muted">
                  Una foto tuya, de frente y con buena luz: es lo primero que ven los estudiantes.
                </p>
                <CambiarFoto
                  id="foto"
                  nombre={nombre}
                  fotoUrl={fotoActual}
                  describedBy={errorDe("foto") ? "foto-error" : undefined}
                  onSubida={setFotoSubida}
                />
                <MensajeCampo id="foto-error" mensaje={errorDe("foto")} />
              </div>
            </div>
            <div>
              <label className="block text-[12.5px] font-bold text-text-secondary" htmlFor="headline">
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
                onChange={(e) => setHeadline(e.target.value)}
                placeholder="Ej.: Conversación en inglés para adultos que ya estudiaron"
                aria-describedby="headline-contador"
                aria-invalid={!!errorDe("headline") || undefined}
                className={`mt-1.5 ${errorDe("headline") ? "border-error" : bordeSegun(estadoDelTitular)}`}
              />
              {errorDe("headline") && estadoDelTitular.estado === "vacio" ? (
                <MensajeCampo id="headline-contador" mensaje={errorDe("headline")} />
              ) : (
                <ContadorPalabras id="headline-contador" estado={estadoDelTitular} />
              )}
            </div>
          </section>
        )}

        {paso === 1 && (
          <section className="space-y-6">
            <PanelRigel pose="guia" texto="Esto es lo que buscan los estudiantes: qué enseñas y para qué sirve." />
            <div className="grid gap-6 lg:max-w-4xl">
              <div>
                <label className="block text-[12.5px] font-bold text-text-secondary" htmlFor="bio">
                  Sobre ti
                </label>
                <textarea
                  id="bio"
                  rows={6}
                  value={bio}
                  onChange={(e) => setBio(e.target.value)}
                  placeholder="Cuéntales cómo son tus clases, tu experiencia y qué te hace especial."
                  aria-describedby="bio-contador"
                  aria-invalid={!!errorDe("bio") || undefined}
                  className={`mt-1.5 w-full rounded-base border-[1.5px] bg-surface-raised px-4 py-3 text-sm leading-relaxed placeholder:text-text-muted focus:shadow-focus focus:outline-none lg:min-h-[260px] ${
                    errorDe("bio") ? "border-error" : bordeSegun(estadoDeLaBio)
                  }`}
                />
                {errorDe("bio") && estadoDeLaBio.estado === "vacio" ? (
                  <MensajeCampo id="bio-contador" mensaje={errorDe("bio")} />
                ) : (
                  <ContadorPalabras id="bio-contador" estado={estadoDeLaBio} />
                )}
              </div>

              <div id="idiomas" tabIndex={-1} className="rounded-base focus:outline-none">
                <h2 className="text-[13.5px] font-bold text-text">Idiomas que enseñas</h2>
                <p className="mt-0.5 text-[12px] text-text-muted">
                  Agrega cada idioma y marca los niveles que enseñas en él.
                </p>
                <MensajeCampo mensaje={errorDe("idiomas")} />
                <div className="mt-3 space-y-3">
                  {langs.map((lang) => {
                    const errorNiveles = errorDe(`niveles-${lang.code}`);
                    return (
                      <div
                        key={lang.code}
                        id={`niveles-${lang.code}`}
                        tabIndex={-1}
                        className={`rounded-card bg-surface-raised p-4 shadow-sm focus:outline-none ${errorNiveles ? "ring-[1.5px] ring-error" : ""}`}
                      >
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
                        <div className="mt-3 flex flex-wrap gap-2" role="group" aria-label={`Niveles de ${nombreIdioma(lang.code)}`}>
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
                        <MensajeCampo mensaje={errorNiveles} />
                        <label className="mt-3 flex items-center justify-between gap-3">
                          <span className="text-[12.5px] font-semibold text-text-secondary">Es mi lengua materna</span>
                          <Toggle activo={lang.isNative} onCambio={(v) => marcarNativo(lang.code, v)} etiqueta="Lengua materna" />
                        </label>
                      </div>
                    );
                  })}
                </div>
                {languages.isError ? (
                  <div className="mt-3">
                    <ErrorCarga mensaje="No pudimos cargar los idiomas." onReintentar={() => void languages.refetch()} />
                  </div>
                ) : languages.isPending ? (
                  <div className="mt-3">
                    <Cargando filas={1} />
                  </div>
                ) : (
                  disponibles.length > 0 && (
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
                  )
                )}
              </div>
            </div>

            <div id="objetivos" tabIndex={-1} className="rounded-base focus:outline-none">
              <h2 className="text-[13.5px] font-bold text-text">¿Para qué objetivos preparas?</h2>
              <p className="mt-0.5 text-[12px] text-text-muted">Elige todos los que apliquen.</p>
              <MensajeCampo mensaje={errorDe("objetivos")} />
              {goalsCat.isError ? (
                <div className="mt-3">
                  <ErrorCarga mensaje="No pudimos cargar los objetivos." onReintentar={() => void goalsCat.refetch()} />
                </div>
              ) : goalsCat.isPending ? (
                <div className="mt-3">
                  <Cargando filas={1} />
                </div>
              ) : (
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
              )}
            </div>
          </section>
        )}

        {paso === 2 && (
          <section className="space-y-6">
            <PanelRigel pose="animo" texto="Tu trayectoria da confianza. Comparte de dónde eres y qué has estudiado." />
            <div className="grid gap-5 lg:grid-cols-2 lg:gap-x-10">
              {/* Primero el país y, debajo, la ciudad de ese país (Pardo, 27/09/2026). */}
              <div className="space-y-5">
                <div>
                  <label className="block text-[12.5px] font-bold text-text-secondary" htmlFor="country">País</label>
                  <SelectorDePais id="country" value={countryCode} onChange={cambiarPais} className="mt-1.5" />
                  <MensajeCampo mensaje={errorDe("country")} />
                </div>
                <div>
                  <label className="block text-[12.5px] font-bold text-text-secondary" htmlFor="city">Ciudad</label>
                  <SelectorDeCiudad id="city" pais={countryCode} value={city} onChange={setCity} className="mt-1.5" />
                  <MensajeCampo mensaje={errorDe("city")} />
                </div>
              </div>
              <div className="space-y-5">
                <div>
                  <label className="block text-[12.5px] font-bold text-text-secondary" htmlFor="years">Años de experiencia</label>
                  <Campo
                    id="years"
                    type="number"
                    inputMode="numeric"
                    min={0}
                    max={ANIOS_MAXIMO}
                    step={1}
                    value={yearsExperience}
                    onChange={(e) => setYearsExperience(e.target.value)}
                    placeholder="Ej.: 5"
                    aria-invalid={!!errorDe("years") || undefined}
                    className={`mt-1.5 ${errorDe("years") ? "border-error" : ""}`}
                  />
                  <MensajeCampo mensaje={errorDe("years")} />
                </div>
                <div>
                  <label className="block text-[12.5px] font-bold text-text-secondary" htmlFor="education">Formación</label>
                  <Campo
                    id="education"
                    type="text"
                    maxLength={160}
                    value={education}
                    onChange={(e) => setEducation(e.target.value)}
                    placeholder="Ej.: Licenciatura en Lenguas Modernas"
                    aria-invalid={!!errorDe("education") || undefined}
                    className={`mt-1.5 ${errorDe("education") ? "border-error" : ""}`}
                  />
                  <MensajeCampo mensaje={errorDe("education")} />
                </div>
              </div>
            </div>
            <div className="grid gap-3 lg:grid-cols-2 lg:gap-x-10">
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
                  Ofrezco la primera clase gratis
                </span>
                <Toggle activo={acceptsTrial} onCambio={setAcceptsTrial} etiqueta="Primera clase gratis" />
              </label>
            </div>
          </section>
        )}

        {paso === 3 && <PasoDocumentos documentos={documentos} errorCv={errorDe("doc-CV")} />}

        {paso === 4 && <PasoAcuerdo aceptado={vista.agreementAccepted ?? false} error={errorDe("acuerdo")} />}

        {paso === PASO_REVISION && (
          <Revision
            vista={vista}
            faltas={faltas}
            nombre={nombre}
            foto={fotoActual}
            datos={{ headline, bio, langs, goals, countryCode, city, yearsExperience, education, certified, acceptsTrial }}
            nombreIdioma={nombreIdioma}
            goalsCat={goalsCat.data}
            onEditar={editar}
            sincronizar={sincronizar}
            onEnviado={() => router.replace("/aplicacion/estado")}
          />
        )}
      </div>

      {errorGuardar && (
        <div className="mt-5">
          <AvisoError mensaje={errorGuardar} />
        </div>
      )}

      {/* Navegación. Con algo por completar, el resumen dice qué falta antes y después de intentar
          seguir; «Siguiente» no deja pasar y lleva el foco al primer campo pendiente. */}
      <nav className="mt-8 flex flex-wrap items-center justify-between gap-3 border-t border-surface-sunken pt-5">
        <Boton
          variante="contorno"
          disabled={paso === 0 || guardar.isPending}
          onClick={() => void irA(paso - 1)}
          className="h-12"
        >
          <ArrowLeft size={16} strokeWidth={2} />
          Atrás
        </Boton>
        {faltasAqui.length > 0 && (
          <p
            aria-live="polite"
            className={`order-first flex w-full items-start gap-1.5 text-[12.5px] sm:order-none sm:w-auto sm:flex-1 sm:justify-end sm:text-right ${
              conErrores ? "font-semibold text-error" : "text-text-muted"
            }`}
          >
            {conErrores && <AlertCircle size={15} strokeWidth={2} className="mt-px shrink-0" />}
            Para seguir te falta {enumerar(faltasAqui.map((f) => f.nombre))}.
          </p>
        )}
        {paso < PASO_REVISION &&
          (revisando ? (
            <Boton variante="primario" disabled={guardar.isPending} onClick={() => void guardarYVolver()} className="h-12">
              {guardar.isPending ? <Spinner /> : <CheckCircle2 size={16} strokeWidth={2} />}
              {guardar.isPending ? "Guardando…" : "Guardar y volver a revisar"}
            </Boton>
          ) : (
            <Boton variante="primario" disabled={guardar.isPending} onClick={() => void irA(paso + 1)} className="h-12">
              {guardar.isPending ? (
                <>
                  <Spinner />
                  Guardando…
                </>
              ) : (
                <>
                  Siguiente
                  <ArrowRight size={16} strokeWidth={2} />
                </>
              )}
            </Boton>
          ))}
      </nav>
    </main>
  );
}

/**
 * Los seis pasos, tocables: atrás siempre; adelante solo si los anteriores están completos (si no,
 * lleva al primero que falta y dice qué). Completo en verde con su check; el actual en coral.
 */
function IndicadorPasos({
  paso,
  faltas,
  alcanzable,
  onIr,
}: {
  paso: number;
  faltas: Falta[][];
  alcanzable: (destino: number) => boolean;
  onIr: (destino: number) => void;
}) {
  return (
    <nav aria-label="Pasos de la postulación" className="mt-4">
      <ol className="grid grid-cols-6 gap-1.5 lg:gap-3">
        {PASOS.map((nombrePaso, i) => {
          const actual = i === paso;
          const completo = i < PASO_REVISION && faltas[i].length === 0;
          const puede = alcanzable(i);
          const estado = actual ? "paso actual" : completo ? "completo" : puede ? "por completar" : "completa antes los anteriores";
          return (
            <li key={nombrePaso}>
              <button
                type="button"
                onClick={() => onIr(i)}
                aria-current={actual ? "step" : undefined}
                aria-disabled={!puede || undefined}
                aria-label={`Paso ${i + 1}: ${nombrePaso}, ${estado}`}
                title={puede ? undefined : "Completa antes los pasos anteriores"}
                className={`group flex w-full flex-col gap-2 rounded-base py-2 text-left focus-visible:shadow-focus ${
                  puede ? "cursor-pointer" : "cursor-not-allowed"
                }`}
              >
                <span
                  className={`h-1.5 w-full rounded-pill transition-colors duration-300 ${
                    actual ? "bg-primary" : completo ? "bg-success" : "bg-surface-sunken"
                  }`}
                />
                <span
                  className={`hidden items-start gap-1.5 text-[12.5px] leading-tight lg:flex ${
                    actual
                      ? "font-bold text-text"
                      : puede
                        ? "font-semibold text-text-secondary group-hover:text-text"
                        : "font-semibold text-text-muted"
                  }`}
                >
                  {completo && !actual ? (
                    <CheckCircle2 size={15} strokeWidth={2.2} className="shrink-0 text-success" />
                  ) : (
                    <span
                      className={`grid h-[15px] w-[15px] shrink-0 place-items-center rounded-full text-[9.5px] font-bold ${
                        actual ? "bg-primary text-on-primary" : "border border-border-strong"
                      }`}
                    >
                      {i + 1}
                    </span>
                  )}
                  {nombrePaso}
                </span>
              </button>
            </li>
          );
        })}
      </ol>
    </nav>
  );
}

/** Lo que falta en un campo, en rojo y debajo de él. Sin mensaje no pinta nada. */
function MensajeCampo({ id, mensaje }: { id?: string; mensaje: string | null }) {
  if (!mensaje) return null;
  return (
    <p id={id} className="mt-1.5 flex items-start gap-1.5 text-[12px] font-semibold text-error">
      <AlertCircle size={14} strokeWidth={2} className="mt-px shrink-0" />
      {mensaje}
    </p>
  );
}

/** Franja cálida con Rigel: da calidez sin robar espacio al formulario. */
function PanelRigel({ pose, texto }: { pose: "saludo" | "guia" | "animo"; texto: string }) {
  return (
    <div className="flex items-center gap-3 rounded-card bg-accent-peach-soft p-4">
      <Rigel pose={pose} decorativo className="h-16 w-auto shrink-0" />
      <p className="text-[13px] leading-relaxed text-[#8a5a33]">{texto}</p>
    </div>
  );
}

/* ---------------- Paso 4: documentos ---------------- */

function PasoDocumentos({ documentos, errorCv }: { documentos: DocumentView[]; errorCv: string | null }) {
  return (
    <section className="space-y-4">
      <p className="max-w-prose text-[13.5px] leading-relaxed text-text-secondary">
        Sube tu hoja de vida: es obligatoria. Los certificados son opcionales y suman confianza.
        Aceptamos PDF e imágenes.
      </p>
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        {DOC_TIPOS.map((tipo) => (
          <SubidorDocumento
            key={tipo.code}
            tipo={tipo}
            docs={documentos.filter((d) => d.docType === tipo.code)}
            error={tipo.code === "CV" ? errorCv : null}
          />
        ))}
      </div>
    </section>
  );
}

function SubidorDocumento({
  tipo,
  docs,
  error: errorObligatorio,
}: {
  tipo: (typeof DOC_TIPOS)[number];
  docs: DocumentView[];
  error: string | null;
}) {
  const queryClient = useQueryClient();
  const input = useRef<HTMLInputElement>(null);
  const [error, setError] = useState<string | null>(null);

  const subir = useMutation({
    mutationFn: (file: File) =>
      uploadFile<DocumentView>("/api/v1/me/teacher-application/documents", file, { docType: tipo.code }),
    onSuccess: () => {
      setError(null);
      void queryClient.invalidateQueries({ queryKey: MI_APLICACION_KEY });
    },
    onError: (err) =>
      setError(err instanceof ApiError ? err.message : "No pudimos subir el archivo."),
  });

  const borrar = useMutation({
    mutationFn: (id: string) =>
      apiFetch<void>(`/api/v1/me/teacher-application/documents/${id}`, { method: "DELETE" }),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: MI_APLICACION_KEY }),
  });

  return (
    <div className={`rounded-card bg-surface-raised p-4 shadow-sm ${errorObligatorio ? "ring-[1.5px] ring-error" : ""}`}>
      <div className="flex items-center justify-between gap-3">
        <p className="flex flex-wrap items-center gap-2 text-[13.5px] font-bold text-text">
          <FileText size={16} strokeWidth={1.9} className="text-text-secondary" />
          {tipo.label}
          {tipo.obligatorio && <Badge tono="coral">Obligatorio</Badge>}
        </p>
        <button
          id={`doc-${tipo.code}`}
          type="button"
          onClick={() => input.current?.click()}
          disabled={subir.isPending}
          className="inline-flex min-h-9 shrink-0 items-center gap-1.5 rounded-pill border-[1.5px] border-border px-3.5 text-[12.5px] font-bold text-text transition-colors hover:bg-surface-sunken focus-visible:shadow-focus disabled:opacity-60"
        >
          {subir.isPending ? <Spinner /> : <Upload size={14} strokeWidth={2} />}
          Subir
        </button>
        <input
          ref={input}
          type="file"
          className="hidden"
          onChange={(e) => {
            const file = e.target.files?.[0];
            if (file) subir.mutate(file);
            if (input.current) input.current.value = "";
          }}
        />
      </div>

      {docs.length > 0 && (
        <ul className="mt-3 space-y-2">
          {docs.map((doc) => (
            <li key={doc.id} className="flex items-center justify-between gap-3 rounded-base bg-surface-sunken px-3.5 py-2.5">
              <span className="min-w-0 break-all text-[12.5px] font-semibold text-text">{doc.fileName}</span>
              <button
                type="button"
                aria-label={`Borrar ${doc.fileName}`}
                onClick={() => doc.id && borrar.mutate(doc.id)}
                disabled={borrar.isPending}
                className="grid h-8 w-8 shrink-0 place-items-center rounded-full text-text-muted transition-colors hover:bg-error-bg hover:text-error focus-visible:shadow-focus"
              >
                <Trash2 size={15} strokeWidth={1.9} />
              </button>
            </li>
          ))}
        </ul>
      )}

      {error && <p className="mt-2 text-[12px] font-semibold text-error">{error}</p>}
      <MensajeCampo mensaje={docs.length === 0 ? errorObligatorio : null} />
    </div>
  );
}

/* ---------------- Paso 5: acuerdo ---------------- */

function PasoAcuerdo({ aceptado, error: errorObligatorio }: { aceptado: boolean; error: string | null }) {
  const queryClient = useQueryClient();
  const acuerdo = useAcuerdoDelProfesor();
  const aceptar = useMutation({
    mutationFn: () =>
      apiFetch<void>("/api/v1/me/agreements/TEACHER_AGREEMENT/accept", { method: "POST" }),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: MI_APLICACION_KEY }),
  });

  const error = aceptar.error instanceof ApiError ? aceptar.error.message : null;

  return (
    <section className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_360px] lg:items-start lg:gap-10">
      <div>
        <div className="flex items-center gap-2 text-[13.5px] font-bold text-text">
          <ShieldCheck size={18} strokeWidth={2} className="text-primary-strong" />
          Acuerdo del profesor
          {acuerdo.data && <span className="font-semibold text-text-muted">· versión {acuerdo.data.version}</span>}
        </div>
        {/* El texto viene de la base, con su versión: es lo que queda como constancia de lo aceptado. */}
        <div className="mt-3 max-h-72 overflow-y-auto rounded-card bg-surface-raised p-4 shadow-sm lg:max-h-[60vh] lg:p-6">
          <div className="max-w-[75ch]">
            {acuerdo.data ? (
              <CuerpoLegal body={acuerdo.data.body} />
            ) : acuerdo.isError ? (
              <ErrorCarga mensaje="No pudimos cargar el acuerdo." onReintentar={() => void acuerdo.refetch()} />
            ) : (
              <Cargando filas={3} />
            )}
          </div>
        </div>
      </div>

      <div className="space-y-3 lg:sticky lg:top-6">
        {aceptado ? (
          <p className="flex items-center gap-2 rounded-card bg-success-bg px-4 py-3 text-[13px] font-semibold text-success">
            <CheckCircle2 size={18} strokeWidth={2.2} />
            Aceptaste el acuerdo. ¡Listo!
          </p>
        ) : (
          <label
            className={`flex cursor-pointer items-start gap-3 rounded-card border-[1.5px] bg-surface-raised p-4 ${
              errorObligatorio ? "border-error" : "border-border"
            }`}
          >
            <input
              id="acuerdo"
              type="checkbox"
              checked={false}
              disabled={aceptar.isPending || !acuerdo.data}
              onChange={() => aceptar.mutate()}
              className="mt-0.5 h-5 w-5 accent-primary"
            />
            <span className="text-[13.5px] font-semibold text-text">
              He leído y acepto el acuerdo del profesor de Orión.
            </span>
          </label>
        )}
        <MensajeCampo mensaje={aceptado ? null : errorObligatorio} />
        {error && <AvisoError mensaje={error} />}
      </div>
    </section>
  );
}

/* ---------------- Paso 6: revisar y enviar ---------------- */

type DatosLocales = {
  headline: string;
  bio: string;
  langs: LangEdit[];
  goals: string[];
  countryCode: string;
  city: string;
  yearsExperience: string;
  education: string;
  certified: boolean;
  acceptsTrial: boolean;
};

/**
 * Todo lo que va a enviar, en una sola pantalla y con «Editar» en cada sección (Pardo, 27/09/2026).
 * «Editar» abre ese paso, que en ese modo guarda y vuelve aquí: nada de recorrer seis pantallas.
 * Si la revisión pidió cambios, su nota va arriba.
 */
function Revision({
  vista,
  faltas,
  nombre,
  foto,
  datos: d,
  nombreIdioma,
  goalsCat,
  onEditar,
  sincronizar,
  onEnviado,
}: {
  vista: TeacherApplicationView;
  faltas: Falta[][];
  nombre: string;
  foto: string | null;
  datos: DatosLocales;
  nombreIdioma: (code: string) => string;
  goalsCat?: GoalResponse[];
  onEditar: (paso: number) => void;
  sincronizar: () => Promise<boolean>;
  onEnviado: () => void;
}) {
  const acuerdo = useAcuerdoDelProfesor();
  const documentos = vista.documents ?? [];
  const anios = aniosDeExperiencia(d.yearsExperience);
  const lugar = [d.city.trim(), paisConBandera(d.countryCode)].filter(Boolean).join(", ");

  return (
    <div className="space-y-6">
      {vista.decisionNote && (
        <section className="rounded-card border-l-[3px] border-primary bg-primary-soft p-4 lg:p-5">
          <p className="flex items-center gap-2 text-[12px] font-bold uppercase tracking-[0.08em] text-primary-strong">
            <MessageSquare size={15} strokeWidth={2} />
            Lo que pide la revisión
          </p>
          <p className="mt-1.5 max-w-[75ch] whitespace-pre-line text-[13.5px] leading-relaxed text-text">
            {vista.decisionNote}
          </p>
        </section>
      )}

      <p className="max-w-prose text-[13.5px] leading-relaxed text-text-secondary">
        Esto es lo que vas a enviar. Toca «Editar» en lo que quieras cambiar: lo guardas y vuelves aquí.
      </p>

      {/* Una columna: en tres, un nombre largo quedaba en cuatro líneas y el nombre de un archivo se
          partía por la mitad. Primero lo que se envía, y al final el botón para enviarlo. */}
      <div className="grid gap-6 lg:max-w-4xl">
        <div className="grid gap-4">
          <SeccionRevision titulo="Datos personales" faltas={faltas[0]} onEditar={() => onEditar(0)}>
            <div className="flex items-center gap-4">
              <Avatar nombre={nombre} fotoUrl={foto} size="xl" />
              <p className="min-w-0 break-words text-[15px] font-bold text-text">{nombre}</p>
            </div>
            <DatoRevision etiqueta="Título" valor={d.headline.trim()} />
          </SeccionRevision>

          <SeccionRevision titulo="Enseñanza" faltas={faltas[1]} onEditar={() => onEditar(1)}>
            <DatoRevision etiqueta="Sobre ti" valor={d.bio.trim()} largo />
            <div>
              <p className="text-[12px] font-bold text-text-secondary">Idiomas y niveles</p>
              {d.langs.length ? (
                <ul className="mt-1.5 grid gap-2 sm:grid-cols-2">
                  {d.langs.map((l) => (
                    <li key={l.code} className="flex flex-wrap items-center gap-2 rounded-base bg-surface-sunken px-3 py-2">
                      <DiscoIdioma code={l.code} size={18} />
                      <span className="text-[13px] font-semibold text-text">{nombreIdioma(l.code)}</span>
                      {l.isNative && <Badge tono="menta">Nativo</Badge>}
                      <span className="text-[12px] text-text-muted">
                        {l.levels.map(etiquetaNivel).join(" · ") || "Sin niveles"}
                      </span>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="mt-1 text-[13px] text-text-muted">Todavía no agregaste ninguno.</p>
              )}
            </div>
            <div>
              <p className="text-[12px] font-bold text-text-secondary">Objetivos</p>
              {d.goals.length ? (
                <div className="mt-1.5 flex flex-wrap gap-1.5">
                  {d.goals.map((code) => (
                    <Badge key={code} tono="lavanda">
                      {etiquetaObjetivo(code, goalsCat)}
                    </Badge>
                  ))}
                </div>
              ) : (
                <p className="mt-1 text-[13px] text-text-muted">Todavía no elegiste ninguno.</p>
              )}
            </div>
          </SeccionRevision>
          <SeccionRevision titulo="Experiencia" faltas={faltas[2]} onEditar={() => onEditar(2)}>
            <DatoRevision etiqueta="Lugar" valor={d.city.trim() ? lugar : ""} />
            <DatoRevision
              etiqueta="Años de experiencia"
              valor={anios == null ? "" : anios === 0 ? "Estoy empezando" : `${anios} ${anios === 1 ? "año" : "años"}`}
            />
            <DatoRevision etiqueta="Formación" valor={d.education.trim()} />
            <div className="flex flex-wrap gap-1.5">
              <Badge tono={d.certified ? "menta" : "neutral"}>
                {d.certified ? "Con certificación docente" : "Sin certificación docente"}
              </Badge>
              <Badge tono={d.acceptsTrial ? "melocoton" : "neutral"}>
                {d.acceptsTrial ? "Primera clase gratis" : "Sin clase de prueba gratis"}
              </Badge>
            </div>
          </SeccionRevision>


          <SeccionRevision titulo="Documentos" faltas={faltas[3]} onEditar={() => onEditar(3)}>
            {documentos.length ? (
              <ul className="grid gap-1.5">
                {documentos.map((doc) => (
                  <li key={doc.id} className="flex items-start gap-2 text-[13px]">
                    <FileText size={15} strokeWidth={1.9} className="mt-0.5 shrink-0 text-text-muted" />
                    <span className="min-w-0 flex-1 break-all font-semibold text-text">{doc.fileName}</span>
                    <span className="shrink-0 text-[11.5px] text-text-muted">{etiquetaDocumento(doc.docType)}</span>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="text-[13px] text-text-muted">Todavía no subiste ninguno.</p>
            )}
          </SeccionRevision>

          <SeccionRevision titulo="Acuerdo del profesor" faltas={faltas[4]} onEditar={() => onEditar(4)}>
            {vista.agreementAccepted ? (
              <p className="flex items-center gap-2 text-[13px] font-semibold text-success">
                <CheckCircle2 size={16} strokeWidth={2.2} />
                Aceptado{acuerdo.data ? ` · versión ${acuerdo.data.version}` : ""}
              </p>
            ) : (
              <p className="flex items-center gap-2 text-[13px] font-semibold text-warning">
                <Circle size={16} strokeWidth={2} />
                Todavía sin aceptar
              </p>
            )}
          </SeccionRevision>
        </div>

        <aside>
          <PanelEnviar
            faltas={faltas}
            missing={vista.missing ?? []}
            dias={vista.reviewBusinessDays ?? 3}
            onEditar={onEditar}
            sincronizar={sincronizar}
            onEnviado={onEnviado}
          />
        </aside>
      </div>
    </div>
  );
}

/** Una sección de la revisión, con su acceso directo al paso que la edita y lo que le falta. */
function SeccionRevision({
  titulo,
  faltas,
  onEditar,
  children,
}: {
  titulo: string;
  faltas: Falta[];
  onEditar: () => void;
  children: ReactNode;
}) {
  return (
    <section
      className={`rounded-card bg-surface-raised p-4 shadow-sm lg:p-5 ${
        faltas.length ? "ring-[1.5px] ring-warning/60" : ""
      }`}
    >
      <div className="flex items-center justify-between gap-3">
        <h2 className="flex items-center gap-2 font-display text-[15px] font-bold text-text">
          {faltas.length ? (
            <Circle size={16} strokeWidth={2} className="text-warning" />
          ) : (
            <CheckCircle2 size={16} strokeWidth={2.2} className="text-success" />
          )}
          {titulo}
        </h2>
        <Boton
          variante="fantasma"
          onClick={onEditar}
          aria-label={`Editar ${titulo.toLowerCase()}`}
          className="h-9 shrink-0 px-3 text-[12.5px]"
        >
          <Pencil size={13} strokeWidth={2} />
          Editar
        </Boton>
      </div>
      {faltas.length > 0 && (
        <ul className="mt-2 grid gap-1 rounded-base bg-warning-bg px-3 py-2">
          {faltas.map((f) => (
            <li key={f.campo} className="flex items-start gap-1.5 text-[12.5px] font-semibold text-warning">
              <AlertCircle size={14} strokeWidth={2} className="mt-px shrink-0" />
              {f.mensaje}
            </li>
          ))}
        </ul>
      )}
      <div className="mt-3 grid gap-3">{children}</div>
    </section>
  );
}

function DatoRevision({ etiqueta, valor, largo = false }: { etiqueta: string; valor: string; largo?: boolean }) {
  return (
    <div>
      <p className="text-[12px] font-bold text-text-secondary">{etiqueta}</p>
      <p
        className={`mt-0.5 text-[13.5px] leading-relaxed ${valor ? "text-text" : "text-text-muted"} ${
          largo ? "max-w-[75ch] whitespace-pre-line" : ""
        }`}
      >
        {valor || "Sin completar"}
      </p>
    </div>
  );
}

/**
 * Enviar a revisión. Se enciende cuando no falta nada aquí ni en el servidor (su lista, `missing`,
 * es la que manda al enviar). Lo que falte se toca y lleva directo a ese paso.
 */
function PanelEnviar({
  faltas,
  missing,
  dias,
  onEditar,
  sincronizar,
  onEnviado,
}: {
  faltas: Falta[][];
  missing: string[];
  dias: number;
  onEditar: (paso: number) => void;
  sincronizar: () => Promise<boolean>;
  onEnviado: () => void;
}) {
  const queryClient = useQueryClient();
  const pendientes = faltas
    .slice(0, PASO_REVISION)
    .map((f, paso) => ({ paso, f }))
    .filter((p) => p.f.length > 0);
  // Si aquí está todo pero el servidor aún ve algo (una foto que acaba de subir), se dice lo suyo.
  const delServidor = pendientes.length === 0 ? missing : [];
  const listo = pendientes.length === 0 && missing.length === 0;

  const enviar = useMutation({
    mutationFn: () =>
      apiFetch<TeacherApplicationView>("/api/v1/me/teacher-application/submit", { method: "POST" }),
    onSuccess: (vista) => {
      queryClient.setQueryData(MI_APLICACION_KEY, vista);
      onEnviado();
    },
    onError: () => {
      // El 400 trae la lista de faltantes en el cuerpo, pero apiFetch no la expone: refrescamos la
      // postulación para que `missing` se repinte con la verdad del servidor.
      void queryClient.invalidateQueries({ queryKey: MI_APLICACION_KEY });
    },
  });

  const error = enviar.error instanceof ApiError ? enviar.error.message : null;
  const plazo = `${dias} ${dias === 1 ? "día hábil" : "días hábiles"}`;

  return (
    <section className="space-y-4 rounded-card bg-surface-raised p-5 shadow-sm">
      {listo ? (
        <div className="flex items-center gap-3 rounded-card bg-success-bg p-4">
          <Rigel pose="celebracion" tono="dorado" decorativo className="h-16 w-auto shrink-0" />
          <p className="text-[13.5px] font-semibold text-success">
            ¡Todo listo! Revisa que esté a tu gusto y envía tu postulación a revisión.
          </p>
        </div>
      ) : (
        <div className="rounded-card bg-warning-bg p-4">
          <p className="text-[13.5px] font-bold text-warning">Te falta poco para enviar</p>
          <p className="mt-0.5 text-[12.5px] text-warning/90">Completa esto y vuelve aquí:</p>
        </div>
      )}

      {!listo && (
        <ul className="space-y-2">
          {pendientes.map(({ paso, f }) => (
            <li key={paso}>
              <button
                type="button"
                onClick={() => onEditar(paso)}
                className="flex w-full items-start gap-2.5 rounded-base bg-surface-sunken px-4 py-3 text-left text-[13px] transition-colors hover:bg-border/60 focus-visible:shadow-focus"
              >
                <Circle size={18} strokeWidth={2} className="shrink-0 text-warning" />
                <span className="min-w-0 flex-1">
                  <span className="block font-bold text-text">{PASOS[paso]}</span>
                  <span className="block text-text-secondary">Falta {enumerar(f.map((x) => x.nombre))}.</span>
                </span>
                <ArrowRight size={16} strokeWidth={2} className="mt-0.5 shrink-0 text-text-muted" />
              </button>
            </li>
          ))}
          {delServidor.map((code) => (
            <li key={code} className="flex items-center gap-2.5 rounded-base bg-surface-sunken px-4 py-3 text-[13px] font-semibold text-text">
              <Circle size={18} strokeWidth={2} className="shrink-0 text-warning" />
              {etiquetaFaltante(code)}
            </li>
          ))}
        </ul>
      )}

      {error && <AvisoError mensaje={error} />}

      <Boton
        variante="primario"
        disabled={!listo || enviar.isPending}
        onClick={async () => {
          if (await sincronizar()) enviar.mutate();
        }}
        className="h-[52px] w-full"
      >
        {enviar.isPending ? (
          <>
            <Spinner />
            Enviando…
          </>
        ) : (
          <>
            <Send size={17} strokeWidth={2} />
            Enviar a revisión
          </>
        )}
      </Boton>
      <p className="text-center text-[12px] leading-relaxed text-text-muted">
        La revisamos en un plazo de {plazo} y te avisamos por correo.
      </p>
    </section>
  );
}
