import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { DocumentoLegal, type DocumentoLegalData } from "@/components/DocumentoLegal";
import { NavPublica } from "@/components/NavPublica";
import { serverFetch } from "@/lib/api/server";

export const metadata: Metadata = {
  title: "Acuerdo del profesor · Orión",
  description:
    "Lo que acepta quien enseña en Orión: cómo se trabaja con los estudiantes, la comisión y el mandato con el que Orión recibe y entrega el dinero de sus clases.",
  alternates: { canonical: "/acuerdo-del-profesor" },
};

/**
 * Pública, como los Términos: quien se postula tiene que poder leerla antes de crear la cuenta, y el
 * profe, volver a ella cuando quiera. Revalidación 0 por la misma razón que `/terminos`.
 */
export default async function AcuerdoDelProfesorPage() {
  const doc = await serverFetch<DocumentoLegalData>("/api/v1/legal/TEACHER_AGREEMENT", 0);
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
