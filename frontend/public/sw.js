/*
 * Service worker mínimo de Orión. Su razón de ser principal es la instalabilidad: sin un SW con
 * un manejador de `fetch`, Chrome no ofrece "Instalar app". El cacheo es conservador para no
 * servir HTML viejo tras un despliegue: navegaciones network-first, y solo los assets con hash
 * inmutable de Next se guardan en caché.
 */
const CACHE = "orion-v1";

self.addEventListener("install", () => {
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener("fetch", (event) => {
  const req = event.request;
  if (req.method !== "GET") return;

  const url = new URL(req.url);
  if (url.origin !== self.location.origin) return;

  // Assets con hash inmutable (JS/CSS de Next) e íconos: cache-first, son seguros de cachear.
  if (url.pathname.startsWith("/_next/static/") || url.pathname.startsWith("/icon")) {
    event.respondWith(
      caches.open(CACHE).then((cache) =>
        cache.match(req).then(
          (hit) =>
            hit ||
            fetch(req).then((res) => {
              cache.put(req, res.clone());
              return res;
            }),
        ),
      ),
    );
    return;
  }

  // Navegaciones: siempre la red primero; si no hay conexión, se cae a lo último cacheado.
  if (req.mode === "navigate") {
    event.respondWith(
      fetch(req).catch(() => caches.match(req).then((hit) => hit || caches.match("/"))),
    );
  }
});
