"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, BookOpen, Check, ChevronDown, Clock, MessageCircle, Mic, Plus, Sparkles, Square, X } from "lucide-react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { Suspense, useEffect, useRef, useState } from "react";
import { AvisoError, Cargando, Vacio } from "@/components/estados";
import { Constelacion } from "@/components/marca";
import { InvitacionDelActa } from "@/components/InvitacionAPracticar";
import { EjerciciosDelActa } from "@/components/practica/EjerciciosDelActa";
import { Bloque, Boton, Spinner, Tarjeta } from "@/components/ui";
import {
  MAX_NOTAS,
  MAX_PALABRAS,
  MIN_NOTAS,
  type ActaDelEstudiante,
  type ActaDelProfesor,
  type Palabra,
} from "@/lib/actas";
import { ApiError, apiFetch, uploadFile } from "@/lib/api/fetch";
import type { ConversationSummary } from "@/lib/api/types";
import { diaCorto, diaDeLaClase, primerNombre } from "@/lib/practica";
import { useMe } from "@/lib/auth/session";
import { fechaLarga, horaBogota, iniciales } from "@/lib/format";

/**
 * El acta de una clase (Bloque 10, Parte A). La misma ruta sirve a los dos lados y el servidor
 * decide qué vista manda: el profesor escribe, revisa y publica; el estudiante lee lo publicado.
 * Un borrador no existe para el estudiante — el servidor responde 404 y aquí se lee como «todavía
 * no hay resumen», nunca como un error.
 */
export default function ActaPage() {
  return (
    <Suspense fallback={null}>
      <Contenido />
    </Suspense>
  );
}

function Contenido() {
  const { id } = useParams<{ id: string }>();
  const me = useMe();
  const acta = useQuery({
    queryKey: ["lesson-note", id],
    queryFn: () => apiFetch<ActaDelProfesor | ActaDelEstudiante>(`/api/v1/bookings/${id}/lesson-note`),
    retry: false,
  });

  const noHay = acta.error instanceof ApiError && acta.error.status === 404;
  const esProfesor = me.data?.role === "PROFESSOR";

  let cuerpo;
  if (me.isPending || acta.isPending) {
    cuerpo = <Cargando filas={3} />;
  } else if (acta.isError && !noHay) {
    cuerpo = <Vacio titulo="No encontramos esta clase" texto="Puede que el enlace esté incompleto." />;
  } else if (!esProfesor) {
    cuerpo = noHay ? (
      <Vacio
        mascota
        titulo="Todavía no hay resumen"
        texto="Aparecerá aquí cuando tu profesor lo publique, normalmente poco después de la clase."
      />
    ) : (
      <Lectura acta={acta.data as ActaDelEstudiante} />
    );
  } else if (noHay) {
    cuerpo = <CerrarClase bookingId={id} />;
  } else {
    // La llave obliga a montar el editor de nuevo cuando llega otra versión del acta (después de
    // generar): así su estado local arranca siempre de lo que dice el servidor.
    const a = acta.data as ActaDelProfesor;
    cuerpo = <Editor key={`${a.id}-${a.status}-${a.origin}`} acta={a} />;
  }

  return (
    <main className="mx-auto w-full max-w-md px-5 py-6 lg:max-w-4xl lg:px-12 lg:py-8">
      <Link
        href="/mis-clases"
        className="inline-flex min-h-11 items-center gap-1.5 text-[13px] font-semibold text-text-secondary transition-colors hover:text-text focus-visible:shadow-focus"
      >
        <ArrowLeft size={15} strokeWidth={2} />
        Volver a mis clases
      </Link>
      <div className="mt-3">{cuerpo}</div>
    </main>
  );
}

/* ------------------------------------------------------------------ profesor: cerrar la clase */

function CerrarClase({ bookingId }: { bookingId: string }) {
  const queryClient = useQueryClient();
  const [notas, setNotas] = useState("");
  const generar = useMutation({
    mutationFn: () =>
      apiFetch<ActaDelProfesor>(`/api/v1/bookings/${bookingId}/lesson-note/draft`, {
        method: "POST",
        body: { rawInput: notas.trim() },
      }),
    onSuccess: (acta) => {
      queryClient.setQueryData(["lesson-note", bookingId], acta);
      queryClient.invalidateQueries({ queryKey: ["lesson-notes-summary"] });
      queryClient.invalidateQueries({ queryKey: ["lesson-notes-index"] });
    },
  });

  if (generar.isPending) {
    return <Esperando />;
  }

  const suficiente = notas.trim().length >= MIN_NOTAS;
  // Un dictado largo puede pasarse del límite: el textarea solo frena lo que se teclea.
  const sobran = notas.length - MAX_NOTAS;

  return (
    <Tarjeta>
      <h1 className="font-display text-h2 font-bold">Cuéntanos cómo estuvo</h1>
      <p className="mt-1 text-[14px] text-text-secondary">
        Escribe o dicta lo que se te venga a la cabeza. Nosotros le damos forma.
      </p>
      <div className="mt-4 flex flex-wrap items-center justify-between gap-2">
        <label htmlFor="notas" className="sr-only">
          Tus notas de la clase
        </label>
        <BotonDictar
          bookingId={bookingId}
          onTexto={(texto) => setNotas((previas) => (previas.trim() ? `${previas.trimEnd()} ${texto}` : texto))}
        />
      </div>
      <textarea
        id="notas"
        rows={4}
        maxLength={MAX_NOTAS}
        value={notas}
        onChange={(e) => setNotas(e.target.value)}
        placeholder="Trabajamos past simple, sigue diciendo 'I go yesterday', le costó 'used to', quedamos en ver condicionales…"
        className="mt-2 w-full resize-y rounded-base border border-border bg-surface px-4 py-3 text-[14.5px] leading-relaxed focus-visible:shadow-focus focus-visible:outline-none"
      />
      <p
        className={`mt-1 text-right text-[12px] tabular-nums ${sobran > 0 ? "font-semibold text-error" : "text-text-muted"}`}
        aria-live="polite"
      >
        {sobran > 0
          ? `Sobran ${sobran} caracteres: recorta un poco para generar`
          : suficiente
            ? `${notas.length}/${MAX_NOTAS}`
            : `Faltan ${MIN_NOTAS - notas.trim().length} caracteres`}
      </p>
      {generar.isError && (
        <div className="mt-3">
          <AvisoError mensaje={generar.error.message} />
        </div>
      )}
      <div className="mt-4 flex flex-wrap items-center gap-2 sm:justify-end">
        <Link
          href="/mis-clases"
          className="inline-flex min-h-11 flex-1 items-center justify-center rounded-pill px-4 text-[14px] font-semibold text-text-secondary transition-colors hover:bg-surface-sunken hover:text-text focus-visible:shadow-focus sm:flex-none"
        >
          Ahora no
        </Link>
        <Boton disabled={!suficiente || sobran > 0} onClick={() => generar.mutate()} className="flex-1 sm:flex-none">
          <Sparkles size={16} strokeWidth={2} />
          Generar acta
        </Boton>
      </div>
    </Tarjeta>
  );
}

/** Lo que dura un dictado como mucho: más es una clase entera grabada, no «lo que se venga». */
const MAX_SEGUNDOS_DICTADO = 180;

/**
 * Dictar en vez de escribir (añadido por Pardo al Bloque 10). Graba en el navegador, manda el
 * audio, y el texto que vuelve se suma a la caja para que el profesor lo revise antes de generar.
 * Si el navegador no puede grabar, el botón no aparece: nada de interfaz que no funciona.
 */
function BotonDictar({ bookingId, onTexto }: { bookingId: string; onTexto: (texto: string) => void }) {
  const [estado, setEstado] = useState<"listo" | "grabando" | "transcribiendo">("listo");
  const [segundos, setSegundos] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const grabadora = useRef<MediaRecorder | null>(null);
  const trozos = useRef<Blob[]>([]);
  const inicio = useRef(0);
  const reloj = useRef<number | null>(null);

  useEffect(
    () => () => {
      if (reloj.current) window.clearInterval(reloj.current);
      const g = grabadora.current;
      if (!g) return;
      // Quien se va a mitad de un dictado lo abandona: apagar el micrófono dispara `onstop`, y sin
      // esto el audio se subiría y se cobraría para un texto que ya nadie va a leer.
      g.onstop = null;
      g.stream.getTracks().forEach((pista) => pista.stop());
    },
    [],
  );

  const puedeGrabar =
    typeof window !== "undefined" && "MediaRecorder" in window && !!navigator.mediaDevices?.getUserMedia;
  if (!puedeGrabar) return null;

  function detener() {
    if (reloj.current) window.clearInterval(reloj.current);
    reloj.current = null;
    if (grabadora.current?.state === "recording") grabadora.current.stop();
  }

  async function enviar(tipo: string) {
    setEstado("transcribiendo");
    const duracion = Math.max(1, Math.min(MAX_SEGUNDOS_DICTADO, Math.round((Date.now() - inicio.current) / 1000)));
    const audio = new Blob(trozos.current, { type: tipo || "audio/webm" });
    try {
      const r = await uploadFile<{ text: string }>(
        `/api/v1/bookings/${bookingId}/lesson-note/dictation`,
        new File([audio], "dictado", { type: audio.type }),
        { seconds: String(duracion) },
      );
      onTexto(r.text);
    } catch (e) {
      setError(e instanceof Error ? e.message : "No alcanzamos a entender el audio. Intenta de nuevo o escríbelo.");
    } finally {
      setEstado("listo");
    }
  }

  async function empezar() {
    setError(null);
    try {
      const flujo = await navigator.mediaDevices.getUserMedia({ audio: true });
      const tipo = ["audio/webm;codecs=opus", "audio/webm", "audio/mp4", "audio/ogg"].find((t) =>
        MediaRecorder.isTypeSupported(t),
      );
      const g = new MediaRecorder(flujo, tipo ? { mimeType: tipo } : undefined);
      trozos.current = [];
      g.ondataavailable = (e) => {
        if (e.data.size > 0) trozos.current.push(e.data);
      };
      g.onstop = () => {
        flujo.getTracks().forEach((pista) => pista.stop());
        void enviar(g.mimeType);
      };
      g.start();
      grabadora.current = g;
      inicio.current = Date.now();
      setSegundos(0);
      setEstado("grabando");
      reloj.current = window.setInterval(() => {
        const transcurridos = Math.floor((Date.now() - inicio.current) / 1000);
        setSegundos(transcurridos);
        if (transcurridos >= MAX_SEGUNDOS_DICTADO) detener();
      }, 250);
    } catch {
      setError("No pudimos usar el micrófono. Revisa el permiso del navegador o escribe tus notas.");
    }
  }

  const tiempo = `${Math.floor(segundos / 60)}:${String(segundos % 60).padStart(2, "0")}`;

  return (
    <div className="flex w-full flex-col gap-1.5">
      <div className="flex flex-wrap items-center gap-2">
        {estado === "grabando" ? (
          <Boton variante="contorno" onClick={detener} className="min-w-[150px]">
            <Square size={14} strokeWidth={2.4} className="fill-current text-primary" />
            Listo · <span className="tabular-nums">{tiempo}</span>
          </Boton>
        ) : (
          <Boton variante="contorno" onClick={() => void empezar()} disabled={estado === "transcribiendo"}>
            {estado === "transcribiendo" ? <Spinner /> : <Mic size={16} strokeWidth={2} />}
            {estado === "transcribiendo" ? "Escribiendo lo que dijiste…" : "Dictar"}
          </Boton>
        )}
        <span className="text-[12px] text-text-muted">El audio se vuelve texto y no se guarda.</span>
      </div>
      {error && <AvisoError mensaje={error} />}
    </div>
  );
}

/** La espera mientras se ordena el borrador: la constelación, nunca un spinner genérico. */
function Esperando() {
  return (
    <div
      className="flex flex-col items-center gap-4 rounded-card bg-surface-raised p-10 text-center shadow-sm"
      aria-busy="true"
      aria-live="polite"
    >
      <div className="gradient-dawn grid h-[110px] w-[110px] place-items-center overflow-hidden rounded-card">
        <Constelacion className="h-[88px] w-[88px]" />
      </div>
      <p className="font-display text-[20px] font-bold">Ordenando tus notas…</p>
      <p className="max-w-[320px] text-[13.5px] text-text-secondary">
        Toma unos segundos. Nada se publica sin que lo revises.
      </p>
    </div>
  );
}

/* ------------------------------------------------------------------ profesor: revisar y publicar */

function Editor({ acta }: { acta: ActaDelProfesor }) {
  const queryClient = useQueryClient();
  const [trabajado, setTrabajado] = useState(acta.workedOn ?? "");
  const [presente, setPresente] = useState(acta.recurringIssues ?? "");
  const [sigue, setSigue] = useState(acta.nextSteps ?? "");
  const [palabras, setPalabras] = useState<Palabra[]>(acta.vocabulary);
  const [guardado, setGuardado] = useState(false);
  // Publicada se lee; «Corregir» la abre para editar mientras dura la ventana.
  const [corrigiendo, setCorrigiendo] = useState(false);

  const borrador = acta.status === "DRAFT";
  const editando = borrador || corrigiendo;
  const estudiante = acta.studentName ?? "tu estudiante";
  const nombre = acta.studentName ? primerNombre(acta.studentName) : "tu estudiante";
  const cuerpo = () => ({
    workedOn: trabajado,
    recurringIssues: presente,
    nextSteps: sigue,
    vocabulary: palabras,
  });

  const actualizar = (nueva: ActaDelProfesor) => {
    queryClient.setQueryData(["lesson-note", acta.bookingId], nueva);
    queryClient.invalidateQueries({ queryKey: ["lesson-notes-summary"] });
    queryClient.invalidateQueries({ queryKey: ["lesson-notes-index"] });
  };

  const guardar = useMutation({
    mutationFn: () =>
      apiFetch<ActaDelProfesor>(`/api/v1/lesson-notes/${acta.id}`, { method: "PUT", body: cuerpo() }),
    onSuccess: (nueva) => {
      setGuardado(true);
      if (!borrador) setCorrigiendo(false);
      actualizar(nueva);
    },
  });

  // Publicar guarda primero: lo que se publica es lo que está en pantalla, no la última versión
  // guardada.
  const publicar = useMutation({
    mutationFn: async () => {
      await apiFetch<ActaDelProfesor>(`/api/v1/lesson-notes/${acta.id}`, { method: "PUT", body: cuerpo() });
      return apiFetch<ActaDelProfesor>(`/api/v1/lesson-notes/${acta.id}/publish`, { method: "POST" });
    },
    onSuccess: actualizar,
  });

  const ocupado = guardar.isPending || publicar.isPending;
  const error = guardar.error ?? publicar.error;
  const vacia = !trabajado.trim() && !presente.trim() && !sigue.trim() && palabras.length === 0;
  const conIa = acta.origin === "AI_DRAFT";

  return (
    <div className="practica flex flex-col gap-4">
      {/* Quién y cuándo: el acta es de una clase con alguien. */}
      <div className="flex items-center gap-3.5">
        <span className="flex h-[52px] w-[52px] shrink-0 items-center justify-center rounded-full bg-lavanda-soft text-[17px] font-extrabold text-lavanda-ink">
          {acta.studentName ? iniciales(acta.studentName) : "?"}
        </span>
        <div className="flex min-w-0 flex-1 flex-col gap-0.5">
          <h1 className="m-0 font-display text-[24px] leading-[1.1] font-bold lg:text-[32px]">Acta · {estudiante}</h1>
          {acta.classStartsAt && (
            <span className="text-[14px] text-ink-2">
              Clase del {diaDeLaClase(acta.classStartsAt)} · {horaBogota(acta.classStartsAt)}
            </span>
          )}
        </div>
      </div>

      {!editando && (
        <>
          <div className="flex flex-wrap items-center gap-2">
            <span className="inline-flex h-[30px] items-center gap-1.5 rounded-pill bg-ok-bg px-3 text-[13px] font-bold text-ok-ink">
              <Check size={14} strokeWidth={2} aria-hidden />
              Publicada{acta.publishedAt ? ` · ${diaCorto(acta.publishedAt)}, ${horaBogota(acta.publishedAt)}` : ""}
            </span>
            {acta.draftedByAi && (
              <span
                title="Partiste de un borrador hecho con IA"
                className="inline-flex h-[30px] items-center gap-[5px] rounded-pill border border-line bg-white px-2.5 text-[12px] font-semibold text-ink-2"
              >
                <Sparkles size={13} strokeWidth={1.75} aria-hidden />
                Hecha con IA
              </span>
            )}
          </div>
          <div className="flex items-center gap-2.5 rounded-pareja bg-white px-3.5 py-3">
            <Clock size={20} strokeWidth={1.75} className="shrink-0 text-ink-2" aria-hidden />
            <span className="flex-1 text-[14px] leading-[1.45]">
              {acta.editable && acta.editableUntil ? (
                <>
                  Puedes corregirla hasta el{" "}
                  <strong>
                    {diaDeLaClase(acta.editableUntil)}, {horaBogota(acta.editableUntil)}
                  </strong>
                </>
              ) : (
                `Ya pasó el plazo para corregirla. ${nombre.charAt(0).toUpperCase()}${nombre.slice(1)} la ve tal como quedó.`
              )}
            </span>
            {acta.editable && (
              <button
                type="button"
                onClick={() => {
                  setGuardado(false);
                  setCorrigiendo(true);
                }}
                className="h-11 shrink-0 cursor-pointer rounded-pill border-[1.5px] border-ink px-4 text-[14px] font-bold text-ink hover:bg-arena"
              >
                Corregir
              </button>
            )}
          </div>
          {guardado && (
            <p className="m-0 text-[13px] font-semibold text-ok-icon" aria-live="polite">
              Guardada. {nombre.charAt(0).toUpperCase()}
              {nombre.slice(1)} ve la versión nueva.
            </p>
          )}
          <div className="grid gap-3.5 lg:grid-cols-2">
            <SeccionLeida titulo="Lo que trabajamos">
              <p className="m-0 text-[15px] leading-[1.55] whitespace-pre-wrap">{acta.workedOn || "—"}</p>
            </SeccionLeida>
            <SeccionLeida titulo="Errores para tener presente">
              <Errores texto={acta.recurringIssues} />
            </SeccionLeida>
            <SeccionLeida titulo="Palabras nuevas">
              {acta.vocabulary.length > 0 ? (
                <div className="flex flex-wrap gap-2">
                  {acta.vocabulary.map((p) => (
                    <span
                      key={p.term}
                      className="inline-flex min-h-9 items-center gap-1.5 rounded-pill bg-durazno-soft px-3.5 py-1.5 text-[14px]"
                    >
                      <strong lang="en">{p.term}</strong>
                      {p.meaning && <span className="text-[#6B3E1A]">{p.meaning}</span>}
                    </span>
                  ))}
                </div>
              ) : (
                <p className="m-0 text-[15px] text-ink-2">Ninguna esta vez.</p>
              )}
            </SeccionLeida>
            <SeccionLeida titulo="Lo que sigue">
              <p className="m-0 text-[15px] leading-[1.55] whitespace-pre-wrap">{acta.nextSteps || "—"}</p>
            </SeccionLeida>
          </div>
        </>
      )}

      {editando && (
        <>
          {borrador ? (
            <div className="flex items-start gap-2.5 rounded-pareja bg-lavanda-soft px-3.5 py-3 text-[14px] leading-[1.45] text-[#3E2E63]">
              <Sparkles size={18} strokeWidth={1.75} className="mt-px shrink-0" aria-hidden />
              <span>
                {conIa
                  ? `Borrador hecho con IA a partir de tus notas. Revísalo y ajústalo; al publicar, ${nombre} recibe su práctica en cerca de un minuto.`
                  : `Escríbela con tus palabras; tus notas están guardadas. Al publicar, ${nombre} la lee y recibe su práctica en cerca de un minuto.`}
              </span>
            </div>
          ) : (
            <div className="flex items-start gap-2.5 rounded-pareja bg-white px-3.5 py-3 text-[14px] leading-[1.45]">
              <Clock size={18} strokeWidth={1.75} className="mt-px shrink-0 text-ink-2" aria-hidden />
              <span>
                Corrigiendo el acta publicada. {nombre.charAt(0).toUpperCase()}
                {nombre.slice(1)} verá que se actualizó.
              </span>
            </div>
          )}
          <div className="grid gap-3.5 lg:grid-cols-2">
            <CampoSeccion titulo="Lo que trabajamos" valor={trabajado} onCambio={setTrabajado} />
            <CampoSeccion
              titulo="Errores para tener presente"
              valor={presente}
              onCambio={setPresente}
              ayuda="Uno por renglón: lo que dijo → cómo va."
            />
            <Vocabulario palabras={palabras} onCambio={setPalabras} />
            <CampoSeccion titulo="Lo que sigue" valor={sigue} onCambio={setSigue} />
          </div>

          {error && <AvisoError mensaje={error.message} />}

          <div className="flex gap-2.5 lg:justify-end">
            {!borrador && (
              <button
                type="button"
                disabled={ocupado}
                onClick={() => setCorrigiendo(false)}
                className="h-14 flex-1 cursor-pointer rounded-pill px-5 text-[16px] font-bold text-ink hover:bg-arena lg:flex-none"
              >
                Cancelar
              </button>
            )}
            <button
              type="button"
              disabled={ocupado}
              onClick={() => guardar.mutate()}
              className="flex h-14 flex-1 cursor-pointer items-center justify-center gap-2 rounded-pill border-[1.5px] border-ink bg-white px-6 text-[16px] font-bold text-ink hover:bg-arena disabled:opacity-60 lg:flex-none"
            >
              {guardar.isPending && <Spinner />}
              {borrador ? "Guardar" : "Guardar cambios"}
            </button>
            {borrador && (
              <button
                type="button"
                disabled={ocupado || vacia}
                onClick={() => publicar.mutate()}
                className="flex h-14 flex-1 cursor-pointer items-center justify-center gap-2 rounded-pill bg-coral px-8 text-[16px] font-bold text-crema shadow-cta hover:bg-coral-hover disabled:cursor-default disabled:bg-disabled-bg disabled:text-disabled-ink disabled:shadow-none lg:flex-none"
              >
                {publicar.isPending && <Spinner />}
                Publicar
              </button>
            )}
          </div>
          {borrador && guardado && !ocupado && (
            <p className="m-0 text-[13px] font-semibold text-ok-icon lg:text-right" aria-live="polite">
              Guardada. Todavía no la ve {nombre}.
            </p>
          )}
        </>
      )}

      <NotasOriginales texto={acta.rawInput} />
      {!borrador && <EjerciciosDelActa actaId={acta.id} />}
    </div>
  );
}

function SeccionLeida({ titulo, children }: { titulo: string; children: React.ReactNode }) {
  return (
    <article className="flex flex-col gap-2.5 rounded-tarjeta bg-white px-5 py-[18px]">
      <h2 className="m-0 text-[13px] font-extrabold tracking-[.08em] text-lavanda-ink uppercase">{titulo}</h2>
      {children}
    </article>
  );
}

/**
 * Los errores, uno por renglón: «lo que dijo → cómo va» se lee con lo dicho tachado y lo correcto
 * en negrita. Un renglón sin flecha va tal cual: el profesor escribe como quiere.
 */
function Errores({ texto }: { texto: string | null }) {
  const renglones = (texto ?? "")
    .split(/\n+/)
    .map((r) => r.trim())
    .filter(Boolean);
  if (renglones.length === 0) return <p className="m-0 text-[15px] text-ink-2">—</p>;
  return (
    <ul className="m-0 flex list-none flex-col gap-2.5 p-0">
      {renglones.map((r, k) => {
        const [mal, bien] = r.split(/\s*(?:→|->)\s*/);
        return (
          <li key={k} className="flex flex-col gap-0.5 text-[15px]">
            {bien ? (
              <>
                <span lang="en" className="text-ink-2 line-through decoration-[#B8A99B]">
                  {mal}
                </span>
                <strong lang="en">{bien}</strong>
              </>
            ) : (
              <span>{r}</span>
            )}
          </li>
        );
      })}
    </ul>
  );
}

function CampoSeccion({
  titulo,
  valor,
  onCambio,
  ayuda,
}: {
  titulo: string;
  valor: string;
  onCambio: (v: string) => void;
  ayuda?: string;
}) {
  const id = `seccion-${titulo.replace(/\s+/g, "-").toLowerCase()}`;
  return (
    <div className="flex flex-col gap-2 rounded-tarjeta bg-white px-[18px] py-4">
      <label htmlFor={id} className="text-[13px] font-extrabold tracking-[.08em] text-lavanda-ink uppercase">
        {titulo}
      </label>
      <textarea
        id={id}
        rows={3}
        maxLength={1200}
        value={valor}
        onChange={(e) => onCambio(e.target.value)}
        className="min-h-[84px] w-full resize-y rounded-ficha border-[1.5px] border-line px-3.5 py-3 text-[15px] leading-[1.5] outline-none focus:border-2 focus:border-noche focus:shadow-[0_0_0_4px_rgba(185,167,230,.45)]"
      />
      {ayuda && <span className="text-[12.5px] text-ink-3">{ayuda}</span>}
    </div>
  );
}

function Vocabulario({ palabras, onCambio }: { palabras: Palabra[]; onCambio: (p: Palabra[]) => void }) {
  const [nueva, setNueva] = useState("");
  const [agregando, setAgregando] = useState(false);
  const lleno = palabras.length >= MAX_PALABRAS;

  const agregar = () => {
    const term = nueva.trim();
    if (!term || lleno || palabras.some((p) => p.term.toLowerCase() === term.toLowerCase())) return;
    onCambio([...palabras, { term }]);
    setNueva("");
  };

  return (
    <div className="flex flex-col gap-2 rounded-tarjeta bg-white px-[18px] py-4">
      <p className="m-0 text-[13px] font-extrabold tracking-[.08em] text-lavanda-ink uppercase">Palabras nuevas</p>
      <ul className="m-0 flex list-none flex-wrap gap-2 p-0">
        {palabras.map((p) => (
          <li
            key={p.term}
            className="inline-flex min-h-9 items-center gap-1.5 rounded-pill bg-durazno-soft py-1 pr-1 pl-3.5 text-[14px]"
          >
            <strong lang="en">{p.term}</strong>
            {p.meaning && <span className="text-[#6B3E1A]">{p.meaning}</span>}
            <button
              type="button"
              aria-label={`Quitar «${p.term}»`}
              onClick={() => onCambio(palabras.filter((x) => x.term !== p.term))}
              // Se ve de 28 px, pero se toca en 44 (brief, A5): el área crece por fuera sin agrandar la pastilla.
              className="relative flex h-7 w-7 cursor-pointer items-center justify-center rounded-full text-[#6B3E1A] after:absolute after:-inset-2 hover:bg-durazno/40"
            >
              <X size={14} strokeWidth={2} />
            </button>
          </li>
        ))}
        {!lleno && !agregando && (
          <li>
            <button
              type="button"
              onClick={() => setAgregando(true)}
              className="min-h-9 cursor-pointer rounded-pill border-[1.5px] border-dashed border-hueco-line px-3.5 text-[14px] font-bold text-ink hover:bg-arena"
            >
              + Agregar palabra
            </button>
          </li>
        )}
      </ul>
      {!lleno && agregando && (
        <div className="flex gap-2">
          <label htmlFor="palabra-nueva" className="sr-only">
            Agregar una palabra
          </label>
          <input
            id="palabra-nueva"
            autoFocus
            value={nueva}
            maxLength={120}
            onChange={(e) => setNueva(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                e.preventDefault();
                agregar();
              }
              if (e.key === "Escape") setAgregando(false);
            }}
            placeholder="La palabra, en inglés"
            className="min-h-11 min-w-0 flex-1 rounded-pill border-[1.5px] border-line bg-white px-4 text-[14px] outline-none focus:border-2 focus:border-noche"
          />
          <Boton variante="fantasma" onClick={agregar} aria-label="Agregar la palabra">
            <Plus size={16} strokeWidth={2.2} />
          </Boton>
        </div>
      )}
    </div>
  );
}

/** Sus notas en crudo, para contrastar: plegadas, porque ya están ordenadas arriba. Solo el profesor las ve. */
function NotasOriginales({ texto }: { texto: string }) {
  return (
    <details className="group rounded-tarjeta bg-white px-5 py-1">
      <summary className="flex min-h-12 cursor-pointer list-none items-center justify-between text-[15px] font-bold [&::-webkit-details-marker]:hidden">
        Tus notas originales
        <ChevronDown size={18} strokeWidth={1.75} className="transition-transform group-open:rotate-180" aria-hidden />
      </summary>
      <p className="m-0 mb-4 text-[14px] leading-[1.6] whitespace-pre-wrap text-ink-2">{texto}</p>
    </details>
  );
}

/* ------------------------------------------------------------------ estudiante: leer */

function Lectura({ acta }: { acta: ActaDelEstudiante }) {
  const secciones = [
    { titulo: "Lo que trabajaron", texto: acta.workedOn, tono: "melocoton" as const },
    { titulo: "Para tener presente", texto: acta.recurringIssues, tono: "melocoton" as const },
  ];
  return (
    <div className="space-y-4">
      <header>
        <h1 className="font-display text-h2 font-bold">Resumen de tu clase</h1>
        <p className="mt-1 text-[14px] text-text-secondary">
          {acta.professorName ? `Lo escribió ${acta.professorName}` : "Lo escribió tu profesor"}
          {acta.publishedAt ? ` el ${fechaLarga(acta.publishedAt)}.` : "."}
        </p>
        {acta.lastEditedAt && (
          <p className="mt-0.5 text-[12.5px] text-text-muted">Actualizada el {fechaLarga(acta.lastEditedAt)}.</p>
        )}
      </header>

      {secciones
        .filter((s) => s.texto?.trim())
        .map((s) => (
          <Bloque key={s.titulo} tono={s.tono} titulo={s.titulo} icono={<BookOpen size={14} strokeWidth={2.2} />}>
            <p className="whitespace-pre-wrap text-[14.5px] leading-relaxed">{s.texto}</p>
          </Bloque>
        ))}

      {acta.vocabulary.length > 0 && (
        <Bloque tono="lavanda" titulo="Palabras nuevas" icono={<Sparkles size={14} strokeWidth={2.2} />}>
          <ul className="flex flex-wrap gap-2">
            {acta.vocabulary.map((p) => (
              <li key={p.term} className="rounded-pill bg-surface-raised px-3 py-1.5 text-[13px] font-semibold text-[#5e4a8a]">
                {p.term}
                {p.meaning && <span className="font-normal text-text-secondary"> · {p.meaning}</span>}
              </li>
            ))}
          </ul>
        </Bloque>
      )}

      {acta.nextSteps?.trim() && (
        <Bloque tono="lavanda" titulo="Lo que sigue" icono={<BookOpen size={14} strokeWidth={2.2} />}>
          <p className="whitespace-pre-wrap text-[14.5px] leading-relaxed">{acta.nextSteps}</p>
        </Bloque>
      )}

      <InvitacionDelActa actaId={acta.id} />
      {acta.professorId && <Escribirle profesorId={acta.professorId} nombre={acta.professorName} />}
    </div>
  );
}

/**
 * El acta no se responde (brief, D3): lo que el estudiante quiera decir sobre ella va por la
 * mensajería. Abre el hilo con su profesor —o reencuentra el que ya había— y salta a él.
 */
function Escribirle({ profesorId, nombre }: { profesorId: string; nombre: string | null }) {
  const router = useRouter();
  const escribir = useMutation({
    mutationFn: () =>
      apiFetch<ConversationSummary>("/api/v1/conversations", { method: "POST", body: { counterpartId: profesorId } }),
    onSuccess: (conv) => {
      if (conv.id) router.push(`/mensajes/${conv.id}`);
    },
  });
  return (
    <div className="flex flex-wrap items-center justify-between gap-2 px-1">
      <p className="text-[13.5px] text-text-secondary">
        ¿Algo que quieras decirle{nombre ? ` a ${primerNombre(nombre)}` : ""} sobre la clase?
      </p>
      <Boton variante="contorno" disabled={escribir.isPending} onClick={() => escribir.mutate()}>
        <MessageCircle size={16} strokeWidth={2} />
        Escríbele
      </Boton>
      {escribir.isError && <AvisoError mensaje={escribir.error.message} />}
    </div>
  );
}
