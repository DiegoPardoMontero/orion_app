"use client";

import { MessageCircle, Plus } from "lucide-react";
import Link from "next/link";
import { useState, type FormEvent } from "react";
import { ConoceOrion } from "@/components/bienvenida/ConoceOrion";
import { AvisoError, Cargando, ErrorCarga, Vacio } from "@/components/estados";
import { PoliticaCancelacion } from "@/components/PoliticaCancelacion";
import { PreguntasFrecuentes } from "@/components/PreguntasFrecuentes";
import { Badge, Boton, BotonPrincipal, Campo, Spinner, Tarjeta } from "@/components/ui";
import { ApiError } from "@/lib/api/fetch";
import { useMe } from "@/lib/auth/session";
import { fechaRelativa } from "@/lib/format";
import {
  ETIQUETA_ESTADO,
  TONO_ESTADO,
  useAbrirSolicitud,
  useCategoriasSoporte,
  useContacto,
  useMisSolicitudes,
} from "@/lib/soporte";

/**
 * El canal de atención. Dos cosas a la vez y a propósito:
 *
 * <p>El **ticket** es el registro. El art. 50 de la Ley 1480 de 2011 exige un mecanismo, en el
 * mismo medio donde se contrata, que deje constancia de la fecha y hora y permita seguimiento. Un
 * número de WhatsApp no cumple eso.
 *
 * <p>**WhatsApp** es el trato. Cumplir la ley con un formulario y dejar a la gente sin nadie con
 * quien hablar sería cumplir la letra y fallar en lo que importa para una academia pequeña.
 */
export default function AyudaPage() {
  const { data: me } = useMe();
  // El aspirante todavía no enseña, pero sus dudas son las del profesor: por eso postula.
  const faq =
    me?.role === "STUDENT"
      ? ("estudiante" as const)
      : me?.role === "PROFESSOR" || me?.role === "TEACHER_APPLICANT"
        ? ("profesor" as const)
        : null;
  const politica =
    me?.role === "STUDENT"
      ? ("estudiante" as const)
      : me?.role === "PROFESSOR"
        ? ("profesor" as const)
        : null;

  const solicitudes = useMisSolicitudes();
  const contacto = useContacto();
  const [abriendo, setAbriendo] = useState(false);

  return (
    <main className="mx-auto w-full max-w-3xl px-5 py-8 lg:px-8">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="font-display text-h1 font-bold">Ayuda</h1>
          <p className="mt-1 text-[14px] text-text-secondary">
            Escríbenos y te respondemos. Toda solicitud queda registrada con su fecha.
          </p>
        </div>
        {!abriendo && (
          <Boton variante="primario" onClick={() => setAbriendo(true)}>
            <Plus size={17} strokeWidth={2} />
            Nueva solicitud
          </Boton>
        )}
      </div>

      {contacto.data && (
        <a
          href={`https://wa.me/${contacto.data.whatsappDigits}`}
          target="_blank"
          rel="noreferrer noopener"
          className="mt-5 flex items-center gap-3 rounded-card border border-border bg-surface-raised p-4 transition-colors hover:border-border-strong focus-visible:shadow-focus"
        >
          <span className="grid h-10 w-10 shrink-0 place-items-center rounded-full bg-success-bg text-success">
            <MessageCircle size={19} strokeWidth={1.75} />
          </span>
          <span className="min-w-0">
            <span className="block text-[14px] font-bold">Hablar por WhatsApp</span>
            <span className="block text-[12.5px] text-text-muted">{contacto.data.horario}</span>
          </span>
        </a>
      )}

      <ConoceOrion rol={me?.role === "PROFESSOR" || me?.role === "STUDENT" ? me.role : null} />

      {abriendo && <FormularioNuevaSolicitud onListo={() => setAbriendo(false)} />}

      <h2 className="mt-8 font-display text-[17px] font-bold">Mis solicitudes</h2>

      {solicitudes.isPending && <Cargando filas={2} />}
      {solicitudes.isError && <ErrorCarga mensaje="No pudimos cargar tus solicitudes." onReintentar={() => solicitudes.refetch()} />}
      {solicitudes.data?.length === 0 && (
        <Vacio
          titulo="Todavía no has escrito"
          texto="Cuando abras una solicitud, aparecerá aquí con su respuesta."
        />
      )}

      <div className="mt-3 grid gap-2">
        {solicitudes.data?.map((t) => (
          <Link key={t.code} href={`/ayuda/${t.code}`} className="block">
            <Tarjeta className="border border-border transition-colors hover:border-border-strong">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="font-display text-[15px] font-bold">{t.subject}</p>
                <Badge tono={TONO_ESTADO[t.status]}>{ETIQUETA_ESTADO[t.status]}</Badge>
              </div>
              <p className="mt-1 text-[12.5px] text-text-muted">
                <span className="mono">{t.code}</span> · {t.categoryLabel} ·{" "}
                {fechaRelativa(t.createdAt)}
              </p>
            </Tarjeta>
          </Link>
        ))}
      </div>

      {/* Las preguntas frecuentes y la política de cancelación viven también en la cuenta de cada
          rol, pero /perfil está cerrado para el profesor no aprobado y el aspirante ni lo tiene en
          el menú — y son justo quienes más dudas tienen. Ayuda es la única pantalla a la que
          llegan todos, así que aquí no pueden faltar. */}
      {faq && <PreguntasFrecuentes rol={faq} />}
      {politica && <PoliticaCancelacion rol={politica} />}
    </main>
  );
}

function FormularioNuevaSolicitud({ onListo }: { onListo: () => void }) {
  const categorias = useCategoriasSoporte();
  const abrir = useAbrirSolicitud();
  const [category, setCategory] = useState("");
  const [subject, setSubject] = useState("");
  const [body, setBody] = useState("");

  const listo = category !== "" && subject.trim() !== "" && body.trim() !== "";
  const error = abrir.error instanceof ApiError ? abrir.error.message : null;
  const conPlazo = categorias.data?.find((c) => c.code === category)?.hasLegalDeadline;

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (!listo) return;
    abrir.mutate(
      { category, subject: subject.trim(), body: body.trim() },
      { onSuccess: onListo },
    );
  }

  return (
    <Tarjeta className="mt-5">
      <form onSubmit={onSubmit}>
        <h2 className="font-display text-[17px] font-bold">Nueva solicitud</h2>

        <label className="mt-4 block text-[12px] font-bold uppercase tracking-[0.04em] text-text-secondary" htmlFor="categoria">
          ¿De qué se trata?
        </label>
        <select
          id="categoria"
          value={category}
          onChange={(event) => setCategory(event.target.value)}
          className="mt-1.5 h-12 w-full rounded-base border border-border bg-surface px-3 text-[15px] focus-visible:shadow-focus"
        >
          <option value="">Elige una opción</option>
          {categorias.data?.map((c) => (
            <option key={c.code} value={c.code}>
              {c.label}
            </option>
          ))}
        </select>

        {conPlazo && (
          <p className="mt-2 rounded-base bg-accent-lavender-soft px-4 py-3 text-[12.5px] leading-relaxed text-info">
            Esta solicitud tiene un plazo de respuesta fijado por ley. Te confirmaremos la fecha
            exacta al crearla.
          </p>
        )}

        <label className="mt-4 block text-[12px] font-bold uppercase tracking-[0.04em] text-text-secondary" htmlFor="asunto">
          Asunto
        </label>
        <Campo
          id="asunto"
          type="text"
          maxLength={160}
          placeholder="En una línea, qué pasa"
          value={subject}
          onChange={(event) => setSubject(event.target.value)}
          className="mt-1.5"
        />

        <label className="mt-4 block text-[12px] font-bold uppercase tracking-[0.04em] text-text-secondary" htmlFor="cuerpo">
          Cuéntanos
        </label>
        <textarea
          id="cuerpo"
          rows={5}
          maxLength={4000}
          placeholder="Los detalles que nos ayuden a entender qué pasó."
          value={body}
          onChange={(event) => setBody(event.target.value)}
          className="mt-1.5 w-full rounded-base border border-border bg-surface p-3 text-[15px] leading-relaxed focus-visible:shadow-focus"
        />

        {error && (
          <div className="mt-3">
            <AvisoError mensaje={error} />
          </div>
        )}

        <div className="mt-4 flex flex-wrap gap-2">
          <BotonPrincipal type="submit" disabled={!listo || abrir.isPending} className="w-auto px-6">
            {abrir.isPending ? (
              <>
                <Spinner />
                Enviando…
              </>
            ) : (
              "Enviar"
            )}
          </BotonPrincipal>
          <Boton variante="fantasma" onClick={onListo} type="button">
            Cancelar
          </Boton>
        </div>
      </form>
    </Tarjeta>
  );
}
