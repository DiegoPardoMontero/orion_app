"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowRight, Check, GraduationCap, KeyRound, Mail, User } from "lucide-react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useState } from "react";
import { CambiarClave } from "@/components/CambiarClave";
import { CambiarFoto } from "@/components/CambiarFoto";
import { MiCielo } from "@/components/gamificacion/MiCielo";
import { MiFicha } from "@/components/gamificacion/MiFicha";
import { PanelProgreso } from "@/components/PanelProgreso";
import { PoliticaCancelacion } from "@/components/PoliticaCancelacion";
import { PreguntasFrecuentes } from "@/components/PreguntasFrecuentes";
import { AvisoError, Cargando, ErrorCarga } from "@/components/estados";
import { PhoneInput } from "@/components/PhoneInput";
import { BotonPrincipal, Campo } from "@/components/ui";
import { ApiError, apiFetch } from "@/lib/api/fetch";
import { useMiAplicacion } from "@/lib/aplicacion";
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

type Seccion = "resumen" | "cielo" | "ficha" | "datos" | "cancelar" | "preguntas";

const SECCIONES: { clave: Seccion; label: string }[] = [
  { clave: "resumen", label: "Resumen" },
  { clave: "ficha", label: "Mi ficha" },
  { clave: "datos", label: "Mis datos" },
  { clave: "cancelar", label: "Si hay que cancelar" },
  { clave: "preguntas", label: "Preguntas" },
];

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
  const queryClient = useQueryClient();

  const [nombre, setNombre] = useState(inicial.fullName);
  const [telefono, setTelefono] = useState(inicial.whatsappPhone ?? "");
  const [guardado, setGuardado] = useState(false);
  const [cambiandoClave, setCambiandoClave] = useState(false);

  const guardar = useMutation({
    mutationFn: () =>
      apiFetch<Cuenta>("/api/v1/me/account", {
        method: "PUT",
        body: { fullName: nombre.trim(), whatsappPhone: telefono.trim() || undefined },
      }),
    onSuccess: (actualizada) => {
      queryClient.setQueryData(["me", "account"], actualizada);
      // El nombre se ve en el header/avatar: refrescamos la sesión para que se actualice.
      void queryClient.invalidateQueries({ queryKey: meQueryKey });
      setGuardado(true);
      setTimeout(() => setGuardado(false), 3000);
    },
  });

  const error = guardar.error instanceof ApiError ? guardar.error.message : null;

  const params = useSearchParams();
  const pedida = params.get("seccion");
  const seccion: Seccion = SECCIONES.some((s) => s.clave === pedida) || pedida === "cielo"
    ? (pedida as Seccion)
    : "resumen";

  return (
    <main className="mx-auto w-full max-w-md px-5 py-6 lg:max-w-3xl lg:px-12 lg:py-8">
      <h1 className="font-display text-h1 font-bold">Mi perfil</h1>
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

      {seccion === "resumen" && (
        <>
          <PanelProgreso />
          <div className="mt-8">
            <MiCielo />
          </div>
        </>
      )}

      {seccion === "cielo" && <MiCielo />}

      {seccion === "ficha" && <MiFicha />}

      {seccion === "cancelar" && (
        <div className="mt-6">
          <PoliticaCancelacion rol="estudiante" />
        </div>
      )}

      {seccion === "preguntas" && <PreguntasFrecuentes rol="estudiante" />}

      <div className={seccion === "datos" ? "" : "hidden"}>
      <h2 className="mt-8 font-display text-[19px] font-bold">Tus datos</h2>

      <div className="mt-4">
        <CambiarFoto nombre={inicial.fullName} fotoUrl={inicial.photoUrl} />
      </div>

      <label className="mt-6 block text-[12px] font-bold uppercase tracking-[0.04em] text-text-secondary" htmlFor="nombre">
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

      <label className="mt-4 block text-[12px] font-bold uppercase tracking-[0.04em] text-text-secondary" htmlFor="telefono">
        WhatsApp <span className="font-semibold normal-case text-text-muted">(opcional)</span>
      </label>
      <PhoneInput id="telefono" value={telefono} onChange={setTelefono} className="mt-1.5" />
      <p className="mt-1.5 text-[12px] text-text-muted">Por aquí te escribe tu profesor para coordinar.</p>

      {/* Correo y rol se muestran, no se editan. */}
      <div className="mt-5 flex items-center gap-2.5 rounded-base bg-surface-sunken px-4 py-3">
        <Mail size={16} strokeWidth={1.75} className="shrink-0 text-text-muted" />
        <span className="truncate text-[13px] text-text-secondary">{inicial.email}</span>
      </div>

      <button
        type="button"
        onClick={() => setCambiandoClave(true)}
        className="mt-3 flex w-full items-center gap-2.5 rounded-base border-[1.5px] border-border px-4 py-3 text-left text-[13.5px] font-semibold text-text transition-colors hover:bg-surface-sunken focus-visible:shadow-focus"
      >
        <KeyRound size={16} strokeWidth={1.75} className="text-text-secondary" />
        Cambiar contraseña
      </button>

      {error && (
        <div className="mt-4">
          <AvisoError mensaje={error} />
        </div>
      )}

      {guardado && (
        <p className="mt-4 flex items-center gap-2 rounded-card bg-success-bg px-4 py-3 text-[13px] font-semibold text-success">
          <Check size={16} strokeWidth={2.4} />
          Listo, tus datos quedaron actualizados.
        </p>
      )}

      <BotonPrincipal
        disabled={!nombre.trim() || guardar.isPending}
        onClick={() => guardar.mutate()}
        className="mt-5"
      >
        {guardar.isPending ? "Guardando…" : "Guardar cambios"}
      </BotonPrincipal>

      <PoliticaCancelacion rol="estudiante" />

      <PreguntasFrecuentes rol="estudiante" />

      <EnseñarCta />
      </div>

      {cambiandoClave && <CambiarClave onCerrar={() => setCambiandoClave(false)} />}
    </main>
  );
}

/**
 * Puente hacia la postulación de profesor desde el perfil del estudiante. Si ya empezó una, lleva a
 * su estado; si no, lo invita a postular. Sin postulación viva, no muestra nada llamativo de más.
 */
function EnseñarCta() {
  const aplic = useMiAplicacion();
  const tieneApp = !aplic.noAplico && !!aplic.status;
  const destino = tieneApp ? "/aplicacion/estado" : "/aplicacion";

  return (
    <Link
      href={destino}
      className="mt-6 flex items-center gap-3 rounded-card bg-accent-lavender-soft p-4 transition-colors hover:bg-[#e2d7f4] focus-visible:shadow-focus"
    >
      <span className="grid h-11 w-11 shrink-0 place-items-center rounded-full bg-white text-[#5e4a8a]">
        <GraduationCap size={20} strokeWidth={1.9} />
      </span>
      <span className="min-w-0 flex-1">
        <span className="block text-[13.5px] font-bold text-[#5e4a8a]">
          {tieneApp ? "Ver mi solicitud" : "Enseña en Orión"}
        </span>
        <span className="block text-[12px] text-[#5e4a8a]/85">
          {tieneApp ? "Revisa el estado de tu postulación." : "¿Quieres dar clases? Postúlate como profesor."}
        </span>
      </span>
      <ArrowRight size={18} strokeWidth={2} className="shrink-0 text-[#5e4a8a]" />
    </Link>
  );
}
