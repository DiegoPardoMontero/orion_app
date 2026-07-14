import type { ReactNode } from "react";

/** La estrella de 4 puntas del wordmark. Path literal del entregable de diseño. */
export function EstrellaCoral({ className = "" }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" className={className} fill="currentColor">
      <path d="M12 2l2.6 6.6L21 11l-6.4 2.4L12 20l-2.6-6.6L3 11l6.4-2.4z" />
    </svg>
  );
}

export function Wordmark({ className = "text-[20px]" }: { className?: string }) {
  return (
    <span className={`inline-flex items-center gap-1.5 font-extrabold ${className}`}>
      Orión
      <EstrellaCoral className="h-[0.55em] w-[0.55em] text-accent" />
    </span>
  );
}

/** Estrellas que titilan sobre el degradado. Posiciones fijas: nada aleatorio entre renders. */
const ESTRELLAS = [
  { top: "18%", left: "12%", size: 7, delay: "0s", color: "#FFD9A8" },
  { top: "30%", left: "78%", size: 5, delay: "0.6s", color: "#FF9C7E" },
  { top: "62%", left: "22%", size: 4, delay: "1.2s", color: "#FFFFFF" },
  { top: "12%", left: "56%", size: 5, delay: "1.8s", color: "#FFFFFF" },
  { top: "72%", left: "88%", size: 6, delay: "0.9s", color: "#FFD9A8" },
];

/** Cabecera nocturna con la constelación: el motivo de marca de Orión. */
export function HeroNoche({
  children,
  className = "",
}: {
  children: ReactNode;
  className?: string;
}) {
  return (
    <div className={`hero-noche relative overflow-hidden ${className}`}>
      {ESTRELLAS.map((estrella, i) => (
        <span
          key={i}
          className="estrella absolute"
          style={{
            top: estrella.top,
            left: estrella.left,
            width: estrella.size,
            height: estrella.size,
            color: estrella.color,
            animationDelay: estrella.delay,
          }}
        >
          <EstrellaCoral className="h-full w-full" />
        </span>
      ))}
      <div className="relative">{children}</div>
    </div>
  );
}
