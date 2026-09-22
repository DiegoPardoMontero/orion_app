import type { Metadata } from "next";
import Link from "next/link";
import { Mic } from "lucide-react";
import { Constelacion, Wordmark } from "@/components/marca";
import { Meissa } from "@/components/Meissa";

/**
 * La puerta al diagnóstico: el destino del botón grande de la portada.
 *
 * <p><strong>Una pantalla y a hablar</strong> (Pardo, 22/09/2026). Antes tenía tres pasos, un
 * bloque de condiciones y otro de privacidad, y había que bajar para encontrar el botón; ahora es
 * el diseño «antes» del handoff de Meissa sin la lista de pasos, y cabe sin scroll en un teléfono.
 *
 * <p>Pública y sin cuenta: el nombre y las dos casillas se piden en la pantalla siguiente, justo
 * antes de hablar. Meissa aparece hablando, con su burbuja; Rigel no sale aquí, porque es quien
 * recibe a la persona en el registro y nunca comparten pantalla.
 */
export const metadata: Metadata = {
  title: "Prueba tu inglés en 2 minutos · Orión",
  description:
    "Una conversación de dos minutos con Meissa, sin cuenta y sin examen. Recibe tu Confidence Score, un resumen de lo que contaste y tres profesores elegidos para ti. Gratis.",
  alternates: { canonical: "/diagnostico" },
};

export default function DiagnosticoPage() {
  return (
    <main className="flex min-h-dvh flex-col lg:flex-row">
      {/* Marca: arriba en móvil (300 px) con Meissa abajo a la derecha; panel izquierdo del 47 %
          en escritorio, como el login de Rigel pero del otro lado. */}
      <div className="gradient-dawn relative flex h-[300px] shrink-0 flex-col overflow-hidden rounded-b-[24px] p-6 lg:m-5 lg:h-auto lg:w-[47%] lg:rounded-[22px] lg:p-10">
        <Constelacion className="pointer-events-none absolute left-2 top-10 h-[120px] w-[120px] opacity-60 lg:left-8 lg:top-24 lg:h-[220px] lg:w-[220px]" />
        <Link href="/" className="relative w-fit rounded-base focus-visible:shadow-focus">
          <Wordmark className="text-[15px] text-on-primary" />
        </Link>

        <div className="absolute bottom-3 right-3 flex flex-col items-end lg:right-10 lg:top-20 lg:bottom-auto">
          <p className="max-w-[210px] rounded-[22px_22px_6px_22px] bg-[#FFF6EE] px-4 py-3 text-[14px] font-medium leading-[1.45] text-text lg:max-w-[250px] lg:text-[16px]">
            Hola, soy Meissa. Hablemos dos minutos y te digo por dónde empezar.
          </p>
          <Meissa estado="habla" sobreAmanecer decorativo className="mt-1 h-[146px] w-auto lg:h-[236px]" />
        </div>

        <div className="relative mt-auto hidden lg:block">
          <h1 className="max-w-[14ch] font-display text-[40px] font-bold leading-[1.08] text-on-primary">
            Prueba tu inglés hablando.
          </h1>
          <p className="mt-3 text-[15px] text-on-primary/80">
            Dos minutos de conversación. Sin cuenta, sin tarjeta, sin nota.
          </p>
        </div>
      </div>

      <div className="flex flex-1 items-start justify-center px-6 py-6 lg:items-center lg:px-10">
        <div className="w-full max-w-md lg:max-w-[440px]">
          <h2 className="font-display text-[28px] font-bold leading-tight lg:text-[38px]">
            Habla dos minutos con Meissa
          </h2>
          <p className="mt-2 text-[15px] leading-relaxed text-text-secondary">
            Te hace preguntas sencillas sobre ti y tu día. Respondes en inglés como puedas: si te
            trabas, está bien. Sin cuenta, sin tarjeta, sin nota.
          </p>

          <p className="mt-5 flex items-start gap-3 rounded-[16px] border border-border bg-surface-raised p-4 text-[13px] leading-relaxed text-[#5E4E6B]">
            <Mic size={18} strokeWidth={2} className="mt-0.5 shrink-0 text-text-secondary" />
            Te pediremos permiso para usar el micrófono. La conversación se guarda solo para darte
            tu resultado, y puedes pedir que la borremos.
          </p>

          <Link
            href="/diagnostico/empezar"
            className="mt-6 inline-flex h-[52px] w-full items-center justify-center rounded-pill bg-primary px-7 text-[15px] font-bold text-on-primary shadow-primary transition-colors hover:bg-primary-strong focus-visible:shadow-focus"
          >
            Empezar con Meissa
          </Link>
        </div>
      </div>
    </main>
  );
}
