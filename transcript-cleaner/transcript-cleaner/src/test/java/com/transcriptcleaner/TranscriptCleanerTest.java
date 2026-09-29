package com.transcriptcleaner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranscriptCleanerTest {

    private final TranscriptCleaner cleaner = new TranscriptCleaner();

    // ---- regra de agrupamento (mesmo speaker consecutivo) ----

    @Test
    void groupsConsecutiveLinesFromSameSpeaker() {
        String content = cleaner.buildCleanedContent(List.of(
                "John: A",
                "John: B",
                "John: C"
        ));

        assertEquals("None \nJohn: A B C\n\n", content);
    }

    // ---- mudança de speaker ----

    @Test
    void doesNotGroupNonConsecutiveSpeakers() {
        String content = cleaner.buildCleanedContent(List.of(
                "John: A",
                "Mary: B",
                "John: C"
        ));

        String expected =
                "None \nJohn: A\n\n" +
                "None \nMary: B\n\n" +
                "None \nJohn: C\n\n";

        assertEquals(expected, content);
    }

    // ---- timestamp do bloco ----

    @Test
    void blockUsesTimestampThatWasCurrentWhenSpeakerStarted() {
        String content = cleaner.buildCleanedContent(List.of(
                "00:00:01.000 --> 00:00:02.000",
                "John: Hello"
        ));

        assertEquals("00:00:01.000 \nJohn: Hello\n\n", content);
    }

    @Test
    void continuingSpeakerKeepsOriginalBlockTimestampEvenAfterNewTimestampLine() {
        String content = cleaner.buildCleanedContent(List.of(
                "00:00:01.000 --> 00:00:02.000",
                "John: Hello",
                "00:00:05.000 --> 00:00:06.000",
                "John: how are you?"
        ));

        // O timestamp do bloco é "congelado" no início da fala do speaker;
        // uma nova linha de timestamp no meio da mesma fala não reabre o
        // timestamp do bloco atual.
        assertEquals("00:00:01.000 \nJohn: Hello how are you?\n\n", content);
    }

    // ---- numeração ignorada ----

    @Test
    void ignoresNumberingLines() {
        String content = cleaner.buildCleanedContent(List.of(
                "1",
                "00:00:01.000 --> 00:00:02.000",
                "John: Hello",
                "2"
        ));

        assertEquals("00:00:01.000 \nJohn: Hello\n\n", content);
    }

    // ---- placeholder "None" quando não há timestamp ----

    @Test
    void usesNoneLiteralWhenNoTimestampWasSeenBeforeFirstSpeaker() {
        String content = cleaner.buildCleanedContent(List.of("John: Hello"));
        assertTrue(content.startsWith("None \n"));
    }

    // ---- bug do texto órfão (preservado de propósito) ----

    @Test
    void orphanTextBeforeFirstSpeakerIsSilentlyDiscarded() {
        // Regressão: texto solto antes do primeiro speaker é sobrescrito
        // (não concatenado) assim que o primeiro speaker aparece — mesmo
        // comportamento do Python original. Ver docs/migration/behavior.md 3.2.
        String content = cleaner.buildCleanedContent(List.of(
                "some orphan narration line",
                "John: Hello"
        ));

        assertEquals("None \nJohn: Hello\n\n", content);
    }

    @Test
    void nonSpeakerLineAfterASpeakerIsAppendedToCurrentBlock() {
        String content = cleaner.buildCleanedContent(List.of(
                "John: Hello",
                "additional narration"
        ));

        assertEquals("None \nJohn: Hello additional narration\n\n", content);
    }

    // ---- linhas em branco / sem correspondência ----

    @Test
    void blankLineBeforeAnySpeakerContributesOnlyWhitespaceAndIsDiscardedLikeOrphanText() {
        String content = cleaner.buildCleanedContent(List.of(
                "",
                "John: Hello"
        ));

        assertEquals("None \nJohn: Hello\n\n", content);
    }

    // ---- arquivo vazio ----

    @Test
    void emptyInputProducesEmptyOutput() {
        assertEquals("", cleaner.buildCleanedContent(List.of()));
    }

    @Test
    void onlyNumberingLinesProduceEmptyOutput() {
        assertEquals("", cleaner.buildCleanedContent(List.of("1", "2", "123")));
    }

    @Test
    void onlyTimestampLineWithoutSpeakerProducesEmptyOutput() {
        assertEquals("", cleaner.buildCleanedContent(List.of("00:00:01.000 --> 00:00:02.000")));
    }

    // ---- múltiplos blocos completos ----

    @Test
    void multipleBlocksAreConcatenatedInOrder() {
        String content = cleaner.buildCleanedContent(List.of(
                "00:00:01.000 --> 00:00:02.000",
                "John: Hello",
                "00:00:05.000 --> 00:00:06.000",
                "Mary: Hi John"
        ));

        String expected =
                "00:00:01.000 \nJohn: Hello\n\n" +
                "00:00:05.000 \nMary: Hi John\n\n";

        assertEquals(expected, content);
    }

    // ---- encoding UTF-8 (nomes/textos acentuados) ----

    @Test
    void preservesUtf8AccentedContent() {
        String content = cleaner.buildCleanedContent(List.of(
                "João: Isso é uma transcrição com acentuação e ação."
        ));

        assertTrue(content.contains("João: Isso é uma transcrição com acentuação e ação."));
    }

    // ---- fluxo completo via arquivo (leitura/escrita real em disco) ----

    @Test
    void cleanReadsInputAndWritesExpectedOutputFile(@TempDir Path tempDir) throws IOException {
        Path input = tempDir.resolve("meeting.vtt");
        Files.writeString(input, String.join("\n",
                "1",
                "00:00:01.000 --> 00:00:04.500",
                "John: Hello",
                "John: how are you?",
                "2",
                "00:00:05.000 --> 00:00:07.000",
                "Mary: Hi John",
                ""
        ), StandardCharsets.UTF_8);

        Path output = tempDir.resolve("meeting_cleaned.txt");

        cleaner.clean(input, output);

        assertTrue(Files.exists(output));
        String written = Files.readString(output, StandardCharsets.UTF_8);

        String expected =
                "00:00:01.000 \nJohn: Hello how are you?\n\n" +
                "00:00:05.000 \nMary: Hi John\n\n";

        assertEquals(expected, written);
    }

    @Test
    void cleanDoesNotThrowWhenInputFileDoesNotExist(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("does-not-exist.vtt");
        Path output = tempDir.resolve("does-not-exist_cleaned.txt");

        // Não deve lançar exceção; apenas loga e retorna, replicando o
        // comportamento do clean_transcript original.
        cleaner.clean(missing, output);

        assertTrue(Files.notExists(output));
    }
}
