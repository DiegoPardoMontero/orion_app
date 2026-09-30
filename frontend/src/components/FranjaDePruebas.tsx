import { ES_PRUEBAS } from "@/lib/config";

/**
 * La franja del ambiente de pruebas (UAT). Sin ella, una pestaña de pruebas y una de Orión real se
 * ven idénticas, y lo que en una es de mentira (una clase, un pago de sandbox) en la otra es de
 * verdad. Va en el flujo de la página y no fija, para no tapar las cabeceras que sí lo son.
 */
export function FranjaDePruebas() {
  if (!ES_PRUEBAS) return null;
  return (
    <div
      role="note"
      className="bg-[repeating-linear-gradient(135deg,#FFE3A3_0_12px,#FFD27A_12px_24px)] px-4 py-1.5 text-center text-[12px] font-bold text-[#5c3d00]"
    >
      Ambiente de pruebas: nada de lo que pase aquí es real, y los pagos son de mentira.
    </div>
  );
}
