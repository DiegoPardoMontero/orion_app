"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";

/**
 * TanStack Query es la caché de datos del servidor. Cada consulta tiene una clave; cuando algo
 * cambia (una reserva, una cancelación), invalidamos esa clave y Query vuelve a pedir los datos
 * sola. Es lo que evita tener que sincronizar estado a mano por toda la app.
 *
 * useState para crear el cliente: así se crea UNA vez por montaje y no uno nuevo en cada render.
 */
export function Providers({ children }: { children: ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            // No reintentar los errores del backend (un 404 o un 422 no mejoran reintentando).
            retry: false,
            refetchOnWindowFocus: false,
          },
        },
      }),
  );

  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}
