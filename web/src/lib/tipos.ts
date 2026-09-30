/**
 * Tipos do domínio, espelhados do aplicativo Android.
 *
 * O app grava no Room e o site grava no Firestore. Para os dois enxergarem a
 * mesma informação, os nomes dos campos precisam ser idênticos: o que o app
 * salva em `inicioMinutos`, o site lê em `inicioMinutos`.
 */

export const StatusEntrevista = {
  AGENDADA: 'AGENDADA',
  EM_ANDAMENTO: 'EM_ANDAMENTO',
  CONCLUIDA: 'CONCLUIDA',
  CANCELADA: 'CANCELADA',
  NAO_COMPARECEU: 'NAO_COMPARECEU',
  APROVADO: 'APROVADO',
  REPROVADO: 'REPROVADO',
  ENCERRADA: 'ENCERRADA',
} as const;

export type StatusEntrevista = (typeof StatusEntrevista)[keyof typeof StatusEntrevista];

export const ROTULO_STATUS: Record<StatusEntrevista, string> = {
  AGENDADA: 'Agendada',
  EM_ANDAMENTO: 'Em andamento',
  CONCLUIDA: 'Concluída',
  CANCELADA: 'Cancelada',
  NAO_COMPARECEU: 'Não compareceu',
  APROVADO: 'Aprovado',
  REPROVADO: 'Reprovado',
  ENCERRADA: 'Encerrada',
};

/** Status que já contam como resposta do recrutador. */
export const DECIDIDOS: StatusEntrevista[] = [
  StatusEntrevista.CONCLUIDA,
  StatusEntrevista.APROVADO,
  StatusEntrevista.REPROVADO,
  StatusEntrevista.ENCERRADA,
];

/**
 * Status a partir dos quais a agenda para de esperar resposta.
 *
 * Um candidato decidido não está mais na mesa: o horário dele volta a
 * ficar livre para o próximo da fila.
 */
export const ENCERRAVEIS: StatusEntrevista[] = [
  StatusEntrevista.CONCLUIDA,
  StatusEntrevista.APROVADO,
  StatusEntrevista.REPROVADO,
];

/** Depois de qualquer um destes, a entrevista vira registro imutável. */
export const FINALIZADOS: StatusEntrevista[] = [
  StatusEntrevista.CONCLUIDA,
  StatusEntrevista.APROVADO,
  StatusEntrevista.REPROVADO,
  StatusEntrevista.ENCERRADA,
];

export const ehFinalizado = (status: StatusEntrevista) => FINALIZADOS.includes(status);

export const TipoResposta = {
  TEXTO: 'TEXTO',
  TEXTO_LONGO: 'TEXTO_LONGO',
  NUMERO: 'NUMERO',
  SIM_NAO: 'SIM_NAO',
  SECAO: 'SECAO',
  TABELA_EXPERIENCIAS: 'TABELA_EXPERIENCIAS',
} as const;

export type TipoResposta = (typeof TipoResposta)[keyof typeof TipoResposta];

export const ROTULO_TIPO: Record<TipoResposta, string> = {
  TEXTO: 'Texto',
  TEXTO_LONGO: 'Texto longo',
  NUMERO: 'Número',
  SIM_NAO: 'Sim / Não',
  SECAO: 'Seção',
  TABELA_EXPERIENCIAS: 'Locais onde trabalhou',
};

export const RespostaAutomatica = {
  NENHUMA: 'NENHUMA',
  DIA_E_HORA: 'DIA_E_HORA',
  NOME_CANDIDATO: 'NOME_CANDIDATO',
  TELEFONE_CANDIDATO: 'TELEFONE_CANDIDATO',
  DADOS_DA_VAGA: 'DADOS_DA_VAGA',
} as const;

export type RespostaAutomatica = (typeof RespostaAutomatica)[keyof typeof RespostaAutomatica];

export const ROTULO_AUTOMATICA: Record<RespostaAutomatica, string> = {
  NENHUMA: 'Automático',
  DIA_E_HORA: 'Dia e hora',
  NOME_CANDIDATO: 'Nome do candidato',
  TELEFONE_CANDIDATO: 'Telefone do candidato',
  DADOS_DA_VAGA: 'Dados da vaga',
};

export const TipoCurriculo = {
  RESUMO: 'RESUMO',
  PDF: 'PDF',
  IMAGEM: 'IMAGEM',
} as const;

export type TipoCurriculo = (typeof TipoCurriculo)[keyof typeof TipoCurriculo];

export interface Pergunta {
  id: string;
  roteiroId: string;
  titulo: string;
  tipo: TipoResposta;
  ordem: number;
  dica: string;
  respostaAutomatica: RespostaAutomatica;
}

export interface Roteiro {
  id: string;
  titulo: string;
  conteudo: string;
  ordem: number;
  padrao: boolean;
  /** Vazio no roteiro padrão do processo; preenchido no roteiro de uma vaga. */
  vagaId: string | null;
}

export interface Entrevista {
  id: string;
  nome: string;
  telefone: string;
  data: string;
  inicioMinutos: number;
  duracaoMinutos: number;
  status: StatusEntrevista;
  ordem: number;
  vagaId: string | null;
  inicioManual: boolean;
  curriculo: string;
  tipoCurriculo: TipoCurriculo;
  caminhoCurriculo: string;
  nomeArquivoCurriculo: string;
  criadoEm: number;
}

export interface Vaga {
  id: string;
  titulo: string;
  empresa: string;
  tipoContrato: string;
  horarioTrabalho: string;
  salarioBeneficios: string;
  tempoExperiencia: string;
  escolaridade: string;
  exigeHabilitacao: string;
  resumoAtividades: string;
  limiteCandidatos: number;
  criadoEm: number;
}

export interface LinhaExperiencia {
  id: string;
  entrevistaId: string;
  local: string;
  ano: string;
  duracao: string;
  cargo: string;
  motivoSaida: string;
  ordem: number;
}

export interface Definicoes {
  /** Horário de início do expediente, em minutos desde a meia-noite. */
  horarioInicioMinutos: number;
  almocoInicioMinutos: number;
  almocoFimMinutos: number;
  quantidadePorDia: number;
  duracaoMinutos: number;
  intervaloMinutos: number;
}
