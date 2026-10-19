
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

export const ehDecidido = (status: StatusEntrevista) =>
  status === StatusEntrevista.APROVADO || status === StatusEntrevista.REPROVADO;

export const ehEncerravel = (status: StatusEntrevista) =>
  status === StatusEntrevista.CONCLUIDA || ehDecidido(status);

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

export const DECIDIDOS: StatusEntrevista[] = [
  StatusEntrevista.CONCLUIDA,
  StatusEntrevista.APROVADO,
  StatusEntrevista.REPROVADO,
  StatusEntrevista.ENCERRADA,
];

export const ENCERRAVEIS: StatusEntrevista[] = [
  StatusEntrevista.CONCLUIDA,
  StatusEntrevista.APROVADO,
  StatusEntrevista.REPROVADO,
];

export const FINALIZADOS: StatusEntrevista[] = [
  StatusEntrevista.CONCLUIDA,
  StatusEntrevista.APROVADO,
  StatusEntrevista.REPROVADO,
  StatusEntrevista.ENCERRADA,
];

export const ehFinalizado = (status: StatusEntrevista) =>
  status !== StatusEntrevista.AGENDADA && status !== StatusEntrevista.EM_ANDAMENTO;

export const TipoResposta = {
  TEXTO: 'TEXTO',
  TEXTO_LONGO: 'TEXTO_LONGO',
  NUMERO: 'NUMERO',
  SIM_NAO: 'SIM_NAO',
  DATA: 'DATA',
  AUTOMATICO: 'AUTOMATICO',
  SECAO: 'SECAO',
  TABELA_EXPERIENCIAS: 'TABELA_EXPERIENCIAS',
} as const;

export type TipoResposta = (typeof TipoResposta)[keyof typeof TipoResposta];

export const ROTULO_TIPO: Record<TipoResposta, string> = {
  TEXTO: 'Texto',
  TEXTO_LONGO: 'Texto longo',
  NUMERO: 'Número',
  SIM_NAO: 'Sim/Não',
  DATA: 'Data',
  AUTOMATICO: 'Automático',
  SECAO: 'Seção',
  TABELA_EXPERIENCIAS: 'Tabela de locais',
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
  NENHUMA: 'Preencher manualmente',
  DIA_E_HORA: 'Dia e hora da entrevista',
  NOME_CANDIDATO: 'Nome do candidato',
  TELEFONE_CANDIDATO: 'Telefone do candidato',
  DADOS_DA_VAGA: 'Informações da vaga',
};

export const TipoCurriculo = {
  RESUMO: 'RESUMO',
  PDF: 'PDF',
  IMAGEM: 'IMAGEM',
} as const;

export type TipoCurriculo = (typeof TipoCurriculo)[keyof typeof TipoCurriculo];

export interface Pergunta {
  id: string;
  roteiroId: number;
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

  vagaId: number | null;
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
  vagaId: number | null;
  inicioManual: boolean;
  curriculo: string;
  tipoCurriculo: TipoCurriculo;
  caminhoCurriculo: string;
  nomeArquivoCurriculo: string;

  inicioReal: number | null;
  fimReal: number | null;

  respostasRoteiro: string;
  notas: string;
  criadoEm: number;
}

export interface Vaga {
  id: string;
  titulo: string;
  empresa: string;
  tipoContrato: string;
  horarioTrabalho: string;

  entradaMinutos: number;
  saidaMinutos: number;
  salarioBeneficios: string;
  tempoExperiencia: string;
  escolaridade: string;
  exigeHabilitacao: string;
  resumoAtividades: string;
  limiteCandidatos: number;
  criadoEm: number;
}

export function horarioDaVaga(vaga: Pick<Vaga, 'horarioTrabalho' | 'entradaMinutos' | 'saidaMinutos'>): string {
  const preenchido = (m: number) => Number.isFinite(m) && m >= 0;
  if (preenchido(vaga.entradaMinutos) && preenchido(vaga.saidaMinutos)) {
    const h = (m: number) => `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;
    return `${h(vaga.entradaMinutos)} às ${h(vaga.saidaMinutos)}`;
  }
  return vaga.horarioTrabalho;
}

export interface LinhaExperiencia {
  id: string;
  entrevistaId: number;
  local: string;
  ano: string;
  duracao: string;
  cargo: string;
  motivoSaida: string;
  ordem: number;
}

export interface Definicoes {

  horarioInicioMinutos: number;
  almocoInicioMinutos: number;
  almocoFimMinutos: number;
  quantidadePorDia: number;
  duracaoMinutos: number;
  intervaloMinutos: number;
}
