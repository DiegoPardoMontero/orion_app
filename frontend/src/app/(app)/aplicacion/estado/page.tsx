"use client";

import { Clock, MessageSquare, PencilLine, Rocket, Sparkles } from "lucide-react";
import Link from "next/link";
import { Cargando, ErrorCarga, Vacio } from "@/components/estados";
import { Rigel, type RigelPose } from "@/components/Rigel";
import { Badge, Boton } from "@/components/ui";
import { estadoAplicacion, etiquetaFaltante, useMiAplicacion } from "@/lib/aplicacion";

/**
 * Estado de la postulación del profesor: en qué punto está, qué dijo la revisión y qué sigue. Cada
 * estado lleva su color (nunca solo el color: siempre hay texto) y una acción clara.
 */
export default function EstadoAplicacionPage() {
  const app = useMiAplicacion();

  if (app.isPending) {
    return (
      <main className="mx-auto w-full max-w-lg px-5 py-6">
        <Cargando filas={3} />
      </main>
    );
  }

  // 404 = aún no ha postulado: no es un error, es una invitación a empezar.
  if (app.noAplico) {
    return (
      <main className="mx-auto w-full max-w-lg px-5 py-8">
        <Vacio
          mascota
          titulo="Aún no has postulado"
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
      <main className="mx-auto w-full max-w-lg px-5 py-6">
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

  const mensaje: Record<string, string> = {
    DRAFT: "Tu postulación está en borrador. Termina de completarla y envíala a revisión cuando estés listo.",
    PENDING_REVIEW: "Tu postulación está en la fila de revisión. Te avisaremos por correo en cuanto tengamos novedades.",
    UNDER_REVIEW: "Nuestro equipo está revisando tu postulación. Muy pronto tendrás respuesta.",
    CHANGES_REQUESTED: "La revisión pide algunos ajustes. Cámbialos y vuelve a enviar tu postulación.",
    APPROVED: "¡Felicidades! Tu postulación fue aprobada. Ya puedes completar y publicar tu perfil de profesor.",
    REJECTED: "Esta vez tu postulación no fue aprobada. Gracias por tu interés; puedes volver a intentarlo más adelante.",
  };

  return (
    <main className="mx-auto w-full max-w-lg px-5 py-6 lg:py-8">
      <p className="text-[12px] font-bold uppercase tracking-[0.1em] text-primary-strong">Mi solicitud</p>
      <h1 className="mt-2 font-display text-h1 font-bold">Estado de tu postulación</h1>

      <div className="mt-5 flex items-center gap-4 rounded-card bg-surface-raised p-5 shadow-sm">
        <Rigel pose={pose} decorativo className="h-20 w-auto shrink-0" />
        <div className="min-w-0">
          <Badge tono={cfg.tono} punto={cfg.punto}>
            {cfg.label}
          </Badge>
          <p className="mt-2 text-[13.5px] leading-relaxed text-text-secondary">{mensaje[status] ?? ""}</p>
        </div>
      </div>

      {/* Devolución del revisor: solo cuando la hay y aporta (cambios o rechazo). */}
      {vista.decisionNote && (status === "CHANGES_REQUESTED" || status === "REJECTED") && (
        <div className="mt-4 rounded-card bg-primary-soft p-4">
          <p className="flex items-center gap-2 text-[12.5px] font-bold text-primary-strong">
            <MessageSquare size={15} strokeWidth={2} />
            Comentario de la revisión
          </p>
          <p className="mt-1.5 whitespace-pre-line text-[13.5px] leading-relaxed text-text">{vista.decisionNote}</p>
        </div>
      )}

      {/* Requisitos pendientes mientras siga en borrador. */}
      {status === "DRAFT" && faltantes.length > 0 && (
        <div className="mt-4 rounded-card bg-warning-bg p-4">
          <p className="text-[12.5px] font-bold text-warning">Te falta por completar</p>
          <ul className="mt-2 space-y-1.5">
            {faltantes.map((f) => (
              <li key={f} className="flex items-center gap-2 text-[13px] text-warning">
                <Clock size={14} strokeWidth={2} className="shrink-0" />
                {etiquetaFaltante(f)}
              </li>
            ))}
          </ul>
        </div>
      )}

      <div className="mt-6">
        {status === "APPROVED" && (
          <Link href="/perfil">
            <Boton variante="primario" className="h-[52px] w-full">
              <Rocket size={17} strokeWidth={2} />
              Completa y publica tu perfil
            </Boton>
          </Link>
        )}
        {status === "CHANGES_REQUESTED" && (
          <Link href="/aplicacion">
            <Boton variante="primario" className="h-[52px] w-full">
              <PencilLine size={17} strokeWidth={2} />
              Editar y reenviar
            </Boton>
          </Link>
        )}
        {status === "DRAFT" && (
          <Link href="/aplicacion">
            <Boton variante="primario" className="h-[52px] w-full">
              <Sparkles size={17} strokeWidth={2} />
              Continuar postulación
            </Boton>
          </Link>
        )}
      </div>
    </main>
  );
}
