"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import { KeyRound, Lock, Mail, User } from "lucide-react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useState } from "react";
import { CambiarClave, useEtiquetaDeClave } from "@/components/CambiarClave";
import { MiCielo } from "@/components/gamificacion/MiCielo";
import { TarjetaDiagnostico } from "@/components/gamificacion/TarjetaDiagnostico";
import { MiFicha, QuienLoVe } from "@/components/gamificacion/MiFicha";
import { InvitacionAPracticar } from "@/components/InvitacionAPracticar";
import { PanelProgreso } from "@/components/PanelProgreso";
import { MisPuntosChip } from "@/components/Puntos";
import { Cargando, ErrorCarga } from "@/components/estados";
import { PhoneInput } from "@/components/PhoneInput";
import { EdicionEnPagina, useFormularioEditable } from "@/components/edicion/EdicionEnPagina";
import { Campo } from "@/components/ui";
import { apiFetch } from "@/lib/api/fetch";
import { meQueryKey } from "@/lib/auth/session";

type Cuenta = {
  fullName: string;
  email: string;
  whatsappPhone: string | null;
  role: string;
  photoUrl: string | null;
};

export default function CuentaPage() {
  const cuenta = useQuery({
    queryKey: ["me", "account"],
    queryFn: () => apiFetch<Cuenta>("/api/v1/me/account"),
  });

  if (cuenta.isPending) {
    return (
      <main className="mx-auto w-full max-w-md px-5 py-6">
        <Cargando filas={3} />
      </main>
    );
  }

  if (cuenta.isError) {
    return (
      <main className="mx-auto w-full max-w-md px-5 py-6">
        <ErrorCarga mensaje="No pudimos cargar tu cuenta." onReintentar={() => void cuenta.refetch()} />
      </main>
    );
  }

  return <FormularioCuenta inicial={cuenta.data} />;
}

type Seccion = "resumen" | "cielo" | "ficha";

/**
 * Dos secciones y no cinco (24/09/2026): «Si hay que cancelar» y «Preguntas» salieron del perfil
 * —las reglas se explican al cancelar y las preguntas viven en Ayuda—, y la ficha y los datos van
 * juntos, separados por quién ve cada cosa. Los enlaces viejos (`?seccion=datos` y los otros) caen
 * en la sección que los reemplaza.
 */
const SECCIONES: { clave: Seccion; label: string }[] = [
  { clave: "resumen", label: "Resumen" },
  { clave: "ficha", label: "Mi ficha y mis datos" },
];

const ANTIGUAS: Record<string, Seccion> = { datos: "ficha", cancelar: "resumen", preguntas: "resumen" };

/**
 * La sección vive en la URL y no en un estado local, para que un enlace pueda apuntar a una
 * concreta —un correo de logro a «cielo», por ejemplo— y para que atrás funcione como se espera.
 */
function SubNav({ actual }: { actual: Seccion }) {
  return (
    <nav className="mt-5 -mx-5 overflow-x-auto px-5 lg:mx-0 lg:px-0">
      <ul className="flex w-max gap-1.5 lg:w-auto lg:flex-wrap">
        {SECCIONES.map(({ clave, label }) => {
          const activa = actual === clave || (clave === "resumen" && actual === "cielo");
          return (
            <li key={clave}>
              <Link
                href={`/cuenta?seccion=${clave}`}
                scroll={false}
                aria-current={activa ? "page" : undefined}
                className={`inline-flex h-9 items-center rounded-pill px-3.5 text-[13px] font-semibold transition-colors focus-visible:shadow-focus ${
                  activa
                    ? "bg-primary text-on-primary"
                    : "bg-surface-sunken text-text-secondary hover:bg-border/60 hover:text-text"
                }`}
              >
                {label}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}

function FormularioCuenta({ inicial }: { inicial: Cuenta }) {
  const [cambiandoClave, setCambiandoClave] = useState(false);

  const params = useSearchParams();
  const pedida = params.get("seccion") ?? "resumen";
  const seccion: Seccion =
    SECCIONES.some((s) => s.clave === pedida) || pedida === "cielo" ? (pedida as Seccion) : (ANTIGUAS[pedida] ?? "resumen");

  return (
    <main className="mx-auto w-full max-w-md px-5 py-6 lg:max-w-5xl lg:px-12 lg:py-8">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h1 className="font-display text-h1 font-bold">Mi perfil</h1>
        <MisPuntosChip />
      </div>
      <p className="mt-1 text-[14px] text-text-secondary">
        Cómo vas y los datos con los que coordinas tus clases.
      </p>

      {/*
        Cinco cosas distintas vivían apiladas en una sola pantalla: el progreso, tu cielo, la ficha
        que lee tu profesor, tus datos de contacto y las reglas de cancelación. Lo que se venía a
        mirar quedaba mezclado con lo que se cambia una vez al año, y había que recorrerlo entero
        para encontrar cualquier cosa. Ahora son secciones, y la dirección las recuerda —
        `?seccion=` — para poder enlazar a una concreta desde un correo o una notificación.
      */}
      <SubNav actual={seccion} />

      {/* El diagnóstico va al final: es una foto del día que se hizo, no lo que se viene a mirar. */}
      {seccion === "resumen" && (
        <>
          <PanelProgreso />
          <InvitacionAPracticar />
          <div className="mt-8">
            <MiCielo />
          </div>
          <div className="mt-8">
            <TarjetaDiagnostico />
          </div>
        </>
      )}

      {seccion === "cielo" && <MiCielo />}

      {/* La ficha y los datos se editan directo, y una sola barra guarda los dos (24/09/2026). */}
      <EdicionEnPagina>
        {seccion === "ficha" && <MiFicha />}

        <div className={seccion === "ficha" ? "" : "hidden"}>
          <QuienLoVe
            icono={<Lock size={15} strokeWidth={2} />}
            titulo="Solo para ti"
            texto="Tu correo, tu WhatsApp y tu contraseña. No los ve ningún profesor ni otro estudiante."
          />
          <MisDatos inicial={inicial} onCambiarClave={() => setCambiandoClave(true)} />
        </div>
      </EdicionEnPagina>

      {cambiandoClave && <CambiarClave onCerrar={() => setCambiandoClave(false)} />}
    </main>
  );
}

/** Nombre y WhatsApp: se editan directo y se guardan con la barra de la página. */
function MisDatos({ inicial, onCambiarClave }: { inicial: Cuenta; onCambiarClave: () => void }) {
  const queryClient = useQueryClient();
  const [nombre, setNombre] = useState(inicial.fullName);
  const [telefono, setTelefono] = useState(inicial.whatsappPhone ?? "");
  const etiquetaDeClave = useEtiquetaDeClave();

  const sucio = nombre.trim() !== inicial.fullName.trim() || telefono.trim() !== (inicial.whatsappPhone ?? "").trim();

  useFormularioEditable(
    sucio,
    async () => {
      if (!nombre.trim()) throw new Error("Tu nombre no puede quedar vacío.");
      const actualizada = await apiFetch<Cuenta>("/api/v1/me/account", {
        method: "PUT",
        body: { fullName: nombre.trim(), whatsappPhone: telefono.trim() || undefined },
      });
      queryClient.setQueryData(["me", "account"], actualizada);
      setNombre(actualizada.fullName);
      setTelefono(actualizada.whatsappPhone ?? "");
      // El nombre se ve en el header/avatar: refrescamos la sesión para que se actualice.
      void queryClient.invalidateQueries({ queryKey: meQueryKey });
    },
    () => {
      setNombre(inicial.fullName);
      setTelefono(inicial.whatsappPhone ?? "");
    },
  );

  return (
    <div className="mt-3 rounded-card border border-border bg-surface-raised p-5">
      <label className="block text-[12px] font-bold uppercase tracking-[0.04em] text-text-secondary" htmlFor="nombre">
        Nombre completo
      </label>
      <Campo
        id="nombre"
        type="text"
        maxLength={150}
        value={nombre}
        onChange={(event) => setNombre(event.target.value)}
        icono={<User size={18} strokeWidth={1.75} />}
        className="mt-1.5"
      />
      <p className="mt-1.5 text-[12px] text-text-muted">Tu nombre sí lo ven tus profesores.</p>

      <label className="mt-4 block text-[12px] font-bold uppercase tracking-[0.04em] text-text-secondary" htmlFor="telefono">
        WhatsApp <span className="font-semibold normal-case text-text-muted">(opcional)</span>
      </label>
      <PhoneInput id="telefono" value={telefono} onChange={setTelefono} className="mt-1.5" />
      <p className="mt-1.5 text-[12px] text-text-muted">
        Solo lo usa el equipo de Orión si necesita avisarte algo de una clase.
      </p>

      {/* Correo y rol se muestran, no se editan. */}
      <div className="mt-5 flex items-center gap-2.5 rounded-base bg-surface-sunken px-4 py-3">
        <Mail size={16} strokeWidth={1.75} className="shrink-0 text-text-muted" />
        <span className="truncate text-[13px] text-text-secondary">{inicial.email}</span>
      </div>

      <button
        type="button"
        onClick={onCambiarClave}
        className="mt-3 flex w-full items-center gap-2.5 rounded-base border-[1.5px] border-border px-4 py-3 text-left text-[13.5px] font-semibold text-text transition-colors hover:bg-surface-sunken focus-visible:shadow-focus"
      >
        <KeyRound size={16} strokeWidth={1.75} className="text-text-secondary" />
        {etiquetaDeClave}
      </button>
    </div>
  );
}
