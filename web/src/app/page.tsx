'use client';

import Link from 'next/link';
import { useAuth } from '@/context/AuthContext';
import { useMobile, URL_APK } from '@/lib/useMobile';

export default function PaginaInicial() {
  const { usuario, carregando } = useAuth();
  const mobile = useMobile();

  return (
    <main className="pagina" style={{ maxWidth: 640 }}>
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

      <div style={{ textAlign: 'center', padding: '48px 0 16px' }}>
        <h1 style={{ fontSize: 32, marginBottom: 8 }}>EntrevistaMais</h1>
        <p className="subtitulo" style={{ fontSize: 17 }}>
          Agenda de entrevistas: horários calculados sozinhos, vagas, roteiro de
          perguntas e respostas. No computador é este site; no celular, o app.
        </p>

        {/* No celular, o foco é baixar o aplicativo. */}
        {mobile && (
          <div className="download-cta">
            {URL_APK ? (
              <a className="baixar" href={URL_APK} download>
                <span aria-hidden>⬇️</span>
                Download do aplicativo
              </a>
            ) : (
              <div className="baixar" title="Configurar link (ver instruções)">
                <span aria-hidden>⬇️</span>
                Download do aplicativo
              </div>
            )}
            <p className="aviso-android">
              Android 8 ou superior. O site continua funcional aqui no celular.
            </p>
          </div>
        )}
      </div>

      <section className="cartao">
        <strong>Uma conta, os dois lugares</strong>
        <p className="dica">
          O cadastro é o mesmo no app e no site. Cada recrutador tem sua conta e
          não enxerga os dados dos outros.
        </p>
        <div className="acoes" style={{ justifyContent: 'center' }}>
          {!carregando &&
            (usuario ? (
              <Link className="botao" href="/agenda">
                Ir para a agenda
              </Link>
            ) : (
              <>
                <Link className="botao" href="/cadastro">
                  Criar conta grátis
                </Link>
                <Link className="botao botao-secundario" href="/login">
                  Já tenho conta
                </Link>
              </>
            ))}
        </div>
      </section>

      <section className="cartao">
        <strong>O que dá para fazer</strong>
        <p className="dica">
          • A agenda calcula os horários dos candidatos a partir das suas
          definições e rezerva o próximo horário automaticamente.
          <br />• Vagas: cadastre a vaga uma vez e o app preenche os dados na
          hora da entrevista.
          <br />• Roteiro: perguntas em ordem, com respostas automáticas de dia,
          hora, nome e dados da vaga.
          <br />• Acompanhe candidatos de agendada até aprovado, reprovado ou
          encerrado.
        </p>
      </section>
    </main>
  );
}