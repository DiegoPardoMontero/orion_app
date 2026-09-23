"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, BookOpen, Check, NotebookPen, Plus, Sparkles, X } from "lucide-react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { Suspense, useState } from "react";
import { AvisoError, Cargando, Vacio } from "@/components/estados";
import { Constelacion } from "@/components/marca";
import { Badge, Bloque, Boton, Spinner, Tarjeta } from "@/components/ui";
import {
  MAX_NOTAS,
  MAX_PALABRAS,
  MIN_NOTAS,
  type ActaDelEstudiante,
  type ActaDelProfesor,
  type Palabra,
} from "@/lib/actas";
import { ApiError, apiFetch } from "@/lib/api/fetch";
import { useMe } from "@/lib/auth/session";
import { fechaLarga } from "@/lib/format";

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
    },
  });

  if (generar.isPending) {
    return <Esperando />;
  }

  const suficiente = notas.trim().length >= MIN_NOTAS;

  return (
    <Tarjeta>
      <h1 className="font-display text-h2 font-bold">Cuéntanos cómo estuvo</h1>
      <p className="mt-1 text-[14px] text-text-secondary">
        Escribe o dicta lo que se te venga a la cabeza. Nosotros le damos forma.
      </p>
      <label htmlFor="notas" className="sr-only">
        Tus notas de la clase
      </label>
      <textarea
        id="notas"
        rows={4}
        maxLength={MAX_NOTAS}
        value={notas}
        onChange={(e) => setNotas(e.target.value)}
        placeholder="Trabajamos past simple, sigue diciendo 'I go yesterday', le costó 'used to', quedamos en ver condicionales…"
        className="mt-4 w-full resize-y rounded-base border border-border bg-surface px-4 py-3 text-[14.5px] leading-relaxed focus-visible:shadow-focus focus-visible:outline-none"
      />
      <p className="mt-1 text-right text-[12px] tabular-nums text-text-muted" aria-live="polite">
        {suficiente ? `${notas.length}/${MAX_NOTAS}` : `Faltan ${MIN_NOTAS - notas.trim().length} caracteres`}
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
        <Boton disabled={!suficiente} onClick={() => generar.mutate()} className="flex-1 sm:flex-none">
          <Sparkles size={16} strokeWidth={2} />
          Generar acta
        </Boton>
      </div>
    </Tarjeta>
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

  const borrador = acta.status === "DRAFT";
  const cuerpo = () => ({
    workedOn: trabajado,
    recurringIssues: presente,
    nextSteps: sigue,
    vocabulary: palabras,
  });

  const actualizar = (nueva: ActaDelProfesor) => {
    queryClient.setQueryData(["lesson-note", acta.bookingId], nueva);
    queryClient.invalidateQueries({ queryKey: ["lesson-notes-summary"] });
  };

  const guardar = useMutation({
    mutationFn: () =>
      apiFetch<ActaDelProfesor>(`/api/v1/lesson-notes/${acta.id}`, { method: "PUT", body: cuerpo() }),
    onSuccess: (nueva) => {
      setGuardado(true);
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

  return (
    <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_300px]">
      <Tarjeta>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h1 className="font-display text-h2 font-bold">Acta de la clase</h1>
          {borrador ? (
            <span className="rounded-pill bg-warning-bg px-3 py-1 text-[12px] font-bold text-warning">
              Borrador
            </span>
          ) : (
            <Badge tono="menta">
              <Check size={12} strokeWidth={2.4} />
              Publicada
            </Badge>
          )}
        </div>
        <p className="mt-2 text-[13.5px] text-text-secondary">
          {!acta.editable
            ? "Ya pasó el plazo para editarla. Tu estudiante la ve tal como quedó."
            : borrador
              ? acta.origin === "MANUAL"
                ? "Escríbela con tus palabras; tus notas están guardadas. Lo que publiques es lo que verá tu estudiante."
                : "Revísala antes de publicar. Lo que publiques es lo que verá tu estudiante."
              : "Puedes corregirla un tiempo después de publicarla; tu estudiante verá que se actualizó."}
        </p>

        <div className="mt-5 space-y-4">
          <CampoSeccion titulo="Lo que trabajaron" valor={trabajado} onCambio={setTrabajado} soloLectura={!acta.editable} />
          <CampoSeccion titulo="Para tener presente" valor={presente} onCambio={setPresente} soloLectura={!acta.editable} />
          <Vocabulario palabras={palabras} onCambio={setPalabras} soloLectura={!acta.editable} />
          <CampoSeccion titulo="Lo que sigue" valor={sigue} onCambio={setSigue} soloLectura={!acta.editable} />
        </div>

        {error && (
          <div className="mt-4">
            <AvisoError mensaje={error.message} />
          </div>
        )}

        {acta.editable && (
          <div className="mt-5 flex flex-wrap items-center gap-2 sm:justify-end">
            {guardado && !ocupado && (
              <span className="text-[12.5px] font-semibold text-success" aria-live="polite">
                Guardada
              </span>
            )}
            <Boton
              variante="contorno"
              disabled={ocupado}
              onClick={() => guardar.mutate()}
              className="flex-1 sm:flex-none"
            >
              {guardar.isPending && <Spinner />}
              {borrador ? "Guardar sin publicar" : "Guardar cambios"}
            </Boton>
            {borrador && (
              <Boton disabled={ocupado || vacia} onClick={() => publicar.mutate()} className="flex-1 sm:flex-none">
                {publicar.isPending && <Spinner />}
                Publicar
              </Boton>
            )}
          </div>
        )}
      </Tarjeta>

      <NotasOriginales texto={acta.rawInput} />
    </div>
  );
}

function CampoSeccion({
  titulo,
  valor,
  onCambio,
  soloLectura,
}: {
  titulo: string;
  valor: string;
  onCambio: (v: string) => void;
  soloLectura: boolean;
}) {
  const id = `seccion-${titulo.replace(/\s+/g, "-").toLowerCase()}`;
  return (
    <div>
      <label htmlFor={id} className="text-[12px] font-bold uppercase tracking-[0.06em] text-text-secondary">
        {titulo}
      </label>
      <textarea
        id={id}
        rows={3}
        maxLength={1200}
        readOnly={soloLectura}
        value={valor}
        onChange={(e) => onCambio(e.target.value)}
        className="mt-1.5 w-full resize-y rounded-base border border-border bg-accent-peach-soft/40 px-4 py-3 text-[14.5px] leading-relaxed focus-visible:shadow-focus focus-visible:outline-none read-only:bg-surface-sunken"
      />
    </div>
  );
}

function Vocabulario({
  palabras,
  onCambio,
  soloLectura,
}: {
  palabras: Palabra[];
  onCambio: (p: Palabra[]) => void;
  soloLectura: boolean;
}) {
  const [nueva, setNueva] = useState("");
  const lleno = palabras.length >= MAX_PALABRAS;

  const agregar = () => {
    const term = nueva.trim();
    if (!term || lleno || palabras.some((p) => p.term.toLowerCase() === term.toLowerCase())) return;
    onCambio([...palabras, { term }]);
    setNueva("");
  };

  return (
    <div>
      <p className="text-[12px] font-bold uppercase tracking-[0.06em] text-text-secondary">Palabras nuevas</p>
      <ul className="mt-2 flex flex-wrap gap-2">
        {palabras.map((p) => (
          <li
            key={p.term}
            className="inline-flex min-h-9 items-center gap-1 rounded-pill bg-accent-lavender-soft pl-3 pr-1 text-[13px] font-semibold text-[#5e4a8a]"
          >
            {p.term}
            {!soloLectura && (
              <button
                type="button"
                aria-label={`Quitar «${p.term}»`}
                onClick={() => onCambio(palabras.filter((x) => x.term !== p.term))}
                className="grid h-8 w-8 place-items-center rounded-full transition-colors hover:bg-[#e2d7f4] focus-visible:shadow-focus"
              >
                <X size={14} strokeWidth={2.2} />
              </button>
            )}
          </li>
        ))}
        {palabras.length === 0 && (
          <li className="text-[13px] text-text-muted">Ninguna todavía.</li>
        )}
      </ul>
      {!soloLectura && !lleno && (
        <div className="mt-2 flex gap-2">
          <label htmlFor="palabra-nueva" className="sr-only">
            Agregar una palabra
          </label>
          <input
            id="palabra-nueva"
            value={nueva}
            maxLength={120}
            onChange={(e) => setNueva(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                e.preventDefault();
                agregar();
              }
            }}
            placeholder="Agregar una palabra"
            className="min-h-11 min-w-0 flex-1 rounded-pill border border-border bg-surface px-4 text-[14px] focus-visible:shadow-focus focus-visible:outline-none"
          />
          <Boton variante="fantasma" onClick={agregar} aria-label="Agregar la palabra">
            <Plus size={16} strokeWidth={2.2} />
          </Boton>
        </div>
      )}
    </div>
  );
}

/** Sus notas en crudo, para contrastar: al lado en escritorio, en un acordeón cerrado en móvil. */
function NotasOriginales({ texto }: { texto: string }) {
  const contenido = (
    <p className="whitespace-pre-wrap text-[13.5px] leading-relaxed text-text-secondary">{texto}</p>
  );
  return (
    <aside>
      <details className="rounded-card bg-surface-sunken p-4 lg:hidden">
        <summary className="flex min-h-11 cursor-pointer items-center gap-1.5 text-[13px] font-bold">
          <NotebookPen size={15} strokeWidth={2} />
          Tus notas originales
        </summary>
        <div className="mt-2">{contenido}</div>
      </details>
      <div className="hidden rounded-card bg-surface-sunken p-5 lg:block">
        <p className="flex items-center gap-1.5 text-[12px] font-bold uppercase tracking-[0.06em] text-text-secondary">
          <NotebookPen size={14} strokeWidth={2} />
          Tus notas originales
        </p>
        <div className="mt-3">{contenido}</div>
        <p className="mt-4 text-[12px] text-text-muted">Solo tú las ves.</p>
      </div>
    </aside>
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
    </div>
  );
}
