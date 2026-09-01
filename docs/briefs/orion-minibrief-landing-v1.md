# Mini-brief · Landing v1 en la raíz de orionidiomas.com

**Prerequisito:** rediseño Design v2 en producción. Rigen `CLAUDE.md`, los tokens de `docs/design-v2/` y la guía `MASCOTA.md`. Dos pasos, DETENTE en cada uno.
**Objetivo:** que `orionidiomas.com/` deje de ser una pantalla de login y se convierta en la página de marketing que Sofía puede poner en su bio de Instagram/TikTok hoy mismo — el primer eslabón del funnel.

## Decisiones — LEER ANTES

1. **Vive en `/` del mismo proyecto Next.** Sin subdominios ni cambios de DNS en esta versión (el split `app.orionidiomas.com` queda para después si algún día hace falta). Las rutas de la app no se mueven.
2. **Excepción consciente de arquitectura:** la landing es pública y el SEO importa → se renderiza en **servidor/estático**, a diferencia de las pantallas autenticadas (la regla de client components del brief de la Tarea 4 aplica a pantallas con sesión, no a marketing). Documentar el porqué en el código.
3. **Usuario con sesión que visita `/`:** ve la landing con el CTA principal cambiado a "Ir a mi panel". Sin redirects forzados.
4. **Sistema design-v2 obligatorio** (tokens, componentes, tipografías). Mascota: **apruebo un Protagonista en el hero** — regístralo en la tabla viva de `MASCOTA.md`; cualquier otra aparición, por la rúbrica de esa guía.
5. **Copy real, cero lorem ipsum.** Fuente: el manual corporativo de Sofía (historia, misión, Método ORION®, voz de las secciones 36–37). El copy final lo aprueban Pardo y Sofía en el DETENTE del Paso 0.
6. **Prohibido inventar testimonios.** La sección queda maquetada pero **oculta** hasta tener citas reales y autorizadas (pedírselas a Sofía de sus estudiantes de WhatsApp). Publicar testimonios ficticios daña la marca y la confianza — no es negociable.
7. **Contacto por WhatsApp:** botón con número desde configuración (`NEXT_PUBLIC_SUPPORT_WHATSAPP`). Si la variable no existe, el botón no se renderiza — pendiente de que Pardo confirme el número.

## Estructura de la página (una sola, en este orden)

1. **Hero:** eslogan institucional *"Learn with confidence. Transform your opportunities."* + subtítulo en español + CTA primario "Crea tu cuenta" → `/registro` + secundario "Ya tengo cuenta" → login. Protagonista de la mascota.
2. **Qué es Orión** — 2–3 frases desde la historia del manual.
3. **El Método ORION®** — las cinco letras (Observe · Relate · Interact · Optimize · Navigate) como piezas visuales.
4. **Cómo funciona** — 3 pasos: crea tu cuenta → elige tu profesor → reserva tu clase.
5. **Confianza comunicativa** — el diferenciador: para adultos que saben más inglés del que se atreven a hablar (teaser honesto del Confidence Score®, sin prometer features que aún no existen).
6. **Testimonios** — maquetada, oculta (decisión 6).
7. **CTA final + footer:** contacto, enlaces de login/registro, espacio reservado para legal (términos/privacidad llegan con el MVP 2).

## Paso 0 — Estructura, secciones y copy

Página completa server-rendered con imágenes optimizadas y responsive desde el primer commit (390 → 1280, el estándar de la casa).

**Verificación:** revisión en navegador en ambos anchos + **revisión de copy con Sofía** (mandarle captura o URL de preview). **DETENTE.**

## Paso 1 — SEO y pulido

Metadata (título, descripción), Open Graph con imagen propia (puede llevar la mascota), `sitemap.xml` y `robots.txt` básicos, verificación de la matriz responsive completa, y **Lighthouse ≥ 95 en Performance, SEO y Accesibilidad** — es una página estática: el listón sube.

**Verificación:** puntajes + compartir el link en un chat de WhatsApp y comprobar que la preview (OG) se ve bien. **DETENTE. Fin.**

## Definition of done

- [ ] `orionidiomas.com/` muestra la landing; la app intacta en sus rutas; sesión activa ve "Ir a mi panel"
- [ ] Copy aprobado por Sofía; testimonios ocultos hasta tener reales
- [ ] Registro alcanzable en un clic desde el hero
- [ ] Lighthouse ≥ 95 ×3; OG preview correcta en WhatsApp
- [ ] Mascota registrada en la tabla viva de `MASCOTA.md`; `docs/ESTADO.md` actualizado

## Fuera de alcance

Blog, prueba de nivel pública, captura de leads/CRM, subdominio `app.`, páginas múltiples, testimonios ficticios, animaciones pesadas. Si algo parece faltar, pregunta antes de agregarlo.
