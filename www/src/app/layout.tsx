import type { Metadata } from "next";
import "./globals.css";
import { Providers } from "@/components/providers";
import { EnvironmentGate } from "@/components/environment-setup";


export const metadata: Metadata = {
  title: "RelayForge — Webhook Console",
  description: "Event routing and delivery observability for RelayForge.",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en" className="h-full antialiased">
      <body className="min-h-full flex flex-col"><Providers><EnvironmentGate>{children}</EnvironmentGate></Providers></body>
    </html>
  );
}
