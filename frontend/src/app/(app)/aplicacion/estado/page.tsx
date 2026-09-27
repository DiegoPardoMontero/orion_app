"use client";

import { Clock, MessageSquare, PencilLine, Rocket, Sparkles } from "lucide-react";
import Link from "next/link";
import { Cargando, ErrorCarga, Vacio } from "@/components/estados";
import { Rigel, type RigelPose } from "@/components/Rigel";
import { Badge, Boton } from "@/components/ui";
import { estadoAplicacion, etiquetaFaltante, useMiAplicacion } from "@/lib/aplicacion";
import { useMe } from "@/lib/auth/session";

/** En escritorio, casi todo el ancho junto al menú (Pardo, 27/09/2026); en el teléfono, una columna. */
const CONTENEDOR = "mx-auto w-full max-w-lg px-5 lg:max-w-[1600px] lg:px-10 xl:px-14";

/**
 * Estado de la postulación del profesor: en qué punto está, qué dijo la revisión y qué sigue. Cada
 * estado lleva su color (nunca solo el color: siempre hay texto) y una acción clara.
 */
export default function EstadoAplicacionPage() {
  const app = useMiAplicacion();
  // Un estudiante ve su postulación (la de antes de un rechazo), pero no la lleva: para enseñar se
  // abre otra cuenta por «Quiero enseñar» (Pardo, 25/09/2026).
  const { data: me } = useMe();
  const puedePostular = me?.role === "TEACHER_APPLICANT" || me?.role === "PROFESSOR";

  if (app.isPending) {
    return (
      <main className={`${CONTENEDOR} py-6 lg:py-8`}>
        <Cargando filas={3} />
      </main>
    );
  }

  // 404 = aún no ha postulado: no es un error, es una invitación a empezar.
  if (app.noAplico && me && !puedePostular) {
    return (
      <main className={`${CONTENEDOR} py-8`}>
        <Vacio
          mascota
          titulo="Tu cuenta es de estudiante"
          texto="Desde aquí no se postula a profesor. Para enseñar en Orión, crea una cuenta aparte con otro correo, desde «Quiero enseñar»."
        />
      </main>
    );
  }

  if (app.noAplico) {
    return (
      <main className={`${CONTENEDOR} py-8`}>
        <Vacio
          mascota
          titulo="Aún no te has postulado"
          texto="¿Quieres enseñar en Orión? Completa tu postulación y nuestro equipo la revisará."
          accion={
            <Link href="/aplicacion">
              <Boton variante="primario" className="h-12">
                Empezar postulación
              </Boton>
            </Link>
          }
        />
      </main>
    );
  }

  if (app.isError) {
    return (
      <main className={`${CONTENEDOR} py-6 lg:py-8`}>
        <ErrorCarga mensaje="No pudimos cargar tu postulación." onReintentar={() => void app.refetch()} />
      </main>
    );
  }

  const vista = app.data!;
  const status = vista.status ?? "DRAFT";
  const cfg = estadoAplicacion(status);
  const faltantes = vista.missing ?? [];

  const pose: RigelPose =
    status === "APPROVED"
      ? "celebracion"
      : status === "REJECTED"
        ? "espera"
        : status === "CHANGES_REQUESTED"
          ? "animo"
          : status === "PENDING_REVIEW" || status === "UNDER_REVIEW"
            ? "espera"
            : "saludo";

  // El plazo lo fija Ajustes, no esta pantalla: si un día deja de cumplirse, se cambia el dato.
  const dias = vista.reviewBusinessDays ?? 3;
  const plazo = `${dias} ${dias === 1 ? "día hábil" : "días hábiles"}`;

  const mensaje: Record<string, string> = {
    DRAFT: `Tu postulación está en borrador. Termina de completarla y envíala a revisión cuando estés listo: la revisamos en un plazo de ${plazo}.`,
    PENDING_REVIEW: `Tu postulación está en la fila de revisión. La revisamos en un plazo de ${plazo} y te avisaremos por correo en cuanto tengamos novedades.`,
    UNDER_REVIEW: `Nuestro equipo está revisando tu postulación. Tendrás respuesta dentro del plazo de ${plazo} que te prometimos.`,
    CHANGES_REQUESTED: "La revisión pide algunos ajustes. Cámbialos y vuelve a enviar tu postulación.",
    APPROVED: "¡Felicidades! Tu postulación fue aprobada. Ya puedes completar y publicar tu perfil de profesor.",
    // Rechazado, el aspirante vuelve a ser estudiante y desde esa cuenta ya no postula (25/09/2026):
    // prometerle «volver a intentarlo» sería mandarlo a una puerta cerrada. El profesor invitado sí puede.
    REJECTED: puedePostular
      ? "Esta vez tu postulación no fue aprobada. Gracias por tu interés; puedes volver a intentarlo más adelante."
      : "Esta vez tu postulación no fue aprobada. Gracias por tu interés; tu cuenta sigue abierta para tomar clases.",
  };

  const accion =
    status === "APPROVED" ? (
      <Link href="/perfil" className="block">
        <Boton variante="primario" className="h-[52px] w-full lg:w-auto lg:px-7">
          <Rocket size={17} strokeWidth={2} />
          Completa y publica tu perfil
        </Boton>
      </Link>
    ) : status === "CHANGES_REQUESTED" && puedePostular ? (
      <Link href="/aplicacion" className="block">
        <Boton variante="primario" className="h-[52px] w-full lg:w-auto lg:px-7">
          <PencilLine size={17} strokeWidth={2} />
          Editar y reenviar
        </Boton>
      </Link>
    ) : status === "DRAFT" && puedePostular ? (
      <Link href="/aplicacion" className="block">
        <Boton variante="primario" className="h-[52px] w-full lg:w-auto lg:px-7">
          <Sparkles size={17} strokeWidth={2} />
          Continuar postulación
        </Boton>
      </Link>
    ) : null;

  const nota = vista.decisionNote && (status === "CHANGES_REQUESTED" || status === "REJECTED");
  const pendientes = status === "DRAFT" && faltantes.length > 0;

  return (
    <main className={`${CONTENEDOR} py-6 lg:py-8`}>
      <p className="text-[12px] font-bold uppercase tracking-[0.1em] text-primary-strong">Mi postulación</p>
      <h1 className="mt-2 font-display text-h1 font-bold">Estado de tu postulación</h1>

      <div className="mt-5 flex flex-col gap-5 rounded-card bg-surface-raised p-5 shadow-sm lg:flex-row lg:items-center lg:gap-8 lg:p-8">
        <div className="flex min-w-0 flex-1 items-center gap-4 lg:gap-6">
          <Rigel pose={pose} decorativo className="h-20 w-auto shrink-0 lg:h-32" />
          <div className="min-w-0">
            <Badge tono={cfg.tono} punto={cfg.punto}>
              {cfg.label}
            </Badge>
            <p className="mt-2 max-w-[70ch] text-[13.5px] leading-relaxed text-text-secondary lg:text-[15px]">
              {mensaje[status] ?? ""}
            </p>
          </div>
        </div>
        {accion && <div className="shrink-0">{accion}</div>}
      </div>

      {(nota || pendientes) && (
        <div className="mt-4 grid gap-4 lg:grid-cols-2 lg:gap-6">
          {/* Devolución del revisor: solo cuando la hay y aporta (cambios o rechazo). */}
          {nota && (
            <div className="rounded-card bg-primary-soft p-4 lg:p-5">
              <p className="flex items-center gap-2 text-[12.5px] font-bold text-primary-strong">
                <MessageSquare size={15} strokeWidth={2} />
                Comentario de la revisión
              </p>
              <p className="mt-1.5 max-w-[75ch] whitespace-pre-line text-[13.5px] leading-relaxed text-text">
                {vista.decisionNote}
              </p>
            </div>
          )}

          {/* Requisitos pendientes mientras siga en borrador. */}
          {pendientes && (
            <div className="rounded-card bg-warning-bg p-4 lg:p-5">
              <p className="text-[12.5px] font-bold text-warning">Te falta por completar</p>
              <ul className="mt-2 grid gap-1.5 sm:grid-cols-2">
                {faltantes.map((f) => (
                  <li key={f} className="flex items-center gap-2 text-[13px] text-warning">
                    <Clock size={14} strokeWidth={2} className="shrink-0" />
                    {etiquetaFaltante(f)}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
    </main>
  );
}
