"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Plus, ShieldCheck, Trash2, UserRound } from "lucide-react";
import { AvisoError, Cargando } from "@/components/estados";
import { Modal } from "@/components/Modal";
import { Badge, Boton, Campo, Spinner } from "@/components/ui";
import { ApiError, apiFetch } from "@/lib/api/fetch";
import { type Adolescente, anosDeNacimientoPosibles, NIVELES, useAdolescentes } from "@/lib/adolescentes";

type DocumentoLegalData = { code: string; version: string; title: string; body: string; effectiveFrom: string };

/**
 * «Adolescentes a mi cargo» (Bloque 12): perfiles de 13 a 17 años bajo la cuenta del adulto. Crear uno
 * pasa por la autorización del representante legal, en pantalla aparte y con su propia casilla, más la
 * de «fue escuchado». Revocarla cierra el perfil y borra lo suyo.
 */
export function Adolescentes() {
  const perfiles = useAdolescentes(true);
  const [creando, setCreando] = useState(false);
  const [editando, setEditando] = useState<Adolescente | null>(null);
  const [revocando, setRevocando] = useState<Adolescente | null>(null);

  return (
    <section className="mt-6 rounded-card border border-border bg-surface-raised p-5" aria-labelledby="adolescentes-titulo">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h2 id="adolescentes-titulo" className="font-display text-[17px] font-bold">Adolescentes a mi cargo</h2>
          <p className="mt-1 text-[13.5px] leading-relaxed text-text-secondary">
            Si eres padre, madre o tutor de alguien de 13 a 17 años, puedes reservarle clases desde tu cuenta.
            Él o ella no tiene cuenta propia: tú apruebas, pagas y hablas con el profe.
          </p>
        </div>
      </div>

      {perfiles.isPending && <div className="mt-4"><Cargando filas={1} /></div>}
      {perfiles.isError && <div className="mt-4"><AvisoError mensaje="No pudimos cargar los perfiles." /></div>}

      {perfiles.data && perfiles.data.length > 0 && (
        <ul className="mt-4 grid gap-2.5">
          {perfiles.data.map((p) => (
            <li key={p.id} className="flex items-center gap-3 rounded-base border border-border px-4 py-3">
              <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-accent-lavender-soft text-[#5E4A8A]">
                <UserRound size={18} strokeWidth={1.75} />
              </span>
              <span className="min-w-0 flex-1">
                <span className="block font-semibold text-text">
                  {p.firstName} <span className="font-normal text-text-secondary">· {p.age} años</span>
                </span>
                <span className="block truncate text-[12.5px] text-text-secondary">
                  {NIVELES.find((n) => n.code === p.selfDeclaredLevel)?.label ?? "Sin nivel"}
                  {p.motivation ? ` · ${p.motivation}` : ""}
                </span>
              </span>
              <Badge tono="menta">
                <ShieldCheck size={12} strokeWidth={2.4} /> Autorizado
              </Badge>
              <Boton variante="contorno" className="h-9 px-3" onClick={() => setEditando(p)}>Editar</Boton>
              <button
                type="button"
                aria-label={`Revocar la autorización de ${p.firstName}`}
                onClick={() => setRevocando(p)}
                className="flex h-9 w-9 items-center justify-center rounded-full text-text-muted hover:bg-surface-sunken hover:text-error focus-visible:shadow-focus"
              >
                <Trash2 size={16} strokeWidth={1.75} />
              </button>
            </li>
          ))}
        </ul>
      )}

      <Boton variante="contorno" className="mt-4 h-11" onClick={() => setCreando(true)}>
        <Plus size={16} strokeWidth={2.2} /> Agregar a un adolescente
      </Boton>

      {creando && <ModalCrear onCerrar={() => setCreando(false)} />}
      {editando && <ModalEditar perfil={editando} onCerrar={() => setEditando(null)} />}
      {revocando && <ModalRevocar perfil={revocando} onCerrar={() => setRevocando(null)} />}
    </section>
  );
}

function ModalCrear({ onCerrar }: { onCerrar: () => void }) {
  const qc = useQueryClient();
  const anoActual = new Date().getFullYear();
  const [nombre, setNombre] = useState("");
  const [nacimiento, setNacimiento] = useState<number>(anoActual - 14);
  const [nivel, setNivel] = useState<Adolescente["selfDeclaredLevel"]>(null);
  const [motivacion, setMotivacion] = useState("");
  const [paso, setPaso] = useState<"datos" | "autorizacion">("datos");
  const [autorizo, setAutorizo] = useState(false);
  const [escuchado, setEscuchado] = useState(false);

  const texto = useQuery({
    queryKey: ["legal", "GUARDIAN_AUTHORIZATION"],
    queryFn: () => apiFetch<DocumentoLegalData>("/api/v1/legal/GUARDIAN_AUTHORIZATION", { redirectOn401: false }),
    enabled: paso === "autorizacion",
    staleTime: 10 * 60_000,
  });

  const crear = useMutation({
    mutationFn: () =>
      apiFetch<Adolescente>("/api/v1/me/adolescents", {
        method: "POST",
        body: {
          firstName: nombre.trim(),
          birthYear: nacimiento,
          selfDeclaredLevel: nivel,
          motivation: motivacion.trim() || null,
          acceptsAuthorization: autorizo,
          heardConfirmed: escuchado,
        },
      }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["me", "adolescents"] });
      onCerrar();
    },
  });
  const error = crear.error instanceof ApiError ? crear.error.message : null;
  const datosListos = nombre.trim().length > 0;

  return (
    <Modal titulo={paso === "datos" ? "Un adolescente a tu cargo" : "Autorización del representante legal"} onCerrar={onCerrar}>
      {paso === "datos" ? (
        <div className="grid gap-4">
          <label className="block">
            <span className="text-[13px] font-bold text-text">Nombre de pila</span>
            <Campo value={nombre} onChange={(e) => setNombre(e.target.value)} maxLength={60} className="mt-1.5" autoFocus />
            <span className="mt-1 block text-[12px] text-text-muted">Solo el nombre: sin apellidos, sin foto, sin correo ni teléfono.</span>
          </label>
          <label className="block">
            <span className="text-[13px] font-bold text-text">Año de nacimiento</span>
            <select
              value={nacimiento}
              onChange={(e) => setNacimiento(Number(e.target.value))}
              className="mt-1.5 h-[52px] w-full rounded-base border-[1.5px] border-border bg-surface-raised px-4 text-[15px]"
            >
              {anosDeNacimientoPosibles(anoActual).map((a) => (
                <option key={a} value={a}>{a} · {anoActual - a} años</option>
              ))}
            </select>
          </label>
          <fieldset>
            <legend className="text-[13px] font-bold text-text">¿Cómo va con el idioma?</legend>
            <div className="mt-1.5 flex flex-wrap gap-2">
              {NIVELES.map((n) => (
                <button
                  key={n.code}
                  type="button"
                  aria-pressed={nivel === n.code}
                  onClick={() => setNivel(nivel === n.code ? null : n.code)}
                  className={`min-h-10 rounded-pill px-4 text-[13.5px] font-semibold ${nivel === n.code ? "bg-primary text-on-primary" : "bg-surface-sunken text-text-secondary"}`}
                >
                  {n.label}
                </button>
              ))}
            </div>
          </fieldset>
          <label className="block">
            <span className="text-[13px] font-bold text-text">¿Para qué quiere el idioma? <span className="font-normal text-text-muted">(opcional)</span></span>
            <textarea
              value={motivacion}
              onChange={(e) => setMotivacion(e.target.value)}
              maxLength={500}
              rows={2}
              placeholder="Para el colegio, un intercambio, los videojuegos…"
              className="mt-1.5 w-full rounded-base border-[1.5px] border-border bg-surface-raised px-4 py-3 text-[15px]"
            />
          </label>
          <div className="flex gap-2.5">
            <Boton variante="contorno" onClick={onCerrar} className="h-11 flex-1">Cancelar</Boton>
            <Boton disabled={!datosListos} onClick={() => setPaso("autorizacion")} className="h-11 flex-1">Seguir</Boton>
          </div>
        </div>
      ) : (
        <div className="grid gap-4">
          <div className="max-h-[40vh] overflow-y-auto rounded-base border border-border bg-surface-sunken p-4 text-[13.5px] leading-relaxed text-text-secondary">
            {texto.isPending && <Cargando filas={2} />}
            {texto.isError && <AvisoError mensaje="No pudimos cargar el texto de la autorización." />}
            {texto.data && <TextoLegal body={texto.data.body} version={texto.data.version} />}
          </div>
          <label className="flex items-start gap-3 text-[14px]">
            <input type="checkbox" checked={autorizo} onChange={(e) => setAutorizo(e.target.checked)} className="mt-1 h-5 w-5" />
            <span>Declaro que soy el padre, la madre o el tutor legal de <strong>{nombre.trim()}</strong> y autorizo el tratamiento de sus datos como dice este texto.</span>
          </label>
          <label className="flex items-start gap-3 text-[14px]">
            <input type="checkbox" checked={escuchado} onChange={(e) => setEscuchado(e.target.checked)} className="mt-1 h-5 w-5" />
            <span>Hablé con {nombre.trim()}, escuché su opinión y está de acuerdo con tomar clases en Orión.</span>
          </label>
          {error && <AvisoError mensaje={error} />}
          <div className="flex gap-2.5">
            <Boton variante="contorno" onClick={() => setPaso("datos")} className="h-11 flex-1">Atrás</Boton>
            <Boton disabled={!autorizo || !escuchado || crear.isPending || !texto.data} onClick={() => crear.mutate()} className="h-11 flex-1">
              {crear.isPending ? <Spinner /> : "Autorizar y crear el perfil"}
            </Boton>
          </div>
        </div>
      )}
    </Modal>
  );
}

function ModalEditar({ perfil, onCerrar }: { perfil: Adolescente; onCerrar: () => void }) {
  const qc = useQueryClient();
  const [nombre, setNombre] = useState(perfil.firstName);
  const [nivel, setNivel] = useState<Adolescente["selfDeclaredLevel"]>(perfil.selfDeclaredLevel);
  const [motivacion, setMotivacion] = useState(perfil.motivation ?? "");
  const guardar = useMutation({
    mutationFn: () =>
      apiFetch<Adolescente>(`/api/v1/me/adolescents/${perfil.id}`, {
        method: "PATCH",
        body: { firstName: nombre.trim(), selfDeclaredLevel: nivel, motivation: motivacion.trim() || null },
      }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["me", "adolescents"] });
      onCerrar();
    },
  });
  const error = guardar.error instanceof ApiError ? guardar.error.message : null;
  return (
    <Modal titulo={`El perfil de ${perfil.firstName}`} onCerrar={onCerrar}>
      <div className="grid gap-4">
        <label className="block">
          <span className="text-[13px] font-bold text-text">Nombre de pila</span>
          <Campo value={nombre} onChange={(e) => setNombre(e.target.value)} maxLength={60} className="mt-1.5" />
        </label>
        <div className="flex flex-wrap gap-2">
          {NIVELES.map((n) => (
            <button key={n.code} type="button" aria-pressed={nivel === n.code} onClick={() => setNivel(nivel === n.code ? null : n.code)}
              className={`min-h-10 rounded-pill px-4 text-[13.5px] font-semibold ${nivel === n.code ? "bg-primary text-on-primary" : "bg-surface-sunken text-text-secondary"}`}>
              {n.label}
            </button>
          ))}
        </div>
        <textarea value={motivacion} onChange={(e) => setMotivacion(e.target.value)} maxLength={500} rows={2}
          className="w-full rounded-base border-[1.5px] border-border bg-surface-raised px-4 py-3 text-[15px]" placeholder="¿Para qué quiere el idioma?" />
        {error && <AvisoError mensaje={error} />}
        <div className="flex gap-2.5">
          <Boton variante="contorno" onClick={onCerrar} className="h-11 flex-1">Cancelar</Boton>
          <Boton disabled={nombre.trim().length === 0 || guardar.isPending} onClick={() => guardar.mutate()} className="h-11 flex-1">
            {guardar.isPending ? <Spinner /> : "Guardar"}
          </Boton>
        </div>
      </div>
    </Modal>
  );
}

function ModalRevocar({ perfil, onCerrar }: { perfil: Adolescente; onCerrar: () => void }) {
  const qc = useQueryClient();
  const revocar = useMutation({
    mutationFn: () => apiFetch<void>(`/api/v1/me/adolescents/${perfil.id}`, { method: "DELETE" }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["me", "adolescents"] });
      onCerrar();
    },
  });
  const error = revocar.error instanceof ApiError ? revocar.error.message : null;
  return (
    <Modal titulo={`¿Revocar la autorización de ${perfil.firstName}?`} onCerrar={onCerrar}>
      <p className="text-[14px] leading-relaxed text-text-secondary">
        Su perfil se cierra y borramos sus datos, salvo lo que la ley nos obliga a conservar de las clases
        pagadas. Ya no podrás reservarle clases; si cambias de idea, lo creas de nuevo.
      </p>
      {error && <div className="mt-3"><AvisoError mensaje={error} /></div>}
      <div className="mt-5 flex gap-2.5">
        <Boton variante="contorno" onClick={onCerrar} className="h-11 flex-1">No</Boton>
        <Boton variante="peligro" disabled={revocar.isPending} onClick={() => revocar.mutate()} className="h-11 flex-1">
          {revocar.isPending ? <Spinner /> : "Revocar"}
        </Boton>
      </div>
    </Modal>
  );
}

/** El markdown de los documentos legales, en lo mínimo: títulos, viñetas y párrafos. */
export function TextoLegal({ body, version }: { body: string; version: string }) {
  const bloques = body.split(/\n\s*\n/).map((b) => b.trim()).filter(Boolean);
  return (
    <div className="grid gap-3">
      <p className="text-[11px] font-bold uppercase tracking-[0.12em] text-text-muted">Versión {version}</p>
      {bloques.map((b, i) => {
        if (b.startsWith("## ")) return <h3 key={i} className="font-display text-[15px] font-bold text-text">{b.slice(3)}</h3>;
        if (b.split("\n").every((l) => /^(-|\d+\.)\s/.test(l))) {
          return (
            <ul key={i} className="grid gap-1 pl-4">
              {b.split("\n").map((l, j) => <li key={j} className="list-disc">{sinMarcas(l.replace(/^(-|\d+\.)\s/, ""))}</li>)}
            </ul>
          );
        }
        return <p key={i}>{sinMarcas(b)}</p>;
      })}
    </div>
  );
}

function sinMarcas(t: string): string {
  return t.replace(/\*\*/g, "").replace(/\{\{[a-z_]+\}\}/g, "");
}
