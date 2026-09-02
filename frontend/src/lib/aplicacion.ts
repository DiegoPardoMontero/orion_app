"use client";

/**
 * Vocabulario y datos compartidos del Bloque 2 (postulación a profesor): estados, tonos de color,
 * tipos de documento, eventos de la bitácora y requisitos faltantes. Vive aparte de i18n porque es
 * un dominio con su propia lógica (tono de badge, si un tipo de documento es obligatorio), no solo
 * cadenas sueltas. El backend manda códigos; la UI los traduce a español de Colombia.
 */

import { useQuery } from "@tanstack/react-query";
import { apiFetch, ApiError } from "@/lib/api/fetch";
import type { TeacherApplicationView } from "@/lib/api/types";

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
  agreement: "Acepta el acuerdo del profesor",
};

export function etiquetaFaltante(code: string): string {
  return FALTANTE_LABEL[code] ?? code;
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

/**
 * La postulación del usuario actual. Un 404 (aún no ha postulado) NO es un fallo de red: se expone
 * como `noAplico` para que el shell distinga «no aplicó» de «error real». `retry:false` para no
 * reintentar un 404, y `redirectOn401:false` NO hace falta (un 401 sí debe ir al login).
 */
export function useMiAplicacion(enabled = true) {
  const query = useQuery({
    queryKey: MI_APLICACION_KEY,
    queryFn: () => apiFetch<TeacherApplicationView>("/api/v1/me/teacher-application"),
    enabled,
    retry: false,
  });

  const noAplico = query.error instanceof ApiError && query.error.status === 404;
  const status = query.data?.status;
  const aprobado = status === "APPROVED";

  return { ...query, noAplico, status, aprobado };
}
