import {
  collection,
  doc,
  addDoc,
  setDoc,
  updateDoc,
  deleteDoc,
  getDoc,
  getDocs,
  onSnapshot,
  query,
  orderBy,
  runTransaction,
  Timestamp,
  type DocumentSnapshot,
} from 'firebase/firestore';
import { obterDb } from './firebase';
import type {
  Entrevista,
  Vaga,
  Roteiro,
  Pergunta,
  Definicoes,
  LinhaExperiencia,
} from './tipos';
import { numero } from './horario';
import { calcularHorarios } from './agenda';

/**
 * Acesso ao Firestore.
 *
 * Estrutura: toda informação fica em um subconjunto do usuário (UID), então
 * duas contas nunca se enxergam:
 *   usuarios/{uid}/definicoes -> documento único
 *   usuarios/{uid}/vagas/*        -> uma vaga por documento
 *   usuarios/{uid}/entrevistas/*  -> uma entrevista por documento
 *   usuarios/{uid}/roteiros/*     -> roteiro padrão + um por vaga
 *   usuarios/{uid}/perguntas/*    -> perguntas de um roteiro
 *
 * É esta a forma que o aplicativo Android vai usar quando ligar o Firebase,
 * então o login e os dados ficam iguais no app e no site.
 */

const colecoes = (uid: string) => ({
  vagas: collection(obterDb(), 'usuarios', uid, 'vagas'),
  entrevistas: collection(obterDb(), 'usuarios', uid, 'entrevistas'),
  roteiros: collection(obterDb(), 'usuarios', uid, 'roteiros'),
  perguntas: (roteiroId: string) =>
    collection(obterDb(), 'usuarios', uid, 'roteiros', roteiroId, 'perguntas'),
  experiencias: (entrevistaId: string) =>
    collection(obterDb(), 'usuarios', uid, 'entrevistas', entrevistaId, 'experiencias'),
});

const documentos = (uid: string) => ({
  definicoes: doc(obterDb(), 'usuarios', uid, 'definicoes', 'padrao'),
  vaga: (id: string) => doc(obterDb(), 'usuarios', uid, 'vagas', id),
  entrevista: (id: string) => doc(obterDb(), 'usuarios', uid, 'entrevistas', id),
  roteiro: (id: string) => doc(obterDb(), 'usuarios', uid, 'roteiros', id),
});

/** As respostas ficam em um documento único por entrevista: {perguntaId: valor}. */
const respostasDoc = (uid: string, entrevistaId: string) =>
  doc(obterDb(), 'usuarios', uid, 'respostas', entrevistaId);

const lerDocumento = <T>(snap: DocumentSnapshot, padrao: Partial<T>): T => {
  const dados = snap.data();
  return { ...padrao, id: snap.id, ...dados } as T;
};

/* ---------------- Definições ---------------- */

export const DEFINICOES_PADRAO: Definicoes = {
  horarioInicioMinutos: 9 * 60,
  almocoInicioMinutos: 12 * 60,
  almocoFimMinutos: 13 * 60,
  quantidadePorDia: 6,
  duracaoMinutos: 45,
  intervaloMinutos: 0,
};

export const ROTEIRO_PADRAO_INICIAL = [
  { titulo: 'Apresentação profissional', tipo: 'TEXTO_LONGO', dica: 'Peça um resumo da trajetória.', respostaAutomatica: 'NENHUMA' },
  { titulo: 'Por que se candidatou a esta vaga?', tipo: 'TEXTO_LONGO', dica: '', respostaAutomatica: 'NENHUMA' },
  { titulo: 'Experiências anteriores', tipo: 'TABELA_EXPERIENCIAS', dica: '', respostaAutomatica: 'NENHUMA' },
] as const;

function lerDefinicoesLidas(dados: Record<string, unknown>): Definicoes {
  return {
    horarioInicioMinutos: numero(dados.horarioInicioMinutos),
    almocoInicioMinutos: numero(dados.almocoInicioMinutos),
    almocoFimMinutos: numero(dados.almocoFimMinutos),
    quantidadePorDia: numero(dados.quantidadePorDia),
    duracaoMinutos: numero(dados.duracaoMinutos),
    intervaloMinutos: numero(dados.intervaloMinutos),
  };
}

export function observarDefinicoes(
  uid: string,
  aoAtualizar: (definicoes: Definicoes) => void,
): () => void {
  return onSnapshot(documentos(uid).definicoes, (snap) => {
    const dados = snap.data();
    if (!dados) {
      aoAtualizar(DEFINICOES_PADRAO);
      return;
    }
    aoAtualizar(lerDefinicoesLidas(dados));
  });
}

export async function salvarDefinicoes(uid: string, valores: Definicoes): Promise<void> {
  await setDoc(documentos(uid).definicoes, {
    ...valores,
    atualizadoEm: Timestamp.now(),
  });
}

async function lerDefinicoes(uid: string): Promise<Definicoes> {
  const snap = await getDoc(documentos(uid).definicoes);
  const dados = snap.data();
  if (!dados) return DEFINICOES_PADRAO;
  return lerDefinicoesLidas(dados);
}

/* ---------------- Vagas ---------------- */

function vagaDoDocumento(snap: DocumentSnapshot): Vaga {
  return lerDocumento<Vaga>(snap, {
    id: '',
    titulo: '',
    empresa: '',
    tipoContrato: '',
    horarioTrabalho: '',
    salarioBeneficios: '',
    tempoExperiencia: '',
    escolaridade: '',
    exigeHabilitacao: '',
    resumoAtividades: '',
    limiteCandidatos: 0,
    criadoEm: 0,
  });
}

export function observarVagas(uid: string, aoAtualizar: (vagas: Vaga[]) => void): () => void {
  return onSnapshot(
    query(colecoes(uid).vagas, orderBy('criadoEm', 'asc')),
    (snap) => aoAtualizar(snap.docs.map(vagaDoDocumento)),
  );
}

export async function criarVaga(uid: string, vaga: Omit<Vaga, 'id' | 'criadoEm'>): Promise<string> {
  const ref = await addDoc(colecoes(uid).vagas, {
    ...vaga,
    criadoEm: Timestamp.now(),
  });
  return ref.id;
}

export async function atualizarVaga(uid: string, id: string, vaga: Omit<Vaga, 'id' | 'criadoEm'>): Promise<void> {
  await setDoc(documentos(uid).vaga(id), { ...vaga, criadoEm: Timestamp.now() });
}

export async function removerVaga(uid: string, id: string): Promise<void> {
  await deleteDoc(documentos(uid).vaga(id));
}

/** Quantidade de entrevistas já vinculadas, por vaga (para o limite). */
export async function contarCandidatos(uid: string): Promise<Record<string, number>> {
  const consulta = await getDocs(colecoes(uid).entrevistas);
  const mapa: Record<string, number> = {};
  consulta.docs.forEach((snap) => {
    const vagaId = snap.data().vagaId;
    if (vagaId) mapa[vagaId] = (mapa[vagaId] ?? 0) + 1;
  });
  return mapa;
}

/* ---------------- Entrevistas ---------------- */

function entrevistaDoDocumento(snap: DocumentSnapshot): Entrevista {
  return lerDocumento<Entrevista>(snap, {
    id: '',
    nome: '',
    telefone: '',
    data: '',
    inicioMinutos: 0,
    duracaoMinutos: 45,
    status: 'AGENDADA',
    ordem: 0,
    vagaId: null,
    inicioManual: false,
    curriculo: '',
    tipoCurriculo: 'RESUMO',
    caminhoCurriculo: '',
    nomeArquivoCurriculo: '',
    criadoEm: 0,
  });
}

export function observarEntrevistas(
  uid: string,
  data: string,
  aoAtualizar: (entrevistas: Entrevista[]) => void,
): () => void {
  return onSnapshot(colecoes(uid).entrevistas, (snap) => {
    const todas = snap.docs.map(entrevistaDoDocumento);
    aoAtualizar(todas.filter((e) => e.data === data).sort((a, b) => a.ordem - b.ordem));
  });
}

export async function adicionarEntrevista(
  uid: string,
  dados: Pick<Entrevista, 'nome' | 'telefone' | 'data' | 'vagaId' | 'duracaoMinutos'>,
): Promise<void> {
  const definicoes = await lerDefinicoes(uid);
  const consulta = await getDocs(colecoes(uid).entrevistas);

  const lista = consulta.docs
    .map(entrevistaDoDocumento)
    .filter((e) => e.data === dados.data)
    .sort((a, b) => a.ordem - b.ordem);

  const calculado = calcularHorarios(
    {
      inicioMinutos: definicoes.horarioInicioMinutos,
      almocoInicioMinutos: definicoes.almocoInicioMinutos,
      almocoFimMinutos: definicoes.almocoFimMinutos,
      intervaloMinutos: definicoes.intervaloMinutos,
      duracaoMinutos: definicoes.duracaoMinutos,
      quantidadePorDia: definicoes.quantidadePorDia,
    },
    [...lista, { id: 'novo', nome: dados.nome }],
  );

  const inicio = calculado.find((c) => c.candidato.id === 'novo');
  await addDoc(colecoes(uid).entrevistas, {
    ...dados,
    duracaoMinutos: inicio?.duracaoMinutos ?? dados.duracaoMinutos,
    inicioMinutos: inicio?.inicioMinutos ?? 0,
    ordem: lista.length,
    status: 'AGENDADA',
    inicioManual: false,
    curriculo: '',
    tipoCurriculo: 'RESUMO',
    criadoEm: Timestamp.now(),
  });
}

export function observarEntrevista(
  uid: string,
  id: string,
  aoAtualizar: (entrevista: Entrevista | null) => void,
): () => void {
  return onSnapshot(documentos(uid).entrevista(id), (snap) => {
    aoAtualizar(snap.exists() ? entrevistaDoDocumento(snap) : null);
  });
}

export async function atualizarStatus(
  uid: string,
  id: string,
  status: Entrevista['status'],
): Promise<void> {
  await updateDoc(documentos(uid).entrevista(id), { status });
}

export async function moverEntrevista(uid: string, id: string, direcao: -1 | 1): Promise<void> {
  runTransaction(obterDb(), async (transacao) => {
    const consulta = await getDocs(query(colecoes(uid).entrevistas, orderBy('ordem', 'asc')));
    const todas = consulta.docs.map(entrevistaDoDocumento);
    const indice = todas.findIndex((e) => e.id === id);
    if (indice < 0) return;

    const alvo = todas[indice + direcao];
    if (!alvo) return;

    transacao.update(documentos(uid).entrevista(id), { ordem: alvo.ordem });
    transacao.update(documentos(uid).entrevista(alvo.id), { ordem: todas[indice].ordem });
  });
}

export async function removerEntrevista(uid: string, id: string): Promise<void> {
  await deleteDoc(documentos(uid).entrevista(id));
}

export async function alterarHorario(
  uid: string,
  id: string,
  inicioMinutos: number,
): Promise<string | null> {
  const snapEntrevista = await getDoc(documentos(uid).entrevista(id));
  const entrevista = snapEntrevista.data();
  if (!entrevista) return 'Entrevista não encontrada.';

  const consulta = await getDocs(colecoes(uid).entrevistas);
  const mesmaData = consulta.docs
    .map(entrevistaDoDocumento)
    .filter((e) => e.data === entrevista.data && e.id !== id);
  const duracao = numero(entrevista.duracaoMinutos) || 45;

  const choca = mesmaData.find(
    (outra) =>
      inicioMinutos < outra.inicioMinutos + outra.duracaoMinutos &&
      outra.inicioMinutos < inicioMinutos + duracao,
  );

  await updateDoc(documentos(uid).entrevista(id), {
    inicioMinutos,
    inicioManual: true,
  });

  return choca ? `Horário alterado, mas choca com ${choca.nome}.` : null;
}

export async function voltarAoHorarioCalculado(uid: string, id: string): Promise<void> {
  await updateDoc(documentos(uid).entrevista(id), { inicioManual: false });
}

/* ---------------- Roteiro e perguntas ---------------- */

export function observarRoteiros(uid: string, aoAtualizar: (roteiros: Roteiro[]) => void): () => void {
  return onSnapshot(
    query(colecoes(uid).roteiros, orderBy('ordem', 'asc')),
    (snap) => aoAtualizar(snap.docs.map((doc) => lerDocumento<Roteiro>(doc, {
      id: '', titulo: '', conteudo: '', ordem: 0, padrao: true, vagaId: null,
    }))),
  );
}

export function observarPerguntas(
  uid: string,
  roteiroId: string,
  aoAtualizar: (perguntas: Pergunta[]) => void,
): () => void {
  return onSnapshot(
    query(colecoes(uid).perguntas(roteiroId), orderBy('ordem', 'asc')),
    (snap) => aoAtualizar(snap.docs.map((doc) => lerDocumento<Pergunta>(doc, {
      id: '', roteiroId, titulo: '', tipo: 'TEXTO', ordem: 0, dica: '', respostaAutomatica: 'NENHUMA',
    }))),
  );
}

export async function salvarRoteiro(
  uid: string,
  roteiro: Omit<Roteiro, 'id'>,
  perguntas: Omit<Pergunta, 'id' | 'roteiroId' | 'ordem'>[],
  id?: string,
): Promise<string> {
  // Criação ou atualização do documento do roteiro.
  const roteiroId = id ?? (await addDoc(colecoes(uid).roteiros, roteiro)).id;
  await setDoc(documentos(uid).roteiro(roteiroId), roteiro);

  // Apaga as perguntas antigas e reescreve na ordem atual.
  const existentes = await getDocs(colecoes(uid).perguntas(roteiroId));
  for (const antiga of existentes.docs) {
    await deleteDoc(antiga.ref);
  }
  for (const [indice, pergunta] of perguntas.entries()) {
    await setDoc(doc(colecoes(uid).perguntas(roteiroId)), {
      ...pergunta,
      ordem: indice,
    });
  }
  return roteiroId;
}

export async function apagarRoteiro(uid: string, id: string): Promise<void> {
  await deleteDoc(documentos(uid).roteiro(id));
}

export function observarRespostas(
  uid: string,
  entrevistaId: string,
  aoAtualizar: (respostas: Record<string, string>) => void,
): () => void {
  // Um documento por entrevista com `{perguntaId: valor}`. Ler de uma vez é
  // mais barato que um alerta por pergunta.
  return onSnapshot(respostasDoc(uid, entrevistaId), (snap) => {
    const dados = snap.data();
    const mapa: Record<string, string> = {};
    if (dados) {
      for (const [perguntaId, valor] of Object.entries(dados)) {
        if (typeof valor === 'string') mapa[perguntaId] = valor;
      }
    }
    aoAtualizar(mapa);
  });
}

export async function salvarResposta(
  uid: string,
  entrevistaId: string,
  perguntaId: string,
  valor: string,
): Promise<void> {
  await setDoc(respostasDoc(uid, entrevistaId), { [perguntaId]: valor }, { merge: true });
}

export async function salvarExperiencia(
  uid: string,
  entrevistaId: string,
  linha: Omit<LinhaExperiencia, 'id' | 'entrevistaId'>,
): Promise<void> {
  await addDoc(colecoes(uid).experiencias(entrevistaId), linha);
}