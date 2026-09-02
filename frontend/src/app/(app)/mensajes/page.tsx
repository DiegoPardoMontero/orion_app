"use client";

import { MessagesSquare } from "lucide-react";
import { ListaConversaciones } from "./lista";

/**
 * Bandeja de mensajes. En móvil es la lista a pantalla completa; al tocar una conversación se
 * navega a `/mensajes/[id]`. En desktop se muestra a dos columnas: la lista a la izquierda y, como
 * aún no hay hilo abierto, una invitación amable a la derecha.
 */
export default function MensajesPage() {
  return (
    <main className="mx-auto w-full max-w-md px-5 py-6 lg:max-w-5xl lg:px-12 lg:py-8">
      <h1 className="font-display text-h1 font-bold">Mensajes</h1>
      <p className="mt-1 text-[13.5px] text-text-secondary">
        Coordina tus clases aquí. Por tu seguridad, todo queda dentro de Orión.
      </p>

      <div className="mt-5 lg:grid lg:grid-cols-[340px_minmax(0,1fr)] lg:gap-8">
        <div>
          <ListaConversaciones />
        </div>

        <div className="hidden place-items-center rounded-card bg-surface-raised p-10 text-center shadow-sm lg:grid">
          <div>
            <div className="mx-auto grid h-16 w-16 place-items-center rounded-full bg-primary-soft text-primary">
              <MessagesSquare size={28} strokeWidth={1.75} />
            </div>
            <p className="mt-4 font-display text-[18px] font-bold text-text">
              Selecciona una conversación
            </p>
            <p className="mt-1 text-[13.5px] text-text-secondary">
              Elige un chat de la izquierda para ver el hilo y responder.
            </p>
          </div>
        </div>
      </div>
    </main>
  );
}
