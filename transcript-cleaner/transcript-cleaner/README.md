# Transcript Cleaner (Java)

Migração para Java 21 do script Python original de limpeza de transcripts
de reunião (`.vtt`/`.txt`), preservando o comportamento observável do
código Python — inclusive suas peculiaridades. Veja
`docs/migration/behavior.md` e `docs/migration/differences.md` para os
detalhes da análise de migração.

## Objetivo

Ler transcripts brutos (formato tipo VTT: linhas de numeração, timestamps
`HH:MM:SS.mmm --> HH:MM:SS.mmm` e falas no formato `Speaker: texto`) e
produzir uma versão "limpa", agrupando falas consecutivas do mesmo speaker
em um único bloco, prefixado pelo timestamp de início da fala.

## Entrada

A aplicação pede, via terminal, o caminho de um **arquivo de lista**: um
arquivo de texto simples com um caminho de transcript por linha, por
exemplo:

```
"C:\transcripts\meeting1.vtt"
"C:\transcripts\meeting2.vtt"
/home/user/transcripts/meeting3.txt
```

Aspas duplas nas extremidades de cada linha (e da própria linha do
caminho de lista) são removidas automaticamente, assim como espaços em
branco nas extremidades.

## Formatos suportados

- `.vtt`
- `.txt`

A comparação de extensão é *case-insensitive* (`.VTT`, `.Vtt`, `.TXT` etc.
são todos aceitos). Qualquer outra extensão é reportada como não suportada
e o arquivo é ignorado (os demais arquivos da lista continuam sendo
processados normalmente).

## Saída

Para cada arquivo de entrada `nome.ext`, é gerado `nome_cleaned.txt` no
**mesmo diretório** do arquivo original. Exemplo:

```
meeting1.vtt  -> meeting1_cleaned.txt
meeting2.txt  -> meeting2_cleaned.txt
```

Cada bloco de fala no arquivo de saída segue o formato:

```
00:00:01.000 
John: Hello world

00:00:05.000 
Mary: Hi John

```

> Se nenhum timestamp foi lido antes do início de uma fala, o bloco usa o
> texto literal `None` no lugar do timestamp — esse é um comportamento do
> script Python original, intencionalmente preservado. Veja
> `docs/migration/behavior.md`.

## Execução

Pré-requisitos: JDK 21+ e Maven.

Rodar os testes:

```bash
mvn test
```

Empacotar a aplicação:

```bash
mvn package
```

Isso gera `target/transcript-cleaner.jar`. Para executar:

```bash
java -jar target/transcript-cleaner.jar
```

A aplicação então pergunta:

```
Enter the file path:
```

Informe o caminho do arquivo de lista (com ou sem aspas) e pressione
Enter. O progresso e eventuais erros são impressos no console.

## Estrutura do projeto

```
transcript-cleaner/
├── pom.xml
├── README.md
├── docs/migration/          # documentação da migração Python -> Java
└── src/
    ├── main/java/com/transcriptcleaner/
    │   ├── Main.java                 # entrada da aplicação (CLI)
    │   ├── TranscriptProcessor.java  # processa o arquivo de lista
    │   ├── TranscriptCleaner.java    # limpeza/agrupamento de um transcript
    │   ├── TranscriptParser.java     # regex de numeração/timestamp/speaker
    │   └── LoggingConfigurator.java  # configuração mínima de logging
    └── test/java/com/transcriptcleaner/
        ├── TranscriptParserTest.java
        ├── TranscriptCleanerTest.java
        └── TranscriptProcessorTest.java
```

## Nota sobre validação neste repositório

Os testes foram escritos em JUnit 5 e a lógica principal (parsing e
agrupamento) foi validada manualmente com um harness sem dependências
externas antes da entrega, reproduzindo os mesmos casos usados nos testes
JUnit — mas o ambiente onde este projeto foi gerado não tem acesso à
internet nem Maven instalado, então `mvn test`/`mvn package` **não foram
executados aqui**. Recomenda-se rodar `mvn test` localmente antes de usar
em produção.
