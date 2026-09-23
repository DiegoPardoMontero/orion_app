"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { apiFetch } from "@/lib/api/fetch";
import {
  ConversacionDeVoz,
  traduccionVisible,
  type DiagnosticoIniciado,
  type FaseDeMeissa,
  type TurnoMedido,
} from "@/lib/api/diagnostico";
import { Meissa } from "@/components/Meissa";
import { Boton } from "@/components/ui";

/**
 * Las preguntas del guion: seis de Meissa, contando la del saludo. El contador avanza cuando
 * empieza cada turno suyo y se queda en la sexta durante la despedida.
 */
const TURNOS = 6;

/**
 * Las notas que se le mandan a Meissa. Son cadenas exactas que el guion v5 reconoce; un test del
 * backend comprueba que las dos partes digan lo mismo.
 */
const NOTA_ORION = "[Orión now]";
const NOTA_VEINTE_SEGUNDOS = "[20 seconds left]";
const NOTA_SE_ACABO = "[Time is up]";

/** Lo que se espera a la despedida después de los dos minutos antes de cerrar igual. */
const GRACIA_SEGUNDOS = 25;

const ESTADO: Record<FaseDeMeissa, string> = {
  escucha: "te escucha",
  habla: "está hablando",
  piensa: "un momento…",
};

/**
 * La conversación, con el diseño «durante» del handoff de Meissa: ella sola y centrada, sobre
 * crema, sin nada que compita con su voz.
 *
 * <p><strong>Lo que Meissa dice va escrito; lo que dices tú, no.</strong> Leer la pregunta ayuda a
 * entenderla, y no hay lip-sync: el subtítulo es lo que se lee. Debajo, su traducción al español,
 * frase por frase mientras habla, y ocultable (handoff de Meissa). Pero la transcripción propia sigue
 * sin aparecer, porque la gente se corrige al verse escrita y eso arruina justo lo que se mide.
 *
 * <p>La conversación es libre (Pardo, 22/09/2026): Meissa detecta sola cuándo terminaste, no hay
 * botón de «ya terminé». A falta de veinte segundos Meissa lo avisa en su siguiente turno, y al
 * llegar a los dos minutos se despide; la pantalla se cierra cuando termina la despedida.
 *
 * <p>Salir nunca pasa en silencio: pide confirmación en una hoja, porque perder dos minutos de
 * exposición por un toque accidental es lo peor que le puede pasar a esta pantalla.
 */
export function Conversacion({
  sesion,
  minutos,
  onTerminar,
  onSalir,
  onReintentar,
}: {
  sesion: DiagnosticoIniciado;
  minutos: number;
  onTerminar: () => void;
  onSalir: () => void;
  onReintentar: () => void;
}) {
  const voz = useRef<ConversacionDeVoz | null>(null);
  const [fase, setFase] = useState<FaseDeMeissa>("piensa");
  const [subtitulo, setSubtitulo] = useState("");
  // La traducción del turno en curso, frase por frase: `undefined` mientras llega, `null` si no
  // hay nada que mostrar (la frase ya era español, o no llegó a tiempo).
  const [traducciones, setTraducciones] = useState<(string | null | undefined)[]>([]);
  const turnoEnPantalla = useRef(0);
  const [mostrarTraduccion, setMostrarTraduccion] = useState(leerPreferenciaDeTraduccion);
  const [turno, setTurno] = useState(1);
  const [segundos, setSegundos] = useState(0);
  const [fallo, setFallo] = useState<string | null>(null);
  const [confirmarSalida, setConfirmarSalida] = useState(false);
  const cerrando = useRef(false);
  const avisado = useRef(false);
  const seAcabo = useRef(false);

  const terminar = useCallback(() => {
    if (cerrando.current) return;
    cerrando.current = true;
    voz.current?.colgar();
    onTerminar();
  }, [onTerminar]);

  // Los turnos se empujan según llegan y no al final: si la persona cierra la pestaña a mitad, lo
  // que ya dijo está guardado y la conversación se puede cerrar igual.
  useEffect(() => {
    const cliente = new ConversacionDeVoz({
      onFase: setFase,
      onSubtitulo: setSubtitulo,
      onError: setFallo,
      onEmpiezaTurnoDeMeissa: (numero) => {
        turnoEnPantalla.current = numero;
        setTraducciones([]);
        setTurno(Math.min(TURNOS, numero));
      },
      onFrase: (frase, turnoDeLaFrase, indice) => {
        setTraducciones((previas) => {
          const nuevas = [...previas];
          nuevas[indice] = undefined;
          return nuevas;
        });
        void apiFetch<{ translation: string | null }>(
          `/api/v1/assessments/${sesion.assessmentId}/translate`,
          { method: "POST", body: { text: frase }, redirectOn401: false },
        )
          .then((r) => r.translation)
          .catch(() => null)
          .then((traduccion) => {
            // Si Meissa ya pasó a otro turno, esta traducción es de algo que ya no está en pantalla.
            if (turnoEnPantalla.current !== turnoDeLaFrase) return;
            setTraducciones((previas) => {
              const nuevas = [...previas];
              nuevas[indice] = traduccion;
              return nuevas;
            });
          });
      },
      onTurnoDeMeissa: (cuantos, preguntaba) => {
        // Antes de su cuarto turno le toca nombrar a Orión, una vez. Lo dispara la pantalla y no
        // su propia cuenta: contando sola, lo nombraba en casi todos los turnos.
        if (cuantos === 3) voz.current?.nota(NOTA_ORION);
        // Un turno sin pregunta después de la sexta, del aviso o del final es la despedida: se deja
        // un respiro y se pasa al resultado.
        if (!preguntaba && (cuantos >= TURNOS || avisado.current || seAcabo.current)) {
          setTimeout(terminar, 1500);
        }
      },
      onTurno: (t: TurnoMedido) => {
        void apiFetch(`/api/v1/assessments/${sesion.assessmentId}/turns`, {
          method: "POST",
          body: t,
          redirectOn401: false,
        }).catch(() => undefined);
      },
    });
    voz.current = cliente;

    cliente
      .conectar(sesion.clientSecret, sesion.model)
      .catch((e: Error) => setFallo(e.message));

    return () => cliente.colgar();
  }, [sesion, terminar]);

  useEffect(() => {
    const id = setInterval(() => setSegundos((s) => s + 1), 1000);
    return () => clearInterval(id);
  }, []);

  // Si el ajuste no llegó, los dos minutos del guion: un reloj en «NaN:NaN» no le dice nada a nadie.
  const total = (Number.isFinite(minutos) && minutos > 0 ? minutos : 2) * 60;
  const transcurrido = Math.min(segundos, total);
  const reloj = `${Math.floor(transcurrido / 60)}:${String(transcurrido % 60).padStart(2, "0")}`;

  // El tiempo, en tres momentos. A los veinte segundos del final, la nota: Meissa lo dice en su
  // siguiente turno, sin interrumpir. Al final, la despedida: se pide ya si nadie está hablando, y
  // si no, en cuanto termine quien habla. Y un corte duro por si la despedida no llega nunca:
  // quedarse esperando al proveedor deja a la persona hablando sola.
  useEffect(() => {
    if (!avisado.current && segundos >= total - 20) {
      avisado.current = true;
      voz.current?.nota(NOTA_VEINTE_SEGUNDOS);
    }
    if (!seAcabo.current && segundos >= total) {
      seAcabo.current = true;
      voz.current?.pedirDespedida(NOTA_SE_ACABO);
    }
    if (segundos >= total + GRACIA_SEGUNDOS) terminar();
  }, [segundos, total, terminar]);

  return (
    // A pantalla completa y por encima del armazón de la app: nada compite con la voz de Meissa,
    // ni siquiera la navegación. Las hojas (z-50) siguen quedando encima.
    <main className="fixed inset-0 z-40 overflow-y-auto bg-surface">
      <div className="mx-auto flex min-h-dvh w-full max-w-lg flex-col px-6 pb-7 pt-5">
        <div className="flex items-center justify-between">
          <span className="inline-flex items-center gap-1.5 rounded-pill bg-accent-lavender-soft px-3 py-1 text-[12.5px] font-bold text-[#5E4A8A]">
            <span aria-hidden="true" className="h-1.5 w-1.5 rounded-full bg-[#7A4A8C]" />
            Diagnóstico
          </span>
          <span className="text-[14px] font-semibold tabular-nums text-text-secondary">
            Pregunta {turno} de {TURNOS} · {reloj}
          </span>
        </div>
        <div
          className="mt-3 h-1.5 overflow-hidden rounded-full bg-accent-lavender-soft"
          role="progressbar"
          aria-label="Tiempo de la conversación"
          aria-valuemin={0}
          aria-valuemax={total}
          aria-valuenow={transcurrido}
        >
          <div
            className="h-full rounded-full bg-[#7A4A8C] transition-[width] duration-1000 ease-linear"
            style={{ width: `${(transcurrido / total) * 100}%` }}
          />
        </div>

        <div className="flex flex-1 flex-col items-center justify-center py-6">
          <Meissa estado={fase} decorativo className="h-[200px] w-auto lg:h-[230px]" />
          <p role="status" aria-live="polite" className="mt-2 text-[14px] font-semibold text-text-secondary">
            <span className="text-[#7A4A8C]">Meissa</span> · {ESTADO[fase]}
          </p>
        </div>

        <section className="rounded-[24px] bg-surface-raised p-5 shadow-[0_10px_30px_-12px_rgba(51,32,59,0.25)]">
          {fallo ? (
            <>
              <p className="text-[15px] font-semibold text-text">
                No te oímos. Revisa el micrófono e intenta de nuevo.
              </p>
              <Boton variante="primario" className="mt-4 w-full" onClick={onReintentar}>
                Reintentar
              </Boton>
            </>
          ) : (
            <>
              <p lang="en" className="min-h-[3.2em] font-display text-[19px] font-semibold leading-snug text-text">
                {subtitulo || "…"}
              </p>
              {mostrarTraduccion && traduccionVisible(traducciones) && (
                <p lang="es" className="mt-2 text-[14px] leading-relaxed text-text-secondary">
                  {traduccionVisible(traducciones)}
                </p>
              )}
            </>
          )}
        </section>
        {!fallo && (
          <button
            type="button"
            onClick={() => {
              const nueva = !mostrarTraduccion;
              setMostrarTraduccion(nueva);
              guardarPreferenciaDeTraduccion(nueva);
            }}
            className="mx-auto mt-2 min-h-11 px-3 text-[13px] font-semibold text-text-secondary underline decoration-border underline-offset-4 hover:text-text focus-visible:shadow-focus"
          >
            {mostrarTraduccion ? "Ocultar traducción" : "Ver traducción al español"}
          </button>
        )}

        <button
          type="button"
          onClick={() => setConfirmarSalida(true)}
          className="mt-3 h-11 rounded-pill text-[14px] font-bold text-text-secondary hover:bg-surface-sunken focus-visible:shadow-focus"
        >
          Salir del diagnóstico
        </button>

        {confirmarSalida && (
          <HojaDeSalida
            onSeguir={() => setConfirmarSalida(false)}
            onSalir={() => {
              cerrando.current = true;
              voz.current?.colgar();
              onSalir();
            }}
          />
        )}
      </div>
    </main>
  );
}

const PREFERENCIA_TRADUCCION = "orion.meissa.traduccion";

/** Se recuerda en el navegador; sin almacenamiento (modo privado), se muestra. */
function leerPreferenciaDeTraduccion(): boolean {
  try {
    return window.localStorage.getItem(PREFERENCIA_TRADUCCION) !== "oculta";
  } catch {
    return true;
  }
}

function guardarPreferenciaDeTraduccion(mostrar: boolean) {
  try {
    window.localStorage.setItem(PREFERENCIA_TRADUCCION, mostrar ? "visible" : "oculta");
  } catch {
    // Sin almacenamiento la preferencia dura lo que dure la pantalla, y está bien.
  }
}

/** La hoja inferior de «¿Salir?». En un portal, para que ninguna barra de la app quede encima. */
function HojaDeSalida({ onSeguir, onSalir }: { onSeguir: () => void; onSalir: () => void }) {
  useEffect(() => {
    const alPulsar = (e: KeyboardEvent) => e.key === "Escape" && onSeguir();
    window.addEventListener("keydown", alPulsar);
    return () => window.removeEventListener("keydown", alPulsar);
  }, [onSeguir]);

  return createPortal(
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-[#33203B]/40" onClick={onSeguir}>
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="salir-titulo"
        className="w-full max-w-lg rounded-t-[24px] bg-surface p-6 pb-8 [animation:sheet-up_220ms_ease-out_both]"
        onClick={(e) => e.stopPropagation()}
      >
        <p id="salir-titulo" className="font-display text-[20px] font-bold">
          ¿Salir? Perderás lo que llevas.
        </p>
        <div className="mt-5 grid gap-2.5">
          <Boton variante="primario" className="w-full" onClick={onSeguir} autoFocus>
            Seguir hablando
          </Boton>
          <Boton variante="contorno" className="w-full" onClick={onSalir}>
            Salir
          </Boton>
        </div>
      </div>
    </div>,
    document.body,
  );
}
