# Ambiente de pruebas (UAT): cómo montarlo en Railway

Pedido de Pardo del 29/09/2026 (`docs/pedidos/2026-09-29-1840-…`): una réplica de Orión para probar
funciones nuevas y crear usuarios de prueba, sin tocar a los profes y estudiantes de verdad.

## Cómo queda

| | Producción | Pruebas (UAT) |
|---|---|---|
| Dirección | orionidiomas.com | uat.orionidiomas.com |
| Rama de git | `master` | `develop` |
| Base de datos | la de siempre | una propia, que nace vacía |
| Pagos | Wompi de verdad | Wompi **sandbox**: nadie paga nada |
| Correos | normales | con «[Pruebas]» en el asunto |
| Pantalla | normal | una franja arriba que dice «Ambiente de pruebas» |
| Google | indexa | no indexa nada |

**La forma de trabajar:** Claude sube primero a `develop`, que se despliega en UAT. Pardo lo prueba
ahí, y cuando está bien se pasa a `master`, que es producción. Lo urgente puede ir directo a
producción si Pardo lo pide.

## Lo que ya está en el código

- `ORION_ENVIRONMENT=uat` en el backend: los correos salen con «[Pruebas]» en el asunto.
- `NEXT_PUBLIC_ENVIRONMENT=uat` en el frontend:
  - una franja fija arriba, «Ambiente de pruebas: nada de lo que pase aquí es real»;
  - `robots.txt` que no deja indexar nada;
  - todas las páginas con `noindex`.
- Todo lo demás es igual a producción: el mismo código y el mismo perfil `prod`.

## Paso a paso en Railway (una vez, ~1 hora)

### 1. El ambiente

1. En el proyecto de Orión en Railway, arriba a la izquierda, donde dice **production**: «New
   Environment» → **Duplicate environment** a partir de *production*, con el nombre `uat`. Railway
   copia los servicios (backend, frontend y Postgres) y sus variables; la base nueva nace vacía.
2. En `uat`, en cada servicio (backend y frontend) → Settings → Source → **Branch: `develop`**.
3. Claude crea la rama `develop` en GitHub (a partir de `master`) antes de este paso.

### 2. Las variables del backend en `uat`

Cambia estas; las demás se quedan como vienen de producción:

| Variable | Valor en UAT |
|---|---|
| `ORION_ENVIRONMENT` | `uat` |
| `ORION_APP_BASE_URL` | `https://uat.orionidiomas.com` |
| `ORION_CORS_ALLOWED_ORIGINS` | `https://uat.orionidiomas.com` |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | los del Postgres de **uat** (si usan referencias `${{Postgres.…}}`, ya apuntan solas) |
| `WOMPI_PUBLIC_KEY`, `WOMPI_INTEGRITY_SECRET`, `WOMPI_EVENTS_SECRET` | las llaves de **sandbox** de Wompi (`pub_test_…`, etc.) |
| `WOMPI_API_BASE_URL` | `https://sandbox.wompi.co/v1`, o bórrala: por defecto ya es el sandbox |
| `ORION_ADMIN_EMAIL`, `ORION_ADMIN_PASSWORD` | el admin de pruebas; se crea solo en el primer arranque |
| `ORION_ALERTS_ENABLED` | `false`, para que las alertas de UAT no te lleguen como si fueran de producción |
| `JAAS_WEBHOOK_SECRET` | el del webhook de UAT (paso 4), o bórrala si no lo configuras |

`JAAS_*`, `CLOUDINARY_URL`, `RESEND_API_KEY`, `OPENAI_API_KEY`, `ORION_VOICE_PROVIDER` y las claves
VAPID pueden quedar iguales: las pruebas usan las mismas cuentas. OpenAI gasta del mismo tope mensual.

### 3. Las variables del frontend en `uat`

| Variable | Valor en UAT |
|---|---|
| `NEXT_PUBLIC_ENVIRONMENT` | `uat`. Se lee al compilar (el Dockerfile la declara como `ARG`); después de ponerla hay que volver a desplegar. |
| `API_URL` | igual que en producción (`http://backend.railway.internal:8080`): cada ambiente tiene su propia red interna |

### 4. Dominio y servicios externos

1. **Dominio**: en el frontend de `uat` → Settings → Networking → Custom Domain:
   `uat.orionidiomas.com`. Railway da un CNAME; ponlo en el DNS de orionidiomas.com.
2. **Wompi sandbox**: en el panel de Wompi, modo de pruebas, pon la URL de eventos
   `https://uat.orionidiomas.com/api/v1/webhooks/payments/wompi`.
3. **JaaS (opcional)**: otro endpoint de webhook, `https://uat.orionidiomas.com/api/v1/webhooks/video/jaas`,
   con los mismos tres eventos, y su secreto en `JAAS_WEBHOOK_SECRET` de uat.
4. **Entrar con Google (opcional)**: en Google Cloud → Credenciales, agrega
   `https://uat.orionidiomas.com` a los orígenes autorizados y su URL de retorno.

### 5. Comprobar

1. Abre `uat.orionidiomas.com`: debe verse la franja de «Ambiente de pruebas».
2. Entra con el admin de pruebas y ve a **Sistema**. Pagos debe decir «Apuntando al SANDBOX». En
   producción eso sería una alarma; aquí es lo correcto.
3. «Correo de prueba» en Sistema: el asunto llega con «[Pruebas]».
4. Crea usuarios de prueba desde Usuarios, o regístrate con alias de Gmail (`tu+algo@gmail.com`).
5. Una reserva pagada con las tarjetas de prueba de Wompi sandbox.

## Lo que no hace (a propósito)

- **No copia los datos de producción**: tendría datos personales reales en un ambiente con más manos.
  UAT nace vacío y se llena con datos de prueba.
- **No tiene clave de acceso**: cualquiera con la dirección puede abrirla, aunque Google no la indexa
  y ahí no hay datos reales ni dinero. Si hace falta cerrarla, se le pone un usuario y contraseña
  delante; hay que hacerlo con cuidado para no romper la subida de hojas de vida.
