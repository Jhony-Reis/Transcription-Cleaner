package com.transcriptcleaner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranscriptProcessorTest {

    private final TranscriptProcessor processor = new TranscriptProcessor(new TranscriptCleaner());

    @Test
    void missingListFileIsHandledWithoutThrowing(@TempDir Path tempDir) throws IOException {
        Path missingList = tempDir.resolve("does-not-exist-list.txt");

        // Equivalente a FileNotFoundError no Python: não deve propagar.
        processor.processList(missingList.toString());
    }

    @Test
    void processesVttFileAndProducesCleanedOutput(@TempDir Path tempDir) throws IOException {
        Path transcript = tempDir.resolve("meeting1.vtt");
        Files.writeString(transcript, "John: Hello there\n", StandardCharsets.UTF_8);

        Path listFile = tempDir.resolve("list.txt");
        Files.writeString(listFile, transcript.toString() + "\n", StandardCharsets.UTF_8);

        processor.processList(listFile.toString());

        Path expectedOutput = tempDir.resolve("meeting1_cleaned.txt");
        assertTrue(Files.exists(expectedOutput));
        assertEquals("None \nJohn: Hello there\n\n", Files.readString(expectedOutput, StandardCharsets.UTF_8));
    }

    @Test
    void extensionMatchingIsCaseInsensitive(@TempDir Path tempDir) throws IOException {
        Path transcript = tempDir.resolve("meeting.VTT");
        Files.writeString(transcript, "John: Hi\n", StandardCharsets.UTF_8);

        Path listFile = tempDir.resolve("list.txt");
        Files.writeString(listFile, transcript.toString() + "\n", StandardCharsets.UTF_8);

        processor.processList(listFile.toString());

        assertTrue(Files.exists(tempDir.resolve("meeting_cleaned.txt")));
    }

    @Test
    void txtExtensionIsAlsoAccepted(@TempDir Path tempDir) throws IOException {
        Path transcript = tempDir.resolve("meeting.TXT");
        Files.writeString(transcript, "John: Hi\n", StandardCharsets.UTF_8);

        Path listFile = tempDir.resolve("list.txt");
        Files.writeString(listFile, transcript.toString() + "\n", StandardCharsets.UTF_8);

        processor.processList(listFile.toString());

        assertTrue(Files.exists(tempDir.resolve("meeting_cleaned.txt")));
    }

    @Test
    void unsupportedExtensionIsSkippedWithoutInterruptingOtherFiles(@TempDir Path tempDir) throws IOException {
        Path badFile = tempDir.resolve("meeting.docx");
        Files.writeString(badFile, "irrelevant", StandardCharsets.UTF_8);

        Path goodFile = tempDir.resolve("meeting.vtt");
        Files.writeString(goodFile, "John: Hi\n", StandardCharsets.UTF_8);

        Path listFile = tempDir.resolve("list.txt");
        Files.writeString(listFile,
                badFile + "\n" + goodFile + "\n",
                StandardCharsets.UTF_8);

        processor.processList(listFile.toString());

        assertFalse(Files.exists(tempDir.resolve("meeting.docx_cleaned.txt")));
        assertTrue(Files.exists(tempDir.resolve("meeting_cleaned.txt")));
    }

    @Test
    void missingInputFileIsSkippedWithoutInterruptingOtherFiles(@TempDir Path tempDir) throws IOException {
        Path missingTranscript = tempDir.resolve("missing.vtt");
        Path goodFile = tempDir.resolve("meeting.vtt");
        Files.writeString(goodFile, "John: Hi\n", StandardCharsets.UTF_8);

        Path listFile = tempDir.resolve("list.txt");
        Files.writeString(listFile,
                missingTranscript + "\n" + goodFile + "\n",
                StandardCharsets.UTF_8);

        processor.processList(listFile.toString());

        assertFalse(Files.exists(tempDir.resolve("missing_cleaned.txt")));
        assertTrue(Files.exists(tempDir.resolve("meeting_cleaned.txt")));
    }

    @Test
    void quotedPathsInListFileAreHandled(@TempDir Path tempDir) throws IOException {
        Path transcript = tempDir.resolve("meeting.vtt");
        Files.writeString(transcript, "John: Hi\n", StandardCharsets.UTF_8);

        Path listFile = tempDir.resolve("list.txt");
        Files.writeString(listFile, "\"" + transcript + "\"\n", StandardCharsets.UTF_8);

        processor.processList(listFile.toString());

        assertTrue(Files.exists(tempDir.resolve("meeting_cleaned.txt")));
    }

    @Test
    void quotedAndPaddedListPathItselfIsHandled(@TempDir Path tempDir) throws IOException {
        Path transcript = tempDir.resolve("meeting.vtt");
        Files.writeString(transcript, "John: Hi\n", StandardCharsets.UTF_8);

        Path listFile = tempDir.resolve("list.txt");
        Files.writeString(listFile, transcript.toString() + "\n", StandardCharsets.UTF_8);

        // Simula o usuário colando o caminho da lista entre aspas, com espaços.
        processor.processList("  \"" + listFile + "\"  ");

        assertTrue(Files.exists(tempDir.resolve("meeting_cleaned.txt")));
    }

    @Test
    void pathsWithSpacesAndUnicodeAreHandled(@TempDir Path tempDir) throws IOException {
        Path dir = Files.createDirectory(tempDir.resolve("Minhas Transcrições"));
        Path transcript = dir.resolve("reunião café.vtt");
        Files.writeString(transcript, "José: Olá, tudo bem?\n", StandardCharsets.UTF_8);

        Path listFile = tempDir.resolve("list.txt");
        Files.writeString(listFile, transcript.toString() + "\n", StandardCharsets.UTF_8);

        processor.processList(listFile.toString());

        Path expectedOutput = dir.resolve("reunião café_cleaned.txt");
        assertTrue(Files.exists(expectedOutput));
        assertEquals("None \nJosé: Olá, tudo bem?\n\n",
                Files.readString(expectedOutput, StandardCharsets.UTF_8));
    }
}
