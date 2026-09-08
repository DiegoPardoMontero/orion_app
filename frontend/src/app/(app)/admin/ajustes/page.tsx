"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AlertTriangle, History } from "lucide-react";
import { useState } from "react";
import { AvisoError, Cargando, ErrorCarga } from "@/components/estados";
import { Modal } from "@/components/Modal";
import { Boton, Campo, Spinner, Tarjeta, Toggle } from "@/components/ui";
import { apiFetch, ApiError } from "@/lib/api/fetch";
import { fechaRelativa } from "@/lib/format";

type Setting = {
  key: string;
  value: string;
  updatedAt: string;
  group: string;
  label: string;
  description: string;
  type: "ENTERO" | "BOOLEANO" | "OPCION" | "TEXTO";
  min: number | null;
  max: number | null;
  options: string[];
  sensitive: boolean;
};

type Cambio = {
  key: string;
  label: string;
  oldValue: string | null;
  newValue: string;
  changedByName: string;
  changedAt: string;
};

const ETIQUETA_GRUPO: Record<string, string> = {
  DINERO: "Dinero",
  PLAZOS: "Plazos",
  REPUTACION: "Reputación y ranking",
  POLITICAS: "Políticas",
  OTROS: "Sin catalogar",
};

/**
 * La pantalla más peligrosa de Orión: un cero de más en la comisión cambia lo que cobra cada
 * reserva desde ese instante.
 *
 * <p>Las protecciones son parte del entregable, no un extra. Los ajustes van agrupados y con su
 * explicación en español —no solo la clave—, el servidor valida tipo y rango (esta pantalla no es
 * la única puerta), los sensibles piden confirmación escrita como la purga, y todo cambio queda en
 * un historial visible con quién, cuándo y desde qué valor.
 *
 * <p>Lo que NO se toca desde aquí: las llaves de Wompi, el correo y los datos del responsable. Eso
 * es configuración de despliegue, y ponerla en una pantalla web convierte una sesión de
 * administrador robada en una fuga de credenciales.
 */
export default function AdminAjustesPage() {
  const queryClient = useQueryClient();
  const ajustes = useQuery({
    queryKey: ["admin", "settings"],
    queryFn: () => apiFetch<Setting[]>("/api/v1/admin/settings"),
  });
  const historial = useQuery({
    queryKey: ["admin", "settings", "changes"],
    queryFn: () => apiFetch<Cambio[]>("/api/v1/admin/settings/changes"),
  });
  const [verHistorial, setVerHistorial] = useState(false);

  const guardar = useMutation({
    mutationFn: ({ key, value }: { key: string; value: string }) =>
      apiFetch<Setting>(`/api/v1/admin/settings/${key}`, { method: "PUT", body: { value } }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "settings"] });
    },
  });

  if (ajustes.isPending) return <Cargando filas={4} />;
  if (ajustes.isError) {
    return (
      <ErrorCarga mensaje="No pudimos cargar los ajustes." onReintentar={() => ajustes.refetch()} />
    );
  }

  const grupos = [...new Set(ajustes.data.map((a) => a.group))];

  return (
    <main className="mx-auto w-full max-w-3xl px-5 py-8 lg:px-8">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="font-display text-h1 font-bold">Ajustes</h1>
          <p className="mt-1 text-[14px] text-text-secondary">
            Las reglas de negocio de Orión. Un cambio aquí aplica desde el instante en que se guarda.
          </p>
        </div>
        <Boton variante="contorno" onClick={() => setVerHistorial(true)}>
          <History size={16} strokeWidth={2} />
          Historial
        </Boton>
      </div>

      <p className="mt-4 flex items-start gap-2.5 rounded-base bg-warning-bg px-4 py-3 text-[13px] leading-relaxed text-warning">
        <AlertTriangle size={17} strokeWidth={1.75} className="mt-px shrink-0" />
        <span>
          La comisión se congela en cada reserva: cambiarla no toca las clases ya reservadas, solo
          las siguientes. Los plazos, en cambio, se leen al usarlos y aplican de inmediato a todo.
        </span>
      </p>

      {grupos.map((grupo) => (
        <section key={grupo} className="mt-7">
          <h2 className="font-display text-[17px] font-bold">
            {ETIQUETA_GRUPO[grupo] ?? grupo}
          </h2>
          <div className="mt-3 grid gap-2">
            {ajustes.data
              .filter((a) => a.group === grupo)
              .map((ajuste) => (
                <FilaAjuste
                  key={ajuste.key}
                  ajuste={ajuste}
                  onGuardar={(value) => guardar.mutateAsync({ key: ajuste.key, value })}
                />
              ))}
          </div>
        </section>
      ))}

      {verHistorial && (
        <Modal titulo="Últimos cambios" onCerrar={() => setVerHistorial(false)}>
          {historial.isPending && <Cargando filas={3} />}
          {historial.data?.length === 0 && (
            <p className="text-[14px] text-text-secondary">Todavía no se ha cambiado nada.</p>
          )}
          <div className="grid gap-3">
            {historial.data?.map((c, i) => (
              <div key={i} className="border-b border-border pb-3 last:border-b-0 last:pb-0">
                <p className="text-[14px] font-bold">{c.label}</p>
                <p className="mt-0.5 text-[13px] text-text-secondary">
                  <span className="mono">{c.oldValue ?? "—"}</span> →{" "}
                  <span className="mono font-bold text-text">{c.newValue}</span>
                </p>
                <p className="mt-0.5 text-[12px] text-text-muted">
                  {c.changedByName} · {fechaRelativa(c.changedAt)}
                </p>
              </div>
            ))}
          </div>
        </Modal>
      )}
    </main>
  );
}

function FilaAjuste({
  ajuste,
  onGuardar,
}: {
  ajuste: Setting;
  onGuardar: (value: string) => Promise<unknown>;
}) {
  const [valor, setValor] = useState(ajuste.value);
  const [confirmando, setConfirmando] = useState(false);
  const [escrito, setEscrito] = useState("");
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const cambiado = valor !== ajuste.value;

  async function guardar(value: string) {
    setGuardando(true);
    setError(null);
    try {
      await onGuardar(value);
      setConfirmando(false);
      setEscrito("");
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "No se pudo guardar.");
      setValor(ajuste.value);
    } finally {
      setGuardando(false);
    }
  }

  function intentar() {
    // Los sensibles piden escribir el valor nuevo otra vez, como la purga pide escribir BORRAR.
    // No es seguridad —quien llega aquí ya tiene el rol— sino un freno contra el descuido, que es
    // de lo que hay que protegerse: nadie cambia la comisión al 30 % a propósito.
    if (ajuste.sensitive) {
      setConfirmando(true);
      return;
    }
    guardar(valor);
  }

  return (
    <Tarjeta className="border border-border">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0 flex-1">
          <p className="text-[14.5px] font-bold">
            {ajuste.label}
            {ajuste.sensitive && (
              <span className="ml-2 rounded-pill bg-warning-bg px-2 py-0.5 align-middle text-[10.5px] font-bold uppercase tracking-[0.06em] text-warning">
                Sensible
              </span>
            )}
          </p>
          <p className="mt-1 text-[12.5px] leading-relaxed text-text-secondary">
            {ajuste.description}
          </p>
          <p className="mt-1 mono text-[11px] text-text-muted">{ajuste.key}</p>
        </div>

        <div className="flex shrink-0 items-center gap-2">
          {ajuste.type === "BOOLEANO" ? (
            <Toggle
              activo={valor === "true"}
              onCambio={(activo) => {
                const nuevo = String(activo);
                setValor(nuevo);
                if (ajuste.sensitive) setConfirmando(true);
                else guardar(nuevo);
              }}
              etiqueta={ajuste.label}
            />
          ) : ajuste.type === "OPCION" ? (
            <select
              value={valor}
              onChange={(e) => setValor(e.target.value)}
              aria-label={ajuste.label}
              className="h-11 rounded-base border border-border bg-surface px-3 text-[14px] focus-visible:shadow-focus"
            >
              {ajuste.options.map((o) => (
                <option key={o} value={o}>
                  {o}
                </option>
              ))}
            </select>
          ) : (
            <Campo
              type="number"
              value={valor}
              min={ajuste.min ?? undefined}
              max={ajuste.max ?? undefined}
              onChange={(e) => setValor(e.target.value)}
              aria-label={ajuste.label}
              className="w-28"
            />
          )}

          {cambiado && ajuste.type !== "BOOLEANO" && (
            <Boton variante="primario" onClick={intentar} disabled={guardando}>
              {guardando ? <Spinner /> : "Guardar"}
            </Boton>
          )}
        </div>
      </div>

      {error && (
        <div className="mt-3">
          <AvisoError mensaje={error} />
        </div>
      )}

      {confirmando && (
        <Modal
          titulo={`Cambiar «${ajuste.label}»`}
          onCerrar={() => {
            setConfirmando(false);
            setValor(ajuste.value);
            setEscrito("");
          }}
        >
          <p className="text-[14px] leading-relaxed text-text-secondary">
            Vas a cambiarlo de <strong className="mono">{ajuste.value}</strong> a{" "}
            <strong className="mono">{valor}</strong>. Escribe el valor nuevo para confirmar.
          </p>
          <Campo
            type="text"
            value={escrito}
            onChange={(e) => setEscrito(e.target.value)}
            placeholder={valor}
            aria-label="Confirma escribiendo el valor nuevo"
            className="mt-4"
          />
          <div className="mt-4 flex gap-2">
            <Boton
              variante="primario"
              disabled={escrito.trim() !== valor || guardando}
              onClick={() => guardar(valor)}
            >
              {guardando ? <Spinner /> : "Confirmar cambio"}
            </Boton>
            <Boton
              variante="fantasma"
              onClick={() => {
                setConfirmando(false);
                setValor(ajuste.value);
                setEscrito("");
              }}
            >
              Cancelar
            </Boton>
          </div>
        </Modal>
      )}
    </Tarjeta>
  );
}
