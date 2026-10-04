export function minutosParaHora(minutos: number): string {
  const h = Math.floor(minutos / 60);
  const m = minutos % 60;
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
}

export function horaParaMinutos(hora: string): number {
  const [h, m] = hora.split(':').map((parte) => Number.parseInt(parte, 10) || 0);
  return h * 60 + m;
}

/** Hoje no fuso do navegador, no formato ISO (yyyy-mm-dd) salvo no banco. */
export function hojeIso(): string {
  const agora = new Date();
  const offset = agora.getTimezoneOffset();
  const local = new Date(agora.getTime() - offset * 60_000);
  return local.toISOString().slice(0, 10);
}

export function formatarTelefone(telefone: string): string {
  const digitos = telefone.replace(/\D/g, '');
  if (digitos.length === 11) {
    return `(${digitos.slice(0, 2)}) ${digitos.slice(2, 7)}-${digitos.slice(7)}`;
  }
  if (digitos.length === 10) {
    return `(${digitos.slice(0, 2)}) ${digitos.slice(2, 6)}-${digitos.slice(6)}`;
  }
  return telefone;
}

/** Converte um valor salvo no Firestore de volta para `number`. */
export function numero(valor: unknown): number {
  if (typeof valor === 'number') return valor;
  const numeroFirestore = valor as { toNumber?: () => number } | null;
  if (numeroFirestore?.toNumber) return numeroFirestore.toNumber();
  return Number(valor ?? 0) || 0;
}

const DIAS_SEMANA = [
  'Domingo',
  'Segunda-feira',
  'Terça-feira',
  'Quarta-feira',
  'Quinta-feira',
  'Sexta-feira',
  'Sábado',
];

/** "540" -> "9h" / "45min" / "9h15min". */
export function minutosParaHoraComSufixo(minutos: number): string {
  const horas = Math.floor(minutos / 60);
  const resto = minutos % 60;
  if (horas > 0 && resto > 0) return `${horas}h${resto}min`;
  if (horas > 0) return `${horas}h`;
  return `${resto}min`;
}

/** "12/03/2026" a partir de uma data ISO (yyyy-mm-dd). */
export function paraTexto(data: string): string {
  const [ano, mes, dia] = data.split('-');
  if (!ano || !mes || !dia) return data;
  return `${dia}/${mes}/${ano}`;
}

/** "Quinta-feira" a partir de uma data ISO (yyyy-mm-dd). */
export function paraDiaDaSemana(data: string): string {
  const [ano, mes, dia] = data.split('-').map(Number);
  if (!ano || !mes || !dia) return '';
  const local = new Date(ano, mes - 1, dia);
  return DIAS_SEMANA[local.getDay()] ?? '';
}

/** "Hoje - quinta-feira", "Amanhã - sexta-feira" ou "12/03/2026 - quinta-feira". */
export function paraTituloAgenda(data: string): string {
  const amanha = new Date();
  amanha.setDate(amanha.getDate() + 1);
  const isoAmanha = hojeIsoDe(amanha);
  const diaDaSemana = paraDiaDaSemana(data);
  if (data === hojeIso()) return `Hoje - ${diaDaSemana}`;
  if (data === isoAmanha) return `Amanhã - ${diaDaSemana}`;
  return `${paraTexto(data)} - ${diaDaSemana}`;
}

export function somarDias(data: string, dias: number): string {
  const [ano, mes, dia] = data.split('-').map(Number);
  const local = new Date(ano, mes - 1, dia + dias);
  return hojeIsoDe(local);
}

function hojeIsoDe(data: Date): string {
  const ano = data.getFullYear();
  const mes = String(data.getMonth() + 1).padStart(2, '0');
  const dia = String(data.getDate()).padStart(2, '0');
  return `${ano}-${mes}-${dia}`;
}