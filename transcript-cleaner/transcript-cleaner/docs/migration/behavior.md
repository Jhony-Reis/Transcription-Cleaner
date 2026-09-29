# behavior.md — Comportamento do Python original (preservado na migração)

## 1. Fluxo geral

```
input("Enter the file path: ")
  -> strip()
  -> encode("unicode_escape").decode()   # ver differences.md — removido no Java
  -> transcript_loop(caminho_da_lista)
       -> lê arquivo de lista (uma linha por transcript)
       -> para cada linha: strip() + strip('"') -> Path
            -> se não existe: loga erro, continua
            -> se extensão não é .vtt/.txt (case-insensitive): loga erro, continua
            -> calcula output_path = stem + "_cleaned.txt" no mesmo diretório
            -> clean_transcript(input_path, output_path)
```

## 2. `clean_transcript(file_path, output_path)`

1. Lê o arquivo inteiro com `encoding='utf-8'` e faz `.splitlines()`.
   - Se falhar (qualquer `Exception`), loga `Error: Failed to read {file_path}: {e}` e **retorna** sem lançar exceção adiante.
2. Compila 3 regex (uma vez, fora do loop de linhas):
   - `numbering_pattern = r'^\d+$'`
   - `timestamp_pattern = r'^(\d{2}:\d{2}:\d{2}\.\d{3}) --> (\d{2}:\d{2}:\d{2}\.\d{3}$)'`
   - `speaker_pattern = r"^([\w\s\'\-/()]+):\s+(.+)"`
3. Estado mutável durante o loop:
   - `prev_speaker` (None no início)
   - `prev_text` (`""` no início)
   - `current_timestamp` (None no início — último timestamp visto)
   - `speaker_timestamp` (None no início — timestamp "congelado" no começo do bloco atual)
4. Para cada linha (já com `.strip()` aplicado):
   - **Numeração** (`^\d+$`): linha é descartada (`continue`), não afeta nenhum estado.
   - **Timestamp**: se casar, `current_timestamp = grupo(1)` (o timestamp de início; o de fim é ignorado) e `continue`. Não fecha nem abre bloco de speaker.
   - **Speaker** (`^([\w\s'\-/()]+):\s+(.+)`):
     - Se `prev_speaker` existe **e** é igual (string exata) ao novo speaker capturado: **concatena** (`prev_text += " " + text`) — mesmo bloco continua.
     - Caso contrário (speaker novo, ou é o primeiro speaker encontrado):
       - Se já havia um `prev_speaker` (ou seja, não é o primeiro), **fecha** o bloco anterior, produzindo:
         ```
         {speaker_timestamp} \n{prev_speaker}: {prev_text}\n\n
         ```
       - Inicia um novo bloco: `prev_speaker = speaker`, `prev_text = text`, `speaker_timestamp = current_timestamp` (o timestamp mais recente lido até aqui — pode ser `None` se nenhum timestamp apareceu antes deste speaker).
   - **Nenhum dos casos acima** (linha "solta", não numeração/timestamp/speaker, incluindo linhas vazias): `prev_text += " " + line`, **mesmo que `prev_speaker` ainda seja `None`**. Isso é intencional no código original e gera um efeito colateral (ver seção "Bug do texto órfão" abaixo).
5. Ao final do loop, se `prev_speaker` não é `None`, fecha o último bloco pendente (mesmo formato acima).
6. Escreve `"".join(cleaned_lines)` em `output_path` com `encoding='utf-8'`.
   - Se falhar, loga `Error: Failed to write to {output_path}: {e}` (não lança adiante).
   - Se der certo, loga `{output_path} has been created`.

## 3. Comportamentos "estranhos" do original que foram preservados de propósito

### 3.1 "None" literal no timestamp
Se o primeiro speaker do arquivo aparece **antes** de qualquer linha de timestamp válida, `speaker_timestamp` permanece `None` quando o bloco é fechado. O f-string `f"{speaker_timestamp} \n"` produz literalmente a string `"None \n"` (Python imprime `str(None) == "None"`). **Isso foi preservado no Java**: quando não há timestamp registrado, o bloco é escrito com a palavra `None` no lugar do timestamp — não com string vazia, não com `null` omitido.

### 3.2 Bug do texto órfão
Se aparecem linhas de texto "soltas" (que não são numeração, timestamp nem speaker) **antes** do primeiro speaker do arquivo, elas são acumuladas em `prev_text` (que começa como `""`). Porém, assim que o primeiro speaker aparece, o código faz:
```python
prev_speaker = speaker
prev_text = f"{text}"   # SOBRESCREVE prev_text, não concatena
```
Ou seja, todo o texto solto acumulado antes do primeiro speaker é **silenciosamente descartado**. Isso é um bug do código original, mas a especificação da migração pede explicitamente para não "corrigir silenciosamente" esse tipo de comportamento — apenas documentá-lo e cobrir com teste de regressão. **Preservado tal como está no Java.**

### 3.3 Agrupamento por igualdade exata de string
A comparação `speaker == prev_speaker` é uma comparação de string exata (case-sensitive, sensível a espaços). Como o grupo de captura do speaker inclui `\s` na classe de caracteres, variações como `"John"` vs `"John "` (com espaço a mais antes dos dois-pontos) são capturadas como speakers **diferentes** e não são agrupadas. Preservado.

### 3.4 Linhas de numeração são descartadas incondicionalmente
Uma linha como `"42"` é sempre tratada como numeração e descartada, mesmo que apareça no meio de uma fala de um speaker (ela nunca vira parte de `prev_text`). Preservado.

### 3.5 `.splitlines()` vs leitura linha a linha
O Python usa `Path.read_text(...).splitlines()`, que separa linhas por um conjunto mais amplo de terminadores Unicode do que a leitura padrão via `BufferedReader`/`Files.readAllLines` em Java (ver `differences.md`).

## 4. Tratamento de arquivo de lista (`transcript_loop`)

- Apenas `FileNotFoundError` é capturado explicitamente ao ler o arquivo de lista; qualquer outro erro (permissão, encoding inválido, etc.) **não é tratado** e propaga, encerrando o programa com traceback. Esse comportamento foi preservado no Java (apenas `NoSuchFileException` é tratado; demais `IOException` propagam).
- Cada linha do arquivo de lista passa por `strip()` e depois `strip('"')` (remove aspas duplas nas extremidades, quantas houver, não apenas uma). Preservado.
- Extensão é comparada em minúsculas (`.suffix.lower()`), aceitando `.vtt`/`.VTT`/`.Vtt` etc. Preservado.
- Um arquivo inválido/ausente/com extensão não suportada **não interrompe** o processamento dos demais arquivos da lista. Preservado.
