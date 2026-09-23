"use client";

import { useQuery } from "@tanstack/react-query";
import { Sparkles } from "lucide-react";
import Link from "next/link";
import { apiFetch } from "@/lib/api/fetch";
import { fechaLarga } from "@/lib/format";
import { palabrasNuevas, primerNombre, resumirLoTrabajado, type SetDePractica } from "@/lib/practica";

/**
 * La invitación a practicar (Bloque 10, paso B5.1): «Para esta semana · 4 min». Si no hay un set
 * vivo, la tarjeta no aparece —nada de «no tienes práctica disponible»—.
 */
export function InvitacionAPracticar() {
  const practica = useQuery({
    queryKey: ["me", "practice"],
    queryFn: () => apiFetch<SetDePractica | undefined>("/api/v1/me/practice"),
    staleTime: 60_000,
  });
  const s = practica.data;
  if (!s) return null;

  const empezada = s.status === "IN_PROGRESS";
  const dia = s.classStartsAt ? fechaLarga(s.classStartsAt).split(",")[0] : null;
  const de = [dia ? `Del ${dia}` : "De tu última clase", s.professorName ? `con ${primerNombre(s.professorName)}` : null]
    .filter(Boolean)
    .join(" ");
  const que = [resumirLoTrabajado(s.workedOn), s.vocabularyCount > 0 ? palabrasNuevas(s.vocabularyCount) : null].filter(Boolean).join(" y ");

  return (
    <section className="mt-6 rounded-card bg-accent-lavender-soft p-5" aria-labelledby="titulo-practica">
      <p id="titulo-practica" className="flex items-center gap-1.5 text-[13px] font-bold uppercase tracking-[0.04em] text-[#5e4a8a]">
        <Sparkles size={14} strokeWidth={2.2} />
        Para esta semana · {s.estimatedMinutes ?? s.itemCount} min
      </p>
      <p className="mt-2 text-[14.5px] text-text">
        {de}
        {que ? `: ${que}.` : "."}
      </p>
      <Link
        href={`/practica/${s.id}`}
        className="mt-3 inline-flex min-h-11 items-center rounded-pill bg-primary px-6 text-[15px] font-bold text-on-primary shadow-primary transition-colors hover:bg-primary-strong focus-visible:shadow-focus"
      >
        {empezada ? "Seguir practicando" : "Practicar"}
      </Link>
    </section>
  );
}
