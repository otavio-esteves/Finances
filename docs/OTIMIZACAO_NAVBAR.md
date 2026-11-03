# Navegação pela navbar — 31/10/2026

O relato foi de engasgos no Galaxy S21, principalmente ao abrir Gráficos. Esta rodada alterou a navegação e o desenho do fundo da barra. A comparação foi feita em um emulador isolado; o S21 não estava conectado.

## Alterações

- Tocar uma aba seleciona diretamente a página, inclusive entre vizinhas. O deslocamento horizontal animado continua disponível pelo gesto. Isso evita desenhar duas telas financeiras durante a animação de um clique.
- O pager mantém uma página vizinha de cada lado pré-composta para reutilizar os layouts. Suas coletas financeiras ficam suspensas enquanto a página estiver oculta. Estados salvos de listas, opções dos gráficos e o ViewModel do Chat sobrevivem à remoção das páginas da composição.
- A seleção da navbar acompanha a página estabilizada; o ícone muda imediatamente. O tratamento de cliques consecutivos continua cancelando o pedido anterior e mantendo o último destino.
- Menu, pill e importação compartilham **um efeito de desfoque**, em vez de três. Um contorno com três regiões preserva os espaços transparentes entre os controles. As medidas usam o mesmo arredondamento em pixels do layout, inclusive em RTL.
- O fundo desfocado usa escala de entrada `0.5` na versão fixada do Haze. Ícones, texto, superfícies e bordas continuam desenhados na resolução normal. O desfoque mantém o raio de 24 dp.

Não houve alteração de banco, consultas financeiras, cálculos ou modelo de IA.

## Comparação

`NavbarPerformanceTest` prepara 1.200 lançamentos fictícios no mês atual, aquece as quatro páginas e percorre 24 trocas: três repetições de Gráficos → Transações → Chat → Início → Chat → Gráficos → Início → Transações. As fixtures são removidas ao final, pelo seu marcador exclusivo.

Ambas as versões usaram debug, Android 15/API 35, x86_64, 1080 × 2400, densidade 420, dois núcleos, 2 GB de RAM e SwiftShader. Os relatórios locais ficam em `app/build/reports/navigation/before.json` e `after.json`.

| Medida | Antes | Depois |
|---|---:|---:|
| Mediana do clique até o Compose ficar ocioso | 1.098 ms | 933 ms |
| P95 do clique até o Compose ficar ocioso | 1.680 ms | 1.196 ms |
| Mediana de `FrameMetrics.TOTAL_DURATION` | 247 ms | 237 ms |
| P95 de `FrameMetrics.TOTAL_DURATION` | 602 ms | 477 ms |
| Quadros registrados durante as trocas | 200 | 190 |

| Destino | Mediana antes | Mediana depois |
|---|---:|---:|
| Gráficos | 1.233 ms | 974 ms |
| Transações | 970 ms | 951 ms |
| Chat | 726 ms | 812 ms |
| Início | 1.322 ms | 866 ms |

A mediana geral caiu aproximadamente 15%; a de Gráficos, 21%. A amostra do Chat ficou mais lenta. São séries curtas em renderização por software, com latências muito maiores que o orçamento de um quadro de 60 Hz. O tempo até o Compose ficar ocioso inclui a sincronização do teste e não mede diretamente o primeiro pixel exibido após o toque. Esses números servem como comparação local e não demonstram ganho de FPS nem garantem os mesmos percentuais no S21.

A rodada registrada foi repetida após descartar capturas com um aviso do System UI do emulador. O benchmark agora inicializa a automação antes do aquecimento e confere a janela ativa antes e depois das trocas, recusando medições encobertas por outra janela. O pacote `.validation` foi limpo antes de cada versão, mantendo as duas bases com apenas as mesmas fixtures de desempenho.

## Repetir a medição

Compile a versão isolada e instale o app e seu APK de testes em um emulador ou aparelho de validação:

```bash
JAVA_HOME=/opt/android-studio/jbr ./gradlew -PisolatedDeviceValidation=true assembleStandaloneDebug assembleStandaloneDebugAndroidTest
adb -s emulator-5554 install -r app/build/outputs/apk/standalone/debug/app-standalone-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/standalone/debug/app-standalone-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w -r \
  -e class br.com.otavioesteves.finances.ui.NavbarPerformanceTest \
  -e navigationBenchmark true \
  br.com.otavioesteves.finances.validation.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 exec-out run-as br.com.otavioesteves.finances.validation cat files/navbar-performance.json
```

Troque o serial quando necessário. O teste exige o pacote `.validation` e o argumento explícito; sem ele a medição é pulada e não insere fixtures. O relatório informa os quadros, percentis e cada troca individual. `droppedMetricReports` conta notificações de métricas perdidas, não quadros perdidos pela interface.

A validação funcional usa `MainTabsNavigationTest`, `ChartsTypesTest`, `PageLifecycleOwnerTest` e `AppearanceSettingsTest`: navegação por toque e gesto, último destino em cliques rápidos, rascunho do Chat, escolha de gráfico após recriação da página, lifecycle e temas.

Resultado da rodada: 208 testes JVM aprovados; sete testes finais de navegação/lifecycle aprovados, além dos quatro testes de gráficos/aparência da rodada de UI. A captura das quatro páginas foi repetida com a checagem de janela ativa e conferida visualmente. Build do APK normal de debug concluído; lint com zero erros e 35 avisos.
