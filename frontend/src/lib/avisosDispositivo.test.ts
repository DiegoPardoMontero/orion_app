import { afterEach, describe, expect, it, vi } from "vitest";

vi.mock("@/lib/api/fetch", async (original) => ({
  ...(await original<typeof import("@/lib/api/fetch")>()),
  apiFetch: vi.fn(() => Promise.resolve(undefined)),
}));

import { ApiError, apiFetch } from "@/lib/api/fetch";
import { desdeBase64Url, esIphoneSinInstalar, estadoActual, motivoDelFallo, soltarAlCerrarSesion } from "./avisosDispositivo";

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

describe("«activos» quiere decir que Orión sabe a dónde avisar", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.mocked(apiFetch).mockReset();
    vi.mocked(apiFetch).mockResolvedValue(undefined);
  });

  it("con una suscripción en el navegador, se la vuelve a mandar al servidor antes de decir «activos»", async () => {
    const sub = {
      endpoint: "https://fcm.googleapis.com/fcm/send/y",
      toJSON: () => ({ endpoint: "https://fcm.googleapis.com/fcm/send/y", keys: { p256dh: "p", auth: "a" } }),
    };
    vi.stubGlobal("window", { PushManager: {}, Notification: {} });
    vi.stubGlobal("Notification", { permission: "granted" });
    vi.stubGlobal("navigator", {
      serviceWorker: { getRegistration: async () => ({ pushManager: { getSubscription: async () => sub } }) },
    });
    vi.mocked(apiFetch).mockImplementation(async (ruta: string) =>
      ruta === "/api/v1/push/config" ? { enabled: true, publicKey: "BKey" } : undefined,
    );

    expect(await estadoActual()).toBe("activos");
    expect(apiFetch).toHaveBeenCalledWith("/api/v1/me/push-subscriptions", {
      method: "POST",
      body: { endpoint: "https://fcm.googleapis.com/fcm/send/y", keys: { p256dh: "p", auth: "a" } },
    });
  });
});

describe("por qué no se pudo, dicho para la persona", () => {
  it("lo que responde Orión llega tal cual", () => {
    expect(motivoDelFallo(new ApiError(422, "Esa suscripción no viene de un servicio de avisos conocido."))).toBe(
      "Esa suscripción no viene de un servicio de avisos conocido.",
    );
  });

  it("un permiso negado lleva al candado; lo demás del navegador, casi siempre, a una ventana de incógnito", () => {
    expect(motivoDelFallo(new DOMException("x", "NotAllowedError"))).toMatch(/candado/);
    expect(motivoDelFallo(new DOMException("Registration failed", "AbortError"))).toMatch(/incógnito/);
  });

  it("sin nada más que decir, la conexión", () => {
    expect(motivoDelFallo(new Error("x"))).toMatch(/conexión/);
  });
});

describe("el iPhone sin instalar", () => {
  afterEach(() => vi.unstubAllGlobals());

  function en(userAgent: string, instalada: boolean) {
    vi.stubGlobal("navigator", { userAgent, maxTouchPoints: 5, standalone: instalada });
    vi.stubGlobal("window", { matchMedia: () => ({ matches: instalada }) });
  }

  it("en Safari del iPhone, sin instalar, sí; instalada o en otro sistema, no", () => {
    en("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) Safari/604.1", false);
    expect(esIphoneSinInstalar()).toBe(true);
    en("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) Safari/604.1", true);
    expect(esIphoneSinInstalar()).toBe(false);
    en("Mozilla/5.0 (Linux; Android 14) Chrome/140.0 Mobile", false);
    expect(esIphoneSinInstalar()).toBe(false);
  });
});
