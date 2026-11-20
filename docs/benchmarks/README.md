# Evidências sintéticas de desempenho

Arquivos gerados por testes executados na consolidação, sem descrições financeiras privadas, senhas ou modelos simulados. Metodologia e limitações: [PERFORMANCE_BETA.md](../PERFORMANCE_BETA.md) e [CATEGORIZACAO_BENCHMARK.md](../CATEGORIZACAO_BENCHMARK.md).

- `jvm-before-*`: regras/vocabulário já corrigidos, antes de preparar o catálogo por lote.
- `categorization-before-vocabulary.json` e `categorization-p1-vocabulary.json`: métricas agregadas efetivamente medidas antes/depois da expansão do vocabulário. Preservam os tempos sob carga compartilhada, sem atribuí-los a uma comparação controlada. Métricas por classe foram omitidas desses relatórios antigos porque o cálculo de F1 foi corrigido posteriormente; use os relatórios finais para F1/precisão/recall.
- `jvm-after-*`: preparação por lote, mesmas métricas de qualidade e mesmos hashes de corpus/catalogo. `jvm-after-repeat.json` é a repetição sem alteração das regras.
- `review-before-optimization.json`: getters originais, repetição sem teste Android concorrente.
- `review-after-optimization.json`: cache selecionado da lista incluída e totais; contagens/confirmação sem cache.
- `review-discarded-full-cache.json`: variante descartada por piora da primeira leitura.

Não use estes tempos como requisito de suporte de LLM ou como medição de desempenho em telefone. Heap é do processo JVM; GC, JIT e carga externa variam.

`android-api35-results.json` e `android-api35-device.json` foram exportados do ensaio opt-in executado no AVD após os builds. O modelo não foi executado por falta de capacidade; os três modos locais foram medidos. PSS é do processo completo, incluindo runner e bibliotecas dos testes PDF anteriores.

`validation-summary.json` resume os XMLs JUnit/lint finais, comandos concluídos e hashes dos quatro APKs normais verificados. Não contém conteúdo do corpus bancário privado.
