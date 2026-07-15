"use client";

import { KeyRound, LogOut } from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
import { Avatar } from "@/components/Avatar";
import { CambiarClave } from "@/components/CambiarClave";
import { Wordmark } from "@/components/marca";
import { canAccess, HOME_BY_ROLE, NAV_BY_ROLE } from "@/lib/auth/roles";
import { useLogout, useMe } from "@/lib/auth/session";

/**
 * Shell de la zona autenticada. Hace de guarda: sin sesión manda a /login, y si el rol no puede
 * ver esta ruta lo devuelve a su propia home (sin pantalla de error: no es un fallo del usuario,
 * esa página simplemente no es para él).
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
      <div className="grid flex-1 place-items-center">
        <p className="text-[13px] text-text-muted">Cargando…</p>
      </div>
    );
  }

  const esAdmin = me.role === "ADMIN";

  return (
    <>
      <Header />
      {/* El admin usa ancho completo (tablas); estudiante y profesor viven en columna móvil. */}
      <div className={esAdmin ? "flex-1" : "mx-auto w-full max-w-md flex-1"}>{children}</div>
    </>
  );
}

function Header() {
  const { data: me } = useMe();
  const pathname = usePathname();
  const router = useRouter();
  const logout = useLogout();
  const [menuAbierto, setMenuAbierto] = useState(false);
  const [cambiandoClave, setCambiandoClave] = useState(false);

  if (!me) return null;

  const nav = NAV_BY_ROLE[me.role];

  return (
    <header className="hero-noche">
      <div className="mx-auto flex max-w-5xl items-center justify-between gap-2 px-4 py-3">
        <Wordmark className="shrink-0 text-[17px] text-white" />

        <nav className="flex items-center gap-0.5">
          {nav.map((item) => {
            const activo = pathname.startsWith(item.href);
            return (
              <Link
                key={item.href}
                href={item.href}
                className={`whitespace-nowrap rounded-base px-3 py-1.5 text-[13px] transition-colors ${
                  activo
                    ? "bg-white/15 font-bold text-white"
                    : "font-semibold text-[#c9bff0] hover:text-white"
                }`}
              >
                {item.label}
              </Link>
            );
          })}
        </nav>

        <div className="relative">
          <button
            type="button"
            aria-label="Menú de usuario"
            onClick={() => setMenuAbierto((abierto) => !abierto)}
            className="rounded-full ring-2 ring-transparent hover:ring-white/30"
          >
            <Avatar nombre={me.fullName} size="sm" />
          </button>

          {menuAbierto && (
            <>
              {/* Capa invisible: un clic fuera cierra el menú. */}
              <button
                type="button"
                aria-hidden="true"
                tabIndex={-1}
                onClick={() => setMenuAbierto(false)}
                className="fixed inset-0 z-40 cursor-default"
              />
              <div className="absolute right-0 z-50 mt-2 w-56 overflow-hidden rounded-card border border-border-subtle bg-surface-raised shadow-[0_12px_24px_rgba(20,16,46,0.18)]">
                <div className="border-b border-border-subtle px-4 py-3">
                  <p className="truncate text-[13px] font-bold text-text">{me.fullName}</p>
                  <p className="truncate text-[11.5px] text-text-muted">{me.email}</p>
                </div>
                <button
                  type="button"
                  onClick={() => {
                    setMenuAbierto(false);
                    setCambiandoClave(true);
                  }}
                  className="flex w-full items-center gap-2.5 px-4 py-2.5 text-left text-[13px] font-semibold text-text hover:bg-surface-sunken"
                >
                  <KeyRound size={15} strokeWidth={2.2} />
                  Cambiar contraseña
                </button>
                <button
                  type="button"
                  onClick={() =>
                    logout.mutate(undefined, { onSuccess: () => router.replace("/login") })
                  }
                  className="flex w-full items-center gap-2.5 px-4 py-2.5 text-left text-[13px] font-semibold text-error hover:bg-error-bg"
                >
                  <LogOut size={15} strokeWidth={2.2} />
                  Salir
                </button>
              </div>
            </>
          )}
        </div>
      </div>

      {cambiandoClave && <CambiarClave onCerrar={() => setCambiandoClave(false)} />}
    </header>
  );
}
