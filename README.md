# Finances

O Finances é um app Android de finanças pessoais que transforma lançamentos de **faturas de cartão e extratos bancários** em uma visão clara dos gastos. A experiência desejada é simples: receber o arquivo, identificar e categorizar as transações automaticamente, organizar a visão mensal e usar IA local para sugerir categorias durante a importação.

O processamento e os dados financeiros ficam no aparelho. A revisão das categorias antes de salvar os lançamentos mantém a pessoa no controle do resultado.

## Experiência central

1. **Receber a fatura ou o extrato.** A pessoa indica se o arquivo é uma fatura ou um extrato e o seleciona no seletor do Android. O parser aceita **CSV, OFX e PDFs digitais ou digitalizados** no formato data completa, descrição e valor, inclusive quando um lançamento ocupa até seis linhas consecutivas. Em CSV de fatura, valores positivos são compras e negativos são estornos; em CSV de extrato, o sinal segue débito e crédito. Datas abreviadas e meses por nome são aceitos quando o próprio documento informa um período, ano ou referência mensal inequívoca; páginas digitalizadas usam OCR local com modelo em português incluído no APK. PDFs com senha permitem informar a senha somente para aquela leitura. A revisão permite corrigir descrição, data e valor, e exige conferência explícita quando houve OCR.
2. **Organizar automaticamente.** O app extrai os lançamentos e sugere categorias. Quando há um modelo LiteRT-LM importado e disponível, tenta usá-lo; caso contrário, usa regras locais. A pessoa revisa e pode corrigir cada sugestão antes de confirmar a importação.
3. **Entender os gastos.** A Início mostra o saldo do mês, a composição por categoria e transações recentes. A visão de categorias e os filtros de mês ajudam a localizar os maiores gastos. A Início também apresenta comparações numéricas de despesas por categoria com o mês anterior; análises abertas em linguagem natural continuam sendo uma meta.

```text
Arquivo CSV/OFX/PDF → extração → sugestão de categoria → revisão → transações locais
                                                          ↓
                                      Início → Gráficos → Transações
```

O lançamento manual, a edição de transações e o backup complementam esse fluxo. A direção do produto está no uso de arquivos para evitar digitação lançamento por lançamento.

[Referência visual original](<docs/ChatGPT Image Sep 19, 2026, 05_25_12 PM.png>) — imagem de conceito; não representa todas as funções já implementadas.

## O que funciona hoje

| Área | Estado atual |
|---|---|
| Entrada de arquivos | Importação local de CSV/OFX/PDF de até 10 MB, com escolha entre extrato e fatura e revisão de tipo e categoria. A revisão mostra contagem e totais; pagamentos de cartão reconhecidos são sugeridos como transferência, tanto a débito quanto a crédito; em extratos, rótulos genéricos como “Pagamento da fatura” preservam o tipo extraído para revisão, e faturas de energia/água/telefonia continuam como despesas; estornos e escolhas memorizadas são preservados. PDF digital, digitalizado, misto e protegido por senha. OCR português local, reconstrução de tabelas por posição e colunas separadas de débito/crédito, com saldo identificado por cabeçalho. Datas sem ano exigem referência explícita; ambiguidades recusam o lote. Correção de descrição/data/valor e conferência obrigatória quando houve OCR. [Cobertura e limites](docs/PDF_IMPORTACAO.md). O lote é salvo de forma atômica; repetir os mesmos lançamentos na mesma conta/cartão é recusado, independentemente da ordem nos novos lotes. Contas distintas podem importar lotes iguais quando identificadas na revisão. Possíveis sobreposições com o histórico exigem confirmação explícita. |
| Organização | Regras locais e memória de correções explícitas, com LiteRT-LM opcional. Há [benchmark sintético reproduzível](docs/CATEGORIZACAO_BENCHMARK.md) de regras, memória e fluxo combinado; qualidade do modelo real e generalização ainda exigem medição independente. |
| Visualização | Início com saldo mensal, gráfico de composição por categoria e transações recentes; telas de categorias e histórico com seleção de mês. Em Gráficos, composição alterna Rosca/Pizza e as séries anuais alternam Barras/Linhas/Área, mantendo rosca e barras como padrões. |
| Dados | Room local, lançamentos manuais, ícone opcional por transação, exportação CSV/JSON e backup portátil protegido por senha em fluxo; restauração JSON legada preservada. O conjunto de registros ainda ocupa memória. |
| Navegação | Início, Gráficos, Transações e Categorias formam quatro páginas lado a lado, com gesto horizontal e uma barra flutuante de quatro ícones. O mês selecionado é compartilhado com Categorias, seus detalhes e Histórico, e fica salvo ao reabrir o app. O botão circular à esquerda abre o menu lateral com o título Finances, Configurações e o aviso de privacidade; o botão circular à direita abre a importação e o acesso ao lançamento individual. |

Na página Gráficos, a ação **Adicionar exemplos** insere 60 lançamentos fictícios de janeiro a outubro de 2026, marcados com `[EXEMPLO]`. A ação **Remover** exclui apenas os exemplos identificados por esse marcador interno. Os exemplos entram nos totais do app enquanto estiverem salvos. Repetir a ação em dias diferentes preserva os exemplos já gravados, identificados por mês e descrição.

### Próximas entregas ligadas à ideia central

1. Ampliar a validação de layouts de **Nubank, PicPay, Banco do Brasil, Caixa e Inter**. Nesta consolidação, cinco cenários privados passaram (Nubank, PicPay, Caixa e Mercado Pago); BB/Inter ainda dependem de amostras privadas disponíveis. OCR local, senha, tabelas posicionadas e colunas de débito/crédito já estão implementados; os testes atuais representam famílias de layout, não certificam todos os formatos dessas instituições.
2. Ampliar o benchmark implementado com um conjunto independente e executar o modelo real em hardware compatível. Manter regressões de incerteza, duplicidade e sobreposição já automatizadas.
3. Ampliar as comparações mensais determinísticas já implementadas com novos insights a partir dos dados confirmados, sempre com números verificáveis no histórico.
4. Concluir a distribuição do modelo no flavor `play` e validar a experiência de ponta a ponta em aparelhos reais. Os flavors `play` e `standalone` existem, mas ainda têm o mesmo comportamento; o asset pack não foi integrado.

O [plano do motor local](docs/PLANO_MOTOR_IA_LOCAL.md) detalha as decisões e pendências de IA. A [arquitetura](docs/ARCHITECTURE.md) descreve os fluxos e contratos implementados.

Arquivos que falham na importação podem ser investigados em `samples/private/`, ignorado pelo Git. O [guia de amostras locais](samples/README.md) explica como registrar o erro e repetir a validação dos PDFs no Android com contagens e totais esperados.

## IA local e limites atuais

Ao abrir ou retomar a tela do modelo, o app reavalia a capacidade disponível, inclusive depois de liberar armazenamento, sem precisar reiniciar o processo. A importação também confere a capacidade novamente após o seletor de arquivos. **Remover modelo** fica disponível sempre que existe um arquivo instalado, mesmo sem espaço livre suficiente, RAM ou ABI compatível para executar a IA.

O motor escolhido para a integração atual é **LiteRT-LM**. O usuário pode importar manualmente um bundle `.litertlm` pela tela de modelo em Config. A importação copia o arquivo para o armazenamento privado do app, calcula seu SHA-256 e tenta inicializar o runtime antes de substituir o modelo anterior. A cópia do modelo verifica cancelamento e limita o tamanho ao espaço disponível, mantendo uma reserva de 2 GB livres e preservando o modelo anterior até validar o candidato. Uma falha remove o candidato incompleto. A leitura de um provedor bloqueado e a inicialização nativa só permitem conferir cancelamento quando a API retorna. A lista de hashes de modelos medidos ainda está vazia. Por isso, um modelo importado aparece como não verificado até que haja medições e hashes conhecidos; a inicialização sozinha não comprova qualidade das respostas.

A categorização usa IA local quando o modelo está disponível e regras como fallback. As regras reconhecem frases e palavras inteiras, normalizam acentos e incluem termos de estabelecimentos e serviços; correspondências ambíguas continuam com baixa confiança ou sem categoria para revisão. O cancelamento é conferido antes e depois de cada inferência, antes de repetir uma resposta inválida e entre lotes; uma chamada nativa em andamento só permite essa checagem quando retorna. A inferência real e seu desempenho ainda exigem validação em dispositivo. O motor LiteRT-LM é reutilizado entre chamadas e liberado em segundo plano ou sob pressão de memória. A tela do Chat foi removida; o gerenciamento de modelos e a categorização das faturas continuam disponíveis. O banco mantém o histórico antigo para preservar a compatibilidade de backup e restauração.

Para importar um modelo próprio, abra **Config → Gerenciar Modelo de IA → Selecionar arquivo do modelo** e escolha um bundle `.litertlm` já salvo no aparelho. O app ainda não oferece um link oficial de download. A checagem provisória de capacidade exige 6 GB de RAM, ABI `arm64-v8a` e 2 GB livres; aparelhos abaixo desse limite continuam com a categorização por regras. Esses limites ainda precisam ser medidos e revistos, especialmente para o aparelho intermediário previsto no plano.

O OCR usa Tesseract com modelo português incluído e não depende do modelo LiteRT-LM. O projeto **não declara a permissão `INTERNET`**. O arquivo é escolhido pelo seletor do Android; o destino de uma exportação ou backup também é escolhido pela pessoa e pode ser um provedor externo. O app não integra telemetria nem serviço de nuvem para processar dados financeiros.

## Tecnologias e organização

- Kotlin, Jetpack Compose e Material 3 para a interface.
- ViewModel e StateFlow para estado de tela.
- Camadas de domínio, dados e apresentação, com injeção manual em `AppContainer`.
- Room para persistência local; `Money` armazena valores em centavos (`Long`).
- Parsers locais de CSV/OFX, extração de texto PDF com PDFBox-Android e integração LiteRT-LM para inferência no dispositivo.
- Gradle Version Catalog para versões e dependências.

```text
UI Compose → ViewModels → casos de uso e contratos de domínio ← Room, parsers e IA local
```

## Privacidade e cuidados

- O app não declara `INTERNET`, desativa o backup automático do Android e bloqueia capturas de tela por padrão com `FLAG_SECURE`. Em Configurações → Privacidade, a opção **Permitir capturas de tela** pode liberar a captura imediatamente e fica salva para as próximas aberturas. Há um teste instrumentado que verifica a ausência da permissão de rede no app instalado.
- Transações e histórico do Chat ficam no banco local. O arquivo original selecionado não é alterado; a leitura PDF pode usar uma cópia no cache privado, removida ao final, e a senha não é persistida; transações confirmadas e metadados da importação são salvos.
- **Backup protegido por senha (`.fnb`)** usa criptografia autenticada portátil. JSON avançado e CSV continuam legíveis e sem proteção pelo app. Senha errada ou arquivo inválido não podem substituir o banco. Veja [formato e limites de segurança](docs/BACKUP_SEGURANCA.md).
- O banco Room não tem criptografia adicional nem bloqueio interno por PIN/biometria. Um aparelho desbloqueado permite acesso ao app.
- Um modelo importado pelo usuário é carregado por um runtime nativo. A inicialização detecta algumas incompatibilidades, mas não garante qualidade de inferência nem segurança do bundle; use arquivos de origem confiável.

## Correções, importações e lembretes

- A revisão oferece filtros **Todos**, **Sem categoria**, **Sugestão incerta**, **Possível duplicata** e **Excluídos**. Em **Selecionar lançamentos**, marque itens ou selecione os visíveis e aplique uma categoria em lote; a seleção precisa reunir lançamentos do mesmo tipo. Trocar de filtro limpa a seleção. Sugestões com confiança abaixo de 75% são sinalizadas para conferência; essa indicação não representa uma precisão medida do modelo.
- Cada possível duplicata mostra o lançamento do arquivo ao lado dos registros correspondentes no histórico, incluindo data, valor, tipo, categoria e identificação disponível. **Excluir** retira apenas aquela linha da importação; **Reincluir** desfaz a escolha antes de salvar. Os totais e as exigências de categoria/OCR consideram os itens incluídos, e manter possíveis duplicatas exige confirmação. O lote continua identificado pelo conjunto completo extraído do arquivo, inclusive as linhas excluídas, para evitar uma nova importação acidental do mesmo lote. As correções dos itens excluídos não são memorizadas.
- A Início apresenta **Resultado registrado no mês**, receitas e despesas, calculados a partir dos lançamentos disponíveis. Esse resultado pode abranger apenas parte da movimentação. Quando ainda não há transações em todo o banco, a tela explica o fluxo e oferece **Importar primeira fatura ou extrato**; meses vazios de quem já tem histórico oferecem uma nova importação sem repetir a apresentação inicial.
- Ao corrigir uma categoria ou tipo e confirmar a importação, o app memoriza a escolha para a mesma descrição e tipo de entrada. A comparação ignora caixa, acentos e espaços repetidos, mas preserva números e pontuação. Só os lançamentos sem memória seguem para a IA/regras. Editar a categoria de uma transação salva também atualiza a memória. Correções de valor/data, sozinhas, não ensinam uma categoria.
- Em Configurações, **Histórico de importações → Desfazer importação** remove atomicamente as transações ainda vinculadas ao lote, inclusive as editadas. Outros lotes e lançamentos manuais são preservados. O arquivo pode ser importado novamente; a memória das correções permanece. Importações anteriores à versão 8 do banco não têm vínculos e não podem ser desfeitas automaticamente. A opção **Apagar memória das correções** não apaga transações.
- Na revisão, **Identificar conta e conferir total** permite identificar a conta/cartão e informar o total de despesas do documento. A diferença exige conferência explícita antes de salvar. Este campo não extrai automaticamente o total da fatura: não use saldo, pagamentos ou transferências. Use sempre a mesma identificação para cada conta/cartão: a comparação normaliza composição Unicode, caixa e espaços repetidos. Lotes sem identificação compartilham a origem não identificada. Identificações diferentes permitem lotes iguais, mantendo o aviso de sobreposição para revisão explícita. A identificação salva no lote define sua origem e não muda ao editar a identificação de um lançamento. Não representa conexão bancária nem saldo individual de conta.
- A Início compara as despesas registradas por categoria com o mês anterior e oferece acesso aos lançamentos. Não presume gasto zero quando não há histórico. Meses incompletos são identificados como uma limitação da comparação.
- Os lembretes locais dizem **Já faturou esse mês?** no último dia de cada mês e no dia 05, às 9h do aparelho. Configurações → **Lembretes de faturas** permite desativá-los. Android 13 ou superior solicita permissão; sem permissão, nenhuma notificação é enviada. O agendamento é refeito após reiniciar, atualizar o app ou alterar hora/fuso; o Android pode atrasar o horário por economia de bateria. Não exige internet nem permissão de alarmes exatos.
- O banco usa schema 10, com migração que preserva os dados. O backup JSON v5 preserva memória, identificação, vínculos de importação e a chave original de categorização de cada lançamento; backups v1–v4 continuam aceitos. A preferência de notificações pertence à instalação, não ao backup financeiro.

## Desenvolvimento

Requisitos: JDK 17 completo, Android SDK Platform 36 e Build Tools 35.0.0, com `ANDROID_HOME` ou `sdk.dir` em `local.properties`. Use o wrapper Gradle na raiz do repositório.

```bash
./gradlew testStandaloneDebugUnitTest
./gradlew lintStandaloneDebug
./gradlew assembleStandaloneDebug
```

Se o Gradle informar que o Java não oferece `JAVA_COMPILER`, o runtime selecionado não contém o compilador `javac`. Aponte `JAVA_HOME` para um JDK completo e confirme com `"$JAVA_HOME/bin/javac" -version`. Nesta máquina, o JDK incluído no Android Studio está em `/opt/android-studio/jbr`:

```bash
JAVA_HOME=/opt/android-studio/jbr ./gradlew testStandaloneDebugUnitTest
```

Esse caminho depende da instalação local; não o fixe nas configurações compartilhadas do Gradle. No Android Studio, selecione o mesmo JDK em **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK**.

O CI também compila a release com `assembleStandaloneRelease` e executa `connectedStandaloneDebugAndroidTest` em um emulador API 35. O APK de debug fica em `app/build/outputs/apk/standalone/debug/`. As versões efetivas de Kotlin, KSP, Room e LiteRT-LM estão em [`gradle/libs.versions.toml`](gradle/libs.versions.toml); consulte esse arquivo antes de atualizar dependências ou a documentação.

Os testes JVM cobrem modelos, casos de uso, parsers, ViewModels e roteamento entre IA e regras. A [validação de otimização](docs/VALIDACAO_OTIMIZACAO.md) registra os cenários sintéticos e os limites das medições. Testes instrumentados cobrem migração Room e a permissão de rede. **Isso ainda não comprova qualidade de categorização, respostas do modelo, compatibilidade de faturas PDF reais ou desempenho do motor em aparelhos reais.**

### Consistência de importação e backup

O CSV bancário usa a direção explícita de débito ou crédito quando a coluna a fornece. Um sinal explícito incompatível com a direção e o tipo de documento recusa o lote. Uma coluna `Tipo` usada para operações como PIX e TED continua compatível com valores interpretados pelo sinal. Em OFX, a presença de `CORRECTFITID` ou `CORRECTACTION` recusa o lote inteiro. Nesses casos, importe um extrato consolidado do banco ou use CSV ou PDF.

Descrições `MEMO` e `NAME` de OFX preservam o conteúdo completo até a próxima tag, inclusive quebras de linha em XML e SGML. Lançamentos novos que diferem nas linhas seguintes mantêm descrições, memórias e hashes próprios. Para reconhecer dados gravados por versões anteriores, o parser mantém referências transitórias às primeiras linhas antigas, antes e depois da decodificação de entidades. Memórias de primeiras linhas truncadas não são aplicadas automaticamente ao texto completo; novas correções usam a descrição inteira. Memórias antigas de entidades codificadas continuam aceitas quando não houve truncamento. Como versões antigas descartaram o restante do texto, essas referências históricas são conservadoras e não conseguem reconstruir a descrição perdida.

CSVs devem ter uma única coluna identificável de data, descrição e valor, e cada linha precisa ter a mesma quantidade de campos do cabeçalho. Colunas extras sem ambiguidade são aceitas; valores decimais ou descrições contendo o separador devem estar entre aspas. OFX respeita o charset do cabeçalho SGML ou da declaração XML; texto com bytes inválidos para a codificação é recusado, sem substituir caracteres silenciosamente. Descrições OFX decodificam uma vez as entidades XML padrão e referências numéricas Unicode; entidades desconhecidas ou inválidas são recusadas, sem resolver DTDs ou entidades externas. Hashes de lotes, sobreposições e memórias salvos antes dessa decodificação continuam reconhecidos ao reler um OFX.

A leitura de CSV mantém apenas a linha atual antes de convertê-la em lançamento; linhas vazias são descartadas durante a leitura, inclusive antes do cabeçalho, sem remover quebras de linha dentro de campos entre aspas. Além do limite de 10 MB por arquivo, cada CSV aceita até 50.000 lançamentos, 64 colunas e 128 KiB em UTF-8 por campo decodificado. O limite persistido de 64 KiB para descrições continua sendo aplicado após retirar o escape de proteção do CSV nativo. Exceder um limite recusa o arquivo inteiro com orientação para reduzi-lo, sem importar parcialmente os primeiros registros. A leitura confere cancelamento entre blocos de caracteres e registros.

A leitura OFX percorre as tags em sequência e verifica cancelamento entre registros e blocos de caracteres, inclusive em campos longos, comentários e declarações. Cada transação precisa ter exatamente um `TRNAMT` e um `DTPOSTED`; repetições recusam o lote mesmo quando os valores são iguais. Blocos `ORIGCURRENCY` aninhados, sem fechamento ou que atravessam transações também são recusados. A moeda original de uma conversão não substitui a moeda do valor faturado. Folhas SGML sem fechamento e arquivos XML continuam aceitos; os blocos financeiros abertos, incluindo `STMTTRN`, `BANKTRANLIST` e `OFX`, precisam fechar na ordem correta. Arquivos truncados são recusados por inteiro, mesmo após lançamentos válidos. Comentários e instruções entre campos continuam aceitos; se houver continuação de texto após um comentário ou instrução dentro de um campo, o arquivo é recusado com orientação para exportá-lo novamente. DTD e CDATA são recusados, sem resolução de entidades externas.

Cabeçalhos monetários de CSV e PDF reconhecem também nomes de moedas estrangeiras, como dólares, euros, libras e ienes, ignorando caixa e acentos. Esses nomes não autorizam interpretar o valor como reais. Cabeçalhos em reais/BRL e valores sem unidade explícita mantêm as convenções existentes; tabelas PDF com uma coluna inequívoca de valor convertido em reais continuam aceitas.

A reconstrução de tabelas PDF usa a mesma validação de moeda nos layouts comuns e nos extratos com descrições centralizadas. Unidades como `Valor (USD)`, `Débito (EUR)` e `Crédito (USD)` são conferidas mesmo quando o PDF separa o nome, os parênteses e a moeda em palavras distintas. Colunas estrangeiras exigem uma conversão explicitamente identificada em reais; uma coluna com moedas conflitantes ou uma página posterior inválida recusa o arquivo inteiro. Testes permanentes cobrem a reconstrução geométrica e a extração de PDFs gerados, incluindo os casos válidos em BRL.

A checagem de sobreposições compartilha os grupos de registros históricos entre lançamentos iguais, incluindo descrições históricas alternativas, sem copiar todos os candidatos para cada linha. Todos os candidatos permanecem disponíveis, com IDs únicos e a ordem de comparação preservada. A revisão mostra cinco registros históricos por página, com controles para percorrer os demais e a contagem total.

PDFs validam também declarações globais de moeda antes de remover capas bancárias, incluindo rótulo e moeda em linhas separadas e expressões como `Valores expressos em dólares`. Uma declaração estrangeira, desconhecida ou sem valor exige valores explicitamente em reais; tabelas reconhecem `Moeda` e `Currency` e recusam colunas de moeda repetidas. Unidades como centavos, milhares e porcentagens são recusadas antes da interpretação em reais; centavos continuam aceitos no formato completo do CSV nativo. Uma transação incompleta não pode atravessar totais, saldos ou mudanças de seção para receber um valor posterior. Os testes de contrato de importação exercitam variações de rótulos, espaços, quebras de linha, tipos de documento e resumos; o CI executa esses testes junto da suíte JVM, e os testes Android incluem a extração de PDFs gerados. Os buffers internos do PDFBox são limitados a 32 MiB desde a abertura, inclusive para objetos comprimidos que expandem antes da contagem de páginas. Exceder esse orçamento recusa o documento sem liberar uma importação parcial. Os demais [limites de PDF e OCR](docs/PDF_IMPORTACAO.md#ocr-local-e-recursos) continuam aplicados.

Datas brasileiras são validadas estritamente, incluindo anos bissextos. Valores zero são aceitos na importação e na restauração; o tipo determina o sinal financeiro e a magnitude armazenada nunca pode ser negativa. A impressão digital v5 ordena os lançamentos, preserva a multiplicidade de compras idênticas e considera a identificação da conta/cartão. Diferenças de caixa, espaços e composição Unicode nessa identificação não criam outra conta. Use a mesma identificação nas reimportações; lotes sem identificação são comparados entre si. Hashes históricos v1 a v4 continuam sendo conferidos apenas para a mesma identificação de origem. A versão v5 mantém os aliases de texto truncado e reconhece os hashes gerados para CSVs antigos que determinavam a direção apenas pelo sinal. Importações antigas com hash v1 continuam bloqueadas quando o arquivo mantém a ordem original; se forem reordenadas, dependem do aviso de sobreposição, pois os registros antigos não guardam o lote original para recalcular o hash.


O CSV exportado pelo próprio Finances, identificado pelo cabeçalho completo com `Valor (Centavos)` e `Tipo`, pode ser reimportado preservando valor e tipo, inclusive transferências e valores zero, independentemente da seleção de extrato/fatura. O tipo declarado tem prioridade sobre as sugestões automáticas e pode ser editado na revisão. A importação passa pela revisão normal e não restaura IDs, categorias ou observações do arquivo; para restauração completa, use o backup JSON. No formato nativo v2, o importador retira exatamente o apóstrofo de proteção e recupera o texto original; CSVs nativos antigos mantêm esse apóstrofo. A exportação consulta o banco para o mês escolhido ao abrir o seletor de destino, sem depender da lista já carregada na tela. Transações e categorias são lidas na mesma transação do banco, impedindo misturar dados anteriores e posteriores a uma restauração.

A soma das magnitudes de todos os lançamentos registrados, incluindo transferências, deve caber em um Long de centavos. O banco valida inserções, edições e lotes atomicamente; a revisão mostra erro e bloqueia a confirmação quando o lote excede esse limite. A restauração recusa texto UTF-8 inválido e valida somas e datas antes de substituir os dados; somente anos de 1 a 9999 são aceitos. A geração e a restauração de backups pelo seletor processam o JSON em fluxo, sem o antigo teto de 50 MB. O conjunto de registros ainda ocupa memória, de modo que bases muito grandes dependem da capacidade do aparelho. Campos de texto persistidos são limitados a 64 KiB em UTF-8; entradas maiores são recusadas antes de gravar ou substituir dados, sem truncamento silencioso.

Lançamentos zerados importados ou restaurados podem ser editados mantendo o valor zero. A criação manual continua exigindo valor maior que zero. Inclusões, edições e lotes conferem a existência e o tipo atual da categoria na mesma transação que grava os lançamentos. Categorias incompatíveis são recusadas sem alterar transações, totais ou memórias de correção.

O formulário de novo lançamento captura a revisão dos dados antes da primeira consulta de categorias e aguarda uma restauração em andamento antes de carregar esse cadastro. Salvar verifica essa revisão dentro da transação Room de inclusão. Uma restauração invalida o formulário aberto e os salvamentos enfileirados, mesmo quando o backup reutiliza IDs, tipos e nomes de categorias. Nesse caso, volte e abra um novo lançamento para conferir os dados atuais; observar ou selecionar novamente a categoria no formulário antigo não remove a proteção. Essa revisão é transitória: não altera o schema nem o formato do backup.


### Categorias e navegação por mês

Em **Categorias na barra inferior → Criar categoria**, informe o nome e o tipo (despesa, receita ou transferência). Categorias sem movimento também aparecem na lista e ficam disponíveis no formulário de lançamento. Nomes repetidos no mesmo tipo são recusados. A inicialização do banco disponibiliza as categorias antes da primeira leitura; a restauração preserva categorias personalizadas e completa os tipos essenciais ausentes.

O botão **Ver transações**, abaixo das transações recentes da Início, abre o histórico no mês exibido. Alterar o mês em qualquer tela atualiza todas as visões mensais. A seleção fica salva e também é preservada no retorno de uma edição.

A abertura do app tem uma transição breve e os controles de navegação compartilham o estilo de vidro. A barra inferior cresceu aproximadamente 15%, com ajuste proporcional para telas estreitas. Os cards do histórico usam uma coluna financeira consistente, altura mínima adaptada à fonte e menu secundário de ações; valores extensos podem expandir o card para preservar a leitura.


A [auditoria de design](docs/ARCHITECTURE.md#auditoria-de-design-e-feedback-de-processamento) registra os problemas e as correções por tela. Durante a importação, a tela mostra a etapa atual, uma animação contínua de atividade e o tempo decorrido. Na categorização, a contagem e a barra refletem os lançamentos concluídos; enquanto o primeiro lote aguarda resposta, a barra permanece indeterminada. O avanço medido é suavizado e a animação continua entre lotes; voltar cancela o processamento. O formulário usa campos retangulares para data e ícone, com prévia do símbolo selecionado e cores da paleta ativa. O seletor de categoria inclui **Nova categoria**, que cria e seleciona uma categoria do tipo do lançamento sem perder os dados preenchidos. As categorias têm cores suaves e estáveis nos cards, detalhes, ícones de transações e gráficos; abrem seus lançamentos no mês selecionado. O seletor de mês tem largura ajustada ao texto.

O card **Visão do mês** usa as cores da paleta ativa, incluindo gradiente, borda, sombra e contraste do texto, em temas claro e escuro.

Se um backup for restaurado enquanto um lançamento estiver aberto para edição, salvar ou excluir nesse formulário será recusado. Volte e abra novamente o lançamento para trabalhar com os dados restaurados. PDFs preservam a continuação da descrição também quando o valor aparece na primeira linha; possíveis correspondências com descrições truncadas de importações antigas continuam sinalizadas para revisão. O CSV exportado protege também fórmulas precedidas por caracteres de controle, mantendo o texto original na reimportação do formato nativo.

A restauração também invalida revisões de importação iniciadas antes dela: selecione o arquivo novamente para conferir os dados atuais. Backups com nomes de arquivo ou mensagens vazios e metadados com datas inválidas são recusados antes de substituir o banco. Em PDFs de extrato, títulos genéricos como “Lançamentos” e “Demonstrativo” não bastam para aplicar a convenção de sinais de uma fatura.

As paletas incluem vermelho/laranja, laranja, azul, verde, roxo, rosa, turquesa, dourado e preto e branco. A opção preto e branco adapta os destaques ao fundo claro ou escuro; o check de importação concluída acompanha a paleta ativa.

O histórico de importações também protege confirmações de desfazer anteriores a uma restauração, inclusive quando os IDs e metadados do lote são reutilizados. A inserção dos exemplos ocorre em uma única transação, com categorias atuais e deduplicação mensal; solicitações antigas são recusadas após restauração. As telas de importação de extratos e modelos consultam o nome do arquivo em IO e mostram erros do provedor sem encerrar a interface. Veja os detalhes na [arquitetura](docs/ARCHITECTURE.md#proteção-do-histórico-inserção-de-exemplos-e-consulta-de-documentos).


## Consolidação e validação para beta

O [registro da consolidação](docs/CONSOLIDACAO_BETA.md) distingue a linha de base e as correções verificadas. A [matriz de preparação para beta](docs/BETA_READINESS.md) lista evidências, riscos e roteiro físico; a existência de APKs não significa aprovação para distribuição.

O [benchmark de categorização](docs/CATEGORIZACAO_BENCHMARK.md) usa dados sintéticos públicos e compara regras, memória e fluxo combinado, sem exigir LLM. Para repeti-lo:

```bash
./gradlew testStandaloneDebugUnitTest --tests '*CategorizationBenchmarkTest'
```

Validação dos dois flavors (JDK 17+, SDK 36):

```bash
./gradlew testStandaloneDebugUnitTest testPlayDebugUnitTest lintStandaloneDebug lintPlayDebug assembleStandaloneDebug assembleStandaloneRelease assemblePlayDebug assemblePlayRelease
python3 scripts/verify_android_privacy.py
./gradlew -PisolatedDeviceValidation=true connectedStandaloneDebugAndroidTest connectedPlayDebugAndroidTest
```

A última linha precisa de emulador/dispositivo e usa o pacote `.validation` para preservar a instalação pessoal. Os APKs debug ficam em `app/build/outputs/apk/<flavor>/debug/`; após validação isolada, refaça `assembleStandaloneDebug assemblePlayDebug` sem a propriedade para gerar os IDs normais e repita o verificador de privacidade. Instale com `adb install -r <apk>`. Se houver incompatibilidade de assinatura, não desinstale a instalação com dados para contornar o erro: preserve e confira um backup e use o pacote isolado de validação. APK release local otimizado precisa de assinatura para distribuição; nenhuma chave privada ou publicação foi adicionada nesta rodada. Os flavors continuam sem integração de asset pack da Play Store.
