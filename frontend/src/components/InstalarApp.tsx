"use client";

import { Download, Share, X } from "lucide-react";
import { useEffect, useState } from "react";
import { Wordmark } from "./marca";

/** Evento no estándar de Chromium para el prompt de instalación. */
type PromptEvent = Event & {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: "accepted" | "dismissed" }>;
};

const CERRADO = "orion-instalar-cerrado";

/**
 * Registra el service worker y sugiere instalar la app. En Chromium usa el evento
 * `beforeinstallprompt`; en iOS/Safari, que no lo dispara, muestra la instrucción manual. No
 * aparece si la app ya está instalada (display-mode: standalone) ni si se cerró en esta sesión.
 */
export function InstalarApp() {
  const [prompt, setPrompt] = useState<PromptEvent | null>(null);
  const [visible, setVisible] = useState(false);
  const [ios, setIos] = useState(false);

  useEffect(() => {
    if ("serviceWorker" in navigator) {
      navigator.serviceWorker.register("/sw.js").catch(() => {});
    }

    const nav = navigator as Navigator & { standalone?: boolean };
    const instalada =
      window.matchMedia("(display-mode: standalone)").matches || nav.standalone === true;
    if (instalada) return;

    const cerrado = sessionStorage.getItem(CERRADO) === "1";

    const onPrompt = (event: Event) => {
      // Evitamos el mini-infobar por defecto para usar nuestro banner.
      event.preventDefault();
      setPrompt(event as PromptEvent);
      if (!cerrado) setVisible(true);
    };
    window.addEventListener("beforeinstallprompt", onPrompt);

    const onInstalled = () => setVisible(false);
    window.addEventListener("appinstalled", onInstalled);

    // iOS Safari no dispara beforeinstallprompt: se instala a mano desde Compartir. La detección
    // va en un rAF para no llamar setState de forma síncrona dentro del effect.
    const ua = navigator.userAgent;
    const esIos = /iphone|ipad|ipod/i.test(ua);
    const esSafari = /safari/i.test(ua) && !/crios|fxios|chrome|android/i.test(ua);
    const raf = requestAnimationFrame(() => {
      if (esIos && esSafari && !cerrado) {
        setIos(true);
        setVisible(true);
      }
    });

    return () => {
      cancelAnimationFrame(raf);
      window.removeEventListener("beforeinstallprompt", onPrompt);
      window.removeEventListener("appinstalled", onInstalled);
    };
  }, []);

  if (!visible) return null;

  function cerrar() {
    setVisible(false);
    sessionStorage.setItem(CERRADO, "1");
  }

  async function instalar() {
    if (!prompt) return;
    await prompt.prompt();
    await prompt.userChoice;
    setPrompt(null);
    setVisible(false);
  }

  return (
    <div className="anim-rise fixed bottom-[88px] left-1/2 z-[60] w-[calc(100%-2rem)] max-w-sm -translate-x-1/2 sm:bottom-6 sm:left-auto sm:right-6 sm:translate-x-0">
      <div className="flex items-start gap-3 rounded-card border border-border bg-surface-raised p-4 shadow-lg">
        <span className="grid h-11 w-11 shrink-0 place-items-center rounded-base bg-accent-peach-soft">
          <Wordmark className="text-[12px] text-primary-strong" />
        </span>

        <div className="min-w-0 flex-1">
          <p className="font-display text-[15px] font-bold text-text">Instala Orión</p>
          {ios ? (
            <p className="mt-0.5 flex flex-wrap items-center gap-1 text-[12.5px] leading-relaxed text-text-secondary">
              Toca <Share size={14} strokeWidth={1.75} className="inline" /> Compartir y luego
              «Añadir a inicio».
            </p>
          ) : (
            <p className="mt-0.5 text-[12.5px] leading-relaxed text-text-secondary">
              Tenla a un toque en tu pantalla de inicio, como una app.
            </p>
          )}

          {!ios && (
            <button
              type="button"
              onClick={instalar}
              className="mt-2.5 inline-flex min-h-10 items-center gap-2 rounded-pill bg-primary px-4 text-[14px] font-bold text-on-primary shadow-primary transition-colors hover:bg-primary-strong focus-visible:shadow-focus"
            >
              <Download size={16} strokeWidth={1.75} />
              Instalar
            </button>
          )}
        </div>

        <button
          type="button"
          aria-label="Cerrar"
          onClick={cerrar}
          className="grid h-8 w-8 shrink-0 place-items-center rounded-full text-text-muted transition-colors hover:bg-surface-sunken hover:text-text focus-visible:shadow-focus"
        >
          <X size={16} strokeWidth={1.75} />
        </button>
      </div>
    </div>
  );
}
