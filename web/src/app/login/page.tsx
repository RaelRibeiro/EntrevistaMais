'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useAuth, guardarPerfil } from '@/context/AuthContext';
import { obterAuth } from '@/lib/firebase';

export default function PaginaLogin() {
  const { entrarEmail, entrarGoogle } = useAuth();
  const router = useRouter();
  const [email, setEmail] = useState('');
  const [senha, setSenha] = useState('');
  const [erro, setErro] = useState('');

  const aoEntrar = async (evento: React.FormEvent) => {
    evento.preventDefault();
    setErro('');
    try {
      await entrarEmail(email, senha);
      const atual = obterAuth().currentUser;
      if (atual?.uid) void guardarPerfil(atual.uid, atual.displayName ?? '', email);
      router.push('/agenda');
    } catch (e) {
      setErro(erroDe(e));
    }
  };

  return (
    <main className="login-caixa">
      <h1>Entrar</h1>
      <p className="subtitulo">Use a mesma conta do aplicativo.</p>

      <button
        className="botao"
        style={{ width: '100%', margin: '12px 0' }}
        onClick={async () => {
          try {
            await entrarGoogle();
            const atual = obterAuth().currentUser;
            if (atual?.uid) {
              void guardarPerfil(atual.uid, atual.displayName ?? '', atual.email ?? '');
            }
            router.push('/agenda');
          } catch (e) {
            setErro(erroDe(e));
          }
        }}
      >
        Entrar com Google
      </button>

      <div style={{ textAlign: 'center', color: 'var(--texto-suave)', margin: '6px 0' }}>ou</div>

      <form onSubmit={aoEntrar}>
        <input
          className="campo"
          type="email"
          placeholder="E-mail"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
        />
        <input
          className="campo"
          type="password"
          placeholder="Senha"
          value={senha}
          onChange={(e) => setSenha(e.target.value)}
          required
        />
        {erro && <p className="erro">{erro}</p>}
        <button className="botao" type="submit" style={{ width: '100%' }}>
          Entrar
        </button>
      </form>

      <p className="dica" style={{ textAlign: 'center', marginTop: 14 }}>
        Ainda não tem conta? <Link href="/cadastro">Crie aqui</Link> ·{' '}
        <Link href="/">Voltar</Link>
      </p>
    </main>
  );
}

function erroDe(e: unknown): string {
  const codigo = (e as { code?: string })?.code ?? '';
  const mensagens: Record<string, string> = {
    'auth/invalid-credential': 'E-mail ou senha incorretos.',
    'auth/user-not-found': 'Nenhuma conta com este e-mail.',
    'auth/wrong-password': 'Senha incorreta.',
    'auth/popup-closed-by-user': 'Login com Google cancelado.',
    'auth/popup-blocked': 'O navegador bloqueou a janela do Google. Libere pop-ups.',
  };
  return mensagens[codigo] ?? 'Não foi possível entrar. Tente novamente.';
}