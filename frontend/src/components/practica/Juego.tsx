"use client";

import { useMutation } from "@tanstack/react-query";
import { ChevronRight, Flame, RotateCcw, SkipForward, X } from "lucide-react";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { AvisoError } from "@/components/estados";
import { Meissa, type MeissaEstado } from "@/components/Meissa";
import { Rigel, type RigelPose } from "@/components/Rigel";
import { apiFetch } from "@/lib/api/fetch";
import {
  estrellasDe,
  instruccionDe,
  leerPayload,
  mostrarEsperada,
  nombreDelProfe,
  NOMBRE_DEL_TIPO,
  partirLinea,
  rachaDe,
  SE_OYEN,
  TEXTO_RACHA,
  textoDelRegreso,
  type Ejercicio,
  type Resultado,
  type SetDePractica,
} from "@/lib/practica";
import { useCerrarConEscape } from "@/lib/useCerrarConEscape";
import { useVozEnIngles } from "@/lib/voz";
import { Constelacion, formaDe } from "./Constelacion";
import { BotonPrincipal, BotonSecundario, ChipCategoria, PanelRespuesta, Racha } from "./piezas";
import {
  ArmaLaFrase,
  CazaElError,
  Completa,
  Corrige,
  EscuchaYElige,
  EscuchaYEscribe,
  OrdenaLaConversacion,
  Parejas,
  RespondeEnElChat,
  TuFrase,
  type Fase,
} from "./tipos";

/**
 * La práctica mientras se juega (handoff `design_handoff_orion_practica`, §8, §10.4 y §10.7): un
 * ejercicio por pantalla, la constelación arriba, la transición entre uno y otro y «Salir y seguir
 * luego». Cada respuesta se guarda en el servidor al momento, así que salir es inmediato y volver
 * abre el mismo ejercicio.
 */

type PairView = { pairCorrect: boolean; closed: boolean; attemptsLeft: number; item: Ejercicio };

type Transicion = { indice: number; racha: number; siguiente: string; estrellas: ReturnType<typeof estrellasDe>; cerrados: number };

export function Juego({
  set,
  onItem,
  onTerminar,
  terminando,
  errorAlTerminar,
}: {
  set: SetDePractica;
  onItem: (item: Ejercicio) => void;
  onTerminar: () => void;
  terminando: boolean;
  errorAlTerminar: string | null;
}) {
  const router = useRouter();
  const items = [...set.items].sort((a, b) => a.index - b.index);
  const [actualId, setActualId] = useState<string | null>(() => items.find((i) => !i.closed)?.id ?? null);
  const [transicion, setTransicion] = useState<Transicion | null>(null);
  const [entrando, setEntrando] = useState(false);
  const [salir, setSalir] = useState(false);
  // El regreso (§10.7): si ya había estrellas cuando abrió, se le dice dónde va.
  const [regreso, setRegreso] = useState(() => items.some((i) => i.closed) ? textoDelRegreso(items) : null);

  const actual = items.find((i) => i.id === actualId) ?? null;
  const forma = formaDe(set.id);
  const estrellas = estrellasDe(items, actualId);
  const racha = rachaDe(estrellas);
  const profe = nombreDelProfe(set);

  useEffect(() => {
    if (!regreso) return;
    const t = window.setTimeout(() => setRegreso(null), 4000);
    return () => window.clearTimeout(t);
  }, [regreso]);

  // Todo cerrado al abrir (salió justo después del último): directo al cierre.
  const sinNada = actual === null && transicion === null;
  useEffect(() => {
    if (sinNada && !terminando && !errorAlTerminar) onTerminar();
  }, [sinNada, terminando, errorAlTerminar, onTerminar]);

  const avanzar = useCallback(
    (t: Transicion | null, siguienteId: string | null) => {
      if (siguienteId) {
        setTransicion(null);
        setActualId(siguienteId);
        setEntrando(true);
      } else {
        // El último: la transición se queda hasta que llega el cierre.
        setTransicion(t);
        setActualId(null);
        onTerminar();
      }
    },
    [onTerminar],
  );

  const seguir = () => {
    if (!actual) return;
    const siguiente = items.find((i) => !i.closed && i.id !== actual.id) ?? null;
    if (actual.skipped) {
      // Lo saltado no enciende nada: sin transición.
      avanzar(null, siguiente?.id ?? null);
      return;
    }
    const indice = items.findIndex((i) => i.id === actual.id);
    const finales = estrellasDe(items, null);
    const t: Transicion = {
      indice,
      racha: finales[indice] === "primero" ? rachaDe(finales.slice(0, indice + 1)) : 0,
      siguiente: siguiente ? NOMBRE_DEL_TIPO[siguiente.type] : "El cierre",
      estrellas: finales,
      cerrados: items.filter((i) => i.closed).length,
    };
    setTransicion(t);
    // El siguiente se monta detrás de la transición y entra (24 px desde la derecha) cuando se va.
    setEntrando(false);
    setActualId(siguiente?.id ?? null);
  };

  return (
    <div className="practica relative flex min-h-dvh flex-col bg-crema text-ink lg:min-h-dvh">
      <header className="flex h-[68px] shrink-0 items-center justify-between gap-3 px-3 lg:h-24 lg:px-10">
        <button
          type="button"
          onClick={() => setSalir(true)}
          aria-label="Salir y seguir luego"
          className="flex h-11 min-w-11 cursor-pointer items-center justify-center gap-2 rounded-pill text-[14px] font-bold text-ink lg:bg-white lg:pr-[18px] lg:pl-3.5"
        >
          <X size={22} strokeWidth={1.75} aria-hidden />
          <span className="hidden lg:inline">Salir y seguir luego</span>
        </button>
        <Constelacion
          estados={estrellas}
          forma={forma}
          ancho={170}
          r={14}
          guias
          encender={actual && actual.closed && !actual.skipped ? items.indexOf(actual) : -1}
          className="w-[124px] lg:w-[170px]"
        />
        {racha >= 3 ? (
          <span className="flex w-11 justify-end lg:w-[180px]">
            <Racha n={racha} />
          </span>
        ) : (
          <span className="w-11 lg:w-[180px]" aria-hidden />
        )}
      </header>

      {actual && (
        <Pantalla
          key={actual.id}
          item={actual}
          profe={profe}
          entrando={entrando}
          onItem={onItem}
          onSeguir={seguir}
        />
      )}

      {!actual && (!transicion || errorAlTerminar) && (
        <div className="flex flex-1 flex-col items-center justify-center gap-4 px-6 text-center">
          {errorAlTerminar ? (
            <>
              <AvisoError mensaje={errorAlTerminar} />
              <BotonPrincipal onClick={onTerminar}>Intentar otra vez</BotonPrincipal>
            </>
          ) : (
            <p role="status" className="text-[15px] font-semibold text-ink-2">
              Encendiendo tu constelación…
            </p>
          )}
        </div>
      )}

      {regreso && (
        <div
          role="status"
          className="pr-aparece absolute top-16 left-1/2 z-30 flex w-[calc(100%-24px)] -translate-x-1/2 items-center gap-3 rounded-sub bg-ink py-3 pr-3 pl-4 text-crema shadow-[0_14px_30px_-12px_rgba(51,32,59,.55)] lg:top-[88px] lg:w-[460px]"
        >
          <RotateCcw size={20} strokeWidth={1.75} className="shrink-0 text-durazno" aria-hidden />
          <span className="flex-1 text-[14px] leading-[1.4]">
            <strong>Sigues donde ibas.</strong> {regreso}
          </span>
          <button
            type="button"
            aria-label="Cerrar aviso"
            onClick={() => setRegreso(null)}
            className="flex h-11 w-11 shrink-0 cursor-pointer items-center justify-center text-crema"
          >
            <X size={18} strokeWidth={1.75} />
          </button>
        </div>
      )}

      {transicion && !(errorAlTerminar && !actualId) && (
        <PantallaDeTransicion
          t={transicion}
          forma={forma}
          total={items.length}
          terminando={!actualId}
          onListo={() => avanzar(transicion, actualId)}
        />
      )}

      {salir && (
        <DialogoSalir
          onQuedarse={() => setSalir(false)}
          onSalir={() => router.push("/cuenta?seccion=resumen")}
        />
      )}
    </div>
  );
}

/* ------------------------------------------------------------------------------------------------
 * Un ejercicio, con su máquina de estados (§13)
 * ---------------------------------------------------------------------------------------------- */

const TEXTO_RIGEL: Partial<Record<Fase, string>> = {
  enviando: "Déjame ver…",
  correcto: "Estrella encendida.",
  casi: "Vas bien. Mira la pista.",
  mostrada: "Tranqui, así queda.",
};
const POSE_RIGEL: Record<Fase, RigelPose> = {
  inicial: "guia",
  interactuando: "guia",
  enviando: "atento",
  correcto: "celebracion",
  casi: "animo",
  mostrada: "animo",
  sinvoz: "guia",
  saltado: "guia",
};
const TEXTO_MEISSA: Partial<Record<Fase, string>> = {
  enviando: "Déjame ver…",
  correcto: "¡Bien oído!",
  casi: "Te la digo otra vez, más despacio.",
  mostrada: "Así suena. Repítela conmigo.",
  sinvoz: "Tu dispositivo no me deja hablar en inglés.",
  saltado: "Sin problema. Seguimos.",
};
const ESTADO_MEISSA: Record<Fase, MeissaEstado> = {
  inicial: "escucha",
  interactuando: "escucha",
  enviando: "piensa",
  correcto: "cierre",
  casi: "escucha",
  mostrada: "habla",
  sinvoz: "piensa",
  saltado: "escucha",
};

function Pantalla({
  item,
  profe,
  entrando,
  onItem,
  onSeguir,
}: {
  item: Ejercicio;
  profe: string;
  entrando: boolean;
  onItem: (item: Ejercicio) => void;
  onSeguir: () => void;
}) {
  const voz = useVozEnIngles();
  const seOye = SE_OYEN.includes(item.type);
  const [respuesta, setRespuesta] = useState<string | null>(null);
  const [tocado, setTocado] = useState(false);
  const [casi, setCasi] = useState(false);
  const [ronda, setRonda] = useState(0);
  const [intentosQueQuedan, setIntentosQueQuedan] = useState<number | null>(null);
  const [pendiente, setPendiente] = useState<[string, string] | null>(null);
  const [fallo, setFallo] = useState<[string, string] | null>(null);

  const responder = useMutation({
    mutationFn: (texto: string) =>
      apiFetch<Resultado>(`/api/v1/practice-items/${item.id}/answer`, { method: "POST", body: { answer: texto } }),
    onSuccess: (r) => {
      onItem(r.item);
      setIntentosQueQuedan(r.attemptsLeft);
      if (!r.correct && !r.closed) setCasi(true);
    },
  });
  const unir = useMutation({
    mutationFn: (par: [string, string]) =>
      apiFetch<PairView>(`/api/v1/practice-items/${item.id}/pair`, {
        method: "POST",
        body: { term: par[0], meaning: par[1] },
      }),
    onMutate: (par) => setPendiente(par),
    onSuccess: (r, par) => {
      onItem(r.item);
      setIntentosQueQuedan(r.attemptsLeft);
      if (!r.pairCorrect && !r.closed) {
        setFallo(par);
        setCasi(true);
      }
    },
    onSettled: () => setPendiente(null),
  });
  const saltar = useMutation({
    mutationFn: () => apiFetch<Ejercicio>(`/api/v1/practice-items/${item.id}/skip`, { method: "POST" }),
    onSuccess: onItem,
  });

  const parejas = item.type === "MATCH_MEANING";
  const unidos = parejas ? Object.keys(leerUnidosSeguro(item.answer)).length : 0;
  const totalPares = parejas ? (leerPayload<{ terms?: string[] }>(item).terms ?? []).length : 0;
  // Parejas: solo el último par se ve «Comprobando…»; los de antes se unen sin detener el juego.
  const enviandoPar = parejas && pendiente !== null && unidos + 1 === totalPares;

  let fase: Fase;
  if (item.skipped) fase = "saltado";
  else if (item.closed) fase = item.correct ? "correcto" : "mostrada";
  else if (responder.isPending || enviandoPar) fase = "enviando";
  else if (seOye && voz.disponible === false) fase = "sinvoz";
  else if (casi) fase = "casi";
  else if (tocado || unidos > 0) fase = "interactuando";
  else fase = "inicial";

  const segundoIntento = item.attempts > 0 && !item.closed;
  const quedan = intentosQueQuedan ?? (segundoIntento ? 1 : null);

  const onRespuesta = (r: string | null, t: boolean) => {
    setRespuesta(r);
    if (t) {
      setTocado(true);
      setCasi(false);
    }
  };
  const otraVez = () => {
    setCasi(false);
    setFallo(null);
    setRespuesta(null);
    setTocado(false);
    setRonda((n) => n + 1);
  };

  const propsTipo = { item, fase, onRespuesta };
  const llave = `${item.id}-${ronda}`;
  let bloque;
  switch (item.type) {
    case "MATCH_MEANING":
      bloque = (
        <Parejas
          key={llave}
          item={item}
          fase={fase}
          pendiente={pendiente}
          fallo={fallo}
          onUnir={(t, m) => unir.mutate([t, m])}
          onTocar={() => {
            setCasi(false);
            setTocado(true);
          }}
        />
      );
      break;
    case "FILL_BLANK":
      bloque = <Completa key={llave} {...propsTipo} />;
      break;
    case "FIX_SENTENCE":
      bloque = <Corrige key={llave} {...propsTipo} />;
      break;
    case "SPOT_ERROR":
      bloque = <CazaElError key={llave} {...propsTipo} />;
      break;
    case "BUILD_SENTENCE":
      bloque = <ArmaLaFrase key={llave} {...propsTipo} />;
      break;
    case "ORDER_DIALOGUE":
      bloque = <OrdenaLaConversacion key={llave} {...propsTipo} />;
      break;
    case "CHOOSE_REPLY":
      bloque = <RespondeEnElChat key={llave} {...propsTipo} />;
      break;
    case "LISTEN_CHOOSE":
      bloque = <EscuchaYElige key={llave} {...propsTipo} voz={voz} />;
      break;
    case "DICTATION":
      bloque = <EscuchaYEscribe key={llave} {...propsTipo} voz={voz} />;
      break;
    case "WRITE_SENTENCE":
      bloque = <TuFrase key={llave} {...propsTipo} />;
      break;
  }

  // La burbuja y la pose: Rigel en todo menos en Escucha, donde habla Meissa. Nunca los dos.
  const dice = leerPayload<{ say?: string }>(item).say ?? "";
  let burbuja: string;
  if (seOye) {
    // Mientras suena, Meissa dice lo que pronuncia. En el dictado no: sería darle la respuesta.
    if (voz.hablando && (fase === "inicial" || fase === "interactuando" || fase === "casi")) {
      burbuja = item.type === "LISTEN_CHOOSE" ? `«${dice}»` : "Te la digo en inglés. Escucha bien.";
    } else {
      burbuja = TEXTO_MEISSA[fase] ?? instruccionDe(item);
    }
  } else {
    burbuja = TEXTO_RIGEL[fase] ?? instruccionDe(item);
  }
  const personaje = seOye ? (
    <Meissa estado={voz.hablando ? "habla" : ESTADO_MEISSA[fase]} decorativo className="h-auto w-[72px] shrink-0 lg:w-[176px]" />
  ) : (
    <Rigel pose={POSE_RIGEL[fase]} decorativo className="h-auto w-[72px] shrink-0 lg:w-[176px]" />
  );

  // El panel de respuesta. En el segundo intento la pista se queda a la vista mientras se corrige.
  let panel = null;
  const tuFrase = item.type === "WRITE_SENTENCE";
  if (fase === "correcto") {
    const termino = leerPayload<{ term?: string }>(item).term;
    panel = (
      <PanelRespuesta
        tono="bien"
        titulo="Así es."
        cuerpo={tuFrase && termino ? `Tu frase usa ${termino} y se entiende. ${capital(profe)} la verá tal cual.` : item.explanation}
      />
    );
  } else if (fase === "mostrada") {
    const accepted = item.type === "FIX_SENTENCE" ? leerPayload<{ accepted?: string[] }>(item).accepted : undefined;
    panel = (
      <PanelRespuesta
        tono="mostrada"
        titulo={tuFrase ? "Mira este ejemplo." : "Ahora ya la tienes."}
        respuesta={item.expected ? respuestaMostrada(item, item.expected) : null}
        cuerpo={
          tuFrase
            ? item.expected
              ? `Es un ejemplo. Tu frase también le llega a ${profe} tal cual la escribiste.`
              : item.explanation
            : item.explanation
        }
        nota={accepted?.[0] ? `También vale: ${accepted[0]}` : null}
      />
    );
  } else if (fase === "saltado") {
    panel = <PanelRespuesta tono="saltado" titulo="Lo saltamos." cuerpo={`No cuenta como error. ${capital(profe)} lo verá como saltado.`} />;
  } else if ((fase === "casi" || segundoIntento) && fase !== "sinvoz" && item.hint) {
    panel = (
      <PanelRespuesta
        tono="casi"
        titulo="Casi…"
        cuerpo={item.hint}
        nota={quedan === 1 ? "Te queda un intento." : quedan && quedan > 1 ? `Te quedan ${quedan} intentos.` : null}
      />
    );
  }

  // El botón principal según la fase (copy-y-estados, «Textos fijos»).
  let principal;
  if (fase === "correcto" || fase === "mostrada" || fase === "saltado") {
    principal = <BotonPrincipal onClick={onSeguir} className="flex-1 lg:flex-none">Seguir</BotonPrincipal>;
  } else if (fase === "enviando") {
    principal = (
      <BotonPrincipal cargando className="flex-1 lg:flex-none">
        {tuFrase ? "Revisando tu frase…" : "Comprobando…"}
      </BotonPrincipal>
    );
  } else if (fase === "casi") {
    principal = <BotonPrincipal onClick={otraVez} className="flex-1 lg:flex-none">Intentar otra vez</BotonPrincipal>;
  } else if (parejas) {
    principal = <BotonPrincipal deshabilitado className="flex-1 lg:flex-none">Seguir</BotonPrincipal>;
  } else {
    principal = (
      <BotonPrincipal
        deshabilitado={fase === "sinvoz" || respuesta === null}
        onClick={() => respuesta && responder.mutate(respuesta)}
        className="flex-1 lg:flex-none"
      >
        Comprobar
      </BotonPrincipal>
    );
  }

  const error = responder.error ?? unir.error ?? saltar.error;
  const idInstruccion = `instruccion-${item.id}`;

  return (
    <div
      className={`flex flex-1 flex-col gap-3.5 px-5 pb-4 lg:flex-row lg:items-start lg:justify-center lg:gap-10 lg:px-14 lg:pt-2 lg:pb-10 ${
        entrando ? "pr-entra" : ""
      }`}
    >
      <div className="flex shrink-0 items-center gap-3 lg:w-[220px] lg:flex-col-reverse">
        {personaje}
        <div className="min-w-0 flex-1 rounded-[18px_18px_18px_6px] bg-white px-3.5 py-3 text-[15px] leading-[1.4] font-semibold text-pretty shadow-bubble lg:w-full lg:rounded-[20px] lg:px-[18px] lg:py-4 lg:text-[17px]">
          <span id={idInstruccion} aria-live="polite">
            {burbuja}
          </span>
        </div>
      </div>

      <section
        aria-labelledby={idInstruccion}
        className="flex min-h-0 flex-1 flex-col gap-4 lg:max-w-[680px] lg:rounded-tarjeta lg:bg-white lg:px-8 lg:py-7 lg:shadow-card"
      >
        <ChipCategoria categoria={item.category} tipo={NOMBRE_DEL_TIPO[item.type]} />
        {bloque}
        <div className="min-h-0 flex-1" />
        {panel}
        {error && <AvisoError mensaje={error.message} />}
        <div className="flex shrink-0 gap-2.5 lg:justify-end">
          {fase === "sinvoz" && (
            <BotonSecundario icono={SkipForward} onClick={() => saltar.mutate()} deshabilitado={saltar.isPending} className="flex-1 lg:flex-none">
              Saltar este
            </BotonSecundario>
          )}
          {principal}
        </div>
      </section>
    </div>
  );
}

function leerUnidosSeguro(answer: string | null): Record<string, string> {
  if (!answer || !answer.startsWith("{")) return {};
  try {
    return JSON.parse(answer) as Record<string, string>;
  } catch {
    return {};
  }
}

/**
 * La respuesta en la caja de «Ahora ya la tienes.»: en Completa, la frase entera (no solo la palabra
 * que iba); en Ordena, lo que dice cada uno, sin los nombres, que la lista de arriba ya muestra.
 */
function respuestaMostrada(item: Ejercicio, esperada: string): string {
  if (item.type === "FILL_BLANK") {
    const frase = leerPayload<{ sentence?: string }>(item).sentence;
    if (frase && /_{2,}/.test(frase)) return frase.replace(/_{2,}/, esperada);
  }
  if (item.type === "ORDER_DIALOGUE") {
    try {
      return (JSON.parse(esperada) as string[]).map((l) => partirLinea(l).texto).join(" → ");
    } catch {
      // Tal cual, si no se puede leer.
    }
  }
  return mostrarEsperada(item.type, esperada);
}

function capital(s: string): string {
  return s.charAt(0).toUpperCase() + s.slice(1);
}

/* ------------------------------------------------------------------------------------------------
 * La transición (§10.4)
 * ---------------------------------------------------------------------------------------------- */

function PantallaDeTransicion({
  t,
  forma,
  total,
  terminando,
  onListo,
}: {
  t: Transicion;
  forma: number;
  total: number;
  terminando: boolean;
  onListo: () => void;
}) {
  const conRacha = t.racha >= 3;
  const listo = useRef(false);
  const terminar = useCallback(() => {
    if (listo.current) return;
    listo.current = true;
    onListo();
  }, [onListo]);

  // 1,4 s con racha y 0,6 s sin ella; tocar adelanta.
  useEffect(() => {
    const ms = conRacha ? 1400 : 600;
    const id = window.setTimeout(terminar, ms);
    return () => window.clearTimeout(id);
  }, [conRacha, terminar]);

  return (
    <div
      role="status"
      aria-live="polite"
      onClick={terminar}
      className="pr-aparece fixed inset-0 z-40 flex cursor-pointer flex-col items-center justify-center gap-[18px] bg-noche p-8 text-center text-crema"
    >
      <Constelacion
        estados={t.estrellas}
        forma={forma}
        ancho={420}
        r={10}
        guias
        fondo="noche"
        encender={t.indice}
        className="w-[300px] lg:w-[420px]"
      />
      {conRacha && (
        <span className="pr-racha flex h-10 items-center gap-2 rounded-pill bg-durazno px-4 text-[15px] font-extrabold text-ink">
          <Flame size={18} strokeWidth={1.75} aria-hidden />
          Racha
        </span>
      )}
      <h2 className="m-0 font-display text-[38px] leading-[1.05] font-extrabold lg:text-[52px]">
        {conRacha ? (TEXTO_RACHA[Math.min(t.racha, 5)] ?? "¡Sigue la racha!") : "Estrella encendida"}
      </h2>
      <p className="m-0 max-w-[340px] text-[16px] leading-[1.5] text-[#E9DEF5]">
        {conRacha ? "Todas al primer intento. Tu constelación va brillando." : `Llevas ${t.cerrados} de ${total}.`}
      </p>
      <Rigel pose={conRacha ? "racha" : "celebracion"} decorativo className="h-auto w-[130px] lg:w-[150px]" />
      <span className="flex items-center gap-2 text-[14px] font-bold text-durazno-soft">
        {terminando ? "Sigue: El cierre" : `Sigue: ${t.siguiente}`}
        <ChevronRight size={16} strokeWidth={1.75} aria-hidden />
      </span>
    </div>
  );
}

/* ------------------------------------------------------------------------------------------------
 * «¿Sigues luego?» (§10.7)
 * ---------------------------------------------------------------------------------------------- */

function DialogoSalir({ onQuedarse, onSalir }: { onQuedarse: () => void; onSalir: () => void }) {
  const primero = useRef<HTMLButtonElement>(null);
  useCerrarConEscape(true, onQuedarse);
  useEffect(() => {
    primero.current?.focus();
  }, []);
  return (
    <div
      className="pr-aparece fixed inset-0 z-50 flex items-end justify-center bg-[rgba(46,30,78,.62)] lg:items-center"
      onClick={(e) => {
        if (e.target === e.currentTarget) onQuedarse();
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="titulo-salir"
        className="pr-hoja flex w-full flex-col items-center gap-3 rounded-[28px_28px_0_0] bg-white px-5 pt-6 pb-7 text-center lg:w-[440px] lg:rounded-tarjeta lg:px-8 lg:pt-7 lg:pb-5"
      >
        <Rigel pose="animo" decorativo className="h-auto w-[110px]" />
        <h2 id="titulo-salir" className="m-0 font-display text-[26px] leading-[1.15] font-bold">
          ¿Sigues luego?
        </h2>
        <p className="m-0 text-[15px] leading-[1.55] text-pretty text-ink-2">
          Guardamos lo que llevas. Cuando vuelvas, arrancas en este mismo ejercicio, con tus estrellas encendidas.
        </p>
        <div className="flex flex-col gap-1.5 self-stretch pt-1.5">
          <button
            ref={primero}
            type="button"
            onClick={onSalir}
            className="h-14 cursor-pointer rounded-pill bg-coral text-[16px] font-bold text-crema shadow-cta hover:bg-coral-hover"
          >
            Salir y seguir luego
          </button>
          <button type="button" onClick={onQuedarse} className="h-[52px] cursor-pointer rounded-pill text-[16px] font-bold text-ink">
            Seguir practicando
          </button>
        </div>
      </div>
    </div>
  );
}
