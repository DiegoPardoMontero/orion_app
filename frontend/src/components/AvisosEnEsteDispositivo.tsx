"use client";

import { BellRing } from "lucide-react";
import { useEffect, useState } from "react";
import {
  activar,
  desactivar,
  esIphoneSinInstalar,
  estadoActual,
  motivoDelFallo,
  probar,
  type EstadoAvisos,
} from "@/lib/avisosDispositivo";

/**
 * El pie de la campana: activar los avisos en este dispositivo, o apagarlos y probarlos. Discreto a
 * propósito —una línea, sin ventana emergente—: el permiso del navegador solo se pide si la persona
 * pulsa «Activar», que es lo que evita el «¿Permitir notificaciones?» nada más entrar.
 *
 * <p>Siempre dice en qué están los avisos aquí, también cuando no se pueden activar y por qué. Antes
 * se escondía en esos casos —y mientras revisaba, que puede tardar unos segundos—, y así nadie podía
 * saber qué pasaba: Pardo, el 28/09/2026, abría la campana y no veía nada.
 */
/** El estado, o por qué no se pudo saber: nunca un error que deje la campana callada. */
async function consultar(): Promise<{ estado: EstadoAvisos | null; fallo: string | null }> {
  try {
    return { estado: await estadoActual(), fallo: null };
  } catch (error) {
    return { estado: null, fallo: motivoDelFallo(error) };
  }
}

export function AvisosEnEsteDispositivo() {
  const [estado, setEstado] = useState<EstadoAvisos | null>(null);
  const [fallo, setFallo] = useState<string | null>(null);
  const [ocupado, setOcupado] = useState(false);
  const [nota, setNota] = useState<string | null>(null);

  useEffect(() => {
    let vivo = true;
    void consultar().then((r) => {
      if (!vivo) return;
      setEstado(r.estado);
      setFallo(r.fallo);
    });
    return () => {
      vivo = false;
    };
  }, []);

  function revisar() {
    setFallo(null);
    setEstado(null);
    void consultar().then((r) => {
      setEstado(r.estado);
      setFallo(r.fallo);
    });
  }

  async function hacer(accion: () => Promise<EstadoAvisos>) {
    setOcupado(true);
    setNota(null);
    try {
      setEstado(await accion());
    } catch (error) {
      setNota(motivoDelFallo(error));
    } finally {
      setOcupado(false);
    }
  }

  return (
    <div className="flex items-start gap-2.5 border-t border-surface-sunken bg-surface px-4 py-3">
      <BellRing size={16} strokeWidth={1.9} className="mt-0.5 shrink-0 text-text-muted" aria-hidden />
      <div className="min-w-0 flex-1 text-[12.5px] leading-snug text-text-secondary">
        {fallo ? (
          <>
            <p>{fallo}</p>
            <button
              type="button"
              onClick={() => revisar()}
              className="mt-1.5 font-semibold text-primary-strong hover:underline"
            >
              Reintentar
            </button>
          </>
        ) : (
          estado === null && <p className="text-text-muted">Revisando los avisos en este dispositivo…</p>
        )}
        {!fallo && estado === "no-soportado" && (
          <p>
            {esIphoneSinInstalar()
              ? "En iPhone, los avisos solo llegan con Orión instalada: en Safari, toca Compartir → «Añadir a pantalla de inicio» y abre Orión desde ese ícono."
              : "Este navegador no puede recibir avisos de Orión. Prueba con Chrome, Edge, Firefox o Safari."}
          </p>
        )}
        {!fallo && estado === "apagados-en-orion" && (
          <p>Los avisos en el dispositivo todavía no están encendidos en Orión.</p>
        )}
        {!fallo && estado === "activos" && (
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
        {!fallo && estado === "inactivos" && (
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
        {!fallo && estado === "bloqueados" && (
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
