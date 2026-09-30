"use client";

/**
 * Vocabulario y datos compartidos del Bloque 2 (postulación a profesor): estados, tonos de color,
 * tipos de documento, eventos de la bitácora y requisitos faltantes. Vive aparte de i18n porque es
 * un dominio con su propia lógica (tono de badge, si un tipo de documento es obligatorio), no solo
 * cadenas sueltas. El backend manda códigos; la UI los traduce a español de Colombia.
 */

import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef } from "react";
import { apiFetch, ApiError } from "@/lib/api/fetch";
import { meQueryKey } from "@/lib/auth/session";
import type { TeacherApplicationView } from "@/lib/api/types";
import { estadoBio, estadoTitular, MAX_PALABRAS_BIO, MIN_PALABRAS_BIO, MIN_PALABRAS_TITULAR } from "@/lib/perfil-profesor";

/** Tono de badge del sistema (ver `Badge` en ui.tsx). */
type TonoBadge = "menta" | "melocoton" | "lavanda" | "coral" | "error" | "neutral";

type EstadoConfig = { label: string; tono: TonoBadge; punto: boolean };

/**
 * Cada estado con su etiqueta y su color. DRAFT gris, revisión ámbar, cambios coral, aprobada
 * verde, rechazada rojo suave — el color lo pide el brief y nunca es el único indicador (siempre
 * hay texto).
 */
export const ESTADO_APLICACION: Record<string, EstadoConfig> = {
  DRAFT: { label: "Borrador", tono: "neutral", punto: false },
  PENDING_REVIEW: { label: "En revisión", tono: "melocoton", punto: true },
  UNDER_REVIEW: { label: "En revisión", tono: "melocoton", punto: true },
  CHANGES_REQUESTED: { label: "Cambios solicitados", tono: "coral", punto: true },
  APPROVED: { label: "Aprobada", tono: "menta", punto: true },
  REJECTED: { label: "No aprobada", tono: "error", punto: true },
};

export function estadoAplicacion(status?: string): EstadoConfig {
  return (status && ESTADO_APLICACION[status]) || { label: status ?? "—", tono: "neutral", punto: false };
}

/**
 * El mismo estado, leído desde el admin. Para el aspirante una postulación enviada ya está «En
 * revisión»; para el admin, mientras nadie la abre, está «Por revisar»: así no se confunde con la
 * que ya empezó a revisar (UNDER_REVIEW) ni dice «En revisión» junto al botón «Empezar revisión».
 */
export function estadoAplicacionAdmin(status?: string): EstadoConfig {
  const cfg = estadoAplicacion(status);
  return status === "PENDING_REVIEW" ? { ...cfg, label: "Por revisar" } : cfg;
}

/** Tipos de documento que acepta el backend. El CV es el único obligatorio para enviar a revisión. */
export const DOC_TIPOS = [
  { code: "CV", label: "Hoja de vida (CV)", obligatorio: true },
  { code: "TEACHING_CERTIFICATE", label: "Certificado docente", obligatorio: false },
  { code: "UNIVERSITY_DEGREE", label: "Título universitario", obligatorio: false },
  { code: "LANGUAGE_CERTIFICATION", label: "Certificación de idioma", obligatorio: false },
  { code: "OTHER", label: "Otro documento", obligatorio: false },
] as const;

export function etiquetaDocumento(code?: string): string {
  return DOC_TIPOS.find((t) => t.code === code)?.label ?? code ?? "Documento";
}

/** Requisitos que el backend reporta en `missing`, traducidos a una acción concreta. */
export const FALTANTE_LABEL: Record<string, string> = {
  photo: "Sube una foto de perfil",
  bio: "Escribe tu presentación",
  language: "Agrega al menos un idioma con su nivel",
  goal: "Elige al menos un objetivo de enseñanza",
  cv: "Sube tu hoja de vida (CV)",
  payout: "Registra tu llave Bre-B: a dónde te pagamos",
  agreement: "Acepta el acuerdo del profesor",
};

export function etiquetaFaltante(code: string): string {
  return FALTANTE_LABEL[code] ?? code;
}

/* ---------------- Lo obligatorio de cada paso del wizard ---------------- */

/** Los pasos del wizard, en orden. El último no se completa: se envía. */
export const PASOS_POSTULACION = [
  "Datos personales",
  "Enseñanza",
  "Experiencia",
  "Documentos",
  // La llave Bre-B va en la postulación (Pardo, 29/09/2026), antes del acuerdo que habla del mandato
  // de recaudo: así el profe aprobado entra a la bienvenida sin que se le pida nada más.
  "Pagos",
  "Acuerdo",
  "Revisar y enviar",
] as const;

export const PASO_REVISION = PASOS_POSTULACION.length - 1;

/** Lo que el aspirante lleva escrito, tal como lo tiene el wizard (aún sin guardar). */
export type BorradorPostulacion = {
  tieneFoto: boolean;
  titular: string;
  bio: string;
  idiomas: { code: string; levels: string[] }[];
  objetivos: string[];
  pais: string;
  ciudad: string;
  /** Tal cual está en el campo: vacío es «no lo ha escrito», no cero. */
  anios: string;
  formacion: string;
  tieneCv: boolean;
  tieneLlave: boolean;
  aceptoAcuerdo: boolean;
};

/**
 * Un campo por completar. `campo` es el id del control en la pantalla (para llevar el foco ahí),
 * `mensaje` lo que se dice junto a él y `nombre` cómo se cuenta en el resumen («tu foto»).
 */
export type Falta = { campo: string; mensaje: string; nombre: string };

export const ANIOS_MAXIMO = 80;

/** Los años de experiencia como número entero entre 0 y 80, o null si no lo son. */
export function aniosDeExperiencia(texto: string): number | null {
  const limpio = texto.trim();
  if (!/^\d{1,2}$/.test(limpio)) return null;
  const n = Number(limpio);
  return n <= ANIOS_MAXIMO ? n : null;
}

/**
 * Lo que le falta a un paso para poder seguir (Pardo, 27/09/2026: «todos los campos son
 * obligatorios»; enterarse al final de que faltaba la foto obligaba a devolverse seis pantallas).
 *
 * <p>Va más allá de lo que exige el backend al enviar (`missingRequirements`: foto, presentación, un
 * idioma con algún nivel, un objetivo, CV, llave Bre-B y acuerdo): aquí también son obligatorios el título, el
 * mínimo de palabras, un nivel por cada idioma, país, ciudad, años de experiencia y formación.
 */
export function faltasDelPaso(paso: number, b: BorradorPostulacion): Falta[] {
  const faltas: Falta[] = [];
  if (paso === 0) {
    if (!b.tieneFoto) {
      faltas.push({ campo: "foto", nombre: "tu foto", mensaje: "Sube una foto de perfil: es lo primero que ven los estudiantes." });
    }
    const titular = estadoTitular(b.titular);
    if (titular.estado !== "ok") {
      faltas.push({
        campo: "headline",
        nombre: "el título",
        mensaje: titular.estado === "vacio" ? `Escribe tu título: mínimo ${MIN_PALABRAS_TITULAR} palabras.` : titular.mensaje,
      });
    }
  } else if (paso === 1) {
    const bio = estadoBio(b.bio);
    if (bio.estado !== "ok") {
      faltas.push({
        campo: "bio",
        nombre: "tu presentación",
        mensaje:
          bio.estado === "vacio"
            ? `Escribe tu presentación: entre ${MIN_PALABRAS_BIO} y ${MAX_PALABRAS_BIO} palabras.`
            : bio.mensaje,
      });
    }
    if (b.idiomas.length === 0) {
      faltas.push({ campo: "idiomas", nombre: "un idioma", mensaje: "Agrega al menos un idioma que enseñes." });
    }
    for (const idioma of b.idiomas) {
      if (idioma.levels.length === 0) {
        faltas.push({
          campo: `niveles-${idioma.code}`,
          nombre: "los niveles de cada idioma",
          mensaje: "Marca al menos un nivel que enseñes en este idioma.",
        });
      }
    }
    if (b.objetivos.length === 0) {
      faltas.push({ campo: "objetivos", nombre: "un objetivo", mensaje: "Elige al menos un objetivo para el que preparas." });
    }
  } else if (paso === 2) {
    if (!b.pais) faltas.push({ campo: "country", nombre: "tu país", mensaje: "Elige tu país." });
    if (!b.ciudad.trim()) {
      faltas.push({
        campo: "city",
        nombre: "tu ciudad",
        mensaje: b.pais ? "Escribe o elige tu ciudad." : "Elige primero tu país y después tu ciudad.",
      });
    }
    if (aniosDeExperiencia(b.anios) === null) {
      faltas.push({
        campo: "years",
        nombre: "tus años de experiencia",
        mensaje: b.anios.trim()
          ? `Escribe un número entero entre 0 y ${ANIOS_MAXIMO}.`
          : "¿Cuántos años llevas enseñando? Si estás empezando, escribe 0.",
      });
    }
    if (!b.formacion.trim()) {
      faltas.push({
        campo: "education",
        nombre: "tu formación",
        mensaje: "Cuéntanos tu formación: un título, un curso o cómo aprendiste el idioma.",
      });
    }
  } else if (paso === 3) {
    if (!b.tieneCv) faltas.push({ campo: "doc-CV", nombre: "tu hoja de vida", mensaje: "Sube tu hoja de vida (CV), en PDF o imagen." });
  } else if (paso === 4) {
    if (!b.tieneLlave) {
      faltas.push({ campo: "llave", nombre: "tu llave Bre-B", mensaje: "Registra tu llave Bre-B y guárdala para seguir." });
    }
  } else if (paso === 5) {
    if (!b.aceptoAcuerdo) {
      faltas.push({ campo: "acuerdo", nombre: "aceptar el acuerdo", mensaje: "Lee el acuerdo y márcalo como aceptado para seguir." });
    }
  }
  return faltas;
}

/** El primer paso con algo por completar; la revisión si no falta nada. */
export function primerPasoIncompleto(b: BorradorPostulacion): number {
  for (let paso = 0; paso < PASO_REVISION; paso++) {
    if (faltasDelPaso(paso, b).length > 0) return paso;
  }
  return PASO_REVISION;
}

/** «tu foto», «tu foto y el título», «tu país, tu ciudad y tu formación». Sin repetidos. */
export function enumerar(nombres: string[]): string {
  const unicos = [...new Set(nombres)];
  if (unicos.length <= 1) return unicos[0] ?? "";
  return `${unicos.slice(0, -1).join(", ")} y ${unicos[unicos.length - 1]}`;
}

/** Eventos de la bitácora de la postulación, en pasado y en español. */
export const EVENTO_LABEL: Record<string, string> = {
  CREATED: "Postulación creada",
  SUBMITTED: "Enviada a revisión",
  REVIEW_STARTED: "Revisión iniciada",
  CHANGES_REQUESTED: "Cambios solicitados",
  RESUBMITTED: "Reenviada a revisión",
  APPROVED: "Aprobada",
  REJECTED: "No aprobada",
};

export function etiquetaEvento(code?: string): string {
  return (code && EVENTO_LABEL[code]) || code || "";
}

/** Estados de admin para el filtro de la bandeja (en el orden en que se revisan). */
export const ESTADOS_ADMIN = [
  { valor: "", etiqueta: "Todas" },
  { valor: "PENDING_REVIEW", etiqueta: "Pendientes" },
  { valor: "UNDER_REVIEW", etiqueta: "En revisión" },
  { valor: "CHANGES_REQUESTED", etiqueta: "Con cambios" },
  { valor: "APPROVED", etiqueta: "Aprobadas" },
  { valor: "REJECTED", etiqueta: "Rechazadas" },
  { valor: "DRAFT", etiqueta: "Borradores" },
] as const;

/** La clave de caché de la postulación propia. Compartida por el wizard, el estado y el shell. */
export const MI_APLICACION_KEY = ["me", "teacher-application"] as const;

/** Mientras la revisan, la decisión puede llegar en cualquier momento. */
const EN_REVISION = ["PENDING_REVIEW", "UNDER_REVIEW"];

/**
 * La postulación del usuario actual. Un 404 (aún no ha postulado) NO es un fallo de red: se expone
 * como `noAplico` para que el shell distinga «no aplicó» de «error real». `retry:false` para no
 * reintentar un 404, y `redirectOn401:false` NO hace falta (un 401 sí debe ir al login).
 *
 * <p>En revisión se vuelve a preguntar cada minuto: si no, quien tenía la sesión abierta no se
 * enteraba de la decisión hasta recargar.
 */
export function useMiAplicacion(enabled = true) {
  const query = useQuery({
    queryKey: MI_APLICACION_KEY,
    queryFn: () => apiFetch<TeacherApplicationView>("/api/v1/me/teacher-application"),
    enabled,
    retry: false,
    refetchInterval: (q) => (EN_REVISION.includes(q.state.data?.status ?? "") ? 60_000 : false),
  });

  const noAplico = query.error instanceof ApiError && query.error.status === 404;
  const status = query.data?.status;
  const aprobado = status === "APPROVED";

  // La decisión cambia el rol (aspirante → profe): la sesión se refresca en cuanto el sondeo la ve.
  // Antes se refrescaba al primer clic en «Completa y publica tu perfil», que rebotaba
  // /perfil → /aplicacion → /perfil con el rol viejo en caché.
  const queryClient = useQueryClient();
  const anterior = useRef(status);
  useEffect(() => {
    if (anterior.current && status && anterior.current !== status) {
      void queryClient.invalidateQueries({ queryKey: meQueryKey });
    }
    anterior.current = status;
  }, [status, queryClient]);

  return { ...query, noAplico, status, aprobado };
}
