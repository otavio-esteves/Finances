# Avaliação reproduzível da categorização

O corpus `samples/categorization/corpus.json` contém 58 descrições **sintéticas**, brasileiras, com categoria e tipo esperados. Não foi derivado de descrições privadas. O catálogo do ensaio inclui Educação, uma categoria personalizada; o ensaio não altera as categorias instaladas pelo aplicativo.

Execute na raiz, com JDK 17 ou posterior:

```bash
./gradlew testStandaloneDebugUnitTest --tests '*CategorizationBenchmarkTest'
```

O relatório fica em `app/build/reports/categorization/results.json`. O teste compara regras, memória isolada (abstenção para desconhecidos) e o fluxo efetivo `SynthesizeStatementUseCase` com memória e regras, em contexto de fatura. As três correções de treinamento estão explícitas no teste, separadas dos demais casos; o relatório distingue `seen_correction` e `unseen`. Números de parcelas e identificadores permanecem diferentes. É possível substituir o categorizador na função de avaliação para comparar outra implementação com o mesmo corpus.

Cada execução usa uma passagem de aquecimento não medida e cinco repetições em lotes de até 20. Datas, descrições e centavos devem permanecer idênticos. Acurácia exige categoria **e tipo** corretos; `byCategory` contém suporte, precisão, recall e F1, com `null` para denominadores indefinidos. A classe sem categoria é explícita. `byCohort` e `sources` evitam atribuir cobertura de regras ao modelo ou à memória.

- `uncategorizedRate`: fração sem categoria sugerida.
- `correctionRate`: categoria/tipo errado **ou** categoria ausente, pois a confirmação exige preenchê-la, mesmo quando a abstenção foi a resposta segura esperada.
- `reviewRate`: categoria ausente ou pontuação abaixo de 0,75.
- `highConfidenceErrors` e sua taxa: erros entre sugestões com categoria e pontuação ≥ 0,75. Esse corte é uma heurística da interface, **não uma probabilidade calibrada**.
- Latência média, p50 e p95 por lote; tempo por transação é a média amortizada dos lotes, não o tempo isolado de cada inferência.
- Memória: heap usado do processo JVM antes/depois e pico observado com amostragem de 20 ms. Não representa alocações exclusivas do categorizador, RSS/PSS Android nem pico nativo exato. Pode variar com GC e carga da máquina.

## Linha de base medida nesta rodada

Medição no ambiente de desenvolvimento, antes da expansão do vocabulário; mesmo corpus de 58 casos:

| Modo | Acurácia | Sem categoria | Correção/preenchimento necessário | Erros de alta confiança | ms/transação amortizado |
|---|---:|---:|---:|---:|---:|
| Regras | 60,34% | 5,17% | 43,10% | 0 | 1,760 |
| Memória isolada | 8,62% | 94,83% | 94,83% | 0 | 0,324 |
| Memória + regras + síntese | 65,52% | 3,45% | 37,93% | 0 | 3,624 |

A memória isolada cobre apenas escolhas explícitas vistas; sua baixa cobertura total não significa que deva inferir descrições desconhecidas. Os três exemplos de correção vista tiveram 100% de acerto. Essas medidas são pequenas e sintéticas; não estimam a acurácia da população de usuários nem substituem um corpus de avaliação independente. Adicionar termos com base nesses erros melhora este conjunto e precisa de validação externa para demonstrar generalização.

Métricas agregadas originais preservadas em `benchmarks/categorization-before-vocabulary.json` e `benchmarks/categorization-p1-vocabulary.json`. O cálculo de F1 por classe foi corrigido depois dessas execuções; esses recortes históricos não incluem F1. Os relatórios finais usam o cálculo validado pela regressão de métricas.

## Modelo real e hardware

`model-status.json` registra `not_run` na execução JVM. O ambiente atual é um AVD x86_64, sem bundle compatível nem os requisitos de RAM/ABI da inferência. Nenhuma acurácia, latência ou memória de LiteRT-LM foi medida ou simulada.

Os testes Android opt-in de modelo real já existentes validam inicialização/inferência, mas não equivalem a este benchmark de qualidade. O teste `CategorizationDeviceBenchmarkTest` descrito abaixo conecta o avaliador compartilhado ao categorizador real e mede os modos separadamente, incluindo `SuggestionSource.AI`, PSS e latência no aparelho. O modelo só pode receber crédito pelas sugestões efetivamente originadas da IA; fallback integral não é execução de qualidade do modelo. Essa medição permanece pendente de aparelho e bundle.

## Resultado após as correções P1

No mesmo corpus e ambiente, regras: **94,83%**; memória isolada: **8,62%**; fluxo combinado: **100%**. Zero erros de alta confiança nos três modos. Os três casos de correção vista continuam corretos. A suíte total, incluindo benchmark e regressões de respostas inválidas do modelo, passou com 502 testes.

Os tempos amortizados desta segunda execução foram 9,176 / 0,423 / 10,665 ms, respectivamente. Houve execução concorrente de testes Android/OCR e crescimento do vocabulário; esses tempos não sustentam uma comparação isolada de desempenho com a primeira execução. Uma medição específica antes/depois da otimização fica registrada na etapa P4. Não foram obtidas métricas de modelo real.

## Mesmo avaliador no Android, com modelo opcional

O avaliador foi compartilhado entre os source sets JVM/Android (`src/sharedTest`), sem entrar no aplicativo de produção. `CategorizationDeviceBenchmarkTest` usa o mesmo corpus e catálogo, treinamento sintético isolado e cinco modos quando há hardware e bundle reais: regras, memória, combinado com regras, modelo com fallback de abstenção e combinado com modelo/regras/memória. A origem de cada sugestão permanece no relatório.

Monte e instale os APKs isolados conforme `samples/README.md`. Com ambos instalados, execute:

```bash
adb shell am instrument -w -r \
  -e class br.com.otavioesteves.finances.data.ai.CategorizationDeviceBenchmarkTest \
  -e categorizationBenchmark true \
  br.com.otavioesteves.finances.validation.test/androidx.test.runner.AndroidJUnitRunner
adb exec-out run-as br.com.otavioesteves.finances.validation cat files/categorization-benchmark/results.json > categorization-android-results.json
adb exec-out run-as br.com.otavioesteves.finances.validation cat files/categorization-benchmark/device.json > categorization-android-device.json
```

Para modelo real, acrescente `-e validationModelPath CAMINHO_ABSOLUTO_DO_BUNDLE_NO_APARELHO` **antes do nome do runner**. O arquivo precisa ser legível pelo app; use a importação de modelo da instalação isolada. O gate atual de RAM/ABI/armazenamento também é aplicado pelo ensaio e não é reduzido para forçar uma execução. Sem modelo/capacidade, os três modos locais rodam e o motivo de não executar o modelo é registrado; não há métricas zeradas simuladas.

`device.json` registra PSS do processo antes/depois e pico observado com amostragem de 100 ms durante cada modo, incluindo aquecimento/inicialização. Picos menores que o intervalo podem não ser observados; PSS não é memória exclusiva do modelo. O arquivo de resultados também mede heap Java e latências após aquecimento. Liberação do runtime ocorre em `finally`.

A execução instrumental não lê transações nem memórias privadas do banco. Preserve os JSONs de medição fora do corpus de casos de teste. Usar `am instrument` permite extraí-los antes de desinstalar o pacote de validação; `connectedAndroidTest` pode desinstalá-lo ao terminar. A ausência de sugestões `AI` exige registrar fallback, mesmo que o runtime tenha sido tentado.

O relatório por categoria inclui `accuracy` condicionada aos exemplos daquela classe (igual ao recall), além de precisão, recall e F1. F1 usa `2*TP/(preditos+suporte)`: uma categoria presente mas nunca sugerida recebe F1 zero; precisão sem predições é indefinida (`null`). Um teste de métricas com três respostas controladas confere erros de alta confiança, abstenção segura e essas contagens; ele não representa execução de modelo.

`environment.json` registra hashes SHA-256 do corpus serializado e do catálogo, JDK/arquitetura, horário e rótulo de implementação (`FINANCES_BENCHMARK_LABEL`, opcional). `device.json` acrescenta ABI/SDK, hash do bundle real quando fornecido e rótulo `implementationLabel` do runner. Compare implementações com hashes iguais; tempos e memória exigem contexto de carga/hardware.

`ImportReviewSummaryBenchmarkTest` mede leituras dos dados derivados da revisão com 6000 linhas, 5400 incluídas: primeira leitura de cada estado imutável e releitura do mesmo estado. O relatório `review-summary.json` contém média/mediana de 30 amostras, com checagens de valores exatos após edição, troca de tipo e exclusão/reinclusão. Não mede desenho Compose, FPS ou desempenho de dispositivo físico.

Execução Android desta rodada: três modos locais medidos no AVD API 35, com hashes de corpus/catalogo iguais aos da JVM. Regras mantiveram 94,83% e o fluxo combinado 100% nos 58 casos sintéticos. PSS/latências reais do processo e a razão de **não executar o modelo** estão em `benchmarks/android-api35-*.json` e em [PERFORMANCE_BETA.md](PERFORMANCE_BETA.md). O benchmark JVM agora bloqueia regressões nos casos locais já suportados; a função de avaliação de candidatos continua disponível sem exigir esse patamar de acurácia de um modelo novo.
