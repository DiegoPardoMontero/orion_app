"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Check, Eye, EyeOff, Lock, Sparkles } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { AvatarOrion } from "@/components/gamificacion/AvatarOrion";
import { EstrellaLogro } from "@/components/gamificacion/EstrellaLogro";
import { ApiError, apiFetch } from "@/lib/api/fetch";
import type { GoalResponse, LanguageResponse } from "@/lib/api/types";
import {
  estadoDe,
  numeralDe,
  NIVEL_ESTUDIANTE,
  type Engagement,
  type FichaEstudiante,
  type Logro,
} from "@/lib/gamificacion";
import { AvisoError } from "@/components/estados";
import { Boton, BotonPrincipal } from "@/components/ui";

const NIVELES = ["BEGINNER", "INTERMEDIATE", "ADVANCED"] as const;

/**
 * «Mi ficha»: lo que el estudiante declara sobre sí mismo, más su avatar compuesto y las últimas
 * estrellas que encendió.
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
        Lo que cuentas de ti. Todo es opcional; lo que llenes le sirve a tu profesor.
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
          <p className="font-display text-[17px] font-bold">{ficha.data.fullName}</p>
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

      <Formulario
        ficha={ficha.data}
        idiomas={idiomas.data ?? []}
        objetivos={objetivos.data ?? []}
        onGuardado={() => {
          void queryClient.invalidateQueries({ queryKey: ["me", "student-profile"] });
          void queryClient.invalidateQueries({ queryKey: ["me", "achievements"] });
          void queryClient.invalidateQueries({ queryKey: ["me", "engagement"] });
        }}
      />

      <Privacidad ficha={ficha.data} onCambio={() => void ficha.refetch()} />
    </section>
  );
}

/** Las tres últimas encendidas. Sin ninguna, se invita en vez de dejar el hueco. */
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
    <ul className="mt-3 flex justify-center gap-2 sm:justify-start">
      {recientes.map((logro) => (
        <li key={logro.code}>
          <EstrellaLogro
            familia={logro.family}
            brillo={logro.glow}
            estado={estadoDe(logro)}
            numeral={numeralDe(logro.code)}
            size={38}
            titulo={`${logro.name}. ${logro.description}`}
          />
        </li>
      ))}
    </ul>
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
      onGuardado();
    },
  });

  const alternar = (code: string) =>
    setMetas((previas) =>
      previas.includes(code) ? previas.filter((c) => c !== code) : [...previas, code],
    );

  const error = guardar.error instanceof ApiError ? guardar.error.message : null;

  return (
    <div className="mt-4 rounded-card border border-border bg-surface-raised p-5">
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

      {guardado && !guardar.isPending && (
        <p className="mt-3 flex items-center gap-2 rounded-card bg-success-bg px-4 py-3 text-[13px] font-semibold text-success">
          <Check size={16} strokeWidth={2.4} />
          Guardado. Tu profesor ya lo puede ver.
        </p>
      )}

      <BotonPrincipal
        className="mt-4"
        disabled={guardar.isPending}
        onClick={() => {
          setGuardado(false);
          guardar.mutate();
        }}
      >
        {guardar.isPending ? "Guardando…" : "Guardar mi ficha"}
      </BotonPrincipal>
    </div>
  );
}

/**
 * El interruptor del perfil público.
 *
 * <p>La fecha de nacimiento solo se pide al activarlo, porque solo ahí hace falta: el servidor no
 * deja publicar el perfil de un menor de edad, y esa comprobación es suya, no de este formulario.
 */
function Privacidad({ ficha, onCambio }: { ficha: FichaEstudiante; onCambio: () => void }) {
  const publico = ficha.isPublic === true;
  const [fecha, setFecha] = useState(ficha.birthDate ?? "");
  const [pidiendoFecha, setPidiendoFecha] = useState(false);

  const cambiar = useMutation({
    mutationFn: (destino: boolean) =>
      apiFetch<FichaEstudiante>("/api/v1/me/student-profile/visibility", {
        method: "PUT",
        body: { isPublic: destino, birthDate: fecha || null },
      }),
    onSuccess: () => {
      setPidiendoFecha(false);
      onCambio();
    },
  });

  const error = cambiar.error instanceof ApiError ? cambiar.error.message : null;

  return (
    <div className="mt-4 rounded-card border border-border bg-surface-raised p-5">
      <div className="flex items-start gap-3">
        <span className="mt-0.5 text-text-muted">
          {publico ? <Eye size={18} strokeWidth={1.8} /> : <Lock size={18} strokeWidth={1.8} />}
        </span>
        <div className="min-w-0 flex-1">
          <p className="text-[14px] font-bold">
            {publico ? "Tu ficha es pública" : "Tu ficha es privada"}
          </p>
          {/* Ser exactos aquí importa: quien lee esto está decidiendo qué muestra de sí mismo. Los
              profesores con los que ya tuvo clase la ven de todos modos —la usan para preparar la
              clase—; lo que este interruptor decide es si otros estudiantes también. */}
          <p className="mt-1 text-[13px] leading-relaxed text-text-secondary">
            {publico
              ? "Cualquier estudiante de Orión puede abrirla. Tus profesores la ven siempre."
              : "Solo la ven tus profesores, para preparar tus clases. Ningún otro estudiante."}
          </p>
        </div>
      </div>

      {(pidiendoFecha || (!publico && fecha === "")) && !publico && (
        <div className={pidiendoFecha ? "mt-4" : "hidden"}>
          <label className="block text-[12px] font-bold uppercase tracking-[0.04em] text-text-secondary" htmlFor="nacimiento">
            Tu fecha de nacimiento
          </label>
          <input
            id="nacimiento"
            type="date"
            value={fecha}
            onChange={(e) => setFecha(e.target.value)}
            className="mt-1.5 w-full rounded-base border-[1.5px] border-border bg-surface px-4 py-3 text-[14px] outline-none focus-visible:border-primary focus-visible:shadow-focus"
          />
          <p className="mt-1.5 text-[12.5px] text-text-muted">
            Se pide una sola vez y no aparece en tu perfil.
          </p>
        </div>
      )}

      {error && (
        <div className="mt-3">
          <AvisoError mensaje={error} />
        </div>
      )}

      <Boton
        variante="secundario"
        className="mt-4"
        disabled={cambiar.isPending}
        onClick={() => {
          if (publico) {
            cambiar.mutate(false);
            return;
          }
          if (!fecha) {
            setPidiendoFecha(true);
            return;
          }
          cambiar.mutate(true);
        }}
      >
        {publico ? (
          <>
            <EyeOff size={15} strokeWidth={1.9} />
            Volverlo privado
          </>
        ) : (
          <>
            <Eye size={15} strokeWidth={1.9} />
            Hacerlo visible
          </>
        )}
      </Boton>
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
