"use client";

import { useQuery } from "@tanstack/react-query";
import { CalendarCheck, Clock, CreditCard, ShieldAlert, Wallet } from "lucide-react";
import Link from "next/link";
import { useParams, useSearchParams } from "next/navigation";
import { Suspense } from "react";
import { Cargando, ErrorCarga } from "@/components/estados";
import { LineaImporte } from "@/components/dinero";
import { Boton, BotonPrincipal, Tarjeta } from "@/components/ui";
import { apiFetch } from "@/lib/api/fetch";
import type { PaymentStatusResponse } from "@/lib/api/types";
import { precioCop } from "@/lib/format";

/**
 * La vuelta de la pasarela. Wompi redirige aquí en cuanto el usuario termina, pero "terminar" no
 * es "pagar": con PSE el banco puede tardar minutos en confirmar, y quien manda es el webhook, no
 * esta redirección. Por eso la pantalla consulta el estado en bucle en vez de creerse la URL.
 *
 * Sirve también como sala de espera de una reserva sin pagar: si el pago sigue pendiente ofrece
 * volver a la pasarela con el cupo todavía apartado.
 */
export default function PagoPage() {
  // useSearchParams exige una frontera de Suspense, igual que en /mis-clases.
  return (
    <Suspense fallback={<main className="mx-auto w-full max-w-md px-7 py-8"><Cargando filas={3} /></main>}>
      <EstadoDelPago />
    </Suspense>
  );
}

function EstadoDelPago() {
  const { id } = useParams<{ id: string }>();
  // Wompi devuelve al usuario con ?id=<transacción> en la URL. Se lo pasamos al backend para que
  // le pregunte directamente a la pasarela: es lo que salva el caso del webhook que no llegó.
  const transactionId = useSearchParams().get("id");

  const pago = useQuery({
    queryKey: ["booking", id, "payment", transactionId],
    queryFn: () =>
      apiFetch<PaymentStatusResponse>(
        `/api/v1/bookings/${id}/payment${transactionId ? `?transactionId=${encodeURIComponent(transactionId)}` : ""}`,
      ),
    // Mientras la pasarela no se pronuncie, se vuelve a preguntar cada 3 s. Cuando el pago deja
    // de estar PENDING no hay nada más que esperar y el sondeo se apaga solo.
    refetchInterval: (query) =>
      query.state.data?.paymentStatus === "PENDING" ? 3000 : false,
  });

  if (pago.isPending) {
    return (
      <main className="mx-auto w-full max-w-md px-7 py-8">
        <Cargando filas={3} />
      </main>
    );
  }

  if (pago.isError) {
    return (
      <main className="mx-auto w-full max-w-md px-7 py-8">
        <ErrorCarga
          mensaje="No pudimos consultar el estado de tu pago."
          onReintentar={() => void pago.refetch()}
        />
      </main>
    );
  }

  const estado = pago.data;
  const pendiente = estado.paymentStatus === "PENDING";
  const cabecera = describir(estado);
  const confirmada = estado.paymentStatus === "PAID" || estado.paymentStatus === "RELEASED";

  return (
    <main className="mx-auto w-full max-w-md px-7 py-8">
      <Tarjeta className="text-center">
        <Encabezado {...cabecera} />

        <div className="mt-5 rounded-base border border-border bg-surface-sunken px-4 py-3 text-left text-[13px]">
          <LineaImporte etiqueta="Valor de la clase" valor={precioCop(estado.amountCop)} />
          {estado.creditAppliedCop > 0 && (
            <LineaImporte
              etiqueta="Saldo a favor aplicado"
              valor={`− ${precioCop(estado.creditAppliedCop)}`}
              tono="credito"
            />
          )}
          <LineaImporte tono="total" etiqueta="Pagado con la pasarela" valor={precioCop(estado.chargedCop)} />
        </div>

        <div className="mt-5 grid gap-2.5">
          {pendiente && estado.checkoutUrl && (
            <BotonPrincipal onClick={() => { window.location.href = estado.checkoutUrl!; }}>
              Retomar el pago
              <CreditCard size={18} strokeWidth={1.75} />
            </BotonPrincipal>
          )}

          <Link href="/mis-clases" className="block">
            <Boton variante={confirmada ? "primario" : "contorno"} className="w-full">
              Ir a Mis clases
            </Boton>
          </Link>

          {!confirmada && !pendiente && (
            <Link href="/profesores" className="block">
              <Boton variante="contorno" className="w-full">
                Buscar otro horario
              </Boton>
            </Link>
          )}
        </div>

        {pendiente && estado.expiresAt && (
          <p className="mt-4 flex items-center justify-center gap-1.5 text-[11.5px] text-text-muted">
            <Wallet size={13} strokeWidth={1.75} />
            Te guardamos el cupo hasta las {hora(estado.expiresAt)}.
          </p>
        )}
      </Tarjeta>
    </main>
  );
}

/**
 * Qué se le dice al estudiante, a partir del ESTADO DEL PAGO y no del de la reserva.
 *
 * Antes esto miraba `bookingStatus === "CONFIRMED"` y todo lo demás caía en "el pago no se
 * completó, no se te cobró nada". Eso le mentía a cualquiera que abriera desde el historial una
 * clase ya dictada (COMPLETED) o una devuelta (REFUNDED): sí se le cobró. El dinero lo cuenta el
 * pago, no la agenda.
 */
function describir(estado: PaymentStatusResponse): {
  tono: "menta" | "melocoton" | "coral";
  icono: React.ReactNode;
  titulo: string;
  texto: string;
} {
  switch (estado.paymentStatus) {
    case "PENDING":
      return {
        tono: "melocoton",
        icono: <Clock size={26} strokeWidth={1.75} />,
        titulo: "Estamos esperando tu pago",
        texto:
          "Si pagaste por PSE, tu banco puede tardar unos minutos en confirmarlo. Esta página se actualiza sola.",
      };
    case "PAID":
      return {
        tono: "menta",
        icono: <CalendarCheck size={26} strokeWidth={1.75} />,
        titulo: "¡Tu clase quedó confirmada!",
        texto:
          "Te enviamos el correo con la invitación al calendario y, si es virtual, el enlace de la sala.",
      };
    case "RELEASED":
      return {
        tono: "menta",
        icono: <CalendarCheck size={26} strokeWidth={1.75} />,
        titulo: "Clase dictada",
        texto: "Este pago ya está cerrado. Gracias por estudiar con Orión.",
      };
    case "REFUNDED":
      return {
        tono: "menta",
        icono: <Wallet size={26} strokeWidth={1.75} />,
        titulo: "Te devolvimos el valor de la clase",
        texto:
          "Quedó como saldo a favor y se descuenta solo la próxima vez que reserves. Lo ves en Pagos y saldo.",
      };
    case "DISPUTED":
      return {
        tono: "melocoton",
        icono: <ShieldAlert size={26} strokeWidth={1.75} />,
        titulo: "Estamos revisando este pago",
        texto:
          "Algo no cuadró entre tu pago y esta clase. Ya lo estamos mirando y te escribimos apenas se resuelva.",
      };
    default:
      return {
        tono: "coral",
        icono: <ShieldAlert size={26} strokeWidth={1.75} />,
        titulo: "El pago no se completó",
        texto:
          "No se te cobró nada y el cupo volvió a quedar libre. Puedes elegir otro horario cuando quieras.",
      };
  }
}

function Encabezado({
  tono,
  icono,
  titulo,
  texto,
}: {
  tono: "menta" | "melocoton" | "coral";
  icono: React.ReactNode;
  titulo: string;
  texto: string;
}) {
  const TONOS = {
    menta: "bg-success-bg text-success",
    melocoton: "bg-warning-bg text-warning",
    coral: "bg-primary-soft text-primary-strong",
  } as const;

  return (
    <>
      <span
        aria-hidden="true"
        className={`inline-grid h-14 w-14 place-items-center rounded-full ${TONOS[tono]}`}
      >
        {icono}
      </span>
      <h1 className="mt-4 font-display text-h2 font-bold text-text">{titulo}</h1>
      <p className="mt-2 text-[13.5px] text-text-secondary">{texto}</p>
    </>
  );
}

const hora = (iso: string) =>
  new Intl.DateTimeFormat("es-CO", {
    timeZone: "America/Bogota",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(new Date(iso));
