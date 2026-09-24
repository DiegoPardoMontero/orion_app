"use client";

import {
  Banknote,
  BarChart3,
  CalendarClock,
  CalendarDays,
  CalendarRange,
  ClipboardList,
  Coins,
  Gavel,
  GraduationCap,
  LayoutDashboard,
  KeyRound,
  LifeBuoy,
  LogOut,
  MessageCircle,
  HeartPulse,
  PanelLeftClose,
  PanelLeftOpen,
  SlidersHorizontal,
  Undo2,
  Sparkles,
  User,
  Star,
  Users,
  Wallet,
  type LucideIcon,
} from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { Suspense, useEffect, useState, type ReactNode } from "react";
import { CambiarClave } from "@/components/CambiarClave";
import { CampanaNotificaciones } from "@/components/CampanaNotificaciones";
import { Vacio } from "@/components/estados";
import { AvisoCorreoSinVerificar } from "@/components/AvisoCorreoSinVerificar";
import { AvisoMayoriaDeEdad } from "@/components/AvisoMayoriaDeEdad";
import { Bienvenida } from "@/components/bienvenida/Bienvenida";
import { Encendido } from "@/components/gamificacion/Encendido";
import { FranjaDeFicha } from "@/components/FranjaDeFicha";
import { Wordmark } from "@/components/marca";
import { MisPuntosChip } from "@/components/Puntos";
import { Boton } from "@/components/ui";
import { useMiAplicacion } from "@/lib/aplicacion";
import {
  canAccess,
  esRutaPublica,
  HOME_BY_ROLE,
  NAV_BY_ROLE,
  TABS_BY_ROLE,
  type NavGroup,
  type NavItem,
} from "@/lib/auth/roles";
import type { Role } from "@/lib/auth/session";
import { useLogout, useMe } from "@/lib/auth/session";
import { useCerrarConEscape } from "@/lib/useCerrarConEscape";
import { useMensajesNoLeidos } from "@/lib/mensajeria";
import { MiAvatar } from "@/components/gamificacion/MiAvatar";
import { NavPublica } from "@/components/NavPublica";

/** Cada ruta lleva su ícono; el activo va relleno para no marcarse solo por color. */
const ICONO: Record<string, LucideIcon> = {
  "/profesores": Users,
  "/saldo": Wallet,
  "/ganancias": Banknote,
  "/admin/pagos": Coins,
  "/admin/panel": LayoutDashboard,
  "/admin/reclamos": Gavel,
  "/admin/resenas": Star,
  "/desempeno": BarChart3,
  "/mis-clases": CalendarDays,
  "/mensajes": MessageCircle,
  "/cuenta": User,
  "/logros": Sparkles,
  "/disponibilidad": CalendarClock,
  "/perfil": User,
  "/aplicacion/estado": GraduationCap,
  "/aplicacion": GraduationCap,
  "/admin/usuarios": Users,
  "/admin/aplicaciones": ClipboardList,
  "/admin/reservas": CalendarRange,
  "/ayuda": LifeBuoy,
  "/admin/soporte": LifeBuoy,
  "/admin/ajustes": SlidersHorizontal,
  "/admin/sistema": HeartPulse,
  "/admin/devoluciones": Undo2,
};

/** Rutas del profesor que exigen postulación APPROVED; si no, se muestra un aviso en vez de la UI. */
const RUTAS_PROFESOR_APROBADO = ["/disponibilidad", "/perfil"];

const ETIQUETA_ROL: Record<Role, string> = {
  STUDENT: "Estudiante",
  PROFESSOR: "Profesor",
  ADMIN: "Administración",
  TEACHER_APPLICANT: "Aspirante a profesor",
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

  // La postulación interesa a quien puede postular; el admin nunca.
  const esAplicante =
    me?.role === "STUDENT" || me?.role === "PROFESSOR" || me?.role === "TEACHER_APPLICANT";
  const aplic = useMiAplicacion(esAplicante);

  // Los mensajes internos son de estudiante y profesor. El aspirante no los tiene: preguntarlos
  // por él solo consigue un 403 en la consola en cada pantalla que abra.
  const tieneMensajeria = me?.role === "STUDENT" || me?.role === "PROFESSOR";
  const noLeidosMensajes = useMensajesNoLeidos(tieneMensajeria);

  // El catálogo y el perfil de un profesor se ven sin cuenta: ahí, sin sesión, no se salta al login.
  const publica = esRutaPublica(pathname);

  useEffect(() => {
    if (isError) {
      if (!publica) router.replace("/login");
      return;
    }
    if (me && !allowed) {
      router.replace(HOME_BY_ROLE[me.role]);
    }
  }, [isError, me, allowed, router, publica]);

  if (isError && publica) {
    return (
      <div className="flex min-h-dvh flex-col">
        <NavPublica />
        <div className="flex-1">{children}</div>
      </div>
    );
  }

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

  /**
   * El aula va a pantalla completa, sin lateral ni barra inferior.
   *
   * <p>No es solo estética. Mientras estabas en clase, el menú te dejaba navegar a otra sección con
   * un clic, y salir así no colgaba: la conferencia te daba por dentro un rato más y, al volver,
   * aparecías dos veces. Con dos personas eso parecen tres.
   *
   * <p>Quitando el armazón, la única salida es el botón de la propia aula, que cuelga antes de irse.
   * Se entra desde «Mis clases» y se sale por ahí: una puerta, y siempre la misma.
   */
  const enClase = /^\/mis-clases\/[^/]+\/aula$/.test(pathname);

  /**
   * Practicar, en el celular, también va sin cabecera ni barra inferior (handoff de práctica, §8): es
   * un flujo enfocado de cinco minutos, y la salida es «Salir y seguir luego», que avisa que se
   * guardó. En escritorio el lateral se queda: ahí no estorba y orienta.
   */
  const practicando = /^\/practica\/[^/]+$/.test(pathname);

  if (enClase) {
    return (
      <>
        {children}
        <Encendido />
      </>
    );
  }

  return (
    <div className="lg:flex lg:min-h-dvh">
      <Sidebar me={me} grupos={nav} pathname={pathname} noLeidosMensajes={noLeidosMensajes} />

      <div className="flex min-h-dvh flex-1 flex-col">
        {!practicando && <MobileHeader me={me} />}
        {/* Barra y no diálogo: la tarea está a medias en el buzón, y bloquear la app no la acerca.
            Quien de verdad la necesita es quien va a reservar, y ahí el backend responde 422. */}
        {!me.emailVerified && (
          <AvisoCorreoSinVerificar
            correo={me.email}
            ensena={me.role === "PROFESSOR" || me.role === "TEACHER_APPLICANT"}
          />
        )}
        {/* La ficha a medias: Rigel lo recuerda en todas las pantallas hasta que se complete. */}
        {me.role === "STUDENT" && (
          <Suspense fallback={null}>
            <FranjaDeFicha />
          </Suspense>
        )}
        <div className={`flex-1 lg:pb-0 ${practicando ? "" : "pb-24"}`}>
          {rutaProtegida ? <GateProfesor aplic={aplic}>{children}</GateProfesor> : children}
        </div>
        {!practicando && <TabBar nav={TABS_BY_ROLE[me.role]} pathname={pathname} noLeidosMensajes={noLeidosMensajes} />}
      </div>

      {/* La celebración vive en el armazón y no en una pantalla: una estrella se enciende cuando
          termina una clase, que casi nunca es mientras se mira el tablero de logros. */}
      <Encendido />

      {/* Las cuentas anteriores a la regla de mayoría de edad nunca la declararon. Va aquí y no en
          una pantalla porque hay que pedirla entren por donde entren. */}
      {!me.adultConfirmed && <AvisoMayoriaDeEdad />}

      {/* La bienvenida espera a la declaración de edad: dos diálogos a la vez no se leen. */}
      {me.adultConfirmed && <Bienvenida me={me} />}
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
): NavGroup[] {
  const base = NAV_BY_ROLE[role];
  if (!app.resuelto) return base;

  const grupo: NavGroup = {
    titulo: "Postulación",
    items: [{ href: "/aplicacion/estado", label: "Mi solicitud" }],
  };

  // El aspirante ya lleva su postulación en el menú base: añadirla otra vez la duplicaría.
  const enCurso =
    (role === "PROFESSOR" && !app.aprobado) ||
    (role === "STUDENT" && !app.noAplico && !!app.status);

  return enCurso ? [...base, grupo] : base;
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
function MobileHeader({ me }: { me: { fullName: string; email: string; role: Role; photoUrl?: string | null; hasPassword?: boolean } }) {
  return (
    <header className="sticky top-0 z-30 flex h-16 items-center justify-between border-b border-surface-sunken bg-surface px-5 lg:hidden">
      <Wordmark className="text-[16px] text-primary" />
      <div className="flex items-center gap-1">
        {me.role === "STUDENT" && (
          <Link href="/cuenta?seccion=resumen#puntos" className="mr-1 rounded-pill focus-visible:shadow-focus">
            <MisPuntosChip compacto />
          </Link>
        )}
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
            data-tour={`nav:${item.href}`}
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
  grupos,
  pathname,
  noLeidosMensajes,
}: {
  me: { fullName: string; email: string; role: Role; photoUrl?: string | null; hasPassword?: boolean };
  grupos: NavGroup[];
  pathname: string;
  noLeidosMensajes: number;
}) {
  // Colapsado se recuerda entre pantallas y entre visitas: quien lo cierra para tener más sitio no
  // quiere volver a cerrarlo en cada navegación. `localStorage` puede fallar (ventana privada,
  // datos bloqueados), y si falla el lateral simplemente sale abierto, que es el valor bueno.
  const [colapsado, setColapsado] = useState(false);

  useEffect(() => {
    try {
      // La regla pide no llamar a setState dentro de un efecto, y con razón en el caso habitual.
      // Aquí el dato vive en `localStorage`, que no se puede leer durante el render sin romper el
      // renderizado en el servidor: el servidor no sabe si este navegador lo dejó plegado.
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setColapsado(window.localStorage.getItem("orion.lateral") === "colapsado");
    } catch {
      // Sin almacenamiento: abierto, que es el valor bueno.
    }
  }, []);

  function alternar() {
    setColapsado((antes) => {
      const ahora = !antes;
      try {
        window.localStorage.setItem("orion.lateral", ahora ? "colapsado" : "abierto");
      } catch {
        // No poder recordarlo no impide plegarlo ahora.
      }
      return ahora;
    });
  }

  return (
    <aside
      className={`sticky top-0 hidden h-dvh shrink-0 flex-col border-r border-surface-sunken bg-surface py-6 transition-[width] lg:flex ${
        colapsado ? "w-[76px] px-3" : "w-[248px] px-4"
      }`}
    >
      <div className={`flex items-center ${colapsado ? "justify-center" : "justify-between pl-3"}`}>
        {!colapsado && <Wordmark className="text-[18px] text-primary" />}
        {!colapsado && <CampanaNotificaciones anclaje="izquierda" />}
        {colapsado && <CampanaNotificaciones anclaje="izquierda" />}
      </div>

      <button
        type="button"
        onClick={alternar}
        aria-expanded={!colapsado}
        aria-label={colapsado ? "Expandir el menú" : "Colapsar el menú"}
        title={colapsado ? "Expandir el menú" : "Colapsar el menú"}
        className={`mt-4 flex h-9 items-center gap-2 rounded-pill text-[12.5px] font-semibold text-text-muted transition-colors hover:bg-surface-sunken hover:text-text focus-visible:shadow-focus ${
          colapsado ? "justify-center px-0" : "px-4"
        }`}
      >
        {colapsado ? (
          <PanelLeftOpen size={18} strokeWidth={1.9} />
        ) : (
          <>
            <PanelLeftClose size={18} strokeWidth={1.9} />
            Colapsar
          </>
        )}
      </button>

      {!colapsado && (
        <p className="mt-6 px-3 text-[11px] font-bold uppercase tracking-[0.1em] text-text-muted">
          {ETIQUETA_ROL[me.role]}
        </p>
      )}

      <nav className={`flex flex-col overflow-y-auto ${colapsado ? "mt-4 gap-2" : "mt-3 gap-4"}`}>
        {grupos.map((grupo, i) => (
          <div key={grupo.titulo ?? `g${i}`} className="flex flex-col gap-1">
            {grupo.titulo && !colapsado && (
              <p className="px-4 pb-1 text-[10.5px] font-bold uppercase tracking-[0.11em] text-text-muted">
                {grupo.titulo}
              </p>
            )}
            {grupo.items.map((item) => {
              const activo = pathname.startsWith(item.href);
              const Icono = ICONO[item.href] ?? Users;
              const badge = item.href === "/mensajes" ? noLeidosMensajes : 0;
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  data-tour={`nav:${item.href}`}
                  aria-current={activo ? "page" : undefined}
                  // Colapsado, el nombre se va de la pantalla pero no del árbol accesible: el
                  // `title` lo devuelve al pasar el ratón y `aria-label` a quien no ve el icono.
                  title={colapsado ? item.label : undefined}
                  aria-label={colapsado ? item.label : undefined}
                  className={`relative flex h-10 items-center rounded-pill text-[14px] transition-colors ${
                    colapsado ? "justify-center px-0" : "gap-3 px-4"
                  } ${
                    activo
                      ? "bg-primary-soft font-bold text-primary-strong"
                      : "font-semibold text-text-secondary hover:bg-surface-sunken"
                  }`}
                >
                  <Icono size={18} strokeWidth={1.75} fill={activo ? "currentColor" : "none"} />
                  {!colapsado && item.label}
                  {badge > 0 && (
                    <span
                      className={`grid h-[18px] min-w-[18px] place-items-center rounded-pill bg-primary px-1.5 text-[11px] font-bold text-on-primary ${
                        colapsado ? "absolute right-1 top-0.5" : "ml-auto"
                      }`}
                    >
                      {badge > 9 ? "9+" : badge}
                    </span>
                  )}
                </Link>
              );
            })}
          </div>
        ))}
      </nav>

      <div className="mt-auto">
        {colapsado ? (
          <div className="flex justify-center">
            <MiAvatar size={36} />
          </div>
        ) : (
          <MenuUsuario me={me} posicion="arriba" />
        )}
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
  me: { fullName: string; email: string; role: Role; photoUrl?: string | null; hasPassword?: boolean };
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
        <MiAvatar size={36} />
      </button>
    ) : (
      <button
        type="button"
        aria-label="Menú de usuario"
        onClick={() => setAbierto((v) => !v)}
        className="flex w-full items-center gap-2.5 rounded-pill bg-surface-raised px-3 py-2 text-left shadow-sm transition hover:shadow-md focus-visible:shadow-focus"
      >
        <MiAvatar size={36} />
        <span className="min-w-0">
          <span className="block truncate text-[13px] font-bold text-text">{me.fullName}</span>
          <span className="flex items-center gap-1.5 truncate text-[11px] text-text-muted">
            {ETIQUETA_ROL[me.role]}
            {me.role === "STUDENT" && <MisPuntosChip compacto />}
          </span>
        </span>
      </button>
    );

  useCerrarConEscape(abierto, () => setAbierto(false));

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
              {me.role === "STUDENT" && (
                <Link
                  href="/cuenta?seccion=resumen#puntos"
                  onClick={() => setAbierto(false)}
                  className="mt-2 inline-flex rounded-pill focus-visible:shadow-focus"
                >
                  <MisPuntosChip />
                </Link>
              )}
            </div>
            {/* La barra inferior de móvil está topada en cinco entradas y ya está llena, así que
                Ayuda cuelga de aquí: sin esto solo sería alcanzable escribiendo la URL. */}
            <Link
              href="/ayuda"
              onClick={() => setAbierto(false)}
              className="flex w-full items-center gap-2.5 px-4 py-2.5 text-left text-[13px] font-semibold text-text hover:bg-surface-sunken"
            >
              <LifeBuoy size={15} strokeWidth={1.75} />
              Ayuda
            </Link>
            <button
              type="button"
              onClick={() => {
                setAbierto(false);
                setCambiandoClave(true);
              }}
              className="flex w-full items-center gap-2.5 px-4 py-2.5 text-left text-[13px] font-semibold text-text hover:bg-surface-sunken"
            >
              <KeyRound size={15} strokeWidth={1.75} />
              {me.hasPassword === false ? "Crear una contraseña" : "Cambiar contraseña"}
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
