'use client';

import { useEffect, useState } from 'react';

export function useMobile(): boolean {
  const [mobile, setMobile] = useState<boolean | null>(null);

  useEffect(() => {
    const calcular = () => {
      const fina = window.matchMedia('(max-width: 600px)').matches;

      const toque = window.matchMedia('(pointer: coarse)').matches;
      setMobile(fina && toque);
    };
    calcular();
    window.addEventListener('resize', calcular);
    return () => window.removeEventListener('resize', calcular);
  }, []);

  return mobile === true;
}

export const URL_APK =
  process.env.NEXT_PUBLIC_APK_URL ?? '';
