# architecture.md — Arquitetura Java

## Visão geral

```
Main
 └─ lê a entrada do usuário (equivalente a input("Enter the file path: "))
 └─ delega para TranscriptProcessor

TranscriptProcessor
 └─ lê o arquivo de lista de transcripts
 └─ para cada caminho listado: valida existência/extensão, calcula o output path
 └─ delega a limpeza de cada arquivo para TranscriptCleaner

TranscriptCleaner
 └─ lê o arquivo de transcript (UTF-8)
 └─ usa TranscriptParser para reconhecer numeração / timestamp / speaker
 └─ aplica as regras de agrupamento e fechamento de blocos
 └─ escreve o arquivo *_cleaned.txt (UTF-8)

TranscriptParser
 └─ encapsula as 3 expressões regulares (compiladas uma única vez, como
    constantes estáticas) e expõe métodos de reconhecimento por linha
 └─ record SpeakerMatch(speaker, text)

LoggingConfigurator
 └─ configura o java.util.logging para imprimir apenas a mensagem
    (sem timestamp/nível), aproximando o output de um `print()` do Python,
    sem espalhar System.out.println() pela lógica de negócio.
```

## Por que essa divisão

- **Main**: só orquestra (lê stdin, instancia colaboradores, chama o processor).
  Não foi criada uma classe `Cli` separada porque a "entrada do usuário" no
  programa original é uma única linha (`input(...)`) — extrair isso para uma
  classe própria seria criar uma classe artificial só para aumentar a
  contagem de arquivos, o que a especificação pede para evitar. Se no futuro
  a CLI crescer (múltiplos argumentos, flags, etc.), isso deve ser extraído.

- **TranscriptProcessor**: espelha exatamente `transcript_loop`. Não conhece
  as regras de parsing/agrupamento — apenas decide *quais* arquivos processar
  e *para onde* escrever a saída.

- **TranscriptCleaner**: espelha `clean_transcript`. Conhece o algoritmo de
  agrupamento de blocos, mas delega o reconhecimento de padrões (regex) ao
  `TranscriptParser`, o que torna o algoritmo de agrupamento testável
  isoladamente das regras de regex e vice-versa.

- **TranscriptParser**: classe utilitária *stateless*, com os 3 `Pattern`
  compilados como `static final` (nunca recompilados dentro de um loop,
  conforme exigido). Expõe:
  - `boolean isNumberingLine(String line)`
  - `Optional<String> matchTimestamp(String line)`
  - `Optional<SpeakerMatch> matchSpeaker(String line)`

  Uso de `Matcher.lookingAt()` (não `matches()`) em todos os métodos, pois é
  o equivalente direto de `re.match()` do Python: âncora apenas o início da
  string, não exige consumir a string inteira (exceto onde o próprio padrão
  tem `$`, que já força isso).

## Tratamento de erros

- `TranscriptCleaner.clean(...)` **nunca lança exceção** para fora — replica
  o comportamento de `clean_transcript`, que captura erros de leitura e
  escrita internamente e apenas loga, sem propagar. Isso garante que um
  arquivo com problema não interrompa o processamento dos demais (mesma
  garantia que o Python dá via `continue`/`return` local).

- `TranscriptProcessor.processList(...)` captura apenas `NoSuchFileException`
  na leitura do arquivo de lista (equivalente a `FileNotFoundError`); demais
  `IOException` são declaradas com `throws` e propagam para `Main`, que as
  reporta e encerra — replicando o fato de que o Python só trata
  `FileNotFoundError` nesse ponto e deixa qualquer outra exceção "explodir".

- Exceções genéricas (`catch (Exception e)`) foram evitadas; usamos tipos
  específicos (`IOException`, `NoSuchFileException`).

## Modelo de dados

Não foi criada uma classe de domínio "TranscriptBlock" separada porque o
único consumidor do bloco fechado é a própria escrita textual (o Python
também não modela isso como objeto — monta a string diretamente). Um método
privado `appendBlock(...)` em `TranscriptCleaner` cumpre esse papel sem
introduzir uma classe extra.

## Logging

`java.util.logging.Logger` por classe, configurado uma única vez por
`LoggingConfigurator.configure()` (chamado em `Main`) para usar um
`ConsoleHandler` com formatação mínima (só a mensagem), evitando
`System.out.println` espalhado pela lógica de negócio, mas preservando a
visibilidade das mensagens para o usuário final, tal como o `print()` do
Python fazia.
