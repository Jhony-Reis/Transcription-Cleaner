# differences.md — Diferenças entre Python e Java

Nenhuma das diferenças abaixo altera o comportamento observável para
arquivos `.vtt`/`.txt` reais e bem formados. Cada uma foi avaliada com o
formato:

```
COMPORTAMENTO PYTHON:
COMPORTAMENTO JAVA:
DIFERENÇA:
CAUSA:
IMPACTO:
DECISÃO:
```

## 1. `encode("unicode_escape").decode()` no caminho do arquivo de lista

COMPORTAMENTO PYTHON: `user_input.encode("unicode_escape").decode()` é
aplicado à string digitada pelo usuário antes de ser usada como caminho do
arquivo de lista. Essa transformação escapa caracteres especiais (por
exemplo, transforma cada `\` literal em `\\`).

COMPORTAMENTO JAVA: a entrada do usuário é usada diretamente (apenas com
`strip()`), sem qualquer transformação de escape.

DIFERENÇA: para uma entrada como `C:\transcripts\lista.txt`, o Python
produziria a string `C:\\transcripts\\lista.txt` (com barras duplicadas)
antes de construir o `Path`; o Java usa `C:\transcripts\lista.txt`
diretamente.

CAUSA: não identificamos, na leitura do notebook, nenhuma necessidade
funcional para esse escape — o valor resultante nunca é "des-escapado" em
nenhum outro ponto do código antes de virar um `Path`. Tudo indica que essa
linha foi copiada de outro contexto (por exemplo, tratamento de entrada via
alguma interface que exigisse strings escapadas) e ficou no script sem
necessidade real para o fluxo de `input()` + `Path`.

IMPACTO: se mantida, essa transformação **quebraria** caminhos Windows
reais contendo uma única barra invertida por segmento (ex.:
`C:\Users\pessoa\lista.txt` viraria uma string com barras duplas, que o
`Path` do Python ainda consegue resolver corretamente na prática — barras
duplas em caminhos Windows não UNC costumam ser toleradas — mas é, na
melhor das hipóteses, um passo inútil, e na pior, uma fonte de bugs sutis
com caminhos específicos).

DECISÃO: **removida** na migração Java. `java.nio.file.Path` já lida
corretamente com caminhos Windows/Unix/Unicode em UTF-8 sem qualquer
escape manual. Essa decisão está documentada também no Javadoc de
`Main.java`.

---

## 2. `\w`, `\s`, `\d` — Unicode vs ASCII

COMPORTAMENTO PYTHON: em Python 3, `re` sobre `str` usa por padrão a
semântica Unicode: `\w` casa letras acentuadas (ex.: "João"), `\s` casa
espaços Unicode diversos, `\d` casa dígitos decimais de outros sistemas de
numeração Unicode além de `0-9`.

COMPORTAMENTO JAVA: `java.util.regex.Pattern`, por padrão, trata `\w`,
`\s` e `\d` apenas no sentido ASCII, a menos que a flag
`Pattern.UNICODE_CHARACTER_CLASS` seja usada.

DIFERENÇA / DECISÃO:
- No **padrão de speaker**, habilitamos `Pattern.UNICODE_CHARACTER_CLASS`
  explicitamente, pois nomes de speaker com acentuação (João, José, etc.)
  são um caso de uso real e esperado (há inclusive testes de encoding
  UTF-8 pedidos na especificação). Sem essa flag, `"João: Olá"` não seria
  reconhecido como linha de speaker em Java, quebrando a equivalência.
- Nos padrões de **numeração** e **timestamp**, mantivemos `\d` no sentido
  ASCII padrão do Java (sem a flag). Embora isso seja tecnicamente distinto
  do Python (que também casaria outros dígitos decimais Unicode nesses
  padrões), avaliamos que: (a) timestamps de arquivos `.vtt`/`.txt` reais
  sempre usam dígitos ASCII `0-9`; (b) usar `\d` Unicode nesses dois
  padrões só aumentaria o risco de falsos positivos (por exemplo, algum
  dígito Unicode aparecendo por acaso em uma linha de texto livre sendo
  mal-interpretado como número de linha).

IMPACTO: nulo para arquivos reais de transcript (que usam apenas dígitos
ASCII em timestamps e numeração de cue).

---

## 3. `str.strip()` (Python) vs `String.strip()` (Java)

COMPORTAMENTO PYTHON: `str.strip()` remove caracteres considerados
espaço em branco segundo `str.isspace()` (Unicode-aware).

COMPORTAMENTO JAVA: `String.strip()` (desde Java 11) também é
Unicode-aware, usando `Character.isWhitespace`/`Character.isSpaceChar`.

DIFERENÇA: os conjuntos exatos de caracteres considerados "espaço em
branco" pelas duas linguagens não são 100% idênticos em casos extremamente
raros de caracteres de controle Unicode pouco usados.

IMPACTO: irrelevante na prática para transcripts de reunião.

DECISÃO: usar `String.strip()` diretamente, sem tratamento especial.

---

## 4. Separadores de linha — `str.splitlines()` (Python) vs `Files.readAllLines` (Java)

COMPORTAMENTO PYTHON: `Path.read_text(...).splitlines()` quebra o texto em
um conjunto amplo de terminadores de linha Unicode (`\n`, `\r\n`, `\r`,
`\v`, `\f`, `\x1c`–`\x1e`, `\x85`, `\u2028`, `\u2029`).

COMPORTAMENTO JAVA: `Files.readAllLines(...)` (baseado em
`BufferedReader.readLine()`) reconhece `\n`, `\r\n` e `\r` como
terminadores de linha.

DIFERENÇA: arquivos contendo os separadores Unicode "exóticos" listados
acima (não usados em arquivos `.vtt`/`.txt` de transcrição gerados por
ferramentas de reunião conhecidas) seriam quebrados em mais linhas pelo
Python do que pelo Java.

IMPACTO: não identificado nenhum caso realista em que isso ocorreria para
os arquivos-alvo desta aplicação.

DECISÃO: usar `Files.readAllLines` (API padrão, idiomática, sem
dependências extras). Caso no futuro surjam arquivos com esses separadores
exóticos, isso pode ser revisitado.

---

## 5. Tratamento de erro de leitura/escrita — exceção genérica (Python) vs específica (Java)

COMPORTAMENTO PYTHON: `clean_transcript` usa `except Exception as e:` tanto
na leitura quanto na escrita, capturando qualquer tipo de erro.

COMPORTAMENTO JAVA: `TranscriptCleaner.clean` captura especificamente
`IOException` (a superclasse relevante para erros de leitura/escrita de
arquivo em Java) — não `Exception` genérico, conforme pedido na
especificação da migração ("evite `catch (Exception e)` sem necessidade").

IMPACTO: praticamente nenhum para o fluxo normal de I/O de arquivos (erros
de leitura/escrita de arquivo em Java são, na prática, sempre
`IOException` ou subclasses). Um erro completamente não relacionado a I/O
(ex.: `OutOfMemoryError`) não seria capturado em nenhuma das duas versões
de forma equivalente (no Python seria capturado por `except Exception`
apenas se for uma subclasse de `Exception`, o que não é o caso de erros
como `MemoryError`; no Java, `Error` não é `Exception`/`IOException` de
qualquer forma). Comportamento efetivamente equivalente para os casos
relevantes.

DECISÃO: manter `IOException` específico no Java, mais alinhado às boas
práticas pedidas na especificação, sem alterar o comportamento observável
para os casos reais de falha de leitura/escrita.
