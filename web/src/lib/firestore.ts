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
import {
  ref,
  uploadBytes,
  getDownloadURL,
  deleteObject,
  type StorageReference,
} from 'firebase/storage';
import { obterDb, obterStorage } from './firebase';
import type {
  Entrevista,
  Vaga,
  Roteiro,
  Pergunta,
  Definicoes,
  LinhaExperiencia,
  TipoCurriculo,
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

/** Perguntas padrão do processo — as mesmas 29 que o aplicativo cria no 1º login. */
export const ROTEIRO_PADRAO_INICIAL = [
  { titulo: 'Identificação', tipo: 'SECAO', dica: '', respostaAutomatica: 'NENHUMA' },
  { titulo: 'Dia e Horário', tipo: 'TEXTO', dica: '', respostaAutomatica: 'DIA_E_HORA' },
  { titulo: 'Nome', tipo: 'TEXTO', dica: '', respostaAutomatica: 'NOME_CANDIDATO' },
  { titulo: 'Onde viu a vaga', tipo: 'TEXTO', dica: 'Indicação, site de vagas, indicação de amigo...', respostaAutomatica: 'NENHUMA' },
  { titulo: 'Dados pessoais', tipo: 'SECAO', dica: '', respostaAutomatica: 'NENHUMA' },
  { titulo: 'Idade', tipo: 'NUMERO', dica: '', respostaAutomatica: 'NENHUMA' },
  { titulo: 'Escolaridade', tipo: 'TEXTO', dica: '', respostaAutomatica: 'NENHUMA' },
  { titulo: 'Tem habilitação?', tipo: 'SIM_NAO', dica: '', respostaAutomatica: 'NENHUMA' },
  { titulo: 'É casado?', tipo: 'SIM_NAO', dica: '', respostaAutomatica: 'NENHUMA' },
  { titulo: 'Qual a profissão do marido/esposa?', tipo: 'TEXTO', dica: '', respostaAutomatica: 'NENHUMA' },
  { titulo: 'Tem filhos?', tipo: 'SIM_NAO', dica: '', respostaAutomatica: 'NENHUMA' },
  { titulo: 'Quantos filhos?', tipo: 'NUMERO', dica: '', respostaAutomatica: 'NENHUMA' },
  { titulo: 'Moradia e família', tipo: 'SECAO', dica: '', respostaAutomatica: 'NENHUMA' },
  { titulo: 'É daqui de Barbacena mesmo?', tipo: 'SIM_NAO', dica: '', respostaAutomatica: 'NENHUMA' },
  { titulo: 'Mora em qual bairro?', tipo: 'TEXTO', dica: '', respostaAutomatica: 'NENHUMA' },
  { titulo: 'Com quem mora atualmente?', tipo: 'TEXTO_LONGO', dica: '', respostaAutomatica: 'NENHUMA' },
  { titulo: 'E a relação familiar é boa?', tipo: 'SIM_NAO', dica: '', respostaAutomatica: 'NENHUMA' },
  { titulo: 'Saúde', tipo: 'SECAO', dica: '', respostaAutomatica: 'NENHUMA' },
  { titulo: 'Faz o uso de alguma medicação, bebida ou cigarro?', tipo: 'TEXTO_LONGO', dica: '', respostaAutomatica: 'NENHUMA' },
  { titulo: 'Já esteve internado(a) nos últimos anos?', tipo: 'SIM_NAO', dica: '', respostaAutomatica: 'NENHUMA' },
  { titulo: 'Está trabalhando em algo atualmente?', tipo: 'SIM_NAO', dica: '', respostaAutomatica: 'NENHUMA' },
  { titulo: 'Relacionamento interpessoal', tipo: 'SECAO', dica: '', respostaAutomatica: 'NENHUMA' },
  { titulo: 'Relacionamento com a equipe (interpessoal)', tipo: 'TEXTO_LONGO', dica: '', respostaAutomatica: 'NENHUMA' },
  { titulo: 'Remuneração', tipo: 'SECAO', dica: '', respostaAutomatica: 'NENHUMA' },
  { titulo: 'Qual seu último salário? Qual sua pretensão salarial?', tipo: 'TEXTO_LONGO', dica: '', respostaAutomatica: 'NENHUMA' },
  { titulo: 'Experiência profissional', tipo: 'SECAO', dica: '', respostaAutomatica: 'NENHUMA' },
  { titulo: 'Gostaria que você falasse agora dos locais onde trabalhou, por quanto tempo e o porquê de ter saído', tipo: 'TABELA_EXPERIENCIAS', dica: '', respostaAutomatica: 'NENHUMA' },
  { titulo: 'Informações da vaga', tipo: 'SECAO', dica: '', respostaAutomatica: 'NENHUMA' },
  { titulo: 'Horário de trabalho, salário e benefícios, tempo de experiência, escolaridade, habilitação e atividades principais', tipo: 'TEXTO_LONGO', dica: '', respostaAutomatica: 'DADOS_DA_VAGA' },
] as const;

/**
 * Cria o roteiro padrão (com as perguntas acima) na primeira vez em que o
 * usuário entra — igual ao aplicativo. Não faz nada se já existir um.
 */
export async function garantirRoteiroPadrao(uid: string): Promise<void> {
  const consulta = await getDocs(query(colecoes(uid).roteiros));
  if (consulta.docs.some((d) => d.data().padrao === true)) return;
  const roteiroRef = await addDoc(colecoes(uid).roteiros, {
    titulo: 'Roteiro de Entrevista',
    conteudo: 'Roteiro de entrevista padrão',
    ordem: 0,
    padrao: true,
    vagaId: null,
  });
  for (const [indice, pergunta] of ROTEIRO_PADRAO_INICIAL.entries()) {
    await setDoc(doc(colecoes(uid).perguntas(roteiroRef.id)), {
      ...pergunta,
      ordem: indice,
    });
  }
}

/* ---------------- Currículo (Storage) ---------------- */

const caminhoCurriculoDe = (uid: string, arquivo: File): string => {
  const limpo = arquivo.name.replace(/[^\p{L}\p{N}._-]/gu, '_');
  const base = limpo.replace(/\.[^.]+$/, '');
  const ext = arquivo.name.match(/\.[^.]+$/)?.[0]?.toLowerCase() ?? '';
  return `usuarios/${uid}/curriculos/${Date.now()}_${base}${ext}`;
};

const tipoCurriculoDe = (arquivo: File): TipoCurriculo => {
  const nome = arquivo.name.toLowerCase();
  return nome.endsWith('.pdf') ? 'PDF' : 'IMAGEM';
};

/** Envia o arquivo para o Storage e devolve o caminho e o tipo gravados. */
export async function anexarCurriculo(
  uid: string,
  arquivo: File,
): Promise<{ caminhoCurriculo: string; tipoCurriculo: TipoCurriculo }> {
  const caminho = caminhoCurriculoDe(uid, arquivo);
  const referencia: StorageReference = ref(obterStorage(), caminho);
  await uploadBytes(referencia, arquivo);
  return { caminhoCurriculo: caminho, tipoCurriculo: tipoCurriculoDe(arquivo) };
}

/** URL pública de download de um arquivo já gravado no Storage. */
export async function urlDoCurriculo(caminho: string): Promise<string> {
  return getDownloadURL(ref(obterStorage(), caminho));
}

/** Apaga o arquivo do Storage (usado ao remover o candidato ou trocar o anexo). */
export async function apagarCurriculoArquivo(caminho: string): Promise<void> {
  try {
    await deleteObject(ref(obterStorage(), caminho));
  } catch {
    // Se a regra/arquivo não existir, o dado Firestore continua valendo.
  }
}

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
    entradaMinutos: NaN,
    saidaMinutos: NaN,
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
  await setDoc(documentos(uid).vaga(id), vaga, { merge: true });
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
    inicioReal: null,
    fimReal: null,
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
  dados: Pick<Entrevista, 'nome' | 'telefone' | 'data' | 'vagaId' | 'duracaoMinutos'> & {
    curriculo?: string;
    tipoCurriculo?: TipoCurriculo;
    caminhoCurriculo?: string;
    nomeArquivoCurriculo?: string;
  },
): Promise<void> {
  const definicoes = await lerDefinicoes(uid);
  const consulta = await getDocs(colecoes(uid).entrevistas);

  const lista = consulta.docs
    .map(entrevistaDoDocumento)
    .filter((e) => e.data === dados.data)
    .sort((a, b) => a.ordem - b.ordem);

  if (lista.length >= definicoes.quantidadePorDia) {
    throw new Error(`O dia já tem ${definicoes.quantidadePorDia} entrevistas, que é o limite definido.`);
  }

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
    curriculo: dados.curriculo ?? '',
    tipoCurriculo: dados.tipoCurriculo ?? 'RESUMO',
    caminhoCurriculo: dados.caminhoCurriculo ?? '',
    nomeArquivoCurriculo: dados.nomeArquivoCurriculo ?? '',
    criadoEm: Timestamp.now(),
  });
}

/** Atualiza campos pontuais de uma entrevista (currículo, status, horários…). */
export async function atualizarEntrevista(
  uid: string,
  id: string,
  campos: Partial<Pick<Entrevista, 'status' | 'inicioReal' | 'fimReal' | 'curriculo' | 'tipoCurriculo' | 'caminhoCurriculo' | 'nomeArquivoCurriculo' | 'inicioManual' | 'inicioMinutos'>>,
): Promise<void> {
  await updateDoc(documentos(uid).entrevista(id), campos);
}

/**
 * Recalcula os horários de um dia inteiro seguindo as Definições, respeitando
 * pausa do almoço e os candidatos cujo horário foi alterado à mão ou que já
 * estão em andamento. Igual ao botão "Recalcular" do aplicativo.
 */
export async function recalcularHorariosDoDia(uid: string, data: string): Promise<void> {
  const definicoes = await lerDefinicoes(uid);
  const consulta = await getDocs(colecoes(uid).entrevistas);

  const pendentes = consulta.docs
    .map(entrevistaDoDocumento)
    .filter((e) => e.data === data && e.status !== 'ENCERRADA')
    .sort((a, b) => a.ordem - b.ordem);

  let cursor = definicoes.horarioInicioMinutos;
  const promessas: Promise<void>[] = [];
  for (const e of pendentes) {
    if (e.inicioManual || e.status === 'EM_ANDAMENTO') {
      cursor = e.inicioMinutos + e.duracaoMinutos + definicoes.intervaloMinutos;
      continue;
    }
    if (cursor < definicoes.almocoInicioMinutos && e.inicioMinutos >= definicoes.almocoInicioMinutos) {
      cursor = definicoes.almocoFimMinutos;
    }
    const duracao = e.duracaoMinutos || definicoes.duracaoMinutos;
    promessas.push(
      updateDoc(documentos(uid).entrevista(e.id), {
        inicioMinutos: cursor,
        duracaoMinutos: duracao,
      }),
    );
    cursor += duracao + definicoes.intervaloMinutos;
  }
  await Promise.all(promessas);
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
  const snap = await getDoc(documentos(uid).entrevista(id));
  const dados = snap.data();
  const caminho = dados?.caminhoCurriculo as string | undefined;
  if (caminho) void apagarCurriculoArquivo(caminho);

  const experiencias = await getDocs(colecoes(uid).experiencias(id));
  for (const linha of experiencias.docs) await deleteDoc(linha.ref);

  await deleteDoc(respostasDoc(uid, id));
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
  const perguntas = await getDocs(colecoes(uid).perguntas(id));
  for (const pergunta of perguntas.docs) await deleteDoc(pergunta.ref);
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

export function observarExperiencias(
  uid: string,
  entrevistaId: string,
  aoAtualizar: (linhas: LinhaExperiencia[]) => void,
): () => void {
  return onSnapshot(
    query(colecoes(uid).experiencias(entrevistaId), orderBy('ordem', 'asc')),
    (snap) =>
      aoAtualizar(
        snap.docs.map((doc) =>
          lerDocumento<LinhaExperiencia>(doc, {
            id: '',
            entrevistaId,
            local: '',
            ano: '',
            duracao: '',
            cargo: '',
            motivoSaida: '',
            ordem: 0,
          }),
        ),
      ),
  );
}

export async function atualizarExperiencia(
  uid: string,
  entrevistaId: string,
  id: string,
  campos: Partial<Omit<LinhaExperiencia, 'id' | 'entrevistaId'>>,
): Promise<void> {
  await updateDoc(doc(colecoes(uid).experiencias(entrevistaId), id), campos);
}

export async function removerExperiencia(
  uid: string,
  entrevistaId: string,
  id: string,
): Promise<void> {
  await deleteDoc(doc(colecoes(uid).experiencias(entrevistaId), id));
}