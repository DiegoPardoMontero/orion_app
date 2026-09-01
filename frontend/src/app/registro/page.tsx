"use client";

import { ArrowRight, Eye, EyeOff, Lock, Mail, MessageCircle, User } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { AvisoError } from "@/components/estados";
import { Constelacion, Wordmark } from "@/components/marca";
import { Rigel } from "@/components/Rigel";
import { BotonPrincipal, Campo, Spinner } from "@/components/ui";
import { ApiError } from "@/lib/api/fetch";
import { HOME_BY_ROLE } from "@/lib/auth/roles";
import { useRegister } from "@/lib/auth/session";
import { fuerzaClave } from "@/lib/password";

export default function RegistroPage() {
  const router = useRouter();
  const registro = useRegister();

  const [nombre, setNombre] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [whatsapp, setWhatsapp] = useState("");
  const [verClave, setVerClave] = useState(false);

  const fuerza = fuerzaClave(password);
  const listo = nombre.trim().length > 0 && /.+@.+\..+/.test(email) && password.length >= 8;

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (!listo) return;
    registro.mutate(
      {
        fullName: nombre.trim(),
        email: email.trim(),
        password,
        whatsappPhone: whatsapp.trim() || undefined,
      },
      { onSuccess: (me) => router.replace(HOME_BY_ROLE[me.role]) },
    );
  }

  const error = registro.error instanceof ApiError ? registro.error.message : null;

  return (
    <main className="flex min-h-dvh flex-col lg:flex-row">
      {/* Marca: hero del amanecer. Rigel arriba a la derecha se presenta; titular y subtítulo
          despejados abajo a la izquierda, sin compartir columna con el personaje. */}
      <div className="gradient-dawn relative flex h-[300px] flex-col justify-end overflow-hidden rounded-b-[24px] p-7 lg:order-2 lg:m-5 lg:h-auto lg:w-[47%] lg:rounded-[22px] lg:p-10">
        <Constelacion className="pointer-events-none absolute left-2 top-8 h-[120px] w-[120px] opacity-[0.45] lg:h-[200px] lg:w-[200px]" />
        <Wordmark className="absolute left-7 top-7 text-[15px] text-on-primary lg:left-10 lg:top-10" />
        <Rigel
          pose="saludo"
          decorativo
          className="pointer-events-none absolute right-2 top-8 h-[150px] w-auto lg:right-8 lg:top-10 lg:h-[236px]"
        />
        <div className="relative">
          <h1 className="max-w-[14ch] font-display text-[28px] font-bold leading-[1.15] text-on-primary lg:text-[40px]">
            Da el primer paso hoy.
          </h1>
          <p className="mt-2 max-w-[34ch] text-[13px] leading-relaxed text-on-primary/85 lg:text-[15px]">
            Soy Rigel. Te acompaño desde tu primera clase hasta que hables sin pensarlo.
          </p>
        </div>
      </div>

      {/* Formulario de registro. */}
      <div className="flex flex-1 items-start justify-center px-7 py-8 lg:order-1 lg:items-center lg:px-10">
        <form onSubmit={onSubmit} className="w-full max-w-md lg:max-w-[400px]">
          <h2 className="font-display text-[26px] font-bold lg:text-[34px]">Crea tu cuenta</h2>
          <p className="mt-1 text-[14px] text-text-secondary">
            Regístrate como estudiante y reserva tu primera clase.
          </p>

          <label
            className="mt-6 block text-[12px] font-bold uppercase tracking-[0.04em] text-text-secondary"
            htmlFor="nombre"
          >
            Nombre completo
          </label>
          <Campo
            id="nombre"
            type="text"
            required
            autoComplete="name"
            maxLength={150}
            placeholder="María Gómez"
            icono={<User size={18} strokeWidth={1.75} />}
            value={nombre}
            onChange={(event) => setNombre(event.target.value)}
            className="mt-1.5"
          />

          <label
            className="mt-4 block text-[12px] font-bold uppercase tracking-[0.04em] text-text-secondary"
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
              autoComplete="new-password"
              placeholder="Mínimo 8 caracteres"
              icono={<Lock size={18} strokeWidth={1.75} />}
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              className="mt-1.5 pr-12"
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

          {/* Medidor de fuerza: 4 segmentos que se encienden en menta; mensaje que dice qué falta. */}
          <div className="mt-2.5" aria-hidden="true">
            <div className="flex gap-1.5">
              {[0, 1, 2, 3].map((i) => (
                <span
                  key={i}
                  className={`h-[5px] flex-1 rounded-pill transition-colors ${
                    i < fuerza.nivel ? "bg-success" : "bg-border"
                  }`}
                />
              ))}
            </div>
            <p
              className={`mt-1.5 text-[12px] ${
                fuerza.nivel >= 3
                  ? "text-success"
                  : password
                    ? "text-text-secondary"
                    : "text-text-muted"
              }`}
            >
              {fuerza.mensaje}
            </p>
          </div>

          <label
            className="mt-4 block text-[12px] font-bold uppercase tracking-[0.04em] text-text-secondary"
            htmlFor="whatsapp"
          >
            WhatsApp <span className="font-semibold normal-case text-text-muted">(opcional)</span>
          </label>
          <Campo
            id="whatsapp"
            type="tel"
            autoComplete="tel"
            maxLength={20}
            placeholder="+57 300 111 2233"
            icono={<MessageCircle size={18} strokeWidth={1.75} />}
            value={whatsapp}
            onChange={(event) => setWhatsapp(event.target.value)}
            className="mt-1.5"
          />
          <p className="mt-1.5 text-[12px] text-text-muted">
            Por aquí coordinas la clase con tu profesor.
          </p>

          {error && (
            <div className="mt-4">
              <AvisoError mensaje={error} />
            </div>
          )}

          <BotonPrincipal type="submit" disabled={!listo || registro.isPending} className="mt-6">
            {registro.isPending ? (
              <>
                <Spinner />
                Creando…
              </>
            ) : (
              <>
                Crear cuenta
                <ArrowRight size={18} strokeWidth={1.75} />
              </>
            )}
          </BotonPrincipal>

          <p className="mt-6 text-center text-[13px] text-text-secondary">
            ¿Ya tienes cuenta?{" "}
            <Link href="/login" className="font-bold text-primary-strong hover:underline">
              Entra
            </Link>
          </p>
        </form>
      </div>
    </main>
  );
}
