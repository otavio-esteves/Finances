# Finances

O Finances é um app Android de finanças pessoais que transforma lançamentos de **faturas de cartão e extratos bancários** em uma visão clara dos gastos. A experiência desejada é simples: receber o arquivo, identificar e categorizar as transações automaticamente, mostrar insights úteis e permitir que a pessoa tire dúvidas com uma IA local no **Chat, à direita da Início**.

O processamento e os dados financeiros ficam no aparelho. A revisão das categorias antes de salvar os lançamentos mantém a pessoa no controle do resultado.

## Experiência central

1. **Receber a fatura ou o extrato.** A pessoa seleciona um arquivo no seletor do Android. Hoje o parser aceita **CSV e OFX**; suporte a PDF, comum em faturas de cartão, ainda está planejado. O suporte depende também do formato exportado pela instituição.
2. **Organizar automaticamente.** O app extrai os lançamentos e sugere categorias. Quando há um modelo LiteRT-LM importado e disponível, tenta usá-lo; caso contrário, usa regras locais. A pessoa revisa e pode corrigir cada sugestão antes de confirmar a importação.
3. **Entender os gastos.** A Início mostra o saldo do mês, a composição por categoria e transações recentes. A visão de categorias e os filtros de mês ajudam a localizar os maiores gastos. **Insights escritos e proativos** ainda são uma meta do produto, não uma função pronta.
4. **Perguntar à IA.** O Chat é a aba imediatamente à direita de Início na barra inferior. Ele responde com os dados locais disponíveis. Hoje recebe saldo e totais por categoria do **mês corrente**; consultas sobre outros meses ou lançamentos específicos exigem a ampliação do contexto planejada para o chat.

```text
Arquivo CSV/OFX → extração → sugestão de categoria → revisão → transações locais
                                                          ↓
                                      Início e categorias → Chat à direita
```

O lançamento manual, a edição de transações e o backup complementam esse fluxo. A direção do produto está no uso de arquivos para evitar digitação lançamento por lançamento.

[Referência visual da Início e do Chat](<docs/ChatGPT Image Sep 19, 2026, 05_25_12 PM.png>) — imagem de conceito; não representa todas as funções já implementadas.

## O que funciona hoje

| Área | Estado atual |
|---|---|
| Entrada de arquivos | Importação local de CSV/OFX, com revisão e correção das categorias sugeridas antes da confirmação. |
| Organização | Categorização por regras como fallback permanente; integração com LiteRT-LM para tentar categorizar com um modelo importado. A qualidade dessa inferência ainda precisa ser medida com extratos reais anonimizados. |
| Visualização | Início com saldo mensal, gráfico de composição por categoria e transações recentes; telas de categorias e histórico com seleção de mês. |
| Chat | Histórico persistido e respostas baseadas no saldo e nos totais por categoria do mês corrente. Com modelo disponível, usa LiteRT-LM; sem ele ou se a inferência falhar, usa respostas limitadas por regras. Não há recuperação de transações individuais, streaming nem insights proativos. |
| Dados | Room local, lançamentos manuais, exportação CSV/JSON e backup/restauração JSON. |
| Navegação | Barra inferior: **Início → Chat → Gráfico → Config**. O Chat fica à direita da Início; **não há gesto de swipe entre essas telas** na implementação atual. |

### Próximas entregas ligadas à ideia central

1. Ler **PDF de faturas e extratos** e ampliar a compatibilidade com arquivos reais de diferentes instituições.
2. Medir e melhorar a categorização automática, inclusive o tratamento de lançamentos incertos e possíveis duplicatas.
3. Gerar **insights proativos** a partir dos dados confirmados, como categorias com maior peso e mudanças entre períodos, com números verificáveis no histórico.
4. Ampliar o Chat para perguntas sobre períodos e transações específicas, com recuperação de dados, respostas progressivas e menor tempo de espera.
5. Concluir a distribuição do modelo no flavor `play` e validar a experiência de ponta a ponta em aparelhos reais. Os flavors `play` e `standalone` existem, mas ainda têm o mesmo comportamento; o asset pack não foi integrado.

O [plano do motor local](docs/PLANO_MOTOR_IA_LOCAL.md) detalha as decisões e pendências de IA. A [arquitetura](docs/ARCHITECTURE.md) descreve os fluxos e contratos implementados.

## IA local e limites atuais

O motor escolhido para a integração atual é **LiteRT-LM**. O usuário pode importar manualmente um bundle `.litertlm` pela tela de modelo em Config. A importação copia o arquivo para o armazenamento privado do app e calcula seu SHA-256. A checagem inicial de formato é apenas uma verificação de tamanho e existência; a lista de hashes de modelos validados ainda está vazia. Por isso, um modelo importado aparece como não verificado até que haja medições e hashes conhecidos.

A categorização e o Chat têm rotas próprias para usar a IA ou o fallback por regras. A inferência real e seu desempenho ainda exigem validação em dispositivo. O Chat atual recarrega o modelo a cada pergunta e só recebe um resumo do mês corrente; uma pergunta como “quanto gastei com mercado em fevereiro?” só poderá ser respondida de forma confiável após a implementação da consulta por período.

Para importar um modelo próprio, abra **Config → Gerenciar Modelo de IA → Selecionar arquivo do modelo** e escolha um bundle `.litertlm` já salvo no aparelho. O app ainda não oferece um link oficial de download. A checagem provisória de capacidade exige 6 GB de RAM, ABI `arm64-v8a` e 2 GB livres; aparelhos abaixo desse limite continuam com a categorização por regras. Esses limites ainda precisam ser medidos e revistos, especialmente para o aparelho intermediário previsto no plano.

O projeto **não declara a permissão `INTERNET`**. O arquivo é escolhido pelo seletor do Android; o destino de uma exportação ou backup também é escolhido pela pessoa e pode ser um provedor externo. O app não integra telemetria nem serviço de nuvem para processar dados financeiros.

## Tecnologias e organização

- Kotlin, Jetpack Compose e Material 3 para a interface.
- ViewModel e StateFlow para estado de tela.
- Camadas de domínio, dados e apresentação, com injeção manual em `AppContainer`.
- Room para persistência local; `Money` armazena valores em centavos (`Long`).
- Parsers locais de CSV/OFX e integração LiteRT-LM para inferência no dispositivo.
- Gradle Version Catalog para versões e dependências.

```text
UI Compose → ViewModels → casos de uso e contratos de domínio ← Room, parsers e IA local
```

## Privacidade e cuidados

- O app não declara `INTERNET`, desativa o backup automático do Android e solicita proteção de captura de tela com `FLAG_SECURE`. Há um teste instrumentado que verifica a ausência da permissão de rede no app instalado.
- Transações e histórico do Chat ficam no banco local. O arquivo original selecionado para importação não é persistido pelo fluxo atual; transações confirmadas e metadados da importação são salvos.
- **Backup/exportação manual em JSON ou CSV não é criptografado pelo app.** A proteção do arquivo no destino escolhido cabe ao usuário.
- O banco Room não tem criptografia adicional nem bloqueio interno por PIN/biometria. Um aparelho desbloqueado permite acesso ao app.
- Um modelo importado pelo usuário é carregado por um runtime nativo. A verificação inicial não garante compatibilidade nem segurança do bundle; use arquivos de origem confiável.

## Desenvolvimento

Requisitos: JDK 17 completo, Android SDK Platform 36 e Build Tools 35.0.0, com `ANDROID_HOME` ou `sdk.dir` em `local.properties`. Use o wrapper Gradle na raiz do repositório.

```bash
./gradlew testStandaloneDebugUnitTest
./gradlew lintStandaloneDebug
./gradlew assembleStandaloneDebug
```

O CI também executa `connectedStandaloneDebugAndroidTest` em um emulador API 35. O APK de debug fica em `app/build/outputs/apk/standalone/debug/`. As versões efetivas de Kotlin, KSP, Room e LiteRT-LM estão em [`gradle/libs.versions.toml`](gradle/libs.versions.toml); consulte esse arquivo antes de atualizar dependências ou a documentação.

Os testes JVM cobrem modelos, casos de uso, parsers, ViewModels e roteamento entre IA e regras. Testes instrumentados cobrem migração Room e a permissão de rede. **Isso ainda não comprova qualidade de categorização, respostas do modelo, compatibilidade de faturas PDF ou desempenho do motor em aparelhos reais.**
