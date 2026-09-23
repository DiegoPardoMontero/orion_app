import { describe, expect, it } from "vitest";
import { ConversacionDeVoz, type ConversacionCallbacks } from "./diagnostico";

/**
 * El cliente de voz, alimentado con la secuencia de eventos que manda OpenAI por WebRTC. Ahí el
 * audio va por la pista de medios y `response.output_audio.delta` no llega nunca: el cliente tiene
 * que funcionar solo con lo que sí viaja por el canal de datos.
 */
function cliente() {
  const subtitulos: string[] = [];
  const turnosIniciados: number[] = [];
  const turnosTerminados: { cuantos: number; preguntaba: boolean }[] = [];
  const cb: ConversacionCallbacks = {
    onTurno: () => undefined,
    onFase: () => undefined,
    onSubtitulo: (t) => subtitulos.push(t),
    onEmpiezaTurnoDeMeissa: (n) => turnosIniciados.push(n),
    onTurnoDeMeissa: (cuantos, preguntaba) => turnosTerminados.push({ cuantos, preguntaba }),
    onError: () => undefined,
  };
  const enviados: object[] = [];
  const voz = new ConversacionDeVoz(cb, (m) => enviados.push(m));
  return { voz, subtitulos, turnosIniciados, turnosTerminados, enviados };
}

/** Un turno completo de Meissa como llega por WebRTC: sin un solo delta de audio. */
function turnoDeMeissa(voz: ConversacionDeVoz, texto: string) {
  voz.recibir({ type: "response.created" });
  voz.recibir({ type: "output_audio_buffer.started" });
  for (const palabra of texto.split(/(?<= )/)) {
    voz.recibir({ type: "response.output_audio_transcript.delta", delta: palabra });
  }
  voz.recibir({ type: "response.output_audio_transcript.done", transcript: texto });
  voz.recibir({ type: "response.done" });
  voz.recibir({ type: "output_audio_buffer.stopped" });
}

describe("ConversacionDeVoz por WebRTC", () => {
  it("el subtítulo de un turno nuevo empieza vacío: nunca arrastra la conversación anterior", () => {
    const { voz, subtitulos } = cliente();
    turnoDeMeissa(voz, "Hi Ana! How's your day going?");
    subtitulos.length = 0;

    voz.recibir({ type: "response.created" });
    voz.recibir({ type: "response.output_audio_transcript.delta", delta: "Logistics, " });

    expect(subtitulos).toEqual(["", "Logistics, "]);
  });

  it("el contador avanza con cada turno de Meissa y sabe si terminó preguntando", () => {
    const { voz, turnosIniciados, turnosTerminados } = cliente();

    turnoDeMeissa(voz, "Hi Ana! How's your day going?");
    turnoDeMeissa(voz, "Thanks for the chat, Ana. Your results are on the screen now.");

    expect(turnosIniciados).toEqual([1, 2]);
    expect(turnosTerminados).toEqual([
      { cuantos: 1, preguntaba: true },
      { cuantos: 2, preguntaba: false },
    ]);
  });

  it("si la persona la interrumpe, ese turno también cuenta como terminado", () => {
    const { voz, turnosTerminados } = cliente();
    voz.recibir({ type: "response.created" });
    voz.recibir({ type: "output_audio_buffer.started" });
    voz.recibir({ type: "output_audio_buffer.cleared" });

    expect(turnosTerminados).toHaveLength(1);
  });
});
