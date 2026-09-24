/*
 * Service worker mínimo de Orión. Su razón de ser principal es la instalabilidad: sin un SW con
 * un manejador de `fetch`, Chrome no ofrece "Instalar app". El cacheo es conservador para no
 * servir HTML viejo tras un despliegue: navegaciones network-first, y solo los assets con hash
 * inmutable de Next se guardan en caché.
 */
const CACHE = "orion-v1";

/*
 * Avisos en el dispositivo (Web Push). El backend manda {title, body, url, tag}, cifrado; el
 * navegador lo descifra antes de entregarlo aquí. `tag` agrupa: un segundo aviso del mismo tipo
 * reemplaza al anterior en vez de apilarse.
 */
self.addEventListener("push", (event) => {
  let aviso = { title: "Orión", body: "", url: "/", tag: "orion" };
  try {
    aviso = { ...aviso, ...event.data.json() };
  } catch {
    // Sin cuerpo legible, igual se muestra algo: un push silencioso haría que el navegador lo castigue.
  }
  event.waitUntil(
    self.registration.showNotification(aviso.title, {
      body: aviso.body,
      tag: aviso.tag,
      icon: "/icon-192.png",
      badge: "/icon-192.png",
      data: { url: aviso.url },
    }),
  );
});

/* Tocar el aviso lleva a su pantalla: a una pestaña de Orión que ya esté abierta, o a una nueva. */
self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const pedido = new URL(event.notification.data?.url || "/", self.location.origin);
  // Solo dentro de Orión: un aviso nunca abre otra página.
  const destino = pedido.origin === self.location.origin ? pedido.href : self.location.origin + "/";
  event.waitUntil(
    self.clients.matchAll({ type: "window", includeUncontrolled: true }).then((ventanas) => {
      const abierta = ventanas.find((v) => v.url.startsWith(self.location.origin));
      if (abierta) {
        return abierta.focus().then((v) => (v && "navigate" in v ? v.navigate(destino) : undefined));
      }
      return self.clients.openWindow(destino);
    }),
  );
});

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
