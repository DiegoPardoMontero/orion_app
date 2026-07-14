"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";
import { canAccess, HOME_BY_ROLE, NAV_BY_ROLE } from "@/lib/auth/roles";
import { useLogout, useMe } from "@/lib/auth/session";
import { iniciales } from "@/lib/format";

/**
 * Shell de la zona autenticada. Hace de guarda: sin sesión manda a /login, y si el rol no puede
 * ver esta ruta lo devuelve a su propia home (sin pantalla de error: no es un fallo del usuario,
 * simplemente esa página no es para él).
 */
export default function AppLayout({ children }: { children: ReactNode }) {
  const { data: me, isPending, isError } = useMe();
  const router = useRouter();
  const pathname = usePathname();

  const allowed = me ? canAccess(me.role, pathname) : false;

  useEffect(() => {
    if (isError) {
      router.replace("/login");
      return;
    }
    if (me && !allowed) {
      router.replace(HOME_BY_ROLE[me.role]);
    }
  }, [isError, me, allowed, router]);

  if (isPending || !me || !allowed) {
    return (
      <div className="flex-1 grid place-items-center">
        <p className="text-sm text-ink-muted">Cargando…</p>
      </div>
    );
  }

  return (
    <>
      <Header />
      <div className="flex-1">{children}</div>
    </>
  );
}

function Header() {
  const { data: me } = useMe();
  const pathname = usePathname();
  const router = useRouter();
  const logout = useLogout();

  if (!me) return null;

  const nav = NAV_BY_ROLE[me.role];

  return (
    <header className="border-b border-line bg-card">
      <div className="mx-auto flex max-w-5xl items-center justify-between gap-4 px-4 py-3">
        <span className="text-lg font-semibold text-accent">Orión</span>

        <nav className="flex items-center gap-1">
          {nav.map((item) => {
            const active = pathname.startsWith(item.href);
            return (
              <Link
                key={item.href}
                href={item.href}
                className={`rounded-orion px-3 py-1.5 text-sm ${
                  active
                    ? "bg-accent-soft font-semibold text-accent-ink"
                    : "text-ink-soft hover:text-ink"
                }`}
              >
                {item.label}
              </Link>
            );
          })}
        </nav>

        <div className="flex items-center gap-2">
          <span
            title={me.fullName}
            className="grid h-8 w-8 place-items-center rounded-full bg-accent-soft text-xs font-semibold text-accent-ink"
          >
            {iniciales(me.fullName)}
          </span>
          <button
            type="button"
            onClick={() => logout.mutate(undefined, { onSuccess: () => router.replace("/login") })}
            className="rounded-orion border border-line px-3 py-1.5 text-sm text-ink-soft"
          >
            Salir
          </button>
        </div>
      </div>
    </header>
  );
}
