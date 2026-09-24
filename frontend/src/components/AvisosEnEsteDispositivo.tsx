"use client";

import { BellRing } from "lucide-react";
import { useEffect, useState } from "react";
import { activar, desactivar, estadoActual, probar, type EstadoAvisos } from "@/lib/avisosDispositivo";

/**
 * El pie de la campana: activar los avisos en este dispositivo, o apagarlos y probarlos. Discreto a
 * propósito —una línea, sin ventana emergente—: el permiso del navegador solo se pide si la persona
 * pulsa «Activar», que es lo que evita el «¿Permitir notificaciones?» nada más entrar.
 */
export function AvisosEnEsteDispositivo() {
  const [estado, setEstado] = useState<EstadoAvisos | null>(null);
  const [ocupado, setOcupado] = useState(false);
  const [nota, setNota] = useState<string | null>(null);

  useEffect(() => {
    let vivo = true;
    estadoActual()
      .then((e) => vivo && setEstado(e))
      .catch(() => vivo && setEstado("no-soportado"));
    return () => {
      vivo = false;
    };
  }, []);

  // Si Orión no los tiene encendidos o el navegador no puede, no se ofrece nada: una opción que no
  // funciona es peor que ninguna.
  if (!estado || estado === "no-soportado" || estado === "apagados-en-orion") return null;

  async function hacer(accion: () => Promise<EstadoAvisos>) {
    setOcupado(true);
    setNota(null);
    try {
      setEstado(await accion());
    } catch {
      setNota("No se pudo. Inténtalo otra vez.");
    } finally {
      setOcupado(false);
    }
  }

  return (
    <div className="flex items-start gap-2.5 border-t border-surface-sunken bg-surface px-4 py-3">
      <BellRing size={16} strokeWidth={1.9} className="mt-0.5 shrink-0 text-text-muted" aria-hidden />
      <div className="min-w-0 flex-1 text-[12.5px] leading-snug text-text-secondary">
        {estado === "activos" && (
          <>
            <p>Los avisos importantes también suenan en este dispositivo.</p>
            <p className="mt-1.5 flex gap-3">
              <button
                type="button"
                disabled={ocupado}
                onClick={() => {
                  setNota(null);
                  void probar()
                    .then((n) => setNota(n > 0 ? "Enviado: debería sonar en unos segundos." : "No llegó a ningún dispositivo."))
                    .catch(() => setNota("No se pudo probar."));
                }}
                className="font-semibold text-primary-strong hover:underline disabled:opacity-50"
              >
                Probar
              </button>
              <button
                type="button"
                disabled={ocupado}
                onClick={() => void hacer(desactivar)}
                className="font-semibold text-text-muted hover:text-text hover:underline disabled:opacity-50"
              >
                Apagar
              </button>
            </p>
          </>
        )}
        {estado === "inactivos" && (
          <>
            <p>¿Te avisamos en este dispositivo una hora antes de cada clase?</p>
            <button
              type="button"
              disabled={ocupado}
              onClick={() => void hacer(activar)}
              className="mt-1.5 font-semibold text-primary-strong hover:underline disabled:opacity-50"
            >
              {ocupado ? "Activando…" : "Activar avisos"}
            </button>
          </>
        )}
        {estado === "bloqueados" && (
          <p>
            Bloqueaste los avisos de Orión en este navegador. Para activarlos, permítelos desde el candado
            junto a la dirección.
          </p>
        )}
        {nota && <p className="mt-1.5 text-text-muted">{nota}</p>}
      </div>
    </div>
  );
}
