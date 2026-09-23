import type { ReactNode } from "react";
import { REDES_SOCIALES } from "@/lib/config";

/**
 * Los íconos de las redes de Orión. lucide ya no trae marcas (y TikTok nunca estuvo), así que van
 * dibujados aquí con el mismo trazo que el resto de íconos: 24 px, línea de 2 y puntas redondas.
 */
const ICONOS: Record<(typeof REDES_SOCIALES)[number]["nombre"], ReactNode> = {
  Instagram: (
    <>
      <rect x="4" y="4" width="16" height="16" rx="4.5" />
      <circle cx="12" cy="12" r="3.5" />
      <path d="M16.5 7.5v.01" />
    </>
  ),
  TikTok: (
    <path d="M21 7.9v4a9.9 9.9 0 0 1-5-1.9v4.5a6.5 6.5 0 1 1-8-6.3v4.3a2.5 2.5 0 1 0 4 2V3h4.1A6 6 0 0 0 21 7.9z" />
  ),
  LinkedIn: (
    <>
      <rect x="3" y="3" width="18" height="18" rx="2.5" />
      <path d="M8 11v5" />
      <path d="M8 8v.01" />
      <path d="M12 16v-5" />
      <path d="M16 16v-3a2 2 0 1 0-4 0" />
    </>
  ),
};

export function Redes({ className = "" }: { className?: string }) {
  return (
    <ul className={`flex items-center gap-1 ${className}`}>
      {REDES_SOCIALES.map((red) => (
        <li key={red.nombre}>
          <a
            href={red.url}
            target="_blank"
            rel="noopener noreferrer"
            aria-label={`Orión en ${red.nombre}`}
            title={red.nombre}
            className="grid h-11 w-11 place-items-center rounded-full text-text-secondary transition-colors hover:bg-surface-sunken hover:text-text focus-visible:shadow-focus"
          >
            <svg
              viewBox="0 0 24 24"
              width="20"
              height="20"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              {ICONOS[red.nombre]}
            </svg>
          </a>
        </li>
      ))}
    </ul>
  );
}
