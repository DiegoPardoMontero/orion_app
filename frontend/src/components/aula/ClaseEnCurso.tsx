"use client";

import { Maximize2, Mic, MicOff, PhoneOff, Video, VideoOff } from "lucide-react";
import { usePathname, useRouter } from "next/navigation";
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
  useSyncExternalStore,
  type ReactNode,
  type PointerEvent as PointerEventReact,
} from "react";
import { createPortal } from "react-dom";
import { appIdDe, cargarJitsi, type ClassroomResponse } from "@/lib/api/aula";

/**
 * La clase en curso, viva en el armazón de la app (24/09/2026: «que yo pueda minimizar y seguir
 * viendo lo de Orión»).
 *
 * <p>Antes la videollamada vivía en la página del aula: salir de ella —con el menú, con «atrás»—
 * desmontaba el iframe y cortaba la clase. Ahora vive aquí, en un proveedor que envuelve toda la
 * zona autenticada, y la página del aula solo le presta su escenario. Fuera del aula, la llamada
 * se encoge a una ventana flotante con micrófono, cámara, volver y colgar; al volver, se agranda.
 *
 * <p><strong>El iframe no se mueve nunca de sitio en el DOM.</strong> Es la regla que sostiene todo:
 * mover un iframe a otro padre lo recarga, y recargarlo es salir y volver a entrar a la sala. Así
 * que vive en un único contenedor fijo, en un portal a {@code body}, y lo que cambia al minimizar es
 * su posición y su tamaño —con una transición: esa es la animación—.
 *
 * <p>Límite honesto: la llamada sigue mientras se navega dentro de la app. Recargar la página, cerrar
 * la pestaña o salir de la zona autenticada (la portada, el login) la cortan; la pestaña lo avisa.
 */

export type FaseDeLlamada = "dentro" | "caida" | "cierre";

type Llamada = {
  bookingId: string;
  datos: ClassroomResponse;
  fase: FaseDeLlamada;
  /** Cuándo se entró, para contar los minutos. */
  entro: number;
  minutos: number;
  micOn: boolean;
  camOn: boolean;
};

type JitsiApi = {
  dispose: () => void;
  executeCommand: (comando: string) => void;
  addListener: (evento: string, fn: (datos?: unknown) => void) => void;
};

type Contexto = {
  llamada: Llamada | null;
  entrar: (bookingId: string, datos: ClassroomResponse, opciones: { micOn: boolean; camOn: boolean }) => void;
  volverAEntrar: () => void;
  terminar: () => void;
  salir: () => void;
  cerrar: () => void;
  /** La página del aula presta aquí su escenario; sin escenario, la llamada va en la ventana flotante. */
  registrarEscenario: (elemento: HTMLElement | null) => void;
  /** Quien entró a la sala en los últimos segundos, para el aviso de bienvenida. */
  llego: string | null;
};

const ContextoClase = createContext<Contexto | null>(null);

export function useClaseEnCurso(): Contexto {
  const c = useContext(ContextoClase);
  if (!c) throw new Error("useClaseEnCurso fuera de ClaseEnCurso");
  return c;
}

export const rutaDelAula = (bookingId: string) => `/mis-clases/${bookingId}/aula`;

const sinSuscripcion = () => () => {};

function suscribirAlTamano(avisar: () => void) {
  window.addEventListener("resize", avisar);
  return () => window.removeEventListener("resize", avisar);
}

type Caja = { left: number; top: number; width: number; height: number };

export function ClaseEnCurso({ children }: { children: ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const [llamada, setLlamada] = useState<Llamada | null>(null);
  const [escenario, setEscenario] = useState<HTMLElement | null>(null);
  const [rectEscenario, setRectEscenario] = useState<Caja | null>(null);
  const [llego, setLlego] = useState<string | null>(null);
  // El portal necesita `document`: solo en el cliente.
  const montado = useSyncExternalStore(sinSuscripcion, () => true, () => false);
  const padreRef = useRef<HTMLDivElement>(null);
  const api = useRef<JitsiApi | null>(null);
  const llamadaRef = useRef<Llamada | null>(null);
  useEffect(() => {
    llamadaRef.current = llamada;
  });

  const minutosDentro = () =>
    llamadaRef.current ? Math.max(1, Math.round((Date.now() - llamadaRef.current.entro) / 60000)) : 0;

  /**
   * Salir de la sala de verdad: colgar primero es lo que avisa a la conferencia; sin eso, quien
   * sigue en clase ve un fantasma tuyo al volver. El `try` es porque, con la conexión caída,
   * `executeCommand` lanza, y lo que no puede fallar es el `dispose` de después.
   */
  const soltar = useCallback(() => {
    try {
      api.current?.executeCommand("hangup");
    } catch {
      // La sesión ya no estaba viva.
    }
    api.current?.dispose();
    api.current = null;
  }, []);

  const montar = useCallback(() => {
    const actual = llamadaRef.current;
    const datos = actual?.datos;
    if (api.current || !actual || !datos?.token || !datos.domain || !datos.room || !padreRef.current) return;

    cargarJitsi(datos.domain, appIdDe(datos.room))
      .then(() => {
        if (!padreRef.current || api.current || llamadaRef.current?.bookingId !== actual.bookingId) return;
        const Constructor = (window as unknown as {
          JitsiMeetExternalAPI: new (domain: string, opciones: Record<string, unknown>) => JitsiApi;
        }).JitsiMeetExternalAPI;
        const instancia = new Constructor(datos.domain!, {
          roomName: datos.room,
          jwt: datos.token,
          parentNode: padreRef.current,
          width: "100%",
          height: "100%",
          userInfo: { displayName: datos.displayName },
          configOverwrite: {
            // La antesala de Orión reemplaza la de Jitsi: pasar por dos es pasar por una de más.
            prejoinPageEnabled: false,
            disableInviteFunctions: true,
            startWithAudioMuted: !actual.micOn,
            startWithVideoMuted: !actual.camOn,
          },
          interfaceConfigOverwrite: {
            TOOLBAR_BUTTONS: [
              "microphone", "camera", "desktop", "fullscreen", "hangup",
              "chat", "raisehand", "tileview", "settings", "videoquality",
            ],
            // La marca de Orión dentro del área de vídeo: la ve también la otra persona.
            SHOW_JITSI_WATERMARK: false,
            SHOW_BRAND_WATERMARK: true,
            BRAND_WATERMARK_LINK: "",
            DEFAULT_LOGO_URL: "/marca-orion.svg",
            DEFAULT_WELCOME_PAGE_LOGO_URL: "/marca-orion.svg",
            MOBILE_APP_PROMO: false,
          },
        });

        // Colgar es terminar: sube el cierre, aunque la llamada estuviera minimizada.
        instancia.addListener("readyToClose", () => {
          const minutos = minutosDentro();
          soltar();
          setLlamada((l) => (l ? { ...l, fase: "cierre", minutos } : l));
        });
        // Perder la conexión NO es terminar: a quien se queda sin internet le quedan minutos pagados.
        const caida = () => {
          const minutos = minutosDentro();
          setLlamada((l) => (l && l.fase === "dentro" ? { ...l, fase: "caida", minutos } : l));
        };
        instancia.addListener("connectionFailed", caida);
        instancia.addListener("suspendDetected", caida);
        instancia.addListener("audioMuteStatusChanged", (d) =>
          setLlamada((l) => (l ? { ...l, micOn: !(d as { muted?: boolean })?.muted } : l)),
        );
        instancia.addListener("videoMuteStatusChanged", (d) =>
          setLlamada((l) => (l ? { ...l, camOn: !(d as { muted?: boolean })?.muted } : l)),
        );
        instancia.addListener("participantJoined", (d) => {
          const nombre = (d as { displayName?: string })?.displayName?.split(" ")[0];
          if (nombre) setLlego(nombre);
        });
        api.current = instancia;
      })
      .catch(() => {
        setLlamada((l) => (l ? { ...l, fase: "caida", minutos: minutosDentro() } : l));
      });
    // Lo que necesita se lee de refs: depender de la llamada montaría un Jitsi nuevo en cada cambio.
  }, [soltar]);

  // Dentro, se monta; en la caída y en el cierre, se suelta (volver a entrar vuelve a montar).
  const fase = llamada?.fase;
  const bookingActual = llamada?.bookingId;
  useEffect(() => {
    if (fase === "dentro") montar();
    else soltar();
  }, [fase, bookingActual, montar, soltar]);

  // Si el armazón desaparece (cerrar sesión, salir a la portada), se cuelga: nada de fantasmas.
  useEffect(() => () => soltar(), [soltar]);

  // El aviso de quien llegó se va solo.
  useEffect(() => {
    if (!llego) return;
    const t = setTimeout(() => setLlego(null), 4500);
    return () => clearTimeout(t);
  }, [llego]);

  // Mientras haya clase, cerrar o recargar la pestaña pregunta: eso sí la cortaría.
  const enLlamada = fase === "dentro" || fase === "caida";
  useEffect(() => {
    if (!enLlamada) return;
    const alCerrar = (evento: BeforeUnloadEvent) => {
      evento.preventDefault();
      evento.returnValue = "";
    };
    window.addEventListener("beforeunload", alCerrar);
    return () => window.removeEventListener("beforeunload", alCerrar);
  }, [enLlamada]);

  // El escenario del aula: se mide y se sigue midiendo mientras exista. El observador mide también
  // la primera vez, al empezar a observar.
  useEffect(() => {
    if (!escenario) return;
    const medir = () => {
      const r = escenario.getBoundingClientRect();
      setRectEscenario({ left: r.left, top: r.top, width: r.width, height: r.height });
    };
    const observador = new ResizeObserver(medir);
    observador.observe(escenario);
    window.addEventListener("resize", medir);
    return () => {
      observador.disconnect();
      window.removeEventListener("resize", medir);
    };
  }, [escenario]);

  const aulaActual = llamada ? rutaDelAula(llamada.bookingId) : null;
  const enSuAula = aulaActual !== null && pathname === aulaActual;

  // Si cuelga (o se corta del todo) estando minimizada, se vuelve al aula: ahí está la hoja de cierre.
  useEffect(() => {
    if (llamada?.fase === "cierre" && !enSuAula && aulaActual) router.push(aulaActual);
  }, [llamada?.fase, enSuAula, aulaActual, router]);

  const contexto: Contexto = {
    llamada,
    entrar: (bookingId, datos, { micOn, camOn }) => {
      if (llamadaRef.current && llamadaRef.current.bookingId !== bookingId) soltar();
      setLlamada({ bookingId, datos, fase: "dentro", entro: Date.now(), minutos: 0, micOn, camOn });
    },
    volverAEntrar: () => setLlamada((l) => (l ? { ...l, fase: "dentro" } : l)),
    terminar: () => {
      const minutos = minutosDentro();
      soltar();
      setLlamada((l) => (l ? { ...l, fase: "cierre", minutos } : l));
    },
    salir: () => {
      soltar();
      setLlamada(null);
    },
    cerrar: () => setLlamada(null),
    registrarEscenario: setEscenario,
    llego,
  };

  const visible = fase === "dentro";
  const rect = escenario ? rectEscenario : null;
  const grande = visible && enSuAula && rect !== null;

  return (
    <ContextoClase.Provider value={contexto}>
      {children}
      {montado &&
        createPortal(
          <Escenario
            visible={visible}
            grande={grande}
            rect={rect}
            llamada={llamada}
            padreRef={padreRef}
            onVolver={() => aulaActual && router.push(aulaActual)}
            onMic={() => api.current?.executeCommand("toggleAudio")}
            onCam={() => api.current?.executeCommand("toggleVideo")}
            onColgar={() => api.current?.executeCommand("hangup")}
          />,
          document.body,
        )}
    </ContextoClase.Provider>
  );
}

/**
 * El único contenedor del iframe. Grande, se pone encima del escenario del aula; chico, flota en una
 * esquina y se puede arrastrar. Entre uno y otro se desliza.
 */
function Escenario({
  visible,
  grande,
  rect,
  llamada,
  padreRef,
  onVolver,
  onMic,
  onCam,
  onColgar,
}: {
  visible: boolean;
  grande: boolean;
  rect: Caja | null;
  llamada: Llamada | null;
  padreRef: React.RefObject<HTMLDivElement | null>;
  onVolver: () => void;
  onMic: () => void;
  onCam: () => void;
  onColgar: () => void;
}) {
  const ventana = {
    w: useSyncExternalStore(suscribirAlTamano, () => window.innerWidth, () => 0),
    h: useSyncExternalStore(suscribirAlTamano, () => window.innerHeight, () => 0),
  };
  // Dónde la dejó quien la arrastró: esquina izquierda o derecha, y a qué altura.
  const [lado, setLado] = useState<"izquierda" | "derecha">("derecha");
  const [altura, setAltura] = useState<number | null>(null);
  const [arrastre, setArrastre] = useState<{ dx: number; dy: number; x: number; y: number } | null>(null);

  const movil = ventana.w > 0 && ventana.w < 1024;
  const ancho = movil ? 148 : 336;
  const alto = movil ? 208 : 212;
  const margen = movil ? 12 : 24;
  // En el celular, encima de la barra de abajo (68 px más el área segura).
  const abajo = movil ? 68 + 16 : 24;
  const topPorDefecto = ventana.h - alto - abajo;
  const top = Math.min(Math.max(altura ?? topPorDefecto, margen + 56), topPorDefecto);
  const left = lado === "derecha" ? ventana.w - ancho - margen : margen;

  const caja: Caja = grande && rect ? rect : { left, top, width: ancho, height: alto };
  const posicion = arrastre ? { ...caja, left: arrastre.x, top: arrastre.y } : caja;

  const empezarArrastre = (e: PointerEventReact<HTMLDivElement>) => {
    if (grande) return;
    (e.target as HTMLElement).setPointerCapture?.(e.pointerId);
    setArrastre({ dx: e.clientX - caja.left, dy: e.clientY - caja.top, x: caja.left, y: caja.top });
  };
  const moverArrastre = (e: PointerEventReact<HTMLDivElement>) => {
    if (!arrastre) return;
    setArrastre({ ...arrastre, x: e.clientX - arrastre.dx, y: e.clientY - arrastre.dy });
  };
  const soltarArrastre = () => {
    if (!arrastre) return;
    // Se pega al lado más cercano, a la altura donde se soltó.
    setLado(arrastre.x + ancho / 2 < ventana.w / 2 ? "izquierda" : "derecha");
    setAltura(arrastre.y);
    setArrastre(null);
  };

  return (
    <div
      aria-hidden={!visible}
      className={`fixed z-40 overflow-hidden bg-preview-bg ${grande ? "" : "clase-flotante rounded-[20px]"} ${
        visible ? "" : "pointer-events-none invisible"
      }`}
      style={{
        left: posicion.left,
        top: posicion.top,
        width: posicion.width,
        height: posicion.height,
        // La animación de minimizar y agrandar: la misma caja, deslizándose entre dos sitios.
        transition: arrastre ? "none" : "left 420ms cubic-bezier(.2,.8,.2,1), top 420ms cubic-bezier(.2,.8,.2,1), width 420ms cubic-bezier(.2,.8,.2,1), height 420ms cubic-bezier(.2,.8,.2,1), border-radius 420ms",
      }}
    >
      {/* Jitsi dibuja aquí dentro. Este div no se desmonta mientras exista la app. */}
      <div ref={padreRef} className={`h-full w-full ${grande ? "" : "pointer-events-none"}`} />

      {!grande && llamada && visible && (
        <>
          {/* Toda la ventana chica lleva de vuelta al aula; la barra de arriba la arrastra. */}
          <button
            type="button"
            onClick={onVolver}
            aria-label="Volver a la clase"
            className="absolute inset-0 cursor-pointer bg-gradient-to-b from-night/55 via-transparent to-night/70 focus-visible:shadow-focus"
          />
          <div
            onPointerDown={empezarArrastre}
            onPointerMove={moverArrastre}
            onPointerUp={soltarArrastre}
            onPointerCancel={soltarArrastre}
            className="absolute inset-x-0 top-0 flex cursor-grab touch-none items-center gap-1.5 px-2.5 py-2 text-[11px] font-bold text-white active:cursor-grabbing"
          >
            <span className="relative flex h-2 w-2 shrink-0" aria-hidden>
              <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-primary opacity-75" />
              <span className="relative inline-flex h-2 w-2 rounded-full bg-primary" />
            </span>
            <span className="truncate">
              En clase{llamada.datos.counterpart ? ` con ${llamada.datos.counterpart.firstName}` : ""}
            </span>
            <Reloj desde={llamada.entro} />
          </div>
          <div className="absolute inset-x-0 bottom-0 flex items-center justify-center gap-1.5 p-2">
            <BotonFlotante etiqueta={llamada.micOn ? "Silenciar micrófono" : "Activar micrófono"} onClick={onMic} apagado={!llamada.micOn}>
              {llamada.micOn ? <Mic size={15} strokeWidth={2.2} /> : <MicOff size={15} strokeWidth={2.2} />}
            </BotonFlotante>
            <BotonFlotante etiqueta={llamada.camOn ? "Apagar cámara" : "Encender cámara"} onClick={onCam} apagado={!llamada.camOn}>
              {llamada.camOn ? <Video size={15} strokeWidth={2.2} /> : <VideoOff size={15} strokeWidth={2.2} />}
            </BotonFlotante>
            {!movil && (
              <BotonFlotante etiqueta="Volver a la clase" onClick={onVolver}>
                <Maximize2 size={15} strokeWidth={2.2} />
              </BotonFlotante>
            )}
            <BotonFlotante etiqueta="Colgar" onClick={onColgar} peligro>
              <PhoneOff size={15} strokeWidth={2.2} />
            </BotonFlotante>
          </div>
        </>
      )}
    </div>
  );
}

function BotonFlotante({
  etiqueta,
  onClick,
  apagado = false,
  peligro = false,
  children,
}: {
  etiqueta: string;
  onClick: () => void;
  apagado?: boolean;
  peligro?: boolean;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      aria-label={etiqueta}
      title={etiqueta}
      onClick={onClick}
      className={`relative grid h-8 w-8 place-items-center rounded-full text-white backdrop-blur transition-colors focus-visible:shadow-focus ${
        peligro ? "bg-error hover:bg-error/85" : apagado ? "bg-white/35 hover:bg-white/45" : "bg-white/15 hover:bg-white/25"
      }`}
    >
      {children}
    </button>
  );
}

/** «12:04»: cuánto va de clase, en la ventana chica. */
function Reloj({ desde }: { desde: number }) {
  const [ahora, setAhora] = useState(() => Date.now());
  useEffect(() => {
    const t = setInterval(() => setAhora(Date.now()), 1000);
    return () => clearInterval(t);
  }, []);
  const s = Math.max(0, Math.floor((ahora - desde) / 1000));
  return (
    <span className="ml-auto shrink-0 tabular-nums text-white/85">
      {Math.floor(s / 60)}:{String(s % 60).padStart(2, "0")}
    </span>
  );
}
