# Plano de IA local e evolução do fluxo de faturas

Este é o plano técnico para a ideia central do Finances: **receber faturas de cartão e extratos, organizar os lançamentos automaticamente, apresentar insights e responder perguntas no Chat depois de Transações**. A IA serve a esse fluxo; o arquivo importado e os números confirmados pela pessoa são a fonte das respostas.

O [README](../README.md) mostra a experiência de produto e o estado atual. A [arquitetura](ARCHITECTURE.md) descreve os fluxos de código. Este plano registra decisões, lacunas e critérios de aceite. As anotações históricas das fases de 2026-09-18/19 foram condensadas abaixo; quando um status diverge de um comentário antigo no código, valem o código e as pendências deste documento.

## 1. Resultado esperado e estado atual

| Passo da experiência | Hoje | Alvo |
|---|---|---|
| Receber arquivo | Seletor do Android; CSV/OFX (também `.qfx` como OFX), PDF digital, digitalizado ou misto, senha e OCR local com revisão obrigatória. | Validar os layouts dos bancos priorizados com arquivos reais anonimizados; cobertura detalhada em [Importação de PDFs](PDF_IMPORTACAO.md). |
| Organizar lançamentos | Sugestões por regras ou tentativa de LiteRT-LM com modelo importado; revisão manual antes de salvar. | Categorização medida, baixa confiança visível, importação segura contra duplicatas e falha parcial. |
| Entender os gastos | Saldo mensal, gráfico por categoria, transações recentes e filtro por mês. | Insights proativos baseados em lançamentos confirmados, com números e período verificáveis. |
| Tirar dúvidas | Chat com contexto de saldo e totais por categoria de um mês resolvido pela pergunta. | Perguntas por intervalos e transações específicas, recuperação detalhada, resposta progressiva e latência aceitável. |
| Modelo no aparelho | Import manual de `.litertlm`; fallback por regras. | Modelo distribuído no flavor `play`, import manual opcional e seleção de modelo validada em aparelhos reais. |

Início, Gráficos, Transações e Chat são as quatro páginas do `HorizontalPager` e da barra inferior, nessa ordem. A geração de insights escritos ainda não está implementada; o gráfico atual é um resumo visual.

## 2. Decisões mantidas

1. **Tudo local.** Arquivo, transações, prompts e histórico de Chat permanecem no aparelho. O app não declara `android.permission.INTERNET`. A Play Store pode entregar um pacote do modelo na instalação futura; o app não baixa modelos por conta própria.
2. **LiteRT-LM como motor atual de integração.** A dependência está no Version Catalog; a versão efetiva é a de `gradle/libs.versions.toml`. Não tratar a existência das classes de integração como validação da inferência em aparelho.
3. **Categorização e Chat são tarefas diferentes.** A primeira processa um lote e devolve categorias/confiança; o segundo precisa recuperar fatos financeiros antes de responder uma pergunta. Eles podem usar modelos diferentes após medições.
4. **Fallback por regras permanente para categorização.** Se não houver modelo, o aparelho não for compatível, a inferência falhar ou a sugestão tiver baixa confiança, o fluxo ainda permite importar com revisão humana. O Chat atual também usa respostas por regras como fallback, mas deve comunicar seus limites com clareza.
5. **Chat somente leitura.** Perguntas não alteram lançamentos ou categorias.
6. **Distribuição planejada:** `play` com Play Asset Delivery na instalação e `standalone` com import manual pelo seletor do Android. Os flavors existem, mas o asset pack e sua resolução ainda não.
7. **Medição antes de fechar o modelo.** O candidato inicialmente escolhido foi Gemma 4 E2B em bundle `.litertlm`. A escolha final para categorização e Chat depende de qualidade, memória e latência medidas nos aparelhos alvo. Outros modelos menores podem ser avaliados.

Essas decisões vieram da revisão de 2026-09-18: publicação pretendida na Play Store; avaliação em um aparelho recente e em um intermediário; extratos reais anonimizados fora do repositório como conjunto de referência; fallback por regras definitivo; Chat sem comandos de escrita.

## 3. Arquitetura existente do motor

### Contratos e roteamento

- `TransactionCategorizer` categoriza `RawStatementEntry` e devolve `CategorySuggestion` com `confidence` e `source` (`AI`, `RULE`, `USER`). `SynthesizeStatementUseCase` usa esse contrato.
- `LocalAiRepository.sendMessage` atende ao Chat atual. `FinancialChat`/`ChatChunk` para respostas progressivas são contratos **planejados**, ainda não existem no código.
- `LocalAiEngine` informa `NotProvisioned`, `Provisioning`, `Ready`, `Unsupported` ou `Failed`; `ModelImporter` gerencia importação e remoção.
- `EngineAwareTransactionCategorizer` e `EngineAwareLocalAiRepository` consultam o estado do motor a cada chamada. Com `Ready`, encaminham aos adaptadores LiteRT-LM; nos demais estados, usam regras.

### Importação do modelo

`ImportedModelLocalAiEngine` copia o bundle escolhido para `filesDir/models/current.litertlm`, calcula SHA-256 e registra nome/hash em preferências locais. A porta de capacidade considera memória, ABI e armazenamento. A importação tenta inicializar o runtime com o candidato antes de substituir o modelo anterior; `warmUp()` tenta inicializar o modelo instalado. `KNOWN_MODEL_HASHES` continua vazio: um arquivo importado não é marcado como verificado. A qualidade das respostas ainda exige inferência e medição em aparelho.

`DeviceCapabilityPolicy` exige provisoriamente **6 GB de RAM, 2 GB livres e `arm64-v8a`**. Isso impede que o aparelho intermediário de cerca de 4 GB, escolhido para medição, importe o modelo hoje. Medir um bundle adequado e rever a política fazem parte da entrega E; enquanto isso, esse aparelho usa regras. O atalho para uma página oficial de download também está oculto porque sua URL ainda não foi definida no app.

Essa importação manual já oferece um caminho para `standalone`. No futuro, a resolução do flavor `play` deve seguir: **modelo importado pela pessoa → asset pack → nenhum modelo**. Nenhuma fonte de asset pack foi conectada ainda.

### Inferência atual

`LiteRtTransactionCategorizer` processa lotes de até 20 entradas, solicita JSON, tenta novamente se a resposta não for interpretável e recorre às regras por lote ou por sugestão incerta. `LiteRtLocalAiRepository` monta um prompt com saldo e totais por categoria do mês resolvido, recebe uma resposta sem streaming e reutiliza a sessão compartilhada `LiteRtEngineSession`, com inferência e mutações do modelo serializadas. Ambos leem itens `Content.Text` da resposta. Um estado `Ready` significa que o runtime conseguiu inicializar o bundle, **não** que a inferência ou sua qualidade foram verificadas.

Os testes JVM cobrem os roteadores e a lógica independente do SDK. Ainda faltam medições reproduzíveis de qualidade, latência e memória, além de validação de ponta a ponta da resposta e da categorização em aparelhos reais. A versão da dependência no catálogo é **0.16.1** nesta revisão; anotações antigas que citavam 0.17.1 não descrevem o build atual.

## 4. Entregas priorizadas pelo produto

### A. Faturas e extratos reais

- A extração de PDF aceita texto posicionado, colunas de débito/crédito, digitalizações por OCR local, documentos mistos e senha. Datas abreviadas exigem referência inequívoca no documento. Nubank, PicPay, Banco do Brasil, Caixa e Inter têm famílias de layouts exercitadas com PDFs sintéticos; falta validar arquivos reais anonimizados dessas instituições. Os limites e a cobertura estão em [Importação de PDFs](PDF_IMPORTACAO.md). O parser de CSV exige colunas reconhecíveis de data, descrição e valor; `.ofx`/`.qfx` usam blocos de transação OFX.
- Definir formatos de amostra anonimizados e testes por instituição; mostrar erro compreensível para arquivos incompatíveis ou malformados.
- A confirmação do lote é atômica e recusa um conjunto idêntico de lançamentos extraídos, independentemente da ordem nos novos lotes v2, sem eliminar compras repetidas. Possíveis sobreposições por data, valor e descrição geram aviso e exigem confirmação. Ainda faltam identificação confiável de descrições alteradas, arquivos antigos com hash v1 reordenados e importações anteriores à migração 2→3.
- **Aceite:** importar uma fatura real de cada formato suportado sem perder valor/data/descrição, revisar sugestões e repetir o arquivo sem duplicar lançamentos silenciosamente.

### B. Categorização confiável

- Criar um conjunto de referência de lançamentos reais anonimizados, guardado fora do Git, e medir acerto por categoria, cobertura e frequência de correção humana. Medir as regras como baseline e comparar cada modelo candidato.
- Confirmar a API de retorno do LiteRT-LM, testar JSON malformado, cancelamento, bundle incompatível e liberação de memória. Validar no aparelho recente e no intermediário antes de afirmar que a IA supera as regras.
- Tornar visível na revisão quando a sugestão veio da IA, de regras ou de escolha humana e quando a confiança é baixa.
- **Aceite:** o lote é importável mesmo após falha da IA, com categoria revisável; os resultados de qualidade e latência são registrados e reproduzíveis.

### C. Insights verificáveis na Início

- Gerar observações a partir das transações **confirmadas**, como maior categoria de gastos e mudança em relação ao período anterior. Cada insight deve carregar período, valor e critério usados para que a pessoa possa conferir no histórico.
- Evitar conclusões quando os dados forem escassos ou incompletos. A geração pode começar com cálculos determinísticos; texto de IA local só deve reformular fatos já calculados.
- **Aceite:** uma alteração de categoria ou exclusão de transação atualiza o insight; nenhum valor apresentado difere da soma consultável no app.

### D. Chat que consulta as finanças

- Ampliar a resolução de período além de um único mês e reconhecer o tema da pergunta; buscar no Room os lançamentos pertinentes e montar um contexto pequeno com origem verificável. O `FinancialContext` atual contém somente saldo e resumo por categoria do mês resolvido, sem transações individuais.
- Criar o contrato de resposta progressiva e lidar com cancelamento, rotação de tela e reutilização/liberação do motor. Testar perguntas que não podem ser respondidas, sem inventar números.
- **Aceite:** “quanto gastei com mercado em fevereiro?” responde a partir das transações daquele fevereiro; a pessoa consegue conferir o total; o Chat continua somente leitura.

### E. Provisionamento e desempenho do modelo

- Medir memória, latência e tamanho do artefato nos dois perfis de aparelho definidos. Decidir se o mesmo modelo serve à categorização e ao Chat.
- Reavaliar os limites de capacidade com essas medições, incluindo um modelo menor para o perfil de 4 GB se viável.
- Integrar um asset pack real ao flavor `play`, resolver a preferência por modelo importado e validar instalação por distribuição de teste da Play. Isso depende do bundle escolhido, da configuração de publicação e da assinatura de release.
- Validar geração e categorização com bundle real após a inicialização do runtime, registrar hashes dos bundles medidos e tratar falhas sem deixar o app inutilizável.
- **Aceite:** o modelo funciona após instalação nos aparelhos suportados, o `standalone` aceita import manual e ambos preservam o manifest sem `INTERNET`.

Para concluir essa distribuição ainda são necessários um bundle compatível, configuração do app no Play Console e assinatura de release. Antes de incluir um modelo no asset pack, conferir seus termos de redistribuição e o tamanho final do AAB. O arquivo do modelo não deve ser colocado no histórico do Git.

### F. Segurança e manutenção

- Revisar logs do runtime, prompts, arquivos temporários, backup do diretório de modelos e regras de minificação para release.
- Tratar arquivos malformados e limites de tamanho sem travar ou esgotar memória; reduzir retenção do conteúdo original durante a importação.
- **Aceite:** testes de falha e release minificado passam; nenhum dado financeiro aparece em logs previstos pelo app.

## 5. Correspondência com as fases anteriores

| Fase original | Estado em 2026-09-26 | Próximo marco |
|---|---|---|
| 0 — baseline | Sem medições reproduzíveis registradas. | Medir regras, APK, memória e latência (B/E). |
| 1 — contratos | `TransactionCategorizer`, `LocalAiEngine` e roteamento implementados. | Manter contratos coerentes com B/D. |
| 2a — import manual | Tela, cópia do bundle e estado do motor implementados. | Validação real do bundle (E). |
| 2b — flavors/asset pack | `play`/`standalone` e teste de permissão de rede existem; asset pack não. | Integrar e validar distribuição (E). |
| 3 — categorização LiteRT-LM | Adaptador escrito, fallback e roteamento presentes; qualidade/execução real não comprovadas. | Confirmar inferência e medir (B). |
| 4 — escolha por medições | Pendente. | Medir nos dois perfis de aparelho (B/E). |
| 5 — Chat com recuperação | Versão reduzida sem recuperação por transação; resposta de ponta a ponta do modelo ainda não confirmada. | Consultas por período, streaming e ciclo de vida (D). |
| 6 — degradação/limpeza | Fallback por regras parcial; revisão de memória, logs e release pendente. | Endurecimento (F). |
| 7 — documentação | README, arquitetura e este plano revisados para o foco em faturas → insights → Chat. | Atualizar junto de cada mudança de comportamento. |

## 6. Verificação e privacidade

- **JVM:** cobrir parsing, modelos, casos de uso, roteamento e lógica determinística de insights com dados sintéticos. Os testes existentes não exercitam a inferência nativa.
- **Android:** manter `MigrationTest` e `NetworkPermissionInvariantTest`; acrescentar uma suíte com bundle real para carga, inferência curta, cancelamento e liberação de memória. Avaliar job separado se o bundle for grande para o CI comum.
- **Qualidade:** usar faturas/extratos próprios anonimizados fora do repositório, com resultados esperados por transação e roteiro fixo de perguntas ao Chat. Testar nos aparelhos alvo.
- **Privacidade:** nunca enviar arquivo, lançamentos ou Chat a serviços externos. Enviar ao modelo local apenas os campos necessários; cabeçalhos de fatura com dados de titular/conta não devem entrar no prompt de categorização. Histórico do Chat e banco Room ainda ficam sem criptografia adicional.

Build, testes JVM e lint são verificações locais disponíveis pelos comandos do README. Eles não substituem a validação da inferência LiteRT-LM nem os testes com arquivos reais em aparelho.
