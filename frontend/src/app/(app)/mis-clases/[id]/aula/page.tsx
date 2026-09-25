"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, PictureInPicture2, ShieldCheck } from "lucide-react";
import { apiFetch, ApiError } from "@/lib/api/fetch";
import type { ClassroomResponse } from "@/lib/api/aula";
import { Cargando, ErrorCarga } from "@/components/estados";
import { Boton } from "@/components/ui";
import { Wordmark } from "@/components/marca";
import { Antesala } from "@/components/aula/Antesala";
import { useClaseEnCurso } from "@/components/aula/ClaseEnCurso";
import { HojaDeCierre } from "@/components/aula/HojaDeCierre";
import { HojaDeConexionCaida } from "@/components/aula/HojaDeConexionCaida";

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
 * cierre y conexión caída.
 *
 * <p>Desde el 24/09/2026 la videollamada no vive en esta página sino en `ClaseEnCurso`, en el
 * armazón de la app: esta página le presta su escenario. «Seguir en Orión» —o darle atrás, o ir a
 * otra sección— la encoge a una ventana flotante sin cortarla, y volver aquí la agranda.
 */
export default function AulaPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const { llamada, entrar, volverAEntrar, terminar, salir, cerrar, registrarEscenario, llego } = useClaseEnCurso();
  const miLlamada = llamada?.bookingId === id ? llamada : null;

  const [micOn, setMicOn] = useState(true);
  const [camOn, setCamOn] = useState(true);
  const [confirmandoSalida, setConfirmandoSalida] = useState(false);

  // Sin caché: el token caduca con la clase. Mientras se espera en la antesala se refresca cada
  // minuto, para que «cerrada» pase a «abierta» sola; en clase ya no hace falta.
  const aula = useQuery({
    queryKey: ["aula", id],
    queryFn: () => apiFetch<ClassroomResponse>(`/api/v1/bookings/${id}/classroom`),
    gcTime: 0,
    staleTime: 0,
    retry: false,
    enabled: !miLlamada,
    refetchInterval: 60_000,
  });

  const mensajeDelServidor = aula.error instanceof ApiError ? aula.error.message : null;
  const datos = miLlamada?.datos ?? aula.data;

  if (!miLlamada && aula.isLoading) return <Cargando />;

  if (!miLlamada && (aula.isError || !datos)) {
    return (
      <main className="mx-auto w-full max-w-lg px-5 py-10">
        <ErrorCarga
          mensaje={mensajeDelServidor ?? "No pudimos abrir la sala."}
          onReintentar={() => void aula.refetch()}
        />
        <Boton variante="contorno" className="mt-4" onClick={() => router.push("/mis-clases")}>
          <ArrowLeft size={16} strokeWidth={2.2} />
          Volver a mis clases
        </Boton>
      </main>
    );
  }

  if (!miLlamada || !datos) {
    return (
      <Antesala
        datos={datos!}
        micOn={micOn}
        camOn={camOn}
        onMic={setMicOn}
        onCam={setCamOn}
        onEntrar={() => entrar(id, datos!, { micOn, camOn })}
      />
    );
  }

  const volverAClases = () => router.push("/mis-clases");

  return (
    <div className="flex h-dvh flex-col bg-preview-bg">
      {/*
        La barra de Orión, siempre visible aunque la llamada esté a pantalla completa. Es lo único
        que dice de quién es esta sala: dentro del iframe manda Jitsi.
      */}
      <header className="shrink-0 text-[13px] text-white/90">
        <div className="flex items-center gap-1.5 px-3 py-2.5 sm:gap-2.5 sm:px-4">
          <Wordmark className="text-[15px] text-white" />
          <span className="hidden min-w-0 flex-1 truncate font-semibold sm:block">
            {datos.counterpart ? `Clase con ${datos.counterpart.firstName}` : "Tu clase"}
          </span>
          <span className="min-w-0 flex-1 sm:hidden" />
          <TiempoDeClase datos={datos} />
          {datos.moderator && (
            <span className="hidden items-center gap-1.5 rounded-full bg-white/10 px-2.5 py-1 text-[11.5px] font-bold sm:flex">
              <ShieldCheck size={13} strokeWidth={2.2} />
              Anfitrión
            </span>
          )}
          {/* Minimizar: la clase sigue en una ventana flotante mientras se usa el resto de Orión. */}
          <button
            type="button"
            onClick={volverAClases}
            title="La clase sigue en una ventana pequeña"
            aria-label="Minimizar: la clase sigue en una ventana pequeña"
            className="flex items-center gap-1.5 rounded-pill px-2 py-1 font-semibold hover:bg-white/10 focus-visible:shadow-focus sm:px-2.5"
          >
            <PictureInPicture2 size={16} strokeWidth={2.2} />
            <span className="hidden sm:inline">Seguir en Orión</span>
          </button>
          <button
            type="button"
            onClick={() => setConfirmandoSalida(true)}
            className="flex items-center gap-1.5 rounded-pill px-2.5 py-1 font-semibold hover:bg-white/10 focus-visible:shadow-focus"
          >
            <ArrowLeft size={15} strokeWidth={2.2} />
            Salir
          </button>
        </div>
        <BarraDeLaClase datos={datos} />
      </header>

      {/* El escenario: aquí se pone, encima, la videollamada del armazón. */}
      <div ref={registrarEscenario} className="min-h-0 flex-1" />

      {llego && miLlamada.fase === "dentro" && (
        <p
          role="status"
          className="aviso-llegada fixed left-1/2 top-16 z-50 flex items-center gap-2 rounded-pill bg-surface-raised px-4 py-2 text-[13.5px] font-bold text-text shadow-lg"
        >
          <span aria-hidden>✨</span>
          {llego} entró a la clase
        </p>
      )}

      {/*
        Salir pregunta: es la única puerta que cuelga. Minimizar, volver atrás o ir a otra sección
        dejan la clase en la ventana flotante.
      */}
      {confirmandoSalida && (
        <div className="fixed inset-0 z-50 grid place-items-center bg-night/60 px-6">
          <div className="w-full max-w-sm rounded-card bg-surface p-6 text-center">
            <p className="font-display text-[18px] font-bold text-text">¿Salir de la clase?</p>
            <p className="mt-2 text-[13.5px] leading-relaxed text-text-secondary">
              Te vas de la sala y {datos.counterpart?.firstName ?? "la otra persona"} deja de verte.
              Puedes volver a entrar mientras la clase siga abierta. Si solo quieres mirar algo de Orión,
              usa «Minimizar»: la clase sigue en una ventana pequeña.
            </p>
            <div className="mt-5 grid grid-cols-2 gap-2.5">
              <Boton variante="contorno" onClick={() => setConfirmandoSalida(false)}>
                Seguir en clase
              </Boton>
              <Boton
                variante="primario"
                onClick={() => {
                  salir();
                  volverAClases();
                }}
              >
                Salir
              </Boton>
            </div>
          </div>
        </div>
      )}

      {miLlamada.fase === "caida" && (
        <HojaDeConexionCaida
          datos={datos}
          minutos={miLlamada.minutos}
          onVolver={volverAEntrar}
          onTerminar={terminar}
        />
      )}

      {miLlamada.fase === "cierre" && (
        <HojaDeCierre
          datos={datos}
          bookingId={id}
          minutos={miLlamada.minutos}
          onCerrar={() => {
            cerrar();
            volverAClases();
          }}
        />
      )}
    </div>
  );
}

/** Cuánto falta, cada 15 s: «Quedan 23 min», «Empieza en 4 min», «Tiempo cumplido». */
function useReloj() {
  const [ahora, setAhora] = useState(() => Date.now());
  useEffect(() => {
    const t = setInterval(() => setAhora(Date.now()), 15_000);
    return () => clearInterval(t);
  }, []);
  return ahora;
}

function TiempoDeClase({ datos }: { datos: ClassroomResponse }) {
  const ahora = useReloj();
  const inicio = new Date(datos.startsAt).getTime();
  const fin = new Date(datos.endsAt).getTime();
  const min = (ms: number) => Math.max(1, Math.ceil(ms / 60000));
  const texto =
    ahora < inicio ? `Empieza en ${min(inicio - ahora)} min` : ahora < fin ? `Quedan ${min(fin - ahora)} min` : "Tiempo cumplido";
  const pocos = ahora >= inicio && fin - ahora <= 5 * 60000 && ahora < fin;
  return (
    <span
      className={`shrink-0 rounded-full px-2.5 py-1 text-[11.5px] font-bold tabular-nums ${
        pocos ? "bg-accent-peach text-night" : "bg-white/10"
      }`}
    >
      {texto}
    </span>
  );
}

/**
 * El tiempo de la clase como un amanecer que avanza: una línea que se llena de coral a durazno, con
 * una estrella en la punta. Los últimos cinco minutos, la estrella late.
 */
function BarraDeLaClase({ datos }: { datos: ClassroomResponse }) {
  const ahora = useReloj();
  const inicio = new Date(datos.startsAt).getTime();
  const fin = new Date(datos.endsAt).getTime();
  const avance = fin > inicio ? Math.min(1, Math.max(0, (ahora - inicio) / (fin - inicio))) : 0;
  const pocos = ahora >= inicio && fin - ahora <= 5 * 60000 && ahora < fin;
  return (
    <div className="relative h-1 w-full bg-white/10" aria-hidden>
      <div
        className="h-full bg-gradient-to-r from-primary to-accent-peach transition-[width] duration-[1500ms] ease-out"
        style={{ width: `${avance * 100}%` }}
      />
      {avance > 0 && avance < 1 && (
        <span
          className={`absolute top-1/2 -translate-x-1/2 -translate-y-1/2 text-[11px] leading-none text-accent-peach drop-shadow-[0_0_6px_rgba(255,193,137,.9)] ${
            pocos ? "animate-pulse" : ""
          }`}
          style={{ left: `${avance * 100}%` }}
        >
          ✦
        </span>
      )}
    </div>
  );
}
