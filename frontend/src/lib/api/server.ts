/**
 * Fetch del lado servidor para datos PÚBLICOS (portada, sitemap, generateMetadata). Los componentes
 * cliente usan `apiFetch` contra el mismo origen (el proxy de Next reescribe /api/*); pero en el
 * servidor no existe un origen relativo, así que hablamos directo al backend usando `API_URL` —la
 * misma variable que usa el rewrite de `next.config.ts`—, con `localhost:8080` como respaldo local.
 *
 * Solo endpoints públicos: nunca manda cookies ni CSRF. Si el backend no responde (por ejemplo
 * durante `next build`, cuando no hay backend arriba), devuelve `null` y quien llama degrada con
 * gracia —oculta la sección— en vez de tumbar el render de la página.
 */
const API_BASE = process.env.API_URL ?? "http://localhost:8080";

export async function serverFetch<T>(path: string, revalidateSeconds = 300): Promise<T | null> {
  try {
    const response = await fetch(`${API_BASE}${path}`, {
      headers: { Accept: "application/json" },
      // ISR: la respuesta se cachea y se revalida sola. La portada es estática entre revalidaciones,
      // lo que mantiene el TTFB bajo (bueno para Lighthouse) sin dejar de reflejar el catálogo.
      next: { revalidate: revalidateSeconds },
    });
    if (!response.ok) return null;
    return (await response.json()) as T;
  } catch {
    return null;
  }
}
