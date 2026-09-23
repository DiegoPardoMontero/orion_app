"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch, marcarCierreDeSesion } from "@/lib/api/fetch";

/**
 * El rol EFECTIVO que devuelve el backend, no la columna de la base.
 *
 * `TEACHER_APPLICANT` es quien se registró para enseñar y todavía espera una decisión. En la base
 * su rol es STUDENT —para que su cuenta siga siendo una cuenta completa si lo rechazan—, pero ni
 * la autorización del backend ni esta aplicación lo tratan como estudiante: no reserva, no paga y
 * no tiene saldo. Lo que tiene es su postulación.
 */
export type Role = "STUDENT" | "PROFESSOR" | "ADMIN" | "TEACHER_APPLICANT";

export type Me = {
  id: string;
  email: string;
  fullName: string;
  role: Role;
  photoUrl?: string | null;
  /**
   * Falso solo en las cuentas anteriores a la regla de mayoría de edad, que nunca la declararon.
   * Se les pide al entrar; el backend vuelve a comprobarlo al reservar, que es donde importa.
   */
  adultConfirmed: boolean;
  /**
   * Si el correo está comprobado. Junto con `adultConfirmed`, son las dos condiciones que hacen
   * falta para reservar; el backend las vuelve a comprobar en BookingService, que es donde mandan.
   */
  emailVerified: boolean;
};

export const meQueryKey = ["auth", "me"] as const;

/**
 * La sesión es una consulta más: TanStack Query la cachea, así que /auth/me se pide una vez y
 * todas las pantallas la leen de la caché. Al hacer login o logout invalidamos esa clave y
 * Query la vuelve a pedir sola.
 */
export function useMe() {
  return useQuery({
    queryKey: meQueryKey,
    queryFn: () => apiFetch<Me>("/api/v1/auth/me"),
    retry: false,
  });
}

export function useLogin() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (credentials: { email: string; password: string }) =>
      apiFetch<Me>("/api/v1/auth/login", { method: "POST", body: credentials }),
    onSuccess: (me) => {
      marcarCierreDeSesion(false);
      // Sembramos la caché con el usuario que acaba de entrar: evita un /me redundante.
      queryClient.setQueryData(meQueryKey, me);
    },
  });
}

export type RegisterInput = {
  fullName: string;
  email: string;
  password: string;
  whatsappPhone?: string;
  /** Se registró desde «Postúlate para dar clases»: la cuenta nace como aspirante, no estudiante. */
  wantsToTeach?: boolean;
  /**
   * Las tres van por separado y las tres son obligatorias. Empaquetar la autorización de datos
   * con la aceptación de los términos la viciaría: el Decreto 1377 de 2013 la exige previa,
   * expresa e informada — y por tanto específica.
   */
  adult: boolean;
  acceptsTerms: boolean;
  acceptsDataPolicy: boolean;
};

/**
 * Auto-registro de estudiantes. El backend crea la cuenta y abre la sesión de una vez, así que
 * al igual que en login sembramos la caché con el usuario devuelto y nadie pide /me de nuevo.
 */
export function useRegister() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (input: RegisterInput) =>
      apiFetch<Me>("/api/v1/auth/register", { method: "POST", body: input }),
    onSuccess: (me) => {
      marcarCierreDeSesion(false);
      queryClient.setQueryData(meQueryKey, me);
    },
  });
}

/**
 * La declaración de mayoría de edad de las cuentas anteriores al Bloque 9. Al confirmarla,
 * refrescamos /me para que el aviso desaparezca sin recargar.
 */
export function useConfirmarMayoriaDeEdad() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () =>
      apiFetch<void>("/api/v1/me/account/adulthood", { method: "POST" }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: meQueryKey });
    },
  });
}

/** Reenvía el correo de verificación. El backend corta a los tres por hora. */
export function useReenviarVerificacion() {
  return useMutation({
    mutationFn: () =>
      apiFetch<void>("/api/v1/me/account/email-verification/resend", { method: "POST" }),
  });
}

export function useLogout() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () => apiFetch<void>("/api/v1/auth/logout", { method: "POST" }),
    onMutate: () => marcarCierreDeSesion(true),
    onSuccess: () => {
      queryClient.clear();
    },
    onError: () => marcarCierreDeSesion(false),
  });
}
