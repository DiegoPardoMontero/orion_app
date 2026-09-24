"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowDown, ArrowLeft, ArrowUp, Check, Sparkles } from "lucide-react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";
import { AvisoError, Cargando, Vacio } from "@/components/estados";
import { Boton, Spinner, Tarjeta } from "@/components/ui";
import { apiFetch } from "@/lib/api/fetch";
import {
  leerPayload,
  mostrarEsperada,
  PUNTOS_POR_PRACTICA,
  type Ejercicio,
  type Resultado,
  type SetDePractica,
} from "@/lib/practica";

/**
 * Practicar entre clases (Bloque 10, pasos B5.2 y B5.3). Un ejercicio por pantalla, cuatro puntos
 * que se encienden —el lenguaje de constelación del Bloque 8, no una barra genérica— y se puede
 * salir y volver donde iba: el servidor sabe qué ejercicios están cerrados.
 *
 * <p>El error de práctica usa el tono de aviso y nunca el rojo de alarma: equivocarse aquí no es un
 * fallo del sistema, y el color lo tiene que decir.
 */
export default function PracticaPage() {
  return (
    <Suspense fallback={null}>
      <Contenido />
    </Suspense>
  );
}

function Contenido() {
  const { id } = useParams<{ id: string }>();
  const queryClient = useQueryClient();
  const set = useQuery({
    queryKey: ["practice-set", id],
    queryFn: () => apiFetch<SetDePractica>(`/api/v1/practice-sets/${id}`),
    retry: false,
  });

  // Abrirla la marca como empezada; se hace una vez, y el servidor lo ignora si ya lo estaba.
  const empezar = useMutation({
    mutationFn: () => apiFetch<SetDePractica>(`/api/v1/practice-sets/${id}/start`, { method: "POST" }),
    onSuccess: (s) => queryClient.setQueryData(["practice-set", id], s),
  });
  const estado = set.data?.status;
  useEffect(() => {
    if (estado === "READY" && empezar.isIdle) empezar.mutate();
  }, [estado, empezar]);

  const completar = useMutation({
    mutationFn: () => apiFetch<SetDePractica>(`/api/v1/practice-sets/${id}/complete`, { method: "POST" }),
    onSuccess: (s) => {
      queryClient.setQueryData(["practice-set", id], s);
      queryClient.invalidateQueries({ queryKey: ["me", "practice"] });
      queryClient.invalidateQueries({ queryKey: ["me", "engagement"] });
      // Si terminar encendió un logro, la estrella se celebra aquí mismo (brief, B5.3): la celebración
      // mira los logros, y sin esto no se enteraba hasta volver a cargar el perfil.
      queryClient.invalidateQueries({ queryKey: ["me", "achievements"] });
    },
  });

  let cuerpo;
  if (set.isPending) {
    cuerpo = <Cargando filas={2} />;
  } else if (set.isError || !set.data) {
    cuerpo = <Vacio titulo="No encontramos esta práctica" texto="Puede que ya haya vencido. La próxima llega con tu siguiente clase." />;
  } else if (set.data.status === "COMPLETED") {
    cuerpo = <Cierre set={set.data} />;
  } else if (set.data.status === "EXPIRED") {
    cuerpo = <Vacio titulo="Esta práctica ya venció" texto="La próxima llega con tu siguiente clase." />;
  } else {
    const s = set.data;
    const actual = s.items.find((i) => !i.closed);
    cuerpo = (
      <>
        <Puntos items={s.items} />
        {actual ? (
          <EjercicioActual
            key={actual.id}
            ejercicio={actual}
            onActualizado={(item) =>
              queryClient.setQueryData<SetDePractica>(["practice-set", id], (previo) =>
                previo ? { ...previo, items: previo.items.map((i) => (i.id === item.id ? item : i)) } : previo,
              )
            }
          />
        ) : (
          <Tarjeta className="mt-5 text-center">
            <p className="font-display text-[20px] font-bold">Terminaste los ejercicios</p>
            {completar.isError && (
              <div className="mt-3">
                <AvisoError mensaje={completar.error.message} />
              </div>
            )}
            <Boton className="mt-4" disabled={completar.isPending} onClick={() => completar.mutate()}>
              {completar.isPending && <Spinner />}
              Ver cómo me fue
            </Boton>
          </Tarjeta>
        )}
      </>
    );
  }

  return (
    <main className="mx-auto w-full max-w-md px-5 py-6 lg:max-w-xl lg:py-8">
      <Link
        href="/cuenta?seccion=resumen"
        className="inline-flex min-h-11 items-center gap-1.5 text-[13px] font-semibold text-text-secondary transition-colors hover:text-text focus-visible:shadow-focus"
      >
        <ArrowLeft size={15} strokeWidth={2} />
        Salir y seguir luego
      </Link>
      <div className="mt-2">{cuerpo}</div>
    </main>
  );
}

/** Los cuatro puntos que se encienden, uno por ejercicio cerrado. */
function Puntos({ items }: { items: Ejercicio[] }) {
  const cerrados = items.filter((i) => i.closed).length;
  return (
    <div
      className="flex items-center justify-center gap-3 py-2"
      role="img"
      aria-label={`Ejercicio ${Math.min(cerrados + 1, items.length)} de ${items.length}`}
    >
      {items.map((i) => (
        <span
          key={i.id}
          aria-hidden="true"
          className={`h-3 w-3 rounded-full transition-colors motion-reduce:transition-none ${
            i.closed ? "bg-[#7A4A8C] shadow-[0_0_10px_rgba(122,74,140,0.55)]" : "bg-accent-lavender-soft"
          }`}
        />
      ))}
    </div>
  );
}

function EjercicioActual({ ejercicio, onActualizado }: { ejercicio: Ejercicio; onActualizado: (e: Ejercicio) => void }) {
  // El diálogo ya trae una respuesta: el orden en que se muestra. Si no, quien cree que ya está bien
  // no podría comprobarlo sin mover antes una línea.
  const [respuesta, setRespuesta] = useState(() =>
    ejercicio.type === "ORDER_DIALOGUE" ? JSON.stringify(leerPayload<{ lines?: string[] }>(ejercicio).lines ?? []) : "",
  );
  const [resultado, setResultado] = useState<Resultado | null>(null);
  const responder = useMutation({
    mutationFn: (texto: string) =>
      apiFetch<Resultado>(`/api/v1/practice-items/${ejercicio.id}/answer`, { method: "POST", body: { answer: texto } }),
    onSuccess: (r) => {
      setResultado(r);
      // Acertado, se avanza solo después de un respiro; si no, se espera a «Intentar otra vez» o «Siguiente».
      if (r.correct) window.setTimeout(() => onActualizado(r.item), 1200);
    },
  });

  const enviar = (texto: string) => {
    if (!texto.trim()) return;
    responder.mutate(texto);
  };

  return (
    <Tarjeta className="mt-4">
      <p className="text-[12px] font-bold uppercase tracking-[0.06em] text-text-secondary">{ejercicio.prompt}</p>
      <div className="mt-3">
        <Entrada ejercicio={ejercicio} valor={respuesta} onCambio={setRespuesta} bloqueado={responder.isPending || !!resultado} />
      </div>

      {resultado && resultado.correct && (
        <p className="mt-4 flex items-center gap-2 rounded-base bg-accent-lavender-soft px-4 py-3 text-[14px] font-semibold text-[#5e4a8a]" aria-live="polite">
          <Check size={16} strokeWidth={2.4} />
          Así es.
        </p>
      )}
      {resultado && !resultado.correct && (
        <div className="mt-4 rounded-base bg-warning-bg px-4 py-3 text-[14px] text-warning" aria-live="polite">
          <p>
            <strong>Casi.</strong> {resultado.item.explanation}
          </p>
          {resultado.closed && resultado.item.expected && (
            <p className="mt-1">
              La respuesta: <strong>{mostrarEsperada(ejercicio.type, resultado.item.expected)}</strong>
            </p>
          )}
        </div>
      )}
      {responder.isError && (
        <div className="mt-3">
          <AvisoError mensaje={responder.error.message} />
        </div>
      )}

      <div className="mt-4 flex flex-wrap justify-end gap-2">
        {!resultado && (
          <Boton disabled={responder.isPending || !respuesta.trim()} onClick={() => enviar(respuesta)}>
            {responder.isPending && <Spinner />}
            Comprobar
          </Boton>
        )}
        {resultado && !resultado.correct && !resultado.closed && (
          <Boton
            variante="contorno"
            onClick={() => {
              setResultado(null);
              onActualizado(resultado.item);
            }}
          >
            Intentar otra vez
          </Boton>
        )}
        {resultado && !resultado.correct && resultado.closed && (
          <Boton onClick={() => onActualizado(resultado.item)}>Siguiente</Boton>
        )}
      </div>
    </Tarjeta>
  );
}

/** La entrada de cada tipo. Todas se responden en texto: la de emparejar y ordenar, como JSON. */
function Entrada({
  ejercicio,
  valor,
  onCambio,
  bloqueado,
}: {
  ejercicio: Ejercicio;
  valor: string;
  onCambio: (v: string) => void;
  bloqueado: boolean;
}) {
  switch (ejercicio.type) {
    case "FILL_BLANK": {
      const p = leerPayload<{ sentence: string; options: string[] }>(ejercicio);
      return (
        <>
          <p lang="en" className="font-display text-[19px] font-semibold leading-snug">
            {p.sentence}
          </p>
          <div className="mt-3 flex flex-wrap gap-2" role="radiogroup" aria-label="Opciones">
            {(p.options ?? []).map((o) => (
              <button
                key={o}
                type="button"
                role="radio"
                aria-checked={valor === o}
                disabled={bloqueado}
                onClick={() => onCambio(o)}
                className={`min-h-11 rounded-pill px-4 text-[14px] font-semibold transition-colors focus-visible:shadow-focus ${
                  valor === o ? "bg-[#7A4A8C] text-on-primary" : "bg-accent-lavender-soft text-[#5e4a8a] hover:bg-[#e2d7f4]"
                }`}
              >
                {o}
              </button>
            ))}
          </div>
        </>
      );
    }
    case "FIX_SENTENCE": {
      const p = leerPayload<{ sentence: string }>(ejercicio);
      return (
        <>
          <p lang="en" className="font-display text-[19px] font-semibold leading-snug">
            {p.sentence}
          </p>
          <TextoLibre valor={valor} onCambio={onCambio} bloqueado={bloqueado} etiqueta="Tu frase corregida" />
        </>
      );
    }
    case "WRITE_SENTENCE": {
      const p = leerPayload<{ term: string }>(ejercicio);
      return (
        <>
          <p lang="en" className="font-display text-[19px] font-semibold">
            {p.term}
          </p>
          <TextoLibre valor={valor} onCambio={onCambio} bloqueado={bloqueado} etiqueta="Tu frase" />
        </>
      );
    }
    case "MATCH_MEANING":
      return <Emparejar ejercicio={ejercicio} onCambio={onCambio} bloqueado={bloqueado} />;
    case "ORDER_DIALOGUE":
      return <Ordenar ejercicio={ejercicio} onCambio={onCambio} bloqueado={bloqueado} />;
  }
}

function TextoLibre({ valor, onCambio, bloqueado, etiqueta }: { valor: string; onCambio: (v: string) => void; bloqueado: boolean; etiqueta: string }) {
  return (
    <label className="mt-3 block">
      <span className="sr-only">{etiqueta}</span>
      <textarea
        lang="en"
        rows={2}
        maxLength={600}
        value={valor}
        readOnly={bloqueado}
        onChange={(e) => onCambio(e.target.value)}
        placeholder={etiqueta}
        className="w-full resize-y rounded-base border border-border bg-surface px-4 py-3 text-[15px] focus-visible:shadow-focus focus-visible:outline-none"
      />
    </label>
  );
}

function Emparejar({ ejercicio, onCambio, bloqueado }: { ejercicio: Ejercicio; onCambio: (v: string) => void; bloqueado: boolean }) {
  const p = leerPayload<{ terms: string[]; meanings: string[] }>(ejercicio);
  const [pares, setPares] = useState<Record<string, string>>({});
  const elegir = (termino: string, significado: string) => {
    const nuevos = { ...pares, [termino]: significado };
    setPares(nuevos);
    onCambio((p.terms ?? []).every((t) => nuevos[t]) ? JSON.stringify(nuevos) : "");
  };
  return (
    <div className="space-y-2">
      {(p.terms ?? []).map((t) => (
        <label key={t} className="flex flex-wrap items-center justify-between gap-2">
          <span lang="en" className="font-semibold">
            {t}
          </span>
          <select
            value={pares[t] ?? ""}
            disabled={bloqueado}
            onChange={(e) => elegir(t, e.target.value)}
            className="min-h-11 rounded-base border border-border bg-surface px-3 text-[14px] focus-visible:shadow-focus"
          >
            <option value="" disabled>
              Elige su significado
            </option>
            {(p.meanings ?? []).map((m) => (
              <option key={m} value={m}>
                {m}
              </option>
            ))}
          </select>
        </label>
      ))}
    </div>
  );
}

function Ordenar({ ejercicio, onCambio, bloqueado }: { ejercicio: Ejercicio; onCambio: (v: string) => void; bloqueado: boolean }) {
  const p = leerPayload<{ lines: string[] }>(ejercicio);
  const [lineas, setLineas] = useState<string[]>(p.lines ?? []);
  const mover = (i: number, hacia: number) => {
    const j = i + hacia;
    if (j < 0 || j >= lineas.length) return;
    const nuevas = [...lineas];
    [nuevas[i], nuevas[j]] = [nuevas[j], nuevas[i]];
    setLineas(nuevas);
    onCambio(JSON.stringify(nuevas));
  };
  return (
    <ol className="space-y-2">
      {lineas.map((l, i) => (
        <li key={l} className="flex items-center gap-2 rounded-base bg-surface-sunken px-3 py-2">
          <span lang="en" className="flex-1 text-[14px]">
            {l}
          </span>
          <button type="button" aria-label="Subir" disabled={bloqueado || i === 0} onClick={() => mover(i, -1)} className="grid h-11 w-11 place-items-center rounded-full hover:bg-surface-raised disabled:opacity-40">
            <ArrowUp size={16} />
          </button>
          <button type="button" aria-label="Bajar" disabled={bloqueado || i === lineas.length - 1} onClick={() => mover(i, 1)} className="grid h-11 w-11 place-items-center rounded-full hover:bg-surface-raised disabled:opacity-40">
            <ArrowDown size={16} />
          </button>
        </li>
      ))}
    </ol>
  );
}

/**
 * El cierre (B5.3): lo logrado y lo que conviene repasar. Nunca «3 de 4» ni un porcentaje en
 * primer plano: la práctica no es un examen y la pantalla no puede insinuar que lo es.
 */
function Cierre({ set }: { set: SetDePractica }) {
  const repasar = set.items.filter((i) => i.correct === false).map((i) => terminoDe(i)).filter(Boolean);
  const minutos = set.estimatedMinutes ?? set.itemCount;
  return (
    <div className="flex flex-col items-center gap-3 rounded-card bg-surface-raised p-8 text-center shadow-sm">
      <span className="gradient-dawn grid h-16 w-16 place-items-center rounded-full text-on-primary">
        <Sparkles size={26} strokeWidth={2} />
      </span>
      <h1 className="font-display text-h2 font-bold">Listo. {minutos} minutos bien usados.</h1>
      {repasar.length > 0 ? (
        <p className="max-w-[340px] text-[14.5px] text-text-secondary">
          Para repasar antes de tu próxima clase: <strong>{Array.from(new Set(repasar)).join(", ")}</strong>.
        </p>
      ) : (
        <p className="max-w-[340px] text-[14.5px] text-text-secondary">Llegas a tu próxima clase con todo esto fresco.</p>
      )}
      <p className="rounded-pill bg-accent-lavender-soft px-4 py-1.5 text-[14px] font-bold text-[#5e4a8a]">+{PUNTOS_POR_PRACTICA} puntos</p>
      <Link
        href="/cuenta?seccion=resumen"
        className="mt-2 inline-flex min-h-11 items-center rounded-pill bg-primary px-6 text-[15px] font-bold text-on-primary shadow-primary transition-colors hover:bg-primary-strong focus-visible:shadow-focus"
      >
        Volver a mi perfil
      </Link>
    </div>
  );
}

function terminoDe(i: Ejercicio): string {
  const p = leerPayload<{ term?: string }>(i);
  if (p.term) return p.term;
  if (i.type === "FIX_SENTENCE") return "la corrección de frases";
  if (i.type === "ORDER_DIALOGUE") return "el orden de un diálogo";
  if (i.type === "MATCH_MEANING") return "los significados";
  return "completar frases";
}
