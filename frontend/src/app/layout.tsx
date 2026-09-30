import type { Metadata, Viewport } from "next";
import { Bricolage_Grotesque, Figtree } from "next/font/google";
import { FranjaDePruebas } from "@/components/FranjaDePruebas";
import { InstalarApp } from "@/components/InstalarApp";
import { ES_PRUEBAS } from "@/lib/config";
import "./globals.css";
import { Providers } from "./providers";

/**
 * next/font descarga y sirve las fuentes desde nuestro origen: sin petición a Google en tiempo
 * de ejecución y sin salto de tipografía al cargar. Dos familias del sistema v2:
 *   · Bricolage Grotesque — display: heros, títulos, cifras.
 *   · Figtree — sans: texto corrido, controles, etiquetas.
 */
const bricolage = Bricolage_Grotesque({
  subsets: ["latin"],
  weight: ["400", "600", "700", "800"],
  variable: "--font-bricolage",
  display: "swap",
});

const figtree = Figtree({
  subsets: ["latin"],
  weight: ["400", "500", "600", "700", "800"],
  variable: "--font-figtree",
  display: "swap",
});

export const metadata: Metadata = {
  metadataBase: new URL(process.env.NEXT_PUBLIC_SITE_URL || "https://orionidiomas.com"),
  title: "Orión Language Academy",
  description: "Find your right teacher, learn your way.",
  manifest: "/manifest.webmanifest",
  appleWebApp: { capable: true, title: "Orión", statusBarStyle: "black-translucent" },
  // El ambiente de pruebas no se indexa (29/09/2026); robots.txt además lo cierra entero.
  ...(ES_PRUEBAS ? { robots: { index: false, follow: false } } : {}),
};

/** El theme-color tiñe la barra del navegador y la barra de estado en modo instalado. */
export const viewport: Viewport = {
  themeColor: "#2E1E4E",
  width: "device-width",
  initialScale: 1,
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html
      lang="es"
      className={`${bricolage.variable} ${figtree.variable} h-full antialiased`}
    >
      <body className="min-h-full flex flex-col">
        <FranjaDePruebas />
        <Providers>{children}</Providers>
        <InstalarApp />
      </body>
    </html>
  );
}
