import type { Metadata } from "next";
import Link from "next/link";
import { Clock, MessageCircle, ShieldCheck, Sparkles, Users } from "lucide-react";
import { NavPublica } from "@/components/NavPublica";
import { Rigel } from "@/components/Rigel";

/**
 * El diagnóstico de confianza: la puerta a la que apunta el héroe de la portada.
 *
 * <p>Pública a propósito: es el destino del héroe de la portada y tiene que abrirse sin sesión. La
 * conversación en sí vive en {@code /diagnostico/empezar}, dentro de la aplicación, porque necesita
 * cuenta —el resultado es de alguien— y consentimiento de voz.
 */
export const metadata: Metadata = {
  title: "Prueba tu inglés en 2 minutos · Orión",
  description:
    "Una conversación corta, sin preguntas de examen. Recibe tu Confidence Score, un diagnóstico escrito de cómo hablas y tres profesores elegidos por lo que contaste. Gratis.",
  alternates: { canonical: "/diagnostico" },
};

const PASOS = [
  {
    icono: MessageCircle,
    titulo: "Hablas dos minutos",
    texto:
      "Una conversación normal con Meissa, la voz de Orión. No hay preguntas de examen ni respuestas correctas, y nadie te corrige mientras hablas.",
  },
  {
    icono: Sparkles,
    titulo: "Recibes tu Confidence Score",
    texto:
      "No es un nivel del MCER. Mide la confianza al hablar: cuánto tardas en arrancar, cuántas frases sueltas a medias, cuándo te devuelves al español.",
  },
  {
    icono: Users,
    titulo: "Y tres profesores",
    texto:
      "Elegidos por lo que contaste en la conversación, no por un catálogo genérico. Con su agenda real, para reservar si quieres.",
  },
];

export default function DiagnosticoPage() {
  return (
    <>
      <NavPublica />

      <header className="gradient-dawn">
        <div className="mx-auto max-w-4xl px-5 py-12 text-center lg:py-16">
          <p className="text-[12px] font-bold uppercase tracking-[0.16em] text-on-primary/80">
            Gratis · Dos minutos · Sin examen
          </p>
          <h1 className="mx-auto mt-3 max-w-[16ch] font-display text-[34px] font-bold leading-[1.08] text-on-primary lg:text-[48px]">
            Prueba tu inglés en 2 minutos.
          </h1>
          <p className="mx-auto mt-4 max-w-[54ch] text-[15px] leading-relaxed text-on-primary/85 lg:text-[17px]">
            Sin examen, sin nota y sin que nadie te corrija. Una conversación corta que termina
            diciéndote cómo hablas de verdad, y con quién seguir.
          </p>
          <Rigel pose="animo" className="mx-auto mt-7 h-[150px] w-auto drop-shadow-2xl" />
        </div>
      </header>

      <main className="mx-auto max-w-4xl px-5 py-12 lg:py-16">
        <ol className="grid gap-4 md:grid-cols-3">
          {PASOS.map(({ icono: Icono, titulo, texto }, i) => (
            <li key={titulo} className="rounded-card bg-surface-raised p-6 shadow-sm">
              <span className="grid h-10 w-10 place-items-center rounded-full bg-primary-soft text-primary-strong">
                <Icono size={19} strokeWidth={2} />
              </span>
              <p className="mt-4 text-[11px] font-bold uppercase tracking-[0.1em] text-text-muted">
                Paso {i + 1}
              </p>
              <p className="mt-1 font-display text-[19px] font-bold">{titulo}</p>
              <p className="mt-1.5 text-[14px] leading-relaxed text-text-secondary">{texto}</p>
            </li>
          ))}
        </ol>

        <div className="mt-10 rounded-card border border-border bg-surface-raised p-7 text-center">
          <p className="inline-flex items-center gap-2 rounded-pill bg-accent-peach-soft px-3.5 py-1.5 text-[12.5px] font-bold text-[#8a5a33]">
            <Clock size={14} strokeWidth={2.2} />
            Dos minutos, y ya
          </p>
          <p className="mx-auto mt-4 max-w-[48ch] text-[15px] leading-relaxed text-text-secondary">
            Necesitas una cuenta (el resultado es tuyo y queda guardado) y tu permiso para procesar
            la voz. Te lo pedimos justo antes de empezar, en una frase.
          </p>
          <div className="mt-6 flex flex-col justify-center gap-3 sm:flex-row">
            <Link
              href="/diagnostico/empezar"
              className="inline-flex h-[52px] items-center justify-center rounded-pill bg-primary px-7 text-[15px] font-bold text-on-primary shadow-primary transition-colors hover:bg-primary-strong focus-visible:shadow-focus"
            >
              Empezar mi diagnóstico
            </Link>
            <Link
              href="/profesores"
              className="inline-flex h-[52px] items-center justify-center rounded-pill border-[1.5px] border-border px-7 text-[15px] font-bold text-text transition-colors hover:bg-surface-sunken focus-visible:shadow-focus"
            >
              Ver profesores
            </Link>
          </div>
        </div>

        <p className="mt-8 flex items-start justify-center gap-2 text-center text-[13px] leading-relaxed text-text-muted">
          <ShieldCheck size={16} strokeWidth={2} className="mt-0.5 shrink-0" />
          <span>
            Tu voz no se guarda en los servidores de Orión y el audio no pasa por nosotros. Antes de
            empezar te pedimos permiso, y puedes retirarlo cuando quieras.
          </span>
        </p>
      </main>
    </>
  );
}
