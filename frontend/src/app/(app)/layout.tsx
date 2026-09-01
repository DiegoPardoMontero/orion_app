"use client";

import {
  CalendarClock,
  CalendarDays,
  CalendarRange,
  KeyRound,
  LogOut,
  User,
  Users,
  type LucideIcon,
} from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
import { Avatar } from "@/components/Avatar";
import { CambiarClave } from "@/components/CambiarClave";
import { Wordmark } from "@/components/marca";
import { canAccess, HOME_BY_ROLE, NAV_BY_ROLE, type NavItem } from "@/lib/auth/roles";
import type { Role } from "@/lib/auth/session";
import { useLogout, useMe } from "@/lib/auth/session";

/** Cada ruta lleva su ícono; el activo va relleno para no marcarse solo por color. */
const ICONO: Record<string, LucideIcon> = {
  "/profesores": Users,
  "/mis-clases": CalendarDays,
  "/cuenta": User,
  "/disponibilidad": CalendarClock,
  "/perfil": User,
  "/admin/usuarios": Users,
  "/admin/reservas": CalendarRange,
};

const ETIQUETA_ROL: Record<Role, string> = {
  STUDENT: "Estudiante",
  PROFESSOR: "Profesor",
  ADMIN: "Administración",
};

/**
 * Shell de la zona autenticada. Hace de guarda (sin sesión → /login; rol sin acceso → su home) y
 * arma la navegación responsive: en móvil, header superior + tab bar inferior; en desktop, sidebar
 * fija de 248 px. El item activo se marca con color + peso + pastilla, nunca por color solo.
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
        <p className="text-sm text-text-muted">Cargando…</p>
      </div>
    );
  }

  const nav = NAV_BY_ROLE[me.role];

  return (
    <div className="lg:flex lg:min-h-dvh">
      <Sidebar me={me} nav={nav} pathname={pathname} />

      <div className="flex min-h-dvh flex-1 flex-col">
        <MobileHeader me={me} />
        <div className="flex-1 pb-24 lg:pb-0">{children}</div>
        <TabBar nav={nav} pathname={pathname} />
      </div>
    </div>
  );
}

/** Cabecera móvil: 64 px, logotipo coral + menú de usuario. Sobre superficie clara. */
function MobileHeader({ me }: { me: { fullName: string; email: string; role: Role; photoUrl?: string | null } }) {
  return (
    <header className="sticky top-0 z-30 flex h-16 items-center justify-between border-b border-surface-sunken bg-surface px-5 lg:hidden">
      <Wordmark className="text-[16px] text-primary" />
      <MenuUsuario me={me} posicion="abajo" />
    </header>
  );
}

/** Tab bar inferior (móvil): 68 px + safe-area, una entrada por sección del rol. */
function TabBar({ nav, pathname }: { nav: NavItem[]; pathname: string }) {
  return (
    <nav
      className="fixed inset-x-0 bottom-0 z-30 flex items-stretch justify-around border-t border-surface-sunken bg-surface-raised lg:hidden"
      style={{ height: "calc(68px + env(safe-area-inset-bottom))", paddingBottom: "env(safe-area-inset-bottom)" }}
    >
      {nav.map((item) => {
        const activo = pathname.startsWith(item.href);
        const Icono = ICONO[item.href] ?? Users;
        return (
          <Link
            key={item.href}
            href={item.href}
            aria-current={activo ? "page" : undefined}
            className="flex flex-1 flex-col items-center justify-center gap-1 pt-2"
          >
            <span
              className={`flex items-center rounded-pill px-3.5 py-[5px] transition-colors ${
                activo ? "bg-primary-soft text-primary" : "text-text-muted"
              }`}
            >
              <Icono size={21} strokeWidth={1.75} fill={activo ? "currentColor" : "none"} />
            </span>
            <span
              className={`text-[11px] ${activo ? "font-bold text-primary" : "font-semibold text-text-muted"}`}
            >
              {item.label}
            </span>
          </Link>
        );
      })}
    </nav>
  );
}

/** Sidebar de desktop: 248 px fija, sin tabs. Logo, entradas pill y tarjeta de usuario al pie. */
function Sidebar({
  me,
  nav,
  pathname,
}: {
  me: { fullName: string; email: string; role: Role; photoUrl?: string | null };
  nav: NavItem[];
  pathname: string;
}) {
  return (
    <aside className="sticky top-0 hidden h-dvh w-[248px] shrink-0 flex-col border-r border-surface-sunken bg-surface px-4 py-6 lg:flex">
      <div className="px-3">
        <Wordmark className="text-[18px] text-primary" />
      </div>

      <p className="mt-8 px-3 text-[11px] font-bold uppercase tracking-[0.1em] text-text-muted">
        {ETIQUETA_ROL[me.role]}
      </p>

      <nav className="mt-3 flex flex-col gap-1.5">
        {nav.map((item) => {
          const activo = pathname.startsWith(item.href);
          const Icono = ICONO[item.href] ?? Users;
          return (
            <Link
              key={item.href}
              href={item.href}
              aria-current={activo ? "page" : undefined}
              className={`flex h-12 items-center gap-3 rounded-pill px-4 text-[14px] transition-colors ${
                activo
                  ? "bg-primary-soft font-bold text-primary-strong"
                  : "font-semibold text-text-secondary hover:bg-surface-sunken"
              }`}
            >
              <Icono size={20} strokeWidth={1.75} fill={activo ? "currentColor" : "none"} />
              {item.label}
            </Link>
          );
        })}
      </nav>

      <div className="mt-auto">
        <MenuUsuario me={me} posicion="arriba" />
      </div>
    </aside>
  );
}

/**
 * Menú de usuario compartido por header y sidebar. El botón disparador se etiqueta «Menú de
 * usuario»; abre cambiar contraseña y salir. Un clic fuera lo cierra.
 */
function MenuUsuario({
  me,
  posicion,
}: {
  me: { fullName: string; email: string; role: Role; photoUrl?: string | null };
  posicion: "abajo" | "arriba";
}) {
  const router = useRouter();
  const logout = useLogout();
  const [abierto, setAbierto] = useState(false);
  const [cambiandoClave, setCambiandoClave] = useState(false);

  const disparador =
    posicion === "abajo" ? (
      <button
        type="button"
        aria-label="Menú de usuario"
        onClick={() => setAbierto((v) => !v)}
        className="rounded-full ring-2 ring-transparent transition hover:ring-primary-soft focus-visible:shadow-focus"
      >
        <Avatar nombre={me.fullName} fotoUrl={me.photoUrl} size="sm" />
      </button>
    ) : (
      <button
        type="button"
        aria-label="Menú de usuario"
        onClick={() => setAbierto((v) => !v)}
        className="flex w-full items-center gap-2.5 rounded-pill bg-surface-raised px-3 py-2 text-left shadow-sm transition hover:shadow-md focus-visible:shadow-focus"
      >
        <Avatar nombre={me.fullName} fotoUrl={me.photoUrl} size="sm" />
        <span className="min-w-0">
          <span className="block truncate text-[13px] font-bold text-text">{me.fullName}</span>
          <span className="block truncate text-[11px] text-text-muted">{ETIQUETA_ROL[me.role]}</span>
        </span>
      </button>
    );

  return (
    <div className="relative">
      {disparador}

      {abierto && (
        <>
          <button
            type="button"
            aria-hidden="true"
            tabIndex={-1}
            onClick={() => setAbierto(false)}
            className="fixed inset-0 z-40 cursor-default"
          />
          <div
            className={`absolute z-50 w-56 overflow-hidden rounded-card border border-border bg-surface-raised shadow-lg ${
              posicion === "abajo" ? "right-0 mt-2 top-full" : "bottom-full left-0 mb-2"
            }`}
          >
            <div className="border-b border-surface-sunken px-4 py-3">
              <p className="truncate text-[13px] font-bold text-text">{me.fullName}</p>
              <p className="truncate text-[11.5px] text-text-muted">{me.email}</p>
            </div>
            <button
              type="button"
              onClick={() => {
                setAbierto(false);
                setCambiandoClave(true);
              }}
              className="flex w-full items-center gap-2.5 px-4 py-2.5 text-left text-[13px] font-semibold text-text hover:bg-surface-sunken"
            >
              <KeyRound size={15} strokeWidth={1.75} />
              Cambiar contraseña
            </button>
            <button
              type="button"
              onClick={() =>
                logout.mutate(undefined, { onSuccess: () => router.replace("/login") })
              }
              className="flex w-full items-center gap-2.5 px-4 py-2.5 text-left text-[13px] font-semibold text-error hover:bg-error-bg"
            >
              <LogOut size={15} strokeWidth={1.75} />
              Salir
            </button>
          </div>
        </>
      )}

      {cambiandoClave && <CambiarClave onCerrar={() => setCambiandoClave(false)} />}
    </div>
  );
}
