# Amostras locais de faturas e extratos

Coloque os arquivos a investigar em `samples/private/`, organizados por instituição e caso:

Se não conseguir anexar o PDF na conversa, copie-o diretamente para essa pasta e informe seu caminho. O agente pode lê-lo no workspace sem um anexo e sem enviar o documento a um conversor externo.

```text
samples/private/
  nubank/
    fatura-2026-10.pdf
    fatura-2026-10.md
  inter/
    extrato-2026-10.pdf
    extrato-2026-10.md
```

O conteúdo de `private/` é ignorado pelo Git, com exceção do `.gitkeep`. Não é incluído no APK nem executado automaticamente como teste. Use essa pasta como conjunto local de diagnóstico; não force sua inclusão no Git. Dê preferência a cópias anonimizadas, preservando posições, colunas, datas e formatos dos valores para que o problema de layout continue reproduzível. Cobrir dados visualmente pode deixar o texto original dentro do PDF.

Para cada arquivo, crie um `.md` com o mesmo nome e registre:

- Instituição e tipo selecionado no app: fatura ou extrato.
- Período de referência e se é PDF digital, digitalizado ou misto, quando souber.
- Se exige senha (sem registrar a senha nesse arquivo).
- Modelo do celular, versão do Android e versão do app.
- Mensagem de erro exata e etapa em que ocorre: leitura, revisão ou categorização.
- Resultado esperado: quantidade de lançamentos, exemplos de data/descrição/valor/tipo e totais de compras, estornos ou créditos, separados de saldo e total da fatura.
- Modelo `.litertlm` instalado, se houver, e se o problema também acontece sem ele.

Neste app, PDFBox/Tesseract extraem o conteúdo e o parser reconhece os lançamentos. Só depois o categorizador por regras ou LiteRT-LM sugere categorias. Um modelo de linguagem instalado não substitui a leitura do PDF.

Ao corrigir um caso real, transforme a estrutura que causou a falha em um teste sintético, sem dados pessoais, nas suítes existentes:

- `app/src/test/java/br/com/otavioesteves/finances/data/statement/`: parsing de texto, datas e tabelas.
- `app/src/androidTest/java/br/com/otavioesteves/finances/data/statement/`: extração de PDFs completos e OCR no Android.

As amostras privadas permanecem locais; o Git recebe a regressão sintética. Consulte [a cobertura de importação](../docs/PDF_IMPORTACAO.md) para limites e cenários já exercitados.

## Repetir a validação local no Android

Crie `samples/private/cases.json`, também ignorado pelo Git. Os caminhos são relativos à pasta desse arquivo:

```json
[
  {
    "id": "fatura_exemplo",
    "file": "nubank/fatura-2026-10.pdf",
    "kind": "CARD_BILL",
    "count": 3,
    "expenseCents": 5000,
    "incomeCents": 0,
    "transferCents": 4000
  }
]
```

Use `BANK_STATEMENT` para extratos. Os números acima são fictícios: preencha com resultados conferidos no seu documento. `count`, `expenseCents`, `incomeCents` e `transferCents` são verificações opcionais; sem elas o roteiro verifica apenas se o parser aceita o arquivo. O campo opcional `referenceYear` informa explicitamente o ano quando todas as compras sem ano forem daquele ano; ele não substitui referências explícitas do PDF. Pagamentos de fatura reconhecidos como transferência entram em `count` e `transferCents`, sem aumentar receitas ou despesas. O roteiro não fornece senhas para PDFs com senha de abertura.

Com um emulador Android iniciado, compile e instale a versão isolada (troque o serial pelo exibido em `adb devices`):

```bash
JAVA_HOME=/opt/android-studio/jbr ./gradlew -PisolatedDeviceValidation=true assembleStandaloneDebug assembleStandaloneDebugAndroidTest
adb -s emulator-5554 install -r app/build/outputs/apk/standalone/debug/app-standalone-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/standalone/debug/app-standalone-debug-androidTest.apk
python3 scripts/validate_private_pdfs.py --serial emulator-5554
```

O script usa o pacote separado `br.com.otavioesteves.finances.validation`, exige um serial de emulador e envia os arquivos apenas ao cache privado desse pacote. Os PDFs não são empacotados no APK. O teste guarda resultados e texto extraído em `samples/private/validation/` e o script remove seu cache de trabalho no emulador ao terminar. Esses diagnósticos contêm dados dos documentos e permanecem ignorados pelo Git. Nenhuma transação é gravada no banco durante esse teste.
