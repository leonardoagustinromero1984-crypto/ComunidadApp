import type { Metadata } from "next";
import { Inter } from "next/font/google";

import { getAppUrl } from "@/lib/env";

import "./globals.css";

const inter = Inter({
  subsets: ["latin"],
  variable: "--font-inter",
});

export const metadata: Metadata = {
  metadataBase: new URL(getAppUrl()),
  title: {
    default: "LeoVer",
    template: "%s | LeoVer",
  },
  description: "Conectamos mascotas, personas y comunidad.",
  openGraph: {
    siteName: "LeoVer",
    locale: "es_AR",
    type: "website",
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="es">
      <body className={`${inter.variable} antialiased`}>{children}</body>
    </html>
  );
}
