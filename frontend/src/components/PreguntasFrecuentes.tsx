"use client";

import { MessageCircle } from "lucide-react";
import { useContacto } from "@/lib/soporte";

type Pregunta = { p: string; r: string };

/**
 * Las dudas de cada rol, contestadas donde surgen.
 *
 * <p>Se separan por rol porque las preguntas no se parecen: el estudiante quiere saber qué pasa con
 * su dinero y cómo entra a clase; el profesor, cuándo cobra y por qué no aparece en el buscador.
 * Una lista única obligaría a los dos a leer la mitad que no les toca.
 *
 * <p>Debajo, siempre, la salida humana. Una sección de preguntas frecuentes que no ofrece hablar
 * con alguien es un callejón sin salida para quien tiene justo la pregunta que no está.
 */
const PREGUNTAS: Record<"estudiante" | "profesor" | "general", Pregunta[]> = {
  estudiante: [
    {
      p: "¿Cómo reservo una clase?",
      r: "Busca un profesor, abre su perfil y elige día y hora entre los cupos libres. Las clases duran 60 minutos y empiezan en punto, en hora de Bogotá.",
    },
    {
      p: "Reservé y no me confirmó la clase. ¿Por qué?",
      r: "Reservar aparta el horario, pero la clase se confirma cuando entra el pago. Tienes 20 minutos para pagarlo; si no, la reserva se cancela sola y no se te cobra nada.",
    },
    {
      p: "¿Cómo pago?",
      r: "Por Wompi, con PSE, tarjeta o Nequi. Los datos de tu tarjeta nunca pasan por Orión. Si pagas por PSE, la confirmación puede tardar unos minutos: la pantalla se queda esperando y se actualiza sola.",
    },
    {
      p: "¿Qué pasa si cancelo?",
      r: "Cancelar se puede siempre. Con más de 12 horas por delante recuperas el valor completo; dentro de las últimas 12 horas no hay devolución, porque tu profesor ya apartó esa hora y no puede dársela a nadie más.",
    },
    {
      p: "¿Puedo pedir que me devuelvan el dinero a la tarjeta en vez de saldo?",
      r: "Sí, si estás dentro de los 5 días hábiles siguientes a la reserva y la clase no ha empezado: es tu derecho de retracto. Al cancelar eliges adónde va el dinero. Al medio de pago puede tardar hasta 15 días calendario; a tu saldo es inmediato.",
    },
    {
      p: "¿Qué es el saldo a favor?",
      r: "Dinero que Orión te debe y que se descuenta solo la próxima vez que reserves. Sale de una clase cancelada, de un reclamo resuelto a tu favor o de un ajuste nuestro. Lo ves en «Pagos y saldo».",
    },
    {
      p: "¿Cómo entro a la clase?",
      r: "Todas las clases son virtuales. Al confirmarse creamos la sala y te llega el enlace por correo, con su invitación de calendario; también está en «Mis clases», en el botón «Unirse a la clase».",
    },
    {
      p: "Mi profesor no llegó. ¿Qué hago?",
      r: "En «Mis clases», pestaña de pasadas, usa «Reportar un problema». Puedes hacerlo desde 15 minutos después de la hora de inicio y hasta 24 horas después de que termine. El pago queda congelado hasta que lo revisemos.",
    },
    {
      p: "¿Puedo cambiar la hora de una clase?",
      r: "Sí, con «Proponer otro horario». La clase no se mueve hasta que tu profesor acepte, y el pago no se toca. Se puede incluso dentro de las 12 horas: es la salida de quien ya no alcanza a cancelar.",
    },
    {
      p: "¿Qué es una semana protegida?",
      r: "Una semana sin clase que no te rompe la racha. Se aplica sola, una vez al mes, y no hay que pedirla. Puentea el hueco pero no cuenta como clase.",
    },
  ],
  profesor: [
    {
      p: "¿Cuánto tarda mi postulación?",
      r: "La revisa una persona. Puede aprobarse, rechazarse o volver con cambios pedidos; en los tres casos te escribimos. Mientras espera, tu cuenta es de aspirante: puedes editar tu postulación pero todavía no recibir reservas.",
    },
    {
      p: "No aparezco en el buscador. ¿Por qué?",
      r: "Hacen falta tres cosas a la vez: postulación aprobada, tarifa por hora fijada y perfil publicado. Si falta una, no apareces.",
    },
    {
      p: "¿Cuánto retiene Orión?",
      r: "El 20 % del precio de la clase. Lo ves desglosado al fijar tu tarifa y clase por clase en «Ganancias». La comisión se calcula siempre sobre el precio, aunque el estudiante pague con saldo.",
    },
    {
      p: "¿Cuándo me pagan?",
      r: "El dinero queda retenido hasta que la clase se dicta; ahí pasa a estar disponible para la siguiente liquidación. Las liquidaciones y la transferencia las hace una persona: no es automático.",
    },
    {
      p: "¿Qué pasa si tengo que cancelar?",
      r: "Cancelar se puede siempre. Con más de 12 horas no tiene consecuencias para ti. Con menos, tu estudiante recupera todo igual, tú no cobras esa clase y queda registrado: las cancelaciones de último momento repetidas pesan en tu perfil.",
    },
    {
      p: "¿Y si el estudiante no llega?",
      r: "Regístralo como inasistencia en «Mis clases». Cobras igual: apartaste tu hora y estuviste ahí. Si no registras nada, el sistema cierra la clase solo a las 24 horas y libera el pago de todos modos.",
    },
    {
      p: "¿Cómo abro mi disponibilidad?",
      r: "En «Disponibilidad» defines franjas semanales que se repiten, y excepciones para los días sueltos. Los cupos salen alineados a la hora y nunca se ofrecen horas ya empezadas.",
    },
    {
      p: "Me pusieron tarifa en cero. ¿Qué significa?",
      r: "Que administración te dejó en clases gratuitas para probar el flujo completo sin mover dinero. Tus clases se muestran como «Gratis» y se reservan sin pasar por la pasarela.",
    },
  ],
  general: [
    {
      p: "¿Qué es Orión?",
      r: "Una academia de idiomas en línea: eliges profesor, reservas la hora que te sirve y das la clase por videollamada. Sin paquetes obligatorios ni matrícula.",
    },
    {
      p: "¿Las clases son presenciales o virtuales?",
      r: "Todas son virtuales. Al confirmar tu clase creamos la sala y te llega el enlace por correo.",
    },
    {
      p: "¿Cuánto cuesta una clase?",
      r: "Cada profesor pone su tarifa por hora, y la ves en su perfil antes de reservar. No hay costos de inscripción ni mensualidades: pagas la clase que reservas.",
    },
    {
      p: "¿Cómo elijo profesor?",
      r: "En el buscador puedes filtrar por idioma, nivel, objetivo, precio, días y franja horaria. Cada perfil muestra su experiencia, sus idiomas y las reseñas de sus estudiantes.",
    },
    {
      p: "¿Es seguro pagar?",
      r: "Sí. El cobro lo procesa Wompi —PSE, tarjeta o Nequi— y los datos de tu tarjeta nunca pasan por los servidores de Orión.",
    },
    {
      p: "¿Puedo cancelar una clase?",
      r: "Siempre. Con más de 12 horas por delante recuperas el valor completo; dentro de las últimas 12 horas la clase se considera prestada y no hay devolución.",
    },
    {
      p: "¿Hay edad mínima?",
      r: "Sí: Orión es solo para mayores de 18 años. La ley colombiana exige la autorización del representante legal para tratar datos de menores, y ese trámite no existe en la plataforma.",
    },
    {
      p: "Quiero dar clases en Orión. ¿Cómo hago?",
      r: "Postúlate desde «Enseña en Orión». Completas tu perfil, tus idiomas y tus documentos, y una persona revisa tu postulación antes de publicarte.",
    },
  ],
};

const TITULO: Record<"estudiante" | "profesor" | "general", string> = {
  estudiante: "Preguntas frecuentes",
  profesor: "Preguntas frecuentes",
  general: "Preguntas frecuentes",
};

export function PreguntasFrecuentes({
  rol,
  className = "",
}: {
  rol: "estudiante" | "profesor" | "general";
  className?: string;
}) {
  const contacto = useContacto();
  const preguntas = PREGUNTAS[rol];

  return (
    <section className={`mt-8 ${className}`}>
      <h2 className="font-display text-[19px] font-bold">{TITULO[rol]}</h2>

      <div className="mt-3 grid gap-2">
        {preguntas.map((pregunta) => (
          <details
            key={pregunta.p}
            className="group rounded-card border border-border bg-surface-raised px-4 py-3.5 open:bg-surface"
          >
            <summary className="flex cursor-pointer list-none items-baseline gap-3 text-[13.5px] font-bold text-text focus-visible:shadow-focus">
              <span className="min-w-0 flex-1">{pregunta.p}</span>
              <span
                aria-hidden="true"
                className="shrink-0 font-mono text-[15px] leading-none text-text-muted"
              >
                <span className="group-open:hidden">+</span>
                <span className="hidden group-open:inline">−</span>
              </span>
            </summary>
            <p className="mt-2 text-[13px] leading-relaxed text-text-secondary">{pregunta.r}</p>
          </details>
        ))}
      </div>

      {contacto.data && (
        <a
          href={`https://wa.me/${contacto.data.whatsappDigits}`}
          target="_blank"
          rel="noreferrer noopener"
          className="mt-3 flex items-center gap-3 rounded-card border border-border bg-surface-raised p-4 transition-colors hover:border-border-strong focus-visible:shadow-focus"
        >
          <span className="grid h-10 w-10 shrink-0 place-items-center rounded-full bg-success-bg text-success">
            <MessageCircle size={19} strokeWidth={1.75} />
          </span>
          <span className="min-w-0">
            <span className="block text-[14px] font-bold">
              ¿No está tu pregunta? Comunícate con el administrador
            </span>
            <span className="block text-[12.5px] text-text-muted">
              Por WhatsApp · {contacto.data.horario}
            </span>
          </span>
        </a>
      )}
    </section>
  );
}
