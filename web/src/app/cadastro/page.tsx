'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useAuth, guardarPerfil } from '@/context/AuthContext';
import { obterAuth } from '@/lib/firebase';

export default function PaginaCadastro() {
  const { criarConta } = useAuth();
  const router = useRouter();
  const [nome, setNome] = useState('');
  const [email, setEmail] = useState('');
  const [senha, setSenha] = useState('');
  const [confirma, setConfirma] = useState('');
  const [erro, setErro] = useState('');

  const aoCadastrar = async (evento: React.FormEvent) => {
    evento.preventDefault();
    setErro('');
    if (senha.length < 6) {
      setErro('A senha precisa de pelo menos 6 caracteres.');
      return;
    }
    if (senha !== confirma) {
      setErro('As senhas não conferem.');
      return;
    }
    try {
      await criarConta(nome, email, senha);
      const atual = obterAuth().currentUser;
      if (atual?.uid) {
        await guardarPerfil(atual.uid, nome, email);
      }
      router.push('/agenda');
    } catch (e) {
      setErro(erroDe(e));
    }
  };

  return (
    <main className="login-caixa">
      <h1>Criar conta</h1>
      <p className="subtitulo">
        A mesma conta funciona no site e no aplicativo.
      </p>

      <form onSubmit={aoCadastrar}>
        <input
          className="campo"
          placeholder="Seu nome"
          value={nome}
          onChange={(e) => setNome(e.target.value)}
          required
        />
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
          placeholder="Senha (mínimo 6 caracteres)"
          value={senha}
          onChange={(e) => setSenha(e.target.value)}
          required
        />
        <input
          className="campo"
          type="password"
          placeholder="Repetir a senha"
          value={confirma}
          onChange={(e) => setConfirma(e.target.value)}
          required
        />
        {erro && <p className="erro">{erro}</p>}
        <button className="botao" type="submit" style={{ width: '100%' }}>
          Criar conta
        </button>
      </form>

      <p className="dica" style={{ textAlign: 'center', marginTop: 14 }}>
        Já tem conta? <Link href="/login">Entrar</Link> ·{' '}
        <Link href="/">Voltar</Link>
      </p>
    </main>
  );
}

function erroDe(e: unknown): string {
  const codigo = (e as { code?: string })?.code ?? '';
  const mensagens: Record<string, string> = {
    'auth/email-already-in-use': 'Este e-mail já tem conta.',
    'auth/invalid-email': 'E-mail inválido.',
    'auth/weak-password': 'Senha muito fraca (mínimo 6 caracteres).',
  };
  return mensagens[codigo] ?? 'Não foi possível criar a conta. Tente novamente.';
}