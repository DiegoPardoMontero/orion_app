"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Check, Eye, EyeOff, Lock, Sparkles } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { CambiarFoto } from "@/components/CambiarFoto";
import { AvatarOrion } from "@/components/gamificacion/AvatarOrion";
import { ChipPuntos } from "@/components/Puntos";
import { EstrellaLogro } from "@/components/gamificacion/EstrellaLogro";
import { ApiError, apiFetch } from "@/lib/api/fetch";
import { misPuntosKey } from "@/lib/puntos";
import type { GoalResponse, LanguageResponse } from "@/lib/api/types";
import {
  estadoDe,
  NOMBRE_FAMILIA,
  numeralDe,
  NIVEL_ESTUDIANTE,
  type Engagement,
  type FichaEstudiante,
  type Logro,
} from "@/lib/gamificacion";
import { AvisoError } from "@/components/estados";
import { Rigel } from "@/components/Rigel";
import { Boton } from "@/components/ui";
import { BarraDeEdicion } from "@/components/BarraDeEdicion";

const NIVELES = ["BEGINNER", "INTERMEDIATE", "ADVANCED"] as const;

/**
 * «Mi ficha»: lo que el estudiante declara sobre sí mismo, más su avatar compuesto y las últimas
 * estrellas que encendió. Desde el 24/09 vive junto a sus datos de cuenta, separados por quién los
 * ve: arriba lo que ven sus profesores, abajo lo que es solo suyo (ver `cuenta/page.tsx`).
 *
 * <p>Todo es opcional a propósito. Una ficha a medias es válida —quien solo quiere reservar una
 * clase no tiene que rellenar un formulario para hacerlo—, y cada campo que sí se llena empuja el
 * logro «Perfil listo», que es la recompensa por hacerlo y no un requisito para usar la app.
 */
export function MiFicha() {
  const queryClient = useQueryClient();

  const ficha = useQuery({
    queryKey: ["me", "student-profile"],
    queryFn: () => apiFetch<FichaEstudiante>("/api/v1/me/student-profile"),
  });

  const resumen = useQuery({
    queryKey: ["me", "engagement"],
    queryFn: () => apiFetch<Engagement>("/api/v1/me/engagement"),
  });

  const logros = useQuery({
    queryKey: ["me", "achievements"],
    queryFn: () => apiFetch<Logro[]>("/api/v1/me/achievements"),
  });

  const idiomas = useQuery({
    queryKey: ["catalog", "languages"],
    queryFn: () => apiFetch<LanguageResponse[]>("/api/v1/catalog/languages"),
    staleTime: Infinity,
  });

  const objetivos = useQuery({
    queryKey: ["catalog", "goals"],
    queryFn: () => apiFetch<GoalResponse[]>("/api/v1/catalog/goals"),
    staleTime: Infinity,
  });

  if (!ficha.data) return null;

  return (
    <section className="mt-8">
      <h2 className="font-display text-[19px] font-bold">Mi ficha</h2>
      <p className="mt-1 text-[13.5px] text-text-secondary">
        Lo que cuentas de ti para que tus clases se parezcan a lo que buscas. Todo es opcional.
      </p>

      <div className="mt-4 flex flex-col items-center gap-4 rounded-card border border-border bg-surface-raised p-5 sm:flex-row sm:items-start sm:gap-6">
        <AvatarOrion
          nombre={ficha.data.fullName}
          fotoUrl={ficha.data.photoUrl}
          frameCode={ficha.data.frameCode}
          paletteCode={ficha.data.paletteCode}
          skyCode={ficha.data.skyCode}
          sealLevel={resumen.data?.sealLevel ?? 1}
          accesorios={ficha.data.accessories}
          size={116}
        />

        <div className="min-w-0 flex-1 text-center sm:text-left">
          <p className="flex flex-wrap items-center justify-center gap-2 font-display text-[17px] font-bold sm:justify-start">
            {ficha.data.fullName}
            {resumen.data && <ChipPuntos total={resumen.data.points} compacto />}
          </p>
          <p className="mt-0.5 text-[13px] text-text-secondary">
            {ficha.data.selfDeclaredLevel
              ? NIVEL_ESTUDIANTE[ficha.data.selfDeclaredLevel]
              : "Todavía no dices en qué nivel te sientes"}
          </p>
          <UltimasEstrellas logros={logros.data} />
          <div className="mt-3 flex flex-wrap justify-center gap-2 sm:justify-start">
            <Link
              href="/logros"
              className="inline-flex min-h-11 items-center gap-2 rounded-pill border-[1.5px] border-border px-4 text-[13.5px] font-bold text-text transition-colors hover:bg-surface-sunken focus-visible:shadow-focus"
            >
              <Sparkles size={15} strokeWidth={1.9} />
              Ver mi cielo
            </Link>
            <Link
              href="/logros/avatar"
              className="inline-flex min-h-11 items-center rounded-pill border-[1.5px] border-border px-4 text-[13.5px] font-bold text-text transition-colors hover:bg-surface-sunken focus-visible:shadow-focus"
            >
              Personalizar avatar
            </Link>
          </div>
        </div>
      </div>

      <QuienLoVe
        icono={<Eye size={15} strokeWidth={2} />}
        titulo="Lo que ven tus profesores"
        texto="Tu foto, tu nombre y esta ficha. La leen antes de cada clase para prepararla."
      />
      <div className="mt-3 rounded-card border border-border bg-surface-raised p-5">
        <CambiarFoto nombre={ficha.data.fullName} fotoUrl={ficha.data.photoUrl} />
      </div>

      <Formulario
        ficha={ficha.data}
        idiomas={idiomas.data ?? []}
        objetivos={objetivos.data ?? []}
        onGuardado={() => {
          void queryClient.invalidateQueries({ queryKey: ["me", "student-profile"] });
          void queryClient.invalidateQueries({ queryKey: ["me", "achievements"] });
          void queryClient.invalidateQueries({ queryKey: ["me", "engagement"] });
          void queryClient.invalidateQueries({ queryKey: misPuntosKey });
        }}
      />

      <QuienLoVe
        icono={<Sparkles size={15} strokeWidth={2} />}
        titulo="Quién más la ve"
        texto="Tú decides si otros estudiantes de Orión también pueden abrirla."
      />
      <Privacidad
        ficha={ficha.data}
        onCambio={() => {
          void ficha.refetch();
          // Hacerla visible da puntos la primera vez: que el chip lo diga sin recargar.
          void queryClient.invalidateQueries({ queryKey: misPuntosKey });
          void queryClient.invalidateQueries({ queryKey: ["me", "engagement"] });
        }}
      />
    </section>
  );
}

/**
 * Las tres últimas encendidas, con nombre. Iban solas, y tres estrellas de colores sin rótulo no
 * dicen nada (24/09/2026: «no entiendo qué significan, por qué tienen distintos colores»): cada una
 * es un logro, y el color es el de su familia, así que se dicen las dos cosas. Sin ninguna, se invita
 * en vez de dejar el hueco.
 */
function UltimasEstrellas({ logros }: { logros?: Logro[] }) {
  const recientes = (logros ?? [])
    .filter((l) => l.unlocked && l.unlockedAt)
    .sort((a, b) => (a.unlockedAt! < b.unlockedAt! ? 1 : -1))
    .slice(0, 3);

  if (recientes.length === 0) {
    return (
      <p className="mt-2 text-[13px] text-text-muted">
        Tu primera estrella se enciende con tu primera clase.
      </p>
    );
  }

  return (
    <div className="mt-3">
      <p className="text-[11.5px] font-bold uppercase tracking-[0.06em] text-text-secondary">
        Tus últimos logros
      </p>
      <ul className="mt-2 flex flex-wrap justify-center gap-2 sm:justify-start">
        {recientes.map((logro) => (
          <li key={logro.code}>
            <Link
              href="/logros"
              title={logro.description}
              className="flex items-center gap-2 rounded-pill bg-surface-sunken py-1 pl-1 pr-3.5 text-left transition-colors hover:bg-border/60 focus-visible:shadow-focus"
            >
              <EstrellaLogro
                familia={logro.family}
                brillo={logro.glow}
                estado={estadoDe(logro)}
                numeral={numeralDe(logro.code)}
                size={30}
              />
              <span className="leading-tight">
                <span className="block text-[12.5px] font-bold text-text">{logro.name}</span>
                <span className="block text-[11px] text-text-muted">{NOMBRE_FAMILIA[logro.family]}</span>
              </span>
            </Link>
          </li>
        ))}
      </ul>
      <p className="mt-1.5 text-[11.5px] text-text-muted">
        Cada logro enciende una estrella; su color es el de su familia. Todas están en «Mi cielo».
      </p>
    </div>
  );
}

function Formulario({
  ficha,
  idiomas,
  objetivos,
  onGuardado,
}: {
  ficha: FichaEstudiante;
  idiomas: LanguageResponse[];
  objetivos: GoalResponse[];
  onGuardado: () => void;
}) {
  const [nivel, setNivel] = useState(ficha.selfDeclaredLevel ?? "");
  const [idioma, setIdioma] = useState(ficha.primaryLanguage ?? "");
  const [motivacion, setMotivacion] = useState(ficha.motivation ?? "");
  const [metas, setMetas] = useState<string[]>(ficha.goalCodes);
  const [guardado, setGuardado] = useState(false);

  // Se mira, y solo después se edita. Mismo gesto que en el perfil del profesor.
  const [editando, setEditando] = useState(false);

  function descartar() {
    setNivel(ficha.selfDeclaredLevel ?? "");
    setIdioma(ficha.primaryLanguage ?? "");
    setMotivacion(ficha.motivation ?? "");
    setMetas(ficha.goalCodes);
    setEditando(false);
  }

  const guardar = useMutation({
    mutationFn: () =>
      apiFetch<FichaEstudiante>("/api/v1/me/student-profile", {
        method: "PUT",
        body: {
          selfDeclaredLevel: nivel || null,
          primaryLanguage: idioma || null,
          motivation: motivacion.trim() || null,
          goalCodes: metas,
        },
      }),
    onSuccess: () => {
      setGuardado(true);
      setEditando(false);
      onGuardado();
    },
  });

  const alternar = (code: string) =>
    setMetas((previas) =>
      previas.includes(code) ? previas.filter((c) => c !== code) : [...previas, code],
    );

  const error = guardar.error instanceof ApiError ? guardar.error.message : null;

  return (
    <div className="mt-3 rounded-card border border-border bg-surface-raised p-5">
      {/* Un fieldset alcanza a todo lo de dentro y no se olvida del control que se añada mañana. */}
      <fieldset disabled={!editando} className="contents">
      <fieldset>
        <legend className="text-[12px] font-bold uppercase tracking-[0.04em] text-text-secondary">
          ¿En qué nivel te sientes?
        </legend>
        {/* Autodeclarado: nadie examina a nadie. Es un punto de partida para el profesor. */}
        <div className="mt-2 flex flex-wrap gap-2">
          {NIVELES.map((codigo) => (
            <Opcion
              key={codigo}
              activa={nivel === codigo}
              onClick={() => setNivel(nivel === codigo ? "" : codigo)}
            >
              {NIVEL_ESTUDIANTE[codigo]}
            </Opcion>
          ))}
        </div>
      </fieldset>

      {idiomas.length > 0 && (
        <fieldset className="mt-5">
          <legend className="text-[12px] font-bold uppercase tracking-[0.04em] text-text-secondary">
            Idioma que estás aprendiendo
          </legend>
          <div className="mt-2 flex flex-wrap gap-2">
            {idiomas.map((lengua) => {
              const codigo = lengua.code ?? "";
              return (
                <Opcion
                  key={codigo}
                  activa={idioma === codigo}
                  onClick={() => setIdioma(idioma === codigo ? "" : codigo)}
                >
                  {lengua.nameEs}
                </Opcion>
              );
            })}
          </div>
        </fieldset>
      )}

      <fieldset className="mt-5">
        <legend className="text-[12px] font-bold uppercase tracking-[0.04em] text-text-secondary">
          ¿Para qué lo aprendes?
        </legend>
        {objetivos.length === 0 ? (
          <p className="mt-2 text-[13px] text-text-muted">Cargando objetivos…</p>
        ) : (
          <div className="mt-2 flex flex-wrap gap-2">
            {objetivos.map((meta) => {
              const codigo = meta.code ?? "";
              return (
                <Opcion key={codigo} activa={metas.includes(codigo)} onClick={() => alternar(codigo)}>
                  {meta.nameEs}
                </Opcion>
              );
            })}
          </div>
        )}
        {metas.length === 0 && objetivos.length > 0 && (
          <p className="mt-2 text-[12.5px] text-text-muted">
            Elige al menos uno y verás profesores que enseñan justo eso.
          </p>
        )}
      </fieldset>

      <label
        className="mt-5 block text-[12px] font-bold uppercase tracking-[0.04em] text-text-secondary"
        htmlFor="motivacion"
      >
        Tu motivación <span className="font-semibold normal-case text-text-muted">(opcional)</span>
      </label>
      <textarea
        id="motivacion"
        rows={3}
        maxLength={280}
        value={motivacion}
        onChange={(e) => setMotivacion(e.target.value)}
        placeholder="Quiero poder presentar una entrevista de trabajo en inglés."
        className="mt-1.5 w-full rounded-base border-[1.5px] border-border bg-surface px-4 py-3 text-[14px] leading-relaxed outline-none placeholder:text-text-muted focus-visible:border-primary focus-visible:shadow-focus"
      />
      <p className="mt-1 text-right text-[11.5px] tabular-nums text-text-muted">
        {motivacion.length}/280
      </p>

      {error && (
        <div className="mt-3">
          <AvisoError mensaje={error} />
        </div>
      )}

      </fieldset>

      {guardado && !guardar.isPending && (
        <p className="mt-3 flex items-center gap-2 rounded-card bg-success-bg px-4 py-3 text-[13px] font-semibold text-success">
          <Check size={16} strokeWidth={2.4} />
          Guardado. Tu profesor ya lo puede ver.
        </p>
      )}

      <BarraDeEdicion
        className="mt-4"
        editando={editando}
        guardando={guardar.isPending}
        etiquetaEditar="Editar mi ficha"
        onEditar={() => setEditando(true)}
        onCancelar={descartar}
        onGuardar={() => {
          setGuardado(false);
          guardar.mutate();
        }}
      />
    </div>
  );
}

/**
 * El interruptor del perfil público. La mayoría de edad ya se confirmó al crear la cuenta (V24): no
 * hace falta pedir una fecha para hacerla visible.
 */
function Privacidad({ ficha, onCambio }: { ficha: FichaEstudiante; onCambio: () => void }) {
  const publico = ficha.isPublic === true;

  const cambiar = useMutation({
    mutationFn: (destino: boolean) =>
      apiFetch<FichaEstudiante>("/api/v1/me/student-profile/visibility", {
        method: "PUT",
        body: { isPublic: destino },
      }),
    onSuccess: onCambio,
  });

  const error = cambiar.error instanceof ApiError ? cambiar.error.message : null;

  return (
    <div className="mt-3 rounded-card border border-border bg-surface-raised p-5">
      <div className="flex items-start gap-3">
        <span className="mt-0.5 text-text-muted">
          {publico ? <Eye size={18} strokeWidth={1.8} /> : <Lock size={18} strokeWidth={1.8} />}
        </span>
        <div className="min-w-0 flex-1">
          <p className="text-[14px] font-bold">
            {publico ? "Tu ficha es visible" : "Tu ficha es privada"}
          </p>
          {/* Ser exactos aquí importa: quien lee esto está decidiendo qué muestra de sí mismo. Los
              profesores con los que ya tuvo clase la ven de todos modos —la usan para preparar la
              clase—; lo que este interruptor decide es si otros estudiantes también. */}
          <p className="mt-1 text-[13px] leading-relaxed text-text-secondary">
            {publico
              ? "Cualquier estudiante de Orión puede abrirla. Tus profesores la ven siempre. Tu correo, tu WhatsApp y tus pagos nunca se muestran."
              : "Solo la ven tus profesores, para preparar tus clases. Ningún otro estudiante."}
          </p>
        </div>
      </div>

      {!publico && (
        <div className="mt-4 flex items-start gap-3 rounded-base bg-rigel-soft px-3.5 py-3">
          <Rigel pose="guia" decorativo className="h-auto w-12 shrink-0" />
          <p className="text-[13px] leading-relaxed text-rigel-ink">
            <strong>Te recomiendo hacerla visible.</strong> Con tu ficha completa y a la vista, los
            profes llegan a tu primera clase sabiendo qué buscas, y te es más fácil encontrar con
            quién practicar.
          </p>
        </div>
      )}

      {error && (
        <div className="mt-3">
          <AvisoError mensaje={error} />
        </div>
      )}

      <Boton
        variante={publico ? "secundario" : "primario"}
        className="mt-4"
        disabled={cambiar.isPending}
        onClick={() => cambiar.mutate(!publico)}
      >
        {publico ? (
          <>
            <EyeOff size={15} strokeWidth={1.9} />
            Volverla privada
          </>
        ) : (
          <>
            <Eye size={15} strokeWidth={1.9} />
            Hacerla visible
          </>
        )}
      </Boton>
    </div>
  );
}

/** El rótulo de cada bloque: quién ve lo que viene debajo. */
export function QuienLoVe({ icono, titulo, texto }: { icono: React.ReactNode; titulo: string; texto: string }) {
  return (
    <div className="mt-7">
      <p className="flex items-center gap-2 text-[12px] font-bold tracking-[0.06em] text-text-secondary uppercase">
        {icono}
        {titulo}
      </p>
      <p className="mt-1 text-[13px] text-text-muted">{texto}</p>
    </div>
  );
}

function Opcion({
  activa,
  onClick,
  children,
}: {
  activa: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={activa}
      className={`min-h-11 rounded-pill border-[1.5px] px-4 text-[13.5px] font-semibold transition-colors focus-visible:shadow-focus ${
        activa
          ? "border-primary bg-primary text-on-primary"
          : "border-border text-text-secondary hover:bg-surface-sunken"
      }`}
    >
      {children}
    </button>
  );
}
