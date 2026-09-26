"use client";

import {
  ArrowRight,
  BadgeCheck,
  BookOpen,
  CalendarDays,
  Eye,
  EyeOff,
  GraduationCap,
  Lock,
  Mail,
  Mic,
  NotebookPen,
  Sparkles,
  User,
  Wallet,
  type LucideIcon,
} from "lucide-react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { Suspense, useEffect, useState, type FormEvent } from "react";
import { AvisoError } from "@/components/estados";
import { Constelacion, Wordmark } from "@/components/marca";
import { AyudaWhatsapp, PhoneInput } from "@/components/PhoneInput";
import { Rigel, type RigelPose } from "@/components/Rigel";
import { BotonPrincipal, Campo, Segmento, Spinner } from "@/components/ui";
import { ApiError, apiFetch } from "@/lib/api/fetch";
import { destinoAlEntrar } from "@/lib/auth/roles";
import { destinoSeguro, entrarYVolver } from "@/lib/auth/volver";
import { useRegister } from "@/lib/auth/session";
import { minutos, useCifras } from "@/lib/cifras";
import { fuerzaClave } from "@/lib/password";
import { whatsappValido } from "@/lib/phone";
import { Consentimiento } from "@/components/Consentimiento";
import { BotonesSociales } from "@/components/BotonesSociales";

/**
 * Con qué intención llega la persona. NO es un rol: la cuenta que crea el backend es la misma en
 * los dos casos —un profesor de Orión lo es cuando su postulación se aprueba, no cuando se
 * registra—. Lo que cambia es el copy y a dónde aterriza después.
 */
type Intencion = "aprender" | "ensenar";

const COPY: Record<Intencion, {
  heroTitulo: string;
  heroTexto: string;
  pose: RigelPose;
  subtitulo: string;
  whatsapp: string;
  boton: string;
}> = {
  aprender: {
    heroTitulo: "Da el primer paso hoy.",
    heroTexto: "Soy Rigel. Te acompaño desde tu primera clase hasta que hables sin pensarlo.",
    pose: "saludo",
    subtitulo: "Crea tu cuenta y reserva tu primera clase.",
    whatsapp: "Lo usamos para avisarte de tus clases. Con tu profesor hablas dentro de Orión.",
    boton: "Crear cuenta",
  },
  ensenar: {
    heroTitulo: "Enseña donde te escuchan.",
    heroTexto: "Soy Rigel. Te acompaño desde tu postulación hasta tu primera clase dictada.",
    // Rigel se pone el birrete cuando quien llega viene a enseñar: la misma pantalla, pero
    // recibiendo a otra persona.
    pose: "profe",
    subtitulo: "Primero tu cuenta; enseguida sigues con tu postulación.",
    whatsapp: "Para que el equipo de Orión te ubique mientras revisa tu postulación.",
    boton: "Crear cuenta y postularme",
  },
};

export default function RegistroPage() {
  // useSearchParams exige una frontera de Suspense.
  return (
    <Suspense fallback={null}>
      <Registro />
    </Suspense>
  );
}

function Registro() {
  const router = useRouter();
  const registro = useRegister();

  // ?rol=profesor llega desde la portada, el login y "Enseña en Orión". Es solo el valor inicial:
  // quien caiga aquí por error cambia de pestaña sin tener que volver atrás.
  const params = useSearchParams();
  const rolInicial = params.get("rol");
  // Quien venía de reservar en un perfil sin cuenta vuelve a ese perfil (solo si viene a aprender).
  const volver = destinoSeguro(params.get("volver"));
  // Quien llega desde la pantalla de invitación (V71): la cuenta es de profesor y el correo es el de
  // la invitación, puesto y sin poder cambiarlo. El token se consume al crear la cuenta.
  const tokenDeInvitacion = params.get("invitacion");
  const invitacion = useQuery({
    queryKey: ["invitacion", tokenDeInvitacion],
    queryFn: () =>
      apiFetch<{ state: string; email: string | null; founder: { rateBps: number; periodMonths: number; baseRateBps: number } | null }>(
        `/api/v1/auth/invite?token=${encodeURIComponent(tokenDeInvitacion ?? "")}`,
        { redirectOn401: false },
      ),
    enabled: !!tokenDeInvitacion,
    retry: false,
  });
  const invitado = invitacion.data?.state === "VALID" ? invitacion.data : null;
  const invitacionCaida = !!tokenDeInvitacion && !!invitacion.data && !invitado;
  // Mientras llega o si es válida, la cuenta es de profesor. Si venció o ya se usó, el registro vuelve
  // a ser el de siempre: quien quería aprender tiene que poder elegirlo.
  const conInvitacion = !!tokenDeInvitacion && !invitacionCaida;
  const [intencion, setIntencion] = useState<Intencion>(
    rolInicial === "profesor" || tokenDeInvitacion ? "ensenar" : "aprender",
  );
  const copy = COPY[intencion];
  const cifras = useCifras();
  // Lo que se gana, en tres líneas: lo mismo que promete la portada, con las cifras de Ajustes.
  const rasgos: { icono: LucideIcon; texto: string }[] =
    intencion === "aprender"
      ? [
          { icono: Sparkles, texto: `Diagnóstico gratis de ${minutos(cifras.assessmentMinutes)}: sabes cómo arrancas.` },
          { icono: BadgeCheck, texto: "Profesores verificados. Pagas clase por clase, sin suscripción." },
          { icono: NotebookPen, texto: "Después de cada clase, un resumen y práctica hecha para ti." },
        ]
      : [
          {
            icono: Wallet,
            texto: invitado?.founder
              ? `Tú pones tu tarifa. Como profe fundador, Orión retiene el ${invitado.founder.rateBps / 100} % tus primeros ${invitado.founder.periodMonths} meses de clases; después, el ${invitado.founder.baseRateBps / 100} %.`
              : `Tú pones tu tarifa, y Orión retiene el ${cifras.commissionPercent} %: lo ves desde el día uno.`,
          },
          { icono: CalendarDays, texto: "Tus horarios, sin mínimos ni permanencia." },
          { icono: Mic, texto: "Un minuto de audio al terminar y el seguimiento de tu estudiante queda listo." },
        ];

  const [nombre, setNombre] = useState("");
  const [email, setEmail] = useState("");
  // El correo de la invitación llega después del primer render: se pone en cuanto llega.
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- el correo de la invitación, una vez
    if (invitado?.email) setEmail(invitado.email);
  }, [invitado?.email]);
  const [password, setPassword] = useState("");
  const [whatsapp, setWhatsapp] = useState("");
  const [verClave, setVerClave] = useState(false);

  // Tres casillas y no una. La autorización de tratamiento de datos tiene que ser específica
  // (Decreto 1377 de 2013): empaquetarla junto a los términos la viciaría. Y la mayoría de edad
  // es una declaración aparte, porque Orión no acepta menores.
  const [mayorDeEdad, setMayorDeEdad] = useState(false);
  const [aceptaTerminos, setAceptaTerminos] = useState(false);
  const [aceptaDatos, setAceptaDatos] = useState(false);

  const fuerza = fuerzaClave(password);
  const listo =
    nombre.trim().length > 0 &&
    /.+@.+\..+/.test(email) &&
    password.length >= 8 &&
    whatsappValido(whatsapp) &&
    mayorDeEdad &&
    aceptaTerminos &&
    aceptaDatos;

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (!listo) return;
    registro.mutate(
      {
        fullName: nombre.trim(),
        email: email.trim(),
        password,
        whatsappPhone: whatsapp,
        // La intención viaja al backend y no se queda en esta pantalla: es lo que decide que la
        // cuenta nazca como aspirante y no como estudiante que además postuló.
        wantsToTeach: intencion === "ensenar",
        adult: mayorDeEdad,
        acceptsTerms: aceptaTerminos,
        acceptsDataPolicy: aceptaDatos,
        inviteToken: invitado ? (tokenDeInvitacion ?? undefined) : undefined,
      },
      {
        // Quien viene a enseñar entra directo a su postulación; quien viene a aprender, al
        // buscador. Sin esto un profesor recién registrado aterrizaría en el marketplace de
        // estudiantes a buscarse a sí mismo.
        onSuccess: (me) =>
          router.replace(intencion === "ensenar" ? "/aplicacion" : (volver ?? destinoAlEntrar(me.role))),
      },
    );
  }

  const error = registro.error instanceof ApiError ? registro.error.message : null;

  return (
    <main className="flex min-h-dvh flex-col lg:flex-row">
      {/* Marca: hero del amanecer. Rigel arriba a la derecha se presenta; titular y subtítulo
          despejados abajo a la izquierda, sin compartir columna con el personaje.

          En escritorio el panel mide lo que la ventana y se queda fijo mientras el formulario
          corre. Antes medía lo que el formulario, así que el titular caía debajo del pliegue y
          había que bajar para leerlo; y el hueco del medio lo ocupan ahora tres razones. */}
      <div className="gradient-dawn relative flex h-[300px] flex-col justify-end overflow-hidden rounded-b-[24px] p-7 lg:sticky lg:top-5 lg:order-2 lg:m-5 lg:h-[calc(100dvh-2.5rem)] lg:min-h-[600px] lg:w-[47%] lg:self-start lg:rounded-[22px] lg:p-10">
        <Constelacion className="pointer-events-none absolute left-2 top-8 h-[120px] w-[120px] opacity-[0.45] lg:h-[200px] lg:w-[200px]" />
        <Wordmark className="absolute left-7 top-7 text-[15px] text-on-primary lg:left-10 lg:top-10" />
        <Rigel
          pose={copy.pose}
          decorativo
          className="pointer-events-none absolute right-2 top-8 h-[150px] w-auto lg:right-8 lg:top-10 lg:h-[236px]"
        />
        <div className="relative">
          <h1 className="max-w-[14ch] font-display text-[28px] font-bold leading-[1.15] text-on-primary lg:text-[40px]">
            {copy.heroTitulo}
          </h1>
          <p className="mt-2 max-w-[34ch] text-[13px] leading-relaxed text-on-primary/85 lg:text-[15px]">
            {copy.heroTexto}
          </p>
          {/* Solo en escritorio: en el teléfono el encabezado se queda en sus 300 px. */}
          <ul className="mt-6 hidden max-w-[40ch] gap-3 border-t border-on-primary/25 pt-6 lg:grid">
            {rasgos.map(({ icono: Icono, texto }) => (
              <li key={texto} className="flex items-start gap-3 text-[14.5px] leading-snug text-on-primary">
                <span className="grid h-8 w-8 shrink-0 place-items-center rounded-full bg-on-primary/15">
                  <Icono size={16} strokeWidth={2} />
                </span>
                <span className="pt-1.5">{texto}</span>
              </li>
            ))}
          </ul>
        </div>
      </div>

      {/* Formulario de registro. */}
      <div className="flex flex-1 items-start justify-center px-7 py-8 lg:order-1 lg:items-center lg:px-10">
        <form onSubmit={onSubmit} className="w-full max-w-md lg:max-w-[400px]">
          <h2 className="font-display text-[26px] font-bold lg:text-[34px]">Crea tu cuenta</h2>
          <p className="mt-1 text-[14px] text-text-secondary">{copy.subtitulo}</p>

          {invitacionCaida && (
            <div className="mt-4">
              <AvisoError mensaje="Esta invitación ya venció o ya se usó. Escríbele a quien te invitó y te enviamos un enlace nuevo." />
            </div>
          )}

          {/* Con invitación no hay nada que elegir: la cuenta es de profesor. */}
          <div className={`mt-5 ${conInvitacion ? "hidden" : ""}`}>
            <Segmento<Intencion>
              valor={intencion}
              onCambio={setIntencion}
              opciones={[
                {
                  valor: "aprender",
                  etiqueta: (<><BookOpen size={15} strokeWidth={1.75} /> Quiero aprender</>),
                },
                {
                  valor: "ensenar",
                  etiqueta: (<><GraduationCap size={15} strokeWidth={1.75} /> Quiero enseñar</>),
                },
              ]}
            />
          </div>

          {intencion === "ensenar" && (
            <p className="mt-3 rounded-base bg-accent-lavender-soft px-4 py-3 text-[12.5px] leading-relaxed text-[#5e4a8a]">
              Creamos tu cuenta y sigues con tu postulación: idiomas que enseñas, experiencia,
              tarifa y documentos. Tu perfil aparece en el marketplace cuando la aprobamos.
            </p>
          )}

          {/* En las dos pestañas: desde «Quiero enseñar», la intención viaja con la ida al
              proveedor y la cuenta nace como aspirante a profesor, como con contraseña. */}
          {/* Con invitación, solo correo y contraseña: una cuenta de Google podría traer otro correo. */}
          {!conInvitacion && (
            <div className="mt-5">
              <BotonesSociales ensenar={intencion === "ensenar"} />
            </div>
          )}

          <label
            className="mt-6 block text-[12px] font-bold uppercase tracking-[0.04em] text-text-secondary"
            htmlFor="nombre"
          >
            Nombre completo
          </label>
          <Campo
            id="nombre"
            type="text"
            required
            autoComplete="name"
            maxLength={150}
            placeholder="María Gómez"
            icono={<User size={18} strokeWidth={1.75} />}
            value={nombre}
            onChange={(event) => setNombre(event.target.value)}
            className="mt-1.5"
          />

          <label
            className="mt-4 block text-[12px] font-bold uppercase tracking-[0.04em] text-text-secondary"
            htmlFor="email"
          >
            Correo
          </label>
          <Campo
            id="email"
            type="email"
            required
            autoComplete="email"
            placeholder="tu@correo.com"
            icono={<Mail size={18} strokeWidth={1.75} />}
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            readOnly={!!invitado}
            aria-readonly={!!invitado}
            className={`mt-1.5 ${error ? "border-error" : ""} ${invitado ? "bg-surface-sunken text-text-secondary" : ""}`}
          />
          {invitado && (
            <p className="mt-1.5 text-[12px] text-text-muted">Es el correo de tu invitación: con él entras a Orión.</p>
          )}

          <label
            className="mt-4 block text-[12px] font-bold uppercase tracking-[0.04em] text-text-secondary"
            htmlFor="password"
          >
            Contraseña
          </label>
          <div className="relative">
            <Campo
              id="password"
              type={verClave ? "text" : "password"}
              required
              autoComplete="new-password"
              placeholder="Mínimo 8 caracteres"
              icono={<Lock size={18} strokeWidth={1.75} />}
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              className="mt-1.5 pr-12"
            />
            <button
              type="button"
              aria-label={verClave ? "Ocultar contraseña" : "Mostrar contraseña"}
              onClick={() => setVerClave((valor) => !valor)}
              className="absolute right-3 top-1/2 mt-[3px] grid h-9 w-9 -translate-y-1/2 place-items-center rounded-full text-text-muted transition-colors hover:text-text focus-visible:shadow-focus"
            >
              {verClave ? <EyeOff size={18} strokeWidth={1.75} /> : <Eye size={18} strokeWidth={1.75} />}
            </button>
          </div>

          {/* Medidor de fuerza: 4 segmentos que se encienden en menta; mensaje que dice qué falta. */}
          <div className="mt-2.5" aria-hidden="true">
            <div className="flex gap-1.5">
              {[0, 1, 2, 3].map((i) => (
                <span
                  key={i}
                  className={`h-[5px] flex-1 rounded-pill transition-colors ${
                    i < fuerza.nivel ? "bg-success" : "bg-border"
                  }`}
                />
              ))}
            </div>
            <p
              className={`mt-1.5 text-[12px] ${
                fuerza.nivel >= 3
                  ? "text-success"
                  : password
                    ? "text-text-secondary"
                    : "text-text-muted"
              }`}
            >
              {fuerza.mensaje}
            </p>
          </div>

          <label
            className="mt-4 block text-[12px] font-bold uppercase tracking-[0.04em] text-text-secondary"
            htmlFor="whatsapp"
          >
            WhatsApp
          </label>
          {/* Obligatorio (Pardo, 25/09/2026). Mientras el número esté a medias, se dice qué falta:
              si no, el botón sigue apagado sin que nadie sepa por qué. */}
          <PhoneInput id="whatsapp" value={whatsapp} onChange={setWhatsapp} className="mt-1.5" />
          <AyudaWhatsapp numero={whatsapp} ayuda={copy.whatsapp} />

          <div className="mt-6 grid gap-3 rounded-base border border-border bg-surface-sunken p-4">
            <Consentimiento
              id="mayor-de-edad"
              marcado={mayorDeEdad}
              onCambio={setMayorDeEdad}
            >
              Declaro que soy <strong>mayor de 18 años</strong>.
            </Consentimiento>
            <Consentimiento
              id="acepta-terminos"
              marcado={aceptaTerminos}
              onCambio={setAceptaTerminos}
            >
              Acepto los{" "}
              <Link href="/terminos" target="_blank" className="font-bold text-primary-strong hover:underline">
                Términos y condiciones
              </Link>
              .
            </Consentimiento>
            <Consentimiento id="acepta-datos" marcado={aceptaDatos} onCambio={setAceptaDatos}>
              Autorizo el tratamiento de mis datos personales conforme a la{" "}
              <Link href="/privacidad" target="_blank" className="font-bold text-primary-strong hover:underline">
                Política de tratamiento
              </Link>
              .
            </Consentimiento>
          </div>

          {error && (
            <div className="mt-4">
              <AvisoError mensaje={error} />
            </div>
          )}

          <BotonPrincipal type="submit" disabled={!listo || registro.isPending} className="mt-6">
            {registro.isPending ? (
              <>
                <Spinner />
                Creando…
              </>
            ) : (
              <>
                {copy.boton}
                <ArrowRight size={18} strokeWidth={1.75} />
              </>
            )}
          </BotonPrincipal>

          <p className="mt-6 text-center text-[13px] text-text-secondary">
            ¿Ya tienes cuenta?{" "}
            <Link href={volver ? entrarYVolver("/login", volver) : "/login"} className="font-bold text-primary-strong hover:underline">
              Entra
            </Link>
          </p>
        </form>
      </div>
    </main>
  );
}
