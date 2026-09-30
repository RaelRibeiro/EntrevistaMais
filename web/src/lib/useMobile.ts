'use client';

import { useEffect, useState } from 'react';

/**
 * Detecta se o visitante está no celular.
 *
 * Preferimos checagem de tela a user-agent: tablet vira "PC" (o site é como
 * o app, então funciona bem), e o botão de download fica apenas onde a
 * instalação faz sentido. Redimensionar para tablets pequenos recalcula.
 */
export function useMobile(): boolean {
  const [mobile, setMobile] = useState<boolean | null>(null);

  useEffect(() => {
    const calcular = () => {
      const fina = window.matchMedia('(max-width: 600px)').matches;
      // Touch é o indicador mais confiável de celular real.
      const toque = window.matchMedia('(pointer: coarse)').matches;
      setMobile(fina && toque);
    };
    calcular();
    window.addEventListener('resize', calcular);
    return () => window.removeEventListener('resize', calcular);
  }, []);

  return mobile === true;
}

/**
 * Link do APK para o botão de download.
 *
 * Troque por uma URL pública (Firebase Hosting, Google Drive como público,
 * GitHub Releases). Enquanto não houver, o botão mostra apenas o aviso.
 */
export const URL_APK =
  process.env.NEXT_PUBLIC_APK_URL ?? '';