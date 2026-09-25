"use client";

import { useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { Mic, ShieldCheck } from "lucide-react";
import { apiFetch, ApiError } from "@/lib/api/fetch";
import type { GoalResponse } from "@/lib/api/types";
import { dias, useCifras } from "@/lib/cifras";
import { AvisoError } from "@/components/estados";
import { Boton } from "@/components/ui";

/**
 * La puerta de entrada: para qué quieres el idioma, y el permiso para usar tu voz.
 *
 * <p><strong>Sin cuenta</strong> (Pardo, 22/09/2026) basta con el nombre —Meissa te saluda con
 * él— y dos casillas: que eres mayor de edad y que autorizas el uso de tu voz. Van separadas y las
 * dos nacen desmarcadas: juntarlas viciaría la autorización, y una casilla premarcada no es un
 * consentimiento, es una suposición. Quien ya empezó sin cuenta en este dispositivo no vuelve a
 * darlas.
 *
 * <p>Con cuenta de estudiante, la autorización de voz se da aparte, igual que antes.
 *
 * <p>El objetivo se pregunta aquí y no al final porque alimenta las <strong>recomendaciones</strong>,
 * no el puntaje: para qué quieres el inglés no te hace hablarlo mejor ni peor, pero sí cambia con
 * quién te conviene empezar.
 */
export function Puerta({
  sinCuenta,
  nombreDelLead,
  empezando,
  error,
  onListo,
}: {
  sinCuenta: boolean;
  /** El nombre del lead de este dispositivo, si ya empezó antes sin cuenta. */
  nombreDelLead: string | null;
  empezando: boolean;
  error: string | null;
  onListo: (objetivos: string[]) => void;
}) {
  const cifras = useCifras();
  const [objetivos, setObjetivos] = useState<string[]>([]);
  const [nombre, setNombre] = useState("");
  const [mayor, setMayor] = useState(false);
  const [voz, setVoz] = useState(false);

  const catalogo = useQuery({
    queryKey: ["catalog", "goals"],
    queryFn: () => apiFetch<GoalResponse[]>("/api/v1/catalog/goals"),
    staleTime: 5 * 60_000,
  });

  // Con cuenta: la autorización de voz de siempre.
  const consentir = useMutation({
    mutationFn: () => apiFetch("/api/v1/me/voice-consent", { method: "POST", body: {} }),
    onSuccess: () => onListo(objetivos),
  });

  // Sin cuenta: el lead, con su nombre y sus dos declaraciones.
  const presentarse = useMutation({
    mutationFn: () =>
      apiFetch("/api/v1/assessment-leads", {
        method: "POST",
        body: { firstName: nombre.trim(), adult: mayor, acceptsVoice: voz },
        redirectOn401: false,
      }),
    onSuccess: () => onListo(objetivos),
  });

  const conocido = sinCuenta && nombreDelLead != null;
  const nuevo = sinCuenta && !conocido;
  const listo = conocido || (nuevo ? nombre.trim().length > 0 && mayor && voz : voz);
  const ocupado = empezando || consentir.isPending || presentarse.isPending;

  const empezar = () => {
    if (conocido) onListo(objetivos);
    else if (nuevo) presentarse.mutate();
    else consentir.mutate();
  };

  const fallo =
    error ??
    (consentir.error instanceof ApiError ? consentir.error.message : null) ??
    (presentarse.error instanceof ApiError ? presentarse.error.message : null);

  const alternar = (code: string) =>
    setObjetivos((prev) => (prev.includes(code) ? prev.filter((c) => c !== code) : [...prev, code]));

  return (
    <main className="mx-auto w-full max-w-lg px-5 pb-8 pt-4 lg:py-8">
      <h1 className="font-display text-[28px] font-bold leading-tight lg:text-h1">
        {conocido ? `Hola de nuevo, ${nombreDelLead}.` : "Antes de empezar."}
      </h1>
      <p className="mt-1 text-[14px] leading-relaxed text-text-secondary">
        {conocido ? "Cuando quieras, Meissa te espera." : "Un momento y ya. Sin examen y sin nota."}
      </p>

      {nuevo && (
        <label className="mt-5 block">
          <span className="text-[14px] font-bold text-text">¿Cómo te llamas?</span>
          <input
            type="text"
            autoComplete="given-name"
            maxLength={60}
            value={nombre}
            onChange={(e) => setNombre(e.target.value)}
            placeholder="Tu nombre"
            className="mt-1.5 h-12 w-full rounded-base border border-border bg-surface-raised px-4 text-[15px] focus:border-primary focus:outline-none focus-visible:shadow-focus"
          />
        </label>
      )}

      <section className="mt-5">
        <h2 className="text-[14px] font-bold text-text">¿Para qué quieres el inglés?</h2>
        <p className="mt-0.5 text-[12.5px] text-text-secondary">
          Opcional. No cambia tu resultado: cambia con quién te conviene empezar.
        </p>
        <div className="mt-2.5 flex flex-wrap gap-2">
          {(catalogo.data ?? []).map((meta) => {
            const activo = objetivos.includes(meta.code ?? "");
            return (
              <button
                key={meta.code}
                type="button"
                aria-pressed={activo}
                onClick={() => meta.code && alternar(meta.code)}
                className={`min-h-11 rounded-pill px-4 text-[13.5px] font-semibold transition-colors focus-visible:shadow-focus ${
                  activo
                    ? "bg-primary text-on-primary"
                    : "bg-surface-sunken text-text-secondary hover:bg-border/60 hover:text-text"
                }`}
              >
                {meta.nameEs}
              </button>
            );
          })}
        </div>
      </section>

      {!conocido && (
        <section className="mt-5 rounded-card bg-accent-lavender-soft p-4">
          <p className="flex items-start gap-2 text-[13px] leading-relaxed text-text-secondary">
            <Mic size={16} strokeWidth={2.2} className="mt-0.5 shrink-0 text-info" />
            <span>
              Vamos a procesar tu voz para darte tu resultado.{" "}
              <strong className="text-text">No guardamos el audio</strong>, solo lo que dijiste en
              texto
              {nuevo
                ? `. Si no creas tu cuenta, lo borramos todo a los ${dias(cifras.assessmentLeadRetentionDays)}.`
                : ", y puedes pedir que lo borremos cuando quieras."}
            </span>
          </p>

          {nuevo && (
            <Casilla marcada={mayor} onCambio={setMayor}>
              Soy mayor de 18 años
            </Casilla>
          )}
          <Casilla marcada={voz} onCambio={setVoz}>
            Autorizo el uso de mi voz para este diagnóstico
          </Casilla>
        </section>
      )}

      {fallo && (
        <div className="mt-4">
          <AvisoError mensaje={fallo} />
        </div>
      )}

      <Boton variante="primario" className="mt-5 w-full" disabled={!listo || ocupado} onClick={empezar}>
        {ocupado ? "Un momento…" : "Empezar la conversación"}
      </Boton>

      <p className="mt-3 flex items-start gap-2 text-[12.5px] leading-relaxed text-text-muted">
        <ShieldCheck size={15} strokeWidth={2} className="mt-0.5 shrink-0" />
        <span>
          Nadie te va a corregir mientras hablas. Puedes hablarle en español: es solo para que te
          conozca, entienda tu contexto y por qué quieres aprender inglés.
        </span>
      </p>

      {/* Voluntario (Pardo, 25/09/2026): quien llegó hasta aquí y lo piensa mejor tiene por dónde
          seguir sin hacerlo. Solo sin cuenta: quien ya la tiene vino a propósito desde su perfil. */}
      {sinCuenta && (
        <p className="mt-4 text-center text-[13px] leading-relaxed text-text-secondary">
          ¿Prefieres no hacerlo ahora?{" "}
          <Link
            href="/registro"
            className="rounded-base font-bold text-primary-strong underline underline-offset-2 focus-visible:shadow-focus"
          >
            Crea tu cuenta sin diagnóstico
          </Link>
          . Lo tienes en tu perfil cuando quieras.
        </p>
      )}
    </main>
  );
}

function Casilla({
  marcada,
  onCambio,
  children,
}: {
  marcada: boolean;
  onCambio: (v: boolean) => void;
  children: React.ReactNode;
}) {
  return (
    <label className="mt-2.5 flex cursor-pointer items-center gap-3 rounded-base bg-surface-raised p-3">
      <input
        type="checkbox"
        checked={marcada}
        onChange={(e) => onCambio(e.target.checked)}
        className="h-5 w-5 shrink-0 accent-[var(--color-primary)]"
      />
      <span className="text-[13.5px] font-semibold text-text">{children}</span>
    </label>
  );
}
