"use client";

import { Search } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import type { GoalResponse, LanguageResponse } from "@/lib/api/types";

/**
 * Buscador del hero: idioma · objetivo · horario. Recibe el catálogo ya resuelto en el servidor
 * (sin waterfall en el cliente) y arma la URL del marketplace con los filtros seleccionados.
 *
 * El horario (MORNING/AFTERNOON/EVENING) se manda como `schedule=`: el backend aún NO lo filtra
 * (no hay parámetro de franja en GET /professors), pero lo dejamos en la URL para no perder la
 * intención del usuario y poder conectarlo cuando exista. Idioma y objetivo sí los aplica la lista.
 */
const HORARIOS = [
  { valor: "MORNING", etiqueta: "Mañana" },
  { valor: "AFTERNOON", etiqueta: "Tarde" },
  { valor: "EVENING", etiqueta: "Noche" },
] as const;

export function BuscadorHero({
  languages,
  goals,
}: {
  languages: LanguageResponse[];
  goals: GoalResponse[];
}) {
  const router = useRouter();
  const [language, setLanguage] = useState("");
  const [goal, setGoal] = useState("");
  const [schedule, setSchedule] = useState("");

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    const params = new URLSearchParams();
    if (language) params.set("language", language);
    if (goal) params.set("goal", goal);
    if (schedule) params.set("schedule", schedule);
    const qs = params.toString();
    router.push(qs ? `/profesores?${qs}` : "/profesores");
  }

  return (
    <form
      onSubmit={onSubmit}
      className="grid gap-3 rounded-card bg-surface-raised p-4 shadow-lg sm:grid-cols-[1fr_1fr_1fr_auto] sm:items-end"
    >
      <Selector
        id="buscar-idioma"
        etiqueta="Idioma"
        valor={language}
        onCambio={setLanguage}
        placeholder="Cualquiera"
        opciones={languages.map((l) => ({
          valor: l.code ?? "",
          etiqueta: `${l.flagEmoji ? `${l.flagEmoji} ` : ""}${l.nameEs ?? l.code ?? ""}`,
        }))}
      />
      <Selector
        id="buscar-objetivo"
        etiqueta="Objetivo"
        valor={goal}
        onCambio={setGoal}
        placeholder="Cualquiera"
        opciones={goals.map((g) => ({ valor: g.code ?? "", etiqueta: g.nameEs ?? g.code ?? "" }))}
      />
      <Selector
        id="buscar-horario"
        etiqueta="Horario"
        valor={schedule}
        onCambio={setSchedule}
        placeholder="Cualquiera"
        opciones={HORARIOS.map((h) => ({ valor: h.valor, etiqueta: h.etiqueta }))}
      />
      <button
        type="submit"
        className="inline-flex h-[52px] items-center justify-center gap-2 rounded-pill bg-primary px-6 text-[15px] font-bold text-on-primary shadow-primary transition-colors hover:bg-primary-strong focus-visible:shadow-focus"
      >
        <Search size={18} strokeWidth={2} />
        Buscar profesor
      </button>
    </form>
  );
}

function Selector({
  id,
  etiqueta,
  valor,
  onCambio,
  placeholder,
  opciones,
}: {
  id: string;
  etiqueta: string;
  valor: string;
  onCambio: (v: string) => void;
  placeholder: string;
  opciones: { valor: string; etiqueta: string }[];
}) {
  return (
    <label htmlFor={id} className="block text-left">
      <span className="mb-1.5 block text-[12px] font-bold uppercase tracking-[0.04em] text-text-muted">
        {etiqueta}
      </span>
      <select
        id={id}
        value={valor}
        onChange={(e) => onCambio(e.target.value)}
        className="h-[52px] w-full rounded-base border-[1.5px] border-border bg-surface-raised px-3.5 text-[15px] font-semibold text-text transition-[border-color,box-shadow] focus:border-primary focus:shadow-focus focus:outline-none"
      >
        <option value="">{placeholder}</option>
        {opciones
          .filter((o) => o.valor)
          .map((o) => (
            <option key={o.valor} value={o.valor}>
              {o.etiqueta}
            </option>
          ))}
      </select>
    </label>
  );
}
