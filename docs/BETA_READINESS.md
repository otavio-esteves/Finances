# Preparação para beta fechado

Esta matriz registra as verificações executadas na consolidação de 19–20/11/2026. **O aplicativo ainda não está aprovado para iniciar beta fechado.** Nenhuma falha crítica foi observada nos cenários executados, mas os testes em aparelhos físicos, a portabilidade por SAF e a assinatura de distribuição permanecem pendentes. Aprovação nesta tabela vale para o requisito e ambiente descritos, não para qualquer documento bancário ou dispositivo.

| Requisito | Estado | Evidência verificável | Risco residual | Ação necessária |
|---|---|---|---|---|
| Linha de base e comparação final | aprovado | `CONSOLIDACAO_BETA.md`: 489→518 JVM; Android 162→169 aprovados por flavor na validação final | Ambiente único; baseline inicial apenas standalone | Manter regressões na CI |
| Integridade de extração e síntese | aprovado | `BetaImportIntegrityTest`, `PdfCoverCompletenessTest`, quatro PDFs gerados em `BetaImportPdfIntegrationTest`; suites completas sem falhas | Layouts não vistos e OCR; recusas conservadoras podem exigir outro formato | Conferência humana obrigatória |
| Corpus privado disponível | aprovado | Cinco cenários locais, roteiro `samples/README.md`; resultados ignorados pelo Git | BB/Inter privados indisponíveis | Validar novos documentos com consentimento e sem versioná-los |
| Atomicidade, duplicidades, sobreposições | aprovado | `FinancialDataIntegrityTest`, regressões de impressão digital/overlap e revisão Compose | Contexto de contas ainda é rótulo opcional | Usar rótulo estável por conta/cartão |
| Benchmark sem LLM | aprovado | 58 casos em JVM e Android; `CategorizationBenchmarkTest`, `CategorizationDeviceBenchmarkTest`, `benchmarks/android-api35-*.json` | Ajuste no próprio corpus não prova generalização | Criar holdout independente com outras descrições |
| Qualidade e desempenho LiteRT-LM real | pendente | Status `not_run`, opt-ins existentes | Sem bundle/aparelho suportado | Medir sugestões realmente originadas da IA, memória e latência |
| Backup portátil autenticado | aprovado | Cinco testes JVM e regressões Android/Compose entre dois Room; senha errada/adulteração preservam banco sentinela | Portabilidade física e provedores externos ainda não executados | Testar cópia em outro aparelho e SAF externo |
| JSON legado e migrações Room | aprovado | Schemas 1–10 intactos; cadeia 1→10, leitores JSON 1–5 e integridade executados nas suites completas de ambos os flavors | Atualização em aparelhos reais e bancos grandes não medida | Repetir atualização física com dados sintéticos e backup prévio |
| Privacidade/Manifest/captura de tela | aprovado | Testes Android em ambos os flavors; quatro APKs normais aprovados por `scripts/verify_android_privacy.py`, incluindo exclusões XML empacotadas; hashes em `benchmarks/validation-summary.json` | Provedores externos podem sincronizar exportações; captura pode ser habilitada explicitamente | Manter verificação na CI e informar limites da exportação |
| Bloqueio de interface opcional | pendente | Avaliação técnica em `BACKUP_SEGURANCA.md`; nenhum PIN próprio | Finances não tem bloqueio interno; Room sem criptografia adicional | Projetar autenticação oficial e preservação de navegação/SAF; usar bloqueio do aparelho |
| Acessibilidade e fontes grandes | pendente | Controle único de confirmação: Compose aprovado em 320 dp/fonte 150% | Fluxo completo e TalkBack físico ainda não medidos | Executar roteiro manual com teclado e fonte 200% |
| Desempenho em Android intermediário | pendente | Medições JVM e PSS/latência API 35 em `PERFORMANCE_BETA.md`; limites de PDF/OCR/IA preservados | AVD não representa telefone físico; listas de backup ainda materializadas | Medir PDFs multipágina, bases grandes, responsividade e cancelamento |
| Flavor standalone | aprovado | 518 JVM, 175 Android (169 aprovados/seis opt-ins), lint sem erros e builds debug/release concluídos | Release local sem assinatura; somente API 35 instrumentada | Executar roteiro físico e assinar artefato de distribuição |
| Flavor play | aprovado | Mesma validação local: 518 JVM, 169 Android aprovados, lint sem erros e debug/release | Sem asset pack nem distribuição Play implementados | Não anunciar Play Asset Delivery; validar distribuição separadamente |
| Dependências e avisos de lint | pendente | Zero erros; 41 avisos e uma sugestão por flavor, incluindo três `TrustAllX509TrustManager` na pilha PDF/PKI preexistente | App sem cliente HTTP/TLS e sem INTERNET; dependências antigas continuam exigindo manutenção | Validar modernização da pilha PDF/PKI sem suprimir os avisos; detalhes em `BACKUP_SEGURANCA.md` |
| Assinatura e distribuição fechada | pendente | APKs release otimizados gerados sem assinatura; nenhuma publicação executada | APK unsigned não é distribuição pronta | Responsável deve assinar e cumprir roteiro físico antes do beta |
| Reprodutibilidade do empacotamento incremental | pendente | Build amplo aprovado; última troca de ID falhou em `packagePlayDebug`, repetição serial aprovada sem alteração de código | Causa da falha pontual não estabelecida; não houve falha de teste financeiro | Se reaparecer na CI, obter diagnóstico do empacotador; não suprimir o erro |

## Roteiro manual obrigatório em aparelho físico

Usar instalação isolada e dados de teste antes de abrir documentos pessoais. Registrar versão Android, RAM, ABI, build, flavor e data; nunca registrar senhas nem descrições financeiras em relatório público.

1. Primeiro uso sem modelo: abrir, entender estados vazios, criar/importar um lançamento e confirmar que regras funcionam offline. Repetir com notificações negadas e aparelho sem biometria.
2. Importar CSV/OFX/PDF sintéticos de `samples/`, incluindo datas na virada do ano, zero, negativos, parcelas, estorno e pagamento da fatura. Comparar todos os centavos e datas com a origem, totais de cada tipo e gráficos; pagamento deve ficar separado das despesas.
3. PDF com senha errada/correta, OCR pouco nítido, multipágina, moedas e unidades não suportadas. Confirmar que ambiguidades recusam o lote ou exigem revisão e que nenhum lote parcial é salvo.
4. Cancelar leitura/OCR/categorização, voltar, girar e recriar Activity; importar novamente. Fechar processo antes da confirmação: banco deve permanecer intacto. Repetir arquivo e lote parcialmente sobreposto; excluir/reincluir candidatos e preservar multiplicidade.
5. Corrigir data, descrição, valor, tipo e categoria; confirmar e verificar Início/Gráficos/Transações. Reabrir, editar e excluir uma transação. Restaurar com formulário antigo aberto e verificar recusa de edição obsoleta.
6. Ativar TalkBack; tocar nos rótulos completos das confirmações. Testar 320 dp e fonte 150%/200%, rolagem, teclado, modal de senha e ausência de botões inacessíveis. Conferir foco e mensagens de erro.
7. Criar `.fnb` com senha longa, cancelar, negar destino e simular espaço insuficiente. Restaurar em **outro aparelho**. Senha errada, arquivo truncado/adulterado e JSON inválido devem preservar um lançamento sentinela existente. Repetir com JSON versões suportadas. Testar provedores SAF local e externo; conferir que a senha não reaparece após rotação/reabertura.
8. Usar Android 24/25 para exercitar PBKDF2 fallback e outro Android recente para JCA; medir tempo até sucesso/cancelamento. Durante escrita atômica da restauração, cancelamento deve ficar indisponível. Conferir backup e captura automáticos bloqueados por padrão.
9. Medir importação de PDFs grandes e OCR multipágina: tempo, memória máxima observada, aquecimento, cancelamento e retorno ao app após pressão de memória. Em dispositivo de IA compatível, repetir benchmark com bundle real e identificar fallback. Não reduzir os requisitos de IA sem evidência.
10. Repetir fluxo crítico em `standalone` e `play`; instalar atualização sobre banco antigo com dados sintéticos. Confirmar migração e backup antes de qualquer distribuição fechada.

## Contas e cartões — estratégia futura, sem implementação nesta rodada

O rótulo opcional atual é contexto de deduplicação, não cadastro de contas. Um cadastro futuro precisa de IDs estáveis, tipo conta/cartão, moeda, vínculo de importações e transações, transferências pareadas sem dupla contagem e regras explícitas para saldos/faturas. Não pode deduzir titularidade somente pelo texto de PIX.

A evolução exigirá nova versão Room com migração explícita/exportada/testada, preservando registros sem conta em um estado “não identificado”, e nova versão de backup com leitor das versões anteriores. Backfill deve depender de confirmação do usuário; nenhum ID pode ser inventado a partir de um nome bancário. Deduplicação e sobreposição deverão manter aliases dos hashes publicados. Nenhuma tabela ou funcionalidade de cadastro foi criada agora.
