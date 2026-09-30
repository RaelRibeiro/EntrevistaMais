import type { MetadataRoute } from 'next';

/**
 * Manifest PWA. No celular com Chrome, o site vira instalável e o sistema
 * oferece "Adicionar à tela inicial" — o caminho que o recrutador usa para
 * chegar ao app do dia a dia sem depender do navegador.
 */
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: 'Entrevistador',
    short_name: 'Entrevistador',
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