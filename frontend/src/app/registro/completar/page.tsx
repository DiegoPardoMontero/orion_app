"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch, ApiError } from "@/lib/api/fetch";
import { destinoAlEntrar } from "@/lib/auth/roles";
import type { Me } from "@/lib/auth/session";
import { Consentimiento } from "@/components/Consentimiento";
import { AvisoError, Cargando } from "@/components/estados";
import { Wordmark } from "@/components/marca";
import { Rigel } from "@/components/Rigel";
import { Boton } from "@/components/ui";

type Pendiente = { name: string | null; email: string; provider: string };

const PROVEEDOR: Record<string, string> = { google: "Google", apple: "Apple", facebook: "Facebook" };

/**
 * El último paso de quien llega nuevo desde Google, Apple o Facebook: la cuenta no nace sin las
 * tres casillas del alta —mayoría de edad, términos y autorización de datos—, cada una en la suya,
 * igual que en el registro con correo. El nombre llega del proveedor y se puede corregir; el
 * correo no, porque es el que el proveedor garantizó. Rigel recibe aquí, como en el registro.
 */
export default function CompletarRegistroPage() {
  const router = useRouter();
  const qc = useQueryClient();
  const pendiente = useQuery({
    queryKey: ["auth", "social", "pending"],
    queryFn: () => apiFetch<Pendiente>("/api/v1/auth/social/pending", { redirectOn401: false }),
    retry: false,
  });

  // El nombre del proveedor hasta que la persona lo toque: estado derivado, sin efecto que lo copie.
  const [nombreEditado, setNombre] = useState<string | null>(null);
  const [mayor, setMayor] = useState(false);
  const [terminos, setTerminos] = useState(false);
  const [datos, setDatos] = useState(false);

  const nombre = nombreEditado ?? pendiente.data?.name ?? "";

  const completar = useMutation({
    mutationFn: () =>
      apiFetch<Me>("/api/v1/auth/social/complete", {
        method: "POST",
        body: { fullName: nombre.trim(), adult: mayor, acceptsTerms: terminos, acceptsDataPolicy: datos },
        redirectOn401: false,
      }),
    onSuccess: (me) => {
      void qc.invalidateQueries({ queryKey: ["auth"] });
      router.replace(destinoAlEntrar(me.role));
    },
  });

  const listo = nombre.trim().length > 0 && mayor && terminos && datos;

  return (
    <main className="mx-auto w-full max-w-md px-6 py-6">
      <Link href="/" className="inline-block rounded-base text-primary focus-visible:shadow-focus">
        <Wordmark className="text-[15px]" />
      </Link>

      {pendiente.isPending && (
        <div className="mt-8">
          <Cargando filas={2} />
        </div>
      )}

      {pendiente.isError && (
        <section className="mt-10">
          <h1 className="font-display text-[26px] font-bold">Tu ingreso venció</h1>
          <p className="mt-2 text-[15px] text-text-secondary">Vuelve a intentarlo desde el inicio.</p>
          <Link
            href="/login"
            className="mt-5 inline-flex h-11 items-center rounded-pill bg-primary px-5 text-[14px] font-bold text-on-primary shadow-primary hover:bg-primary-strong focus-visible:shadow-focus"
          >
            Ir a entrar
          </Link>
        </section>
      )}

      {pendiente.data && (
        <section className="mt-6">
          <div className="flex items-center gap-3">
            <Rigel pose="saludo" decorativo className="h-24 w-auto shrink-0" />
            <div>
              <h1 className="font-display text-[26px] font-bold leading-tight">Un último paso</h1>
              <p className="mt-1 text-[14px] text-text-secondary">
                Entraste con {PROVEEDOR[pendiente.data.provider] ?? "tu cuenta"}. Confirma esto y tu
                cuenta de Orión queda lista.
              </p>
            </div>
          </div>

          <label className="mt-6 block">
            <span className="text-[14px] font-bold text-text">Tu nombre</span>
            <input
              type="text"
              autoComplete="name"
              maxLength={150}
              value={nombre}
              onChange={(e) => setNombre(e.target.value)}
              className="mt-1.5 h-12 w-full rounded-base border border-border bg-surface-raised px-4 text-[15px] focus:border-primary focus:outline-none focus-visible:shadow-focus"
            />
          </label>
          <p className="mt-3 text-[13px] text-text-secondary">
            Correo: <strong className="text-text">{pendiente.data.email}</strong>
          </p>

          <div className="mt-5 grid gap-3">
            <Consentimiento id="mayor" marcado={mayor} onCambio={setMayor}>
              Declaro que soy <strong>mayor de 18 años</strong>.
            </Consentimiento>
            <Consentimiento id="terminos" marcado={terminos} onCambio={setTerminos}>
              Acepto los{" "}
              <Link href="/terminos" target="_blank" className="font-bold text-primary-strong hover:underline">
                Términos y condiciones
              </Link>
              .
            </Consentimiento>
            <Consentimiento id="datos" marcado={datos} onCambio={setDatos}>
              Autorizo el tratamiento de mis datos personales conforme a la{" "}
              <Link href="/privacidad" target="_blank" className="font-bold text-primary-strong hover:underline">
                Política de tratamiento
              </Link>
              .
            </Consentimiento>
          </div>

          {completar.error instanceof ApiError && (
            <div className="mt-4">
              <AvisoError mensaje={completar.error.message} />
            </div>
          )}

          <Boton
            variante="primario"
            className="mt-6 w-full"
            disabled={!listo || completar.isPending}
            onClick={() => completar.mutate()}
          >
            {completar.isPending ? "Creando…" : "Crear mi cuenta"}
          </Boton>
        </section>
      )}
    </main>
  );
}
