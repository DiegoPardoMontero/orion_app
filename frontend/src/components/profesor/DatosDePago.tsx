"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Landmark, ShieldCheck } from "lucide-react";
import { useState } from "react";
import { AvisoError, Cargando, ErrorCarga } from "@/components/estados";
import { Boton, BotonPrincipal, Campo, Segmento, Spinner } from "@/components/ui";
import { ApiError, apiFetch } from "@/lib/api/fetch";
import { useMe } from "@/lib/auth/session";
import { fechaLarga } from "@/lib/format";

type TipoLlave = "PHONE" | "ID_NUMBER" | "EMAIL" | "ALPHANUMERIC";
type TipoDocumento = "CC" | "CE" | "PPT" | "PAS";

/** Lo que devuelve el servidor: siempre enmascarado, también a su dueño. */
type Enmascarados = {
  keyType: TipoLlave;
  keyTypeLabel: string;
  maskedKey: string;
  documentType: TipoDocumento;
  maskedDocument: string;
  holderName: string;
  updatedAt: string;
};

export const DATOS_DE_PAGO_KEY = ["me", "payout-details"] as const;

const LLAVES: { valor: TipoLlave; etiqueta: string; placeholder: string; ayuda: string }[] = [
  { valor: "PHONE", etiqueta: "Celular", placeholder: "300 123 4567", ayuda: "Los 10 dígitos de tu celular." },
  { valor: "ID_NUMBER", etiqueta: "Cédula", placeholder: "1020304050", ayuda: "Tu número de documento, solo dígitos." },
  { valor: "EMAIL", etiqueta: "Correo", placeholder: "tu@correo.com", ayuda: "El correo que registraste como llave." },
  { valor: "ALPHANUMERIC", etiqueta: "Alfanumérica", placeholder: "@tullave", ayuda: "La que empieza por @." },
];

const DOCUMENTOS: { valor: TipoDocumento; etiqueta: string }[] = [
  { valor: "CC", etiqueta: "Cédula de ciudadanía" },
  { valor: "CE", etiqueta: "Cédula de extranjería" },
  { valor: "PPT", etiqueta: "Permiso por protección temporal" },
  { valor: "PAS", etiqueta: "Pasaporte" },
];

/** Los datos de pago del profe, en su sección privada del perfil. */
export function useDatosDePago(activo = true) {
  return useQuery({
    queryKey: DATOS_DE_PAGO_KEY,
    queryFn: () => apiFetch<{ details: Enmascarados | null }>("/api/v1/me/payout-details"),
    enabled: activo,
  });
}

/**
 * A dónde le paga Orión al profe (brief de liquidaciones, paso 2): su llave Bre-B, a su nombre. El
 * servidor nunca devuelve el dato completo, ni a su dueño: aquí se ve enmascarado, y para cambiarlo se
 * escribe de nuevo. Cada cambio le llega por correo.
 */
export function DatosDePago() {
  const datos = useDatosDePago();
  const [editando, setEditando] = useState(false);

  if (datos.isPending) return <Cargando filas={3} />;
  if (datos.isError) {
    return <ErrorCarga mensaje="No pudimos cargar tus datos de pago." onReintentar={() => void datos.refetch()} />;
  }

  const actuales = datos.data.details;
  return (
    <section className="mt-5 max-w-xl">
      <p className="text-[14px] leading-relaxed text-text-secondary">
        Orión recibe en tu nombre lo que pagan tus estudiantes y te lo entrega cada quincena, menos la comisión, por
        transferencia Bre-B a esta llave.
      </p>
      <p className="mt-3 flex items-start gap-2 rounded-base bg-accent-lavender-soft px-4 py-3 text-[13px] text-[#5e4a8a]">
        <ShieldCheck size={16} strokeWidth={1.9} className="mt-0.5 shrink-0" />
        La llave debe estar a tu nombre. Solo el equipo de Orión ve estos datos completos.
      </p>

      {actuales && !editando ? (
        <div className="mt-5 rounded-card border border-border bg-surface-raised p-5">
          <p className="flex items-center gap-2 text-[12px] font-bold uppercase tracking-[0.06em] text-text-muted">
            <Landmark size={15} strokeWidth={1.9} />
            Tus datos de pago
          </p>
          <dl className="mt-3 grid gap-2 text-[14px]">
            <Dato etiqueta={`Llave (${actuales.keyTypeLabel.toLowerCase()})`} valor={actuales.maskedKey} />
            <Dato etiqueta="Titular" valor={actuales.holderName} />
            <Dato etiqueta="Documento" valor={`${actuales.documentType} ${actuales.maskedDocument}`} />
          </dl>
          <p className="mt-3 text-[12px] text-text-muted">Actualizados el {fechaLarga(actuales.updatedAt)}.</p>
          <Boton variante="contorno" onClick={() => setEditando(true)} className="mt-4 h-11">
            Cambiar mis datos de pago
          </Boton>
        </div>
      ) : (
        <Formulario primeraVez={!actuales} onListo={() => setEditando(false)} onCancelar={actuales ? () => setEditando(false) : undefined} />
      )}
    </section>
  );
}

function Dato({ etiqueta, valor }: { etiqueta: string; valor: string }) {
  return (
    <div className="flex flex-wrap justify-between gap-x-4">
      <dt className="text-text-secondary">{etiqueta}</dt>
      <dd className="font-semibold text-text">{valor}</dd>
    </div>
  );
}

function Formulario({
  primeraVez,
  onListo,
  onCancelar,
}: {
  primeraVez: boolean;
  onListo: () => void;
  onCancelar?: () => void;
}) {
  const queryClient = useQueryClient();
  const { data: me } = useMe();
  const [tipo, setTipo] = useState<TipoLlave>("PHONE");
  const [llave, setLlave] = useState("");
  const [tipoDocumento, setTipoDocumento] = useState<TipoDocumento>("CC");
  const [documento, setDocumento] = useState("");
  // El titular es él mismo: la llave tiene que estar a su nombre. Se propone su nombre y lo corrige
  // si su banco lo tiene escrito distinto.
  const [titular, setTitular] = useState(primeraVez ? (me?.fullName ?? "") : "");

  const guardar = useMutation({
    mutationFn: () =>
      apiFetch<{ details: Enmascarados }>("/api/v1/me/payout-details", {
        method: "PUT",
        body: { keyType: tipo, key: llave, documentType: tipoDocumento, documentNumber: documento, holderName: titular },
      }),
    onSuccess: (respuesta) => {
      queryClient.setQueryData(DATOS_DE_PAGO_KEY, respuesta);
      onListo();
    },
  });

  const error = guardar.error instanceof ApiError ? guardar.error.message : null;
  const listo = llave.trim() !== "" && documento.trim() !== "" && titular.trim() !== "";
  const actual = LLAVES.find((l) => l.valor === tipo)!;

  return (
    <form
      className="mt-5 rounded-card border border-border bg-surface-raised p-5"
      onSubmit={(event) => {
        event.preventDefault();
        if (listo) guardar.mutate();
      }}
    >
      <p className="text-[12px] font-bold uppercase tracking-[0.04em] text-text-secondary">Tipo de llave Bre-B</p>
      <div className="mt-1.5">
        <Segmento<TipoLlave>
          valor={tipo}
          onCambio={setTipo}
          opciones={LLAVES.map((l) => ({ valor: l.valor, etiqueta: l.etiqueta }))}
        />
      </div>

      <label htmlFor="llave" className="mt-4 block text-[12px] font-bold uppercase tracking-[0.04em] text-text-secondary">
        Tu llave
      </label>
      <Campo
        id="llave"
        value={llave}
        onChange={(event) => setLlave(event.target.value)}
        placeholder={actual.placeholder}
        autoComplete="off"
        className="mt-1.5"
      />
      <p className="mt-1.5 text-[12px] text-text-muted">{actual.ayuda}</p>

      <label htmlFor="titular" className="mt-4 block text-[12px] font-bold uppercase tracking-[0.04em] text-text-secondary">
        Titular de la llave
      </label>
      <Campo id="titular" value={titular} onChange={(event) => setTitular(event.target.value)} className="mt-1.5" />
      <p className="mt-1.5 text-[12px] text-text-muted">Tu nombre, como aparece en tu banco.</p>

      <div className="mt-4 grid gap-3 sm:grid-cols-[1fr_1fr]">
        <div>
          <label htmlFor="tipo-documento" className="block text-[12px] font-bold uppercase tracking-[0.04em] text-text-secondary">
            Documento
          </label>
          <select
            id="tipo-documento"
            value={tipoDocumento}
            onChange={(event) => setTipoDocumento(event.target.value as TipoDocumento)}
            className="mt-1.5 h-[52px] w-full rounded-base border-[1.5px] border-border bg-surface-raised px-3.5 text-[15px] text-text focus:border-primary focus:shadow-focus focus:outline-none"
          >
            {DOCUMENTOS.map((d) => (
              <option key={d.valor} value={d.valor}>
                {d.etiqueta}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="numero-documento" className="block text-[12px] font-bold uppercase tracking-[0.04em] text-text-secondary">
            Número
          </label>
          <Campo
            id="numero-documento"
            value={documento}
            onChange={(event) => setDocumento(event.target.value)}
            inputMode={tipoDocumento === "PAS" ? "text" : "numeric"}
            autoComplete="off"
            className="mt-1.5"
          />
        </div>
      </div>

      {error && (
        <div className="mt-4">
          <AvisoError mensaje={error} />
        </div>
      )}

      <BotonPrincipal type="submit" disabled={!listo || guardar.isPending} className="mt-5">
        {guardar.isPending ? (
          <>
            <Spinner />
            Guardando…
          </>
        ) : primeraVez ? (
          "Guardar mis datos de pago"
        ) : (
          "Guardar el cambio"
        )}
      </BotonPrincipal>
      {onCancelar && (
        <Boton variante="fantasma" onClick={onCancelar} className="mt-2 w-full">
          Cancelar
        </Boton>
      )}
      <p className="mt-3 text-center text-[12px] text-text-muted">Te mandamos un correo cada vez que cambien.</p>
    </form>
  );
}
