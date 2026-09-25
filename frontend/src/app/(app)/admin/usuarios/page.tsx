"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Check, Copy, Mail, RefreshCw, Search, UserPlus } from "lucide-react";
import { useState } from "react";
import { AvisoError, Cargando, ErrorCarga, Vacio } from "@/components/estados";
import { Modal } from "@/components/Modal";
import { PhoneInput } from "@/components/PhoneInput";
import { BotonPurga } from "@/components/Purga";
import { Badge, Boton, Campo, Spinner } from "@/components/ui";
import { ApiError, apiFetch } from "@/lib/api/fetch";
import type { AdminUserResponse } from "@/lib/api/types";
import { estadoDeFundador } from "@/lib/fundador";
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
            aria-label="Buscar usuarios por nombre o correo"
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
                  <th className="px-3 py-3">Nombre</th>
                  <th className="px-3 py-3">Correo</th>
                  <th className="px-3 py-3">WhatsApp</th>
                  <th className="px-3 py-3">Rol</th>
                  <th className="px-3 py-3">Estado</th>
                  <th className="px-3 py-3 text-right">Acción</th>
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

/**
 * La invitación a un profe (V71): ya no crea la cuenta. El profe abre el enlace, ve la pantalla de
 * invitación, crea su cuenta de aspirante con este correo y lleva su postulación, que se aprueba
 * aquí mismo, en Postulaciones. El nombre es con el que se le saluda, y el cargo de quien invita
 * queda en su cuenta para las siguientes.
 */
function ModalInvitarProfesor({ onCerrar }: { onCerrar: () => void }) {
  const queryClient = useQueryClient();
  const [email, setEmail] = useState("");
  const [nombre, setNombre] = useState("");
  const [cargo, setCargo] = useState<string | null>(null);
  const [fundador, setFundador] = useState(true);
  const [enviado, setEnviado] = useState(false);

  const firma = useQuery({
    queryKey: ["admin", "invite-defaults"],
    queryFn: () =>
      apiFetch<{ inviterName: string; inviterTitle: string | null }>("/api/v1/admin/professors/invite/defaults"),
  });
  const cargoMostrado = cargo ?? firma.data?.inviterTitle ?? "";

  const invitar = useMutation({
    mutationFn: () =>
      apiFetch<void>("/api/v1/admin/professors/invite", {
        method: "POST",
        body: {
          email: email.trim(),
          professorName: nombre.trim() || undefined,
          founder: fundador,
          inviterTitle: cargoMostrado.trim() || undefined,
        },
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "invite-defaults"] });
      setEnviado(true);
    },
  });

  const error = invitar.error instanceof ApiError ? invitar.error.message : null;

  return (
    <Modal titulo="Invitar profesor" onCerrar={onCerrar}>
      {enviado ? (
        <>
          <p className="text-[13px] text-text-secondary">
            Le enviamos la invitación a <span className="font-semibold text-text">{email.trim()}</span>. El enlace
            vence en 7 días. Cuando cree su cuenta y envíe su postulación, la verás en Postulaciones.
          </p>
          <Boton variante="primario" onClick={onCerrar} className="mt-5 h-12 w-full">
            Entendido
          </Boton>
        </>
      ) : (
        <>
          <p className="text-[13px] text-text-secondary">
            Le llega un correo con su invitación personal. Crea su cuenta con este correo, completa su postulación y
            tú la apruebas.
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
          <label className="mt-4 block text-[12.5px] font-bold text-text-secondary" htmlFor="invite-nombre">
            Nombre <span className="font-semibold text-text-muted">(opcional, para saludarlo)</span>
          </label>
          <Campo
            id="invite-nombre"
            type="text"
            maxLength={80}
            value={nombre}
            onChange={(event) => setNombre(event.target.value)}
            placeholder="Mariana"
            className="mt-1.5"
          />
          <label className="mt-4 block text-[12.5px] font-bold text-text-secondary" htmlFor="invite-cargo">
            Tu cargo <span className="font-semibold text-text-muted">(lo ve el profe)</span>
          </label>
          <Campo
            id="invite-cargo"
            type="text"
            maxLength={80}
            value={cargoMostrado}
            onChange={(event) => setCargo(event.target.value)}
            placeholder="directora académica"
            className="mt-1.5"
          />
          <p className="mt-1.5 text-[12px] text-text-muted">
            Así se lee: «Te invita {firma.data?.inviterName ?? "tu nombre"}
            {cargoMostrado.trim() ? `, ${cargoMostrado.trim()}` : ""}».
          </p>
          <label className="mt-4 flex cursor-pointer items-start gap-3 rounded-base bg-surface-sunken p-3" htmlFor="invite-fundador">
            <input
              id="invite-fundador"
              type="checkbox"
              checked={fundador}
              onChange={(event) => setFundador(event.target.checked)}
              className="mt-0.5 h-5 w-5 shrink-0 accent-[var(--color-primary)]"
            />
            <span className="text-[13px] leading-relaxed text-text">
              <span className="font-bold">Profe fundador.</span> Paga la comisión de fundador sus primeros meses de
              clases, con lo que diga Ajustes el día que se apruebe su postulación.
            </span>
          </label>

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
      <td className="px-3 py-3 font-semibold">
        {usuario.fullName}
        {usuario.role === "PROFESSOR" && (
          <span className="mt-0.5 block text-[11.5px] font-medium text-text-muted">{estadoDeFundador(usuario.founder)}</span>
        )}
      </td>
      <td className="px-3 py-3 text-text-secondary">
        <span className="block max-w-[190px] truncate" title={usuario.email ?? undefined}>
          {usuario.email}
        </span>
      </td>
      <td className="px-3 py-3 text-text-secondary">{usuario.whatsappPhone ?? "—"}</td>
      <td className="px-3 py-3">
        <Badge
          tono={
            usuario.role === "PROFESSOR" ? "lavanda" : usuario.role === "ADMIN" ? "coral" : "menta"
          }
        >
          {ETIQUETA_ROL[usuario.role ?? ""] ?? usuario.role}
        </Badge>
      </td>
      <td className="px-3 py-3">
        <Badge tono={activo ? "menta" : "melocoton"}>{activo ? "Activo" : "Inactivo"}</Badge>
      </td>
      <td className="whitespace-nowrap px-3 py-3 text-right">
        <div className="flex items-center justify-end gap-2">
          <Boton
            variante="contorno"
            disabled={cambiarEstado.isPending || usuario.role === "ADMIN"}
            onClick={() => cambiarEstado.mutate()}
            className="h-9 px-3"
          >
            {activo ? "Inactivar" : "Activar"}
          </Boton>
          {usuario.role === "PROFESSOR" && <BotonTarifa profesorId={usuario.id!} />}
          {usuario.role === "PROFESSOR" && <BotonFundador profesorId={usuario.id!} esFundador={!!usuario.founder} />}
          {/* Inactivar oculta; borrar destruye. Son cosas distintas y por eso conviven. */}
          <BotonPurga tipo="user" id={usuario.id!} etiqueta="Borrar" />
        </div>
      </td>
    </tr>
  );
}

/**
 * Otorga o quita el beneficio de profe fundador (brief del profe fundador, paso 2). Otorgarlo copia
 * la comisión y los meses de Ajustes; quitarlo solo cambia las reservas nuevas, y por eso pide una
 * segunda confirmación que lo dice.
 */
function BotonFundador({ profesorId, esFundador }: { profesorId: string; esFundador: boolean }) {
  const queryClient = useQueryClient();
  const [confirmando, setConfirmando] = useState(false);
  const cambiar = useMutation({
    mutationFn: () =>
      apiFetch<unknown>(`/api/v1/admin/professors/${profesorId}/founder`, { method: esFundador ? "DELETE" : "POST" }),
    onSuccess: () => {
      setConfirmando(false);
      void queryClient.invalidateQueries({ queryKey: ["admin", "users"] });
    },
  });

  if (esFundador && confirmando) {
    return (
      <span className="inline-flex items-center gap-2">
        <span className="text-[12px] text-text-secondary">Solo cambia las reservas nuevas.</span>
        <Boton variante="peligro" disabled={cambiar.isPending} onClick={() => cambiar.mutate()} className="h-9 px-3">
          Quitar
        </Boton>
        <Boton variante="fantasma" onClick={() => setConfirmando(false)} className="h-9 px-3">
          No
        </Boton>
      </span>
    );
  }
  return (
    <Boton
      variante="contorno"
      disabled={cambiar.isPending}
      onClick={() => (esFundador ? setConfirmando(true) : cambiar.mutate())}
      className="h-9 px-3"
    >
      {esFundador ? "Quitar fundador" : "Hacer fundador"}
    </Boton>
  );
}

/**
 * Fija la tarifa de un profesor desde administración. Existe por el 0: es el único sitio de la
 * aplicación desde el que se puede poner una clase en gratuita, y sirve para probar el flujo de
 * reserva entero —cupo, confirmación, correo, calendario— sin mover dinero por la pasarela. El
 * propio profesor no puede ponerlo: su formulario conserva el piso de $20.000.
 */
function BotonTarifa({ profesorId }: { profesorId: string }) {
  const [abierto, setAbierto] = useState(false);

  return (
    <>
      <Boton variante="contorno" onClick={() => setAbierto(true)} className="h-9 px-3">
        Tarifa
      </Boton>
      {abierto && <ModalTarifa profesorId={profesorId} onCerrar={() => setAbierto(false)} />}
    </>
  );
}

function ModalTarifa({ profesorId, onCerrar }: { profesorId: string; onCerrar: () => void }) {
  const queryClient = useQueryClient();
  const [valor, setValor] = useState("");

  const guardar = useMutation({
    mutationFn: (hourlyRateCop: number) =>
      apiFetch(`/api/v1/admin/professors/${profesorId}/rate`, {
        method: "PUT",
        body: { hourlyRateCop },
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["professors"] });
      onCerrar();
    },
  });

  const numero = Number(valor.replace(/\D/g, ""));
  const valido = valor.trim() !== "" && (numero === 0 || (numero >= 20000 && numero <= 500000));
  const error = guardar.error instanceof ApiError ? guardar.error.message : null;

  return (
    <Modal titulo="Tarifa por hora" onCerrar={onCerrar}>
      <p className="text-[13px] text-text-secondary">
        Entre $20.000 y $500.000. Escribe <strong>0</strong> para dejar las clases de este profesor
        en gratuitas: se reservan sin pasar por Wompi.
      </p>
      <Campo
        type="text"
        inputMode="numeric"
        autoFocus
        placeholder="0"
        value={valor}
        onChange={(event) => setValor(event.target.value)}
        className="mt-3"
      />
      {valor.trim() !== "" && !valido && (
        <p className="mt-1.5 text-[12px] font-semibold text-error">
          Debe ser 0, o un valor entre 20.000 y 500.000.
        </p>
      )}
      {numero === 0 && valor.trim() !== "" && (
        <p className="mt-1.5 text-[12px] font-semibold text-warning">
          Sus clases quedarán gratuitas y el profesor no recibirá nada por ellas.
        </p>
      )}
      {error && (
        <div className="mt-3">
          <AvisoError mensaje={error} />
        </div>
      )}
      <div className="mt-5 flex gap-2.5">
        <Boton variante="contorno" onClick={onCerrar} className="h-10 flex-1">
          Cancelar
        </Boton>
        <Boton
          disabled={!valido || guardar.isPending}
          onClick={() => guardar.mutate(numero)}
          className="h-10 flex-1"
        >
          {guardar.isPending ? <Spinner /> : "Guardar"}
        </Boton>
      </div>
    </Modal>
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
