"use client";

import { useState } from "react";
import Link from "next/link";
import { useMutation, useQuery } from "@tanstack/react-query";
import { AlertTriangle, CheckCircle2, ExternalLink, XCircle } from "lucide-react";
import { apiFetch, ApiError } from "@/lib/api/fetch";
import { AvisoError, Cargando, ErrorCarga } from "@/components/estados";
import { Boton, Campo, Tarjeta } from "@/components/ui";
import { correosGuardados, EnsayoDelActa, guardarCorreos } from "./EnsayoDelActa";

type Integracion = {
  nombre: string;
  configurada: boolean;
  siFalta: string;
  detalle: string | null;
  variables: string[];
};

type ClaseDePrueba = {
  bookingId: string;
  startsAt: string;
  endsAt: string;
  aula: string;
};

/**
 * Sistema: qué integraciones están vivas en este despliegue, y los ensayos del aula y del acta.
 *
 * <p>Nace de un problema concreto: si una variable de Railway falta o está mal escrita, Orión
 * arranca igual y responde 200 en el health, pero la integración se apaga en silencio. Desde fuera
 * no se distingue una aplicación sana de una con el aula muerta, y la diferencia aparecía cuando se
 * quejaba un usuario — el peor momento y el peor mensajero. Esto lo convierte en algo que se mira.
 *
 * <p>La clase de prueba está aquí y no en «Reservas» por la misma razón: es una herramienta de
 * diagnóstico, no una forma de agendar. No cobra, no manda correos y no cuenta en las ganancias.
 */
export default function SistemaPage() {
  const estado = useQuery({
    queryKey: ["admin", "sistema"],
    queryFn: () => apiFetch<{ integraciones: Integracion[] }>("/api/v1/admin/system/status"),
  });

  if (estado.isLoading) return <Cargando />;
  if (estado.isError || !estado.data) {
    return (
      <main className="mx-auto w-full max-w-3xl px-5 py-6">
        <ErrorCarga mensaje="No pudimos leer el estado del sistema." onReintentar={() => void estado.refetch()} />
      </main>
    );
  }

  const apagadas = estado.data.integraciones.filter((i) => !i.configurada).length;

  return (
    <main className="mx-auto w-full max-w-3xl px-5 py-6 lg:py-8">
      <p className="text-[12px] font-bold uppercase tracking-[0.1em] text-primary-strong">Administración</p>
      <h1 className="mt-2 font-display text-h1 font-bold">Sistema</h1>
      <p className="mt-1.5 text-[13.5px] leading-relaxed text-text-secondary">
        Qué integraciones están configuradas en este despliegue. Si una está apagada, la aplicación
        arranca igual y falla solo cuando alguien la usa: por eso se mira aquí y no en los logs.
      </p>

      {apagadas > 0 && (
        <div className="mt-4 flex items-start gap-2.5 rounded-card bg-accent-peach-soft p-4 text-[13px] text-[#8a5a33]">
          <AlertTriangle size={17} strokeWidth={2} className="mt-0.5 shrink-0" />
          <p>
            {apagadas === 1 ? "Hay 1 integración apagada" : `Hay ${apagadas} integraciones apagadas`}.
            Revisa sus variables en Railway.
          </p>
        </div>
      )}

      <div className="mt-5 grid gap-3">
        {estado.data.integraciones.map((i) => (
          <Tarjeta key={i.nombre}>
            <div className="flex items-start gap-3">
              {i.configurada ? (
                <CheckCircle2 size={19} strokeWidth={2.2} className="mt-0.5 shrink-0 text-success" />
              ) : (
                <XCircle size={19} strokeWidth={2.2} className="mt-0.5 shrink-0 text-error" />
              )}
              <div className="min-w-0 flex-1">
                <p className="text-[14px] font-bold text-text">{i.nombre}</p>
                <p className="mt-0.5 text-[13px] text-text-secondary">
                  {i.configurada ? (i.detalle ?? "Configurada.") : i.siFalta}
                </p>
                <p className="mt-1.5 flex flex-wrap gap-1.5">
                  {i.variables.map((v) => (
                    <code
                      key={v}
                      className="rounded-base bg-surface-sunken px-1.5 py-0.5 text-[11.5px] text-text-muted"
                    >
                      {v}
                    </code>
                  ))}
                </p>
              </div>
            </div>
          </Tarjeta>
        ))}
      </div>

      <CorreoDePrueba />
      <EnsayoDelAula />
      <EnsayoDelActa />
    </main>
  );
}

type ResultadoCorreo = { enviado: boolean; para: string; via: string; detalle: string };

/**
 * Un correo de verdad por el transporte de verdad (24/09/2026). «Configurada» arriba solo dice que
 * la variable existe; que el correo llegue a Gmail —y no a spam— solo se sabe mandando uno.
 */
function CorreoDePrueba() {
  const [para, setPara] = useState("");
  const enviar = useMutation({
    mutationFn: () =>
      apiFetch<ResultadoCorreo>("/api/v1/admin/system/test-email", {
        method: "POST",
        body: para.trim() ? { to: para.trim() } : {},
      }),
  });

  return (
    <section className="mt-8">
      <h2 className="font-display text-h3 font-bold">Correo de prueba</h2>
      <p className="mt-1 text-[13px] leading-relaxed text-text-secondary">
        Sale por el mismo camino y con la misma plantilla que los correos de reservas, recordatorios y
        soporte. Si no escribes una dirección, llega a la tuya.
      </p>
      <Tarjeta className="mt-3">
        <label className="block">
          <span className="text-[12.5px] font-bold text-text-secondary">Enviar a (opcional)</span>
          <Campo
            type="email"
            value={para}
            onChange={(e) => setPara(e.target.value)}
            placeholder="tu correo de Gmail, por ejemplo"
            className="mt-1.5"
          />
        </label>
        {enviar.error instanceof ApiError && (
          <div className="mt-3">
            <AvisoError mensaje={enviar.error.message} />
          </div>
        )}
        <Boton variante="primario" className="mt-4" disabled={enviar.isPending} onClick={() => enviar.mutate()}>
          {enviar.isPending ? "Enviando…" : "Enviar correo de prueba"}
        </Boton>
        {enviar.data && (
          <div className={`mt-4 rounded-card p-4 ${enviar.data.enviado ? "bg-success-bg" : "bg-error-bg"}`}>
            <p className={`text-[13.5px] font-bold ${enviar.data.enviado ? "text-success" : "text-error"}`}>
              {enviar.data.enviado ? `Enviado a ${enviar.data.para}` : `No salió hacia ${enviar.data.para}`}
            </p>
            <p className="mt-1 text-[13px] text-text-secondary">
              {enviar.data.detalle} <span className="text-text-muted">· vía {enviar.data.via}</span>
            </p>
          </div>
        )}
      </Tarjeta>
    </section>
  );
}

/**
 * Ensayar el aula sin pasar por la pasarela.
 *
 * <p>Probar la videollamada exigía reservar, pagar con Wompi y coordinar a dos personas, y el
 * resultado práctico era que no se probaba. Esto la deja en un clic — y hacen falta dos sesiones
 * distintas, porque lo que hay que ver es que el profesor entra como anfitrión y el estudiante no.
 */
function EnsayoDelAula() {
  const [estudiante, setEstudiante] = useState(() => correosGuardados().estudiante);
  const [profesor, setProfesor] = useState(() => correosGuardados().profesor);
  const [cuando, setCuando] = useState(() => {
    // Por defecto, ahora mismo: la sala abre 10 minutos antes, así que se entra de inmediato.
    const d = new Date(Date.now() - new Date().getTimezoneOffset() * 60000);
    return d.toISOString().slice(0, 16);
  });

  const crear = useMutation({
    mutationFn: () =>
      apiFetch<ClaseDePrueba>("/api/v1/admin/system/test-class", {
        method: "POST",
        body: {
          studentEmail: estudiante.trim(),
          professorEmail: profesor.trim(),
          startsAt: cuando,
        },
      }),
    onSuccess: () => guardarCorreos(estudiante, profesor),
  });

  return (
    <section className="mt-8">
      <h2 className="font-display text-h3 font-bold">Ensayar el aula</h2>
      <p className="mt-1 text-[13px] leading-relaxed text-text-secondary">
        Crea una clase confirmada entre dos cuentas para probar la videollamada. No cobra, no manda
        correos y no cuenta en las ganancias de nadie: queda marcada como ensayo.
      </p>

      <Tarjeta className="mt-3">
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="block">
            <span className="text-[12.5px] font-bold text-text-secondary">Correo del estudiante</span>
            <Campo
              type="email"
              value={estudiante}
              onChange={(e) => setEstudiante(e.target.value)}
              placeholder="estudiante@correo.com"
              className="mt-1.5"
            />
          </label>
          <label className="block">
            <span className="text-[12.5px] font-bold text-text-secondary">Correo del profesor</span>
            <Campo
              type="email"
              value={profesor}
              onChange={(e) => setProfesor(e.target.value)}
              placeholder="profesor@correo.com"
              className="mt-1.5"
            />
          </label>
        </div>

        <label className="mt-3 block">
          <span className="text-[12.5px] font-bold text-text-secondary">
            Hora de inicio (hora de Bogotá)
          </span>
          <Campo
            type="datetime-local"
            value={cuando}
            onChange={(e) => setCuando(e.target.value)}
            className="mt-1.5"
          />
        </label>

        {crear.error instanceof ApiError && (
          <div className="mt-3">
            <AvisoError mensaje={crear.error.message} />
          </div>
        )}

        <Boton
          variante="primario"
          className="mt-4"
          disabled={!estudiante.trim() || !profesor.trim() || crear.isPending}
          onClick={() => crear.mutate()}
        >
          {crear.isPending ? "Creando…" : "Crear el ensayo"}
        </Boton>

        {crear.data && (
          <div className="mt-4 rounded-card bg-success-bg p-4">
            <p className="text-[13.5px] font-bold text-success">Ensayo creado.</p>
            <p className="mt-1 text-[13px] text-text-secondary">
              {new Date(crear.data.startsAt).toLocaleString("es-CO", { timeZone: "America/Bogota" })}
              {" · "}
              La sala abre 10 minutos antes.
            </p>
            <Link
              href={crear.data.aula}
              className="mt-2.5 inline-flex items-center gap-1.5 text-[13px] font-bold text-primary-strong hover:underline"
            >
              Abrir el aula
              <ExternalLink size={13} strokeWidth={2.2} />
            </Link>
            <p className="mt-2.5 text-[12.5px] text-text-muted">
              Para ver quién modera hacen falta dos sesiones: entra con cada cuenta en un navegador
              distinto (o uno de ellos en ventana de incógnito).
            </p>
          </div>
        )}
      </Tarjeta>
    </section>
  );
}
