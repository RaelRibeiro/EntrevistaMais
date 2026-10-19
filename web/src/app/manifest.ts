import type { MetadataRoute } from 'next';

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: 'EntrevistaMais',
    short_name: 'EntrevistaMais',
    description: 'Agenda de entrevistas para recrutadores.',
    start_url: '/',
    display: 'standalone',
    background_color: '#f6f7f9',
    theme_color: '#1f6feb',
    lang: 'pt-BR',
    icons: [
      {
        src: '/icone-192.png',
        sizes: '192x192',
        type: 'image/png',
      },
      {
        src: '/icone-512.png',
        sizes: '512x512',
        type: 'image/png',
      },
    ],
  };
}
