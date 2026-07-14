"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { Avatar } from "@/components/Avatar";
import { Cargando, ErrorCarga, Vacio } from "@/components/estados";
import { Modal } from "@/components/Modal";
import { ApiError, apiFetch } from "@/lib/api/fetch";
import type { MyBookingResponse } from "@/lib/api/types";
import { useMe } from "@/lib/auth/session";
import { colorEstado, etiquetaEstado } from "@/lib/estados-clase";
import { fechaYRango } from "@/lib/format";
import { linkWhatsapp } from "@/lib/whatsapp";

type Scope = "upcoming" | "past";

export default function MisClasesPage() {
  const [scope, setScope] = useState<Scope>("upcoming");
  const { data: me } = useMe();

  const { data, isPending, isError, refetch } = useQuery({
    queryKey: ["me", "bookings", scope],
    queryFn: () => apiFetch<MyBookingResponse[]>(`/api/v1/me/bookings?scope=${scope}`),
  });

  const esProfesor = me?.role === "PROFESSOR";

  return (
    <main className="mx-auto max-w-md p-4">
      <Suspense fallback={null}>
        <BannerReserva />
      </Suspense>

      <h1 className="text-xl font-semibold">Mis clases</h1>

      <div className="mt-3 flex overflow-hidden rounded-orion border border-line">
        {(["upcoming", "past"] as const).map((valor) => (
          <button
            key={valor}
            type="button"
            onClick={() => setScope(valor)}
            className={`flex-1 py-1.5 text-xs ${
              scope === valor ? "bg-accent-soft font-semibold text-accent-ink" : "text-ink-soft"
            }`}
          >
            {valor === "upcoming" ? "Próximas" : "Pasadas"}
          </button>
        ))}
      </div>

      <div className="mt-3.5">
        {isPending && <Cargando />}

        {isError && (
          <ErrorCarga mensaje="No pudimos cargar tus clases." onReintentar={() => void refetch()} />
        )}

        {data?.length === 0 &&
          (scope === "upcoming" ? (
            <Vacio
              titulo="Aún no tienes clases"
              texto={
                esProfesor
                  ? "En cuanto un estudiante reserve contigo, la verás aquí."
                  : "Explora los profesores y reserva la primera."
              }
              accion={
                esProfesor ? undefined : (
                  <Link
                    href="/profesores"
                    className="inline-block rounded-orion bg-accent px-4 py-2 text-sm font-semibold text-white"
                  >
                    Ver profesores
                  </Link>
                )
              }
            />
          ) : (
            <Vacio titulo="Nada por aquí todavía" texto="Tus clases pasadas aparecerán en esta pestaña." />
          ))}

        <ul className="space-y-2.5">
          {data?.map((clase) => (
            <TarjetaClase
              key={clase.id}
              clase={clase}
              scope={scope}
              esProfesor={esProfesor}
              miNombre={me?.fullName ?? ""}
            />
          ))}
        </ul>
      </div>
    </main>
  );
}

function TarjetaClase({
  clase,
  scope,
  esProfesor,
  miNombre,
}: {
  clase: MyBookingResponse;
  scope: Scope;
  esProfesor: boolean;
  miNombre: string;
}) {
  const [cancelando, setCancelando] = useState(false);
  const [registrando, setRegistrando] = useState(false);

  const contraparte = clase.counterpart;
  const nombreContraparte = contraparte?.fullName ?? "";
  const virtual = clase.modality === "VIRTUAL";

  const whatsapp = linkWhatsapp({
    telefono: contraparte?.whatsappPhone,
    contraparte: nombreContraparte,
    yo: miNombre,
    inicioIso: clase.startsAt!,
  });

  // Una clase futura y confirmada que NO se puede cancelar solo puede ser por la regla de 24 h.
  const dentroDeLas24 =
    scope === "upcoming" && clase.status === "CONFIRMED" && !clase.canCancel;

  // El profesor registra asistencia de lo que ya ocurrió y sigue confirmado.
  const puedeRegistrar = esProfesor && scope === "past" && clase.status === "CONFIRMED";

  return (
    <li className="rounded-card border border-line bg-card p-3">
      <div className="flex items-center justify-between gap-2">
        <span className="text-[13px] font-semibold">
          {fechaYRango(clase.startsAt!, clase.endsAt!)}
        </span>
        <span
          className={`rounded-orion px-2 py-0.5 text-[11px] ${
            virtual ? "bg-success-soft text-success" : "bg-warning-soft text-warning"
          }`}
        >
          {virtual ? "Virtual" : "Presencial"}
        </span>
      </div>

      <div className="mt-2 flex items-center gap-2">
        <Avatar nombre={nombreContraparte} size="sm" />
        <span className="text-[13px]">
          {esProfesor ? nombreContraparte : `Prof. ${nombreContraparte}`}
        </span>
      </div>

      {clase.locationNote && (
        <p className="mt-1 ml-10 text-xs text-ink-muted">Lugar: {clase.locationNote}</p>
      )}

      {scope === "past" && clase.status !== "CONFIRMED" && (
        <span
          className={`mt-2 inline-block rounded-orion px-2 py-0.5 text-[11px] ${colorEstado(clase.status)}`}
        >
          {etiquetaEstado(clase.status)}
        </span>
      )}

      <div className="mt-2.5 flex gap-2">
        {whatsapp && (
          <a
            href={whatsapp}
            target="_blank"
            rel="noopener noreferrer"
            className="flex-1 rounded-orion border border-line py-1.5 text-center text-xs text-ink"
          >
            WhatsApp
          </a>
        )}

        {scope === "upcoming" && clase.status === "CONFIRMED" && (
          <button
            type="button"
            disabled={!clase.canCancel}
            onClick={() => setCancelando(true)}
            className={`flex-1 rounded-orion py-1.5 text-xs ${
              clase.canCancel
                ? "border border-line text-ink"
                : "border border-transparent text-ink-disabled"
            }`}
          >
            Cancelar
          </button>
        )}

        {puedeRegistrar && (
          <button
            type="button"
            onClick={() => setRegistrando(true)}
            className="flex-1 rounded-orion bg-accent py-1.5 text-xs font-semibold text-white"
          >
            Registrar asistencia
          </button>
        )}
      </div>

      {/* El texto institucional lo dicta el servidor con canCancel; aquí solo se explica. */}
      {dentroDeLas24 && (
        <p className="mt-2 text-center text-[11px] text-ink-muted">
          Faltan menos de 24 h — la clase se considera impartida
        </p>
      )}

      {cancelando && <ModalCancelar clase={clase} onCerrar={() => setCancelando(false)} />}
      {registrando && <ModalAsistencia clase={clase} onCerrar={() => setRegistrando(false)} />}
    </li>
  );
}

function ModalCancelar({ clase, onCerrar }: { clase: MyBookingResponse; onCerrar: () => void }) {
  const queryClient = useQueryClient();
  const [motivo, setMotivo] = useState("");

  const cancelar = useMutation({
    mutationFn: () =>
      apiFetch(`/api/v1/bookings/${clase.id}/cancel`, {
        method: "POST",
        body: { reason: motivo.trim() || undefined },
      }),
    onSuccess: () => {
      // La clase cambia de pestaña y el cupo vuelve a la agenda del profesor: refrescamos ambos.
      void queryClient.invalidateQueries({ queryKey: ["me", "bookings"] });
      void queryClient.invalidateQueries({ queryKey: ["slots"] });
      onCerrar();
    },
  });

  const error = cancelar.error instanceof ApiError ? cancelar.error.message : null;

  return (
    <Modal titulo="¿Cancelar esta clase?" onCerrar={onCerrar}>
      <p className="text-sm text-ink-soft">
        {fechaYRango(clase.startsAt!, clase.endsAt!)} con {clase.counterpart?.fullName}.
      </p>

      <label className="mt-3 block text-xs font-semibold text-ink-soft" htmlFor="motivo">
        Motivo (opcional)
      </label>
      <input
        id="motivo"
        type="text"
        maxLength={300}
        value={motivo}
        onChange={(event) => setMotivo(event.target.value)}
        className="mt-1 w-full rounded-orion border border-line bg-card px-3 py-2 text-sm outline-none focus:border-accent"
      />

      {error && (
        <p className="mt-3 rounded-orion bg-danger-soft px-3 py-2 text-sm text-danger">{error}</p>
      )}

      <div className="mt-4 flex gap-2">
        <button
          type="button"
          onClick={onCerrar}
          className="flex-1 rounded-orion border border-line py-2 text-sm text-ink-soft"
        >
          Volver
        </button>
        <button
          type="button"
          disabled={cancelar.isPending}
          onClick={() => cancelar.mutate()}
          className="flex-1 rounded-orion bg-accent py-2 text-sm font-semibold text-white disabled:opacity-60"
        >
          {cancelar.isPending ? "Cancelando…" : "Sí, cancelar"}
        </button>
      </div>
    </Modal>
  );
}

function ModalAsistencia({ clase, onCerrar }: { clase: MyBookingResponse; onCerrar: () => void }) {
  const queryClient = useQueryClient();
  const [notas, setNotas] = useState("");

  const registrar = useMutation({
    mutationFn: (present: boolean) =>
      apiFetch(`/api/v1/bookings/${clase.id}/attendance`, {
        method: "POST",
        body: { present, notes: notas.trim() || undefined },
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["me", "bookings"] });
      onCerrar();
    },
  });

  const error = registrar.error instanceof ApiError ? registrar.error.message : null;

  return (
    <Modal titulo="Registrar asistencia" onCerrar={onCerrar}>
      <p className="text-sm text-ink-soft">
        {fechaYRango(clase.startsAt!, clase.endsAt!)} con {clase.counterpart?.fullName}.
      </p>

      <label className="mt-3 block text-xs font-semibold text-ink-soft" htmlFor="notas">
        Notas (opcional)
      </label>
      <input
        id="notas"
        type="text"
        maxLength={500}
        value={notas}
        onChange={(event) => setNotas(event.target.value)}
        className="mt-1 w-full rounded-orion border border-line bg-card px-3 py-2 text-sm outline-none focus:border-accent"
      />

      {error && (
        <p className="mt-3 rounded-orion bg-danger-soft px-3 py-2 text-sm text-danger">{error}</p>
      )}

      <div className="mt-4 flex gap-2">
        <button
          type="button"
          disabled={registrar.isPending}
          onClick={() => registrar.mutate(false)}
          className="flex-1 rounded-orion border border-line py-2 text-sm text-ink-soft disabled:opacity-60"
        >
          No asistió
        </button>
        <button
          type="button"
          disabled={registrar.isPending}
          onClick={() => registrar.mutate(true)}
          className="flex-1 rounded-orion bg-accent py-2 text-sm font-semibold text-white disabled:opacity-60"
        >
          Asistió
        </button>
      </div>
    </Modal>
  );
}

function BannerReserva() {
  const params = useSearchParams();
  if (params.get("reservada") !== "1") return null;

  return (
    <p className="mb-3.5 rounded-card bg-success-soft px-3 py-2.5 text-sm text-success">
      ¡Clase reservada! Te enviamos la confirmación al correo.
    </p>
  );
}
