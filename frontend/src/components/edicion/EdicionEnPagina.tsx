"use client";

import { Check } from "lucide-react";
import { useRouter } from "next/navigation";
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useId,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { Modal } from "@/components/Modal";
import { Boton } from "@/components/ui";

/**
 * Editar un perfil sin «modo edición» (24/09/2026).
 *
 * <p>Antes cada formulario de perfil nacía cerrado y había que pulsar «Editar» —abajo del todo—
 * para poder escribir: Pardo mismo intentó corregir campos sin haber pasado por ahí. El modo lo
 * habíamos puesto para que no se cambiara nada sin querer y sin enterarse; la barra de abajo
 * resuelve eso mismo sin esconder nada: los campos se editan directo y, en cuanto algo cambia,
 * aparece fija «Tienes cambios sin guardar · Descartar · Guardar cambios», que no se va hasta
 * decidir. Salir de la página con cambios pregunta.
 *
 * <p>Una página puede tener varios formularios (la ficha del estudiante y sus datos de cuenta, por
 * ejemplo) y una sola barra: cada formulario se registra con {@link useFormularioEditable} y
 * «Guardar cambios» guarda los que cambiaron, uno detrás de otro.
 */

type Formulario = {
  sucio: boolean;
  guardar: () => Promise<void>;
  descartar: () => void;
};

type Registro = {
  registrar: (id: string, formulario: Formulario) => void;
  quitar: (id: string) => void;
};

const Contexto = createContext<Registro | null>(null);

/**
 * Registra un formulario en la barra de la página. {@code guardar} lanza si falla —un
 * `ApiError` con el mensaje del backend, o un `Error` con el de su propia validación—: la
 * barra lo muestra y los cambios siguen en pantalla.
 */
export function useFormularioEditable(sucio: boolean, guardar: () => Promise<void>, descartar: () => void) {
  const registro = useContext(Contexto);
  const id = useId();
  // Las funciones cambian en cada render; la barra siempre llama a las últimas.
  const guardarRef = useRef(guardar);
  const descartarRef = useRef(descartar);
  useEffect(() => {
    guardarRef.current = guardar;
    descartarRef.current = descartar;
  });

  useEffect(() => {
    registro?.registrar(id, {
      sucio,
      guardar: () => guardarRef.current(),
      descartar: () => descartarRef.current(),
    });
  }, [registro, id, sucio]);

  useEffect(() => () => registro?.quitar(id), [registro, id]);
}

export function EdicionEnPagina({ children }: { children: ReactNode }) {
  const [formularios, setFormularios] = useState<Record<string, Formulario>>({});
  const registro = useMemo<Registro>(
    () => ({
      registrar: (id, formulario) => setFormularios((prev) => ({ ...prev, [id]: formulario })),
      quitar: (id) =>
        setFormularios((prev) => {
          const { [id]: _quitado, ...resto } = prev;
          void _quitado;
          return resto;
        }),
    }),
    [],
  );

  const sucios = Object.values(formularios).filter((f) => f.sucio);
  const haySucios = sucios.length > 0;

  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [recienGuardado, setRecienGuardado] = useState(false);

  const guardar = useCallback(async (): Promise<boolean> => {
    setGuardando(true);
    setError(null);
    try {
      for (const f of sucios) await f.guardar();
      setRecienGuardado(true);
      setTimeout(() => setRecienGuardado(false), 2500);
      return true;
    } catch (e) {
      // Un ApiError trae el mensaje del backend; un Error propio, el de la validación del formulario.
      setError(e instanceof Error && e.message ? e.message : "No pudimos guardar. Inténtalo otra vez.");
      return false;
    } finally {
      setGuardando(false);
    }
  }, [sucios]);

  const descartar = () => {
    setError(null);
    for (const f of sucios) f.descartar();
  };

  const destino = useGuardaDeSalida(haySucios);

  return (
    <Contexto.Provider value={registro}>
      {children}

      {(haySucios || recienGuardado) && (
        <div
          role="region"
          aria-label="Cambios sin guardar"
          className="sticky bottom-[calc(80px+env(safe-area-inset-bottom))] z-20 mt-6 lg:bottom-5"
        >
          {haySucios ? (
            <div className="rounded-card bg-night px-4 py-3 text-on-primary shadow-lg">
              <div className="flex flex-col gap-2.5 sm:flex-row sm:items-center sm:gap-3">
                <p className="flex min-w-0 flex-1 items-center gap-2.5 text-[13.5px] font-semibold">
                  <span className="relative flex h-2.5 w-2.5 shrink-0" aria-hidden>
                    <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-accent-peach opacity-60" />
                    <span className="relative inline-flex h-2.5 w-2.5 rounded-full bg-accent-peach" />
                  </span>
                  Tienes cambios sin guardar
                </p>
                <div className="flex gap-2">
                  <button
                    type="button"
                    onClick={descartar}
                    disabled={guardando}
                    className="min-h-10 rounded-pill px-3.5 text-[13.5px] font-semibold text-on-primary/85 transition-colors hover:bg-white/10 hover:text-on-primary focus-visible:shadow-focus disabled:opacity-50"
                  >
                    Descartar
                  </button>
                  <Boton onClick={() => void guardar()} disabled={guardando} className="min-h-10 flex-1 px-4 text-[13.5px] sm:flex-none">
                    {guardando ? "Guardando…" : "Guardar cambios"}
                  </Boton>
                </div>
              </div>
              {error && (
                <p role="alert" className="mt-2 rounded-base bg-white/10 px-3 py-2 text-[12.5px] leading-snug text-on-primary">
                  {error}
                </p>
              )}
            </div>
          ) : (
            <p
              role="status"
              className="mx-auto flex w-fit items-center gap-2 rounded-pill bg-success px-4 py-2.5 text-[13.5px] font-semibold text-on-primary shadow-lg"
            >
              <Check size={16} strokeWidth={2.4} />
              Cambios guardados
            </p>
          )}
        </div>
      )}

      {destino.pendiente && (
        <Modal titulo="¿Salir sin guardar?" onCerrar={destino.cancelar}>
          <p className="text-[14px] leading-relaxed text-text-secondary">
            Cambiaste cosas que todavía no se han guardado. Si sales ahora, se pierden.
          </p>
          <div className="mt-5 grid gap-2.5 sm:grid-cols-2">
            <Boton
              disabled={guardando}
              onClick={async () => {
                if (await guardar()) destino.seguir();
                else destino.cancelar();
              }}
            >
              {guardando ? "Guardando…" : "Guardar y salir"}
            </Boton>
            <Boton
              variante="contorno"
              onClick={() => {
                descartar();
                destino.seguir();
              }}
            >
              Salir sin guardar
            </Boton>
          </div>
          <button
            type="button"
            onClick={destino.cancelar}
            className="mx-auto mt-3 block min-h-10 rounded-pill px-4 text-[13.5px] font-semibold text-text-secondary hover:text-text focus-visible:shadow-focus"
          >
            Seguir editando
          </button>
        </Modal>
      )}
    </Contexto.Provider>
  );
}

/**
 * Mientras haya cambios: cerrar o recargar la pestaña pregunta el navegador, y un enlace de la app
 * pregunta la página. El App Router no trae una guarda de navegación, así que los clics en enlaces
 * se atajan en la fase de captura, antes de que los vea el `Link` de Next.
 */
function useGuardaDeSalida(activa: boolean) {
  const router = useRouter();
  const [pendiente, setPendiente] = useState<string | null>(null);

  useEffect(() => {
    if (!activa) return;
    const alCerrar = (evento: BeforeUnloadEvent) => {
      evento.preventDefault();
      evento.returnValue = "";
    };
    const alClic = (evento: MouseEvent) => {
      if (evento.defaultPrevented || evento.button !== 0) return;
      if (evento.metaKey || evento.ctrlKey || evento.shiftKey || evento.altKey) return;
      const enlace = (evento.target as Element | null)?.closest?.("a[href]") as HTMLAnchorElement | null;
      if (!enlace || enlace.target === "_blank" || enlace.hasAttribute("download")) return;
      const url = new URL(enlace.href, window.location.href);
      if (url.origin !== window.location.origin) return;
      // Un ancla dentro de la misma página no es salir.
      if (url.pathname === window.location.pathname && url.search === window.location.search) return;
      evento.preventDefault();
      evento.stopPropagation();
      setPendiente(url.pathname + url.search + url.hash);
    };
    window.addEventListener("beforeunload", alCerrar);
    document.addEventListener("click", alClic, true);
    return () => {
      window.removeEventListener("beforeunload", alCerrar);
      document.removeEventListener("click", alClic, true);
    };
  }, [activa]);

  return {
    pendiente,
    cancelar: () => setPendiente(null),
    seguir: () => {
      const a = pendiente;
      setPendiente(null);
      if (a) router.push(a);
    },
  };
}
