import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api/fetch";

/**
 * La bienvenida de cada usuario (V51): el video de Sofía para el profesor aprobado y los recorridos
 * guiados que ya vio. El servidor decide a quién le toca el video; aquí solo se pinta.
 */
export type PasoDeBienvenida = "WELCOME_VIDEO" | "TOUR_PROFESSOR" | "TOUR_STUDENT";

export type Bienvenida = {
  /** Ausente si no le toca: no es profesor aprobado o no hay video configurado. */
  welcomeVideo: { url: string; seen: boolean } | null;
  /** El recorrido que le falta ver; el del profesor, solo cuando ya está aprobado. */
  pendingTour: "TOUR_PROFESSOR" | "TOUR_STUDENT" | null;
  completed: PasoDeBienvenida[];
};

export const bienvenidaKey = ["me", "onboarding"] as const;

export function useBienvenida(activa = true) {
  return useQuery({
    queryKey: bienvenidaKey,
    queryFn: () => apiFetch<Bienvenida>("/api/v1/me/onboarding"),
    enabled: activa,
    staleTime: 5 * 60_000,
  });
}

/** Marca un paso como visto. Optimista: el diálogo se cierra al instante, no cuando responde el servidor. */
export function useCompletarPaso() {
  const cliente = useQueryClient();
  return useMutation({
    mutationFn: (paso: PasoDeBienvenida) =>
      apiFetch<void>(`/api/v1/me/onboarding/${paso}`, { method: "POST" }),
    onMutate: (paso) => {
      cliente.setQueryData<Bienvenida>(bienvenidaKey, (b) =>
        b
          ? {
              welcomeVideo: paso === "WELCOME_VIDEO" && b.welcomeVideo ? { ...b.welcomeVideo, seen: true } : b.welcomeVideo,
              pendingTour: b.pendingTour === paso ? null : b.pendingTour,
              completed: b.completed.includes(paso) ? b.completed : [...b.completed, paso],
            }
          : b,
      );
    },
    onSettled: () => cliente.invalidateQueries({ queryKey: bienvenidaKey }),
  });
}
