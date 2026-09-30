# Entrevistador — site

Versão web do aplicativo Entrevistador. O computador usa o site; o celular usa
o aplicativo Android. Os dois compartilham **a mesma conta e a mesma base de
dados** (Firebase Auth + Firestore), então um candidato cadastrado no celular
aparece no site e vice-versa.

## O que já está pronto

- Login com e-mail/senha e com Google.
- Agenda: adicionar candidato (horário calculado sozinho), anexar currículo,
  reordenar, alterar horário, recalcular o dia, concluir/aprovar/reprovar, não
  compareceu, cancelar, encerrar e reabrir.
- Vagas: dropdowns pré-definidos (tipo de contrato, escolaridade, habilitação),
  horário de entrada/saída e limite de candidatos.
- Definições: horário de início, almoço, duração, intervalo e quantidade por dia.
- Roteiro: padrão de 29 perguntas criado automaticamente no 1º login (igual ao
  app), um roteiro por vaga (copia o padrão ao criar), restaurar perguntas
  originais; perguntas com tipo e resposta automática.
- Entrevista: bloco de currículo (abrir/baixar/trocar/remover + resumo), duração
  em andamento, decisões de aprovação, tabela de experiências profissionais e
  respostas editáveis até finalizar.
- Botão de **Download do aplicativo** aparece **somente no celular** (e quando
  `NEXT_PUBLIC_APK_URL` está preenchido).
- PWA: no celular com Chrome, o site pode ser adicionado à tela inicial.

O site e o aplicativo Android compartilham a **mesma conta e os mesmos dados**
(Firebase Auth + Firestore + Storage), então um candidato cadastrado no celular
aparece no site e vice-versa — inclusive os currículos anexados.

## Estrutura do banco (Firestore)

Tudo fica dentro da conta do usuário, e o aplicativo Android vai usar esta
mesma estrutura:

```
usuarios/{uid}
  ├── definicoes/padrao            (horário de início, almoço, duração…)
  ├── vagas/{vagaId}
  ├── entrevistas/{entrevistaId}
  ├── roteiros/{roteiroId}
  │     └── perguntas/{perguntaId}
  ├── respostas/{entrevistaId}     (um documento com {perguntaId: valor})
  └── experiencias/{entrevistaId}  (tabela de experiências profissionais)
```

Currículos anexados vão para o **Storage** em
`usuarios/{uid}/curriculos/{timestamp}_{nome}.{ext}`. As regras de segurança
são `firestore.rules` (Firestore) e `storage.rules` (Storage), ambas na pasta
`web/`.

## Como colocar no ar (passo a passo)

### 1. Firebase (banco + login)

1. Acesse https://console.firebase.google.com e crie um projeto (gratuito).
2. No menu **Build → Authentication**, clique em **Começar** e ative dois métodos:
   - **E-mail/Senha** (habilite);
   - **Google** (selecione o e-mail da conta para o popup).
3. No menu **Build → Firestore Database**, clique em **Criar banco de dados**:
   - modo **produção**, região perto de você.
4. Cole as regras de segurança (arquivo `firestore.rules` da pasta `web/`) no
   botão **Regras** da aba Firestore e clique em **Publicar**. Sem isto, o site
   funciona mas ninguém consegue ler/escrever.
5. No menu **Build → Storage**, clique em **Começar** (região padrão já usada)
   e publique as regras do arquivo `storage.rules` da pasta `web/` na aba
   **Regras**. Sem isto, anexar currículo falha.
6. Em **Configurações do projeto → Seus apps**, adicione um app **Web** (ícone
   `</>`), copie a configuração e preencha o arquivo `web/.env.local` (use o
   `.env.local.example` como modelo).

### 2. Publicar o site no Vercel (gratuito)

1. Suba a pasta `Entrevistador` (o repositório inteiro) para o seu GitHub:
   - `git init && git add . && git commit -m "app + site"` e crie o repositório
     no github.com (guia rápido: https://docs.github.com/pt/get-started).
2. Acesse https://vercel.com, entre com o GitHub e clique em **Add New →
   Project**, importe o repositório.
3. **Framework Preset: Next.js** (o Vercel detecta sozinho). Raiz do projeto:
   a pasta `web/` (campo *Root Directory*).
4. Depois do deploy, em **Settings → Environment Variables** adicione as mesmas
   variáveis do `.env.local` (chaves do mesmo app Firebase). A primeira versão
   sem elas sobe, mas o login só funciona depois de adicionadas e de um novo
   **Redeploy** **→ Deployments**.
5. Pronto: o site fica em `https://seu-projeto.vercel.app`.

### 3. Link de download do aplicativo (Android)

1. Gere o APK assinado: `./gradlew assembleRelease` (a assinatura vive em
   `app/build.gradle.kts`; veja a seção 4). O arquivo sai em
   `app/build/outputs/apk/release/app-release.apk`.
2. Suba esse APK numa URL pública direta e **sem custo**: crie um **GitHub
   Release** (em *Releases*, crie a release e anexe o `.apk` — vira
   `https://github.com/seuusuario/repo/releases/download/v1/app-release.apk`).
3. Preencha `NEXT_PUBLIC_APK_URL` com essa URL no Vercel (variável de ambiente)
   e faça um novo deploy. Só então o botão "Download do aplicativo" aparece —
   e apenas no celular.

### 4. Distribuir o aplicativo sem o aviso "app desconhecido"

1. Gere uma chave de assinatura (uma vez): com o JDK instalado, rode
   `keytool -genkeypair -v -keystore papelada.keystore -alias entrevistador -keyalg RSA -keysize 2048 -validity 10000`.
2. Gere a release assinada: `./gradlew assembleRelease` (a assinatura já está
   configurada em `app/build.gradle.kts`). O APK sai em
   `app/build/outputs/apk/release/app-release.apk`.
3. Suba esse APK numa URL pública direta e **sem custo**: crie um
   **GitHub Release** (em *Releases*, crie a release e anexe o `.apk`).
4. Preencha `NEXT_PUBLIC_APK_URL` com essa URL no Vercel (variável de ambiente)
   e faça um novo deploy. O botão "Download do aplicativo" aparece só no celular.
5. No console Firebase (Autenticação → Google → método de entrada), verifique se
   o **SHA-1 da chave de release** está listado em "Autorizar domínios/apps" —
   sem isso o login Google falha na versão assinada (a chave de debug tem outra
   impressão digital).

O app Android usa o mesmo Firebase (Auth + Firestore + Storage) do site — conta,
vagas, entrevistas, roteiros e currículos são compartilhados.

## Comandos de desenvolvimento

```bash
cd web
npm install
npm run dev        # local em http://localhost:3000
npm run typecheck  # checa tipos
npm run build      # build de produção (o Vercel roda isto)
```