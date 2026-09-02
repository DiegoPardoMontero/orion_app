"use client";

import {
  Banknote,
  CalendarClock,
  CalendarDays,
  CalendarRange,
  ClipboardList,
  Coins,
  GraduationCap,
  KeyRound,
  LogOut,
  MessageCircle,
  User,
  Users,
  Wallet,
  type LucideIcon,
} from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
import { Avatar } from "@/components/Avatar";
import { CambiarClave } from "@/components/CambiarClave";
import { CampanaNotificaciones } from "@/components/CampanaNotificaciones";
import { Vacio } from "@/components/estados";
import { Wordmark } from "@/components/marca";
import { Boton } from "@/components/ui";
import { useMiAplicacion } from "@/lib/aplicacion";
import { canAccess, HOME_BY_ROLE, NAV_BY_ROLE, type NavItem } from "@/lib/auth/roles";
import type { Role } from "@/lib/auth/session";
import { useLogout, useMe } from "@/lib/auth/session";
import { useMensajesNoLeidos } from "@/lib/mensajeria";

/** Cada ruta lleva su ícono; el activo va relleno para no marcarse solo por color. */
const ICONO: Record<string, LucideIcon> = {
  "/profesores": Users,
  "/saldo": Wallet,
  "/ganancias": Banknote,
  "/admin/pagos": Coins,
  "/mis-clases": CalendarDays,
  "/mensajes": MessageCircle,
  "/cuenta": User,
  "/disponibilidad": CalendarClock,
  "/perfil": User,
  "/aplicacion/estado": GraduationCap,
  "/aplicacion": GraduationCap,
  "/admin/usuarios": Users,
  "/admin/aplicaciones": ClipboardList,
  "/admin/reservas": CalendarRange,
};

/** Rutas del profesor que exigen postulación APPROVED; si no, se muestra un aviso en vez de la UI. */
const RUTAS_PROFESOR_APROBADO = ["/disponibilidad", "/perfil"];

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

  // La postulación solo interesa a quien puede postular (estudiante o profesor); el admin nunca.
  const esAplicante = me?.role === "STUDENT" || me?.role === "PROFESSOR";
  const aplic = useMiAplicacion(esAplicante);

  // Los mensajes internos son de estudiante y profesor: el badge suma los no leídos de la bandeja.
  const noLeidosMensajes = useMensajesNoLeidos(esAplicante);

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

  const nav = construirNav(me.role, {
    resuelto: !esAplicante || aplic.isFetched,
    noAplico: aplic.noAplico,
    aprobado: aplic.aprobado,
    status: aplic.status,
  });

  // Portal del profesor no aprobado: en /disponibilidad y /perfil, si no está aprobado, se le
  // muestra un aviso amable en vez de la pantalla (que dependería de permisos que aún no tiene).
  const rutaProtegida =
    me.role === "PROFESSOR" && RUTAS_PROFESOR_APROBADO.some((r) => pathname.startsWith(r));

  return (
    <div className="lg:flex lg:min-h-dvh">
      <Sidebar me={me} nav={nav} pathname={pathname} noLeidosMensajes={noLeidosMensajes} />

      <div className="flex min-h-dvh flex-1 flex-col">
        <MobileHeader me={me} />
        <div className="flex-1 pb-24 lg:pb-0">
          {rutaProtegida ? <GateProfesor aplic={aplic}>{children}</GateProfesor> : children}
        </div>
        <TabBar nav={nav} pathname={pathname} noLeidosMensajes={noLeidosMensajes} />
      </div>
    </div>
  );
}

/**
 * Construye la navegación del rol, añadiendo "Mi solicitud" cuando aplica: al profesor no aprobado
 * (o sin postular) y al estudiante que ya empezó una postulación. Un profesor aprobado ve su menú
 * normal, sin ruido.
 */
function construirNav(
  role: Role,
  app: { resuelto: boolean; noAplico: boolean; aprobado: boolean; status?: string },
): NavItem[] {
  const base = NAV_BY_ROLE[role];
  if (!app.resuelto) return base;

  const item: NavItem = { href: "/aplicacion/estado", label: "Mi solicitud" };

  if (role === "PROFESSOR" && !app.aprobado) {
    return [...base, item];
  }
  if (role === "STUDENT" && !app.noAplico && app.status) {
    return [...base, item];
  }
  return base;
}

/**
 * Aviso del portal del profesor no aprobado. Nunca bloquea a un aprobado (deja pasar), ni siquiera
 * ante un error transitorio distinto de 404 (falla en abierto: el backend sigue siendo el árbitro).
 */
function GateProfesor({
  aplic,
  children,
}: {
  aplic: ReturnType<typeof useMiAplicacion>;
  children: ReactNode;
}) {
  if (aplic.isPending) {
    return (
      <div className="grid flex-1 place-items-center py-16">
        <p className="text-sm text-text-muted">Cargando…</p>
      </div>
    );
  }

  // Aprobado, o error transitorio no-404: dejamos pasar.
  if (aplic.aprobado || (aplic.isError && !aplic.noAplico)) {
    return <>{children}</>;
  }

  const status = aplic.status;
  const enRevision = status === "PENDING_REVIEW" || status === "UNDER_REVIEW";
  const necesitaCambios = status === "CHANGES_REQUESTED";
  const rechazada = status === "REJECTED";

  const titulo = enRevision
    ? "Tu perfil está en revisión"
    : necesitaCambios
      ? "Tu postulación necesita ajustes"
      : rechazada
        ? "Tu postulación no fue aprobada"
        : "Completa tu postulación";

  const texto = enRevision
    ? "Estamos revisando tu postulación. Cuando la aprobemos, podrás publicar tu perfil y abrir tu disponibilidad."
    : necesitaCambios
      ? "La revisión pidió algunos ajustes. Edítalos y vuelve a enviar tu postulación."
      : rechazada
        ? "Consulta el detalle en tu solicitud. Podrás postularte de nuevo más adelante."
        : "Antes de publicar tu perfil y tu disponibilidad, completa y envía tu postulación de profesor.";

  const irAEstado = enRevision || rechazada;

  return (
    <main className="mx-auto w-full max-w-lg px-5 py-10">
      <Vacio
        mascota
        titulo={titulo}
        texto={texto}
        accion={
          <Link href={irAEstado ? "/aplicacion/estado" : "/aplicacion"}>
            <Boton variante="primario" className="h-12">
              {irAEstado ? "Ver mi solicitud" : "Ir a mi postulación"}
            </Boton>
          </Link>
        }
      />
    </main>
  );
}

/** Cabecera móvil: 64 px, logotipo coral + menú de usuario. Sobre superficie clara. */
function MobileHeader({ me }: { me: { fullName: string; email: string; role: Role; photoUrl?: string | null } }) {
  return (
    <header className="sticky top-0 z-30 flex h-16 items-center justify-between border-b border-surface-sunken bg-surface px-5 lg:hidden">
      <Wordmark className="text-[16px] text-primary" />
      <div className="flex items-center gap-1">
        <CampanaNotificaciones />
        <MenuUsuario me={me} posicion="abajo" />
      </div>
    </header>
  );
}

/** Tab bar inferior (móvil): 68 px + safe-area, una entrada por sección del rol. */
function TabBar({
  nav,
  pathname,
  noLeidosMensajes,
}: {
  nav: NavItem[];
  pathname: string;
  noLeidosMensajes: number;
}) {
  return (
    <nav
      className="fixed inset-x-0 bottom-0 z-30 flex items-stretch justify-around border-t border-surface-sunken bg-surface-raised lg:hidden"
      style={{ height: "calc(68px + env(safe-area-inset-bottom))", paddingBottom: "env(safe-area-inset-bottom)" }}
    >
      {nav.map((item) => {
        const activo = pathname.startsWith(item.href);
        const Icono = ICONO[item.href] ?? Users;
        const badge = item.href === "/mensajes" ? noLeidosMensajes : 0;
        return (
          <Link
            key={item.href}
            href={item.href}
            aria-current={activo ? "page" : undefined}
            className="flex min-w-0 flex-1 flex-col items-center justify-center gap-1 pt-2"
          >
            <span
              className={`relative flex items-center rounded-pill px-3.5 py-[5px] transition-colors ${
                activo ? "bg-primary-soft text-primary" : "text-text-muted"
              }`}
            >
              <Icono size={21} strokeWidth={1.75} fill={activo ? "currentColor" : "none"} />
              {badge > 0 && (
                <span className="absolute -right-0.5 -top-0.5 grid h-[15px] min-w-[15px] place-items-center rounded-pill bg-primary px-1 text-[9px] font-bold text-on-primary">
                  {badge > 9 ? "9+" : badge}
                </span>
              )}
            </span>
            {/* min-w-0 + truncate: con cinco pestañas (el dinero entró a la barra) una etiqueta
                larga como "Disponibilidad" ya no cabe en su quinto de pantalla. Se recorta con
                puntos suspensivos en vez de empujar a las vecinas. */}
            <span
              className={`w-full truncate px-1 text-center text-[11px] ${
                activo ? "font-bold text-primary" : "font-semibold text-text-muted"
              }`}
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
  noLeidosMensajes,
}: {
  me: { fullName: string; email: string; role: Role; photoUrl?: string | null };
  nav: NavItem[];
  pathname: string;
  noLeidosMensajes: number;
}) {
  return (
    <aside className="sticky top-0 hidden h-dvh w-[248px] shrink-0 flex-col border-r border-surface-sunken bg-surface px-4 py-6 lg:flex">
      <div className="flex items-center justify-between pl-3">
        <Wordmark className="text-[18px] text-primary" />
        <CampanaNotificaciones />
      </div>

      <p className="mt-8 px-3 text-[11px] font-bold uppercase tracking-[0.1em] text-text-muted">
        {ETIQUETA_ROL[me.role]}
      </p>

      <nav className="mt-3 flex flex-col gap-1.5">
        {nav.map((item) => {
          const activo = pathname.startsWith(item.href);
          const Icono = ICONO[item.href] ?? Users;
          const badge = item.href === "/mensajes" ? noLeidosMensajes : 0;
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
              {badge > 0 && (
                <span className="ml-auto grid h-[18px] min-w-[18px] place-items-center rounded-pill bg-primary px-1.5 text-[11px] font-bold text-on-primary">
                  {badge > 9 ? "9+" : badge}
                </span>
              )}
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
