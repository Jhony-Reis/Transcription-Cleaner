package com.transcriptcleaner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranscriptParserTest {

    // ---- numbering ----

    @ParameterizedTest
    @ValueSource(strings = {"1", "2", "10", "123", "0"})
    void recognizesNumberingLines(String line) {
        assertTrue(TranscriptParser.isNumberingLine(line));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1a", "a1", "1 2", " 1", "1 ", "1.0"})
    void rejectsNonNumberingLines(String line) {
        assertFalse(TranscriptParser.isNumberingLine(line));
    }

    // ---- timestamp ----

    @Test
    void recognizesValidTimestampAndCapturesOnlyStart() {
        Optional<String> match = TranscriptParser.matchTimestamp("00:00:01.000 --> 00:00:04.500");
        assertTrue(match.isPresent());
        assertEquals("00:00:01.000", match.get());
    }

    @Test
    void rejectsMalformedTimestamp() {
        assertTrue(TranscriptParser.matchTimestamp("00:00:01,000 --> 00:00:04,500").isEmpty());
        assertTrue(TranscriptParser.matchTimestamp("0:00:01.000 --> 00:00:04.500").isEmpty());
        assertTrue(TranscriptParser.matchTimestamp("00:00:01.000 -> 00:00:04.500").isEmpty());
        assertTrue(TranscriptParser.matchTimestamp("just some text").isEmpty());
    }

    @Test
    void rejectsTimestampWithTrailingGarbage() {
        // O segundo grupo termina com $, então texto extra após o segundo
        // timestamp invalida o match, assim como no Python.
        assertTrue(TranscriptParser.matchTimestamp("00:00:01.000 --> 00:00:04.500 extra").isEmpty());
    }

    // ---- speaker ----

    @ParameterizedTest
    @ValueSource(strings = {
            "John: Hello",
            "John Doe: Hello",
            "John-Doe: Hello",
            "John/Doe: Hello",
            "John (Host): Hello",
            "O'Connor: Hello"
    })
    void recognizesSupportedSpeakerFormats(String line) {
        assertTrue(TranscriptParser.matchSpeaker(line).isPresent());
    }

    @Test
    void capturesSpeakerAndTextGroups() {
        Optional<TranscriptParser.SpeakerMatch> match = TranscriptParser.matchSpeaker("John Doe: Hello world");
        assertTrue(match.isPresent());
        assertEquals("John Doe", match.get().speaker());
        assertEquals("Hello world", match.get().text());
    }

    @Test
    void requiresAtLeastOneWhitespaceAfterColon() {
        // \s+ exige ao menos um espaço/whitespace entre ':' e o texto
        assertTrue(TranscriptParser.matchSpeaker("John:Hello").isEmpty());
    }

    @Test
    void supportsUnicodeSpeakerNames() {
        Optional<TranscriptParser.SpeakerMatch> match = TranscriptParser.matchSpeaker("João: Olá, tudo bem?");
        assertTrue(match.isPresent());
        assertEquals("João", match.get().speaker());
        assertEquals("Olá, tudo bem?", match.get().text());
    }

    @Test
    void lineWithoutColonIsNotASpeakerLine() {
        assertTrue(TranscriptParser.matchSpeaker("just some narration text").isEmpty());
    }
}
