"use client";

import { apiFetch } from "@/lib/api/fetch";

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

export async function estadoActual(): Promise<EstadoAvisos> {
  if (!soportado()) return "no-soportado";
  const config = await apiFetch<Config>("/api/v1/push/config");
  if (!config.enabled) return "apagados-en-orion";
  if (Notification.permission === "denied") return "bloqueados";
  const sub = await (await registro()).pushManager.getSubscription();
  return sub && Notification.permission === "granted" ? "activos" : "inactivos";
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
  const json = sub.toJSON();
  await apiFetch("/api/v1/me/push-subscriptions", {
    method: "POST",
    body: { endpoint: json.endpoint, keys: { p256dh: json.keys?.p256dh, auth: json.keys?.auth } },
  });
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
