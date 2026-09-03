import type { Role } from "./session";

export type NavItem = { href: string; label: string };

/** A dónde llega cada rol al entrar. */
export const HOME_BY_ROLE: Record<Role, string> = {
  STUDENT: "/profesores",
  PROFESSOR: "/mis-clases",
  // El admin entra al panel: lo primero que necesita ver es si algo espera su decisión.
  ADMIN: "/admin/panel",
};

export const NAV_BY_ROLE: Record<Role, NavItem[]> = {
  STUDENT: [
    { href: "/profesores", label: "Profesores" },
    { href: "/mis-clases", label: "Mis clases" },
    { href: "/saldo", label: "Pagos" },
    { href: "/mensajes", label: "Mensajes" },
    { href: "/cuenta", label: "Perfil" },
  ],
  PROFESSOR: [
    { href: "/mis-clases", label: "Mis clases" },
    { href: "/ganancias", label: "Ganancias" },
    { href: "/desempeno", label: "Desempeño" },
    { href: "/mensajes", label: "Mensajes" },
    { href: "/disponibilidad", label: "Disponibilidad" },
    { href: "/perfil", label: "Perfil" },
  ],
  ADMIN: [
    { href: "/admin/panel", label: "Panel" },
    { href: "/admin/usuarios", label: "Usuarios" },
    { href: "/admin/aplicaciones", label: "Solicitudes" },
    { href: "/admin/reservas", label: "Reservas" },
    { href: "/admin/pagos", label: "Pagos" },
    { href: "/admin/reclamos", label: "Reclamos" },
    { href: "/admin/resenas", label: "Reseñas" },
  ],
};

/** Qué roles pueden entrar a cada zona de la app. */
const ACCESS: { prefix: string; roles: Role[] }[] = [
  { prefix: "/profesores", roles: ["STUDENT"] },
  { prefix: "/cuenta", roles: ["STUDENT"] },
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
  { prefix: "/aplicacion", roles: ["STUDENT", "PROFESSOR"] },
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
