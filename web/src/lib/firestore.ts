import {
  collection,
  doc,
  setDoc,
  updateDoc,
  deleteDoc,
  getDoc,
  getDocs,
  onSnapshot,
  query,
  orderBy,
  where,
  runTransaction,
  writeBatch,
  Timestamp,
  deleteField,
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
  TipoCurriculo,
} from './tipos';
import { TipoCurriculo as TipoCurriculoEnum, ehEncerravel } from './tipos';
import { numero } from './horario';
import { calcularHorarios, paraAgenda, MINUTOS_NO_DIA } from './agenda';

/**
 * Acesso ao Firestore — mesmo esquema que o aplicativo Android usa.
 *
 * Toda informação vive em um subconjunto do usuário (UID) e os identificadores
 * são numéricos: o documento tem id = `"12345"` e guarda o campo `id` como
 * número. O app faz `getLong("id")` e ignora documentos sem esse campo, então
 * tudo o que for escrito aqui precisa ter o `id` numérico gravado.
 *
 *   usuarios/{uid}/definicoes/padrao
 *   usuarios/{uid}/vagas/{id}
 *   usuarios/{uid}/entrevistas/{id}
 *   usuarios/{uid}/roteiros/{id}
 *   usuarios/{uid}/perguntas/{id}       -> campo roteiroId
 *   usuarios/{uid}/respostas/{ent-Id-perguntaId}  -> campo entrevistaId
 *   usuarios/{uid}/experiencias/{id}    -> campo entrevistaId
 */

const LONGO_EPOCA = 1704067200000;

/**
 * Mesmo gerador do app, mas mantendo o valor dentro de 2^53 (seguro para o
 * número do JavaScript). O app usa `(millis shl 20)` que passa do limite — aqui
 * usamos o mesmo deslocamento que o app passará a usar após o ajuste.
 */
function novoId(): number {
  return ((Date.now() - LONGO_EPOCA) * 4096 + Math.floor(Math.random() * 4096)) %
    Number.MAX_SAFE_INTEGER;
}

const colecoes = (uid: string) => ({
  vagas: collection(obterDb(), 'usuarios', uid, 'vagas'),
  entrevistas: collection(obterDb(), 'usuarios', uid, 'entrevistas'),
  roteiros: collection(obterDb(), 'usuarios', uid, 'roteiros'),
  perguntas: collection(obterDb(), 'usuarios', uid, 'perguntas'),
  respostas: collection(obterDb(), 'usuarios', uid, 'respostas'),
  experiencias: collection(obterDb(), 'usuarios', uid, 'experiencias'),
});

const documentos = (uid: string) => ({
  definicoes: doc(obterDb(), 'usuarios', uid, 'definicoes', 'padrao'),
  vaga: (id: string) => doc(obterDb(), 'usuarios', uid, 'vagas', id),
  entrevista: (id: string) => doc(obterDb(), 'usuarios', uid, 'entrevistas', id),
  roteiro: (id: string) => doc(obterDb(), 'usuarios', uid, 'roteiros', id),
});

/** Id igual ao do app: `entrevistaId-perguntaId` (string dos números). */
const idDeResposta = (entrevistaId: number, perguntaId: number): string =>
  `${entrevistaId}-${perguntaId}`;

// O `id` é sempre o id do documento (string). Dados do app também trazem um
// campo `id` numérico; ele NÃO pode sobrescrever o id que usamos para navegar.
const lerDocumento = <T>(snap: DocumentSnapshot, padrao: Partial<T>): T => {
  const dados = snap.data();
  return { ...padrao, ...dados, id: snap.id } as T;
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
 * Cria (ou conserta) o roteiro padrão com as perguntas acima — igual ao app.
 * Se o roteiro padrão antigo (sem campo `id` numérico) existir, ele ganha um
 * id numérico e as perguntas passam a ser gravadas na coleção plana do app.
 */
export async function garantirRoteiroPadrao(uid: string): Promise<void> {
  const docs = await getDocs(query(colecoes(uid).roteiros, where('padrao', '==', true)));
  const confiavel = docs.docs.find((d) => typeof d.data().id === 'number');
  if (confiavel) return;

  let roteiroId: number;
  const antigo = docs.docs[0];
  if (antigo) {
    roteiroId = novoId();
    await setDoc(documentos(uid).roteiro(String(roteiroId)), {
      ...antigo.data(),
      id: roteiroId,
      vagaId: null,
    });
    await deleteDoc(antigo.ref);
  } else {
    roteiroId = novoId();
    await setDoc(documentos(uid).roteiro(String(roteiroId)), {
      titulo: 'Roteiro de Entrevista',
      conteudo: 'Roteiro de entrevista padrão',
      ordem: 0,
      padrao: true,
      vagaId: null,
      id: roteiroId,
    });
  }

  const existentes = await getDocs(
    query(colecoes(uid).perguntas, where('roteiroId', '==', roteiroId)),
  );
  for (const antiga of existentes.docs) await deleteDoc(antiga.ref);

  for (const [indice, pergunta] of ROTEIRO_PADRAO_INICIAL.entries()) {
    const id = novoId();
    await setDoc(doc(colecoes(uid).perguntas, String(id)), {
      id,
      roteiroId,
      titulo: pergunta.titulo,
      tipo: pergunta.tipo,
      ordem: indice,
      dica: pergunta.dica,
      respostaAutomatica: pergunta.respostaAutomatica,
      obrigatoria: false,
    });
  }
}

/* ---------------- Currículo (Firestore) ---------------- */

/**
 * O currículo é gravado no Firestore, e não no Storage do Firebase: o Storage só
 * é liberado com faturamento ativo no projeto, o que exige cartão de crédito.
 * Como o uso é de poucos arquivos, guardamos no próprio Firestore, que já está
 * no nível gratuito.
 *
 * O Firestore aceita no máximo 1 MiB por documento, então o arquivo vira base64
 * (que infla o tamanho em 33%) e é dividido em pedaços gravados como documentos
 * irmãos em `partes/`. Na leitura os pedaços voltam, são concatenados e
 * decodificados de volta ao arquivo original.
 *
 * O custo: base64 ocupa um terço a mais de espaço, abrir o currículo lê mais de
 * um documento e a cota do Firestore se esgota mais rápido do que com um storage
 * de verdade. Em troca, nenhuma configuração de servidor e as regras de
 * segurança continuam isolando cada usuário na própria pasta.
 */
const TAMANHO_MAXIMO = 5 * 1024 * 1024;
/** 500 mil caracteres de base64 deixam folga larga dentro do limite de 1 MiB. */
const TAMANHO_PARTE = 500_000;
/**
 * Os pedaços são numerados com zeros à esquerda para que a ordem do Firestore
 * (que é alfabética) coincida com a ordem do arquivo. Assim os pedaços voltam
 * numa consulta só, em vez de uma leitura por pedaço.
 */
const nomeDaParte = (indice: number): string => String(indice).padStart(6, '0');

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

/** Converte bytes em base64 em pedaços, para não estourar a pilha do navegador. */
function paraBase64(bytes: Uint8Array): string {
  const pedacos = 0x8000;
  let binario = '';
  for (let i = 0; i < bytes.length; i += pedacos) {
    binario += String.fromCharCode(
      ...Array.from(bytes.subarray(i, i + pedacos)),
    );
  }
  return btoa(binario);
}

function deBase64(texto: string): Uint8Array<ArrayBuffer> {
  const binario = atob(texto);
  const bytes = new Uint8Array(new ArrayBuffer(binario.length));
  for (let i = 0; i < binario.length; i += 1) bytes[i] = binario.charCodeAt(i);
  return bytes;
}

const colecaoDeCurriculos = (uid: string, docId: string) =>
  doc(obterDb(), 'usuarios', uid, 'curriculos', docId);

/** Separa o endereço `usuarios/{uid}/curriculos/{docId}.{ext}` nas suas partes. */
function partesDoCaminho(caminho: string): { uid: string; docId: string } | null {
  const pedacos = caminho.split('/');
  if (pedacos.length < 4 || pedacos[0] !== 'usuarios') return null;
  return { uid: pedacos[1], docId: pedacos[3].replace(/\.[^.]+$/, '') };
}

/** Envia o arquivo para o Firestore e devolve o caminho e o tipo gravados. */
export async function anexarCurriculo(
  uid: string,
  arquivo: File,
): Promise<{ caminhoCurriculo: string; tipoCurriculo: TipoCurriculo }> {
  const caminho = caminhoCurriculoDe(uid, arquivo);
  const { docId } = partesDoCaminho(caminho)!;
  const bytes = new Uint8Array(await arquivo.arrayBuffer());
  if (bytes.length > TAMANHO_MAXIMO) {
    throw new Error(
      `Currículo maior que ${TAMANHO_MAXIMO / (1024 * 1024)} MB.`,
    );
  }

  const texto = paraBase64(bytes);
  const total = texto.length;
  const quantidade = total === 0 ? 1 : Math.ceil(total / TAMANHO_PARTE);

  // Um lote só: ou o currículo inteiro vai para o Firestore, ou nada dele fica
  // pela metade (um currículo pela metade não serve para nada).
  const documento = colecaoDeCurriculos(uid, docId);
  const lote = writeBatch(obterDb());
  lote.set(documento, {
    id: Date.now(),
    nome: arquivo.name,
    ext: arquivo.name.match(/\.[^.]+$/)?.[0]?.toLowerCase() ?? '',
    partes: quantidade,
    bytes: bytes.length,
  });
  for (let indice = 0; indice < quantidade; indice += 1) {
    lote.set(doc(documento, 'partes', nomeDaParte(indice)), {
      dados: texto.slice(indice * TAMANHO_PARTE, (indice + 1) * TAMANHO_PARTE),
    });
  }
  await lote.commit();

  return { caminhoCurriculo: caminho, tipoCurriculo: tipoCurriculoDe(arquivo) };
}

/**
 * Monta o arquivo a partir dos pedaços guardados e devolve uma URL temporária
 * para abrir no navegador. Quem chamou é responsável por revogar a URL.
 */
export async function urlDoCurriculo(caminho: string): Promise<string> {
  const partes = partesDoCaminho(caminho);
  if (!partes) throw new Error('Endereço de currículo inválido.');

  const referencia = colecaoDeCurriculos(partes.uid, partes.docId);
  const documento = await getDoc(referencia);
  if (!documento.exists()) throw new Error('Currículo não encontrado.');

  const peca = await getDocs(query(collection(referencia, 'partes')));
  const texto = peca.docs.map((item) => String(item.data().dados ?? '')).join('');

  const dados = documento.data();
  const nome = String(dados?.nome ?? partes.docId);
  const ext = String(dados?.ext ?? '').replace(/^\./, '');
  const tipo = ext === 'pdf' ? 'application/pdf' : `image/${ext || 'jpeg'}`;
  return URL.createObjectURL(new Blob([deBase64(texto)], { type: tipo }));
}

/** Apaga o currículo (usado ao remover o candidato ou trocar o anexo). */
export async function apagarCurriculoArquivo(caminho: string): Promise<void> {
  try {
    const partes = partesDoCaminho(caminho);
    if (!partes) return;
    const referencia = colecaoDeCurriculos(partes.uid, partes.docId);

    const peca = await getDocs(query(collection(referencia, 'partes')));
    const lote = writeBatch(obterDb());
    for (const item of peca.docs) lote.delete(item.ref);
    lote.delete(referencia);
    await lote.commit();
  } catch {
    // Se o documento não existir, o dado da entrevista continua valendo.
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
    entradaMinutos: -1,
    saidaMinutos: -1,
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
  // orderBy('criadoEm') esconderia vagas criadas no app (sem esse campo).
  return onSnapshot(
    query(colecoes(uid).vagas, orderBy('titulo', 'asc')),
    (snap) => aoAtualizar(snap.docs.map(vagaDoDocumento)),
  );
}

export async function criarVaga(uid: string, vaga: Omit<Vaga, 'id' | 'criadoEm'>): Promise<string> {
  const id = novoId();
  await setDoc(documentos(uid).vaga(String(id)), {
    ...vaga,
    id,
    ativa: true,
    criadoEm: Timestamp.now(),
  });
  return String(id);
}

export async function atualizarVaga(uid: string, id: string, vaga: Omit<Vaga, 'id' | 'criadoEm'>): Promise<void> {
  await setDoc(documentos(uid).vaga(id), { ...vaga, id: Number(id) }, { merge: true });
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
    if (typeof vagaId === 'number') {
      const chave = String(vagaId);
      mapa[chave] = (mapa[chave] ?? 0) + 1;
    }
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
  dados: Pick<Entrevista, 'nome' | 'telefone' | 'data' | 'vagaId'> & {
    curriculo?: string;
    tipoCurriculo?: TipoCurriculo;
    caminhoCurriculo?: string;
    nomeArquivoCurriculo?: string;
    /** Horário escolhido a dedo; nulo mantém o cálculo automático da agenda. */
    inicioMinutos?: number | null;
  },
): Promise<string> {
  const nome = dados.nome.trim();
  const telefone = dados.telefone.trim();
  const resumo = dados.curriculo?.trim() ?? '';
  const tipoCurriculo = dados.tipoCurriculo ?? TipoCurriculoEnum.RESUMO;

  // Mesmas mensagens e mesma ordem do app (`EntrevistaRepository.adicionar`).
  if (!nome) throw new Error('Informe o nome do candidato.');
  if (telefone && telefone.replace(/\D/g, '').length < 10) {
    throw new Error('Telefone incompleto — inclua o DDD.');
  }
  if (tipoCurriculo === TipoCurriculoEnum.RESUMO && !resumo) {
    throw new Error('Escreva um resumo do currículo ou anexe um PDF/imagem.');
  }
  if (tipoCurriculo !== TipoCurriculoEnum.RESUMO && !dados.caminhoCurriculo) {
    throw new Error('Anexe o arquivo do currículo.');
  }

  const definicoes = await lerDefinicoes(uid);
  const consulta = await getDocs(colecoes(uid).entrevistas);

  const lista = consulta.docs
    .map(entrevistaDoDocumento)
    .filter((e) => e.data === dados.data)
    .sort((a, b) => a.ordem - b.ordem);

  if (lista.length >= definicoes.quantidadePorDia) {
    throw new Error(`O dia já tem ${definicoes.quantidadePorDia} entrevistas, que é o limite definido.`);
  }

  // A duração é sempre a das Definições: muda o horário, não o tempo da
  // entrevista. Validado aqui para não gravar uma entrevista impossível.
  if (dados.inicioMinutos !== undefined && dados.inicioMinutos !== null) {
    if (dados.inicioMinutos < 0 || dados.inicioMinutos >= MINUTOS_NO_DIA) {
      throw new Error('Horário inválido.');
    }
    if (dados.inicioMinutos + definicoes.duracaoMinutos >= MINUTOS_NO_DIA) {
      throw new Error('A entrevista não cabe antes da meia-noite com esse horário.');
    }
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
    [...lista, { id: 'novo', nome }],
  );

  const inicio = calculado.find((c) => c.candidato.id === 'novo');
  const id = novoId();
  await setDoc(documentos(uid).entrevista(String(id)), {
    id,
    nome,
    telefone,
    data: dados.data,
    vagaId: dados.vagaId ?? null,
    duracaoMinutos: definicoes.duracaoMinutos,
    inicioMinutos: dados.inicioMinutos ?? inicio?.inicioMinutos ?? 0,
    ordem: lista.length,
    status: 'AGENDADA',
    inicioManual: dados.inicioMinutos !== undefined && dados.inicioMinutos !== null,
    curriculo: resumo,
    tipoCurriculo,
    caminhoCurriculo: dados.caminhoCurriculo ?? '',
    nomeArquivoCurriculo: dados.nomeArquivoCurriculo ?? '',
    criadoEm: Timestamp.now(),
  });
  return String(id);
}

/**
 * Horário que o próximo candidato receberia se fosse salvo agora.
 *
 * Um candidato fictício no fim da fila revela a hora, e `null` avisa que o dia
 * está lotado. Igual ao app (`EntrevistaRepository.proximoHorarioMinutos`).
 */
export async function proximoHorarioMinutos(
  uid: string,
  data: string,
): Promise<number | null> {
  const definicoes = await lerDefinicoes(uid);
  const consulta = await getDocs(colecoes(uid).entrevistas);
  const existentes = consulta.docs
    .map(entrevistaDoDocumento)
    .filter((e) => e.data === data)
    .sort((a, b) => a.ordem - b.ordem);

  const calculados = calcularHorarios(
    paraAgenda(definicoes),
    [...existentes.map((e) => ({ id: e.id, nome: e.nome })), { id: 'novo', nome: '' }],
  );
  return calculados.find((c) => c.candidato.id === 'novo')?.inicioMinutos ?? null;
}

/**
 * Encerra de uma vez todos os candidatos do dia que já foram avaliados.
 * Igual ao app (`EntrevistaRepository.encerrarAvaliadosDoDia`).
 */
export async function encerrarAvaliados(uid: string, data: string): Promise<number> {
  const consulta = await getDocs(colecoes(uid).entrevistas);
  const candidatos = consulta.docs
    .map(entrevistaDoDocumento)
    .filter((e) => e.data === data && ehEncerravel(e.status));
  if (candidatos.length === 0) return 0;
  await Promise.all(
    candidatos.map((e) =>
      updateDoc(documentos(uid).entrevista(e.id), { status: 'ENCERRADA' }),
    ),
  );
  return candidatos.length;
}

/** Devolve o candidato para o horário calculado pelas Definições. */
export async function voltarHorarioPadrao(uid: string, id: string): Promise<void> {
  await updateDoc(documentos(uid).entrevista(id), { inicioManual: false });
  await recalcularHorariosDoDia(uid, (await getDoc(documentos(uid).entrevista(id))).data()?.data as string);
}

/** Atualiza campos pontuais de uma entrevista (currículo, status, horários…). */
export async function atualizarEntrevista(
  uid: string,
  id: string,
  campos: Partial<
    Pick<
      Entrevista,
      | 'status'
      | 'inicioReal'
      | 'fimReal'
      | 'curriculo'
      | 'tipoCurriculo'
      | 'caminhoCurriculo'
      | 'nomeArquivoCurriculo'
      | 'inicioManual'
      | 'inicioMinutos'
    >
  >,
): Promise<void> {
  await updateDoc(documentos(uid).entrevista(id), campos);
}

/**
 * Reabre a entrevista: volta para AGENDADA e apaga o registro de horário real.
 *
 * Igual ao app (`EntrevistaRepository.reabrir`). Os campos precisam ser apagados
 * de verdade, e não gravados como `null`: o app limpa com "apagar", e deixar o
 * `inicioReal` para trás faria a duração real antiga reaparecer no resumo.
 */
export async function reabrirEntrevista(uid: string, id: string): Promise<void> {
  await updateDoc(documentos(uid).entrevista(id), {
    status: 'AGENDADA',
    inicioReal: deleteField(),
    fimReal: deleteField(),
  });
}

/**
 * Grava o resumo escrito à mão do currículo.
 *
 * O app força `tipoCurriculo` para RESUMO ao salvar o texto
 * (`EntrevistaRepository.salvarCurriculo`): sem o arquivo, é resumo por
 * definição, e deixar o tipo PDF por cima faria o app tentar abrir um arquivo
 * que já não existe.
 */
export async function salvarResumoCurriculo(
  uid: string,
  id: string,
  curriculo: string,
): Promise<void> {
  await atualizarEntrevista(uid, id, { curriculo, tipoCurriculo: 'RESUMO' });
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

  const entrevistaId = Number(id);
  const respostas = await getDocs(
    query(colecoes(uid).respostas, where('entrevistaId', '==', entrevistaId)),
  );
  for (const resposta of respostas.docs) await deleteDoc(resposta.ref);

  const experiencias = await getDocs(
    query(colecoes(uid).experiencias, where('entrevistaId', '==', entrevistaId)),
  );
  for (const linha of experiencias.docs) await deleteDoc(linha.ref);

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
    query(colecoes(uid).perguntas, where('roteiroId', '==', Number(roteiroId))),
    (snap) => aoAtualizar(snap.docs.map((doc) => lerDocumento<Pergunta>(doc, {
      id: '', roteiroId: Number(roteiroId), titulo: '', tipo: 'TEXTO', ordem: 0, dica: '', respostaAutomatica: 'NENHUMA',
    })).sort((a, b) => a.ordem - b.ordem)),
  );
}

export async function salvarRoteiro(
  uid: string,
  roteiro: Omit<Roteiro, 'id'>,
  perguntas: Omit<Pergunta, 'id' | 'roteiroId' | 'ordem'>[],
  id?: string,
): Promise<string> {
  const roteiroId = id ? Number(id) : novoId();
  const docId = id ?? String(roteiroId);
  await setDoc(documentos(uid).roteiro(docId), {
    ...roteiro,
    id: roteiroId,
    vagaId: roteiro.vagaId ?? null,
  });

  // Apaga as perguntas antigas deste roteiro e reescreve na ordem atual.
  const existentes = await getDocs(
    query(colecoes(uid).perguntas, where('roteiroId', '==', roteiroId)),
  );
  for (const antiga of existentes.docs) await deleteDoc(antiga.ref);

  for (const [indice, pergunta] of perguntas.entries()) {
    const idPergunta = novoId();
    await setDoc(doc(colecoes(uid).perguntas, String(idPergunta)), {
      id: idPergunta,
      roteiroId,
      titulo: pergunta.titulo,
      tipo: pergunta.tipo,
      dica: pergunta.dica,
      respostaAutomatica: pergunta.respostaAutomatica,
      ordem: indice,
      obrigatoria: false,
    });
  }
  return docId;
}

export async function apagarRoteiro(uid: string, id: string): Promise<void> {
  const perguntas = await getDocs(
    query(colecoes(uid).perguntas, where('roteiroId', '==', Number(id))),
  );
  for (const pergunta of perguntas.docs) await deleteDoc(pergunta.ref);
  await deleteDoc(documentos(uid).roteiro(id));
}

export function observarRespostas(
  uid: string,
  entrevistaId: string,
  aoAtualizar: (respostas: Record<string, string>) => void,
): () => void {
  // Uma linha por campo no app: coleção plana com documentos `ent-perguntaId`.
  return onSnapshot(
    query(colecoes(uid).respostas, where('entrevistaId', '==', Number(entrevistaId))),
    (snap) => {
      const mapa: Record<string, string> = {};
      for (const doc of snap.docs) {
        const dados = doc.data();
        const perguntaId = dados.perguntaId;
        const texto = dados.texto;
        if (typeof perguntaId === 'number' && typeof texto === 'string') {
          mapa[String(perguntaId)] = texto;
        }
      }
      aoAtualizar(mapa);
    },
  );
}

export async function salvarResposta(
  uid: string,
  entrevistaId: string,
  perguntaId: string,
  valor: string,
): Promise<void> {
  const nEntrevista = Number(entrevistaId);
  const nPergunta = Number(perguntaId);
  // Resposta em branco não é gravada, e a que existia é apagada: é o que o app
  // faz (`EntrevistaFormularioRepository.salvarResposta`). Gravar `texto: ''`
  // deixaria um documento que mente dizendo que a pergunta foi respondida.
  if (valor.trim() === '') {
    await deleteDoc(doc(colecoes(uid).respostas, idDeResposta(nEntrevista, nPergunta)));
    return;
  }
  await setDoc(doc(colecoes(uid).respostas, idDeResposta(nEntrevista, nPergunta)), {
    entrevistaId: nEntrevista,
    perguntaId: nPergunta,
    texto: valor,
  });
}

export async function salvarExperiencia(
  uid: string,
  entrevistaId: string,
  linha: Omit<LinhaExperiencia, 'id' | 'entrevistaId'>,
): Promise<void> {
  const id = novoId();
  await setDoc(doc(colecoes(uid).experiencias, String(id)), {
    id,
    entrevistaId: Number(entrevistaId),
    ...linha,
  });
}

export function observarExperiencias(
  uid: string,
  entrevistaId: string,
  aoAtualizar: (linhas: LinhaExperiencia[]) => void,
): () => void {
  return onSnapshot(
    query(colecoes(uid).experiencias, where('entrevistaId', '==', Number(entrevistaId))),
    (snap) =>
      aoAtualizar(
        snap.docs
          .map((doc) =>
            lerDocumento<LinhaExperiencia>(doc, {
              id: '',
              entrevistaId: Number(entrevistaId),
              local: '',
              ano: '',
              duracao: '',
              cargo: '',
              motivoSaida: '',
              ordem: 0,
            }),
          )
          .sort((a, b) => a.ordem - b.ordem),
      ),
  );
}

export async function atualizarExperiencia(
  uid: string,
  entrevistaId: string,
  id: string,
  campos: Partial<Omit<LinhaExperiencia, 'id' | 'entrevistaId'>>,
): Promise<void> {
  await updateDoc(doc(colecoes(uid).experiencias, id), campos);
}

export async function removerExperiencia(
  uid: string,
  entrevistaId: string,
  id: string,
): Promise<void> {
  await deleteDoc(doc(colecoes(uid).experiencias, id));
}