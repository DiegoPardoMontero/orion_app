"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowLeft,
  Award,
  BadgeCheck,
  Check,
  ExternalLink,
  FileText,
  GraduationCap,
  MapPin,
  MessageSquare,
  Sparkles,
  Video,
  X,
} from "lucide-react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useState, type ReactNode } from "react";
import { Avatar } from "@/components/Avatar";
import { AvisoError, Cargando, ErrorCarga } from "@/components/estados";
import { Modal } from "@/components/Modal";
import { Badge, Boton, Segmento } from "@/components/ui";
import { ApiError, apiFetch } from "@/lib/api/fetch";
import type {
  AdminApplicationDetail,
  GoalResponse,
} from "@/lib/api/types";
import { estadoAplicacion, etiquetaDocumento, etiquetaEvento } from "@/lib/aplicacion";
import { fechaCorta, horaBogota, precioCop } from "@/lib/format";
import { etiquetaNivel, etiquetaObjetivo } from "@/lib/i18n";

type Tab = "enviados" | "publico";

export default function AdminAplicacionDetallePage() {
  const { id } = useParams<{ id: string }>();

  const detalle = useQuery({
    queryKey: ["admin", "application", id],
    queryFn: () => apiFetch<AdminApplicationDetail>(`/api/v1/admin/teacher-applications/${id}`),
  });

  const goals = useQuery({
    queryKey: ["catalog", "goals"],
    queryFn: () => apiFetch<GoalResponse[]>("/api/v1/catalog/goals"),
    staleTime: 5 * 60_000,
  });

  const [tab, setTab] = useState<Tab>("enviados");

  if (detalle.isPending) {
    return (
      <main className="mx-auto max-w-4xl px-6 py-6">
        <Cargando filas={5} />
      </main>
    );
  }

  if (detalle.isError) {
    return (
      <main className="mx-auto max-w-4xl px-6 py-6">
        <ErrorCarga mensaje="No pudimos cargar la solicitud." onReintentar={() => void detalle.refetch()} />
      </main>
    );
  }

  const data = detalle.data;
  const sol = data.application!;
  const perfil = data.profile ?? {};
  const cfg = estadoAplicacion(sol.status);

  return (
    <main className="mx-auto max-w-4xl px-6 py-6">
      <Link
        href="/admin/aplicaciones"
        className="inline-flex items-center gap-1.5 text-[13px] font-semibold text-text-secondary hover:text-text"
      >
        <ArrowLeft size={16} strokeWidth={1.9} />
        Volver a solicitudes
      </Link>

      <header className="mt-4 flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-4">
          <Avatar nombre={sol.fullName ?? ""} fotoUrl={perfil.photoUrl} size="lg" />
          <div className="min-w-0">
            <h1 className="font-display text-[24px] font-bold">{sol.fullName}</h1>
            <p className="text-[13px] text-text-muted">{sol.email}</p>
          </div>
        </div>
        <Badge tono={cfg.tono} punto={cfg.punto}>
          {cfg.label}
        </Badge>
      </header>

      <div className="mt-5">
        <Acciones id={id} status={sol.status ?? ""} />
      </div>

      <div className="mt-6 max-w-sm">
        <Segmento<Tab>
          valor={tab}
          onCambio={setTab}
          opciones={[
            { valor: "enviados", etiqueta: "Datos enviados" },
            { valor: "publico", etiqueta: "Perfil público" },
          ]}
        />
      </div>

      <div className="mt-5">
        {tab === "enviados" ? (
          <DatosEnviados data={data} goals={goals.data} />
        ) : (
          <PerfilPublico perfil={perfil} goals={goals.data} />
        )}
      </div>

      <Historial data={data} />
    </main>
  );
}

/* ---------------- Acciones de revisión ---------------- */

function Acciones({ id, status }: { id: string; status: string }) {
  const queryClient = useQueryClient();
  const [modal, setModal] = useState<null | "reject" | "request-changes">(null);

  const invalidar = () => {
    void queryClient.invalidateQueries({ queryKey: ["admin", "application", id] });
    void queryClient.invalidateQueries({ queryKey: ["admin", "applications"] });
  };

  const accion = useMutation({
    mutationFn: (path: string) =>
      apiFetch<void>(`/api/v1/admin/teacher-applications/${id}/${path}`, { method: "POST" }),
    onSuccess: invalidar,
  });

  const error = accion.error instanceof ApiError ? accion.error.message : null;

  if (status === "PENDING_REVIEW") {
    return (
      <>
        <Boton variante="primario" disabled={accion.isPending} onClick={() => accion.mutate("start-review")} className="h-11">
          Empezar revisión
        </Boton>
        {error && <div className="mt-3 max-w-md"><AvisoError mensaje={error} /></div>}
      </>
    );
  }

  if (status === "UNDER_REVIEW") {
    return (
      <>
        <div className="flex flex-wrap gap-2.5">
          <Boton variante="primario" disabled={accion.isPending} onClick={() => accion.mutate("approve")} className="h-11">
            <Check size={16} strokeWidth={2.2} />
            Aprobar
          </Boton>
          <Boton variante="secundario" disabled={accion.isPending} onClick={() => setModal("request-changes")} className="h-11">
            Pedir cambios
          </Boton>
          <Boton variante="peligro" disabled={accion.isPending} onClick={() => setModal("reject")} className="h-11">
            <X size={16} strokeWidth={2.2} />
            Rechazar
          </Boton>
        </div>
        {error && <div className="mt-3 max-w-md"><AvisoError mensaje={error} /></div>}
        {modal && (
          <ModalDecision
            id={id}
            tipo={modal}
            onCerrar={() => setModal(null)}
            onListo={() => {
              setModal(null);
              invalidar();
            }}
          />
        )}
      </>
    );
  }

  return (
    <p className="rounded-base bg-surface-sunken px-4 py-3 text-[12.5px] text-text-secondary">
      Sin acciones disponibles en este estado.
    </p>
  );
}

function ModalDecision({
  id,
  tipo,
  onCerrar,
  onListo,
}: {
  id: string;
  tipo: "reject" | "request-changes";
  onCerrar: () => void;
  onListo: () => void;
}) {
  const [nota, setNota] = useState("");
  const valido = nota.trim().length >= 10;

  const enviar = useMutation({
    mutationFn: () =>
      apiFetch<void>(`/api/v1/admin/teacher-applications/${id}/${tipo}`, {
        method: "POST",
        body: { note: nota.trim() },
      }),
    onSuccess: onListo,
  });

  const error = enviar.error instanceof ApiError ? enviar.error.message : null;
  const titulo = tipo === "reject" ? "Rechazar postulación" : "Pedir cambios";

  return (
    <Modal titulo={titulo} onCerrar={onCerrar}>
      <p className="text-[13px] text-text-secondary">
        {tipo === "reject"
          ? "Explica por qué no fue aprobada. El aspirante verá este mensaje."
          : "Indica con claridad qué debe ajustar. El aspirante verá este mensaje y podrá reenviar."}
      </p>
      <textarea
        rows={4}
        value={nota}
        onChange={(e) => setNota(e.target.value)}
        placeholder="Escribe el motivo (mínimo 10 caracteres)…"
        className="mt-3 w-full rounded-base border-[1.5px] border-border bg-surface-raised px-4 py-3 text-sm placeholder:text-text-muted focus:border-primary focus:shadow-focus focus:outline-none"
      />
      <p className="mt-1 text-[11.5px] text-text-muted">{nota.trim().length}/10 mínimo</p>

      {error && <div className="mt-3"><AvisoError mensaje={error} /></div>}

      <div className="mt-5 flex gap-2.5">
        <Boton variante="contorno" onClick={onCerrar} className="h-12 flex-1">
          Cancelar
        </Boton>
        <Boton
          variante={tipo === "reject" ? "peligro" : "primario"}
          disabled={!valido || enviar.isPending}
          onClick={() => enviar.mutate()}
          className="h-12 flex-1"
        >
          {enviar.isPending ? "Enviando…" : titulo}
        </Boton>
      </div>
    </Modal>
  );
}

/* ---------------- Tab: datos enviados ---------------- */

function DatosEnviados({
  data,
  goals,
}: {
  data: AdminApplicationDetail;
  goals?: GoalResponse[];
}) {
  const perfil = data.profile ?? {};
  const documentos = data.documents ?? [];

  return (
    <div className="space-y-5">
      <Bloque titulo="Presentación">
        <Dato etiqueta="Titular" valor={perfil.headline} />
        <Dato etiqueta="Sobre sí" valor={perfil.bio} multilinea />
      </Bloque>

      <Bloque titulo="Idiomas que enseña">
        {perfil.languages && perfil.languages.length > 0 ? (
          <ul className="space-y-2">
            {perfil.languages.map((l) => (
              <li key={l.code} className="text-[13.5px]">
                <span className="font-bold text-text">
                  {l.flagEmoji ? `${l.flagEmoji} ` : ""}
                  {l.nameEs ?? l.code}
                </span>
                {l.isNative && <span className="ml-1.5 text-[11.5px] font-bold text-primary-strong">· Nativo</span>}
                {l.levels && l.levels.length > 0 && (
                  <span className="ml-1.5 text-text-secondary">
                    ({l.levels.map((n) => etiquetaNivel(n)).join(", ")})
                  </span>
                )}
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-[13px] text-text-muted">Sin idiomas.</p>
        )}
      </Bloque>

      <Bloque titulo="Objetivos">
        {perfil.goals && perfil.goals.length > 0 ? (
          <div className="flex flex-wrap gap-1.5">
            {perfil.goals.map((g) => (
              <span key={g} className="rounded-pill bg-surface-sunken px-3 py-1.5 text-[12.5px] font-semibold text-text-secondary">
                {etiquetaObjetivo(g, goals)}
              </span>
            ))}
          </div>
        ) : (
          <p className="text-[13px] text-text-muted">Sin objetivos.</p>
        )}
      </Bloque>

      <Bloque titulo="Experiencia">
        <Dato etiqueta="Ciudad" valor={[perfil.city, perfil.countryCode].filter(Boolean).join(", ") || undefined} />
        <Dato
          etiqueta="Años de experiencia"
          valor={perfil.yearsExperience != null ? String(perfil.yearsExperience) : undefined}
        />
        <Dato etiqueta="Formación" valor={perfil.education} />
        <Dato etiqueta="Certificación docente" valor={perfil.certified ? "Sí" : "No"} />
        <Dato etiqueta="Ofrece clase de prueba" valor={perfil.acceptsTrial ? "Sí" : "No"} />
        {perfil.hourlyRateCop ? <Dato etiqueta="Tarifa por hora" valor={precioCop(perfil.hourlyRateCop)} /> : null}
      </Bloque>

      <Bloque titulo="Documentos">
        {documentos.length > 0 ? (
          <ul className="space-y-2">
            {documentos.map((doc) => (
              <li key={doc.id} className="flex items-center justify-between gap-3 rounded-base bg-surface-sunken px-3.5 py-2.5">
                <span className="flex min-w-0 items-center gap-2">
                  <FileText size={15} strokeWidth={1.9} className="shrink-0 text-text-secondary" />
                  <span className="min-w-0">
                    <span className="block truncate text-[13px] font-semibold text-text">{doc.fileName}</span>
                    <span className="block text-[11px] text-text-muted">{etiquetaDocumento(doc.docType)}</span>
                  </span>
                </span>
                <VerDocumento userId={data.application?.userId ?? ""} docId={doc.id ?? ""} />
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-[13px] text-text-muted">Sin documentos.</p>
        )}
      </Bloque>
    </div>
  );
}

function VerDocumento({ userId, docId }: { userId: string; docId: string }) {
  const abrir = useMutation({
    mutationFn: () =>
      apiFetch<{ url: string }>(`/api/v1/admin/teachers/${userId}/documents/${docId}/url`),
    onSuccess: (res) => {
      if (res.url) window.open(res.url, "_blank", "noopener,noreferrer");
    },
  });

  return (
    <button
      type="button"
      onClick={() => abrir.mutate()}
      disabled={abrir.isPending}
      className="inline-flex shrink-0 items-center gap-1.5 rounded-pill border-[1.5px] border-border px-3.5 py-2 text-[12.5px] font-bold text-text transition-colors hover:bg-surface-raised focus-visible:shadow-focus disabled:opacity-60"
    >
      <ExternalLink size={14} strokeWidth={2} />
      {abrir.isPending ? "Abriendo…" : "Ver documento"}
    </button>
  );
}

/* ---------------- Tab: perfil público (previsualización) ---------------- */

function PerfilPublico({
  perfil,
  goals,
}: {
  perfil: NonNullable<AdminApplicationDetail["profile"]>;
  goals?: GoalResponse[];
}) {
  return (
    <div className="rounded-card bg-surface-raised p-6 shadow-sm">
      <p className="mb-4 text-[11.5px] font-bold uppercase tracking-[0.08em] text-text-muted">
        Así se verá su tarjeta cuando publique
      </p>
      <div className="flex items-center gap-4">
        <Avatar nombre={perfil.fullName ?? ""} fotoUrl={perfil.photoUrl} size="xl" />
        <div className="min-w-0">
          <h2 className="truncate font-display text-[22px] font-bold">{perfil.fullName}</h2>
          <p className="truncate text-[13.5px] text-text-secondary">{perfil.headline}</p>
          {perfil.hourlyRateCop ? (
            <p className="mt-1 font-display text-[18px] font-bold text-text">
              {precioCop(perfil.hourlyRateCop)}
              <span className="ml-1 text-[12px] font-semibold text-text-muted">/ hora</span>
            </p>
          ) : null}
        </div>
      </div>

      <div className="mt-3 flex flex-wrap gap-2">
        <Badge tono="lavanda"><Video size={12} strokeWidth={2.4} /> Virtual</Badge>
        <Badge tono="melocoton"><MapPin size={12} strokeWidth={2.4} /> Presencial</Badge>
        {perfil.certified && <Badge tono="menta"><BadgeCheck size={12} strokeWidth={2.4} /> Certificado</Badge>}
        {perfil.acceptsTrial && <Badge tono="coral"><Sparkles size={12} strokeWidth={2.4} /> Clase de prueba</Badge>}
      </div>

      {perfil.bio && (
        <p className="mt-4 text-[14px] leading-relaxed text-text-secondary">{perfil.bio}</p>
      )}

      {perfil.languages && perfil.languages.length > 0 && (
        <div className="mt-5">
          <h3 className="text-[12px] font-bold uppercase tracking-[0.04em] text-text-muted">Idiomas que enseña</h3>
          <ul className="mt-2 space-y-2">
            {perfil.languages.map((l) => (
              <li key={l.code} className="rounded-base bg-surface-sunken p-3">
                <p className="flex items-center gap-1.5 text-[14px] font-bold text-text">
                  {l.flagEmoji && <span aria-hidden="true">{l.flagEmoji}</span>}
                  {l.nameEs ?? l.code}
                  {l.isNative && (
                    <span className="rounded-pill bg-primary-soft px-2 py-px text-[10.5px] font-bold text-primary-strong">Nativo</span>
                  )}
                </p>
                {l.levels && l.levels.length > 0 && (
                  <div className="mt-1.5 flex flex-wrap gap-1.5">
                    {l.levels.map((n) => (
                      <span key={n} className="rounded-pill bg-accent-lavender-soft px-2.5 py-1 text-[11.5px] font-semibold text-[#5e4a8a]">
                        {etiquetaNivel(n)}
                      </span>
                    ))}
                  </div>
                )}
              </li>
            ))}
          </ul>
        </div>
      )}

      {perfil.goals && perfil.goals.length > 0 && (
        <div className="mt-5">
          <h3 className="text-[12px] font-bold uppercase tracking-[0.04em] text-text-muted">Ideal para</h3>
          <div className="mt-2 flex flex-wrap gap-1.5">
            {perfil.goals.map((g) => (
              <span key={g} className="rounded-pill bg-surface-sunken px-3 py-1.5 text-[12.5px] font-semibold text-text-secondary">
                {etiquetaObjetivo(g, goals)}
              </span>
            ))}
          </div>
        </div>
      )}

      {(perfil.city || perfil.yearsExperience != null || perfil.education) && (
        <dl className="mt-5 space-y-2.5 text-[13.5px]">
          {(perfil.city || perfil.countryCode) && (
            <div className="flex items-start gap-2 text-text-secondary">
              <MapPin size={15} strokeWidth={1.75} className="mt-0.5 shrink-0 text-text-muted" />
              <dd>{[perfil.city, perfil.countryCode].filter(Boolean).join(", ")}</dd>
            </div>
          )}
          {perfil.yearsExperience != null && perfil.yearsExperience > 0 && (
            <div className="flex items-start gap-2 text-text-secondary">
              <Award size={15} strokeWidth={1.75} className="mt-0.5 shrink-0 text-text-muted" />
              <dd>
                {perfil.yearsExperience} {perfil.yearsExperience === 1 ? "año de experiencia" : "años de experiencia"}
              </dd>
            </div>
          )}
          {perfil.education && (
            <div className="flex items-start gap-2 text-text-secondary">
              <GraduationCap size={15} strokeWidth={1.75} className="mt-0.5 shrink-0 text-text-muted" />
              <dd>{perfil.education}</dd>
            </div>
          )}
        </dl>
      )}
    </div>
  );
}

/* ---------------- Historial ---------------- */

function Historial({ data }: { data: AdminApplicationDetail }) {
  const historia = data.history ?? [];
  if (historia.length === 0) return null;

  return (
    <section className="mt-8">
      <h2 className="text-[13px] font-bold uppercase tracking-[0.06em] text-text-muted">Historial</h2>
      <ol className="mt-3 space-y-3 border-l-2 border-surface-sunken pl-4">
        {historia.map((evento, i) => (
          <li key={i} className="relative">
            <span className="absolute -left-[21px] top-1.5 h-2.5 w-2.5 rounded-full bg-primary" aria-hidden="true" />
            <p className="text-[13.5px] font-bold text-text">{etiquetaEvento(evento.eventType)}</p>
            {evento.note && (
              <p className="mt-0.5 flex items-start gap-1.5 text-[12.5px] text-text-secondary">
                <MessageSquare size={13} strokeWidth={1.9} className="mt-0.5 shrink-0" />
                {evento.note}
              </p>
            )}
            {evento.createdAt && (
              <p className="mt-0.5 text-[11.5px] text-text-muted">
                {fechaCorta(evento.createdAt)} · {horaBogota(evento.createdAt)}
              </p>
            )}
          </li>
        ))}
      </ol>
    </section>
  );
}

/* ---------------- Piezas pequeñas ---------------- */

function Bloque({ titulo, children }: { titulo: string; children: ReactNode }) {
  return (
    <section className="rounded-card bg-surface-raised p-5 shadow-sm">
      <h2 className="text-[12px] font-bold uppercase tracking-[0.06em] text-text-muted">{titulo}</h2>
      <div className="mt-3">{children}</div>
    </section>
  );
}

function Dato({ etiqueta, valor, multilinea }: { etiqueta: string; valor?: string; multilinea?: boolean }) {
  return (
    <div className="mb-2.5 last:mb-0">
      <p className="text-[11.5px] font-bold text-text-muted">{etiqueta}</p>
      <p className={`text-[13.5px] text-text ${multilinea ? "whitespace-pre-line leading-relaxed" : ""}`}>
        {valor || <span className="text-text-muted">—</span>}
      </p>
    </div>
  );
}
