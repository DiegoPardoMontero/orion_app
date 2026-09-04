"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Check, Lock } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { AvisoError, Cargando, ErrorCarga } from "@/components/estados";
import { AvatarOrion } from "@/components/gamificacion/AvatarOrion";
import { Boton, Spinner } from "@/components/ui";
import { ApiError, apiFetch } from "@/lib/api/fetch";
import { useMe } from "@/lib/auth/session";
import type { Cosmetico, Engagement, FichaEstudiante } from "@/lib/gamificacion";

/**
 * El personalizador. Lo bloqueado se muestra a la vista con su condición legible: esconderlo
 * quitaría lo único que hace que valga la pena conseguirlo.
 */
export default function PersonalizarAvatarPage() {
  const queryClient = useQueryClient();
  const { data: me } = useMe();

  const cosmeticos = useQuery({
    queryKey: ["me", "cosmetics"],
    queryFn: () => apiFetch<Cosmetico[]>("/api/v1/me/cosmetics"),
  });
  const ficha = useQuery({
    queryKey: ["me", "student-profile"],
    queryFn: () => apiFetch<FichaEstudiante>("/api/v1/me/student-profile"),
  });
  const resumen = useQuery({
    queryKey: ["me", "engagement"],
    queryFn: () => apiFetch<Engagement>("/api/v1/me/engagement"),
  });

  // `null` significa «no ha tocado nada»: entonces manda lo que ya tiene puesto. Copiar la ficha a
  // estado local en cuanto llega parecería más simple, pero obliga a un efecto que sincroniza dos
  // fuentes del mismo dato — y a decidir qué pasa cuando la ficha se recarga con la selección a
  // medias. Derivarlo no tiene ese problema.
  const [frame, setFrame] = useState<string | null>(null);
  const [palette, setPalette] = useState<string | null>(null);
  const [sky, setSky] = useState<string | null>(null);
  const [accesorios, setAccesorios] = useState<{ zone: string; accessoryCode: string }[] | null>(null);

  const puestos = accesorios ?? ficha.data?.accessories ?? [];
  const marcoPuesto = frame ?? ficha.data?.frameCode ?? "trazo";
  const paletaPuesta = palette ?? ficha.data?.paletteCode ?? "trazo";
  const cieloPuesto = sky ?? ficha.data?.skyCode ?? "crema";

  const guardar = useMutation({
    mutationFn: () =>
      apiFetch("/api/v1/me/cosmetics", {
        method: "PUT",
        body: {
          frameCode: marcoPuesto,
          paletteCode: paletaPuesta,
          skyCode: cieloPuesto,
          accessories: puestos,
        },
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["me", "student-profile"] });
      void queryClient.invalidateQueries({ queryKey: ["me", "cosmetics"] });
    },
  });

  if (cosmeticos.isPending || ficha.isPending || resumen.isPending) {
    return (
      <main className="mx-auto w-full max-w-md px-5 py-6 lg:max-w-3xl lg:px-12">
        <Cargando filas={3} />
      </main>
    );
  }
  if (cosmeticos.isError || !cosmeticos.data || !ficha.data || !resumen.data) {
    return (
      <main className="mx-auto w-full max-w-md px-5 py-6 lg:max-w-3xl lg:px-12">
        <ErrorCarga mensaje="No pudimos cargar tus piezas." onReintentar={() => void cosmeticos.refetch()} />
      </main>
    );
  }

  const error = guardar.error instanceof ApiError ? guardar.error.message : null;
  const de = (kind: Cosmetico["kind"]) => cosmeticos.data.filter((c) => c.kind === kind);

  function alternarAccesorio(pieza: Cosmetico) {
    if (!pieza.zone) return;
    setAccesorios((actuales) => {
      const base = actuales ?? puestos;
      const puesto = base.find((a) => a.zone === pieza.zone);
      if (puesto?.accessoryCode === pieza.code) {
        return base.filter((a) => a.zone !== pieza.zone);
      }
      return [
        ...base.filter((a) => a.zone !== pieza.zone),
        { zone: pieza.zone!, accessoryCode: pieza.code },
      ];
    });
  }

  return (
    <main className="mx-auto w-full max-w-md px-5 py-6 lg:max-w-3xl lg:px-12 lg:py-8">
      <h1 className="font-display text-h1 font-bold">Tu avatar</h1>
      <p className="mt-1 text-[14px] text-text-secondary">
        Tu foto no cambia: lo que cambia es lo que la rodea.
      </p>

      <div className="mt-6 flex justify-center">
        <AvatarOrion
          nombre={me?.fullName ?? ""}
          fotoUrl={ficha.data.photoUrl}
          frameCode={marcoPuesto}
          paletteCode={paletaPuesta}
          skyCode={cieloPuesto}
          sealLevel={resumen.data.sealLevel}
          accesorios={puestos}
          size={150}
        />
      </div>

      <Grupo titulo="Órbita" piezas={de("FRAME")} elegido={marcoPuesto} onElegir={setFrame} />
      <Grupo titulo="Paleta" piezas={de("PALETTE")} elegido={paletaPuesta} onElegir={setPalette} />
      <Grupo titulo="Cielo" piezas={de("SKY")} elegido={cieloPuesto} onElegir={setSky} />
      <Grupo
        titulo="Accesorios"
        piezas={de("ACCESSORY")}
        elegido={null}
        puestos={puestos.map((a) => a.accessoryCode)}
        onElegir={(code) => {
          const pieza = de("ACCESSORY").find((c) => c.code === code);
          if (pieza) alternarAccesorio(pieza);
        }}
      />

      {error && (
        <div className="mt-4">
          <AvisoError mensaje={error} />
        </div>
      )}

      <div className="mt-6 flex flex-wrap gap-2.5">
        <Boton onClick={() => guardar.mutate()} disabled={guardar.isPending} className="h-11">
          {guardar.isPending ? <Spinner /> : <Check size={16} strokeWidth={2} />}
          Guardar
        </Boton>
        <Link
          href="/logros"
          className="inline-flex min-h-11 items-center rounded-pill border-[1.5px] border-border px-5 text-[14px] font-bold text-text transition-colors hover:bg-surface-sunken focus-visible:shadow-focus"
        >
          Volver al cielo
        </Link>
        {guardar.isSuccess && !guardar.isPending && (
          <span className="inline-flex items-center gap-1.5 text-[13px] font-semibold text-success">
            <Check size={15} strokeWidth={2.4} />
            Guardado
          </span>
        )}
      </div>
    </main>
  );
}

function Grupo({
  titulo,
  piezas,
  elegido,
  puestos,
  onElegir,
}: {
  titulo: string;
  piezas: Cosmetico[];
  elegido: string | null;
  puestos?: string[];
  onElegir: (code: string) => void;
}) {
  if (piezas.length === 0) return null;

  return (
    <section className="mt-6">
      <h2 className="text-[12px] font-bold uppercase tracking-[0.06em] text-text-secondary">
        {titulo}
      </h2>
      <ul className="mt-2.5 grid grid-cols-2 gap-2.5 sm:grid-cols-3">
        {piezas.map((pieza) => {
          const activo = elegido === pieza.code || puestos?.includes(pieza.code);
          return (
            <li key={`${pieza.kind}-${pieza.code}`}>
              <button
                type="button"
                disabled={!pieza.unlocked}
                aria-pressed={!!activo}
                onClick={() => onElegir(pieza.code)}
                className={`w-full rounded-card border-[1.5px] p-3 text-left transition-colors ${
                  activo
                    ? "border-primary bg-primary-soft/50"
                    : pieza.unlocked
                      ? "border-border bg-surface-raised hover:bg-surface-sunken"
                      : "border-border bg-surface-sunken/60"
                }`}
              >
                <span className="flex items-center gap-2">
                  {!pieza.unlocked && (
                    <Lock size={13} strokeWidth={2.2} className="shrink-0 text-text-muted" />
                  )}
                  <span
                    className={`text-[13.5px] font-bold ${pieza.unlocked ? "text-text" : "text-text-muted"}`}
                  >
                    {pieza.name}
                  </span>
                  {activo && <Check size={14} strokeWidth={2.6} className="ml-auto text-primary-strong" />}
                </span>
                {/* La condición en texto: se lee «Con diez clases», no un código de logro. */}
                <span className="mt-1 block text-[11.5px] leading-tight text-text-muted">
                  {pieza.unlockCondition}
                </span>
              </button>
            </li>
          );
        })}
      </ul>
    </section>
  );
}
