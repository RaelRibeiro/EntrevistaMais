import type { Definicoes, Entrevista } from './tipos';

/**
 * Calcula os horários da agenda automaticamente, igual ao app.
 *
 * Regras, na ordem em que são aplicadas a cada candidato:
 *  1. A primeira entrevista começa no horário de início definido.
 *  2. Cada entrevista ocupa a duração definida, mais o intervalo entre uma e outra.
 *  3. Se o horário cruzar a janela de almoço, é empurrado para o fim do almoço.
 *  4. Depois da quantidade por dia, o dia está lotado.
 */
export const MINUTOS_NO_DIA = 24 * 60;

export interface Candidato {
  id: string;
  nome: string;
}

export interface InicioCalculado {
  candidato: Candidato;
  inicioMinutos: number;
  duracaoMinutos: number;
  fimMinutos: number;
}

export interface DefinicoesDaAgenda {
  inicioMinutos: number;
  almocoInicioMinutos: number;
  almocoFimMinutos: number;
  intervaloMinutos: number;
  duracaoMinutos: number;
  quantidadePorDia: number;
}

export function calcularHorarios(
  definicoes: DefinicoesDaAgenda,
  candidatos: Candidato[],
): InicioCalculado[] {
  const duracao = Math.max(definicoes.duracaoMinutos, 1);
  const intervalo = Math.max(definicoes.intervaloMinutos, 0);
  const limite = Math.max(definicoes.quantidadePorDia, 1);
  const almocoInicio = definicoes.almocoInicioMinutos;
  const almocoFim = definicoes.almocoFimMinutos;

  let cursor = Math.min(
    Math.max(definicoes.inicioMinutos, 0),
    MINUTOS_NO_DIA - 1,
  );
  const resultado: InicioCalculado[] = [];

  for (const candidato of candidatos) {
    if (resultado.length >= limite) break;

    if (cursor < almocoFim && cursor + duracao > almocoInicio) {
      cursor = almocoFim;
    }

    if (cursor + duracao >= MINUTOS_NO_DIA) break;

    resultado.push({
      candidato,
      inicioMinutos: cursor,
      duracaoMinutos: duracao,
      fimMinutos: cursor + duracao,
    });
    cursor += duracao + intervalo;
  }

  return resultado;
}

export function recalcular(
  definicoes: DefinicoesDaAgenda,
  entrevistas: Entrevista[],
): Entrevista[] {
  const ordenados = [...entrevistas].sort((a, b) => a.ordem - b.ordem);
  const candidatos = ordenados.map((e) => ({ id: e.id, nome: e.nome }));
  const calculados = calcularHorarios(definicoes, candidatos);
  const porId = new Map(entrevistas.map((e) => [e.id, e]));

  const resultado: Entrevista[] = [];
  calculados.forEach((item, indice) => {
    const original = porId.get(item.candidato.id);
    if (!original) return;
    resultado.push({
      ...original,
      ordem: indice,
      inicioMinutos: item.inicioMinutos,
      duracaoMinutos: item.duracaoMinutos,
    });
  });

  return resultado;
}

/**
 * Converte as Definições salvas no Firestore para o formato de cálculo. As
 * gravações do app usam `horarioInicioMinutos`; o cálculo usa `inicioMinutos`.
 */
export function paraAgenda(definicoes: Definicoes): DefinicoesDaAgenda {
  return {
    inicioMinutos: definicoes.horarioInicioMinutos,
    almocoInicioMinutos: definicoes.almocoInicioMinutos,
    almocoFimMinutos: definicoes.almocoFimMinutos,
    intervaloMinutos: definicoes.intervaloMinutos ?? 0,
    duracaoMinutos: definicoes.duracaoMinutos ?? 60,
    quantidadePorDia: definicoes.quantidadePorDia ?? 10,
  };
}