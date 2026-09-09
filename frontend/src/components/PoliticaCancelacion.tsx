"use client";

/**
 * La política de cancelación, escrita donde la persona puede leerla antes de necesitarla.
 *
 * <p>Vivía solo en los Términos y en el diálogo de cancelar — es decir, o en un documento que
 * nadie abre, o cinco segundos antes de decidir. Aquí está en la cuenta de las dos partes, porque
 * es la regla que más dinero mueve y la que más discusiones ahorra cuando se conoce de antemano.
 *
 * <p>La frontera son 12 horas y es la misma para los dos lados, pero lo que hay al otro lado no lo
 * es: el estudiante se juega el dinero de su clase y el profesor, su reputación en la plataforma.
 */
export function PoliticaCancelacion({ rol }: { rol: "estudiante" | "profesor" }) {
  const filas =
    rol === "estudiante"
      ? [
          {
            cuando: "Más de 12 horas antes",
            que: "Recuperas el valor completo. Va a tu saldo a favor y queda disponible enseguida; si estás dentro del plazo de retracto, puedes pedir que vuelva al medio de pago que usaste.",
            tono: "bien" as const,
          },
          {
            cuando: "Menos de 12 horas antes",
            que: "No hay devolución. Tu profesor ya apartó esa hora y no puede darla a nadie más, así que la clase se considera prestada.",
            tono: "ojo" as const,
          },
          {
            cuando: "No te conectas",
            que: "Igual que cancelar tarde: la clase se cobra completa. Si el problema fue del profesor, repórtalo desde la clase y lo revisamos.",
            tono: "ojo" as const,
          },
        ]
      : [
          {
            cuando: "Más de 12 horas antes",
            que: "Sin consecuencias para ti. Tu estudiante recupera el valor completo como saldo y tú no cobras esa clase.",
            tono: "bien" as const,
          },
          {
            cuando: "Menos de 12 horas antes",
            que: "Tu estudiante recupera todo igual y tú no cobras. Además queda registrado: las cancelaciones de último momento repetidas alimentan la escalera de sanciones.",
            tono: "ojo" as const,
          },
          {
            cuando: "No te presentas",
            que: "El estudiante puede reportarlo. Si la revisión le da la razón, recupera su dinero y se te registra una ausencia, que es lo que pesa de verdad en tu perfil.",
            tono: "ojo" as const,
          },
        ];

  return (
    <section className="mt-8">
      <h2 className="font-display text-[19px] font-bold">Si hay que cancelar</h2>
      <p className="mt-1 text-[13px] leading-relaxed text-text-secondary">
        {rol === "estudiante"
          ? "Cancelar siempre se puede, a cualquier hora. Lo que cambia según cuándo lo hagas es qué pasa con tu dinero."
          : "Cancelar siempre se puede, a cualquier hora. Lo que cambia según cuándo lo hagas es la consecuencia para ti."}
      </p>

      <ul className="mt-3 grid gap-2">
        {filas.map((fila) => (
          <li
            key={fila.cuando}
            className={`rounded-card border-l-[3px] p-4 ${
              fila.tono === "bien"
                ? "border-success bg-success-bg"
                : "border-warning bg-warning-bg"
            }`}
          >
            <p
              className={`text-[13.5px] font-bold ${
                fila.tono === "bien" ? "text-success" : "text-warning"
              }`}
            >
              {fila.cuando}
            </p>
            <p
              className={`mt-1 text-[12.5px] leading-relaxed ${
                fila.tono === "bien" ? "text-success" : "text-warning"
              }`}
            >
              {fila.que}
            </p>
          </li>
        ))}
      </ul>

      <p className="mt-2.5 text-[12px] leading-relaxed text-text-muted">
        Reprogramar no es cancelar: proponer otro horario se puede incluso dentro de las 12 horas y
        no cuesta nada, porque la clase sigue en pie hasta que la otra persona acepte.
      </p>
    </section>
  );
}
