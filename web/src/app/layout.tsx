import type { Metadata, Viewport } from 'next';
import { AuthProvider } from '@/context/AuthContext';
import './globals.css';

export const metadata: Metadata = {
  title: 'EntrevistaMais — agenda de entrevistas para recrutadores',
  description:
    'Agenda de entrevistas que calcula os horários sozinha: candidatos, vagas, currículos, roteiro de perguntas e decisões no mesmo lugar. No app e no site, com a mesma conta.',
  applicationName: 'EntrevistaMais',
  manifest: '/manifest.webmanifest',
};

export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
  themeColor: '#1f6feb',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="pt-BR">
      <body>
        <AuthProvider>{children}</AuthProvider>
      </body>
    </html>
  );
}
