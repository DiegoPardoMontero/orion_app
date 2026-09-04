"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowLeft,
  ArrowRight,
  BadgeCheck,
  CheckCircle2,
  Circle,
  FileText,
  GraduationCap,
  Plus,
  Send,
  ShieldCheck,
  Sparkles,
  Trash2,
  Upload,
  X,
} from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useRef, useState } from "react";
import { CambiarFoto } from "@/components/CambiarFoto";
import { bordeSegun, ContadorPalabras } from "@/components/ContadorPalabras";
import { AvisoError, Cargando, ErrorCarga } from "@/components/estados";
import { Rigel } from "@/components/Rigel";
import { Badge, Boton, Campo, Spinner, Toggle } from "@/components/ui";
import { DiscoIdioma } from "@/components/DiscoIdioma";
import { ApiError, apiFetch, uploadFile } from "@/lib/api/fetch";
import type {
  DocumentView,
  GoalResponse,
  LanguageResponse,
  ProfileResponse,
  TeacherApplicationView,
} from "@/lib/api/types";
import { DOC_TIPOS, etiquetaFaltante, MI_APLICACION_KEY } from "@/lib/aplicacion";
import { useMe } from "@/lib/auth/session";
import { etiquetaNivel, NIVELES } from "@/lib/i18n";
import { estadoBio, estadoTitular } from "@/lib/perfil-profesor";

type LangEdit = { code: string; isNative: boolean; levels: string[] };

const PASOS = [
  "Datos personales",
  "Enseñanza",
  "Experiencia",
  "Documentos",
  "Acuerdo",
  "Revisar y enviar",
] as const;

/**
 * Wizard de postulación a profesor. Asegura un borrador editable (crea uno solo si hace falta),
 * guarda el avance del perfil al pasar de paso y termina con el envío a revisión. Un profesor ya
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

  // El perfil solo se puede leer con rol PROFESSOR; un estudiante que postula arranca en blanco y
  // el borrador guarda su avance en el servidor de todos modos.
  const perfil = useQuery({
    queryKey: ["me", "profile"],
    queryFn: () => apiFetch<ProfileResponse>("/api/v1/me/profile"),
    enabled: me?.role === "PROFESSOR",
    retry: false,
  });

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

  const cargandoPerfil = me?.role === "PROFESSOR" && perfil.isPending;
  const vista = app.data && editable ? app.data : crear.data;

  if (app.isError && !noAplico) {
    return (
      <main className="mx-auto w-full max-w-lg px-5 py-6">
        <ErrorCarga mensaje="No pudimos cargar tu postulación." onReintentar={() => void app.refetch()} />
      </main>
    );
  }

  if (app.isPending || debeRedirigir || cargandoPerfil || !vista) {
    return (
      <main className="mx-auto w-full max-w-lg px-5 py-6">
        <Cargando filas={4} />
      </main>
    );
  }

  return <Wizard key={vista.id} vista={vista} seed={perfil.data ?? null} nombre={me?.fullName ?? ""} foto={me?.photoUrl} />;
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

  const [paso, setPaso] = useState(0);

  const [headline, setHeadline] = useState(seed?.headline ?? "");
  const [bio, setBio] = useState(seed?.bio ?? "");
  const estadoDelTitular = estadoTitular(headline);
  const estadoDeLaBio = estadoBio(bio);
  const [city, setCity] = useState(seed?.city ?? "");
  const [countryCode, setCountryCode] = useState(seed?.countryCode ?? "CO");
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

  const guardar = useMutation({
    mutationFn: () => {
      const nativo = langs.find((l) => l.isNative)?.code;
      return apiFetch<TeacherApplicationView>("/api/v1/me/teacher-application", {
        method: "PUT",
        body: {
          headline: headline.trim() || undefined,
          bio: bio.trim() || undefined,
          countryCode: countryCode.trim() || undefined,
          city: city.trim() || undefined,
          nativeLanguage: nativo ?? seed?.nativeLanguage ?? undefined,
          yearsExperience: yearsExperience ? Number(yearsExperience) : undefined,
          education: education.trim() || undefined,
          certified,
          acceptsTrial,
          languages: langs
            .filter((l) => l.code)
            .map((l) => ({ code: l.code, isNative: l.isNative, levels: l.levels })),
          goals,
          isPublished: false,
        },
      });
    },
    onSuccess: (actualizada) => queryClient.setQueryData(MI_APLICACION_KEY, actualizada),
  });

  const errorGuardar = guardar.error instanceof ApiError ? guardar.error.message : null;

  // Los pasos 0–2 tocan el perfil: al avanzar se guarda el borrador. Los demás persisten solos.
  async function avanzar() {
    if (paso <= 2) {
      try {
        await guardar.mutateAsync();
      } catch {
        return; // el error queda visible; no avanzamos con un guardado fallido
      }
    }
    setPaso((p) => Math.min(PASOS.length - 1, p + 1));
    if (typeof window !== "undefined") window.scrollTo({ top: 0 });
  }

  const nombreIdioma = (code: string) =>
    languages.data?.find((l) => l.code === code)?.nameEs ?? code;
  const banderaIdioma = (code: string) =>
    languages.data?.find((l) => l.code === code)?.flagEmoji ?? "";
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

  const progreso = Math.round(((paso + 1) / PASOS.length) * 100);

  return (
    <main className="mx-auto w-full max-w-lg px-5 py-6 lg:max-w-2xl lg:py-8">
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
        <div className="mt-3 h-2 overflow-hidden rounded-pill bg-surface-sunken" role="progressbar" aria-valuenow={progreso} aria-valuemin={0} aria-valuemax={100}>
          <div className="h-full rounded-pill bg-primary transition-[width] duration-300 ease-standard" style={{ width: `${progreso}%` }} />
        </div>
      </header>

      <div className="mt-6">
        {paso === 0 && (
          <section className="space-y-5">
            <PanelRigel pose="saludo" texto="Cuéntanos quién eres. Empieza por tu foto y un titular que enganche a tus estudiantes." />
            <CambiarFoto nombre={nombre} fotoUrl={foto} />
            <div>
              <label className="block text-[12.5px] font-bold text-text-secondary" htmlFor="headline">
                Titular
              </label>
              <Campo
                id="headline"
                type="text"
                maxLength={120}
                value={headline}
                onChange={(e) => setHeadline(e.target.value)}
                placeholder="Conversación en inglés para adultos que ya estudiaron"
                aria-describedby="headline-contador"
                className={`mt-1.5 ${bordeSegun(estadoDelTitular)}`}
              />
              <ContadorPalabras id="headline-contador" estado={estadoDelTitular} />
            </div>
          </section>
        )}

        {paso === 1 && (
          <section className="space-y-6">
            <PanelRigel pose="guia" texto="Esto es lo que buscan los estudiantes: qué enseñas y para qué sirve." />
            <div>
              <label className="block text-[12.5px] font-bold text-text-secondary" htmlFor="bio">
                Sobre ti
              </label>
              <textarea
                id="bio"
                rows={5}
                value={bio}
                onChange={(e) => setBio(e.target.value)}
                placeholder="Cuéntales cómo son tus clases, tu experiencia y qué te hace especial."
                aria-describedby="bio-contador"
                className={`mt-1.5 w-full rounded-base border-[1.5px] bg-surface-raised px-4 py-3 text-sm placeholder:text-text-muted focus:shadow-focus focus:outline-none ${bordeSegun(estadoDeLaBio)}`}
              />
              <ContadorPalabras id="bio-contador" estado={estadoDeLaBio} />
            </div>

            <div>
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
                      <Toggle activo={lang.isNative} onCambio={(v) => marcarNativo(lang.code, v)} etiqueta="Lengua materna" />
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
            </div>

            {(goalsCat.data ?? []).length > 0 && (
              <div>
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
              </div>
            )}
          </section>
        )}

        {paso === 2 && (
          <section className="space-y-5">
            <PanelRigel pose="animo" texto="Tu trayectoria da confianza. Comparte de dónde eres y qué has estudiado." />
            <div className="grid grid-cols-2 gap-3">
              <div className="col-span-2 sm:col-span-1">
                <label className="block text-[12.5px] font-bold text-text-secondary" htmlFor="city">Ciudad</label>
                <Campo id="city" type="text" maxLength={80} value={city} onChange={(e) => setCity(e.target.value)} placeholder="Bogotá" className="mt-1.5" />
              </div>
              <div className="col-span-2 sm:col-span-1">
                <label className="block text-[12.5px] font-bold text-text-secondary" htmlFor="country">País</label>
                <Campo id="country" type="text" maxLength={2} value={countryCode} onChange={(e) => setCountryCode(e.target.value.toUpperCase())} placeholder="CO" className="mt-1.5 uppercase" />
              </div>
              <div className="col-span-2 sm:col-span-1">
                <label className="block text-[12.5px] font-bold text-text-secondary" htmlFor="years">Años de experiencia</label>
                <Campo id="years" type="number" min={0} max={80} value={yearsExperience} onChange={(e) => setYearsExperience(e.target.value)} placeholder="5" className="mt-1.5" />
              </div>
              <div className="col-span-2">
                <label className="block text-[12.5px] font-bold text-text-secondary" htmlFor="education">Formación</label>
                <Campo id="education" type="text" maxLength={160} value={education} onChange={(e) => setEducation(e.target.value)} placeholder="Licenciatura en Lenguas Modernas" className="mt-1.5" />
              </div>
            </div>
            <div className="space-y-3">
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
            </div>
          </section>
        )}

        {paso === 3 && <PasoDocumentos documentos={vista.documents ?? []} />}

        {paso === 4 && <PasoAcuerdo aceptado={vista.agreementAccepted ?? false} />}

        {paso === 5 && (
          <PasoRevisar
            faltantes={vista.missing ?? []}
            onEnviado={() => router.replace("/aplicacion/estado")}
          />
        )}
      </div>

      {errorGuardar && paso <= 2 && (
        <div className="mt-5">
          <AvisoError mensaje={errorGuardar} />
        </div>
      )}

      {/* Navegación entre pasos. El último paso no lleva "Siguiente": su acción es enviar. */}
      <nav className="mt-8 flex items-center justify-between gap-3">
        <Boton
          variante="contorno"
          disabled={paso === 0 || guardar.isPending}
          onClick={() => setPaso((p) => Math.max(0, p - 1))}
          className="h-12"
        >
          <ArrowLeft size={16} strokeWidth={2} />
          Atrás
        </Boton>
        {paso < PASOS.length - 1 ? (
          <Boton variante="primario" disabled={guardar.isPending} onClick={() => void avanzar()} className="h-12">
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
        ) : (
          <span className="text-[12px] text-text-muted">Revisa y envía abajo</span>
        )}
      </nav>
    </main>
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

function PasoDocumentos({ documentos }: { documentos: DocumentView[] }) {
  return (
    <section className="space-y-4">
      <p className="text-[13.5px] leading-relaxed text-text-secondary">
        Sube tu hoja de vida. Los certificados son opcionales. Aceptamos PDF e imágenes.
      </p>
      {DOC_TIPOS.map((tipo) => (
        <SubidorDocumento
          key={tipo.code}
          tipo={tipo}
          docs={documentos.filter((d) => d.docType === tipo.code)}
        />
      ))}
    </section>
  );
}

function SubidorDocumento({
  tipo,
  docs,
}: {
  tipo: (typeof DOC_TIPOS)[number];
  docs: DocumentView[];
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
    <div className="rounded-card bg-surface-raised p-4 shadow-sm">
      <div className="flex items-center justify-between gap-3">
        <p className="flex items-center gap-2 text-[13.5px] font-bold text-text">
          <FileText size={16} strokeWidth={1.9} className="text-text-secondary" />
          {tipo.label}
          {tipo.obligatorio && <Badge tono="coral">Obligatorio</Badge>}
        </p>
        <button
          type="button"
          onClick={() => input.current?.click()}
          disabled={subir.isPending}
          className="inline-flex min-h-9 items-center gap-1.5 rounded-pill border-[1.5px] border-border px-3.5 text-[12.5px] font-bold text-text transition-colors hover:bg-surface-sunken focus-visible:shadow-focus disabled:opacity-60"
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
              <span className="min-w-0 truncate text-[12.5px] font-semibold text-text">{doc.fileName}</span>
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
    </div>
  );
}

/* ---------------- Paso 5: acuerdo ---------------- */

function PasoAcuerdo({ aceptado }: { aceptado: boolean }) {
  const queryClient = useQueryClient();
  const aceptar = useMutation({
    mutationFn: () =>
      apiFetch<void>("/api/v1/me/agreements/TEACHER_AGREEMENT/accept", { method: "POST" }),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: MI_APLICACION_KEY }),
  });

  const error = aceptar.error instanceof ApiError ? aceptar.error.message : null;

  return (
    <section className="space-y-4">
      <div className="flex items-center gap-2 text-[13.5px] font-bold text-text">
        <ShieldCheck size={18} strokeWidth={2} className="text-primary-strong" />
        Acuerdo del profesor
      </div>
      <div className="max-h-64 overflow-y-auto rounded-card bg-surface-raised p-4 text-[13px] leading-relaxed text-text-secondary shadow-sm">
        <p>Al postularte como profesor en Orión, aceptas que:</p>
        <ul className="mt-2 list-disc space-y-1.5 pl-5">
          <li>La información y los documentos que envías son veraces y tuyos.</li>
          <li>Impartirás tus clases con puntualidad, respeto y profesionalismo.</li>
          <li>Orión retiene una comisión sobre tu tarifa, que verás con claridad antes de publicar.</li>
          <li>El contacto con estudiantes se coordina por los canales oficiales de la plataforma.</li>
          <li>Orión puede revisar tu perfil y suspenderlo si incumples estas condiciones.</li>
        </ul>
      </div>

      {aceptado ? (
        <p className="flex items-center gap-2 rounded-card bg-success-bg px-4 py-3 text-[13px] font-semibold text-success">
          <CheckCircle2 size={18} strokeWidth={2.2} />
          Aceptaste el acuerdo. ¡Listo!
        </p>
      ) : (
        <label className="flex cursor-pointer items-start gap-3 rounded-card border-[1.5px] border-border bg-surface-raised p-4">
          <input
            type="checkbox"
            checked={false}
            disabled={aceptar.isPending}
            onChange={() => aceptar.mutate()}
            className="mt-0.5 h-5 w-5 accent-primary"
          />
          <span className="text-[13.5px] font-semibold text-text">
            He leído y acepto el acuerdo del profesor de Orión.
          </span>
        </label>
      )}

      {error && <AvisoError mensaje={error} />}
    </section>
  );
}

/* ---------------- Paso 6: revisar y enviar ---------------- */

function PasoRevisar({
  faltantes,
  onEnviado,
}: {
  faltantes: string[];
  onEnviado: () => void;
}) {
  const queryClient = useQueryClient();
  const completo = faltantes.length === 0;

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

  return (
    <section className="space-y-4">
      {completo ? (
        <div className="flex items-center gap-3 rounded-card bg-success-bg p-4">
          <Rigel pose="celebracion" tono="dorado" decorativo className="h-16 w-auto shrink-0" />
          <p className="text-[13.5px] font-semibold text-success">
            ¡Todo listo! Revisa que esté a tu gusto y envía tu postulación a revisión.
          </p>
        </div>
      ) : (
        <div className="rounded-card bg-warning-bg p-4">
          <p className="text-[13.5px] font-bold text-warning">Te falta poco para enviar</p>
          <p className="mt-0.5 text-[12.5px] text-warning/90">Completa estos requisitos para poder enviar:</p>
        </div>
      )}

      <ul className="space-y-2">
        {(completo
          ? ["Foto de perfil", "Presentación", "Idiomas y niveles", "Objetivos", "Hoja de vida (CV)", "Acuerdo aceptado"]
          : faltantes.map(etiquetaFaltante)
        ).map((texto, i) => (
          <li
            key={i}
            className="flex items-center gap-2.5 rounded-base bg-surface-raised px-4 py-3 text-[13px] shadow-sm"
          >
            {completo ? (
              <CheckCircle2 size={18} strokeWidth={2.2} className="shrink-0 text-success" />
            ) : (
              <Circle size={18} strokeWidth={2} className="shrink-0 text-warning" />
            )}
            <span className={completo ? "font-semibold text-text" : "font-semibold text-text"}>{texto}</span>
          </li>
        ))}
      </ul>

      {error && <AvisoError mensaje={error} />}

      <Boton
        variante="primario"
        disabled={!completo || enviar.isPending}
        onClick={() => enviar.mutate()}
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

      {!completo && (
        <p className="flex items-center justify-center gap-1.5 text-[12px] text-text-muted">
          <GraduationCap size={14} strokeWidth={1.9} />
          Vuelve a los pasos anteriores para completar lo que falta.
        </p>
      )}
    </section>
  );
}
