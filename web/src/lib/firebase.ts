import { initializeApp, getApps, type FirebaseApp } from 'firebase/app';
import { getAuth, type Auth } from 'firebase/auth';
import { getFirestore, type Firestore } from 'firebase/firestore';

/**
 * Configuração do Firebase.
 *
 * Os valores vêm do arquivo `.env.local`, preenchido com o projeto criado no
 * Firebase Console. Nada de chave fixa aqui: o repositório pode ser público
 * sem vazar segredo.
 *
 * As chaves de configuração de um app web NÃO são secretas — o segredo fica
 * nas regras de segurança do Firestore.
 */

export const firebaseConfig = {
  apiKey: process.env.NEXT_PUBLIC_FIREBASE_API_KEY ?? '',
  authDomain: process.env.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN ?? '',
  projectId: process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID ?? '',
  messagingSenderId: process.env.NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID ?? '',
  appId: process.env.NEXT_PUBLIC_FIREBASE_APP_ID ?? '',
};

/** Configuração mínima exigida para o site funcionar. */
export function firebaseConfigurado(): boolean {
  return Boolean(
    firebaseConfig.apiKey &&
      firebaseConfig.authDomain &&
      firebaseConfig.projectId &&
      firebaseConfig.appId,
  );
}

// Inicialização Preguiçosa: sem o `.env.local` preenchido o site ainda sobe
// (o login mostra o aviso), e o build/prerender do Next.js não quebra por
// falta de chave. As funções abaixo só podem ser chamadas do navegador.
let appRef: FirebaseApp | null = null;
let authRef: Auth | null = null;
let dbRef: Firestore | null = null;

export function obterApp(): FirebaseApp {
  if (!appRef) {
    if (!firebaseConfigurado()) {
      throw new Error('Configuração do Firebase ausente. Preencha as variáveis NEXT_PUBLIC_FIREBASE_* (veja .env.local.example).');
    }
    appRef = getApps()[0] ?? initializeApp(firebaseConfig);
  }
  return appRef;
}

export function obterAuth(): Auth {
  if (!authRef) authRef = getAuth(obterApp());
  return authRef;
}

export function obterDb(): Firestore {
  if (!dbRef) dbRef = getFirestore(obterApp());
  return dbRef;
}