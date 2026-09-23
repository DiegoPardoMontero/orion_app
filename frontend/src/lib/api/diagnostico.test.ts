import { afterEach, describe, expect, it, vi } from "vitest";
import { ConversacionDeVoz, traduccionVisible, type ConversacionCallbacks } from "./diagnostico";

/**
 * El cliente de voz, alimentado con la secuencia de eventos que manda OpenAI por WebRTC. Ahí el
 * audio va por la pista de medios y `response.output_audio.delta` no llega nunca: el cliente tiene
 * que funcionar solo con lo que sí viaja por el canal de datos.
 */
function cliente() {
  const errores: string[] = [];
  const subtitulos: string[] = [];
  const turnosIniciados: number[] = [];
  const turnosTerminados: { cuantos: number; preguntaba: boolean }[] = [];
  const frases: { frase: string; turno: number; indice: number }[] = [];
  const cb: ConversacionCallbacks = {
    onTurno: () => undefined,
    onFase: () => undefined,
    onSubtitulo: (t) => subtitulos.push(t),
    onEmpiezaTurnoDeMeissa: (n) => turnosIniciados.push(n),
    onTurnoDeMeissa: (cuantos, preguntaba) => turnosTerminados.push({ cuantos, preguntaba }),
    onFrase: (frase, turno, indice) => frases.push({ frase, turno, indice }),
    onError: (m) => errores.push(m),
  };
  const enviados: object[] = [];
  const voz = new ConversacionDeVoz(cb, (m) => enviados.push(m));
  return { voz, subtitulos, turnosIniciados, turnosTerminados, frases, enviados, errores };
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

  it("suelta cada frase apenas se cierra, y la última al terminar el turno", () => {
    const { voz, frases } = cliente();

    turnoDeMeissa(voz, "Logistics, so you're the one everyone calls. What broke this week? Tell me");

    expect(frases).toEqual([
      { frase: "Logistics, so you're the one everyone calls.", turno: 1, indice: 0 },
      { frase: "What broke this week?", turno: 1, indice: 1 },
      { frase: "Tell me", turno: 1, indice: 2 },
    ]);
  });

  it("un número con punto no corta la frase, y cada turno numera sus frases desde cero", () => {
    const { voz, frases } = cliente();

    turnoDeMeissa(voz, "Hi! Classes are 55 minutes.");
    turnoDeMeissa(voz, "Version 2.5 of your app? Tell me more.");

    expect(frases.map((f) => [f.turno, f.indice, f.frase])).toEqual([
      [1, 0, "Hi!"],
      [1, 1, "Classes are 55 minutes."],
      [2, 0, "Version 2.5 of your app?"],
      [2, 1, "Tell me more."],
    ]);
  });

  it("una nota va a la conversación como mensaje de sistema, sin hacerla hablar", () => {
    const { voz, enviados } = cliente();

    voz.nota("[20 seconds left]");

    expect(enviados).toEqual([
      {
        type: "conversation.item.create",
        item: { type: "message", role: "system", content: [{ type: "input_text", text: "[20 seconds left]" }] },
      },
    ]);
  });

  it("al acabarse el tiempo pide la despedida ya si nadie habla, pero no corta a la persona", () => {
    const enSilencio = cliente();
    enSilencio.voz.pedirDespedida("[Time is up]");
    expect(enSilencio.enviados.map((m) => (m as { type: string }).type)).toEqual([
      "conversation.item.create",
      "response.create",
    ]);

    const hablando = cliente();
    hablando.voz.recibir({ type: "input_audio_buffer.speech_started" });
    hablando.voz.pedirDespedida("[Time is up]");
    expect(hablando.enviados.map((m) => (m as { type: string }).type)).toEqual(["conversation.item.create"]);

    const meissaHabla = cliente();
    meissaHabla.voz.recibir({ type: "response.created" });
    meissaHabla.voz.pedirDespedida("[Time is up]");
    expect(meissaHabla.enviados.map((m) => (m as { type: string }).type)).toEqual(["conversation.item.create"]);
  });
});

describe("una respuesta que falla (el límite de tokens por minuto)", () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  const fallida = { type: "response.done", response: { status: "failed" } };
  const pedidos = (enviados: object[]) => enviados.filter((m) => (m as { type: string }).type === "response.create");

  it("se vuelve a pedir tras una pausa, y el turno que no sonó no cuenta", () => {
    vi.useFakeTimers();
    const { voz, enviados, turnosIniciados } = cliente();

    voz.recibir({ type: "response.created" });
    voz.recibir(fallida);
    expect(pedidos(enviados)).toHaveLength(0);
    vi.advanceTimersByTime(2000);
    expect(pedidos(enviados)).toHaveLength(1);

    voz.recibir({ type: "response.created" });
    expect(turnosIniciados).toEqual([1, 1]);
  });

  it("si la persona está hablando no se pide nada: su silencio ya pedirá el turno", () => {
    vi.useFakeTimers();
    const { voz, enviados } = cliente();

    voz.recibir({ type: "response.created" });
    voz.recibir(fallida);
    voz.recibir({ type: "input_audio_buffer.speech_started" });
    vi.advanceTimersByTime(10_000);

    expect(pedidos(enviados)).toHaveLength(0);
  });

  it("si la persona habla y Meissa le responde antes de la pausa, el reintento ya no sale", () => {
    vi.useFakeTimers();
    const { voz, enviados } = cliente();

    voz.recibir({ type: "response.created" });
    voz.recibir(fallida);
    vi.advanceTimersByTime(1000);
    voz.recibir({ type: "input_audio_buffer.speech_started" });
    voz.recibir({ type: "input_audio_buffer.speech_stopped" });
    turnoDeMeissa(voz, "Sorry, go on. What happened next?");
    vi.advanceTimersByTime(10_000);

    expect(pedidos(enviados)).toHaveLength(0);
  });

  it("una respuesta nueva cancela el reintento pendiente", () => {
    vi.useFakeTimers();
    const { voz, enviados } = cliente();

    voz.recibir({ type: "response.created" });
    voz.recibir(fallida);
    voz.recibir({ type: "response.created" });
    vi.advanceTimersByTime(10_000);

    expect(pedidos(enviados)).toHaveLength(0);
  });

  it("a la tercera se avisa, en vez de dejarla callada", () => {
    vi.useFakeTimers();
    const { voz, enviados, errores } = cliente();

    for (let intento = 0; intento < 4; intento++) {
      voz.recibir({ type: "response.created" });
      voz.recibir(fallida);
      vi.advanceTimersByTime(8000);
    }

    expect(pedidos(enviados)).toHaveLength(3);
    expect(errores).toHaveLength(1);
  });

  it("una respuesta interrumpida (cancelled) no se repite", () => {
    vi.useFakeTimers();
    const { voz, enviados } = cliente();

    voz.recibir({ type: "response.created" });
    voz.recibir({ type: "response.done", response: { status: "cancelled" } });
    vi.advanceTimersByTime(10_000);

    expect(pedidos(enviados)).toHaveLength(0);
  });
});

describe("traduccionVisible", () => {
  it("muestra las frases en orden y se detiene en la primera que todavía no llega", () => {
    expect(traduccionVisible(["Hola.", undefined, "¿Y tú?"])).toBe("Hola.");
    expect(traduccionVisible(["Hola.", "¿Cómo vas?"])).toBe("Hola. ¿Cómo vas?");
  });

  it("salta las que no tienen traducción (ya eran español o no llegaron a tiempo)", () => {
    expect(traduccionVisible([null, "¿Dónde estás?"])).toBe("¿Dónde estás?");
    expect(traduccionVisible([])).toBe("");
  });
});
