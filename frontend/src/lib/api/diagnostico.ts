/** Lo que devuelve `POST /api/v1/assessments`: con qué conectarse, y nada más. */
export type DiagnosticoIniciado = {
  assessmentId: string;
  languageCode: string;
  clientSecret: string;
  model: string;
  expiresAt: string;
};

export type Diagnostico = {
  id: string;
  languageCode: string;
  sequence: number;
  status: "IN_PROGRESS" | "COMPLETED" | "ABANDONED" | "FAILED";
  mode: "STANDARD" | "FROM_ZERO";
  score: number | null;
  /** El nombre del tramo («Ya te defiendes»). Hay etiqueta aunque no haya número. */
  label: string;
  scoreVersion: string | null;
  signals: Record<string, number>;
  summary: string | null;
  observations: string[];
  durationSeconds: number | null;
  turnCount: number | null;
  startedAt: string;
  completedAt: string | null;
  recommendations: {
    professorId: string;
    position: number;
    reasonCode: string;
    reasonText: string;
    booked: boolean;
  }[];
};

/** Un turno tal como lo empuja el cliente. Las señales las deduce el servidor, no esto. */
export type TurnoMedido = {
  turnIndex: number;
  speaker: "AI" | "USER";
  transcript: string | null;
  latencyMs: number | null;
  durationMs: number | null;
};

/**
 * La conversación por voz, contra el proveedor y sin pasar por Orión.
 *
 * <p>El audio va del navegador al proveedor directo: lo que no atraviesa nuestros servidores no lo
 * podemos filtrar, y además ahorra el ancho de banda de cada minuto hablado. Orión solo emitió la
 * credencial efímera con la que esto se conecta.
 *
 * <p>Lo que sí mide este cliente es lo único que solo él puede saber: cuánto tarda la persona en
 * abrir la boca después de que la IA calla, y cuánto habla. Todo lo demás —muletillas, frases
 * abandonadas, cambio de idioma— lo deduce el servidor del texto, porque un puntaje que dependiera
 * de números enviados desde aquí no sería reproducible.
 */
/**
 * La fase en la que está Meissa, con la máquina de estados del handoff: habla mientras suena su
 * voz, escucha mientras el micrófono está abierto para ti, y piensa entre que terminas y ella
 * empieza (1–3 s de latencia del proveedor).
 */
export type FaseDeMeissa = "habla" | "escucha" | "piensa";

export type ConversacionCallbacks = {
  onTurno: (turno: TurnoMedido) => void;
  onFase: (fase: FaseDeMeissa) => void;
  /** Lo que Meissa va diciendo, acumulado mientras habla: es lo que se lee (no hay lip-sync). */
  onSubtitulo: (texto: string) => void;
  /** Un turno de Meissa terminó de sonar. Lleva cuántos van y si terminó en pregunta. */
  onTurnoDeMeissa: (cuantos: number, preguntaba: boolean) => void;
  onError: (mensaje: string) => void;
};

export class ConversacionDeVoz {
  private pc: RTCPeerConnection | null = null;
  private micro: MediaStream | null = null;
  private canal: RTCDataChannel | null = null;
  private audio: HTMLAudioElement | null = null;

  private finDeLaIA: number | null = null;
  private inicioDelUsuario: number | null = null;
  private finDelUsuario: number | null = null;
  private indice = 0;
  private subtitulo = "";
  private hablando = false;
  private turnosDeMeissa = 0;
  private ultimoDeMeissa = "";

  constructor(private readonly cb: ConversacionCallbacks) {}

  async conectar(clientSecret: string, model: string) {
    this.micro = await navigator.mediaDevices.getUserMedia({ audio: true });

    this.pc = new RTCPeerConnection();
    this.audio = new Audio();
    this.audio.autoplay = true;
    this.pc.ontrack = (e) => {
      if (this.audio) this.audio.srcObject = e.streams[0];
    };
    this.pc.addTrack(this.micro.getAudioTracks()[0], this.micro);

    this.canal = this.pc.createDataChannel("oai-events");
    this.canal.onmessage = (e) => this.manejar(JSON.parse(e.data));

    const oferta = await this.pc.createOffer();
    await this.pc.setLocalDescription(oferta);

    const resp = await fetch(`https://api.openai.com/v1/realtime/calls?model=${model}`, {
      method: "POST",
      body: oferta.sdp,
      headers: { Authorization: `Bearer ${clientSecret}`, "Content-Type": "application/sdp" },
    });
    if (!resp.ok) throw new Error("No pudimos conectar la conversación.");
    await this.pc.setRemoteDescription({ type: "answer", sdp: await resp.text() });

    // La IA saluda primero: el modelo no arranca solo.
    this.canal.onopen = () => this.canal?.send(JSON.stringify({ type: "response.create" }));
  }

  colgar() {
    this.pc?.close();
    this.micro?.getTracks().forEach((t) => t.stop());
    this.pc = null;
    this.micro = null;
    this.canal = null;
  }

  private manejar(ev: { type: string; transcript?: string; delta?: string; error?: unknown }) {
    switch (ev.type) {
      case "response.done":
        this.finDeLaIA = performance.now();
        break;

      // Terminó de SONAR, que no es lo mismo que terminar de generarse: hasta aquí el micrófono
      // no es tuyo todavía.
      case "output_audio_buffer.stopped":
        this.finDeLaIA = performance.now();
        if (this.hablando) {
          this.hablando = false;
          this.turnosDeMeissa++;
          this.cb.onTurnoDeMeissa(this.turnosDeMeissa, this.ultimoDeMeissa.trim().endsWith("?"));
        }
        this.cb.onFase("escucha");
        break;

      case "input_audio_buffer.speech_started":
        this.inicioDelUsuario = performance.now();
        this.cb.onFase("escucha");
        break;

      case "input_audio_buffer.speech_stopped":
        this.finDelUsuario = performance.now();
        this.cb.onFase("piensa");
        break;

      case "response.output_audio.delta":
        if (!this.hablando) {
          this.hablando = true;
          this.subtitulo = "";
          this.cb.onSubtitulo("");
        }
        this.cb.onFase("habla");
        break;

      case "response.output_audio_transcript.delta":
        this.subtitulo += ev.delta ?? "";
        this.cb.onSubtitulo(this.subtitulo);
        break;

      case "conversation.item.input_audio_transcription.completed": {
        const texto = (ev.transcript ?? "").trim();
        // Los turnos vacíos no se mandan: el detector a veces toma un silencio por habla, y un
        // turno de cero palabras entraría al cálculo como una respuesta cortísima que nunca ocurrió.
        if (!texto) break;
        this.cb.onTurno({
          turnIndex: this.indice++,
          speaker: "USER",
          transcript: texto,
          latencyMs:
            this.finDeLaIA && this.inicioDelUsuario
              ? Math.round(this.inicioDelUsuario - this.finDeLaIA)
              : null,
          durationMs:
            this.inicioDelUsuario && this.finDelUsuario
              ? Math.round(this.finDelUsuario - this.inicioDelUsuario)
              : null,
        });
        break;
      }

      case "response.output_audio_transcript.done":
      case "response.audio_transcript.done": {
        const texto = (ev.transcript ?? "").trim();
        if (!texto) break;
        this.ultimoDeMeissa = texto;
        this.cb.onSubtitulo(texto);
        this.cb.onTurno({
          turnIndex: this.indice++,
          speaker: "AI",
          transcript: texto,
          latencyMs: null,
          durationMs: null,
        });
        break;
      }

      case "error":
        this.cb.onError("Se interrumpió la conversación.");
        break;
    }
  }
}
