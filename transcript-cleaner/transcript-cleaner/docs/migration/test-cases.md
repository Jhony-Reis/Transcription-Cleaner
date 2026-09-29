# test-cases.md — Casos de teste

Casos usados para validar a implementação Java, organizados por classe de
teste. Os mesmos cenários podem ser reproduzidos no Python original
(rodando `clean_transcript`/`transcript_loop` sobre os mesmos arquivos de
entrada) para checagem de equivalência.

## TranscriptParserTest

| Caso | Entrada | Esperado |
|---|---|---|
| Numeração válida | `"1"`, `"2"`, `"10"`, `"123"`, `"0"` | reconhecida como numeração |
| Numeração inválida | `""`, `"1a"`, `"a1"`, `"1 2"`, `" 1"`, `"1 "`, `"1.0"` | não reconhecida |
| Timestamp válido | `"00:00:01.000 --> 00:00:04.500"` | casa, grupo 1 = `"00:00:01.000"` |
| Timestamp malformado | vírgula em vez de ponto, hora com 1 dígito, seta errada, texto livre | não casa |
| Timestamp com lixo após o fim | `"... --> 00:00:04.500 extra"` | não casa (a regra tem `$` no 2º grupo) |
| Speakers suportados | `John:`, `John Doe:`, `John-Doe:`, `John/Doe:`, `John (Host):`, `O'Connor:` | todos reconhecidos |
| Captura correta de grupos | `"John Doe: Hello world"` | speaker=`"John Doe"`, texto=`"Hello world"` |
| Speaker exige espaço após `:` | `"John:Hello"` | não reconhecido |
| Speaker com Unicode | `"João: Olá, tudo bem?"` | speaker=`"João"`, texto=`"Olá, tudo bem?"` |
| Linha sem `:` | `"just some narration text"` | não reconhecida como speaker |

## TranscriptCleanerTest

| Caso | Cenário |
|---|---|
| Agrupamento | `John: A` / `John: B` / `John: C` → um único bloco `"John: A B C"` |
| Mudança de speaker | `John: A` / `Mary: B` / `John: C` → três blocos separados |
| Timestamp do bloco | timestamp antes do speaker é usado como timestamp do bloco |
| Timestamp "congelado" | novo timestamp no meio da mesma fala do mesmo speaker não reabre o bloco |
| Numeração ignorada | linhas `"1"`, `"2"` nunca aparecem na saída nem quebram blocos |
| Placeholder `None` | speaker sem timestamp prévio → bloco começa com `"None "` |
| Bug do texto órfão (regressão) | texto solto antes do 1º speaker é descartado ao surgir o 1º speaker |
| Linha adicional após speaker | texto sem speaker após uma fala é concatenado ao bloco atual |
| Linha vazia antes do 1º speaker | contribui apenas espaço, some com o bug do texto órfão |
| Arquivo vazio | lista de linhas vazia → saída vazia |
| Apenas numeração | saída vazia |
| Apenas timestamp, sem speaker | saída vazia |
| Múltiplos blocos completos | dois speakers com timestamps distintos → dois blocos na ordem correta |
| Encoding UTF-8 | speaker/texto com acentuação (`João`, `ção`) preservados no conteúdo gerado |
| Fluxo completo via disco | escreve um `.vtt` de exemplo em `@TempDir`, chama `clean`, lê o `_cleaned.txt` gerado e compara byte a byte com o esperado |
| Arquivo de entrada inexistente | `clean` não lança exceção; nenhum arquivo de saída é criado |

## TranscriptProcessorTest

| Caso | Cenário |
|---|---|
| Lista inexistente | `processList` não lança exceção (equivalente a `FileNotFoundError`) |
| `.vtt` processado | gera `_cleaned.txt` no mesmo diretório |
| Extensão case-insensitive | `.VTT` e `.TXT` são aceitas |
| Extensão não suportada | arquivo é ignorado, mas os demais da lista continuam sendo processados |
| Arquivo de entrada inexistente | é ignorado (log), mas os demais da lista continuam sendo processados |
| Caminhos entre aspas | aspas nas extremidades de cada linha da lista são removidas |
| Caminho da própria lista entre aspas/espaços | também tratado corretamente |
| Caminhos com espaço e Unicode | diretório `"Minhas Transcrições"` e arquivo `"reunião café.vtt"` processados corretamente, preservando acentuação no nome do `_cleaned.txt` gerado |

## Casos de borda adicionais cobertos (mapeamento com a especificação)

- arquivo vazio → `emptyInputProducesEmptyOutput`
- apenas numeração → `onlyNumberingLinesProduceEmptyOutput`
- apenas timestamp → `onlyTimestampLineWithoutSpeakerProducesEmptyOutput`
- speaker sem texto → coberto indiretamente pela exigência de `\s+(.+)` no parser (não há match sem texto; comportamento idêntico ao Python)
- texto sem speaker → `nonSpeakerLineAfterASpeakerIsAppendedToCurrentBlock` / `orphanTextBeforeFirstSpeakerIsSilentlyDiscarded`
- múltiplos speakers → `doesNotGroupNonConsecutiveSpeakers`
- mesmo speaker repetido → `groupsConsecutiveLinesFromSameSpeaker`
- speaker com Unicode → `supportsUnicodeSpeakerNames` / `preservesUtf8AccentedContent`
- linha vazia → `blankLineBeforeAnySpeakerContributesOnlyWhitespaceAndIsDiscardedLikeOrphanText`
- múltiplas linhas de texto → `continuingSpeakerKeepsOriginalBlockTimestampEvenAfterNewTimestampLine`
- timestamp sem speaker → `onlyTimestampLineWithoutSpeakerProducesEmptyOutput`
- arquivo sem timestamp → `usesNoneLiteralWhenNoTimestampWasSeenBeforeFirstSpeaker`
- arquivo com espaços/aspas no nome/caminho → `pathsWithSpacesAndUnicodeAreHandled`, `quotedPathsInListFileAreHandled`

## Validação manual (fora do JUnit)

Como o ambiente de geração deste projeto não tinha Maven/JUnit disponíveis
offline, os casos centrais de `TranscriptCleanerTest` e
`TranscriptParserTest` foram replicados em um harness manual
(`ManualCheck`, não incluído no projeto final) e executados com
`javac`/`java` puros, com **todos os 15 casos passando**. Isso dá
confiança de que a lógica está correta, mas recomenda-se fortemente rodar
`mvn test` no ambiente do usuário antes de considerar a migração concluída,
conforme o critério de conclusão da especificação.
