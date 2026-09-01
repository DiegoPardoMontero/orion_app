/**
 * El único punto por el que el frontend habla con el backend. Ningún componente llama a fetch()
 * directamente: si lo hiciera, tendría que acordarse del CSRF, del parseo de errores y del 401,
 * y tarde o temprano alguno se olvidaría.
 */

/** Error del API con su código HTTP y el mensaje que el backend ya redactó. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly details?: string[],
  ) {
    super(message);
    this.name = "ApiError";
  }
}

const MUTATING = new Set(["POST", "PUT", "PATCH", "DELETE"]);

/**
 * El backend deja el token CSRF en una cookie legible por JS (a propósito: el JavaScript
 * legítimo debe poder copiarlo al header, y un sitio malicioso no puede leerlo por la política
 * de mismo origen). La cookie de sesión, en cambio, es httpOnly y aquí es invisible.
 */
function csrfToken(): string | null {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
  return match ? decodeURIComponent(match[1]) : null;
}

type ApiFetchOptions = {
  method?: string;
  body?: unknown;
  signal?: AbortSignal;
  /**
   * Un 401 normalmente manda al login. La landing pública consulta la sesión de forma opcional
   * (para mostrar "Ir a mi panel") y NO quiere ese redirect: pasa `false` y trata el 401 como
   * "anónimo".
   */
  redirectOn401?: boolean;
};

export async function apiFetch<T>(path: string, options: ApiFetchOptions = {}): Promise<T> {
  const method = options.method ?? "GET";
  const headers: Record<string, string> = {};

  if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
  }

  if (MUTATING.has(method)) {
    const token = csrfToken();
    if (token) {
      headers["X-XSRF-TOKEN"] = token;
    }
  }

  const response = await fetch(path, {
    method,
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
    // Mismo origen gracias al proxy de Next, pero explícito: manda las cookies.
    credentials: "same-origin",
    signal: options.signal,
  });

  if (
    response.status === 401 &&
    (options.redirectOn401 ?? true) &&
    !window.location.pathname.startsWith("/login")
  ) {
    window.location.href = "/login";
    throw new ApiError(401, "Tu sesión expiró");
  }

  if (!response.ok) {
    throw await toApiError(response);
  }

  // 204 (logout) no trae cuerpo.
  if (response.status === 204 || response.headers.get("content-length") === "0") {
    return undefined as T;
  }

  return (await response.json()) as T;
}

/**
 * Subida de foto de perfil (multipart). No usa apiFetch porque el cuerpo es FormData, no JSON:
 * dejamos que el navegador ponga el Content-Type con su boundary. El header CSRF sí va, como en
 * cualquier mutación.
 */
export async function uploadFoto(file: File): Promise<{ photoUrl: string }> {
  const form = new FormData();
  form.append("file", file);

  const headers: Record<string, string> = {};
  const token = csrfToken();
  if (token) {
    headers["X-XSRF-TOKEN"] = token;
  }

  const response = await fetch("/api/v1/me/photo", {
    method: "POST",
    headers,
    body: form,
    credentials: "same-origin",
  });

  if (!response.ok) {
    throw await toApiError(response);
  }
  return (await response.json()) as { photoUrl: string };
}

/**
 * Los errores del backend ya vienen redactados y con la voz institucional
 * ({"error": "Con menos de 24 horas..."}), así que se propagan tal cual: el frontend no
 * reescribe mensajes de negocio.
 */
async function toApiError(response: Response): Promise<ApiError> {
  try {
    const body = (await response.json()) as { error?: string; details?: string[] };
    return new ApiError(
      response.status,
      body.error ?? "Algo no salió como esperábamos. Inténtalo de nuevo.",
      body.details,
    );
  } catch {
    return new ApiError(response.status, "Algo no salió como esperábamos. Inténtalo de nuevo.");
  }
}
