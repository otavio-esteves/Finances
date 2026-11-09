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
| Entrada de arquivos | Importação local de CSV/OFX/PDF de até 10 MB, com escolha entre extrato e fatura e revisão de tipo e categoria. A revisão mostra contagem e totais; pagamentos de fatura reconhecidos são sugeridos como transferência. PDF digital, digitalizado, misto e protegido por senha. OCR português local, reconstrução de tabelas por posição e colunas separadas de débito/crédito, com saldo identificado por cabeçalho. Datas sem ano exigem referência explícita; ambiguidades recusam o lote. Correção de descrição/data/valor e conferência obrigatória quando houve OCR. [Cobertura e limites](docs/PDF_IMPORTACAO.md). O lote é salvo de forma atômica; repetir os mesmos lançamentos é recusado, independentemente da ordem nos novos lotes. Possíveis sobreposições com o histórico exigem confirmação explícita. |
| Organização | Categorização por regras como fallback permanente; integração com LiteRT-LM para tentar categorizar com um modelo importado. A qualidade dessa inferência ainda precisa ser medida com extratos reais anonimizados. |
| Visualização | Início com saldo mensal, gráfico de composição por categoria e transações recentes; telas de categorias e histórico com seleção de mês. Em Gráficos, composição alterna Rosca/Pizza e as séries anuais alternam Barras/Linhas/Área, mantendo rosca e barras como padrões. |
| Dados | Room local, lançamentos manuais, ícone opcional por transação, exportação CSV/JSON e backup/restauração JSON, com leitura de até 50 MB. |
| Navegação | Início, Gráficos, Transações e Categorias formam quatro páginas lado a lado, com gesto horizontal e uma barra flutuante de quatro ícones. O mês selecionado é compartilhado com Categorias, seus detalhes e Histórico, e fica salvo ao reabrir o app. O botão circular à esquerda abre o menu lateral com o título Finances, Configurações e o aviso de privacidade; o botão circular à direita abre a importação e o acesso ao lançamento individual. |

Na página Gráficos, a ação **Adicionar exemplos** insere 60 lançamentos fictícios de janeiro a outubro de 2026, marcados com `[EXEMPLO]`. A ação **Remover** exclui apenas os exemplos identificados por esse marcador interno. Os exemplos entram nos totais do app enquanto estiverem salvos. Repetir a ação em dias diferentes preserva os exemplos já gravados, identificados por mês e descrição.

### Próximas entregas ligadas à ideia central

1. Validar os layouts reais anonimizados de **Nubank, PicPay, Banco do Brasil, Caixa e Inter**, ampliando o corpus de regressão. OCR local, senha, tabelas posicionadas e colunas de débito/crédito já estão implementados; os testes atuais representam famílias de layout, não certificam todos os formatos dessas instituições.
2. Medir e melhorar a categorização automática, inclusive o tratamento de lançamentos incertos e duplicatas em faturas parcialmente sobrepostas.
3. Ampliar as comparações mensais determinísticas já implementadas com novos insights a partir dos dados confirmados, sempre com números verificáveis no histórico.
4. Concluir a distribuição do modelo no flavor `play` e validar a experiência de ponta a ponta em aparelhos reais. Os flavors `play` e `standalone` existem, mas ainda têm o mesmo comportamento; o asset pack não foi integrado.

O [plano do motor local](docs/PLANO_MOTOR_IA_LOCAL.md) detalha as decisões e pendências de IA. A [arquitetura](docs/ARCHITECTURE.md) descreve os fluxos e contratos implementados.

Arquivos que falham na importação podem ser investigados em `samples/private/`, ignorado pelo Git. O [guia de amostras locais](samples/README.md) explica como registrar o erro e repetir a validação dos PDFs no Android com contagens e totais esperados.

## IA local e limites atuais

O motor escolhido para a integração atual é **LiteRT-LM**. O usuário pode importar manualmente um bundle `.litertlm` pela tela de modelo em Config. A importação copia o arquivo para o armazenamento privado do app, calcula seu SHA-256 e tenta inicializar o runtime antes de substituir o modelo anterior. A cópia do modelo verifica cancelamento e limita o tamanho ao espaço disponível, mantendo uma reserva de 2 GB livres e preservando o modelo anterior até validar o candidato. Uma falha remove o candidato incompleto. A leitura de um provedor bloqueado e a inicialização nativa só permitem conferir cancelamento quando a API retorna. A lista de hashes de modelos medidos ainda está vazia. Por isso, um modelo importado aparece como não verificado até que haja medições e hashes conhecidos; a inicialização sozinha não comprova qualidade das respostas.

A categorização usa IA local quando o modelo está disponível e regras como fallback. As regras reconhecem frases e palavras inteiras, normalizam acentos e incluem termos de estabelecimentos e serviços; correspondências ambíguas continuam com baixa confiança ou sem categoria para revisão. A inferência real e seu desempenho ainda exigem validação em dispositivo. O motor LiteRT-LM é reutilizado entre chamadas e liberado em segundo plano ou sob pressão de memória. A tela do Chat foi removida; o gerenciamento de modelos e a categorização das faturas continuam disponíveis. O banco mantém o histórico antigo para preservar a compatibilidade de backup e restauração.

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
- **Backup/exportação manual em JSON ou CSV não é criptografado pelo app.** A proteção do arquivo no destino escolhido cabe ao usuário.
- O banco Room não tem criptografia adicional nem bloqueio interno por PIN/biometria. Um aparelho desbloqueado permite acesso ao app.
- Um modelo importado pelo usuário é carregado por um runtime nativo. A inicialização detecta algumas incompatibilidades, mas não garante qualidade de inferência nem segurança do bundle; use arquivos de origem confiável.

## Correções, importações e lembretes

- A revisão oferece filtros **Todos**, **Sem categoria**, **Sugestão incerta**, **Possível duplicata** e **Excluídos**. Em **Selecionar lançamentos**, marque itens ou selecione os visíveis e aplique uma categoria em lote; a seleção precisa reunir lançamentos do mesmo tipo. Trocar de filtro limpa a seleção. Sugestões com confiança abaixo de 75% são sinalizadas para conferência; essa indicação não representa uma precisão medida do modelo.
- Cada possível duplicata mostra o lançamento do arquivo ao lado dos registros correspondentes no histórico, incluindo data, valor, tipo, categoria e identificação disponível. **Excluir** retira apenas aquela linha da importação; **Reincluir** desfaz a escolha antes de salvar. Os totais e as exigências de categoria/OCR consideram os itens incluídos, e manter possíveis duplicatas exige confirmação. O lote continua identificado pelo conjunto completo extraído do arquivo, inclusive as linhas excluídas, para evitar uma nova importação acidental do mesmo lote. As correções dos itens excluídos não são memorizadas.
- A Início apresenta **Resultado registrado no mês**, receitas e despesas, calculados a partir dos lançamentos disponíveis. Esse resultado pode abranger apenas parte da movimentação. Quando ainda não há transações em todo o banco, a tela explica o fluxo e oferece **Importar primeira fatura ou extrato**; meses vazios de quem já tem histórico oferecem uma nova importação sem repetir a apresentação inicial.
- Ao corrigir uma categoria ou tipo e confirmar a importação, o app memoriza a escolha para a mesma descrição e tipo de entrada. A comparação ignora caixa, acentos e espaços repetidos, mas preserva números e pontuação. Só os lançamentos sem memória seguem para a IA/regras. Editar a categoria de uma transação salva também atualiza a memória. Correções de valor/data, sozinhas, não ensinam uma categoria.
- Em Configurações, **Histórico de importações → Desfazer importação** remove atomicamente as transações ainda vinculadas ao lote, inclusive as editadas. Outros lotes e lançamentos manuais são preservados. O arquivo pode ser importado novamente; a memória das correções permanece. Importações anteriores à versão 8 do banco não têm vínculos e não podem ser desfeitas automaticamente. A opção **Apagar memória das correções** não apaga transações.
- Na revisão, **Identificar conta e conferir total** permite identificar a conta/cartão e informar o total de despesas do documento. A diferença exige conferência explícita antes de salvar. Este campo não extrai automaticamente o total da fatura: não use saldo, pagamentos ou transferências. A identificação também pode ser editada nos lançamentos e aparece no histórico; não representa conexão bancária nem saldo individual de conta.
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

CSVs devem ter uma única coluna identificável de data, descrição e valor, e cada linha precisa ter a mesma quantidade de campos do cabeçalho. Colunas extras sem ambiguidade são aceitas; valores decimais ou descrições contendo o separador devem estar entre aspas. OFX respeita o charset do cabeçalho SGML ou da declaração XML; texto com bytes inválidos para a codificação é recusado, sem substituir caracteres silenciosamente.

Datas brasileiras são validadas estritamente, incluindo anos bissextos. Valores zero são aceitos na importação e na restauração; o tipo determina o sinal financeiro e a magnitude armazenada nunca pode ser negativa. A impressão digital v3 ordena os lançamentos, preserva a multiplicidade de compras idênticas e considera a identificação da conta/cartão. Diferenças de caixa, espaços e composição Unicode nessa identificação não criam outra conta. Use a mesma identificação nas reimportações; lotes sem identificação são comparados entre si. Hashes antigos v2 e v1 são conferidos apenas para a mesma identificação de origem. Importações antigas com hash v1 continuam bloqueadas quando o arquivo mantém a ordem original; se forem reordenadas, dependem do aviso de sobreposição, pois os registros antigos não guardam o lote original para recalcular o hash.


O CSV exportado pelo próprio Finances, identificado pelo cabeçalho completo com `Valor (Centavos)` e `Tipo`, pode ser reimportado preservando valor e tipo, inclusive transferências e valores zero, independentemente da seleção de extrato/fatura. A importação passa pela revisão normal e não restaura IDs, categorias ou observações do arquivo; para restauração completa, use o backup JSON. Campos de texto protegidos contra fórmulas mantêm o apóstrofo de proteção na reimportação. A exportação consulta o banco para o mês escolhido ao abrir o seletor de destino, sem depender da lista já carregada na tela.

A soma das magnitudes de todos os lançamentos registrados, incluindo transferências, deve caber em um Long de centavos. O banco valida inserções, edições e lotes atomicamente; a revisão mostra erro e bloqueia a confirmação quando o lote excede esse limite. A restauração recusa texto UTF-8 inválido e valida somas e datas antes de substituir os dados; somente anos de 1 a 9999 são aceitos.

Lançamentos zerados importados ou restaurados podem ser editados mantendo o valor zero. A criação manual continua exigindo valor maior que zero.


### Categorias e navegação por mês

Em **Categorias na barra inferior → Criar categoria**, informe o nome e o tipo (despesa, receita ou transferência). Categorias sem movimento também aparecem na lista e ficam disponíveis no formulário de lançamento. Nomes repetidos no mesmo tipo são recusados. A inicialização do banco disponibiliza as categorias antes da primeira leitura; a restauração preserva categorias personalizadas e completa os tipos essenciais ausentes.

O botão **Ver transações**, abaixo das transações recentes da Início, abre o histórico no mês exibido. Alterar o mês em qualquer tela atualiza todas as visões mensais. A seleção fica salva e também é preservada no retorno de uma edição.

A abertura do app tem uma transição breve e os controles de navegação compartilham o estilo de vidro. A barra inferior cresceu aproximadamente 15%, com ajuste proporcional para telas estreitas. Os cards do histórico usam uma coluna financeira consistente, altura mínima adaptada à fonte e menu secundário de ações; valores extensos podem expandir o card para preservar a leitura.


A [auditoria de design](docs/ARCHITECTURE.md#auditoria-de-design-e-feedback-de-processamento) registra os problemas e as correções por tela. Durante a importação, a tela mostra a etapa atual, uma animação contínua de atividade e o tempo decorrido. Na categorização, a contagem e a barra refletem os lançamentos concluídos; enquanto o primeiro lote aguarda resposta, a barra permanece indeterminada. O avanço medido é suavizado e a animação continua entre lotes; voltar cancela o processamento. O formulário usa campos retangulares para data e ícone, com prévia do símbolo selecionado e cores da paleta ativa. O seletor de categoria inclui **Nova categoria**, que cria e seleciona uma categoria do tipo do lançamento sem perder os dados preenchidos. As categorias têm cores suaves e estáveis nos cards, detalhes, ícones de transações e gráficos; abrem seus lançamentos no mês selecionado. O seletor de mês tem largura ajustada ao texto.

O card **Visão do mês** usa as cores da paleta ativa, incluindo gradiente, borda, sombra e contraste do texto, em temas claro e escuro.

As paletas incluem vermelho/laranja, laranja, azul, verde, roxo, rosa, turquesa, dourado e preto e branco. A opção preto e branco adapta os destaques ao fundo claro ou escuro; o check de importação concluída acompanha a paleta ativa.
