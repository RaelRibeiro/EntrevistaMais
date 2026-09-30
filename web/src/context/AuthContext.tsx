'use client';

import {
  createContext,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from 'react';
import {
  onAuthStateChanged,
  createUserWithEmailAndPassword,
  signInWithEmailAndPassword,
  signInWithPopup,
  GoogleAuthProvider,
  signOut,
  updateProfile,
  type User,
} from 'firebase/auth';
import { doc, setDoc, Timestamp } from 'firebase/firestore';
import { obterAuth, obterDb } from '@/lib/firebase';

interface AuthDados {
  usuario: User | null;
  carregando: boolean;
  entrarEmail: (email: string, senha: string) => Promise<void>;
  entrarGoogle: () => Promise<void>;
  criarConta: (nome: string, email: string, senha: string) => Promise<void>;
  sair: () => Promise<void>;
}

const AuthContext = createContext<AuthDados | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [usuario, setUsuario] = useState<User | null>(null);
  const [carregando, setCarregando] = useState(true);

  useEffect(() => {
    const desativar = onAuthStateChanged(obterAuth(), (user) => {
      setUsuario(user);
      setCarregando(false);
    });
    return desativar;
  }, []);

  const entrarEmail = async (email: string, senha: string) => {
    await signInWithEmailAndPassword(obterAuth(), email, senha);
  };

  const entrarGoogle = async () => {
    const provider = new GoogleAuthProvider();
    await signInWithPopup(obterAuth(), provider);
  };

  const criarConta = async (nome: string, email: string, senha: string) => {
    const credencial = await createUserWithEmailAndPassword(obterAuth(), email, senha);
    await updateProfile(credencial.user, { displayName: nome });
  };

  const sair = async () => {
    await signOut(obterAuth());
  };

  return (
    <AuthContext.Provider
      value={{ usuario, carregando, entrarEmail, entrarGoogle, criarConta, sair }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthDados {
  const contexto = useContext(AuthContext);
  if (!contexto) throw new Error('useAuth precisa estar dentro de <AuthProvider>.');
  return contexto;
}

/** Grava o perfil do usuário no Firestore quando ele entra/cria a conta. */
export function guardarPerfil(uid: string, nome: string, email: string): Promise<void> {
  return setDoc(doc(obterDb(), 'usuarios', uid), { nome, email, criadoEm: Timestamp.now() });
}