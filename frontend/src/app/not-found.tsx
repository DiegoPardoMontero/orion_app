import Link from "next/link";
import { Rigel } from "@/components/Rigel";

export default function NotFound() {
  return (
    <main className="flex min-h-dvh flex-col items-center justify-center gap-5 px-6 py-16 text-center">
      <Rigel pose="animo" decorativo className="h-[168px] w-auto" />
      <div>
        <p className="font-display text-[12px] font-bold uppercase tracking-[0.1em] text-primary-strong">
          Error 404
        </p>
        <h1 className="mt-1 font-display text-h1 font-bold">Nos perdimos entre las estrellas</h1>
        <p className="mx-auto mt-2 max-w-[360px] text-[14px] leading-relaxed text-text-secondary">
          Esta página no existe o se movió. Rigel te lleva de vuelta a un lugar conocido.
        </p>
      </div>
      <Link
        href="/"
        className="inline-flex min-h-11 items-center rounded-pill bg-primary px-6 text-[15px] font-bold text-on-primary shadow-primary transition-colors hover:bg-primary-strong focus-visible:shadow-focus"
      >
        Volver al inicio
      </Link>
    </main>
  );
}
