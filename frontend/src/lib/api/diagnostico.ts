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
  /** Lo que Meissa va diciendo en ESTE turno, acumulado: es lo que se lee (no hay lip-sync). */
  onSubtitulo: (texto: string) => void;
  /** Empezó un turno de Meissa: el número de turno, contando el saludo como el primero. */
  onEmpiezaTurnoDeMeissa?: (numero: number) => void;
  /**
   * Meissa terminó de decir una frase entera: es lo que se traduce. Llega apenas la frase se
   * cierra en el texto —que va por delante de la voz—, no al final del turno.
   */
  onFrase?: (frase: string, turno: number, indice: number) => void;
  /** Un turno de Meissa terminó de sonar. Lleva cuántos van y si terminó en pregunta. */
  onTurnoDeMeissa: (cuantos: number, preguntaba: boolean) => void;
  onError: (mensaje: string) => void;
};

/** Un evento del proveedor, con solo lo que este cliente lee de él. */
export type EventoDeVoz = { type: string; transcript?: string; delta?: string; error?: unknown };

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
  private turnosIniciados = 0;
  private pendiente = "";
  private frasesDelTurno = 0;
  private respuestaEnCurso = false;
  private usuarioHablando = false;
  private turnosDeMeissa = 0;
  private ultimoDeMeissa = "";

  /**
   * @param enviar a dónde van los mensajes para la sesión. Por defecto, el canal de datos; los
   *               tests pasan el suyo para ver qué se le habría dicho al proveedor.
   */
  constructor(
    private readonly cb: ConversacionCallbacks,
    private readonly enviar: (mensaje: object) => void = (m) => {
      if (this.canal?.readyState === "open") this.canal.send(JSON.stringify(m));
    },
  ) {}

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
    this.canal.onmessage = (e) => this.recibir(JSON.parse(e.data));

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
    this.canal.onopen = () => this.enviar({ type: "response.create" });
  }

  /**
   * Una nota para Meissa, que ella no lee en voz alta: las que reconoce el guion son el aviso de
   * tiempo y el momento de nombrar a Orión. Se añade a la conversación y la tiene en cuenta en su
   * siguiente turno; no la hace hablar.
   */
  nota(texto: string) {
    this.enviar({
      type: "conversation.item.create",
      item: { type: "message", role: "system", content: [{ type: "input_text", text: texto }] },
    });
  }

  /**
   * Se acabó el tiempo: la nota de despedida y, si nadie está hablando, que se despida ya. Si la
   * persona está hablando no se la corta —su silencio dispara el turno de Meissa, que ya lleva la
   * nota—, y si Meissa está a mitad de un turno, termina el suyo.
   */
  pedirDespedida(texto: string) {
    this.nota(texto);
    if (!this.respuestaEnCurso && !this.usuarioHablando && !this.hablando) {
      this.enviar({ type: "response.create" });
    }
  }

  colgar() {
    this.pc?.close();
    this.micro?.getTracks().forEach((t) => t.stop());
    this.pc = null;
    this.micro = null;
    this.canal = null;
  }

  /**
   * Un evento del proveedor. Público para que los tests lo alimenten sin WebRTC.
   *
   * <p><strong>Por WebRTC el audio no pasa por el canal de datos.</strong> El audio llega por la
   * pista de medios, así que `response.output_audio.delta` —el evento con el que antes se
   * detectaba que Meissa empezaba a hablar— nunca llega por aquí. Por eso el subtítulo no se
   * limpiaba jamás (se veía la conversación entera acumulada hasta que el turno terminaba y saltaba
   * al último) y el contador de preguntas se quedaba en la primera. Ahora el turno nuevo se detecta
   * con `response.created`, que sí viaja por el canal, y el «está sonando» con
   * `output_audio_buffer.started`, que existe justamente para WebRTC.
   */
  recibir(ev: EventoDeVoz) {
    switch (ev.type) {
      // Empieza un turno de Meissa: se borra lo anterior antes de que llegue la primera palabra.
      case "response.created":
        this.respuestaEnCurso = true;
        this.subtitulo = "";
        this.ultimoDeMeissa = "";
        this.pendiente = "";
        this.frasesDelTurno = 0;
        this.turnosIniciados++;
        this.cb.onSubtitulo("");
        this.cb.onEmpiezaTurnoDeMeissa?.(this.turnosIniciados);
        break;

      case "response.done":
        this.respuestaEnCurso = false;
        this.finDeLaIA = performance.now();
        break;

      // Empezó a SONAR. Por WebRTC es este; por WebSocket el audio llega en deltas y sirve igual.
      case "output_audio_buffer.started":
      case "response.output_audio.delta":
        if (!this.hablando) {
          this.hablando = true;
          this.cb.onFase("habla");
        }
        break;

      // Terminó de SONAR, que no es lo mismo que terminar de generarse: hasta aquí el micrófono
      // no es tuyo todavía. `cleared` es cuando la persona la interrumpe: el turno también acabó.
      case "output_audio_buffer.stopped":
      case "output_audio_buffer.cleared":
        this.finDeLaIA = performance.now();
        if (this.hablando) {
          this.hablando = false;
          this.turnosDeMeissa++;
          this.cb.onTurnoDeMeissa(this.turnosDeMeissa, this.ultimoDeMeissa.trim().endsWith("?"));
        }
        this.cb.onFase("escucha");
        break;

      case "input_audio_buffer.speech_started":
        this.usuarioHablando = true;
        this.inicioDelUsuario = performance.now();
        this.cb.onFase("escucha");
        break;

      case "input_audio_buffer.speech_stopped":
        this.usuarioHablando = false;
        this.finDelUsuario = performance.now();
        this.cb.onFase("piensa");
        break;

      case "response.output_audio_transcript.delta":
        this.subtitulo += ev.delta ?? "";
        this.cb.onSubtitulo(this.subtitulo);
        this.pendiente += ev.delta ?? "";
        this.soltarFrasesCompletas();
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
        this.subtitulo = texto;
        this.cb.onSubtitulo(texto);
        // Lo que quedó sin punto final también es una frase: la última del turno.
        this.soltar(this.pendiente);
        this.pendiente = "";
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

  /**
   * Corta lo acumulado en frases terminadas. Una frase termina en punto, cierre de pregunta o
   * exclamación seguidos de espacio: «1.5» o «Mr.» pegado a lo siguiente no la cortan.
   */
  private soltarFrasesCompletas() {
    const fin = /[.!?…]+["')\]]?\s+/g;
    let desde = 0;
    let m: RegExpExecArray | null;
    while ((m = fin.exec(this.pendiente)) !== null) {
      this.soltar(this.pendiente.slice(desde, m.index + m[0].length));
      desde = m.index + m[0].length;
    }
    this.pendiente = this.pendiente.slice(desde);
  }

  private soltar(frase: string) {
    const limpia = frase.trim();
    if (limpia) this.cb.onFrase?.(limpia, this.turnosIniciados, this.frasesDelTurno++);
  }
}
