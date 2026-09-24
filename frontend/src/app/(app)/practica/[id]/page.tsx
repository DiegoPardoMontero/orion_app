"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft } from "lucide-react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { Suspense } from "react";
import { Cargando, Vacio } from "@/components/estados";
import { Constelacion, formaDe } from "@/components/practica/Constelacion";
import { Juego } from "@/components/practica/Juego";
import { EstadoDelSet, Inicio } from "@/components/practica/Portada";
import { Rigel } from "@/components/Rigel";
import { apiFetch } from "@/lib/api/fetch";
import {
  estrellasDe,
  leerPayload,
  PUNTOS_CONSTELACION_PERFECTA,
  PUNTOS_POR_PRACTICA,
  type Ejercicio,
  type SetDePractica,
} from "@/lib/practica";

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
      return (
        <main className="mx-auto w-full max-w-md px-5 py-6 lg:max-w-xl lg:py-8">
          <Cierre set={s} />
        </main>
      );
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

/**
 * El cierre (B5.3): la constelación completa, los puntos, lo logrado y lo que conviene repasar.
 * Nunca «3 de 5» ni un porcentaje en primer plano: la práctica no es un examen y la pantalla no
 * puede insinuar que lo es. Los logros que se encendieron los celebra el armazón de la app.
 */
function Cierre({ set }: { set: SetDePractica }) {
  const repasar = set.items.filter((i) => i.correct === false && !i.skipped).map((i) => terminoDe(i)).filter(Boolean);
  const minutos = set.estimatedMinutes ?? set.itemCount;
  const total = PUNTOS_POR_PRACTICA + (set.perfect ? PUNTOS_CONSTELACION_PERFECTA : 0);
  return (
    <div className="flex flex-col items-center gap-3 rounded-card bg-surface-raised p-7 text-center shadow-sm">
      <Constelacion
        estados={estrellasDe(set.items)}
        forma={formaDe(set.id)}
        ancho={300}
        r={11}
        lineas
        dibujar
        perfecta={set.perfect}
      />
      <Rigel pose="celebracion" decorativo className="h-20 w-auto" />
      <h1 className="font-display text-h2 font-bold">
        {set.perfect ? "¡Constelación perfecta!" : `Listo. ${minutos} minutos bien usados.`}
      </h1>
      {repasar.length > 0 ? (
        <p className="max-w-[340px] text-[14.5px] text-text-secondary">
          Para repasar antes de tu próxima clase: <strong>{Array.from(new Set(repasar)).join(", ")}</strong>.
        </p>
      ) : (
        <p className="max-w-[340px] text-[14.5px] text-text-secondary">Llegas a tu próxima clase con todo esto fresco.</p>
      )}
      <div className="flex flex-col items-center gap-1">
        <p className="rounded-pill bg-accent-lavender-soft px-4 py-1.5 text-[14px] font-bold text-[#5e4a8a]">
          +{PUNTOS_POR_PRACTICA} puntos por practicar
        </p>
        {set.perfect && (
          <p className="rounded-pill bg-accent-peach-soft px-4 py-1.5 text-[14px] font-bold text-[#8a5a33]">
            +{PUNTOS_CONSTELACION_PERFECTA} por la constelación perfecta
          </p>
        )}
        <p className="mt-1 text-[12.5px] font-semibold text-text-muted">Total: +{total} puntos</p>
      </div>
      <div className="mt-2 flex flex-wrap justify-center gap-2">
        {set.bookingId && (
          <Link
            href={`/mis-clases/${set.bookingId}/acta`}
            className="inline-flex min-h-11 items-center rounded-pill border-[1.5px] border-border px-5 text-[14px] font-bold text-text transition-colors hover:bg-surface-sunken focus-visible:shadow-focus"
          >
            Ver el resumen de la clase
          </Link>
        )}
        <Link
          href="/cuenta?seccion=resumen"
          className="inline-flex min-h-11 items-center rounded-pill bg-primary px-6 text-[15px] font-bold text-on-primary shadow-primary transition-colors hover:bg-primary-strong focus-visible:shadow-focus"
        >
          Volver a mi perfil
        </Link>
      </div>
    </div>
  );
}

function terminoDe(i: Ejercicio): string {
  const p = leerPayload<{ term?: string; say?: string }>(i);
  if (p.term) return p.term;
  if (i.type === "LISTEN_CHOOSE" && p.say) return p.say;
  if (i.type === "DICTATION") return "escribir lo que oyes";
  if (i.type === "SPOT_ERROR") return "encontrar el error";
  if (i.type === "BUILD_SENTENCE") return "armar frases";
  if (i.type === "CHOOSE_REPLY") return "responder en una conversación";
  if (i.type === "FIX_SENTENCE") return "la corrección de frases";
  if (i.type === "ORDER_DIALOGUE") return "el orden de un diálogo";
  if (i.type === "MATCH_MEANING") return "los significados";
  return "completar frases";
}
