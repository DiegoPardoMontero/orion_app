"use client";

import { EstrellaLogro } from "@/components/gamificacion/EstrellaLogro";
import { SelloPractica } from "@/components/gamificacion/SelloPractica";
import { estadoDe, numeralDe, type Logro } from "@/lib/gamificacion";

/** El sello de un logro, de la familia que sea: los de Práctica son dorados; los demás, su estrella. */
export function SelloDeLogro({ logro, size, estampar = false }: { logro: Logro; size: number; estampar?: boolean }) {
  if (logro.family === "PRACTICA") {
    return (
      <SelloPractica
        code={logro.code}
        nombre={logro.name}
        brillo={logro.glow}
        estado={estadoDe(logro)}
        hecho={logro.progress}
        total={logro.target}
        size={size}
        estampar={estampar}
      />
    );
  }
  return (
    <div className={estampar ? "pr-estampa" : undefined}>
      <EstrellaLogro
        familia={logro.family}
        brillo={logro.glow}
        estado={estadoDe(logro)}
        progreso={{ hecho: logro.progress, total: logro.target }}
        numeral={numeralDe(logro.code)}
        size={size}
        titulo={`${logro.name}, ${logro.unlocked ? "conseguido" : `${logro.progress} de ${logro.target}`}`}
      />
    </div>
  );
}
