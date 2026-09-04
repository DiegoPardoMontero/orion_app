"use client";

import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowLeft,
  Award,
  BadgeCheck,
  Calendar,
  Check,
  ChevronLeft,
  ChevronRight,
  Clock,
  CreditCard,
  GraduationCap,
  Languages,
  Mail,
  MapPin,
  MessageCircle,
  ShieldCheck,
  Sparkles,
  Video,
} from "lucide-react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { Fragment, useMemo, useState, type ReactNode } from "react";
import { Avatar } from "@/components/Avatar";
import { DiscoIdioma } from "@/components/DiscoIdioma";
import { LineaImporte } from "@/components/dinero";
import { AvisoError, Cargando, ErrorCarga, Vacio } from "@/components/estados";
import { EstrellaRating, EstrellasFijas } from "@/components/Rating";
import { Badge, Bloque, Boton, BotonPrincipal, Campo, Chip, Segmento, Spinner } from "@/components/ui";
import { ApiError, apiFetch } from "@/lib/api/fetch";
import type {
  BookingResponse,
  ConversationSummary,
  CreditBalanceResponse,
  GoalResponse,
  Modality,
  PagedReviews,
  ProfessorDetail,
  SlotView,
  SlotsResponse,
} from "@/lib/api/types";
import { useMe } from "@/lib/auth/session";
import {
  diaBogota,
  esGratis,
  fechaCorta,
  fechaRelativa,
  horaBogota,
  minutoDelDiaBogota,
  precioCop,
  tarifaClase,
} from "@/lib/format";
import { etiquetaNivel, etiquetaObjetivo } from "@/lib/i18n";
import { aplicarSaldo } from "@/lib/saldo";
import { useMediaQuery } from "@/lib/useMediaQuery";

export default function AgendaProfesorPage() {
  // En Next 16 los params de página son una Promise; en un client component se leen con este hook.
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const queryClient = useQueryClient();

  const esDesktop = useMediaQuery("(min-width: 1024px)");
  const { data: me } = useMe();

  // Abrir (o reencontrar) la conversación con este profesor y saltar a su hilo. Un usuario anónimo
  // —que en teoría no llega hasta aquí, porque la ruta es del estudiante— iría a iniciar sesión.
  const abrirConversacion = useMutation({
    mutationFn: () =>
      apiFetch<ConversationSummary>("/api/v1/conversations", {
        method: "POST",
        body: { counterpartId: id },
      }),
    onSuccess: (conv) => {
      if (conv.id) router.push(`/mensajes/${conv.id}`);
    },
  });

  const [diaElegido, setDiaElegido] = useState<string | null>(null);
  const [cupoElegido, setCupoElegido] = useState<string | null>(null);
  const [modalidad, setModalidad] = useState<Modality>("VIRTUAL");
  const [idioma, setIdioma] = useState<string | null>(null);
  const [nota, setNota] = useState("");

  const profesor = useQuery({
    queryKey: ["professor", id],
    queryFn: () => apiFetch<ProfessorDetail>(`/api/v1/professors/${id}`),
  });

  // El catálogo de objetivos traduce los códigos (CONVERSATION, BUSINESS…) a su nombre en español.
  const goals = useQuery({
    queryKey: ["catalog", "goals"],
    queryFn: () => apiFetch<GoalResponse[]>("/api/v1/catalog/goals"),
    staleTime: 5 * 60_000,
  });

  // Los próximos 7 días alimentan los chips de móvil (y el estado de carga/vacío inicial).
  const cupos = useQuery({
    queryKey: ["slots", id],
    queryFn: () => apiFetch<SlotsResponse>(`/api/v1/professors/${id}/slots`),
  });

  // El saldo a favor se descuenta antes de cobrar, así que el desglose se puede anticipar aquí
  // mismo. Es una estimación honesta: la cifra que manda es la que devuelve el backend al reservar.
  const saldo = useQuery({
    queryKey: ["me", "credits"],
    queryFn: () => apiFetch<CreditBalanceResponse>("/api/v1/me/credits"),
    enabled: me?.role === "STUDENT",
    staleTime: 60_000,
  });

  const porDia = useMemo(() => agruparPorDia(cupos.data?.slots ?? []), [cupos.data]);
  const dias = Object.keys(porDia);
  const diaActivo = diaElegido && porDia[diaElegido] ? diaElegido : (dias[0] ?? null);
  const cuposDelDia = diaActivo ? porDia[diaActivo] : [];

  const reservar = useMutation({
    mutationFn: (startsAt: string) =>
      apiFetch<BookingResponse>("/api/v1/bookings", {
        method: "POST",
        body: {
          professorId: id,
          startsAt,
          modality: modalidad,
          languageCode: idiomaDeLaClase ?? undefined,
          locationNote: modalidad === "IN_PERSON" ? nota.trim() || undefined : undefined,
        },
      }),
    onSuccess: (reserva) => {
      void queryClient.invalidateQueries({ queryKey: ["me", "bookings"] });
      void queryClient.invalidateQueries({ queryKey: ["me", "credits"] });

      // El cupo queda apartado, pero la clase no existe hasta que entre el pago. Si el saldo del
      // estudiante la cubrió entera no hay pasarela a la que ir y la reserva ya está confirmada.
      const checkoutUrl = reserva.payment?.checkoutUrl;
      if (checkoutUrl) {
        window.location.href = checkoutUrl;
        return;
      }
      router.push("/mis-clases?reservada=1");
    },
    onError: () => {
      // 422/409: la agenda que ve el usuario está desfasada. Invalidamos todos los cupos del
      // profesor (chips y semana) para que vuelva a pedirlos y vea la realidad.
      setCupoElegido(null);
      void queryClient.invalidateQueries({ queryKey: ["slots", id] });
    },
  });

  const errorReserva = reservar.error instanceof ApiError ? reservar.error.message : null;

  if (profesor.isPending || cupos.isPending) {
    return (
      <main className="mx-auto w-full max-w-md px-7 py-6 lg:max-w-[1180px] lg:px-12 lg:py-8">
        <Cargando filas={4} />
      </main>
    );
  }

  if (profesor.isError) {
    return (
      <main className="mx-auto w-full max-w-md px-7 py-6 lg:max-w-[1180px] lg:px-12 lg:py-8">
        <ErrorCarga
          mensaje="No pudimos cargar este profesor."
          onReintentar={() => void profesor.refetch()}
        />
      </main>
    );
  }

  const detalle = profesor.data;

  const resumen =
    cupoElegido
      ? `${fechaCorta(cupoElegido)} · ${horaBogota(cupoElegido)} · ${modalidad === "VIRTUAL" ? "Virtual" : "Presencial"}`
      : null;

  const idiomasQueEnsena = detalle.languages ?? [];
  // Con un solo idioma no se pregunta: se manda ese. Con varios, el que haya elegido —y si no ha
  // elegido, se manda null y el backend responde 422, que es la verdad.
  const idiomaDeLaClase =
    idiomasQueEnsena.length === 1 ? (idiomasQueEnsena[0].code ?? null) : idioma;

  const precio = detalle.hourlyRateCop ?? null;
  // La misma regla que aplica el backend, mínimo de la pasarela incluido: si el desglose de aquí y
  // el del checkout no coinciden al peso, el estudiante ve cambiar el precio entre dos pantallas.
  const { creditoAplicadoCop: creditoAplicado, aPagarCop: aPagar } = aplicarSaldo(
    precio ?? 0,
    saldo.data?.balanceCop ?? 0,
  );

  // Controles compartidos entre móvil y desktop: idioma, modalidad, nota, confirmación.
  const controles: ReactNode = (
    <>
      {/* El selector solo aparece cuando hay algo que elegir. Con un idioma el backend lo asigna
          solo, y preguntar entre una opción sería un paso de más. */}
      {idiomasQueEnsena.length > 1 && (
        <Bloque
          tono="lavanda"
          titulo="Idioma de la clase"
          icono={<Languages size={16} strokeWidth={1.75} />}
        >
          <div className="flex flex-wrap gap-2">
            {idiomasQueEnsena.map((lang) => (
              <button
                key={lang.code}
                type="button"
                onClick={() => setIdioma(lang.code ?? null)}
                className={`inline-flex min-h-11 items-center gap-2 rounded-pill border-[1.5px] px-4 text-[14px] font-bold transition-colors ${
                  idioma === lang.code
                    ? "border-transparent bg-night text-on-primary"
                    : "border-border bg-surface-raised text-text hover:bg-surface-sunken"
                }`}
              >
                <DiscoIdioma code={lang.code ?? ""} />
                {lang.nameEs}
              </button>
            ))}
          </div>
          {!idioma && (
            <p className="mt-2 text-[12.5px] text-text-secondary">
              Elige en cuál quieres la clase.
            </p>
          )}
        </Bloque>
      )}

      <Bloque tono="menta" titulo="Modalidad" icono={<Video size={16} strokeWidth={1.75} />}>
        <Segmento<Modality>
          valor={modalidad}
          onCambio={setModalidad}
          opciones={[
            { valor: "VIRTUAL", etiqueta: (<><Video size={15} strokeWidth={1.75} /> Virtual</>) },
            { valor: "IN_PERSON", etiqueta: (<><MapPin size={15} strokeWidth={1.75} /> Presencial</>) },
          ]}
        />
      </Bloque>

      {modalidad === "IN_PERSON" ? (
        <Campo
          type="text"
          value={nota}
          onChange={(event) => setNota(event.target.value)}
          maxLength={300}
          icono={<MapPin size={16} strokeWidth={1.75} />}
          placeholder="¿Dónde se encontrarán? (opcional)"
        />
      ) : (
        <p className="flex items-center gap-2 rounded-base bg-accent-lavender-soft px-4 py-3 text-[12.5px] text-[#5e4a8a]">
          <Video size={16} strokeWidth={1.75} className="shrink-0" />
          Al confirmar creamos una sala de videollamada; el enlace llega a tu correo y a Mis clases.
        </p>
      )}

      {errorReserva && <AvisoError mensaje={errorReserva} />}
      {resumen && <p className="text-center text-[13px] font-semibold text-text">{resumen}</p>}

      {precio !== null && (
        <div className="rounded-base border border-border bg-surface-sunken px-4 py-3 text-[13px]">
          <LineaImporte
            etiqueta="Clase de 60 minutos"
            valor={esGratis(precio) ? "Gratis" : precioCop(precio)}
          />
          {creditoAplicado > 0 && (
            <LineaImporte
              etiqueta="Tu saldo a favor"
              valor={`− ${precioCop(creditoAplicado)}`}
              tono="credito"
            />
          )}
          {!esGratis(precio) && (
            <LineaImporte
              tono="total"
              etiqueta={aPagar === 0 ? "Cubierto con tu saldo" : "Total a pagar"}
              valor={precioCop(aPagar)}
            />
          )}
        </div>
      )}

      <BotonPrincipal
        disabled={!cupoElegido || reservar.isPending}
        onClick={() => cupoElegido && reservar.mutate(cupoElegido)}
      >
        {reservar.isPending ? (
          <>
            <Spinner />
            Reservando…
          </>
        ) : aPagar === 0 ? (
          <>
            Confirmar reserva
            <Check size={18} strokeWidth={1.75} />
          </>
        ) : (
          <>
            Continuar al pago
            <CreditCard size={18} strokeWidth={1.75} />
          </>
        )}
      </BotonPrincipal>

      <p className="flex items-center justify-center gap-1.5 text-center text-[11.5px] text-text-muted">
        {aPagar === 0 ? (
          <>
            <Mail size={13} strokeWidth={1.75} />
            Recibirás confirmación por correo con invitación al calendario
          </>
        ) : (
          <>
            <ShieldCheck size={13} strokeWidth={1.75} className="shrink-0" />
            Pagas en Wompi (PSE, tarjeta o Nequi). Te guardamos el cupo mientras completas el pago.
          </>
        )}
      </p>
    </>
  );

  return (
    <main className="mx-auto w-full max-w-md px-7 py-6 lg:max-w-[1180px] lg:px-12 lg:py-8">
      <Link
        href="/profesores"
        aria-label="Volver a profesores"
        className="grid h-11 w-11 place-items-center rounded-full bg-surface-sunken text-text transition-colors hover:bg-border focus-visible:shadow-focus"
      >
        <ArrowLeft size={18} strokeWidth={1.75} />
      </Link>

      <div className="lg:grid lg:grid-cols-[340px_minmax(0,1fr)] lg:gap-10">
        {/* Columna de perfil (compacta) */}
        <section className="mt-5 lg:mt-6">
          <div className="flex items-center gap-4">
            <Avatar nombre={detalle.fullName ?? ""} fotoUrl={detalle.photoUrl} size="lg" className="lg:hidden" />
            <Avatar nombre={detalle.fullName ?? ""} fotoUrl={detalle.photoUrl} size="xl" className="hidden lg:block" />
            <div className="min-w-0">
              <h1 className="truncate font-display text-[20px] font-bold lg:text-[30px]">
                {detalle.fullName}
              </h1>
              <p className="truncate text-[13px] text-text-secondary lg:text-[15px]">
                {detalle.headline}
              </p>
            </div>
          </div>

          {/* Precio por hora: dato honesto, prominente. */}
          {tarifaClase(detalle.hourlyRateCop) ? (
            <p className="mt-3 font-display text-[22px] font-bold text-text">
              {tarifaClase(detalle.hourlyRateCop)}
              {!esGratis(detalle.hourlyRateCop) && (
                <span className="ml-1 text-[13px] font-semibold text-text-muted">/ hora</span>
              )}
            </p>
          ) : null}

          {/* Rating honesto: estrella + promedio si hay ≥3 reseñas; si no, "Nuevo en Orión". */}
          <div className="mt-2">
            <EstrellaRating
              ratingAvg={detalle.ratingAvg}
              ratingCount={detalle.ratingCount}
              conteo="largo"
            />
          </div>

          <div className="mt-3 flex flex-wrap gap-2">
            <Badge tono="lavanda">
              <Video size={12} strokeWidth={2.4} /> Virtual
            </Badge>
            <Badge tono="melocoton">
              <MapPin size={12} strokeWidth={2.4} /> Presencial
            </Badge>
            {detalle.certified && (
              <Badge tono="menta">
                <BadgeCheck size={12} strokeWidth={2.4} /> Certificado
              </Badge>
            )}
            {detalle.acceptsTrial && (
              <Badge tono="coral">
                <Sparkles size={12} strokeWidth={2.4} /> Ofrece clase de prueba
              </Badge>
            )}
          </div>

          {/* Enviar mensaje: solo el estudiante lo ve; el profesor no se escribe a sí mismo. */}
          {me?.role !== "PROFESSOR" && (
            <div className="mt-4">
              <Boton
                variante="secundario"
                disabled={abrirConversacion.isPending}
                onClick={() => {
                  if (!me) {
                    router.push("/login");
                    return;
                  }
                  abrirConversacion.mutate();
                }}
                className="h-11 w-full lg:w-auto"
              >
                {abrirConversacion.isPending ? (
                  <Spinner />
                ) : (
                  <MessageCircle size={17} strokeWidth={1.75} />
                )}
                Enviar mensaje
              </Boton>
              {abrirConversacion.error instanceof ApiError && (
                <div className="mt-2">
                  <AvisoError mensaje={abrirConversacion.error.message} />
                </div>
              )}
            </div>
          )}

          {detalle.bio && (
            <div className="mt-4 rounded-base bg-surface-raised p-4 shadow-sm lg:mt-5 lg:bg-transparent lg:p-0 lg:shadow-none">
              <p className="text-[13.5px] leading-relaxed text-text-secondary lg:text-[15px] lg:leading-[1.7]">
                {detalle.bio}
              </p>
            </div>
          )}

          {/* Idiomas con sus niveles */}
          {detalle.languages && detalle.languages.length > 0 && (
            <div className="mt-5">
              <h2 className="text-[12px] font-bold uppercase tracking-[0.04em] text-text-muted">
                Idiomas que enseña
              </h2>
              <ul className="mt-2 space-y-2">
                {detalle.languages.map((idioma) => (
                  <li key={idioma.code} className="rounded-base bg-surface-raised p-3 shadow-sm">
                    <p className="flex items-center gap-1.5 text-[14px] font-bold text-text">
                      <DiscoIdioma code={idioma.code ?? ""} size={18} />
                      {idioma.nameEs}
                      {idioma.isNative && (
                        <span className="rounded-pill bg-primary-soft px-2 py-px text-[10.5px] font-bold text-primary-strong">
                          Nativo
                        </span>
                      )}
                    </p>
                    {idioma.levels && idioma.levels.length > 0 && (
                      <div className="mt-1.5 flex flex-wrap gap-1.5">
                        {idioma.levels.map((nivel) => (
                          <span
                            key={nivel}
                            className="rounded-pill bg-accent-lavender-soft px-2.5 py-1 text-[11.5px] font-semibold text-[#5e4a8a]"
                          >
                            {etiquetaNivel(nivel)}
                          </span>
                        ))}
                      </div>
                    )}
                  </li>
                ))}
              </ul>
            </div>
          )}

          {/* Objetivos */}
          {detalle.goals && detalle.goals.length > 0 && (
            <div className="mt-5">
              <h2 className="text-[12px] font-bold uppercase tracking-[0.04em] text-text-muted">
                Ideal para
              </h2>
              <div className="mt-2 flex flex-wrap gap-1.5">
                {detalle.goals.map((code) => (
                  <span
                    key={code}
                    className="rounded-pill bg-surface-sunken px-3 py-1.5 text-[12.5px] font-semibold text-text-secondary"
                  >
                    {etiquetaObjetivo(code, goals.data)}
                  </span>
                ))}
              </div>
            </div>
          )}

          {/* Datos: ciudad, experiencia, educación */}
          {(detalle.city ||
            detalle.yearsExperience != null ||
            detalle.education) && (
            <dl className="mt-5 space-y-2.5 text-[13.5px]">
              {(detalle.city || detalle.countryCode) && (
                <div className="flex items-start gap-2 text-text-secondary">
                  <MapPin size={15} strokeWidth={1.75} className="mt-0.5 shrink-0 text-text-muted" />
                  <dd>{[detalle.city, detalle.countryCode].filter(Boolean).join(", ")}</dd>
                </div>
              )}
              {detalle.yearsExperience != null && detalle.yearsExperience > 0 && (
                <div className="flex items-start gap-2 text-text-secondary">
                  <Award size={15} strokeWidth={1.75} className="mt-0.5 shrink-0 text-text-muted" />
                  <dd>
                    {detalle.yearsExperience}{" "}
                    {detalle.yearsExperience === 1 ? "año de experiencia" : "años de experiencia"}
                  </dd>
                </div>
              )}
              {detalle.education && (
                <div className="flex items-start gap-2 text-text-secondary">
                  <GraduationCap
                    size={15}
                    strokeWidth={1.75}
                    className="mt-0.5 shrink-0 text-text-muted"
                  />
                  <dd>{detalle.education}</dd>
                </div>
              )}
            </dl>
          )}
        </section>

        {/* Columna de agenda */}
        <section className="mt-5 lg:mt-6 lg:rounded-card lg:bg-surface-raised lg:p-9 lg:shadow-lg">
          {cupos.isError ? (
            <ErrorCarga mensaje="No pudimos cargar la agenda." onReintentar={() => void cupos.refetch()} />
          ) : esDesktop ? (
            <div className="space-y-5">
              <AgendaSemanal profesorId={id} cupoElegido={cupoElegido} onElegir={setCupoElegido} />
              {controles}
            </div>
          ) : dias.length === 0 ? (
            <Vacio
              mascota
              titulo="Sin cupos esta semana"
              texto="Vuelve en unos días: seguro encontramos un horario que te sirva."
            />
          ) : (
            <div className="space-y-3">
              <Bloque tono="melocoton" titulo="Elige un día" icono={<Calendar size={16} strokeWidth={1.75} />}>
                <div className="flex flex-wrap gap-2">
                  {dias.map((dia) => (
                    <Chip
                      key={dia}
                      familia="fecha"
                      activo={dia === diaActivo}
                      onClick={() => {
                        setDiaElegido(dia);
                        setCupoElegido(null);
                      }}
                    >
                      {fechaCorta(porDia[dia][0].startsAt!)}
                    </Chip>
                  ))}
                </div>
              </Bloque>

              <Bloque
                tono="lavanda"
                titulo="Cupos disponibles"
                icono={<Clock size={16} strokeWidth={1.75} />}
                extra={
                  <span className="text-[12px] font-bold normal-case text-[#5e4a8a]">
                    {cuposDelDia.length} {cuposDelDia.length === 1 ? "libre" : "libres"}
                  </span>
                }
              >
                {cuposDelDia.length === 0 ? (
                  <p className="text-[13px] text-[#5e4a8a]">Elige un día para ver sus horarios.</p>
                ) : (
                  <div className="grid grid-cols-3 gap-2.5">
                    {cuposDelDia.map((cupo) => (
                      <Chip
                        key={cupo.startsAt}
                        familia="hora"
                        activo={cupo.startsAt === cupoElegido}
                        onClick={() => setCupoElegido(cupo.startsAt!)}
                      >
                        {horaBogota(cupo.startsAt!)}
                      </Chip>
                    ))}
                  </div>
                )}
              </Bloque>

              {controles}
            </div>
          )}
        </section>
      </div>

      <SeccionResenas profesorId={id} />
    </main>
  );
}

/**
 * Sección pública de reseñas del profesor. Carga la primera página del endpoint público paginado y
 * ofrece "Ver más" mientras queden páginas. Datos honestos: si el profesor no tiene reseñas visibles
 * no se inventa nada — se muestra "Aún no hay reseñas".
 */
function SeccionResenas({ profesorId }: { profesorId: string }) {
  const resenas = useInfiniteQuery({
    queryKey: ["professor", profesorId, "reviews"],
    queryFn: ({ pageParam }) =>
      apiFetch<PagedReviews>(`/api/v1/professors/${profesorId}/reviews?page=${pageParam}&size=10`),
    initialPageParam: 0,
    getNextPageParam: (ultima) => {
      const siguiente = (ultima.page ?? 0) + 1;
      return siguiente < (ultima.totalPages ?? 0) ? siguiente : undefined;
    },
  });

  const items = resenas.data?.pages.flatMap((p) => p.content ?? []) ?? [];
  const total = resenas.data?.pages[0]?.totalElements ?? 0;

  return (
    <section className="mt-10 lg:mt-12">
      <h2 className="font-display text-[20px] font-bold lg:text-[24px]">
        Reseñas
        {total > 0 && <span className="ml-2 text-[15px] font-semibold text-text-muted">{total}</span>}
      </h2>

      <div className="mt-4">
        {resenas.isPending ? (
          <Cargando filas={2} />
        ) : resenas.isError ? (
          <ErrorCarga
            mensaje="No pudimos cargar las reseñas."
            onReintentar={() => void resenas.refetch()}
          />
        ) : items.length === 0 ? (
          <p className="rounded-card bg-surface-raised px-5 py-8 text-center text-[14px] text-text-secondary shadow-sm">
            Aún no hay reseñas.
          </p>
        ) : (
          <>
            <ul className="grid gap-3 lg:grid-cols-2">
              {items.map((resena) => (
                <li key={resena.id} className="rounded-card bg-surface-raised p-5 shadow-sm">
                  <div className="flex items-center gap-3">
                    <Avatar nombre={resena.studentName ?? ""} size="sm" />
                    <div className="min-w-0">
                      <p className="truncate text-[14px] font-bold text-text">
                        {resena.studentName}
                      </p>
                      {resena.createdAt && (
                        <p className="text-[12px] text-text-muted">{fechaRelativa(resena.createdAt)}</p>
                      )}
                    </div>
                  </div>
                  <div className="mt-2.5">
                    <EstrellasFijas rating={resena.rating ?? 0} />
                  </div>
                  {resena.comment && (
                    <p className="mt-2 text-[13.5px] leading-relaxed text-text-secondary">
                      {resena.comment}
                    </p>
                  )}
                </li>
              ))}
            </ul>

            {resenas.hasNextPage && (
              <div className="mt-5 flex justify-center">
                <Boton
                  variante="contorno"
                  disabled={resenas.isFetchingNextPage}
                  onClick={() => void resenas.fetchNextPage()}
                  className="h-11 px-7"
                >
                  {resenas.isFetchingNextPage ? "Cargando…" : "Ver más reseñas"}
                </Boton>
              </div>
            )}
          </>
        )}
      </div>
    </section>
  );
}

/** Vista semanal navegable (desktop): 7 columnas de días × filas de horas. */
function AgendaSemanal({
  profesorId,
  cupoElegido,
  onElegir,
}: {
  profesorId: string;
  cupoElegido: string | null;
  onElegir: (startsAt: string) => void;
}) {
  const [offset, setOffset] = useState(0);
  const hoy = diaBogota(new Date().toISOString());
  const from = addDays(hoy, offset * 7);
  const dias7 = useMemo(() => Array.from({ length: 7 }, (_, i) => addDays(from, i)), [from]);
  const to = dias7[6];

  const cupos = useQuery({
    queryKey: ["slots", profesorId, "semana", from],
    queryFn: () =>
      apiFetch<SlotsResponse>(`/api/v1/professors/${profesorId}/slots?from=${from}&to=${to}`),
  });

  const { porCelda, horas } = useMemo(() => {
    const celda: Record<string, Record<string, SlotView>> = {};
    // La hora se ordena por el minuto del día, no por su etiqueta: ordenar el texto ponía las
    // 10:00 AM encima de las 9:00 AM y las 7:00 PM encima de las 8:00 AM.
    const orden = new Map<string, number>();
    for (const slot of cupos.data?.slots ?? []) {
      if (!slot.startsAt) continue;
      const dia = diaBogota(slot.startsAt);
      const hora = horaBogota(slot.startsAt);
      (celda[dia] ??= {})[hora] = slot;
      orden.set(hora, minutoDelDiaBogota(slot.startsAt));
    }
    const horasOrdenadas = [...orden.entries()]
      .sort((a, b) => a[1] - b[1])
      .map(([hora]) => hora);
    return { porCelda: celda, horas: horasOrdenadas };
  }, [cupos.data]);

  return (
    <div>
      <div className="flex items-center justify-between">
        <button
          type="button"
          aria-label="Semana anterior"
          disabled={offset === 0}
          onClick={() => setOffset((o) => Math.max(0, o - 1))}
          className="grid h-10 w-10 place-items-center rounded-full border-[1.5px] border-border text-text transition-colors hover:bg-surface-sunken focus-visible:shadow-focus disabled:opacity-40"
        >
          <ChevronLeft size={18} strokeWidth={1.75} />
        </button>
        <p className="font-display text-[15px] font-bold">
          {etiquetaSemana(from)} – {etiquetaSemana(to)}
        </p>
        <button
          type="button"
          aria-label="Semana siguiente"
          disabled={offset >= 5}
          onClick={() => setOffset((o) => Math.min(5, o + 1))}
          className="grid h-10 w-10 place-items-center rounded-full border-[1.5px] border-border text-text transition-colors hover:bg-surface-sunken focus-visible:shadow-focus disabled:opacity-40"
        >
          <ChevronRight size={18} strokeWidth={1.75} />
        </button>
      </div>

      {cupos.isPending ? (
        <div className="mt-4">
          <Cargando filas={3} />
        </div>
      ) : cupos.isError ? (
        <div className="mt-4">
          <ErrorCarga mensaje="No pudimos cargar la semana." onReintentar={() => void cupos.refetch()} />
        </div>
      ) : horas.length === 0 ? (
        <p className="mt-6 rounded-base bg-surface-sunken px-4 py-8 text-center text-[14px] text-text-secondary">
          Sin cupos esta semana. Prueba con la siguiente →
        </p>
      ) : (
        <div className="mt-4 overflow-x-auto">
          <div
            className="grid min-w-[440px] gap-1"
            style={{ gridTemplateColumns: "44px repeat(7, minmax(52px, 1fr))" }}
          >
            <div aria-hidden="true" />
            {dias7.map((dia) => (
              <div key={dia} className="pb-1 text-center">
                <p className="text-[11px] font-bold uppercase tracking-[0.04em] text-text-muted">
                  {diaSemana(dia)}
                </p>
                <p className="text-[15px] font-bold text-text">{diaNumero(dia)}</p>
              </div>
            ))}

            {horas.map((hora) => (
              <Fragment key={hora}>
                <div className="flex items-center justify-end pr-1 text-[12px] font-semibold text-text-muted">
                  {hora}
                </div>
                {dias7.map((dia) => {
                  const slot = porCelda[dia]?.[hora];
                  if (!slot?.startsAt) {
                    return <div key={dia} className="min-h-11 rounded-base bg-surface-sunken/40" />;
                  }
                  const activo = slot.startsAt === cupoElegido;
                  return (
                    <button
                      key={dia}
                      type="button"
                      onClick={() => onElegir(slot.startsAt!)}
                      className={`min-h-11 rounded-base text-[12px] font-semibold transition-colors focus-visible:shadow-focus ${
                        activo
                          ? "bg-primary text-on-primary hora-elegida"
                          : "bg-accent-lavender-soft text-[#5e4a8a] hover:bg-[#e2d7f4]"
                      }`}
                    >
                      {hora}
                    </button>
                  );
                })}
              </Fragment>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

/** Los cupos vienen planos; la agenda los necesita por día (en fecha de Bogotá, no del navegador). */
function agruparPorDia(slots: SlotView[]): Record<string, SlotView[]> {
  const grupos: Record<string, SlotView[]> = {};
  for (const slot of slots) {
    if (!slot.startsAt) continue;
    const dia = diaBogota(slot.startsAt);
    (grupos[dia] ??= []).push(slot);
  }
  return grupos;
}

/** Aritmética de calendario pura (UTC), sin deriva de zona: "YYYY-MM-DD" + días → "YYYY-MM-DD". */
function addDays(dateStr: string, days: number): string {
  const [y, m, d] = dateStr.split("-").map(Number);
  const date = new Date(Date.UTC(y, m - 1, d));
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

// Formateo del encabezado a partir de la fecha (mediodía Bogotá evita que la zona la corra un día).
const bogotaNoon = (dateStr: string) => new Date(`${dateStr}T12:00:00-05:00`);
const diaSemana = (dateStr: string) =>
  new Intl.DateTimeFormat("es-CO", { timeZone: "America/Bogota", weekday: "short" }).format(bogotaNoon(dateStr));
const diaNumero = (dateStr: string) =>
  new Intl.DateTimeFormat("es-CO", { timeZone: "America/Bogota", day: "numeric" }).format(bogotaNoon(dateStr));
const etiquetaSemana = (dateStr: string) =>
  new Intl.DateTimeFormat("es-CO", { timeZone: "America/Bogota", day: "numeric", month: "short" }).format(
    bogotaNoon(dateStr),
  );
