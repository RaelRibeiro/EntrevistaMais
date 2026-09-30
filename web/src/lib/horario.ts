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