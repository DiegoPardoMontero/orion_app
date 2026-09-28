"use client";

import { ApiError, apiFetch } from "@/lib/api/fetch";

/**
 * Avisos en el dispositivo (Web Push, 24/09/2026): una hora antes de cada clase, calificar al día
 * siguiente, un mensaje nuevo, la práctica lista. Sin ser invasivos: solo se piden cuando la persona
 * pulsa «Activar», nunca solos al entrar.
 *
 * <p>En iPhone solo funcionan con Orión instalada en la pantalla de inicio (iOS 16.4+): Safari no
 * los ofrece en una pestaña normal, y por eso `soportado()` es falso ahí.
 */

export type EstadoAvisos = "no-soportado" | "apagados-en-orion" | "bloqueados" | "activos" | "inactivos";

type Config = { enabled: boolean; publicKey: string | null };

export function soportado(): boolean {
  return (
    typeof window !== "undefined" &&
    "serviceWorker" in navigator &&
    "PushManager" in window &&
    "Notification" in window
  );
}

async function registro(): Promise<ServiceWorkerRegistration> {
  const existente = await navigator.serviceWorker.getRegistration("/");
  return existente ?? navigator.serviceWorker.register("/sw.js");
}

/**
 * iPhone o iPad con Orión abierta en Safari, sin instalar: ahí Apple no ofrece avisos, y hay que
 * decir cómo conseguirlos en vez de callarse (28/09/2026: Pardo no veía nada en la campana).
 */
export function esIphoneSinInstalar(): boolean {
  if (typeof navigator === "undefined" || typeof window === "undefined") return false;
  const ios = /iPhone|iPad|iPod/.test(navigator.userAgent) || (/Macintosh/.test(navigator.userAgent) && navigator.maxTouchPoints > 1);
  const instalada =
    window.matchMedia?.("(display-mode: standalone)").matches || (navigator as Navigator & { standalone?: boolean }).standalone === true;
  return ios && !instalada;
}

/**
 * El estado de los avisos en este navegador. Si el navegador tiene una suscripción, se la vuelve a
 * mandar al servidor (es un upsert): «activos» tiene que querer decir que Orión sabe a dónde avisar.
 * Antes bastaba con que el navegador la tuviera, y una que el servidor nunca guardó —un envío que
 * falló, una base recreada— decía «activos» mientras «Probar» no llegaba a ningún lado.
 */
export async function estadoActual(): Promise<EstadoAvisos> {
  if (!soportado()) return "no-soportado";
  const config = await apiFetch<Config>("/api/v1/push/config");
  if (!config.enabled) return "apagados-en-orion";
  if (Notification.permission === "denied") return "bloqueados";
  const sub = await (await registro()).pushManager.getSubscription();
  if (!sub || Notification.permission !== "granted") return "inactivos";
  await guardarEnOrion(sub);
  return "activos";
}

async function guardarEnOrion(sub: PushSubscription) {
  const json = sub.toJSON();
  await apiFetch("/api/v1/me/push-subscriptions", {
    method: "POST",
    body: { endpoint: json.endpoint, keys: { p256dh: json.keys?.p256dh, auth: json.keys?.auth } },
  });
}

/**
 * Por qué no se pudo, dicho para la persona: nunca un «no se pudo» a secas. Lo que responde Orión
 * llega tal cual; lo que rechaza el navegador al suscribirse casi siempre es una ventana de incógnito
 * (Chrome no tiene avisos ahí) o un permiso negado.
 */
export function motivoDelFallo(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof DOMException) {
    if (error.name === "NotAllowedError") {
      return "El navegador no dio permiso para los avisos. Permítelos desde el candado junto a la dirección.";
    }
    return "Este navegador no deja recibir avisos en esta ventana. Si es una ventana de incógnito o privada, abre Orión en una normal.";
  }
  return "No pudimos revisar los avisos. Revisa tu conexión e inténtalo otra vez.";
}

/** Pide el permiso (tiene que ser tras un clic) y registra este navegador. */
export async function activar(): Promise<EstadoAvisos> {
  const config = await apiFetch<Config>("/api/v1/push/config");
  if (!config.enabled || !config.publicKey) return "apagados-en-orion";
  const permiso = await Notification.requestPermission();
  if (permiso !== "granted") return permiso === "denied" ? "bloqueados" : "inactivos";

  const reg = await registro();
  const sub =
    (await reg.pushManager.getSubscription()) ??
    (await reg.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: desdeBase64Url(config.publicKey),
    }));
  await guardarEnOrion(sub);
  return "activos";
}

export async function desactivar(): Promise<EstadoAvisos> {
  const sub = await (await registro()).pushManager.getSubscription();
  if (sub) {
    await apiFetch("/api/v1/me/push-subscriptions/remove", { method: "POST", body: { endpoint: sub.endpoint } });
    await sub.unsubscribe();
  }
  return "inactivos";
}

/**
 * Al cerrar sesión, este navegador deja de avisar a esa cuenta. Si no, en un computador compartido
 * seguían llegando los avisos de quien salió —con el comienzo de sus mensajes— y a quien entrara
 * después le decía «activos» con una suscripción que el servidor seguía asociando a la otra cuenta.
 *
 * <p>No registra el service worker para mirar (si no hay, no hay nada que soltar) y nunca lanza:
 * cerrar sesión no espera a esto.
 */
export async function soltarAlCerrarSesion(): Promise<void> {
  if (!soportado()) return;
  try {
    const reg = await navigator.serviceWorker.getRegistration("/");
    const sub = await reg?.pushManager.getSubscription();
    if (!sub) return;
    await apiFetch("/api/v1/me/push-subscriptions/remove", { method: "POST", body: { endpoint: sub.endpoint } }).catch(
      () => undefined,
    );
    await sub.unsubscribe();
  } catch {
    // Sin permiso o sin service worker: no hay nada que soltar.
  }
}

/** Un aviso de prueba a todos tus navegadores. Devuelve a cuántos llegó. */
export async function probar(): Promise<number> {
  const r = await apiFetch<{ devices: number }>("/api/v1/me/push-subscriptions/test", { method: "POST" });
  return r.devices;
}

/** La clave VAPID llega en base64url; el navegador la quiere en bytes. */
export function desdeBase64Url(b64: string): Uint8Array<ArrayBuffer> {
  const relleno = "=".repeat((4 - (b64.length % 4)) % 4);
  const crudo = atob((b64 + relleno).replace(/-/g, "+").replace(/_/g, "/"));
  const bytes = new Uint8Array(new ArrayBuffer(crudo.length));
  for (let i = 0; i < crudo.length; i++) bytes[i] = crudo.charCodeAt(i);
  return bytes;
}
