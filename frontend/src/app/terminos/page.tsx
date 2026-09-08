import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { DocumentoLegal, type DocumentoLegalData } from "@/components/DocumentoLegal";
import { NavPublica } from "@/components/NavPublica";
import { serverFetch } from "@/lib/api/server";

export const metadata: Metadata = {
  title: "Términos y condiciones · Orión",
  description:
    "Las condiciones de uso de Orión: qué es la plataforma, cómo funciona una clase, precios, cancelaciones y tu derecho de retracto.",
  alternates: { canonical: "/terminos" },
};

/**
 * Server component y pública: el art. 50 de la Ley 1480 de 2011 exige que las condiciones estén
 * disponibles ANTES de contratar, y un documento detrás de un fetch de cliente o de un login llega
 * tarde. El texto viene de la base, no del código: así, cuando alguien pregunte qué aceptó en su
 * momento, la respuesta puede ser el texto de su momento y no el que esté desplegado hoy.
 *
 * <p>Se pide con revalidación 0 —es decir, en cada petición y nunca en el build— a propósito. Con
 * ISR, un despliegue hecho sin el backend arriba (que es lo normal en Railway) prerenderizaría un
 * 404 y lo serviría durante los primeros minutos. Una página que la ley obliga a tener disponible
 * no puede empezar rota y curarse sola.
 */
export default async function TerminosPage() {
  const doc = await serverFetch<DocumentoLegalData>("/api/v1/legal/TERMS", 0);
  if (!doc) notFound();

  return (
    <>
      <NavPublica />
      <main className="bg-surface">
        <DocumentoLegal doc={doc} />
      </main>
    </>
  );
}
