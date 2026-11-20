# Medições de consolidação

Estas medições JVM usam dados sintéticos e foram executadas, não estimadas. O ambiente compartilhava CPU/memória com outros builds e com testes no AVD em parte das execuções. Tempos de processo JVM não comprovam FPS, consumo nativo nem desempenho em celular intermediário.

## Regras em lote

Antes da mudança, cada descrição normalizava os mesmos nomes de categoria para cada grupo de vocabulário. Agora o catálogo/vocabulário é preparado uma vez por lote, separado por tipo; expressões de normalização são compiladas uma vez. Palavras inteiras, prioridade de frase específica, ambiguidades, números, cancelamento e fallback permanecem.

Mesmo corpus/catalogo (hashes iguais), aquecimento e cinco repetições, lotes até 20:

| Modo | Antes: ms/transação amortizado | Após | Repetição após |
|---|---:|---:|---:|
| Regras | 2,263 | 0,392 | 0,263 |
| Memória isolada (sem mudança de implementação) | 0,076 | 0,217 | 0,142 |
| Memória + regras + síntese | 1,568 | 0,247 | 0,192 |

Acurácia, categorias, tipos, cobertura, origem e erros de alta confiança foram comparados e ficaram **idênticos** antes/depois da otimização. A variação da memória isolada mostra que não se deve atribuir toda diferença temporal à mudança de código. O ganho observado nas regras é coerente com a remoção de trabalho repetido; não estabelece um multiplicador garantido em aparelhos reais.

Artefatos verificáveis em `benchmarks/jvm-{before,after}-optimization.json`, `jvm-after-repeat.json` e seus metadados. Heap antes/depois e pico amostrado são do processo inteiro; não representam alocações exclusivas ou memória de LLM. Nenhuma medição de modelo real foi feita.

## Resumo da revisão

Ensaio com 6000 lançamentos, 5400 incluídos, 30 amostras por versão, sem instrumentação Android concorrente nessa repetição. Cada leitura verifica centavos e contagens. “Primeira” usa novo snapshot; “releitura” usa o mesmo snapshot.

| Mediana JVM | Antes | Cache selecionado |
|---|---:|---:|
| Primeira leitura | 4,330 ms | 4,036 ms |
| Releitura | 4,213 ms | 0,838 ms |

A versão selecionada guarda somente a lista incluída e os resultados monetários de um snapshot imutável. Não substitui a aritmética central em centavos. `copy()` cria novos caches; regressões verificam exclusão/reinclusão, edição de valor e troca de tipo sem alterar o snapshot anterior. Contagens e elegibilidade de confirmação continuam calculadas normalmente.

Uma variante que também guardava contagens/eligibilidade apresentou primeira leitura de 5,326 ms, apesar da releitura de 0,021 ms, e foi descartada. O ganho pequeno na primeira leitura da versão final não sustenta promessa de fluidez; o benefício demonstrado é evitar recomputações e cópias repetidas. Artefatos: `benchmarks/review-before-optimization.json`, `review-after-optimization.json` e `review-discarded-full-cache.json`.

## Proteções e pendências

Permanecem os limites de tamanho/páginas/dimensões de PDF/OCR, renderização local sequencial, cancelamento nas fronteiras das APIs, consultas Room em dispatcher apropriado, consultas limitadas de lançamentos recentes e listas lazy, motor nativo compartilhado/serializado e liberação sob pressão de memória. O teste existente de 50000 linhas/2000 candidatos com heap de 128 MiB continua obrigatório; não foi relaxado para aprovar esta rodada.

O benchmark Android opt-in mede os mesmos categorizadores e PSS do processo, aplicando os gates atuais de IA. AVD não representa aparelho intermediário físico. Ainda precisam de medição física: OCR multipágina, pico de memória nativa, tempo de derivação da senha em API 24–25, provedores SAF bloqueantes e inferência com bundle real. Requisitos de 6 GiB/arm64-v8a/reserva de armazenamento da IA não foram reduzidos.

Na execução do AVD em paralelo aos builds otimizados, uma instrumentação foi encerrada pelo ActivityManager por ultrapassar o prazo de inicialização do processo. Ela não gerou métricas válidas e não foi contada como aprovação. As verificações Android finais são executadas depois dos builds, liberando o daemon de compilação antes da repetição. Não foram aumentados os limites de tempo do Android nem alterados os requisitos do aplicativo para mascarar essa ocorrência.

## Execução Android medida nesta rodada

No AVD API 35/x86_64, após terminar os builds, os quatro casos de PDF sintético e o benchmark passaram: **5 testes em 4,532 s**. O mesmo corpus/catálogo teve hashes iguais aos da JVM. Nenhum modelo real foi executado: o gate registrou `not_run: INSUFFICIENT_RAM`; os demais requisitos de suporte também permanecem.

| Modo local | Acurácia | ms/transação amortizado | Pico PSS observado do processo |
|---|---:|---:|---:|
| `rules` | 94.83% | 1.863 | 207.98 MiB |
| `memory_only` | 8.62% | 0.870 | 219.59 MiB |
| `combined_rules` | 100.00% | 1.922 | 242.49 MiB |

PSS inclui app, runner, bibliotecas/fontes dos testes PDF e estado das etapas anteriores. A amostragem de 100 ms não mede alocação exclusiva e pode perder picos. Esses valores não são memória de LLM nem requisitos para telefone físico. A latência é amortizada após aquecimento; não inclui carga de modelo. O host permaneceu compartilhado com outros processos.

Evidências: `benchmarks/android-api35-results.json` e `benchmarks/android-api35-device.json`. O teste interrompido durante builds concorrentes não entrou nestes resultados.
