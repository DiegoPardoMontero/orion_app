/**
 * Una casilla de consentimiento del alta: nace desmarcada y va sola, nunca empaquetada con otra
 * (Decreto 1377 de 2013: juntar la autorización de datos con los términos la viciaría). La usan el
 * registro con correo y el de quien llega por Google, Apple o Facebook.
 */
/**
 * Una casilla de consentimiento. Área de toque completa —la etiqueta también activa— y el foco
 * visible: es el único punto del registro donde marcar por error tiene consecuencias legales,
 * así que tiene que ser deliberado y tiene que verse.
 */
export function Consentimiento({
  id,
  marcado,
  onCambio,
  children,
}: {
  id: string;
  marcado: boolean;
  onCambio: (valor: boolean) => void;
  children: React.ReactNode;
}) {
  return (
    <label htmlFor={id} className="flex cursor-pointer items-start gap-3 text-[13px] leading-relaxed text-text-secondary">
      <input
        id={id}
        type="checkbox"
        checked={marcado}
        onChange={(event) => onCambio(event.target.checked)}
        className="mt-[3px] h-[18px] w-[18px] shrink-0 cursor-pointer accent-primary focus-visible:shadow-focus"
      />
      <span>{children}</span>
    </label>
  );
}
