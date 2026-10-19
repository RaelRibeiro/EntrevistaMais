'use client';

import Link from 'next/link';
import { useAuth } from '@/context/AuthContext';
import { useMobile, URL_APK } from '@/lib/useMobile';

const RECURSOS = [
  {
    icone: '⏱',
    titulo: 'Horário calculado sozinho',
    texto:
      'Você diz o início, a duração e o intervalo. Cada candidato entra na fila do dia e o próximo horário já sai preenchido — se algo atrasar, a agenda inteira recalcula.',
  },
  {
    icone: '🗂',
    titulo: 'Vaga cadastrada uma vez',
    texto:
      'Salário, jornada, endereço e benefícios ficam salvos. Na entrevista, os dados da vaga já estão lá, sem voltar para a planilha.',
  },
  {
    icone: '📋',
    titulo: 'Roteiro com respostas prontas',
    texto:
      'Perguntas na ordem, com dia, hora, nome e dados da vaga escritos sozinhos. Você confere, ajusta o que precisar e avança.',
  },
  {
    icone: '🎯',
    titulo: 'Do agendamento à decisão',
    texto:
      'Agendada, em andamento, aprovada, reprovada ou encerrada: cada candidato tem status claro e nada se perde no meio do dia corrido.',
  },
  {
    icone: '📎',
    titulo: 'Currículo guardado junto',
    texto:
      'PDF ou imagem do candidato e o resumo do currículo ficam no mesmo lugar da entrevista, disponíveis no app e no site.',
  },
  {
    icone: '🔒',
    titulo: 'Conta isolada por recrutador',
    texto:
      'Cada recrutador enxerga apenas os próprios candidatos, vagas e roteiros. Sem dado cruzado entre contas.',
  },
];

const PASSOS = [
  {
    titulo: 'Configure uma vez',
    texto:
      'Defina horário de início, duração da entrevista, intervalo entre candidatos e quantas cabe por dia.',
  },
  {
    titulo: 'Agende em segundos',
    texto:
      'Nome, telefone, vaga e currículo. O horário do candidato já vem calculado e pode ser ajustado na hora.',
  },
  {
    titulo: 'Entreviste com o roteiro em mãos',
    texto:
      'Abra o candidato, siga as perguntas e encerre com a decisão — aprovar, reprovar ou reabrir depois.',
  },
];

const FILA = [
  { hora: '09:00', nome: 'Marina Souza', status: 'Aprovada', classe: 'aprovado' },
  { hora: '09:45', nome: 'João Pereira', status: 'Em andamento', classe: 'em_andamento' },
  { hora: '10:30', nome: 'Ana Beatriz', status: 'Agendada', classe: 'agendada' },
  { hora: '11:15', nome: 'Carlos Lima', status: 'Agendada', classe: 'agendada' },
];

export default function PaginaInicial() {
  const { usuario, carregando } = useAuth();
  const mobile = useMobile();

  const acoes = !carregando && (
    <>
      {usuario ? (
        <Link className="botao lp-botao" href="/agenda">
          Ir para a agenda
        </Link>
      ) : mobile && URL_APK ? (
        <a className="botao lp-botao" href={URL_APK} download>
          Baixar o aplicativo
        </a>
      ) : (
        <>
          <Link className="botao lp-botao" href="/cadastro">
            Criar conta grátis
          </Link>
          <Link className="botao botao-secundario lp-botao" href="/login">
            Já tenho conta
          </Link>
        </>
      )}
    </>
  );

  return (
    <main className="pagina pagina-inicial">
      <header className="cabecalho">
        <nav className="nav">
          <span className="marca">EntrevistaMais</span>
          <span className="espaco" />
          {!carregando &&
            (usuario ? (
              <Link href="/agenda" className="ativo">
                Abrir agenda
              </Link>
            ) : (
              <>
                <Link href="/login">Entrar</Link>
                <Link href="/cadastro" className="ativo">
                  Criar conta
                </Link>
              </>
            ))}
        </nav>
      </header>

      <section className="lp-hero">
        <div className="lp-hero-texto">
          <span className="lp-selo">Para quem entrevista todo dia</span>

          <h1 className="lp-titulo">
            Sua agenda de entrevistas{' '}
            <span className="lp-grifo">montada sozinha</span>
          </h1>

          <p className="lp-subtitulo">
            Você define o início, a duração e o limite do dia. O EntrevistaMais
            encaixa cada candidato no horário certo, mostra quantas vagas do dia
            já foram usadas e leva o roteiro de perguntas para dentro da
            entrevista.
          </p>

          <div className="lp-acoes">{acoes}</div>

          <ul className="lp-notas">
            <li>Grátis</li>
            <li>Mesma conta no app e no site</li>
            <li>Android 8 ou superior</li>
          </ul>

          {mobile && (
            <p className="lp-aviso">
              {URL_APK ? (
                <>
                  Prefere o app? O botão acima baixa o{' '}
                  <strong>EntrevistaMais</strong> direto no celular — o site
                  também funciona por aqui.
                </>
              ) : (
                <>O site também funciona no celular, com a mesma conta.</>
              )}
            </p>
          )}
        </div>

        <div className="lp-preview" aria-hidden>
          <div className="lp-preview-topo">
            <strong>Hoje, quinta-feira</strong>
            <span className="lp-vagas">
              <span className="lp-vagas-barra">
                <span className="lp-vagas-preenchido" style={{ width: '60%' }} />
              </span>
              3 de 5 vagas
            </span>
          </div>

          {FILA.map((item) => (
            <div className="lp-linha" key={item.hora}>
              <span className="lp-linha-hora">{item.hora}</span>
              <span className="lp-linha-nome">{item.nome}</span>
              <span className={`status ${item.classe}`}>{item.status}</span>
            </div>
          ))}

          <div className="lp-preview-pe">
            <span className="lp-pe-bolha">↻</span>
            <span>Horário alterado? Um toque volta ao calculado.</span>
          </div>
        </div>
      </section>

      <section className="lp-secao">
        <p className="secao-titulo">O que você ganha</p>
        <div className="lp-grade">
          {RECURSOS.map((r) => (
            <article className="lp-cartao" key={r.titulo}>
              <span className="lp-icone" aria-hidden>
                {r.icone}
              </span>
              <h3>{r.titulo}</h3>
              <p>{r.texto}</p>
            </article>
          ))}
        </div>
      </section>

      <section className="lp-secao">
        <p className="secao-titulo">Como funciona</p>
        <ol className="lp-passos">
          {PASSOS.map((p, i) => (
            <li key={p.titulo}>
              <span className="lp-numero">{i + 1}</span>
              <h3>{p.titulo}</h3>
              <p>{p.texto}</p>
            </li>
          ))}
        </ol>
      </section>

      <section className="lp-rodape-cta">
        <div>
          <h2>Comece pela agenda de hoje</h2>
          <p>
            Crie a conta em menos de um minuto e adicione o primeiro candidato —
            o horário já vem pronto.
          </p>
        </div>
        <div className="lp-acoes">{acoes}</div>
      </section>

      <footer className="lp-rodape">
        <span className="marca">EntrevistaMais</span>
        <span className="lp-rodape-legal">
          Conta única, dado isolado por recrutador. Android e web com a mesma
          base.
        </span>
      </footer>
    </main>
  );
}
