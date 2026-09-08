import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { DocumentoLegal, type DocumentoLegalData } from "@/components/DocumentoLegal";
import { NavPublica } from "@/components/NavPublica";
import { serverFetch } from "@/lib/api/server";

export const metadata: Metadata = {
  title: "Política de tratamiento de datos · Orión",
  description:
    "Cómo trata Orión tus datos personales: qué recogemos, para qué, con quién los compartimos y cómo ejerces tus derechos como titular.",
  alternates: { canonical: "/privacidad" },
};

/**
 * La Política de tratamiento de la información, con las seis secciones que exige el art. 13 del
 * Decreto 1377 de 2013. Pública y server component por lo mismo que los términos: la autorización
 * de tratamiento tiene que ser INFORMADA, y no lo es si el texto no se puede leer antes de darla.
 */
export default async function PrivacidadPage() {
  const doc = await serverFetch<DocumentoLegalData>("/api/v1/legal/PRIVACY");
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
