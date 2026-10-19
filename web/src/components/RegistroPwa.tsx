'use client';

import { useEffect } from 'react';

export function RegistroPwa() {
  useEffect(() => {
    if (typeof window === 'undefined') return;
    if (!('serviceWorker' in navigator)) return;

    const registrar = () => {
      navigator.serviceWorker.register('/sw.js').catch(() => {
        // offline não é obrigatório; se falhar, o site segue normal
      });
    };

    if (document.readyState === 'complete') {
      registrar();
    } else {
      window.addEventListener('load', registrar, { once: true });
    }

    return () => window.removeEventListener('load', registrar);
  }, []);

  return null;
}
