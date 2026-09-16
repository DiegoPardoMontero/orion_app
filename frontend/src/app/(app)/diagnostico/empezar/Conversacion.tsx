"use client";

import { useEffect, useRef, useState } from "react";
import { Mic, PhoneOff } from "lucide-react";
import { apiFetch } from "@/lib/api/fetch";
import { ConversacionDeVoz, type DiagnosticoIniciado, type TurnoMedido } from "@/lib/api/diagnostico";
import { Boton } from "@/components/ui";

/**
 * La conversación. Casi sin interfaz, y eso es el diseño.
 *
 * <p><strong>Sin transcripción en pantalla.</strong> Leer mientras se conversa cambia por completo
 * el comportamiento —la gente se corrige al verse escrita— y arruina justo lo que se está midiendo.
 * Lo único que hay es el minuto, quién habla y cómo salir.
 *
 * <p>La onda no es decoración: es la única señal de que el micrófono está captando. Durazno cuando
 * habla la IA, lavanda cuando hablas tú. Con `prefers-reduced-motion` se queda quieta y el estado
 * se lee por el texto, que está ahí en los dos casos.
 */
export function Conversacion({
  sesion,
  minutos,
  onTerminar,
}: {
  sesion: DiagnosticoIniciado;
  minutos: number;
  onTerminar: () => void;
}) {
  const voz = useRef<ConversacionDeVoz | null>(null);
  const [quienHabla, setQuienHabla] = useState<"AI" | "USER" | null>(null);
  const [segundos, setSegundos] = useState(0);
  const [fallo, setFallo] = useState<string | null>(null);
  const cerrando = useRef(false);

  // Los turnos se empujan según llegan y no al final: si la persona cierra la pestaña a mitad, lo
  // que ya dijo está guardado y la conversación se puede cerrar igual.
  useEffect(() => {
    const cliente = new ConversacionDeVoz({
      onHabla: setQuienHabla,
      onError: setFallo,
      onTurno: (turno: TurnoMedido) => {
        void apiFetch(`/api/v1/assessments/${sesion.assessmentId}/turns`, {
          method: "POST",
          body: turno,
        }).catch(() => undefined);
      },
    });
    voz.current = cliente;

    cliente
      .conectar(sesion.clientSecret, sesion.model)
      .catch((e: Error) => setFallo(e.message));

    return () => cliente.colgar();
  }, [sesion]);

  useEffect(() => {
    const id = setInterval(() => setSegundos((s) => s + 1), 1000);
    return () => clearInterval(id);
  }, []);

  // El corte es duro y lo aplica también el cliente: quedarse esperando a que el proveedor corte
  // deja a la persona hablando sola.
  useEffect(() => {
    if (segundos >= minutos * 60 && !cerrando.current) {
      cerrando.current = true;
      voz.current?.colgar();
      onTerminar();
    }
  }, [segundos, minutos, onTerminar]);

  const restante = Math.max(0, minutos * 60 - segundos);
  const mm = String(Math.floor(restante / 60)).padStart(2, "0");
  const ss = String(restante % 60).padStart(2, "0");

  const colgar = () => {
    if (cerrando.current) return;
    cerrando.current = true;
    voz.current?.colgar();
    onTerminar();
  };

  return (
    <main className="flex min-h-dvh flex-col items-center justify-center bg-[linear-gradient(170deg,#2E1E4E_0%,#4A2E63_60%,#7A4A8C_100%)] px-6 py-10 text-text-on-night">
      <p className="font-display text-[15px] font-bold tabular-nums text-text-on-night/70">
        {mm}:{ss}
      </p>

      <div className="relative mt-10 grid h-[180px] w-[180px] place-items-center">
        {/* Tres anillos que respiran. El color dice quién tiene la palabra. */}
        {[0, 1, 2].map((i) => (
          <span
            key={i}
            aria-hidden="true"
            className="absolute rounded-full border-2"
            style={{
              width: `${90 + i * 42}px`,
              height: `${90 + i * 42}px`,
              borderColor: quienHabla === "USER" ? "#B9A7E6" : "#FFC189",
              opacity: quienHabla ? 0.55 - i * 0.15 : 0.18,
              animation: quienHabla
                ? `breathe ${2400 + i * 400}ms ease-in-out infinite`
                : undefined,
              transition: "opacity 300ms, border-color 300ms",
            }}
          />
        ))}
        <Mic size={34} strokeWidth={1.75} className="relative text-text-on-night" />
      </div>

      <p aria-live="polite" className="mt-9 text-[15px] font-semibold">
        {fallo
          ? fallo
          : quienHabla === "USER"
            ? "Te escuchamos."
            : quienHabla === "AI"
              ? "Meissa está hablando."
              : "Cuando quieras."}
      </p>

      {restante <= 60 && restante > 0 && (
        <p className="mt-2 text-[13px] text-text-on-night/70">Nos queda poco tiempo.</p>
      )}

      <Boton variante="contorno" className="mt-10 border-text-on-night/40 text-text-on-night" onClick={colgar}>
        <PhoneOff size={16} strokeWidth={2.2} />
        Terminar
      </Boton>
    </main>
  );
}
