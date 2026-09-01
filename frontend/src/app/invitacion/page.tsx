"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowRight, Eye, EyeOff, Lock, User } from "lucide-react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState, type FormEvent } from "react";
import { AvisoError } from "@/components/estados";
import { Wordmark } from "@/components/marca";
import { PhoneInput } from "@/components/PhoneInput";
import { BotonPrincipal, Campo, Spinner } from "@/components/ui";
import { ApiError, apiFetch } from "@/lib/api/fetch";
import { meQueryKey, type Me } from "@/lib/auth/session";
import { fuerzaClave } from "@/lib/password";

export default function InvitacionPage() {
  return (
    <Suspense fallback={null}>
      <Invitacion />
    </Suspense>
  );
}

function Invitacion() {
  const token = useSearchParams().get("token");
  const router = useRouter();
  const queryClient = useQueryClient();

  const info = useQuery({
    queryKey: ["invite", token],
    queryFn: () => apiFetch<{ email: string }>(`/api/v1/auth/invite?token=${token}`, { redirectOn401: false }),
    enabled: !!token,
    retry: false,
  });

  const [nombre, setNombre] = useState("");
  const [telefono, setTelefono] = useState("");
  const [password, setPassword] = useState("");
  const [verClave, setVerClave] = useState(false);
  const [headline, setHeadline] = useState("");
  const [bio, setBio] = useState("");

  const aceptar = useMutation({
    mutationFn: () =>
      apiFetch<Me>("/api/v1/auth/accept-invite", {
        method: "POST",
        body: {
          token,
          fullName: nombre.trim(),
          password,
          whatsappPhone: telefono.trim() || undefined,
          headline: headline.trim() || undefined,
          bio: bio.trim() || undefined,
        },
      }),
    onSuccess: (me) => {
      queryClient.setQueryData(meQueryKey, me);
      router.replace("/disponibilidad");
    },
  });

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (nombre.trim() && password.length >= 8) aceptar.mutate();
  }

  const fuerza = fuerzaClave(password);
  const error = aceptar.error instanceof ApiError ? aceptar.error.message : null;

  return (
    <main className="flex min-h-dvh flex-col items-center justify-center px-6 py-12">
      <div className="w-full max-w-md">
        <Wordmark className="text-[18px] text-primary" />

        {!token || info.isError ? (
          <div className="mt-8">
            <h1 className="font-display text-[26px] font-bold">Invitación no válida</h1>
            <p className="mt-2 text-[14px] leading-relaxed text-text-secondary">
              Este enlace no es válido o ya expiró. Pídele a Orión que te reenvíe la invitación.
            </p>
            <Link href="/login" className="mt-6 inline-block text-[14px] font-bold text-primary-strong hover:underline">
              Ir a entrar
            </Link>
          </div>
        ) : info.isPending ? (
          <p className="mt-8 text-[14px] text-text-muted">Cargando tu invitación…</p>
        ) : (
          <form onSubmit={onSubmit} className="mt-8">
            <p className="text-[12px] font-bold uppercase tracking-[0.1em] text-primary-strong">
              Bienvenido a Orión
            </p>
            <h1 className="mt-2 font-display text-[26px] font-bold">Completa tu perfil de profesor</h1>
            <p className="mt-2 text-[14px] leading-relaxed text-text-secondary">
              Estás activando la cuenta de{" "}
              <span className="font-semibold text-text">{info.data.email}</span>. Con esto podrás
              publicar tu perfil y recibir estudiantes.
            </p>

            <label className="mt-6 block text-[12px] font-bold uppercase tracking-[0.04em] text-text-secondary" htmlFor="nombre">
              Nombre completo
            </label>
            <Campo
              id="nombre"
              type="text"
              required
              maxLength={150}
              placeholder="María Gómez"
              icono={<User size={18} strokeWidth={1.75} />}
              value={nombre}
              onChange={(event) => setNombre(event.target.value)}
              className="mt-1.5"
            />

            <label className="mt-4 block text-[12px] font-bold uppercase tracking-[0.04em] text-text-secondary" htmlFor="telefono">
              WhatsApp
            </label>
            <PhoneInput id="telefono" value={telefono} onChange={setTelefono} className="mt-1.5" />

            <label className="mt-4 block text-[12px] font-bold uppercase tracking-[0.04em] text-text-secondary" htmlFor="password">
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
                onClick={() => setVerClave((v) => !v)}
                className="absolute right-3 top-1/2 mt-[3px] grid h-9 w-9 -translate-y-1/2 place-items-center rounded-full text-text-muted transition-colors hover:text-text focus-visible:shadow-focus"
              >
                {verClave ? <EyeOff size={18} strokeWidth={1.75} /> : <Eye size={18} strokeWidth={1.75} />}
              </button>
            </div>
            <div className="mt-2.5" aria-hidden="true">
              <div className="flex gap-1.5">
                {[0, 1, 2, 3].map((i) => (
                  <span key={i} className={`h-[5px] flex-1 rounded-pill transition-colors ${i < fuerza.nivel ? "bg-success" : "bg-border"}`} />
                ))}
              </div>
              <p className={`mt-1.5 text-[12px] ${fuerza.nivel >= 3 ? "text-success" : password ? "text-text-secondary" : "text-text-muted"}`}>
                {fuerza.mensaje}
              </p>
            </div>

            <label className="mt-4 block text-[12px] font-bold uppercase tracking-[0.04em] text-text-secondary" htmlFor="headline">
              Titular <span className="font-semibold normal-case text-text-muted">(opcional)</span>
            </label>
            <Campo
              id="headline"
              type="text"
              maxLength={120}
              placeholder="Conversación y confianza · A1–B1"
              value={headline}
              onChange={(event) => setHeadline(event.target.value)}
              className="mt-1.5"
            />

            <label className="mt-4 block text-[12px] font-bold uppercase tracking-[0.04em] text-text-secondary" htmlFor="bio">
              Sobre ti <span className="font-semibold normal-case text-text-muted">(opcional)</span>
            </label>
            <textarea
              id="bio"
              rows={3}
              maxLength={800}
              placeholder="Cuéntales cómo son tus clases."
              value={bio}
              onChange={(event) => setBio(event.target.value)}
              className="mt-1.5 w-full rounded-base border-[1.5px] border-border bg-surface-raised px-4 py-3 text-sm placeholder:text-text-muted focus:border-primary focus:shadow-focus focus:outline-none"
            />

            {error && (
              <div className="mt-4">
                <AvisoError mensaje={error} />
              </div>
            )}

            <BotonPrincipal
              type="submit"
              disabled={!nombre.trim() || password.length < 8 || aceptar.isPending}
              className="mt-5"
            >
              {aceptar.isPending ? (
                <>
                  <Spinner />
                  Activando…
                </>
              ) : (
                <>
                  Aceptar y empezar
                  <ArrowRight size={18} strokeWidth={1.75} />
                </>
              )}
            </BotonPrincipal>
          </form>
        )}
      </div>
    </main>
  );
}
