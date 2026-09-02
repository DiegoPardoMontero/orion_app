import type { Metadata } from "next";
import { NavPublica } from "@/components/NavPublica";
import { serverFetch } from "@/lib/api/server";
import type { LanguageResponse } from "@/lib/api/types";
import { SITE_URL } from "@/lib/config";
import { IdiomaLanding } from "./IdiomaLanding";

/**
 * Landing pública por idioma. Server component (SEO): resuelve el nombre del idioma en el servidor
 * para los metadatos y el JSON-LD, y delega el contenido interactivo a la isla cliente
 * `IdiomaLanding`. Ruta dinámica sin `generateStaticParams`: se renderiza bajo demanda, así que un
 * backend caído en build no rompe nada.
 */
async function resolverIdioma(code: string): Promise<LanguageResponse | undefined> {
  const codigo = code.toUpperCase();
  const languages = await serverFetch<LanguageResponse[]>("/api/v1/catalog/languages");
  return languages?.find((l) => (l.code ?? "").toUpperCase() === codigo);
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ code: string }>;
}): Promise<Metadata> {
  const { code } = await params;
  const codigo = code.toUpperCase();
  const idioma = await resolverIdioma(codigo);
  const nombre = idioma?.nameEs ?? "idiomas";
  const title = `Clases de ${nombre} con profesores reales · Orión`;
  const description = `Aprende ${nombre} con profesores reales en Orión. Clases en vivo, a tu ritmo. Elige a tu profesor, mira sus horarios y reserva cuando quieras.`;

  return {
    title,
    description,
    alternates: { canonical: `/idiomas/${codigo}` },
    openGraph: {
      title,
      description,
      type: "website",
      images: [{ url: "/og.png", width: 1200, height: 630, alt: `Clases de ${nombre} en Orión` }],
    },
  };
}

export default async function IdiomaPage({ params }: { params: Promise<{ code: string }> }) {
  const { code } = await params;
  const codigo = code.toUpperCase();
  const idioma = await resolverIdioma(codigo);
  const nombre = idioma?.nameEs ?? "idiomas";

  return (
    <>
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{
          __html: JSON.stringify({
            "@context": "https://schema.org",
            "@type": "Course",
            name: `Clases de ${nombre}`,
            description: `Clases en vivo de ${nombre} con profesores reales en Orión.`,
            inLanguage: "es-CO",
            url: `${SITE_URL}/idiomas/${codigo}`,
            provider: {
              "@type": "Organization",
              name: "Orión Idiomas",
              url: SITE_URL,
            },
          }),
        }}
      />
      <NavPublica />
      <IdiomaLanding codigo={codigo} />
    </>
  );
}
