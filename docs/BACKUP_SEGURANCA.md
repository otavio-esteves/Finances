# Backup portátil e proteção local

Em Configurações, **Backup protegido por senha** gera `.fnb`. Escolha o destino, informe e repita uma senha de 12 a 1024 caracteres. A senha não é aparada nem normalizada; guarde-a fora do aparelho. O Finances não pode recuperá-la. Para restaurar em outra instalação, selecione o arquivo, informe a mesma senha e confirme a substituição dos dados atuais. JSON legado não exige senha.

**Avançado: JSON sem proteção** mantém a exportação legível após um aviso explícito. CSV também é legível. Ao escolher um provedor externo pelo seletor Android, o conteúdo exportado é entregue a ele: o app continua sem INTERNET, mas o provedor pode sincronizar o arquivo. O backup protegido entrega somente o conteúdo cifrado. Recomenda-se proteger os arquivos e conferir a restauração de uma cópia antes de depender dela.

## Formato protegido versão 1

O envelope não muda a versão **5** do JSON interno nem o schema **10** do Room. JSON versões 1–5 continua aceito. O formato protegido é independente do Android Keystore, IDs do dispositivo e instalação.

| Offset | Tamanho | Campo |
|---|---:|---|
| 0 | 8 bytes | ASCII `FNBKENC` seguido de LF (`0a`) |
| 8 | 1 byte | Versão do envelope: `01` |
| 9 | 1 byte | KDF: `01`, PBKDF2-HMAC-SHA256 |
| 10 | 4 bytes | Iterações, inteiro positivo big endian: 600000 |
| 14 | 32 bytes | Salt aleatório por arquivo, `SecureRandom` |
| 46 | restante | Ciphertext Streaming AEAD do Tink, sem prefixo de keyset |

PBKDF2 recebe a senha em UTF-8 e deriva 32 bytes. Android 26+ usa JCA; na ausência do algoritmo (API 24–25), usa a implementação leve Bouncy Castle já incluída pelo PDFBox. Não há seleção forçada de provider Android. A paridade UTF-8 e vetor conhecido são testados.

O material derivado inicializa **Tink `AesGcmHkdfStreaming`**, HKDF-HMAC-SHA256, chave AES de 32 bytes, segmentos cifrados de 65536 bytes e `firstSegmentOffset=0`. Os 46 bytes completos do envelope são os dados associados. O cabeçalho interno, salt de HKDF, prefixo aleatório de nonce, contador de segmentos e marca de último segmento são gerados/verificados pela biblioteca. Não foi criado protocolo próprio de nonces, tags ou segmentação. Implementação: `com.google.crypto.tink:tink-android:1.24.0`.

Referências de implementação: [especificação Streaming AEAD do Tink](https://developers.google.com/tink/streaming-aead/aes_gcm_hkdf_streaming), [configuração Java/Android](https://developers.google.com/tink/setup/java), [orientações criptográficas Android](https://developer.android.com/privacy-and-security/cryptography) e [fator PBKDF2 recomendado pela OWASP](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html). Não há alegação de certificação criptográfica do aplicativo.

A versão atual exige exatamente esse KDF/fator de trabalho; rejeita versões, algoritmos e contagens diferentes antes da derivação cara. Mudanças futuras devem ter leitor explícito compatível. Falha de autenticação e senha errada têm a mesma mensagem, pois não podem ser distinguidas com segurança.

## Integridade e limites

A leitura detecta o envelope, autentica **todos** os segmentos e EOF, decodifica e valida o modelo antes de chamar o repositório. Não retorna ao JSON se um envelope reconhecido falhar. O repositório já valida todas as relações e restaura em uma transação Room. Senha errada, adulteração tardia, truncamento, bytes extras e JSON inválido não podem limpar o banco. Testes JVM exercitam esses cenários; teste Android verifica também preservação do Room e portabilidade entre dois bancos independentes.

JSON é codificado/decodificado por stream; não são criados uma String integral, byte array cifrado integral ou arquivo temporário de backup em produção. Há buffers limitados de criptografia. **O modelo de backup e as listas de registros ainda ficam em memória** para validação/restauração atômica; bases grandes precisam de medição em aparelhos intermediários. Isso não é uma promessa de tamanho ilimitado.

A operação roda no dispatcher IO. Cancelamento é permitido durante criação/leitura e desabilitado antes da substituição atômica. Derivação criptográfica e chamadas bloqueantes de provedores SAF só observam cancelamento quando retornam; a interface permanece em cancelamento até o término. Um destino pode conter arquivo incompleto após falha/cancelamento; o aplicativo não apaga automaticamente um arquivo escolhido pelo usuário. `wt` solicita truncamento ao provedor na exportação.

Senhas não entram em preferências, `rememberSaveable`, SavedStateHandle, banco, backup ou logs. Campos Compose transitórios são descartados; arrays de senha/chave são limpos no fim, inclusive cancelamento antes do início. Strings e cópias internas da VM/biblioteca/IME não permitem garantia de apagamento físico da RAM. Erros de JSON/provedor são sanitizados para não mostrar trechos financeiros. Não há logs de conteúdo financeiro no código principal.

## Auditoria de privacidade e bloqueio opcional

- Manifest mantém somente notificações e recepção de boot; sem INTERNET. Conferir também o Manifest mesclado dos APKs de ambos os flavors.
- `allowBackup=false` e exclusões de extração/backup permanecem. Capturas são bloqueadas por padrão (`FLAG_SECURE`); opção explícita permite habilitá-las.
- PDFs com senha ficam em memória durante leitura. O temporário descriptografado necessário ao renderer OCR é interno ao cache, removido em `finally`; resíduos de sessões anteriores são eliminados no início seguinte. A limpeza preserva arquivos da sessão ativa e outros caches. Remoção lógica não equivale a apagamento físico seguro do armazenamento.
- Modelos fornecidos pelo usuário são bundles não confiáveis processados por runtime nativo. Limites de arquivo/armazenamento, inicialização controlada e requisitos atuais de RAM/ABI permanecem. Modelo desconhecido não vira modelo verificado; não se atribui confiança probabilística à saída.
- Room continua protegido pelo sandbox/criptografia do próprio Android, **sem criptografia adicional do banco pelo Finances**. Backup criptografado não muda isso.

Bloqueio de interface opcional foi avaliado com `BiometricPrompt`/credencial oficial (`KeyguardManager` nas APIs antigas). Não foi incluído: o app mantém NavHost, diálogos em janelas separadas e seletores SAF; uma simples sobreposição deixaria diálogos fora da proteção. Remover a árvore financeira para bloquear exige preservar navegação, resultados SAF e formulários na recriação, além de testar cancelamento de autenticação e retirada da credencial do sistema para evitar perda de acesso. Essa alteração de ciclo de vida foi adiada em favor do backup portátil verificado. Não foi criado PIN próprio nem anunciada criptografia de Room. O bloqueio oficial do aparelho continua sendo a proteção local disponível.

O lint também sinaliza implementações permissivas de `X509TrustManager` na dependência `bcpkix` 1.72 já trazida pelo PDFBox. O app não usa cliente HTTP/TLS nem declara INTERNET; esse aviso não foi suprimido nem tratado como uma conexão segura. A inclusão direta de `bcprov` apenas explicita o provedor leve já existente para o fallback PBKDF2; não moderniza toda a pilha PDF/PKI. O relatório final deve manter esses avisos visíveis. Trocar essa pilha requer validação de PDFs criptografados e certificados, sem atualização ampla silenciosa nesta consolidação.
