# Finances

O Finances é um app Android de finanças pessoais que transforma lançamentos de **faturas de cartão e extratos bancários** em uma visão clara dos gastos. A experiência desejada é simples: receber o arquivo, identificar e categorizar as transações automaticamente, mostrar insights úteis e permitir que a pessoa tire dúvidas com uma IA local no **Chat, à direita de Gráficos**.

O processamento e os dados financeiros ficam no aparelho. A revisão das categorias antes de salvar os lançamentos mantém a pessoa no controle do resultado.

## Experiência central

1. **Receber a fatura ou o extrato.** A pessoa indica se o arquivo é uma fatura ou um extrato e o seleciona no seletor do Android. O parser aceita **CSV, OFX e PDFs digitais ou digitalizados** no formato data completa, descrição e valor, inclusive quando um lançamento ocupa até seis linhas consecutivas. Em CSV de fatura, valores positivos são compras e negativos são estornos; em CSV de extrato, o sinal segue débito e crédito. Datas abreviadas e meses por nome são aceitos quando o próprio documento informa um período, ano ou referência mensal inequívoca; páginas digitalizadas usam OCR local com modelo em português incluído no APK. PDFs com senha permitem informar a senha somente para aquela leitura. A revisão permite corrigir descrição, data e valor, e exige conferência explícita quando houve OCR.
2. **Organizar automaticamente.** O app extrai os lançamentos e sugere categorias. Quando há um modelo LiteRT-LM importado e disponível, tenta usá-lo; caso contrário, usa regras locais. A pessoa revisa e pode corrigir cada sugestão antes de confirmar a importação.
3. **Entender os gastos.** A Início mostra o saldo do mês, a composição por categoria e transações recentes. A visão de categorias e os filtros de mês ajudam a localizar os maiores gastos. **Insights escritos e proativos** ainda são uma meta do produto, não uma função pronta.
4. **Perguntar à IA.** O Chat fica depois de Transações, acessível pela barra inferior ou por gesto horizontal a partir da Início. Ele recebe saldo e totais por categoria do mês pedido; sem mês explícito, usa o mês corrente. Consultas sobre dias, intervalos ou lançamentos específicos ainda não são atendidas.

```text
Arquivo CSV/OFX/PDF → extração → sugestão de categoria → revisão → transações locais
                                                          ↓
                                      Início → Gráficos → Transações → Chat
```

O lançamento manual, a edição de transações e o backup complementam esse fluxo. A direção do produto está no uso de arquivos para evitar digitação lançamento por lançamento.

[Referência visual da Início e do Chat](<docs/ChatGPT Image Sep 19, 2026, 05_25_12 PM.png>) — imagem de conceito; não representa todas as funções já implementadas.

## O que funciona hoje

| Área | Estado atual |
|---|---|
| Entrada de arquivos | Importação local de CSV/OFX/PDF de até 10 MB, com escolha entre extrato e fatura e revisão de tipo e categoria. A revisão mostra contagem e totais; pagamentos de fatura reconhecidos são sugeridos como transferência. PDF digital, digitalizado, misto e protegido por senha. OCR português local, reconstrução de tabelas por posição e colunas separadas de débito/crédito, com saldo identificado por cabeçalho. Datas sem ano exigem referência explícita; ambiguidades recusam o lote. Correção de descrição/data/valor e conferência obrigatória quando houve OCR. [Cobertura e limites](docs/PDF_IMPORTACAO.md). O lote é salvo de forma atômica; repetir os mesmos lançamentos é recusado, independentemente da ordem nos novos lotes. Possíveis sobreposições com o histórico exigem confirmação explícita. |
| Organização | Categorização por regras como fallback permanente; integração com LiteRT-LM para tentar categorizar com um modelo importado. A qualidade dessa inferência ainda precisa ser medida com extratos reais anonimizados. |
| Visualização | Início com saldo mensal, gráfico de composição por categoria e transações recentes; telas de categorias e histórico com seleção de mês. Em Gráficos, composição alterna Rosca/Pizza e as séries anuais alternam Barras/Linhas/Área, mantendo rosca e barras como padrões. |
| Chat | Histórico persistido, carregado inicialmente em blocos de 100 mensagens, e respostas baseadas no saldo e nos totais por categoria de um mês consultado no Room. Aceita nomes de mês e formatos como `02/2026` e `2026-02`; um mês sem ano é interpretado como a ocorrência mais recente. Perguntas numéricas reconhecidas usam cálculos determinísticos, incluindo somas de categorias e exclusões explícitas como “sem Alimentação”; categorias cadastradas sem movimento retornam zero. Recortes não reconhecidos pedem esclarecimento; pedidos por estabelecimento sem dados detalhados recebem uma limitação explícita. Outras perguntas usam LiteRT-LM quando disponível ou regras como fallback. Não há recuperação de transações individuais, streaming nem insights proativos. |
| Dados | Room local, lançamentos manuais, ícone opcional por transação, exportação CSV/JSON e backup/restauração JSON, com leitura de até 50 MB. |
| Navegação | Início, Gráficos, Transações e Chat formam quatro páginas lado a lado, com gesto horizontal e uma pill flutuante de quatro ícones. O botão circular à esquerda abre o menu lateral, que fecha ao tocar fora; o botão circular à direita abre a importação. |

Na página Gráficos, a ação **Adicionar exemplos** insere 60 lançamentos fictícios de janeiro a outubro de 2026, marcados com `[EXEMPLO]`. A ação **Remover** exclui apenas os exemplos identificados por esse marcador interno. Os exemplos entram nos totais do app enquanto estiverem salvos.

### Próximas entregas ligadas à ideia central

1. Validar os layouts reais anonimizados de **Nubank, PicPay, Banco do Brasil, Caixa e Inter**, ampliando o corpus de regressão. OCR local, senha, tabelas posicionadas e colunas de débito/crédito já estão implementados; os testes atuais representam famílias de layout, não certificam todos os formatos dessas instituições.
2. Medir e melhorar a categorização automática, inclusive o tratamento de lançamentos incertos e duplicatas em faturas parcialmente sobrepostas.
3. Gerar **insights proativos** a partir dos dados confirmados, como categorias com maior peso e mudanças entre períodos, com números verificáveis no histórico.
4. Ampliar o Chat para perguntas sobre dias, intervalos e transações específicas, com recuperação de dados, respostas progressivas e menor tempo de espera.
5. Concluir a distribuição do modelo no flavor `play` e validar a experiência de ponta a ponta em aparelhos reais. Os flavors `play` e `standalone` existem, mas ainda têm o mesmo comportamento; o asset pack não foi integrado.

O [plano do motor local](docs/PLANO_MOTOR_IA_LOCAL.md) detalha as decisões e pendências de IA. A [arquitetura](docs/ARCHITECTURE.md) descreve os fluxos e contratos implementados.

## IA local e limites atuais

O motor escolhido para a integração atual é **LiteRT-LM**. O usuário pode importar manualmente um bundle `.litertlm` pela tela de modelo em Config. A importação copia o arquivo para o armazenamento privado do app, calcula seu SHA-256 e tenta inicializar o runtime antes de substituir o modelo anterior. A lista de hashes de modelos medidos ainda está vazia. Por isso, um modelo importado aparece como não verificado até que haja medições e hashes conhecidos; a inicialização sozinha não comprova qualidade das respostas.

A categorização e o Chat têm rotas próprias para usar a IA ou o fallback por regras. A inferência real e seu desempenho ainda exigem validação em dispositivo. Chat e categorização compartilham um motor LiteRT-LM, reutilizado entre chamadas e liberado ao receber notificação de segundo plano ou pressão de memória. O Chat recebe um resumo do mês solicitado, sem lançamentos individuais. Perguntas que dependem de uma transação específica ainda exigem recuperação detalhada.

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

- O app não declara `INTERNET`, desativa o backup automático do Android e solicita proteção de captura de tela com `FLAG_SECURE`. Há um teste instrumentado que verifica a ausência da permissão de rede no app instalado.
- Transações e histórico do Chat ficam no banco local. O arquivo original selecionado não é alterado; a leitura PDF pode usar uma cópia no cache privado, removida ao final, e a senha não é persistida; transações confirmadas e metadados da importação são salvos.
- **Backup/exportação manual em JSON ou CSV não é criptografado pelo app.** A proteção do arquivo no destino escolhido cabe ao usuário.
- O banco Room não tem criptografia adicional nem bloqueio interno por PIN/biometria. Um aparelho desbloqueado permite acesso ao app.
- Um modelo importado pelo usuário é carregado por um runtime nativo. A inicialização detecta algumas incompatibilidades, mas não garante qualidade de inferência nem segurança do bundle; use arquivos de origem confiável.

## Desenvolvimento

Requisitos: JDK 17 completo, Android SDK Platform 36 e Build Tools 35.0.0, com `ANDROID_HOME` ou `sdk.dir` em `local.properties`. Use o wrapper Gradle na raiz do repositório.

```bash
./gradlew testStandaloneDebugUnitTest
./gradlew lintStandaloneDebug
./gradlew assembleStandaloneDebug
```

O CI também executa `connectedStandaloneDebugAndroidTest` em um emulador API 35. O APK de debug fica em `app/build/outputs/apk/standalone/debug/`. As versões efetivas de Kotlin, KSP, Room e LiteRT-LM estão em [`gradle/libs.versions.toml`](gradle/libs.versions.toml); consulte esse arquivo antes de atualizar dependências ou a documentação.

Os testes JVM cobrem modelos, casos de uso, parsers, ViewModels e roteamento entre IA e regras. A [validação de otimização](docs/VALIDACAO_OTIMIZACAO.md) registra os cenários sintéticos e os limites das medições. Testes instrumentados cobrem migração Room e a permissão de rede. **Isso ainda não comprova qualidade de categorização, respostas do modelo, compatibilidade de faturas PDF reais ou desempenho do motor em aparelhos reais.**

### Consistência de importação e backup

Datas brasileiras são validadas estritamente, incluindo anos bissextos. Valores zero são aceitos na importação e na restauração; o tipo determina o sinal financeiro e a magnitude armazenada nunca pode ser negativa. A impressão digital v2 ordena os lançamentos e preserva a multiplicidade de compras idênticas. Importações antigas com hash v1 continuam bloqueadas quando o arquivo mantém a ordem original; se forem reordenadas, dependem do aviso de sobreposição, pois os registros antigos não guardam o lote original para recalcular o hash.
