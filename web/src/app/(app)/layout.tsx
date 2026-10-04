'use client';

import { useEffect } from 'react';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useAuth } from '@/context/AuthContext';
import { useMobile, URL_APK } from '@/lib/useMobile';
import { garantirRoteiroPadrao } from '@/lib/firestore';

const ITENS = [
  { rota: '/agenda', rotulo: 'Agenda' },
  { rota: '/vagas', rotulo: 'Vagas' },
  { rota: '/definicoes', rotulo: 'Definições' },
  { rota: '/roteiro', rotulo: 'Roteiro' },
];

/**
 * Estrutura comum das telas que precisam de conta. Sem login, manda para a
 * página inicial (o guarda-redes de login vive aqui, e não em cada tela).
 */
export default function LayoutApp({ children }: { children: React.ReactNode }) {
  const { usuario, carregando, sair } = useAuth();
  const router = useRouter();
  const caminho = usePathname();
  const mobile = useMobile();

  useEffect(() => {
    if (!carregando && !usuario) router.replace('/');
  }, [carregando, usuario, router]);

  // Igual ao app: garante o roteiro padrão de 29 perguntas no primeiro login.
  useEffect(() => {
    if (usuario?.uid) void garantirRoteiroPadrao(usuario.uid);
  }, [usuario?.uid]);

  if (carregando || !usuario) {
    return <main className="pagina">Carregando…</main>;
  }

  return (
    <>
      <header className="cabecalho">
        <nav className="nav">
          <Link href="/agenda" className="marca">
            EntrevistaMais
          </Link>
          {ITENS.map((item) => (
            <Link
              key={item.rota}
              href={item.rota}
              className={caminho.startsWith(item.rota) ? 'ativo' : ''}
            >
              {item.rotulo}
            </Link>
          ))}
          <span className="espaco" />
          {mobile && URL_APK && (
            <a className="botao" href={URL_APK} download style={{ padding: '6px 12px', fontSize: 13 }}>
              ⬇️ App
            </a>
          )}
          <button
            className="icone-botao"
            title={usuario.email ?? 'Conta'}
            onClick={() => void sair().then(() => router.replace('/'))}
          >
            Sair
          </button>
        </nav>
      </header>
      <main className="pagina">{children}</main>
    </>
  );
}