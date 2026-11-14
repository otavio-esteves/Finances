# Importação de PDFs: cobertura e validação

## Fluxo implementado

1. Escolher **fatura** ou **extrato** e selecionar o PDF pelo seletor do Android.
2. Se o PDF exigir senha, informar a senha fornecida pelo banco. Uma senha incorreta permite nova tentativa. A senha não é salva em preferências, banco, backup ou estado salvo de tela.
3. O PDFBox lê o texto e suas posições. O extrator recompõe linhas e usa as posições dos cabeçalhos para preservar células vazias nas tabelas de débito/crédito. Descrições, horários e documentos mantêm sua ordem na descrição do lançamento; a data de movimento tem preferência quando identificada explicitamente.
4. Páginas digitalizadas, páginas sem texto extraível e páginas com imagem ocupando ao menos 20% da área passam pelo renderizador PDF do Android e pelo Tesseract local. Páginas totalmente brancas são ignoradas. Logos pequenos não substituem o texto digital por OCR. Documentos mistos são processados página a página.
5. Rever os lançamentos e categorias. **Corrigir dados** permite ajustar descrição, data e valor. Corrigir dados refaz a checagem de sobreposição com o histórico. O valor é informado sem sinal; o tipo define despesa, receita ou transferência.
6. Quando houve OCR, a confirmação exige marcar que os dados reconhecidos foram conferidos. Alterar data/valor/descrição retira essa confirmação. Somente depois da revisão o lote é salvo atomicamente.

## Bancos priorizados

Para investigar arquivos que falham no app, coloque as amostras em `samples/private/`, ignorado pelo Git, e registre erro e resultado esperado conforme o [guia de amostras locais](../samples/README.md). Esses arquivos não entram no APK nem são executados automaticamente nos testes.

Nubank, PicPay, Banco do Brasil, Caixa e Inter foram as prioridades informadas. Os testes usam **documentos sintéticos representativos**, não arquivos reais desses bancos:

| Estrutura exercitada | Cobertura |
|---|---|
| Fatura com mês de referência e data abreviada, representando Nubank | Compra sem sinal, mês por nome e ano explícito no documento. |
| Extrato com data, hora, descrição e valor, representando PicPay | Horário preservado na descrição; sinal financeiro explícito. |
| Data de balancete, data de movimento, histórico, débito, crédito e saldo, representando BB | Escolha explícita da data de movimento; células de débito/crédito vazias e saldo sem virar transação. |
| Data de movimento, documento, histórico, valor C/D e saldo, representando Caixa | Documento preservado na descrição; marcador C/D validado. |
| Data, descrição, valor assinado e saldo, representando Inter | Débito assinado e saldo separado. |

Esses testes comprovam as famílias de colunas implementadas. **Não certificam todas as versões de layout de cada instituição.** A investigação do corpus local acrescentou tratamento das estruturas abaixo; os testes versionados continuam sintéticos e os arquivos reais permanecem em `samples/private/`.

- **Nubank:** seção de transações separada das simulações e declarações legais; período por mês abreviado ancorado na emissão, inclusive na virada do ano; sinal menos Unicode. Pagamentos negativos reconhecidos na fatura são transferências, preservando os estornos como receitas.
- **PicPay:** leitura independente de duas colunas de compras, inclusive a continuação acima do primeiro cabeçalho; páginas de ofertas e encargos fora da seção de transações. `Subtotal dos lançamentos` é reconhecido como resumo mesmo quando o texto fica dividido entre as células de data, descrição e valor; não vira lançamento nem completa uma compra incompleta. Datas abreviadas são limitadas ao mês do fechamento e ao anterior, com ano derivado do fechamento explícito; compras anteriores precisam de ano explícito ou de outro layout com período inequívoco.
- **Mercado Pago:** agrupamento geométrico de descrições acima/abaixo da data, prefixos que continuam na página seguinte e coluna de ID separada. No layout reconhecido por detalhe dos movimentos e totais de entradas/saídas, valor sem sinal é crédito e valor negativo é débito. Os totais extraídos devem conferir com esses dois totais do documento antes de liberar o lote.
- **Caixa:** tabela de lançamentos com documento, histórico/complemento, favorecido, CPF/CNPJ, valor e saldo; horário junto da data e exclusão de `SALDO DIA`. O cabeçalho da conta não é interpretado como lançamento.

A identificação usa a estrutura do documento, não apenas o nome do banco. O [roteiro do corpus privado](../samples/README.md#repetir-a-validação-local-no-android) permite repetir a leitura e comparar contagens e totais esperados sem colocar PDFs reais nos APKs ou no Git.

## Datas, valores e integridade

- Datas completas brasileiras com `/`, `-` ou `.`, ISO, datas abreviadas e meses em português por nome/abreviação.
- Datas abreviadas e anos de dois dígitos só são resolvidos quando o próprio documento fornece um período/ano/referência mensal inequívoca. O relógio do aparelho nunca decide o ano de uma compra.
- Sinais à esquerda/direita, parênteses, C/D, CR/DR e Crédito/Débito são validados. Sinal e marcador contraditórios recusam o lote.
- Uma coluna única de valor em extrato exige direção explícita; em fatura selecionada/identificada, compra sem sinal ou com `+` é despesa, enquanto `-`/parênteses indicam crédito. Pagamentos de fatura reconhecidos recebem sugestão de transferência; estornos permanecem receitas. Colunas separadas de débito/crédito definem a direção e não podem apresentar dois valores simultâneos. Dois zeros sem direção inequívoca são recusados.
- Cabeçalhos, totais/saldos reconhecidos e paginação conhecida não viram lançamentos. Colunas monetárias/data de movimento ambíguas, lançamentos incompletos e linhas monetárias não reconhecidas pelo OCR recusam o lote inteiro.
- A impressão digital do lote usa os dados extraídos originalmente. Corrigir os dados antes de confirmar não muda a identidade do lote; as correções são as transações efetivamente gravadas.
- Não há salvamento automático nem confirmação parcial silenciosa. A confirmação não pode ser reiniciada ou substituída por outro arquivo enquanto a gravação está em andamento.

## OCR local e recursos

O APK inclui Tesseract4Android 4.9.0 e o modelo português `tessdata_fast/por.traineddata`, fixado por commit e SHA-256. A origem e a licença estão em `app/src/main/assets/ocr/`. O modelo é copiado para o armazenamento privado e validado antes do uso. Não há download de modelo em tempo de execução, Google Play Services, serviço de nuvem ou telemetria de OCR. O manifest resultante continua sem `INTERNET`.

A imagem é renderizada sobre branco, limitada a 3200 pixels no maior lado e seis milhões de pixels por página. Uma página é processada por vez; bitmap, iterador e motor nativo são liberados. A análise verifica cancelamento entre páginas e durante o reconhecimento. O motor é reutilizado dentro do documento, não mantido permanentemente em memória.

Para renderizar, o app pode criar uma cópia temporária no seu cache privado. Se o PDF foi aberto com senha, essa cópia é decifrada apenas para a renderização e excluída ao encerrar a leitura, inclusive em falhas/cancelamento. O arquivo original selecionado não é alterado. Um encerramento abrupto do processo pode deixar cache privado para a limpeza do sistema.

Limites atuais: arquivo de **10 MB**, **100 páginas**, até **30 páginas com OCR** e **4 Mi caracteres** de texto expandido. O reconhecimento usa um bloco de texto e reconstrói a tabela pelas coordenadas; a segmentação automática de página podia omitir colunas esparsas. Datas/valores abaixo do limiar de confiança 80 passam por uma segunda leitura ampliada do recorte: ela precisa reconhecer exatamente o mesmo texto e atingir o mesmo limiar. Há no máximo 64 releituras por página; desacordo ou baixa confiança recusa o arquivo. Fragmentos numéricos de CPF/conta não são confundidos com datas. A confiança é um indicador do OCR, não prova de exatidão; por isso a revisão humana continua obrigatória.

## Validação

- Rodada de 30/10/2026: **208 testes JVM aprovados**, **13 testes instrumentados direcionados aprovados** (PDF/OCR, revisão, contexto do app e ausência de permissão de Internet) e leitura das quatro amostras privadas de Nubank, PicPay, Mercado Pago e Caixa com contagens e totais esperados conferidos, incluindo transferências. O corpus e os diagnósticos ficam fora do Git. A suíte instrumentada completa foi interrompida pelo encerramento do emulador; a rodada concluída é a seleção direcionada de 13 testes, além do corpus privado.
- Build do APK normal de debug concluído nessa rodada; lint com **zero erros e 35 avisos**. O APK gerado mantém o application ID normal e não declara `INTERNET`.

- Rodada de 25/10/2026: **200 testes JVM aprovados**, sem falhas; suíte instrumentada com **35 aprovados e dois pulados**, por dependerem de um modelo LiteRT-LM não instalado. O runner conta os dois pulados no total de 37. Compilação do app e do APK de testes concluída.
- O APK final passou novamente pelos **10 testes específicos de PDF, revisão e ausência de permissão de Internet**, sem falhas.
- Lint: **zero erros e 34 avisos**. O aviso adicionado pelo extrator sugere apenas a função KTX equivalente a `Bitmap.createBitmap`; não altera o resultado da análise.
- Testes JVM para geometria/colunas vazias, datas, sinais, metadados contraditórios, correções, nova checagem de duplicidade, senha e bloqueio da revisão OCR.
- PDFs completos gerados em teste: texto digital, imagem sem camada de texto, arquivo misto com página branca, PDF criptografado e logo pequeno.
- Reconhecimento nativo testado em emulador API 35/x86_64 em modo avião, com Wi-Fi desativado, sem Google Play Services. O teste do manifest verifica a ausência de `INTERNET` no app instalado.
- Testes Compose exercitam senha incorreta/correta e o fluxo de corrigir valor/data, reconhecer a revisão e confirmar a importação.

```bash
JAVA_HOME=/opt/android-studio/jbr ./gradlew testStandaloneDebugUnitTest
JAVA_HOME=/opt/android-studio/jbr ./gradlew lintStandaloneDebug assembleStandaloneDebug assembleStandaloneDebugAndroidTest
```

Após instalar os APKs de app e testes em um emulador/dispositivo, a suíte instrumentada pode ser repetida sem executar a captura visual auxiliar:

```bash
adb shell am instrument -w -r \
  -e notClass br.com.otavioesteves.finances.ui.FinancesVisualCaptureTest \
  br.com.otavioesteves.finances.test/androidx.test.runner.AndroidJUnitRunner
```

## Limites de conclusão

Um PDF pode conter tabelas arbitrárias, texto ausente/corrompido, fotos desfocadas, manuscritos ou páginas não financeiras. A implementação não garante importação de qualquer arquivo nem transforma um número duvidoso em dado confirmado. Um documento recusado precisa de imagem melhor, referência de ano inequívoca ou adaptação de layout baseada em uma amostra real. A cobertura de bancos deve crescer com amostras anonimizadas e regressões reproduzíveis.
