"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowDown, ArrowLeft, ArrowUp, Check, Sparkles, Turtle, Volume2 } from "lucide-react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { Suspense, useState } from "react";
import { AvisoError, Cargando, Vacio } from "@/components/estados";
import { ConstelacionDePractica } from "@/components/practica/ConstelacionDePractica";
import { Rigel, type RigelPose } from "@/components/Rigel";
import { Boton, Spinner, Tarjeta } from "@/components/ui";
import { fechaLarga } from "@/lib/format";
import { apiFetch } from "@/lib/api/fetch";
import {
  leerPayload,
  mostrarEsperada,
  NOMBRE_DE_CATEGORIA,
  NOMBRE_DEL_TIPO,
  primerNombre,
  PUNTOS_CONSTELACION_PERFECTA,
  PUNTOS_POR_PRACTICA,
  rachaAlPrimerIntento,
  resumirLoTrabajado,
  SE_OYEN,
  unirFichas,
  type Ejercicio,
  type Resultado,
  type SetDePractica,
} from "@/lib/practica";
import { useVozEnIngles } from "@/lib/voz";

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

  // «Empezar» en la portada la marca como empezada; el servidor lo ignora si ya lo estaba.
  const empezar = useMutation({
    mutationFn: () => apiFetch<SetDePractica>(`/api/v1/practice-sets/${id}/start`, { method: "POST" }),
    onSuccess: (s) => queryClient.setQueryData(["practice-set", id], s),
  });

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
  } else if (set.data.status === "READY") {
    cuerpo = <Portada set={set.data} empezando={empezar.isPending} onEmpezar={() => empezar.mutate()} />;
  } else {
    const s = set.data;
    const actual = s.items.find((i) => !i.closed);
    cuerpo = (
      <>
        <ConstelacionDePractica
          total={s.items.length}
          encendidas={s.items.filter((i) => i.closed).length}
          className="mx-auto h-16 w-auto max-w-full"
        />
        {actual ? (
          <EjercicioActual
            key={actual.id}
            ejercicio={actual}
            rachaPrevia={rachaAlPrimerIntento(s.items)}
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

/**
 * La portada del set, antes del primer ejercicio: de qué clase sale, cuánto dura y la constelación
 * apagada que se va a encender.
 */
function Portada({ set, empezando, onEmpezar }: { set: SetDePractica; empezando: boolean; onEmpezar: () => void }) {
  const trabajado = resumirLoTrabajado(set.workedOn);
  const minutos = set.estimatedMinutes ?? set.itemCount;
  const categorias = Array.from(new Set(set.items.map((i) => NOMBRE_DE_CATEGORIA[i.category])));
  return (
    <div className="mt-2 flex flex-col items-center gap-3 rounded-card bg-surface-raised p-7 text-center shadow-sm">
      <Rigel pose="saludo" decorativo className="h-24 w-auto" />
      <h1 className="font-display text-h2 font-bold">
        {set.itemCount} ejercicios · {minutos} minutos
      </h1>
      {(set.classStartsAt || set.professorName) && (
        <p className="max-w-[36ch] text-[14.5px] text-text-secondary">
          Del {set.classStartsAt ? fechaLarga(set.classStartsAt) : "tu última clase"}
          {set.professorName ? ` con ${primerNombre(set.professorName)}` : ""}
          {trabajado ? `: ${trabajado}.` : "."}
        </p>
      )}
      <p className="flex flex-wrap justify-center gap-1.5">
        {categorias.map((c) => (
          <span key={c} className="rounded-pill bg-accent-lavender-soft px-3 py-1 text-[12px] font-bold text-[#5e4a8a]">
            {c}
          </span>
        ))}
      </p>
      <ConstelacionDePractica total={set.itemCount} encendidas={0} className="h-16 w-auto max-w-full" />
      {set.professorName && (
        // Transparencia: el profesor ve cada ejercicio con lo que se respondió (decisión del 24/09/2026).
        <p className="max-w-[36ch] text-[13px] text-text-muted">
          {primerNombre(set.professorName)} verá cómo te fue, así prepara tu próxima clase.
        </p>
      )}
      <Boton className="mt-1" disabled={empezando} onClick={onEmpezar}>
        {empezando && <Spinner />}
        Empezar
      </Boton>
    </div>
  );
}

/** Cómo reacciona Rigel: señala al empezar, espera mientras se comprueba, celebra o anima. */
function poseDe(resultado: Resultado | null, enviando: boolean): RigelPose {
  if (enviando) return "espera";
  if (!resultado) return "guia";
  return resultado.correct ? "celebracion" : "animo";
}

function EjercicioActual({
  ejercicio,
  rachaPrevia,
  onActualizado,
}: {
  ejercicio: Ejercicio;
  rachaPrevia: number;
  onActualizado: (e: Ejercicio) => void;
}) {
  // El diálogo ya trae una respuesta: el orden en que se muestra. Si no, quien cree que ya está bien
  // no podría comprobarlo sin mover antes una línea.
  const [respuesta, setRespuesta] = useState(() =>
    ejercicio.type === "ORDER_DIALOGUE" ? JSON.stringify(leerPayload<{ lines?: string[] }>(ejercicio).lines ?? []) : "",
  );
  const [resultado, setResultado] = useState<Resultado | null>(null);
  const voz = useVozEnIngles();
  const seOye = SE_OYEN.includes(ejercicio.type);
  const saltar = useMutation({
    mutationFn: () => apiFetch<Ejercicio>(`/api/v1/practice-items/${ejercicio.id}/skip`, { method: "POST" }),
    onSuccess: onActualizado,
  });
  const responder = useMutation({
    mutationFn: (texto: string) =>
      apiFetch<Resultado>(`/api/v1/practice-items/${ejercicio.id}/answer`, { method: "POST", body: { answer: texto } }),
    onSuccess: (r) => {
      setResultado(r);
      // Acertado, se avanza solo después de un respiro para leer la explicación; si no, se espera a
      // «Intentar otra vez» o «Siguiente».
      if (r.correct) window.setTimeout(() => onActualizado(r.item), 2200);
    },
  });

  const enviar = (texto: string) => {
    if (!texto.trim()) return;
    responder.mutate(texto);
  };

  return (
    <Tarjeta className="mt-4">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-[11.5px] font-bold uppercase tracking-[0.1em] text-primary-strong">
            {NOMBRE_DE_CATEGORIA[ejercicio.category]} · {NOMBRE_DEL_TIPO[ejercicio.type]}
          </p>
          <p className="mt-1 text-[14px] font-semibold text-text-secondary">{ejercicio.prompt}</p>
        </div>
        <Rigel pose={poseDe(resultado, responder.isPending)} decorativo className="h-14 w-auto shrink-0" />
      </div>
      {seOye && voz.disponible === false ? (
        // Sin voz en inglés no hay ejercicio de escucha posible; saltarlo no cuenta como error.
        <div className="mt-3 rounded-base bg-surface-sunken px-4 py-3 text-[14px] text-text-secondary">
          <p>Tu dispositivo no tiene una voz en inglés, así que este ejercicio no puede sonar. Sáltalo: no cuenta como error.</p>
          <Boton variante="contorno" className="mt-3" disabled={saltar.isPending} onClick={() => saltar.mutate()}>
            {saltar.isPending && <Spinner />}
            Saltar este
          </Boton>
          {saltar.isError && <AvisoError mensaje={saltar.error.message} />}
        </div>
      ) : (
        <div className="mt-3">
          <Entrada
            ejercicio={ejercicio}
            valor={respuesta}
            onCambio={setRespuesta}
            bloqueado={responder.isPending || !!resultado}
            voz={voz}
          />
        </div>
      )}

      {resultado && resultado.correct && (
        <div className="mt-4 rounded-base bg-accent-lavender-soft px-4 py-3 text-[14px] text-[#5e4a8a]" aria-live="polite">
          <p className="flex items-center gap-2 font-semibold">
            <Check size={16} strokeWidth={2.4} />
            Así es.
          </p>
          {resultado.item.explanation && <p className="mt-1">{resultado.item.explanation}</p>}
        </div>
      )}
      {resultado && resultado.correct && resultado.item.attempts === 1 && rachaPrevia + 1 >= 3 && (
        // La racha dentro del set: tres o más seguidas al primer intento.
        <p className="racha-entra mt-3 flex items-center justify-center gap-2 rounded-pill bg-accent-peach-soft px-4 py-2 text-[14px] font-bold text-[#8a5a33]">
          <Sparkles size={15} strokeWidth={2.2} />
          ¡{rachaPrevia + 1} seguidas!
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
        {!resultado && !(seOye && voz.disponible === false) && (
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
  voz,
}: {
  ejercicio: Ejercicio;
  valor: string;
  onCambio: (v: string) => void;
  bloqueado: boolean;
  voz: ReturnType<typeof useVozEnIngles>;
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
    case "SPOT_ERROR": {
      const p = leerPayload<{ tokens: string[] }>(ejercicio);
      return (
        <div lang="en" className="flex flex-wrap gap-2" role="radiogroup" aria-label="Toca la palabra que está mal">
          {(p.tokens ?? []).map((t, i) => (
            <Opcion key={i} elegida={valor === String(i)} bloqueado={bloqueado} onElegir={() => onCambio(String(i))}>
              {t}
            </Opcion>
          ))}
        </div>
      );
    }
    case "BUILD_SENTENCE":
      return <Armar ejercicio={ejercicio} onCambio={onCambio} bloqueado={bloqueado} />;
    case "CHOOSE_REPLY": {
      const p = leerPayload<{ from?: string; message: string; options: string[] }>(ejercicio);
      return (
        <>
          <div className="max-w-[85%] rounded-[18px] rounded-bl-[6px] bg-surface-sunken px-4 py-3">
            {p.from && <p className="text-[11.5px] font-bold text-text-muted">{p.from}</p>}
            <p lang="en" className="text-[15px] text-text">
              {p.message}
            </p>
          </div>
          <Opciones opciones={p.options} valor={valor} onCambio={onCambio} bloqueado={bloqueado} etiqueta="Respuestas" />
        </>
      );
    }
    case "LISTEN_CHOOSE": {
      const p = leerPayload<{ say: string; options: string[] }>(ejercicio);
      return (
        <>
          <Escuchar texto={p.say} voz={voz} />
          <Opciones opciones={p.options} valor={valor} onCambio={onCambio} bloqueado={bloqueado} etiqueta="Significados" />
        </>
      );
    }
    case "DICTATION": {
      const p = leerPayload<{ say: string }>(ejercicio);
      return (
        <>
          <Escuchar texto={p.say} voz={voz} />
          <TextoLibre valor={valor} onCambio={onCambio} bloqueado={bloqueado} etiqueta="Escribe lo que oíste" />
        </>
      );
    }
  }
}

function Opcion({
  elegida,
  bloqueado,
  onElegir,
  children,
}: {
  elegida: boolean;
  bloqueado: boolean;
  onElegir: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      role="radio"
      aria-checked={elegida}
      disabled={bloqueado}
      onClick={onElegir}
      className={`min-h-11 rounded-pill px-4 text-[14px] font-semibold transition-colors focus-visible:shadow-focus ${
        elegida ? "bg-[#7A4A8C] text-on-primary" : "bg-accent-lavender-soft text-[#5e4a8a] hover:bg-[#e2d7f4]"
      }`}
    >
      {children}
    </button>
  );
}

function Opciones({
  opciones,
  valor,
  onCambio,
  bloqueado,
  etiqueta,
}: {
  opciones: string[] | undefined;
  valor: string;
  onCambio: (v: string) => void;
  bloqueado: boolean;
  etiqueta: string;
}) {
  return (
    <div className="mt-3 flex flex-wrap gap-2" role="radiogroup" aria-label={etiqueta}>
      {(opciones ?? []).map((o) => (
        <Opcion key={o} elegida={valor === o} bloqueado={bloqueado} onElegir={() => onCambio(o)}>
          {o}
        </Opcion>
      ))}
    </div>
  );
}

/** Lo que dice Meissa: con la voz del dispositivo, y también más despacio. */
function Escuchar({ texto, voz }: { texto: string; voz: ReturnType<typeof useVozEnIngles> }) {
  return (
    <div className="flex flex-wrap items-center gap-2">
      <Boton disabled={!voz.disponible} onClick={() => voz.hablar(texto)} aria-label="Escuchar">
        <Volume2 size={17} strokeWidth={2} />
        {voz.hablando ? "Sonando…" : "Escuchar"}
      </Boton>
      <Boton variante="contorno" disabled={!voz.disponible} onClick={() => voz.hablar(texto, true)}>
        <Turtle size={17} strokeWidth={2} />
        Más despacio
      </Boton>
    </div>
  );
}

/** Armar la frase: se tocan las fichas en orden; una ficha puesta vuelve a su lugar tocándola. */
function Armar({ ejercicio, onCambio, bloqueado }: { ejercicio: Ejercicio; onCambio: (v: string) => void; bloqueado: boolean }) {
  const p = leerPayload<{ tiles: string[]; guide?: string }>(ejercicio);
  const fichas = p.tiles ?? [];
  const [puestas, setPuestas] = useState<number[]>([]);
  const cambiar = (nuevas: number[]) => {
    setPuestas(nuevas);
    onCambio(nuevas.length === fichas.length ? JSON.stringify(nuevas.map((i) => fichas[i])) : "");
  };
  return (
    <>
      {p.guide && <p className="text-[15px] text-text-secondary">«{p.guide}»</p>}
      <div
        lang="en"
        className="mt-3 flex min-h-14 flex-wrap items-center gap-2 rounded-base border border-dashed border-border px-3 py-2"
        aria-label={`Tu frase: ${unirFichas(puestas.map((i) => fichas[i]))}`}
      >
        {puestas.length === 0 && <span className="text-[13px] text-text-muted">Toca las fichas en orden</span>}
        {puestas.map((i) => (
          <button
            key={i}
            type="button"
            disabled={bloqueado}
            onClick={() => cambiar(puestas.filter((x) => x !== i))}
            className="min-h-11 rounded-pill bg-[#7A4A8C] px-4 text-[14px] font-semibold text-on-primary focus-visible:shadow-focus"
          >
            {fichas[i]}
          </button>
        ))}
      </div>
      <div lang="en" className="mt-3 flex flex-wrap gap-2">
        {fichas.map((f, i) =>
          puestas.includes(i) ? null : (
            <button
              key={i}
              type="button"
              disabled={bloqueado}
              onClick={() => cambiar([...puestas, i])}
              className="min-h-11 rounded-pill bg-accent-lavender-soft px-4 text-[14px] font-semibold text-[#5e4a8a] hover:bg-[#e2d7f4] focus-visible:shadow-focus"
            >
              {f}
            </button>
          ),
        )}
      </div>
    </>
  );
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
 * El cierre (B5.3): la constelación completa, los puntos, lo logrado y lo que conviene repasar.
 * Nunca «3 de 5» ni un porcentaje en primer plano: la práctica no es un examen y la pantalla no
 * puede insinuar que lo es. Los logros que se encendieron los celebra el armazón de la app.
 */
function Cierre({ set }: { set: SetDePractica }) {
  const repasar = set.items.filter((i) => i.correct === false && !i.skipped).map((i) => terminoDe(i)).filter(Boolean);
  const minutos = set.estimatedMinutes ?? set.itemCount;
  const total = PUNTOS_POR_PRACTICA + (set.perfect ? PUNTOS_CONSTELACION_PERFECTA : 0);
  return (
    <div className="flex flex-col items-center gap-3 rounded-card bg-surface-raised p-7 text-center shadow-sm">
      <ConstelacionDePractica total={set.items.length} encendidas={set.items.length} completa className="h-20 w-auto max-w-full" />
      <Rigel pose="celebracion" decorativo className="h-20 w-auto" />
      <h1 className="font-display text-h2 font-bold">
        {set.perfect ? "¡Constelación perfecta!" : `Listo. ${minutos} minutos bien usados.`}
      </h1>
      {repasar.length > 0 ? (
        <p className="max-w-[340px] text-[14.5px] text-text-secondary">
          Para repasar antes de tu próxima clase: <strong>{Array.from(new Set(repasar)).join(", ")}</strong>.
        </p>
      ) : (
        <p className="max-w-[340px] text-[14.5px] text-text-secondary">Llegas a tu próxima clase con todo esto fresco.</p>
      )}
      <div className="flex flex-col items-center gap-1">
        <p className="rounded-pill bg-accent-lavender-soft px-4 py-1.5 text-[14px] font-bold text-[#5e4a8a]">
          +{PUNTOS_POR_PRACTICA} puntos por practicar
        </p>
        {set.perfect && (
          <p className="rounded-pill bg-accent-peach-soft px-4 py-1.5 text-[14px] font-bold text-[#8a5a33]">
            +{PUNTOS_CONSTELACION_PERFECTA} por la constelación perfecta
          </p>
        )}
        <p className="mt-1 text-[12.5px] font-semibold text-text-muted">Total: +{total} puntos</p>
      </div>
      <div className="mt-2 flex flex-wrap justify-center gap-2">
        {set.bookingId && (
          <Link
            href={`/mis-clases/${set.bookingId}/acta`}
            className="inline-flex min-h-11 items-center rounded-pill border-[1.5px] border-border px-5 text-[14px] font-bold text-text transition-colors hover:bg-surface-sunken focus-visible:shadow-focus"
          >
            Ver el resumen de la clase
          </Link>
        )}
        <Link
          href="/cuenta?seccion=resumen"
          className="inline-flex min-h-11 items-center rounded-pill bg-primary px-6 text-[15px] font-bold text-on-primary shadow-primary transition-colors hover:bg-primary-strong focus-visible:shadow-focus"
        >
          Volver a mi perfil
        </Link>
      </div>
    </div>
  );
}

function terminoDe(i: Ejercicio): string {
  const p = leerPayload<{ term?: string; say?: string }>(i);
  if (p.term) return p.term;
  if (i.type === "LISTEN_CHOOSE" && p.say) return p.say;
  if (i.type === "DICTATION") return "escribir lo que oyes";
  if (i.type === "SPOT_ERROR") return "encontrar el error";
  if (i.type === "BUILD_SENTENCE") return "armar frases";
  if (i.type === "CHOOSE_REPLY") return "responder en una conversación";
  if (i.type === "FIX_SENTENCE") return "la corrección de frases";
  if (i.type === "ORDER_DIALOGUE") return "el orden de un diálogo";
  if (i.type === "MATCH_MEANING") return "los significados";
  return "completar frases";
}
