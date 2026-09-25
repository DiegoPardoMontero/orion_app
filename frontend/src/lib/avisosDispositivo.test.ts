import { afterEach, describe, expect, it, vi } from "vitest";

vi.mock("@/lib/api/fetch", () => ({ apiFetch: vi.fn(() => Promise.resolve(undefined)) }));

import { apiFetch } from "@/lib/api/fetch";
import { desdeBase64Url, soltarAlCerrarSesion } from "./avisosDispositivo";

describe("la clave VAPID en bytes", () => {
  it("decodifica base64url sin relleno, con - y _", () => {
    expect(Array.from(desdeBase64Url("-_8"))).toEqual([0xfb, 0xff]);
    expect(Array.from(desdeBase64Url("AQID"))).toEqual([1, 2, 3]);
  });
});

describe("al cerrar sesión, este navegador deja de avisar a esa cuenta", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.mocked(apiFetch).mockReset();
    vi.mocked(apiFetch).mockResolvedValue(undefined);
  });

  function navegadorCon(sub: { endpoint: string; unsubscribe: () => Promise<boolean> } | null) {
    vi.stubGlobal("window", { PushManager: {}, Notification: {} });
    vi.stubGlobal("navigator", {
      serviceWorker: { getRegistration: async () => ({ pushManager: { getSubscription: async () => sub } }) },
    });
  }

  it("le dice al servidor qué navegador soltar y cancela la suscripción", async () => {
    const unsubscribe = vi.fn(async () => true);
    navegadorCon({ endpoint: "https://fcm.googleapis.com/fcm/send/x", unsubscribe });

    await soltarAlCerrarSesion();

    expect(apiFetch).toHaveBeenCalledWith("/api/v1/me/push-subscriptions/remove", {
      method: "POST",
      body: { endpoint: "https://fcm.googleapis.com/fcm/send/x" },
    });
    expect(unsubscribe).toHaveBeenCalled();
  });

  it("sin avisos activos no llama a nadie", async () => {
    navegadorCon(null);
    await soltarAlCerrarSesion();
    expect(apiFetch).not.toHaveBeenCalled();
  });

  it("si el servidor falla, igual suelta el navegador y no rompe el cierre de sesión", async () => {
    vi.mocked(apiFetch).mockRejectedValue(new Error("caído"));
    const unsubscribe = vi.fn(async () => true);
    navegadorCon({ endpoint: "https://fcm.googleapis.com/fcm/send/y", unsubscribe });

    await expect(soltarAlCerrarSesion()).resolves.toBeUndefined();
    expect(unsubscribe).toHaveBeenCalled();
  });
});
