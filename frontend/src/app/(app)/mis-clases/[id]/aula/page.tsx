"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, ShieldCheck } from "lucide-react";
import { apiFetch, ApiError } from "@/lib/api/fetch";
import { appIdDe, cargarJitsi, type ClassroomResponse } from "@/lib/api/aula";
import { Cargando, ErrorCarga } from "@/components/estados";
import { Boton } from "@/components/ui";
import { Wordmark } from "@/components/marca";
import { Antesala } from "@/components/aula/Antesala";
import { HojaDeCierre } from "@/components/aula/HojaDeCierre";
import { HojaDeConexionCaida } from "@/components/aula/HojaDeConexionCaida";

type Fase = "antesala" | "dentro" | "caida" | "cierre";

/**
 * El aula: la clase ocurre DENTRO de Orión.
 *
 * <p>Antes el enlace abría {@code meet.jit.si} en otra pestaña, y eso tenía dos problemas. La
 * persona salía de la plataforma, y —el grave— en una sala pública manda quien entra primero: un
 * estudiante que llegara antes podía silenciar o expulsar a su propio profesor. Ahora Orión firma un
 * token por participante y decide quién modera.
 *
 * <p><strong>Lo de dentro del iframe no es nuestro.</strong> Los mosaicos, la barra de controles y
 * el modo mosaico los dibuja Jitsi. De Orión son el marco, los estados y las puertas: antesala,
 * cierre y conexión caída. Se ocultan los botones que no tienen sentido en una clase de dos
 * personas y nada más — pelearse por CSS con la interfaz de Jitsi es una carrera que se pierde en
 * su siguiente versión.
 *
 * <p>Las cuatro fases viven aquí y no en la ruta: al colgar, la hoja sube <em>sobre</em> el iframe
 * sin recargar, que es lo que pide el diseño y lo que permite volver a entrar si lo que pasó fue
 * una caída y no un final.
 */
export default function AulaPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const contenedor = useRef<HTMLDivElement>(null);
  const api = useRef<{
    dispose: () => void;
    executeCommand: (comando: string) => void;
  } | null>(null);
  const entroAlAula = useRef<number | null>(null);
  // `montar` no puede depender de la respuesta ni de la fase sin volver a montar en cada cambio.
  const datosRef = useRef<ClassroomResponse | null>(null);
  const faseRef = useRef<Fase>("antesala");

  const [fase, setFase] = useState<Fase>("antesala");
  const [minutos, setMinutos] = useState(0);
  const [micOn, setMicOn] = useState(true);
  const [camOn, setCamOn] = useState(true);
  const [fallo, setFallo] = useState<string | null>(null);
  const [confirmandoSalida, setConfirmandoSalida] = useState(false);

  // Sin caché: el token caduca con la clase. Se refresca cada minuto para que la antesala pase de
  // «cerrada» a «abierta» sola, sin que nadie tenga que recargar mirando el reloj.
  const aula = useQuery({
    queryKey: ["aula", id],
    queryFn: () => apiFetch<ClassroomResponse>(`/api/v1/bookings/${id}/classroom`),
    gcTime: 0,
    staleTime: 0,
    retry: false,
    // Solo mientras se espera en la antesala: es donde el reloj tiene que avanzar solo, para que
    // «cerrada» pase a «abierta» sin recargar. Dentro de la sala no se sondea — cada respuesta
    // nueva recreaba la función de montaje y acababa levantando un segundo Jitsi encima del
    // primero. Dos frames, y el duplicado expulsaba al original de la sala.
    refetchInterval: () => (faseRef.current === "antesala" ? 60_000 : false),
  });

  // Refleja en refs lo último renderizado. Va declarado ANTES del efecto que monta Jitsi: los
  // efectos corren en orden, y si este fuera después, el montaje leería refs de la vuelta anterior.
  useEffect(() => {
    datosRef.current = aula.data ?? null;
    faseRef.current = fase;
  });

  const minutosDentro = () =>
    entroAlAula.current ? Math.max(1, Math.round((Date.now() - entroAlAula.current) / 60000)) : 0;

  /**
   * Salir de la sala de verdad.
   *
   * <p>`dispose()` solo quita el iframe. La conferencia no se entera de que te fuiste, así que tu
   * participante se queda dentro hasta que el servidor lo da por perdido — y quien siga en la clase
   * ve un fantasma tuyo junto a ti cuando vuelves a entrar. Con dos personas eso parecen tres.
   *
   * <p>Colgar primero es lo que envía la salida. El `try` no es adorno: si la conexión ya se cayó,
   * `executeCommand` lanza, y entonces lo que no puede fallar es el `dispose` de después.
   */
  const soltar = useCallback(() => {
    try {
      api.current?.executeCommand("hangup");
    } catch {
      // La sesión ya no estaba viva. Da igual: lo que importa es que el iframe se vaya.
    }
    api.current?.dispose();
    api.current = null;
  }, []);

  const montar = useCallback(() => {
    const datos = datosRef.current;
    // Ya hay una instancia: no se monta otra. Es la barrera final contra el frame duplicado, por si
    // algún día otra dependencia vuelve a disparar el efecto.
    if (api.current || !datos?.token || !datos.domain || !datos.room || !contenedor.current) return;

    cargarJitsi(datos.domain, appIdDe(datos.room))
      .then(() => {
        if (!contenedor.current) return;
        const Constructor = (window as unknown as {
          JitsiMeetExternalAPI: new (domain: string, opciones: Record<string, unknown>) => {
            dispose: () => void;
            executeCommand: (comando: string) => void;
            addListener: (evento: string, fn: () => void) => void;
          };
        }).JitsiMeetExternalAPI;

        const instancia: {
          dispose: () => void;
          executeCommand: (comando: string) => void;
          addListener: (evento: string, fn: () => void) => void;
        } = new Constructor(datos.domain!, {
          roomName: datos.room,
          jwt: datos.token,
          parentNode: contenedor.current,
          userInfo: { displayName: datos.displayName },
          configOverwrite: {
            // La antesala de Orión reemplaza la de Jitsi: pasar por dos es pasar por una de más.
            prejoinPageEnabled: false,
            disableInviteFunctions: true,
            startWithAudioMuted: !micOn,
            startWithVideoMuted: !camOn,
          },
          interfaceConfigOverwrite: {
            TOOLBAR_BUTTONS: [
              "microphone", "camera", "desktop", "fullscreen", "hangup",
              "chat", "raisehand", "tileview", "settings", "videoquality",
            ],
            // La marca de Orión dentro del área de vídeo: es el único sitio de la llamada que
            // controlamos y que ve también la otra persona. La de Jitsi se apaga.
            SHOW_JITSI_WATERMARK: false,
            SHOW_BRAND_WATERMARK: true,
            BRAND_WATERMARK_LINK: "",
            DEFAULT_LOGO_URL: "/marca-orion.svg",
            DEFAULT_WELCOME_PAGE_LOGO_URL: "/marca-orion.svg",
            MOBILE_APP_PROMO: false,
          },
        });

        entroAlAula.current = Date.now();

        // Colgar es terminar: sube el cierre.
        instancia.addListener("readyToClose", () => {
          setMinutos(minutosDentro());
          soltar();
          setFase("cierre");
        });

        // Perder la conexión NO es terminar. Es la distinción entera de la hoja de caída: a quien
        // se queda sin internet le quedan minutos pagados, y decirle «¿cómo te fue?» es mentirle.
        const caida = () => {
          setMinutos(minutosDentro());
          setFase("caida");
        };
        instancia.addListener("connectionFailed", caida);
        instancia.addListener("suspendDetected", caida);

        api.current = instancia;
      })
      .catch((error: Error) => setFallo(error.message));
    // Sin dependencias reactivas a propósito: lo que necesita se lee de refs. Poner `aula.data`
    // aquí es lo que montaba un Jitsi nuevo en cada refresco de la consulta.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Entrar monta; salir destruye. La limpieza no puede preguntar por `fase`, porque en el cierre
  // del efecto `fase` todavía vale lo que valía cuando se montó.
  useEffect(() => {
    if (fase !== "dentro") {
      soltar();
      return;
    }
    montar();
  }, [fase, montar, soltar]);

  useEffect(() => () => soltar(), [soltar]);

  const mensajeDelServidor = aula.error instanceof ApiError ? aula.error.message : null;

  if (aula.isLoading) return <Cargando />;

  if (aula.isError || fallo || !aula.data) {
    return (
      <main className="mx-auto w-full max-w-lg px-5 py-10">
        <ErrorCarga
          mensaje={mensajeDelServidor ?? fallo ?? "No pudimos abrir la sala."}
          onReintentar={() => {
            setFallo(null);
            void aula.refetch();
          }}
        />
        <Boton variante="contorno" className="mt-4" onClick={() => router.push("/mis-clases")}>
          <ArrowLeft size={16} strokeWidth={2.2} />
          Volver a mis clases
        </Boton>
      </main>
    );
  }

  const datos = aula.data;
  const volverAClases = () => router.push("/mis-clases");

  if (fase === "antesala") {
    return (
      <Antesala
        datos={datos}
        micOn={micOn}
        camOn={camOn}
        onMic={setMicOn}
        onCam={setCamOn}
        onEntrar={() => setFase("dentro")}
      />
    );
  }

  return (
    <div className="flex h-dvh flex-col bg-preview-bg">
      {/*
        La barra de Orión, siempre visible aunque la llamada esté a pantalla completa. Es lo único
        que dice de quién es esta sala: dentro del iframe manda Jitsi, y sin esto la clase parecía
        ocurrir en cualquier sitio menos aquí.
      */}
      <header className="flex shrink-0 items-center gap-3 px-4 py-2.5 text-[13px] text-white/90">
        <Wordmark className="text-[15px] text-white" />
        <span className="hidden min-w-0 flex-1 truncate font-semibold sm:block">
          {datos.counterpart ? `Clase con ${datos.counterpart.firstName}` : "Tu clase"}
        </span>
        <span className="min-w-0 flex-1 sm:hidden" />
        {datos.moderator && (
          <span className="flex items-center gap-1.5 rounded-full bg-white/10 px-2.5 py-1 text-[11.5px] font-bold">
            <ShieldCheck size={13} strokeWidth={2.2} />
            Anfitrión
          </span>
        )}
        <button
          type="button"
          onClick={() => setConfirmandoSalida(true)}
          className="flex items-center gap-1.5 rounded-pill px-2.5 py-1 font-semibold hover:bg-white/10 focus-visible:shadow-focus"
        >
          <ArrowLeft size={15} strokeWidth={2.2} />
          Salir
        </button>
      </header>

      {/* Jitsi dibuja aquí dentro. El iframe se crea y se destruye con la instancia. */}
      <div ref={contenedor} className="min-h-0 flex-1" />

      {/*
        Salir pregunta. Es la otra mitad del arreglo del fantasma: antes se salía navegando por el
        menú, sin colgar y sin darse cuenta; ahora hay una sola puerta y avisa de lo que hace.
      */}
      {confirmandoSalida && (
        <div className="fixed inset-0 z-50 grid place-items-center bg-night/60 px-6">
          <div className="w-full max-w-sm rounded-card bg-surface p-6 text-center">
            <p className="font-display text-[18px] font-bold text-text">¿Salir de la clase?</p>
            <p className="mt-2 text-[13.5px] leading-relaxed text-text-secondary">
              Te vas de la sala y {datos.counterpart?.firstName ?? "la otra persona"} deja de verte.
              Puedes volver a entrar mientras la clase siga abierta.
            </p>
            <div className="mt-5 grid grid-cols-2 gap-2.5">
              <Boton variante="contorno" onClick={() => setConfirmandoSalida(false)}>
                Seguir en clase
              </Boton>
              <Boton
                variante="primario"
                onClick={() => {
                  soltar();
                  volverAClases();
                }}
              >
                Salir
              </Boton>
            </div>
          </div>
        </div>
      )}

      {fase === "caida" && (
        <HojaDeConexionCaida
          datos={datos}
          minutos={minutos}
          onVolver={() => setFase("dentro")}
          onTerminar={() => {
            soltar();
            setFase("cierre");
          }}
        />
      )}

      {fase === "cierre" && (
        <HojaDeCierre
          datos={datos}
          bookingId={id}
          minutos={minutos}
          onCerrar={volverAClases}
        />
      )}
    </div>
  );
}
