#!/bin/sh
set -e

# Next congela el destino de rewrites() en `next build` (queda en routes-manifest.json), así que
# la promesa de "misma imagen en todos lados" no se cumple sola. La imagen se construye con un
# placeholder y aquí, al arrancar, lo sustituimos por el API_URL real del entorno (local usa
# backend:8080; Railway, backend.railway.internal:8080). Una sola imagen, destino en runtime.
: "${API_URL:=http://localhost:8080}"

for f in .next/routes-manifest.json .next/required-server-files.json server.js; do
  if [ -f "$f" ]; then
    sed -i "s#http://__API_URL__#${API_URL}#g" "$f"
  fi
done

exec node server.js
