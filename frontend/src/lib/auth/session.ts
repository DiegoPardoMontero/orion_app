"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api/fetch";

export type Role = "STUDENT" | "PROFESSOR" | "ADMIN";

export type Me = {
  id: string;
  email: string;
  fullName: string;
  role: Role;
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
      // Sembramos la caché con el usuario que acaba de entrar: evita un /me redundante.
      queryClient.setQueryData(meQueryKey, me);
    },
  });
}

export function useLogout() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () => apiFetch<void>("/api/v1/auth/logout", { method: "POST" }),
    onSuccess: () => {
      queryClient.clear();
    },
  });
}
