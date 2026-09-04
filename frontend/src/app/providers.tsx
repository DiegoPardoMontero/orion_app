"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";
import { ApiError } from "@/lib/api/fetch";

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
            /*
             * Reintentar lo que puede mejorar, y solo eso.
             *
             * Un 404 o un 422 son la respuesta, no un fallo: repetirlos solo hace esperar. Un corte
             * de red o un 500, en cambio, se arreglan solos casi siempre — y sin reintentar, un
             * único paquete perdido deja a la persona mirando «No pudimos cargar…» con un botón que
             * tiene que descubrir y pulsar. Dos intentos más, y luego sí se le cuenta.
             */
            retry: (intentos, error) =>
              error instanceof ApiError && error.status < 500 ? false : intentos < 2,
            refetchOnWindowFocus: false,
          },
        },
      }),
  );

  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}
