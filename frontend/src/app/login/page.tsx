"use client";

import { ArrowRight, Eye, EyeOff, GraduationCap, Lock, Mail } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { AvisoError } from "@/components/estados";
import { Constelacion, Wordmark } from "@/components/marca";
import { Rigel } from "@/components/Rigel";
import { BotonPrincipal, Campo, Spinner } from "@/components/ui";
import { ApiError } from "@/lib/api/fetch";
import { HOME_BY_ROLE } from "@/lib/auth/roles";
import { useLogin } from "@/lib/auth/session";

export default function LoginPage() {
  const router = useRouter();
  const login = useLogin();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [verClave, setVerClave] = useState(false);

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    login.mutate(
      { email, password },
      // Cada rol aterriza donde le sirve: el estudiante a explorar, la profesora a sus clases.
      { onSuccess: (me) => router.replace(HOME_BY_ROLE[me.role]) },
    );
  }

  const error = login.error instanceof ApiError ? login.error.message : null;

  return (
    <main className="flex min-h-dvh flex-col lg:flex-row">
      {/* Marca: hero de 280 px arriba en móvil; panel del amanecer a la derecha en desktop (47%).
          Titular arriba a la izquierda, Rigel abajo a la derecha, constelación abajo a la izquierda:
          el texto nunca comparte columna con el personaje. */}
      <div className="gradient-dawn relative flex h-[280px] flex-col overflow-hidden rounded-b-[24px] p-7 lg:order-2 lg:m-5 lg:h-auto lg:w-[47%] lg:rounded-[22px] lg:p-10">
        <Constelacion className="pointer-events-none absolute -bottom-4 -left-8 h-[130px] w-[130px] opacity-[0.55] lg:bottom-6 lg:left-6 lg:h-[220px] lg:w-[220px]" />
        <div className="relative">
          <Wordmark className="text-[15px] text-on-primary" />
          <h1 className="mt-4 max-w-[12ch] font-display text-[30px] font-bold leading-[1.15] text-on-primary lg:mt-8 lg:text-[42px]">
            Tu inglés está a punto de amanecer.
          </h1>
        </div>
        <Rigel
          pose="saludo"
          decorativo
          className="pointer-events-none absolute bottom-3 right-3 h-[146px] w-auto lg:bottom-8 lg:right-8 lg:h-[230px]"
        />
      </div>

      {/* Formulario: debajo del hero en móvil; mitad izquierda (53%) centrada en desktop. */}
      <div className="flex flex-1 items-start justify-center px-7 py-8 lg:order-1 lg:items-center lg:px-10">
        <form onSubmit={onSubmit} className="w-full max-w-md lg:max-w-[400px]">
          <h2 className="font-display text-[26px] font-bold lg:text-[34px]">Qué bueno verte</h2>
          <p className="mt-1 text-[14px] text-text-secondary">
            Entra para reservar y coordinar tus clases.
          </p>

          <label
            className="mt-6 block text-[12px] font-bold uppercase tracking-[0.04em] text-text-secondary"
            htmlFor="email"
          >
            Correo
          </label>
          <Campo
            id="email"
            type="email"
            required
            autoComplete="email"
            placeholder="tu@correo.com"
            icono={<Mail size={18} strokeWidth={1.75} />}
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            className={`mt-1.5 ${error ? "border-error" : ""}`}
          />

          <label
            className="mt-4 block text-[12px] font-bold uppercase tracking-[0.04em] text-text-secondary"
            htmlFor="password"
          >
            Contraseña
          </label>
          <div className="relative">
            <Campo
              id="password"
              type={verClave ? "text" : "password"}
              required
              autoComplete="current-password"
              placeholder="••••••••"
              icono={<Lock size={18} strokeWidth={1.75} />}
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              className={`mt-1.5 pr-12 ${error ? "border-error" : ""}`}
            />
            <button
              type="button"
              aria-label={verClave ? "Ocultar contraseña" : "Mostrar contraseña"}
              onClick={() => setVerClave((valor) => !valor)}
              className="absolute right-3 top-1/2 mt-[3px] grid h-9 w-9 -translate-y-1/2 place-items-center rounded-full text-text-muted transition-colors hover:text-text focus-visible:shadow-focus"
            >
              {verClave ? <EyeOff size={18} strokeWidth={1.75} /> : <Eye size={18} strokeWidth={1.75} />}
            </button>
          </div>

          <div className="mt-2 text-right">
            <Link
              href="/recuperar"
              className="text-[13px] font-semibold text-primary-strong hover:underline"
            >
              ¿Olvidaste tu contraseña?
            </Link>
          </div>

          {error && (
            <div className="mt-4">
              <AvisoError mensaje={error} />
            </div>
          )}

          <BotonPrincipal type="submit" disabled={login.isPending} className="mt-6">
            {login.isPending ? (
              <>
                <Spinner />
                Entrando…
              </>
            ) : (
              <>
                Entrar
                <ArrowRight size={18} strokeWidth={1.75} />
              </>
            )}
          </BotonPrincipal>

          <p className="mt-6 text-center text-[13px] text-text-secondary">
            ¿Primera vez?{" "}
            <Link href="/registro" className="font-bold text-primary-strong hover:underline">
              Crea tu cuenta
            </Link>
          </p>

          {/* El profesor que llega aquí por el boca a boca no tiene por qué saber que el camino
              empieza en el mismo registro que el de los estudiantes. Se lo decimos. */}
          <div className="mt-5 flex items-start gap-3 rounded-base border border-border bg-surface-sunken px-4 py-3.5">
            <GraduationCap size={18} strokeWidth={1.75} className="mt-0.5 shrink-0 text-primary" />
            <p className="text-[13px] leading-relaxed text-text-secondary">
              ¿Enseñas idiomas?{" "}
              <Link
                href="/registro?rol=profesor"
                className="font-bold text-primary-strong hover:underline"
              >
                Postúlate para dar clases
              </Link>{" "}
              en Orión.
            </p>
          </div>

          <p className="mt-8 text-center text-[12px] italic text-text-muted">
            Find the right teacher, learn your way.
          </p>
        </form>
      </div>
    </main>
  );
}
