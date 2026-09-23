"use client";

import { MessageCircle } from "lucide-react";
import { useId, useState } from "react";
import { useContacto } from "@/lib/soporte";
import { diasHabiles, horas, minutos, useCifras } from "@/lib/cifras";
import type { PublicFigures } from "@/lib/api/types";

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
type Lista = "estudiante" | "profesor" | "general" | "portadaEstudiante" | "portadaProfesor";

const base = (c: PublicFigures): Record<"estudiante" | "profesor" | "general", Pregunta[]> => ({
  estudiante: [
    {
      p: "¿Cómo reservo una clase?",
      r: `Busca un profesor, abre su perfil y elige día y hora entre los cupos libres. Las clases duran ${minutos(c.classMinutes)} y empiezan en punto, en hora de Bogotá.`,
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
      r: `Cancelar se puede siempre. Con más de ${horas(c.studentCancelHours)} por delante recuperas el valor completo; dentro de las últimas ${horas(c.studentCancelHours)} no hay devolución, porque tu profesor ya apartó esa hora y no puede dársela a nadie más.`,
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
      r: `En «Mis clases», pestaña de pasadas, usa «Reportar un problema». Puedes hacerlo desde ${minutos(c.noShowReportMinutes)} después de la hora de inicio y hasta ${horas(c.disputeReportWindowHours)} después de que termine. El pago queda congelado hasta que lo revisemos.`,
    },
    {
      p: "¿Puedo cambiar la hora de una clase?",
      r: `Sí, con «Proponer otro horario». La clase no se mueve hasta que tu profesor acepte, y el pago no se toca. Se puede incluso dentro de las ${horas(c.studentCancelHours)}: es la salida de quien ya no alcanza a cancelar.`,
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
      r: `El ${c.commissionPercent} % del precio de la clase. Lo ves desglosado al fijar tu tarifa y clase por clase en «Ganancias». La comisión se calcula siempre sobre el precio, aunque el estudiante pague con saldo.`,
    },
    {
      p: "¿Cuándo me pagan?",
      r: "El dinero queda retenido hasta que la clase se dicta; ahí pasa a estar disponible para la siguiente liquidación. Las liquidaciones y la transferencia las hace una persona: no es automático.",
    },
    {
      p: "¿Qué pasa si tengo que cancelar?",
      r: `Cancelar se puede siempre. Con más de ${horas(c.professorCancelHours)} no tiene consecuencias para ti. Con menos, tu estudiante recupera todo igual, tú no cobras esa clase y queda registrado: las cancelaciones de último momento repetidas pesan en tu perfil.`,
    },
    {
      p: "¿Y si el estudiante no llega?",
      r: `Regístralo como inasistencia en «Mis clases». Cobras igual: apartaste tu hora y estuviste ahí. Si no registras nada, el sistema cierra la clase solo a las ${horas(c.autoCompleteHours)} y libera el pago de todos modos.`,
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
      r: "Una academia de inglés en línea: eliges profesor, reservas la hora que te sirve y das la clase por videollamada. Sin paquetes obligatorios ni matrícula.",
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
      r: "Sí. El cobro lo procesa Wompi (PSE, tarjeta o Nequi) y los datos de tu tarjeta nunca pasan por los servidores de Orión.",
    },
    {
      p: "¿Puedo cancelar una clase?",
      r: `Siempre. Con más de ${horas(c.studentCancelHours)} por delante recuperas el valor completo; dentro de las últimas ${horas(c.studentCancelHours)} la clase se considera prestada y no hay devolución.`,
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
});

/**
 * Las de la portada, con las preguntas que propuso Sofía (23/09/2026). Donde la regla ya tenía
 * respuesta, se reutiliza tal cual: una regla, un texto.
 */
const preguntas = (c: PublicFigures): Record<Lista, Pregunta[]> => {
  const b = base(c);
  return {
    ...b,
    portadaEstudiante: [
      { p: "¿Cuánto cuesta una clase?", r: respuesta(b.general, "¿Cuánto cuesta una clase?") },
      {
        p: "¿Tengo que comprar un paquete o puedo tomar una sola clase?",
        r: "Una sola: no hay paquetes, mensualidades ni renovaciones. Pagas cada clase al reservarla.",
      },
      { p: "¿Qué pasa si necesito cancelar?", r: respuesta(b.estudiante, "¿Qué pasa si cancelo?") },
      { p: "¿Qué pasa si mi profesor no se presenta?", r: respuesta(b.estudiante, "Mi profesor no llegó. ¿Qué hago?") },
      {
        p: "¿Con cuánta anticipación debo reservar?",
        r: `Con al menos ${horas(c.bookingMinLeadHours)}: los cupos más cercanos ya no se ofrecen, para que tu profesor alcance a preparar la clase.`,
      },
      {
        p: "¿Cómo son las clases? ¿Qué plataforma usan?",
        r: `En vivo y uno a uno, de ${minutos(c.classMinutes)}, por videollamada dentro de Orión. Al confirmarse la clase te llega el enlace por correo, y también la encuentras en «Mis clases», con el botón «Unirse a la clase».`,
      },
      {
        p: "¿Qué es el diagnóstico inicial y es obligatorio?",
        r: `Una conversación de ${minutos(c.assessmentMinutes)} en inglés, gratis y sin crear cuenta. Al terminar ves tu punto de partida y tienes tres profesores elegidos por lo que contaste. No es obligatorio: puedes reservar sin hacerlo.`,
      },
      {
        p: "¿Qué es el Método ORION™?",
        r: "Nuestro sistema de seguimiento. Tu profesor enseña a su manera; al terminar cada clase deja un resumen de lo que trabajaron, lo que conviene tener presente y lo que sigue, y de ese resumen salen ejercicios de práctica hechos para ti hasta la próxima clase.",
      },
      {
        p: "¿Puedo cambiar de profesor?",
        r: "Cuando quieras: reservas tu próxima clase con otro profesor. No hay contratos ni paquetes que te aten a nadie.",
      },
      {
        p: "¿Cómo pago? ¿Es seguro?",
        r: "Por Wompi, con PSE, tarjeta o Nequi. El cobro lo procesa Wompi y los datos de tu tarjeta nunca pasan por los servidores de Orión.",
      },
      {
        p: "¿Qué datos usan si entro con Google o Facebook?",
        r: "Solo tu nombre, tu correo y el identificador de tu cuenta, para crear tu cuenta de Orión Idiomas e iniciar sesión. No publicamos nada en tu nombre ni accedemos a ningún otro dato de tu cuenta de Google o Facebook.",
      },
      {
        p: "Soy menor de edad, ¿puedo tomar clases?",
        r: "No. Orión es solo para mayores de 18 años: la ley colombiana exige la autorización del representante legal para tratar datos de menores, y ese trámite no existe en la plataforma.",
      },
    ],
    portadaProfesor: [
      {
        p: "¿Cómo me postulo y cuánto tarda la aprobación?",
        r: `Con «Postúlate como profesor»: completas tu perfil, tus idiomas y tus documentos, y una persona revisa tu postulación en ${diasHabiles(c.applicationReviewBusinessDays)}. Puede aprobarse, rechazarse o volver con cambios pedidos; en los tres casos te escribimos.`,
      },
      { p: "¿Cuánto cobra Orión de comisión?", r: respuesta(b.profesor, "¿Cuánto retiene Orión?") },
      {
        p: "¿Yo pongo mi tarifa?",
        r: "Sí. La fijas tú, entre $20.000 y $500.000 por clase, y la cambias cuando quieras. El estudiante la ve en tu perfil antes de reservar.",
      },
      { p: "¿Cuándo y cómo me pagan?", r: respuesta(b.profesor, "¿Cuándo me pagan?") },
      { p: "¿Qué pasa si un estudiante no se presenta?", r: respuesta(b.profesor, "¿Y si el estudiante no llega?") },
      {
        p: "¿Qué pasa si yo no puedo presentarme a una clase?",
        r: `${respuesta(b.profesor, "¿Qué pasa si tengo que cancelar?")} Si no te presentas sin cancelar, tu estudiante puede reportarlo: si se confirma, recupera su dinero y la ausencia queda en tu registro.`,
      },
      {
        p: "¿Qué necesito para usar el Método ORION™?",
        r: "Nada aparte de dar tu clase. Al terminar, escribes o dictas en un minuto lo que viste; armamos el resumen, tú lo revisas y lo publicas, y de ahí salen los ejercicios de práctica de tu estudiante.",
      },
      {
        p: "¿Tengo que seguir una metodología específica?",
        r: "No. Enseñas con tu estilo y tu material. El Método ORION™ es el marco del seguimiento después de la clase, no de la clase misma.",
      },
      {
        p: "¿Qué datos usan si entro con Google o Facebook?",
        r: "Solo tu nombre, tu correo y el identificador de tu cuenta, para crear tu cuenta de Orión Idiomas e iniciar sesión. No publicamos nada en tu nombre ni accedemos a ningún otro dato de tu cuenta de Google o Facebook.",
      },
      {
        p: "¿Puedo traer a mis propios estudiantes?",
        r: "Sí: comparte el enlace de tu perfil. Reservan y pagan por Orión, y aplica la misma comisión que a cualquier clase.",
      },
    ],
  };
};

const TITULO: Record<"estudiante" | "profesor" | "general", string> = {
  estudiante: "Preguntas frecuentes",
  profesor: "Preguntas frecuentes",
  general: "Preguntas frecuentes",
};

/** La respuesta de otra lista, para no mantener dos versiones de la misma regla. */
const respuesta = (lista: Pregunta[], pregunta: string) => lista.find((q) => q.p === pregunta)!.r;

export function PreguntasFrecuentes({
  rol,
  className = "",
}: {
  rol: "estudiante" | "profesor" | "general";
  className?: string;
}) {
  const contacto = useContacto();
  const cifras = useCifras();
  const lista = preguntas(cifras)[rol];

  return (
    <section className={`mt-8 ${className}`}>
      <h2 className="font-display text-[19px] font-bold">{TITULO[rol]}</h2>

      <div className="mt-3 grid gap-2">
        {lista.map((pregunta) => (
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

/**
 * Las preguntas de la portada, en dos pestañas (Sofía, 23/09/2026): el estudiante y el profesor no
 * se hacen las mismas, y una lista única obliga a leer la mitad que no toca. Debajo, la salida
 * humana por WhatsApp.
 */
export function PreguntasDeLaPortada() {
  const contacto = useContacto();
  const cifras = useCifras();
  const listas = preguntas(cifras);
  const [activa, setActiva] = useState<"portadaEstudiante" | "portadaProfesor">("portadaEstudiante");
  const base = useId();
  const pestanas = [
    { id: "portadaEstudiante", etiqueta: "Estudiantes" },
    { id: "portadaProfesor", etiqueta: "Profesores" },
  ] as const;

  return (
    <section>
      <h2 className="text-center font-display text-h2 font-bold">Preguntas frecuentes</h2>
      <div role="tablist" aria-label="Preguntas frecuentes" className="mx-auto mt-5 flex w-fit gap-1 rounded-pill bg-surface-sunken p-1">
        {pestanas.map((p) => (
          <button
            key={p.id}
            type="button"
            role="tab"
            id={`${base}-${p.id}`}
            aria-selected={p.id === activa}
            aria-controls={`${base}-panel`}
            onClick={() => setActiva(p.id)}
            className={`min-h-11 rounded-pill px-5 text-[14.5px] font-bold transition-colors focus-visible:shadow-focus ${
              p.id === activa ? "bg-surface-raised text-text shadow-sm" : "text-text-secondary hover:text-text"
            }`}
          >
            {p.etiqueta}
          </button>
        ))}
      </div>

      <div role="tabpanel" id={`${base}-panel`} aria-labelledby={`${base}-${activa}`} className="mt-5 grid gap-2">
        {listas[activa].map((pregunta) => (
          <details
            key={pregunta.p}
            className="group rounded-card border border-border bg-surface-raised px-4 py-3.5 open:bg-surface"
          >
            <summary className="flex cursor-pointer list-none items-baseline gap-3 text-[14px] font-bold text-text focus-visible:shadow-focus">
              <span className="min-w-0 flex-1">{pregunta.p}</span>
              <span aria-hidden="true" className="shrink-0 font-mono text-[15px] leading-none text-text-muted">
                <span className="group-open:hidden">+</span>
                <span className="hidden group-open:inline">−</span>
              </span>
            </summary>
            <p className="mt-2 text-[13.5px] leading-relaxed text-text-secondary">{pregunta.r}</p>
          </details>
        ))}
      </div>

      {contacto.data && (
        <p className="mt-5 text-center text-[14px] text-text-secondary">
          ¿No encuentras lo que buscas?{" "}
          <a
            href={`https://wa.me/${contacto.data.whatsappDigits}`}
            target="_blank"
            rel="noreferrer noopener"
            className="font-bold text-success hover:underline"
          >
            Escríbenos por WhatsApp
          </a>{" "}
          — te respondemos directamente.
        </p>
      )}
    </section>
  );
}
