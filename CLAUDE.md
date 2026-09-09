# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Orión — marketplace de clases particulares de idiomas para una academia colombiana. El estudiante
busca profesor, reserva una hora de su disponibilidad y **paga por la pasarela** (Wompi); la clase
se da por videollamada; Orión retiene comisión y liquida al profesor cuando la clase ya se dictó.
Volumen esperado: decenas de usuarios.

El MVP 1 —agendar y hablar por WhatsApp, sin dinero— quedó atrás hace varios bloques. Hoy hay
pagos, saldo a favor, reclamos, reputación, gamificación, textos legales y soporte con plazos de
ley. **Qué hay construido está en `docs/ESTADO.md`**; empieza por ahí.

El trabajo se dirige por briefs en `docs/briefs/`. **Léelos antes de tocar código**: definen
el alcance cerrado de cada tarea, y construir features que no están en el brief es una
violación explícita de las instrucciones. La comunicación con Pardo es en español; el código
y los identificadores, en inglés.

## Commands

Infraestructura (Postgres + Mailpit):

```bash
docker compose up -d          # postgres:5432, mailpit web:8025 / smtp:1025
docker compose down -v        # borra también el volumen (esquema desde cero)
```

Backend (desde `backend/`):

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
./mvnw verify                          # unitarios (*Test) + integración (*IT)
./mvnw test                            # solo unitarios
./mvnw verify -Dit.test=AuthFlowIT     # un solo test de integración
```

Los tests usan Testcontainers con Postgres real: Docker debe estar corriendo, pero la infra
de `docker compose` no necesita estar arriba.

## Architecture

**Monolito modular** bajo `co.orion`, trece módulos: `identity`, `scheduling`, `catalog`,
`billing`, `lifecycle`, `reputation`, `messaging`, `notifications`, `engagement`, `legal`,
`support`, `admin` y `shared`. Dentro de cada módulo: `api/` (controllers y DTOs) ·
`application/` (servicios) · `domain/` (entidades) · `persistence/` (repositorios).

`scheduling` puede depender de `identity` (por ejemplo para comprobar que un profesor está
publicado), nunca al revés. La dependencia que sorprende es **`identity → reputation`** (el perfil
público muestra la calificación), y por eso existe **`lifecycle`**: es el único sitio que necesita
reserva, pago e historial a la vez. `engagement` es el contrario —depende de casi todos y nadie
depende de él—, así que se puede borrar entero sin tocar el marketplace.

Entre módulos no hay relaciones JPA: se guarda el `UUID` plano y la integridad la garantiza la FK
de la base, no el grafo de objetos.

**`notifications` se acopla por eventos, no por llamadas.** `BookingService` publica
`BookingCreatedEvent` / `BookingCancelledEvent` y no sabe que existen los correos. El listener
usa `@TransactionalEventListener(AFTER_COMMIT)`: así nunca se notifica una reserva que hizo
rollback, y un fallo de correo (try/catch + log) nunca tumba una reserva.

**Las invariantes duras viven en la base, no en el código.** Doble reserva: índice único parcial
`(professor_id, starts_at) WHERE status='CONFIRMED'`. Doble asistencia: `UNIQUE` sobre
`booking_id`. Un pago por reserva: `payments.booking_id UNIQUE` —por eso una misma clase no puede
tener dos cobros, y tres líneas en el historial son tres reservas, no tres pagos—. Un webhook
reenviado se procesa una vez. El servicio hace un chequeo amable con buen mensaje (422), pero el
árbitro final —el que cierra la ventana entre el chequeo y el INSERT— es la constraint, y su
violación se traduce a 409. Nada de locks pesimistas.

**Las reglas de negocio con número viven en `platform_settings`, no en el código.** Comisión,
ventanas de cancelación, plazos de reclamo, umbrales del ranking: cambiarlas es un `UPDATE` (o la
pantalla de Ajustes del admin), nunca un despliegue. Se leen en cada evaluación y no se cachean.
Las que fija una ley —5 días hábiles de retracto, 15 calendario de devolución, los plazos de habeas
data— **no** son ajustes: están en el código porque cambiarlas sería incumplir.

**Cancelar siempre se puede.** La ventana (hoy 12 h, ambos lados) no bloquea: decide qué pasa con
el dinero, y eso lo resuelve `billing` al recibir el evento. Bloquear era peor para los dos lados —
al estudiante que ya sabía que no iba se le obligaba a dejar la clase en pie, y el profesor se
enteraba delante de una sala vacía.

**Dos puertas que están cerradas a propósito.** Orión es **solo para mayores de 18** (art. 7 de la
Ley 1581: tratar datos de menores exige autorización del representante legal, y ese flujo no
existe) y **todas las clases son virtuales** (el CHECK de `bookings.modality` solo admite `VIRTUAL`
desde la V30). Las dos se han pedido «abrir» alguna vez; las dos exigen una decisión de producto y
—la primera— un abogado, no un parche.

**El cálculo de cupos vive en `SlotCalculator`, una clase pura** (sin Spring, sin repositorios,
sin reloj del sistema: el "ahora" entra por parámetro). Sus 12 tests corren en ~150 ms porque no
levantan nada. No mover esa lógica a SQL ni inyectarle dependencias — es lo que la hace
exhaustivamente testeable. Reglas del dominio: clases de 60 min alineadas a la hora, intervalos
semiabiertos `[inicio, fin)`, todo razonado en `BusinessZone.BOGOTA`, y nunca cupos ya iniciados.

**Flyway es el dueño del esquema.** `spring.jpa.hibernate.ddl-auto=validate`, siempre.
Hibernate nunca crea ni altera tablas; solo valida que las entidades coincidan con lo que
migró Flyway — si divergen, la aplicación no arranca. Todo cambio de esquema es una migración
nueva en `backend/src/main/resources/db/migration`. Este principio es permanente.

**Convenciones de datos:** IDs `UUID` generados en la base (`gen_random_uuid()`, mapeados con
`@Generated(event = INSERT)`, no con `@GeneratedValue`). Tiempos en `TIMESTAMPTZ`, almacenados
en UTC; la zona de negocio es `America/Bogota`. Roles y estados son `VARCHAR + CHECK`, no
enums nativos de Postgres (evolucionarlos es una migración trivial en vez de un `ALTER TYPE`).
El email se normaliza a minúsculas en el constructor de `User`, no en la base.

**El `Clock` de `shared/config` es la única fuente de la hora.** Nunca `Instant.now()` directo:
la auditoría JPA (`@CreatedDate`/`@LastModifiedDate`) lee de ese bean a través de un
`DateTimeProvider`, para que congelarlo en un test congele también las marcas de auditoría.
`ProfessorSlotsIT` lo sustituye por un `Clock.fixed` y así el endpoint de cupos es determinista.

**No configurar `hibernate.jdbc.time_zone`.** Desplaza también las columnas `TIME` (hora de pared,
sin zona) por el offset del servidor: una regla 18:00–21:00 se guardaría como 23:00–02:00. Los
`Instant` sobre `TIMESTAMPTZ` ya almacenan el instante absoluto sin ayuda.

**Autenticación por sesión, sin JWT.** Cookie `ORION_SESSION` httpOnly + CSRF con cookie
`XSRF-TOKEN` legible por JS y header `X-XSRF-TOKEN` en toda petición mutante (el login está
exento). Los errores siempre son JSON — nunca un redirect a una página de login. La API vive
bajo `/api/v1`.

## Conventions

- **Spring Boot 4.1** (Boot 3.5 llegó a EOL el 30/06/2026). Nunca degradar la versión de Boot
  para "resolver" un error de compilación: eso requiere aprobación explícita de Pardo.
- Boot 4 renombró artefactos y movió clases de paquete respecto a Boot 3 (`starter-webmvc`,
  `starter-flyway`, un módulo `-test` por starter, `org.testcontainers:testcontainers-*`,
  `@DataJpaTest` en `boot.data.jpa.test.autoconfigure`, `TestRestTemplate` en
  `boot.resttestclient` y con `@AutoConfigureTestRestTemplate` explícito). **El conocimiento
  entrenado sobre Spring está sesgado a Boot 3**: ante un error, compara contra lo que genera
  Spring Initializr para Boot 4.1 o busca la clase en los jars de `~/.m2` antes de teorizar.
- Sin Lombok. DTOs como `record` de Java 21. Inyección por constructor, siempre.
- Claridad sobre magia. Comentarios solo donde el "por qué" no sea obvio.
- Un commit por paso del brief, mensaje convencional en inglés
  (`feat(identity): session-based auth with role protection`).
- Antes de declarar terminado un paso: `./mvnw verify` en verde.
