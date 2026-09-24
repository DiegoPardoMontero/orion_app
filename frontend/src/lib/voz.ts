import { useCallback, useEffect, useRef, useState } from "react";

/**
 * La voz en inglés del dispositivo, para los ejercicios de escucha. Es la síntesis de voz del
 * navegador: no cuesta nada y no sale de la máquina del estudiante. La contracara es que no todos
 * los dispositivos traen una voz en inglés; ahí el ejercicio se puede saltar sin que cuente.
 *
 * <p>Las voces cargan tarde en varios navegadores (llegan con `voiceschanged`), así que la respuesta
 * empieza en `null` —todavía no se sabe— y se decide en cuanto llegan o, si no llegan, al segundo y
 * medio.
 */
export function useVozEnIngles() {
  const [voz, setVoz] = useState<SpeechSynthesisVoice | null | undefined>(undefined);
  const [hablando, setHablando] = useState(false);
  const vivo = useRef(true);

  useEffect(() => {
    vivo.current = true;
    if (!("speechSynthesis" in globalThis)) {
      const t = setTimeout(() => setVoz(null), 0);
      return () => clearTimeout(t);
    }
    const elegir = () => {
      const voces = window.speechSynthesis.getVoices().filter((v) => v.lang.toLowerCase().startsWith("en"));
      if (voces.length === 0) return false;
      // Primero una de EE. UU. y, entre ellas, la local (suena igual sin red).
      const preferida =
        voces.find((v) => v.lang === "en-US" && v.localService) ?? voces.find((v) => v.lang === "en-US") ?? voces[0];
      if (vivo.current) setVoz(preferida);
      return true;
    };
    if (elegir()) return;
    const alCambiar = () => {
      elegir();
    };
    window.speechSynthesis.addEventListener("voiceschanged", alCambiar);
    const limite = window.setTimeout(() => {
      if (vivo.current && !elegir()) setVoz(null);
    }, 1500);
    return () => {
      vivo.current = false;
      window.speechSynthesis.removeEventListener("voiceschanged", alCambiar);
      window.clearTimeout(limite);
      window.speechSynthesis.cancel();
    };
  }, []);

  const hablar = useCallback(
    (texto: string, lento = false) => {
      if (!voz) return;
      window.speechSynthesis.cancel();
      const frase = new SpeechSynthesisUtterance(texto);
      frase.voice = voz;
      frase.lang = voz.lang;
      frase.rate = lento ? 0.65 : 0.95;
      frase.onstart = () => setHablando(true);
      frase.onend = () => setHablando(false);
      frase.onerror = () => setHablando(false);
      window.speechSynthesis.speak(frase);
    },
    [voz],
  );

  /** `undefined`: todavía se está averiguando; `false`: no hay voz en inglés. */
  const disponible = voz === undefined ? undefined : voz !== null;
  return { disponible, hablar, hablando };
}
