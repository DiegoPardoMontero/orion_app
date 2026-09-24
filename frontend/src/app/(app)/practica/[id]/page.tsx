"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft } from "lucide-react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { Suspense } from "react";
import { Cargando, Vacio } from "@/components/estados";
import { Cierre } from "@/components/practica/Cierre";
import { Juego } from "@/components/practica/Juego";
import { EstadoDelSet, Inicio } from "@/components/practica/Portada";
import { apiFetch } from "@/lib/api/fetch";
import type { Ejercicio, SetDePractica } from "@/lib/practica";

/**
 * Practicar entre clases (Bloque 10, Parte B; rediseño del handoff `design_handoff_orion_practica`).
 * Cada set es una constelación de cinco estrellas: un ejercicio por pantalla, cada uno enciende la
 * suya y al terminar se dibujan las líneas. En el celular va a pantalla completa, sin la barra de
 * abajo: es un flujo enfocado, y la única salida es «Salir y seguir luego».
 *
 * <p>El error de práctica es «Casi…» en ámbar y nunca el rojo de alarma: equivocarse aquí no es un
 * fallo del sistema, y el color lo tiene que decir.
 */
export default function PracticaPage() {
  return (
    <Suspense fallback={null}>
      <Contenido />
    </Suspense>
  );
}

function Contenido() {
  const { id } = useParams<{ id: string }>();
  const queryClient = useQueryClient();
  const set = useQuery({
    queryKey: ["practice-set", id],
    queryFn: () => apiFetch<SetDePractica>(`/api/v1/practice-sets/${id}`),
    retry: false,
    // Mientras se prepara, se mira cada cinco segundos: tarda cerca de un minuto.
    refetchInterval: (q) => (q.state.data?.status === "PENDING" ? 5000 : false),
  });

  // «Empezar» en el inicio la marca como empezada; el servidor lo ignora si ya lo estaba.
  const empezar = useMutation({
    mutationFn: () => apiFetch<SetDePractica>(`/api/v1/practice-sets/${id}/start`, { method: "POST" }),
    onSuccess: (s) => queryClient.setQueryData(["practice-set", id], s),
  });

  const completar = useMutation({
    mutationFn: () => apiFetch<SetDePractica>(`/api/v1/practice-sets/${id}/complete`, { method: "POST" }),
    onSuccess: (s) => {
      queryClient.setQueryData(["practice-set", id], s);
      queryClient.invalidateQueries({ queryKey: ["me", "practice"] });
      queryClient.invalidateQueries({ queryKey: ["me", "engagement"] });
      // Si terminar encendió un logro, la estrella se celebra aquí mismo (brief, B5.3): la celebración
      // mira los logros, y sin esto no se enteraba hasta volver a cargar el perfil.
      queryClient.invalidateQueries({ queryKey: ["me", "achievements"] });
    },
  });

  const alItem = (item: Ejercicio) =>
    queryClient.setQueryData<SetDePractica>(["practice-set", id], (previo) =>
      previo ? { ...previo, items: previo.items.map((i) => (i.id === item.id ? item : i)) } : previo,
    );

  if (set.isPending) {
    return (
      <main className="mx-auto w-full max-w-md px-5 py-6">
        <Cargando filas={2} />
      </main>
    );
  }
  if (set.isError || !set.data || !["PENDING", "READY", "IN_PROGRESS", "COMPLETED", "EXPIRED"].includes(set.data.status)) {
    return (
      <main className="mx-auto w-full max-w-md px-5 py-6">
        <Link
          href="/cuenta?seccion=resumen"
          className="inline-flex min-h-11 items-center gap-1.5 text-[13px] font-semibold text-text-secondary transition-colors hover:text-text focus-visible:shadow-focus"
        >
          <ArrowLeft size={15} strokeWidth={2} />
          Volver a mi perfil
        </Link>
        <Vacio titulo="No encontramos esta práctica" texto="Puede que ya haya vencido. La próxima llega con tu siguiente clase." />
      </main>
    );
  }

  const s = set.data;
  switch (s.status) {
    case "PENDING":
    case "EXPIRED":
      return <EstadoDelSet set={s} />;
    case "READY":
      return <Inicio set={s} empezando={empezar.isPending} onEmpezar={() => empezar.mutate()} />;
    case "COMPLETED":
      return <Cierre set={s} />;
    default:
      return (
        <Juego
          set={s}
          onItem={alItem}
          onTerminar={() => completar.mutate()}
          terminando={completar.isPending}
          errorAlTerminar={completar.error?.message ?? null}
        />
      );
  }
}
