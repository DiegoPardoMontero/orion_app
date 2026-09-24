"use client";

import {
  BookOpen,
  Check,
  Clock,
  Eye,
  Flame,
  Headphones,
  Lightbulb,
  Link2,
  MessagesSquare,
  PencilLine,
  Play,
  RotateCcw,
  SkipForward,
  Sparkles,
  VolumeX,
  type LucideIcon,
} from "lucide-react";
import type { ReactNode } from "react";
import { NOMBRE_DE_CATEGORIA, type CategoriaDeEjercicio } from "@/lib/practica";

/**
 * Las piezas de la pantalla de ejercicio (handoff `design_handoff_orion_practica`, §9). Todas salen
 * de las mismas variantes: una ficha, una pareja, una opción o una sugerida del chat se ven igual
 * cuando están elegidas, bien, casi o mostradas. El resultado nunca depende solo del color: cada
 * variante de resultado lleva su ícono, y la etiqueta para lectores de pantalla dice en palabras
 * cómo está.
 */

export type Variante = "normal" | "elegida" | "puesta" | "unida" | "bien" | "casi" | "mostrada" | "usada" | "apagada";

const CLASE: Record<Variante, string> = {
  normal: "bg-white border-[1.5px] border-line text-ink shadow-ficha",
  elegida: "bg-white border-2 border-noche text-ink shadow-ficha-lift -translate-y-[3px]",
  puesta: "bg-white border-[1.5px] border-lavanda text-ink",
  unida: "bg-lavanda-soft border-[1.5px] border-lavanda text-[#3E2E63]",
  bien: "bg-ok-bg border-[1.5px] border-ok-line text-ok-ink",
  casi: "bg-casi-bg border-[1.5px] border-casi-line text-casi-ink",
  mostrada: "bg-shown-bg border-[1.5px] border-dashed border-shown-line text-shown-ink",
  usada: "bg-transparent border-[1.5px] border-dashed border-[#D9CBBE] text-transparent",
  apagada: "bg-white border-[1.5px] border-line text-ink opacity-50",
};

/** Solo la normal responde al puntero: las demás ya dicen algo y no invitan a tocarlas. */
const HOVER = "hover:border-[#B8A99B] hover:shadow-[0_3px_0_#D9CBBE] hover:-translate-y-px";

const ICONO: Partial<Record<Variante, LucideIcon>> = {
  unida: Link2,
  bien: Check,
  casi: RotateCcw,
  mostrada: Eye,
};

const EN_PALABRAS: Partial<Record<Variante, string>> = {
  elegida: "elegida",
  unida: "unida",
  bien: "correcta",
  casi: "casi, revísala",
  mostrada: "te la mostramos",
  usada: "ya usada",
  apagada: "desactivada",
};

export function claseDe(v: Variante, interactiva: boolean): string {
  return `${CLASE[v]} ${interactiva && v === "normal" ? HOVER : ""}`;
}

export function etiquetaDe(texto: string, v: Variante): string {
  const e = EN_PALABRAS[v];
  return e ? `${texto}, ${e}` : texto;
}

export function IconoDeVariante({ v, size = 16 }: { v: Variante; size?: number }) {
  const I = ICONO[v];
  return I ? <I size={size} strokeWidth={2} aria-hidden className="shrink-0" /> : null;
}

/**
 * Una ficha (§9.1): una palabra que se toca para ponerla, o para devolverla si ya está puesta.
 * La usada deja su hueco en el banco, del mismo ancho, para que las demás no salten de sitio.
 */
export function Ficha({
  texto,
  v = "normal",
  onToque,
  interactiva = true,
  grande = false,
  className = "",
  lang = "en",
}: {
  texto: string;
  v?: Variante;
  onToque?: () => void;
  interactiva?: boolean;
  /** En escritorio el texto sube a 17. */
  grande?: boolean;
  className?: string;
  lang?: string;
}) {
  const base = `inline-flex min-h-12 min-w-12 items-center justify-center gap-1.5 rounded-ficha px-4 font-sans font-bold transition-[transform,box-shadow] duration-180 ease-out-soft ${
    grande ? "text-[17px]" : "text-[16px]"
  }`;
  if (v === "usada" || !onToque || !interactiva) {
    return (
      <span
        lang={lang}
        className={`${base} ${claseDe(v, false)} ${className}`}
        aria-hidden={v === "usada" || undefined}
        aria-label={v === "usada" ? undefined : etiquetaDe(texto, v)}
      >
        {texto}
        <IconoDeVariante v={v} />
      </span>
    );
  }
  return (
    <button
      type="button"
      lang={lang}
      onClick={onToque}
      aria-label={etiquetaDe(texto, v)}
      className={`${base} ${claseDe(v, true)} cursor-pointer ${className}`}
    >
      {texto}
      <IconoDeVariante v={v} />
    </button>
  );
}

/* ---- Categorías (§7) ---- */

export const CATEGORIA: Record<CategoriaDeEjercicio, { fondo: string; tinta: string; icono: LucideIcon }> = {
  PALABRAS: { fondo: "#FFE9D6", tinta: "#8A4A1F", icono: BookOpen },
  FRASES: { fondo: "#E6E1EF", tinta: "#2E1E4E", icono: PencilLine },
  CONVERSACION: { fondo: "#FDE4E0", tinta: "#A8321F", icono: MessagesSquare },
  ESCUCHA: { fondo: "#EFE9F9", tinta: "#5E4A8A", icono: Headphones },
  TU_TURNO: { fondo: "#FFF1C9", tinta: "#6E500C", icono: Sparkles },
};

export function ChipCategoria({ categoria, tipo }: { categoria: CategoriaDeEjercicio; tipo: string }) {
  const c = CATEGORIA[categoria];
  const I = c.icono;
  return (
    <div className="flex flex-wrap items-center gap-2.5">
      <span
        className="inline-flex h-[30px] items-center gap-1.5 rounded-pill pr-3 pl-2.5 text-[12px] font-extrabold tracking-[.06em] uppercase"
        style={{ background: c.fondo, color: c.tinta }}
      >
        <I size={15} strokeWidth={1.75} aria-hidden />
        {NOMBRE_DE_CATEGORIA[categoria]}
      </span>
      <span className="text-[14px] font-bold text-ink-2">{tipo}</span>
    </div>
  );
}

/** El círculo de la categoría, para listas (inicio del set). */
export function CirculoCategoria({ categoria, tam = 28 }: { categoria: CategoriaDeEjercicio; tam?: number }) {
  const c = CATEGORIA[categoria];
  const I = c.icono;
  return (
    <span
      className="flex shrink-0 items-center justify-center rounded-full"
      style={{ width: tam, height: tam, background: c.fondo, color: c.tinta }}
      aria-hidden
    >
      <I size={tam > 28 ? 16 : 15} strokeWidth={1.75} />
    </span>
  );
}

/* ---- Racha (§9.7) ---- */

export function Racha({ n }: { n: number }) {
  return (
    <span
      role="status"
      key={n}
      className="pr-racha flex h-9 items-center gap-1.5 rounded-pill bg-durazno-soft px-3 text-[13px] font-extrabold whitespace-nowrap text-durazno-ink"
    >
      <Flame size={16} strokeWidth={1.75} aria-hidden />
      {n} seguidas
    </span>
  );
}

/* ---- Panel de respuesta (compartido) ---- */

export type Tono = "bien" | "casi" | "mostrada" | "saltado";

const PANEL: Record<Tono, { fondo: string; borde: string; circulo: string; icono: string; titulo: string; I: LucideIcon }> = {
  bien: { fondo: "#DEF3E7", borde: "#A9DABD", circulo: "#2E6B4A", icono: "#FFFFFF", titulo: "#1F5238", I: Check },
  casi: { fondo: "#FFEBC7", borde: "#F2C170", circulo: "#F2B34C", icono: "#33203B", titulo: "#6B440A", I: Lightbulb },
  mostrada: { fondo: "#F3EEFB", borde: "#D5CAF0", circulo: "#5E4A8A", icono: "#FFFFFF", titulo: "#4A3A75", I: Eye },
  saltado: { fondo: "#FFFFFF", borde: "#E3D6CA", circulo: "#EADFD4", icono: "#33203B", titulo: "#33203B", I: SkipForward },
};

export function PanelRespuesta({
  tono,
  titulo,
  respuesta,
  cuerpo,
  nota,
}: {
  tono: Tono;
  titulo: string;
  respuesta?: string | null;
  cuerpo?: string | null;
  nota?: string | null;
}) {
  const p = PANEL[tono];
  const I = p.I;
  return (
    <div
      role="status"
      aria-live="polite"
      className={`flex items-start gap-3.5 rounded-panel border-[1.5px] px-[18px] py-4 ${tono === "bien" ? "pr-panel-ok" : "pr-panel-casi"}`}
      style={{ background: p.fondo, borderColor: p.borde }}
    >
      <span
        aria-hidden
        className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full"
        style={{ background: p.circulo, color: p.icono }}
      >
        <I size={20} strokeWidth={2} />
      </span>
      <span className="flex min-w-0 flex-1 flex-col gap-1.5">
        <strong className="font-display text-[20px] font-bold" style={{ color: p.titulo }}>
          {titulo}
        </strong>
        {respuesta && (
          <span lang="en" className="rounded-[12px] bg-white px-3 py-2 text-[16px] leading-[1.4] font-bold">
            {respuesta}
          </span>
        )}
        {cuerpo && <span className="text-[15px] leading-[1.5] text-pretty text-ink-body">{cuerpo}</span>}
        {nota && (
          <span className="text-[13px] font-bold" style={{ color: p.titulo }}>
            {nota}
          </span>
        )}
      </span>
    </div>
  );
}

/* ---- Botones ---- */

/**
 * El botón principal (§9, «Botón principal»): coral, uno por pantalla. Deshabilitado sigue en el
 * orden del teclado —`aria-disabled`, no `disabled`—, para que quien navega con teclado sepa que
 * está ahí y por qué no responde todavía.
 */
export function BotonPrincipal({
  children,
  onClick,
  deshabilitado = false,
  cargando = false,
  className = "",
}: {
  children: ReactNode;
  onClick?: () => void;
  deshabilitado?: boolean;
  cargando?: boolean;
  className?: string;
}) {
  const apagado = deshabilitado && !cargando;
  return (
    <button
      type="button"
      aria-disabled={deshabilitado || cargando || undefined}
      aria-busy={cargando || undefined}
      onClick={() => {
        if (!deshabilitado && !cargando) onClick?.();
      }}
      className={`flex h-14 min-w-[180px] items-center justify-center gap-2.5 rounded-pill px-8 text-[16px] font-bold transition-colors ${
        apagado
          ? "cursor-default bg-disabled-bg text-disabled-ink"
          : cargando
            ? "cursor-default bg-coral text-crema"
            : "cursor-pointer bg-coral text-crema shadow-cta hover:bg-coral-hover"
      } ${className}`}
    >
      {cargando && (
        <span
          aria-hidden
          className="pr-giro h-[18px] w-[18px] rounded-full border-[2.5px] border-[rgba(255,246,238,.4)] border-t-crema"
        />
      )}
      {children}
    </button>
  );
}

export function BotonSecundario({
  children,
  onClick,
  icono,
  className = "",
  deshabilitado = false,
}: {
  children: ReactNode;
  onClick?: () => void;
  icono?: LucideIcon;
  className?: string;
  deshabilitado?: boolean;
}) {
  const I = icono;
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={deshabilitado}
      className={`flex h-14 items-center justify-center gap-2 rounded-pill border-[1.5px] border-ink bg-white px-[22px] text-[16px] font-bold text-ink transition-colors hover:bg-arena disabled:opacity-60 ${className}`}
    >
      {I && <I size={18} strokeWidth={1.75} aria-hidden />}
      {children}
    </button>
  );
}

/* ---- Campo de texto (copy-y-estados, «Estados del campo de texto») ---- */

export type EstadoCampo = "vacio" | "escribiendo" | "casi" | "bien" | "mostrada" | "enviando" | "deshabilitado";

const CAMPO: Record<EstadoCampo, string> = {
  vacio: "bg-white border-[1.5px] border-line focus:border-2 focus:border-noche focus:shadow-[0_0_0_4px_rgba(185,167,230,.45)]",
  escribiendo: "bg-white border-2 border-noche shadow-[0_0_0_4px_rgba(185,167,230,.45)]",
  casi: "bg-[#FFF8EA] border-2 border-casi-line",
  bien: "bg-[#F1FAF4] border-2 border-ok-line",
  mostrada: "bg-white border-[1.5px] border-line",
  enviando: "bg-white border-[1.5px] border-line opacity-60",
  deshabilitado: "bg-[#F6EFE8] border-[1.5px] border-dashed border-[#D9CBBE] opacity-70",
};

export function Campo({
  etiqueta,
  valor,
  onCambio,
  placeholder,
  alto,
  estado,
  ayuda,
  soloLectura,
  campoRef,
}: {
  etiqueta: string;
  valor: string;
  onCambio: (v: string) => void;
  placeholder: string;
  alto: number;
  estado: EstadoCampo;
  ayuda?: string | null;
  soloLectura: boolean;
  campoRef?: React.Ref<HTMLTextAreaElement>;
}) {
  return (
    <label className="flex flex-col gap-2">
      <span className="text-[14px] font-bold text-ink-2">{etiqueta}</span>
      <textarea
        ref={campoRef}
        lang="en"
        value={valor}
        maxLength={600}
        readOnly={soloLectura}
        disabled={estado === "deshabilitado"}
        placeholder={placeholder}
        onChange={(e) => onCambio(e.target.value)}
        spellCheck={false}
        autoCapitalize="sentences"
        className={`block w-full resize-none rounded-sub px-4 py-3.5 text-[17px] leading-[1.45] font-semibold text-ink caret-coral outline-none placeholder:font-semibold placeholder:text-ink-3 focus-visible:outline-none ${CAMPO[estado]}`}
        style={{ minHeight: alto }}
      />
      {ayuda && <span className="text-[13px] leading-[1.45] text-ink-2">{ayuda}</span>}
    </label>
  );
}

/* ---- Reproducir (§9.5) ---- */

export function Reproducir({
  sonando,
  despacio,
  enCasi,
  onTocar,
  onRepetir,
  onDespacio,
  deshabilitado,
  sub,
}: {
  sonando: boolean;
  despacio: boolean;
  enCasi: boolean;
  onTocar: () => void;
  onRepetir: () => void;
  onDespacio: () => void;
  deshabilitado: boolean;
  /** La sub-superficie: blanca en el celular, crema en escritorio. */
  sub: string;
}) {
  return (
    <div className={`flex items-center gap-4 rounded-tarjeta px-[18px] py-4 ${sub}`}>
      <button
        type="button"
        onClick={onTocar}
        disabled={deshabilitado}
        aria-label={sonando ? "Sonando. Toca para detener" : "Reproducir"}
        className="flex h-[72px] w-[72px] shrink-0 cursor-pointer items-center justify-center rounded-full bg-noche text-crema transition-[background,box-shadow] hover:bg-noche-hover disabled:opacity-40"
        style={{ boxShadow: sonando ? "0 0 0 8px rgba(185,167,230,.45)" : "0 8px 18px -8px rgba(46,30,78,.6)" }}
      >
        {sonando ? (
          <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" aria-hidden>
            <line x1="6" y1="9" x2="6" y2="15" />
            <line x1="10" y1="5" x2="10" y2="19" />
            <line x1="14" y1="8" x2="14" y2="16" />
            <line x1="18" y1="10" x2="18" y2="14" />
          </svg>
        ) : (
          <Play size={30} fill="currentColor" strokeWidth={0} aria-hidden />
        )}
      </button>
      <div className="flex min-w-0 flex-1 flex-col gap-2">
        <span aria-live="polite" className="text-[16px] font-bold">
          {sonando ? "Sonando…" : enCasi ? "Escúchala otra vez" : "Toca para escuchar"}
        </span>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={onRepetir}
            disabled={deshabilitado}
            className="flex h-11 cursor-pointer items-center gap-1.5 rounded-pill border-[1.5px] border-line bg-white px-3.5 text-[14px] font-bold text-ink disabled:opacity-40"
          >
            <RotateCcw size={16} strokeWidth={1.75} aria-hidden />
            Repetir
          </button>
          <button
            type="button"
            onClick={onDespacio}
            disabled={deshabilitado}
            aria-pressed={despacio}
            className={`flex h-11 cursor-pointer items-center gap-1.5 rounded-pill border-[1.5px] px-3.5 text-[14px] font-bold disabled:opacity-40 ${
              despacio ? "border-noche bg-noche text-crema" : "border-line bg-white text-ink"
            }`}
          >
            <Clock size={16} strokeWidth={1.75} aria-hidden />
            Más despacio
          </button>
        </div>
      </div>
    </div>
  );
}

/** Sin voz en inglés (§9.5): el reproductor se cambia por este aviso y se puede saltar. */
export function SinVoz() {
  return (
    <div role="note" className="flex items-start gap-3.5 rounded-tarjeta bg-shown-bg px-[18px] py-4">
      <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-white text-lavanda-ink" aria-hidden>
        <VolumeX size={20} strokeWidth={1.75} />
      </span>
      <span className="flex flex-col gap-1">
        <strong className="text-[16px]">Tu dispositivo no tiene voz en inglés</strong>
        <span className="text-[14px] leading-[1.5] text-ink-2">Puedes saltar este ejercicio. No cuenta como error.</span>
      </span>
    </div>
  );
}

/* ---- Burbuja de chat (§9.4) ---- */

export type EstadoBurbuja = "enviando" | "bien" | "casi" | "mostrada";

const META: Record<EstadoBurbuja, { texto: string; tinta: string; I: LucideIcon }> = {
  enviando: { texto: "Enviando…", tinta: "#7A6B85", I: Clock },
  bien: { texto: "Así es", tinta: "#2E6B4A", I: Check },
  casi: { texto: "Casi…", tinta: "#8A5A12", I: RotateCcw },
  mostrada: { texto: "Te la mostramos", tinta: "#5E4A8A", I: Eye },
};

export function BurbujaEllos({ texto }: { texto: string }) {
  return (
    <div className="flex flex-col items-start gap-1">
      <span
        lang="en"
        className="pr-burbuja-ellos max-w-[82%] rounded-[18px_18px_18px_6px] border border-line-soft bg-white px-[15px] py-[11px] text-[15px] leading-[1.4] font-semibold"
      >
        {texto}
      </span>
    </div>
  );
}

export function BurbujaTu({ texto, estado }: { texto: string; estado: EstadoBurbuja }) {
  const color =
    estado === "bien"
      ? "bg-ok-bg text-ok-ink"
      : estado === "casi"
        ? "bg-casi-bg text-casi-ink"
        : estado === "mostrada"
          ? "bg-shown-bg text-shown-ink"
          : "bg-noche text-crema";
  const m = META[estado];
  return (
    <div className="flex flex-col items-end gap-1">
      <span lang="en" className={`pr-burbuja-tu max-w-[82%] rounded-[18px_18px_6px_18px] px-[15px] py-[11px] text-[15px] leading-[1.4] font-semibold ${color}`}>
        {texto}
      </span>
      <span className="flex items-center gap-1 text-[12px] font-bold" style={{ color: m.tinta }}>
        <m.I size={13} strokeWidth={2} aria-hidden />
        {m.texto}
      </span>
    </div>
  );
}

export function Escribiendo({ quien }: { quien: string }) {
  return (
    <div className="flex flex-col items-start">
      <span
        aria-label={`${quien} está escribiendo`}
        role="img"
        className="flex gap-[5px] rounded-[18px_18px_18px_6px] bg-white px-4 py-3.5 shadow-[0_1px_2px_rgba(51,32,59,.08)]"
      >
        {[0, 0.15, 0.3].map((d) => (
          <span key={d} className="pr-punto h-[7px] w-[7px] rounded-full bg-ink-3" style={{ animationDelay: `${d}s` }} />
        ))}
      </span>
    </div>
  );
}
