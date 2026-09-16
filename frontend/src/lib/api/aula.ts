/**
 * El aula, tal como la sirve `/api/v1/bookings/{id}/classroom`.
 *
 * <p>`domain`, `room` y `token` son nulos mientras la sala no se pueda abrir: la antesala tiene que
 * dibujarse igual —con quién es la clase, cuánto falta, la prueba de micrófono— mucho antes de que
 * exista una llave. Una llave solo existe cuando sirve.
 */
export type EstadoDelAula = "CLOSED" | "OPEN" | "STARTED" | "ENDED";

export type ClassroomResponse = {
  state: EstadoDelAula;
  startsAt: string;
  endsAt: string;
  opensAt: string;
  expiresAt: string;
  classMinutes: number;
  moderator: boolean;
  counterpart: {
    name: string;
    firstName: string;
    photoUrl: string | null;
    headline: string | null;
  } | null;
  counterpartPresent: boolean;
  displayName: string;
  domain: string | null;
  room: string | null;
  token: string | null;
};

/**
 * El IFrame API de JaaS, cargado desde el dominio del propio proveedor.
 *
 * <p>No se puede empaquetar con la aplicación: 8x8 sirve el script desde su dominio y ligado al
 * AppID, y es él quien decide qué versión corresponde a la cuenta. Se carga una sola vez y se
 * reutiliza — montarlo dos veces deja dos iframes peleando por el micrófono.
 */
let cargando: Promise<void> | null = null;

export function cargarJitsi(domain: string, appId: string): Promise<void> {
  if (typeof window === "undefined") return Promise.resolve();
  if ((window as { JitsiMeetExternalAPI?: unknown }).JitsiMeetExternalAPI) return Promise.resolve();
  if (cargando) return cargando;

  cargando = new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.src = `https://${domain}/${appId}/external_api.js`;
    script.async = true;
    script.onload = () => resolve();
    script.onerror = () => {
      cargando = null;
      reject(new Error("No se pudo cargar la videollamada."));
    };
    document.body.appendChild(script);
  });
  return cargando;
}

/**
 * El AppID va delante de la sala: `room` llega ya compuesto como «{appId}/{reserva}», que es la
 * forma que espera el IFrame API. El script, en cambio, se pide solo con el AppID.
 */
export const appIdDe = (room: string) => room.split("/")[0];
