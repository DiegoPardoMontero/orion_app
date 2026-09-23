/**
 * A dónde volver después de entrar o de crear la cuenta: quien quiso reservar desde el perfil de
 * un profesor, sin cuenta, vuelve a ese perfil y no a la portada.
 *
 * <p>Solo una ruta de esta misma app. Aceptar cualquier cosa sería un redireccionamiento abierto:
 * un enlace `/login?volver=https://otro.sitio` con nuestra pantalla de entrada delante.
 */
export function destinoSeguro(volver: string | null | undefined): string | null {
  if (!volver) return null;
  // Una sola barra al principio, y nada que el navegador lea como otro origen (`//x`, `/\x`).
  if (!/^\/(?![/\\])/.test(volver)) return null;
  if (/[\s\\]/.test(volver) || volver.length > 200) return null;
  return volver;
}

/** La ruta de entrada que trae de vuelta aquí. */
export function entrarYVolver(base: "/login" | "/registro", ruta: string): string {
  return `${base}?volver=${encodeURIComponent(ruta)}`;
}
