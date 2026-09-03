"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Check, Copy, Mail, RefreshCw, Search, UserPlus } from "lucide-react";
import { useState } from "react";
import { AvisoError, Cargando, ErrorCarga, Vacio } from "@/components/estados";
import { Modal } from "@/components/Modal";
import { PhoneInput } from "@/components/PhoneInput";
import { BotonPurga } from "@/components/Purga";
import { Badge, Boton, Campo } from "@/components/ui";
import { ApiError, apiFetch } from "@/lib/api/fetch";
import type { AdminUserResponse } from "@/lib/api/types";
import { generarClave } from "@/lib/password";

type Rol = "" | "STUDENT" | "PROFESSOR" | "ADMIN";

const ETIQUETA_ROL: Record<string, string> = {
  STUDENT: "Estudiante",
  PROFESSOR: "Profesor",
  ADMIN: "Admin",
};

/** El plural en español no es "+es": "Estudiantes", no "Estudiantees". */
const FILTROS_ROL: { valor: Rol; etiqueta: string }[] = [
  { valor: "", etiqueta: "Todos" },
  { valor: "STUDENT", etiqueta: "Estudiantes" },
  { valor: "PROFESSOR", etiqueta: "Profesores" },
  { valor: "ADMIN", etiqueta: "Admins" },
];

export default function AdminUsuariosPage() {
  const [rol, setRol] = useState<Rol>("");
  const [busqueda, setBusqueda] = useState("");
  const [creando, setCreando] = useState(false);
  const [invitando, setInvitando] = useState(false);

  const params = new URLSearchParams();
  if (rol) params.set("role", rol);
  if (busqueda.trim()) params.set("q", busqueda.trim());
  const query = params.toString();

  const usuarios = useQuery({
    queryKey: ["admin", "users", rol, busqueda.trim()],
    queryFn: () => apiFetch<AdminUserResponse[]>(`/api/v1/admin/users${query ? `?${query}` : ""}`),
  });

  return (
    <main className="mx-auto max-w-5xl px-6 py-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="font-display text-h1 font-bold">Usuarios</h1>
        <div className="flex items-center gap-2">
          <Boton variante="secundario" onClick={() => setInvitando(true)} className="h-11">
            <Mail size={16} strokeWidth={1.75} />
            Invitar profesor
          </Boton>
          <Boton variante="primario" onClick={() => setCreando(true)} className="h-11">
            <UserPlus size={16} strokeWidth={1.75} />
            Crear usuario
          </Boton>
        </div>
      </div>

      <div className="mt-4 flex flex-wrap items-center gap-2">
        <div className="min-w-[220px] flex-1">
          <Campo
            type="search"
            value={busqueda}
            onChange={(event) => setBusqueda(event.target.value)}
            placeholder="Buscar por nombre o correo"
            icono={<Search size={16} strokeWidth={2.2} />}
          />
        </div>
        {FILTROS_ROL.map((filtro) => (
          <button
            key={filtro.valor || "todos"}
            type="button"
            onClick={() => setRol(filtro.valor)}
            className={`rounded-base px-3.5 py-2 text-[12.5px] transition-colors ${
              rol === filtro.valor
                ? "bg-night font-bold text-on-primary"
                : "border-[1.5px] border-border bg-surface-raised font-semibold text-text-secondary hover:bg-surface-sunken"
            }`}
          >
            {filtro.etiqueta}
          </button>
        ))}
      </div>

      <div className="mt-5">
        {usuarios.isPending && <Cargando filas={4} />}

        {usuarios.isError && (
          <ErrorCarga
            mensaje="No pudimos cargar los usuarios."
            onReintentar={() => void usuarios.refetch()}
          />
        )}

        {usuarios.data?.length === 0 && (
          <Vacio
            titulo="Sin resultados"
            texto="Prueba con otro filtro o crea un usuario nuevo."
          />
        )}

        {!!usuarios.data?.length && (
          <div className="overflow-x-auto rounded-card bg-surface-raised shadow-md">
            <table className="w-full text-[13px]">
              <thead>
                <tr className="bg-surface text-left text-[11px] font-bold uppercase tracking-[0.1em] text-text-muted">
                  <th className="px-4 py-3">Nombre</th>
                  <th className="px-4 py-3">Correo</th>
                  <th className="px-4 py-3">WhatsApp</th>
                  <th className="px-4 py-3">Rol</th>
                  <th className="px-4 py-3">Estado</th>
                  <th className="px-4 py-3 text-right">Acción</th>
                </tr>
              </thead>
              <tbody>
                {usuarios.data.map((usuario) => (
                  <FilaUsuario key={usuario.id} usuario={usuario} />
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {creando && <ModalCrearUsuario onCerrar={() => setCreando(false)} />}
      {invitando && <ModalInvitarProfesor onCerrar={() => setInvitando(false)} />}
    </main>
  );
}

function ModalInvitarProfesor({ onCerrar }: { onCerrar: () => void }) {
  const queryClient = useQueryClient();
  const [email, setEmail] = useState("");
  const [enviado, setEnviado] = useState(false);

  const invitar = useMutation({
    mutationFn: () =>
      apiFetch<void>("/api/v1/admin/professors/invite", {
        method: "POST",
        body: { email: email.trim() },
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "users"] });
      setEnviado(true);
    },
  });

  const error = invitar.error instanceof ApiError ? invitar.error.message : null;

  return (
    <Modal titulo="Invitar profesor" onCerrar={onCerrar}>
      {enviado ? (
        <>
          <p className="text-[13px] text-text-secondary">
            Le enviamos la invitación a <span className="font-semibold text-text">{email.trim()}</span>.
            Aparecerá como profesor inactivo hasta que complete su perfil.
          </p>
          <Boton variante="primario" onClick={onCerrar} className="mt-5 h-12 w-full">
            Entendido
          </Boton>
        </>
      ) : (
        <>
          <p className="text-[13px] text-text-secondary">
            Le enviamos un correo para que complete su perfil y active su cuenta. Nada de claves
            temporales.
          </p>
          <label className="mt-4 block text-[12.5px] font-bold text-text-secondary" htmlFor="invite-email">
            Correo
          </label>
          <Campo
            id="invite-email"
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            placeholder="profesor@correo.com"
            className="mt-1.5"
          />

          {error && (
            <div className="mt-3">
              <AvisoError mensaje={error} />
            </div>
          )}

          <div className="mt-5 flex gap-2.5">
            <Boton variante="contorno" onClick={onCerrar} className="h-11 flex-1">
              Cancelar
            </Boton>
            <Boton
              variante="primario"
              disabled={!email.trim() || invitar.isPending}
              onClick={() => invitar.mutate()}
              className="h-11 flex-1"
            >
              {invitar.isPending ? "Enviando…" : "Enviar invitación"}
            </Boton>
          </div>
        </>
      )}
    </Modal>
  );
}

function FilaUsuario({ usuario }: { usuario: AdminUserResponse }) {
  const queryClient = useQueryClient();
  const activo = usuario.status === "ACTIVE";

  const cambiarEstado = useMutation({
    mutationFn: () =>
      apiFetch<AdminUserResponse>(`/api/v1/admin/users/${usuario.id}`, {
        method: "PATCH",
        body: { status: activo ? "INACTIVE" : "ACTIVE" },
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "users"] });
      // Un profesor inactivo desaparece del directorio de los estudiantes.
      void queryClient.invalidateQueries({ queryKey: ["professors"] });
    },
  });

  return (
    <tr className="border-t border-surface-sunken hover:bg-surface">
      <td className="px-4 py-3 font-semibold">{usuario.fullName}</td>
      <td className="px-4 py-3 text-text-secondary">{usuario.email}</td>
      <td className="px-4 py-3 text-text-secondary">{usuario.whatsappPhone ?? "—"}</td>
      <td className="px-4 py-3">
        <Badge
          tono={
            usuario.role === "PROFESSOR" ? "lavanda" : usuario.role === "ADMIN" ? "coral" : "menta"
          }
        >
          {ETIQUETA_ROL[usuario.role ?? ""] ?? usuario.role}
        </Badge>
      </td>
      <td className="px-4 py-3">
        <Badge tono={activo ? "menta" : "melocoton"}>{activo ? "Activo" : "Inactivo"}</Badge>
      </td>
      <td className="px-4 py-3 text-right">
        <div className="flex items-center justify-end gap-2">
          <Boton
            variante="contorno"
            disabled={cambiarEstado.isPending || usuario.role === "ADMIN"}
            onClick={() => cambiarEstado.mutate()}
            className="h-9"
          >
            {activo ? "Inactivar" : "Activar"}
          </Boton>
          {/* Inactivar oculta; borrar destruye. Son cosas distintas y por eso conviven. */}
          <BotonPurga tipo="user" id={usuario.id!} etiqueta="Borrar" />
        </div>
      </td>
    </tr>
  );
}

function ModalCrearUsuario({ onCerrar }: { onCerrar: () => void }) {
  const queryClient = useQueryClient();

  const [email, setEmail] = useState("");
  const [nombre, setNombre] = useState("");
  const [telefono, setTelefono] = useState("");
  const [rol, setRol] = useState<"STUDENT" | "PROFESSOR">("STUDENT");
  const [clave, setClave] = useState(generarClave);
  const [copiada, setCopiada] = useState(false);

  const crear = useMutation({
    mutationFn: () =>
      apiFetch<AdminUserResponse>("/api/v1/admin/users", {
        method: "POST",
        body: {
          email: email.trim(),
          fullName: nombre.trim(),
          whatsappPhone: telefono.trim() || undefined,
          role: rol,
          password: clave,
        },
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "users"] });
      onCerrar();
    },
  });

  const error = crear.error instanceof ApiError ? crear.error.message : null;

  async function copiar() {
    await navigator.clipboard.writeText(clave);
    setCopiada(true);
    setTimeout(() => setCopiada(false), 2000);
  }

  return (
    <Modal titulo="Crear usuario" onCerrar={onCerrar}>
      <label className="block text-[12.5px] font-bold text-text-secondary" htmlFor="nombre">
        Nombre completo
      </label>
      <Campo
        id="nombre"
        value={nombre}
        onChange={(event) => setNombre(event.target.value)}
        className="mt-1.5"
      />

      <label className="mt-3 block text-[12.5px] font-bold text-text-secondary" htmlFor="email">
        Correo
      </label>
      <Campo
        id="email"
        type="email"
        value={email}
        onChange={(event) => setEmail(event.target.value)}
        className="mt-1.5"
      />

      <label className="mt-3 block text-[12.5px] font-bold text-text-secondary" htmlFor="telefono">
        WhatsApp (opcional)
      </label>
      <PhoneInput id="telefono" value={telefono} onChange={setTelefono} className="mt-1.5" />

      <p className="mt-3 text-[12.5px] font-bold text-text-secondary">Rol</p>
      <div className="mt-1.5 flex gap-2">
        {(["STUDENT", "PROFESSOR"] as const).map((valor) => (
          <Boton
            key={valor}
            variante={rol === valor ? "tinta" : "contorno"}
            onClick={() => setRol(valor)}
            className="h-10 flex-1"
          >
            {ETIQUETA_ROL[valor]}
          </Boton>
        ))}
      </div>

      <p className="mt-3 text-[12.5px] font-bold text-text-secondary">Contraseña temporal</p>
      <div className="mt-1.5 flex items-center gap-2">
        <code className="flex-1 truncate rounded-base border-[1.5px] border-border bg-surface-sunken px-4 py-2.5 text-[13px] font-semibold">
          {clave}
        </code>
        <button
          type="button"
          aria-label="Generar otra"
          onClick={() => setClave(generarClave())}
          className="grid h-10 w-10 shrink-0 place-items-center rounded-base border-[1.5px] border-border text-text-secondary hover:bg-surface-sunken"
        >
          <RefreshCw size={16} strokeWidth={2.2} />
        </button>
        <button
          type="button"
          aria-label="Copiar contraseña"
          onClick={() => void copiar()}
          className="grid h-10 w-10 shrink-0 place-items-center rounded-base border-[1.5px] border-border text-text-secondary hover:bg-surface-sunken"
        >
          {copiada ? <Check size={16} strokeWidth={2.4} /> : <Copy size={16} strokeWidth={2.2} />}
        </button>
      </div>
      <p className="mt-1.5 text-[11.5px] text-text-muted">
        Compártela por WhatsApp: se lee en voz alta sin equivocarse.
      </p>

      {error && (
        <div className="mt-3">
          <AvisoError mensaje={error} />
        </div>
      )}

      <div className="mt-5 flex gap-2.5">
        <Boton variante="contorno" onClick={onCerrar} className="h-11 flex-1">
          Cancelar
        </Boton>
        <Boton
          variante="primario"
          disabled={!email.trim() || !nombre.trim() || crear.isPending}
          onClick={() => crear.mutate()}
          className="h-11 flex-1"
        >
          {crear.isPending ? "Creando…" : "Crear"}
        </Boton>
      </div>
    </Modal>
  );
}
