# Plano de otimização do Finances

## Objetivo

Reduzir travamentos perceptíveis no Samsung Galaxy S21, sobretudo ao abrir o Histórico de transações e alternar entre Início, Gráficos, Transações e Chat, preservando cores, tipografia, dimensões, transparências, desfoque e animações da interface atual. Os pontos abaixo são hipóteses extraídas do código; a ordem final depende de medições no aparelho em que o problema ocorre.

## 0. Estabelecer uma linha de base no aparelho

- Registrar variante do Galaxy S21, versão do Android, memória e taxa de atualização da tela. Reproduzir os travamentos com uma base fixa: mês vazio, mês com centenas de transações, Chat com histórico longo e CSV/OFX/PDF pequeno e grande.
- Medir abertura a frio e a quente, troca entre Início, Gráficos, Transações e Chat, rolagem da Início e do Histórico, seleção de mês, importação e envio de mensagem. Capturar tempo até a primeira resposta visual, quadros lentos, pausas da thread principal, uso de memória e tempo de inferência.
- Usar System Trace/Perfetto e o profiler do Android Studio no aparelho; automatizar os cenários repetíveis com Macrobenchmark se o gargalo persistir. Comparar uma build de produção ou equivalente, evitando tirar conclusões apenas da build debug.
- Registrar capturas de tela e vídeos curtos dos mesmos estados antes das alterações. Não registrar dados financeiros reais nos artefatos de medição.

**Estado:** o S21 SM-G991B (Android 15) foi detectado. A versão anterior abriu em cerca de 3,0 s pelo `am start -W`, que retornou `LaunchState: UNKNOWN`; `gfxinfo` não entregou os dados de quadros. Não há comparação confiável de fluidez antes/depois. Testes instrumentados executados no aparelho desinstalaram o app e apagaram seus dados locais; não repetir a tarefa Gradle `connectedAndroidTest` em aparelho com dados pessoais. A APK foi reinstalada. Testes instrumentados futuros devem usar emulador ou aparelho dedicado; a fluidez da interface pode ser avaliada no S21 com a base sintética abaixo, sem limpar os dados.

**Base sintética no S21:** foram acrescentadas 600 transações identificadas por `SIMULADO`, sem substituir lançamentos existentes: 360 em setembro de 2026 e 20 por mês de setembro de 2025 a agosto de 2026. A inserção pontual no Room verificou 600 registros sintéticos; a APK auxiliar foi removida e o app principal continuou instalado. O CSV gerado também está em `Downloads/finances_transacoes_simuladas_2026-09.csv` no aparelho. Evitar importá-lo novamente para não duplicar os lançamentos.

## 1. Remover trabalho pesado da thread principal

**Importação:** a leitura limitada já roda em `Dispatchers.IO`; parsing e categorização também saíram da thread principal. CSV/OFX/PDF têm limite de 10 MB, e escolher outro arquivo cancela a seleção anterior. O parser ainda recebe um `ByteArray` completo. Medir o custo de manter os bytes, texto e linhas em memória; preferir leitura incremental se arquivos maiores forem suportados.

**Exportação:** CSV/JSON e backup/restauração já usam despachantes de fundo para leitura, serialização e escrita. Medir esses fluxos com arquivos grandes antes de outros ajustes.

**Aceite:** importação e exportação deixam a navegação e o indicador de carregamento responsivos; valores, datas e descrições permanecem idênticos nos testes de regressão.

## 2. Medir e reduzir custo de desenho sem alterar a aparência

- O `hazeSource` cobre páginas inteiras em `MainTabsScreen` e a coluna rolável da Início em `DashboardScreen`; a barra inferior e o seletor de mês aplicam `hazeEffect` com raio de 24 dp. Medir o custo de GPU/RenderThread durante rolagem e gesto horizontal. Se for relevante, limitar a captura à área visível atrás dos controles e evitar camadas ou recálculos de desfoque desnecessários.
- Verificar recomposições da Início e do Chat. O rascunho do Chat participa de um `uiState` que também contém todo o histórico; isolar o estado do campo de texto se cada tecla estiver recompondo a lista. Manter `LazyColumn`, chaves estáveis e posição de rolagem.
- Comparar capturas antes/depois nos estados claro/escuro e durante rolagem: mesma forma, cor, opacidade, nitidez percebida e movimento. Não remover o efeito de vidro como atalho; qualquer substituição visual exige equivalência verificada no aparelho.

**Ajuste aplicado:** o `HorizontalPager` tem quatro páginas (Início, Gráficos, Transações e Chat) e mantém uma página adjacente composta em cada direção. A barra inferior anima trocas adjacentes em 220 ms e salta diretamente entre páginas distantes. Medir no S21 a fluidez e o custo de memória antes de novos ajustes.

**Também implementado:** a coleta do rascunho ficou dentro do compositor do Chat, separada da lista de mensagens. O posicionamento inicial no fim do histórico passou a ser imediato; mensagens novas ainda animam a rolagem. O raio, cores, transparências e formas do efeito de vidro continuam iguais; qualquer mudança no `hazeSource` depende de trace e comparação visual no S21.

**Aceite:** queda mensurável de quadros lentos na troca de páginas e na rolagem, sem diferença visual perceptível nas capturas e no uso normal.

## 3. Consultar somente os dados necessários

- `DashboardViewModel` recebe todas as transações do mês, ordena a lista novamente e usa apenas seis. Criar consulta Room com `ORDER BY date DESC, id DESC LIMIT 6` para a Início; manter a consulta completa para o Histórico.
- Avaliar um índice por data e id em `transactions` após medir as consultas em meses grandes. Uma mudança de schema exige migração, schema exportado e teste instrumentado.
- O Chat lê e transforma todo o histórico a cada atualização. Se o histórico longo aparecer nos traces, introduzir paginação ou carregamento por blocos sem alterar a ordem e o visual das mensagens.

**Aceite:** a Início mantém exatamente os mesmos seis lançamentos e totais; consultas e alocações diminuem em bases grandes; migração passa nos testes.

**Primeiro ajuste aplicado:** a Início já usa `LIMIT 6` no Room e o schema 4 acrescenta um índice por data e id, usado pela consulta ordenada do Histórico. Confirmar o ganho no S21 com a mesma base antes/depois.

**Também implementado:** o Chat consulta inicialmente 100 mensagens e carrega mais 100 ao tocar em “Carregar mensagens anteriores”. O schema 5 indexa a ordenação do histórico. Nenhuma mensagem é apagada.

## 4. Reduzir a latência da IA local

- `LiteRtLocalAiRepository` inicializa e libera um `Engine` a cada mensagem; medir separadamente carga do modelo e geração da resposta. Se a carga dominar, manter uma instância reutilizável enquanto o app estiver ativo, com fila de acesso, liberação em baixa memória e tratamento de cancelamento.
- Medir memória de pico e latência em aparelho recente e intermediário antes de escolher cache permanente. Preservar o fallback por regras e os limites de privacidade atuais.

**Aceite:** segunda pergunta responde mais rápido sem crescimento contínuo de memória, falhas de inferência ou alteração dos valores financeiros apresentados.

## Ordem e critérios de conclusão

1. Medir no Galaxy S21 a abertura do Histórico e a troca entre Início, Gráficos, Transações e Chat, validando os primeiros ajustes de consulta e pager.
2. Atacar o gargalo confirmado de desenho ou recomposição, preservando a estética por comparação visual.
3. Medir o uso de memória da importação e o tempo de exportação com arquivos grandes; a leitura e a escrita já foram movidas para despachantes de fundo.
4. Medir o Chat com histórico longo para avaliar os blocos de 100 mensagens; otimizar a vida útil do modelo após medir seu custo real.

Comparar cada etapa com a mesma base de dados e o mesmo aparelho. Como meta inicial para uma tela de 60 Hz: reduzir em pelo menos 30% o número de quadros acima de 16,7 ms nos fluxos afetados e eliminar pausas da thread principal acima de 100 ms causadas por leitura, parsing ou exportação. Registrar antes/depois e ajustar a meta à taxa de atualização do aparelho. Testes JVM, build, Lint e testes instrumentados de migração continuam como verificações de regressão; eles não substituem a medição visual e de fluidez no celular.
