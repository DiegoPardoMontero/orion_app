import type { Role } from "./session";

export type NavItem = { href: string; label: string };

/**
 * Las entradas del lateral, agrupadas por lo que la persona va a hacer con ellas. Un menú plano de
 * siete líneas obliga a leerlas todas cada vez; agrupadas, el ojo salta al bloque y luego a la
 * línea. Los grupos con un solo elemento van sin título: un rótulo para una sola entrada es ruido.
 */
export type NavGroup = { titulo?: string; items: NavItem[] };

/** Donde se recuerda de dónde venía alguien mientras va y vuelve de Google, Apple o Facebook. */
export const DESDE_KEY = "orion:desde";

/**
 * A dónde llega alguien al entrar o crear su cuenta. Quien viene del resultado del diagnóstico
 * («¿Te lo guardamos?») aterriza en su perfil, donde lo ve guardado: el backend ya lo pasó a su
 * cuenta con la cookie de este dispositivo.
 *
 * <p>El origen se lee de la URL (`?desde=diagnostico`) o, si la persona fue y volvió de un
 * proveedor, de sessionStorage, donde lo dejó el botón antes de salir: la vuelta llega a otra URL.
 * Sin useSearchParams, para no obligar a envolver el login en una frontera de Suspense.
 */
export function destinoAlEntrar(role: Role): string {
  let desde: string | null = null;
  if (typeof window !== "undefined") {
    desde = new URLSearchParams(window.location.search).get("desde");
    try {
      desde ??= window.sessionStorage.getItem(DESDE_KEY);
      window.sessionStorage.removeItem(DESDE_KEY);
    } catch {
      // Sin almacenamiento (modo privado estricto): se entra al inicio de su rol, que también vale.
    }
  }
  if (desde === "diagnostico" && role === "STUDENT") return "/cuenta?seccion=resumen";
  return HOME_BY_ROLE[role];
}

/** A dónde llega cada rol al entrar. */
export const HOME_BY_ROLE: Record<Role, string> = {
  STUDENT: "/profesores",
  PROFESSOR: "/mis-clases",
  // El admin entra al panel: lo primero que necesita ver es si algo espera su decisión.
  ADMIN: "/admin/panel",
  // El aspirante entra a su postulación, que es lo único que tiene por hacer aquí.
  TEACHER_APPLICANT: "/aplicacion",
};

export const NAV_BY_ROLE: Record<Role, NavGroup[]> = {
  STUDENT: [
    // «Buscar profesor» entra aquí y el grupo pasa a llamarse «Clases»: buscar al siguiente profesor
    // es parte de tus clases, no una sección aparte, y suelto arriba competía con la agenda sin
    // ganarle. Tres entradas y no más — apretar aquí lo que sea es lo que había que evitar.
    {
      titulo: "Clases",
      items: [
        { href: "/profesores", label: "Buscar profesor" },
        { href: "/mis-clases", label: "Mi agenda" },
        { href: "/mensajes", label: "Mensajes" },
      ],
    },
    {
      titulo: "Mi cuenta",
      items: [
        // «Mi cielo» ya no está suelto: vive dentro del perfil, que es de quien es.
        { href: "/cuenta", label: "Perfil" },
        { href: "/saldo", label: "Pagos y saldo" },
        { href: "/ayuda", label: "Ayuda" },
      ],
    },
  ],
  PROFESSOR: [
    {
      titulo: "Enseñar",
      items: [
        { href: "/mis-clases", label: "Agenda" },
        { href: "/disponibilidad", label: "Disponibilidad" },
        { href: "/mensajes", label: "Mensajes" },
      ],
    },
    {
      titulo: "Mi trabajo",
      items: [
        { href: "/ganancias", label: "Ganancias" },
        { href: "/desempeno", label: "Desempeño" },
      ],
    },
    {
      titulo: "Mi cuenta",
      items: [
        { href: "/perfil", label: "Perfil público" },
        { href: "/ayuda", label: "Ayuda" },
      ],
    },
  ],
  // El aspirante no tiene menú de estudiante porque no tiene experiencia de estudiante: mientras
  // su postulación espera, lo único que puede hacer en Orión es llevarla y cuidar su cuenta.
  TEACHER_APPLICANT: [
    { items: [{ href: "/aplicacion/estado", label: "Mi postulación" }] },
    // El aspirante es quien más dudas tiene y el único que no puede resolverlas por su cuenta:
    // no tiene clases, ni saldo, ni mensajería. Dejarlo sin canal sería dejarlo sin nadie.
    { items: [{ href: "/ayuda", label: "Ayuda" }] },
  ],
  ADMIN: [
    { items: [{ href: "/admin/panel", label: "Panel" }] },
    {
      titulo: "Operación",
      items: [
        { href: "/admin/reservas", label: "Clases" },
        { href: "/admin/pagos", label: "Pagos" },
        { href: "/admin/devoluciones", label: "Devoluciones" },
      ],
    },
    {
      titulo: "Personas",
      items: [
        { href: "/admin/usuarios", label: "Usuarios" },
        { href: "/admin/aplicaciones", label: "Postulaciones" },
      ],
    },
    {
      titulo: "Por resolver",
      items: [
        { href: "/admin/reclamos", label: "Reclamos" },
        { href: "/admin/soporte", label: "Soporte" },
        { href: "/admin/llamadas", label: "Llamadas" },
        { href: "/admin/resenas", label: "Reseñas" },
      ],
    },
    {
      titulo: "Configuración",
      items: [
        { href: "/admin/ajustes", label: "Ajustes" },
        { href: "/admin/sistema", label: "Sistema" },
      ],
    },
  ],
};

/**
 * Lo que cabe en la barra inferior de móvil. Cinco como máximo, y elegidas: no es el menú completo
 * recortado, son los destinos a los que de verdad se salta a diario. El resto vive en el lateral de
 * escritorio y en el menú de usuario.
 */
export const TABS_BY_ROLE: Record<Role, NavItem[]> = {
  STUDENT: [
    { href: "/profesores", label: "Buscar" },
    { href: "/mis-clases", label: "Agenda" },
    { href: "/mensajes", label: "Mensajes" },
    { href: "/saldo", label: "Pagos" },
    { href: "/cuenta", label: "Perfil" },
  ],
  PROFESSOR: [
    { href: "/mis-clases", label: "Agenda" },
    { href: "/disponibilidad", label: "Horarios" },
    { href: "/mensajes", label: "Mensajes" },
    { href: "/ganancias", label: "Ganancias" },
    { href: "/perfil", label: "Perfil" },
  ],
  TEACHER_APPLICANT: [{ href: "/aplicacion/estado", label: "Postulación" }],
  ADMIN: [
    { href: "/admin/panel", label: "Panel" },
    { href: "/admin/reclamos", label: "Reclamos" },
    { href: "/admin/pagos", label: "Pagos" },
    { href: "/admin/usuarios", label: "Usuarios" },
  ],
};

/** Qué roles pueden entrar a cada zona de la app. */
const ACCESS: { prefix: string; roles: Role[] }[] = [
  { prefix: "/profesores", roles: ["STUDENT"] },
  { prefix: "/cuenta", roles: ["STUDENT"] },
  { prefix: "/logros", roles: ["STUDENT"] },
  // El perfil público de un estudiante lo abre quien tenga derecho a verlo; el servidor decide
  // cuál de las tres capas aplica y responde 404 cuando no, así que aquí solo se deja pasar.
  { prefix: "/estudiantes", roles: ["STUDENT", "PROFESSOR", "ADMIN"] },
  // Dinero: el saldo y el historial son del estudiante; las ganancias, de quien da la clase.
  { prefix: "/saldo", roles: ["STUDENT"] },
  { prefix: "/pago", roles: ["STUDENT"] },
  { prefix: "/ganancias", roles: ["PROFESSOR"] },
  { prefix: "/desempeno", roles: ["PROFESSOR"] },
  { prefix: "/mis-clases", roles: ["STUDENT", "PROFESSOR"] },
  { prefix: "/mensajes", roles: ["STUDENT", "PROFESSOR"] },
  { prefix: "/disponibilidad", roles: ["PROFESSOR"] },
  { prefix: "/perfil", roles: ["PROFESSOR"] },
  // La postulación a profesor: la abre un estudiante que quiere enseñar o un profesor recién
  // creado por el admin que aún no completa su perfil. El admin revisa desde /admin/aplicaciones.
  { prefix: "/aplicacion", roles: ["STUDENT", "PROFESSOR", "TEACHER_APPLICANT"] },
  // Ayuda: todos los que estén dentro, incluido el aspirante. Va explícito y no por el "todo lo
  // no listado se permite" para que se lea como una decisión y no como un olvido.
  { prefix: "/ayuda", roles: ["STUDENT", "PROFESSOR", "TEACHER_APPLICANT", "ADMIN"] },
  { prefix: "/admin", roles: ["ADMIN"] },
];

/**
 * La guarda del cliente es comodidad, no seguridad: quien la salte se topa igual con el 403 del
 * backend, que es quien de verdad decide. Aquí solo evitamos enseñar una pantalla que no aplica.
 */
export function canAccess(role: Role, pathname: string): boolean {
  const rule = ACCESS.find((entry) => pathname.startsWith(entry.prefix));
  return rule ? rule.roles.includes(role) : true;
}
