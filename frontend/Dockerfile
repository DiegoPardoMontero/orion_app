FROM node:24-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

FROM node:24-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
# Horneamos un placeholder en el destino del rewrite; el entrypoint lo sustituye por el API_URL
# real al arrancar. Así la MISMA imagen sirve en cualquier entorno (local y Railway).
ENV API_URL=http://__API_URL__
RUN npm run build

FROM node:24-alpine AS run
WORKDIR /app
ENV NODE_ENV=production
# El server standalone de Next escucha en localhost por defecto; dentro de un contenedor eso
# significa "nadie me alcanza desde fuera". 0.0.0.0 lo hace accesible.
ENV HOSTNAME=0.0.0.0
RUN addgroup -S orion && adduser -S orion -G orion && chown orion:orion /app
# --chown para que el usuario no-root pueda reescribir el manifest en el entrypoint: sed -i crea
# un temp file junto al original, así que necesita escribir tanto los archivos como su directorio
# (por eso también el chown de /app de arriba, donde queda server.js).
COPY --from=build --chown=orion:orion /app/.next/standalone ./
COPY --from=build --chown=orion:orion /app/.next/static ./.next/static
COPY --from=build --chown=orion:orion /app/public ./public
# Se toma de la etapa `build` y NO del contexto, aunque el archivo esté ahí al lado. Motivo: el
# builder distribuido de Railway reparte las etapas entre nodos, y el que ejecuta esta última puede
# no tener el snapshot del contexto — falla con "lstat /docker-entrypoint.sh: no such file or
# directory" con un Dockerfile que compila perfecto en local. La etapa `build` ya lo trajo con
# `COPY . .`, así que copiarlo de allí quita la última dependencia del contexto en esta etapa.
COPY --from=build --chmod=755 --chown=orion:orion /app/docker-entrypoint.sh ./docker-entrypoint.sh
USER orion
EXPOSE 3000
# El entrypoint inyecta el API_URL real en el manifest y luego arranca `node server.js`.
ENTRYPOINT ["./docker-entrypoint.sh"]
