"use client";

import { ArrowRight, Eye, EyeOff, Lock, Mail } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { AvisoError } from "@/components/estados";
import { HeroNoche, Wordmark } from "@/components/marca";
import { BotonPrincipal, Campo } from "@/components/ui";
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
    <main className="flex-1">
      <div className="mx-auto flex min-h-full max-w-md flex-col">
        <HeroNoche className="rounded-b-sheet px-6 py-11 text-center">
          <Wordmark className="text-[34px] text-white" />
          <p className="mx-auto mt-2 max-w-[260px] text-[13px] leading-relaxed text-[#c9bff0]">
            Aprende con confianza. Transforma tus oportunidades.
          </p>
        </HeroNoche>

        <form onSubmit={onSubmit} className="px-5 py-7">
          <label className="block text-[12.5px] font-bold text-text-secondary" htmlFor="email">
            Correo
          </label>
          <Campo
            id="email"
            type="email"
            required
            autoComplete="email"
            placeholder="tu@correo.com"
            icono={<Mail size={18} strokeWidth={2.2} />}
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            className={`mt-1.5 ${error ? "border-[#e2544f]" : ""}`}
          />

          <label className="mt-4 block text-[12.5px] font-bold text-text-secondary" htmlFor="password">
            Contraseña
          </label>
          <div className="relative">
            <Campo
              id="password"
              type={verClave ? "text" : "password"}
              required
              autoComplete="current-password"
              placeholder="••••••••"
              icono={<Lock size={18} strokeWidth={2.2} />}
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              className={`mt-1.5 pr-12 ${error ? "border-[#e2544f]" : ""}`}
            />
            <button
              type="button"
              aria-label={verClave ? "Ocultar contraseña" : "Mostrar contraseña"}
              onClick={() => setVerClave((valor) => !valor)}
              className="absolute right-4 top-1/2 mt-[3px] -translate-y-1/2 text-text-muted"
            >
              {verClave ? <EyeOff size={18} strokeWidth={2.2} /> : <Eye size={18} strokeWidth={2.2} />}
            </button>
          </div>

          {error && (
            <div className="mt-4">
              <AvisoError mensaje={error} />
            </div>
          )}

          <BotonPrincipal type="submit" disabled={login.isPending} className="mt-6">
            {login.isPending ? "Entrando…" : "Entrar"}
            {!login.isPending && <ArrowRight size={18} strokeWidth={2.2} />}
          </BotonPrincipal>
        </form>
      </div>
    </main>
  );
}
