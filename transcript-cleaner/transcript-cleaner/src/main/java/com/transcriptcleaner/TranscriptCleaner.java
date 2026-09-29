package com.transcriptcleaner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Equivalente Java de {@code clean_transcript(file_path, output_path)}.
 *
 * <p>Lê um arquivo de transcript, agrupa falas consecutivas do mesmo
 * speaker e escreve o resultado em {@code output_path}, no mesmo formato
 * do Python original. Erros de leitura/escrita são registrados via log e
 * <b>não</b> são propagados — assim como no Python, uma falha aqui não deve
 * interromper o processamento de outros arquivos da lista (essa garantia é
 * dada pelo chamador, {@link TranscriptProcessor}).
 */
public final class TranscriptCleaner {

    private static final Logger LOG = Logger.getLogger(TranscriptCleaner.class.getName());

    /**
     * Representação textual usada quando nenhum timestamp foi visto antes
     * do início de um bloco. Replica o comportamento do Python, em que
     * {@code f"{speaker_timestamp} \n"} com {@code speaker_timestamp = None}
     * produz literalmente a string "None " (str(None) == "None"). Ver
     * docs/migration/behavior.md, seção 3.1.
     */
    private static final String NO_TIMESTAMP_PLACEHOLDER = "None";

    public void clean(Path inputPath, Path outputPath) {
        List<String> lines;
        try {
            lines = Files.readAllLines(inputPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.info(() -> "Error: Failed to read " + inputPath + ": " + e.getMessage());
            return;
        }

        String content = buildCleanedContent(lines);

        try {
            Files.writeString(outputPath, content, StandardCharsets.UTF_8);
            LOG.info(() -> outputPath + " has been created");
        } catch (IOException e) {
            LOG.info(() -> "Error: Failed to write to " + outputPath + ": " + e.getMessage());
        }
    }

    /**
     * Replica fielmente o loop principal de {@code clean_transcript}: mesma
     * máquina de estados, mesma ordem de verificação (numeração, timestamp,
     * speaker, "else"), mesmas regras de agrupamento/fechamento de bloco.
     */
    String buildCleanedContent(List<String> rawLines) {
        StringBuilder output = new StringBuilder();

        String prevSpeaker = null;
        StringBuilder prevText = new StringBuilder();
        String currentTimestamp = null;
        String speakerTimestamp = null;

        for (String rawLine : rawLines) {
            String line = rawLine.strip();

            if (TranscriptParser.isNumberingLine(line)) {
                continue;
            }

            Optional<String> timestamp = TranscriptParser.matchTimestamp(line);
            if (timestamp.isPresent()) {
                currentTimestamp = timestamp.get();
                continue;
            }

            Optional<TranscriptParser.SpeakerMatch> speakerMatch = TranscriptParser.matchSpeaker(line);
            if (speakerMatch.isPresent()) {
                String speaker = speakerMatch.get().speaker();
                String text = speakerMatch.get().text();

                if (prevSpeaker != null && speaker.equals(prevSpeaker)) {
                    prevText.append(' ').append(text);
                } else {
                    if (prevSpeaker != null) {
                        appendBlock(output, speakerTimestamp, prevSpeaker, prevText.toString());
                    }
                    prevSpeaker = speaker;
                    // Nota: aqui o Python faz `prev_text = f"{text}"`, ou seja,
                    // SOBRESCREVE qualquer texto órfão acumulado antes do
                    // primeiro speaker. Ver docs/migration/behavior.md, 3.2.
                    prevText = new StringBuilder(text);
                    speakerTimestamp = currentTimestamp;
                }
            } else {
                // Linha "solta" (nem numeração, nem timestamp, nem speaker),
                // incluindo linhas vazias. Concatenada mesmo se prevSpeaker
                // ainda for null (bug preservado — ver behavior.md, 3.2).
                prevText.append(' ').append(line);
            }
        }

        if (prevSpeaker != null) {
            appendBlock(output, speakerTimestamp, prevSpeaker, prevText.toString());
        }

        return output.toString();
    }

    private static void appendBlock(StringBuilder output, String timestamp, String speaker, String text) {
        String timestampDisplay = (timestamp == null) ? NO_TIMESTAMP_PLACEHOLDER : timestamp;
        output.append(timestampDisplay).append(" \n")
                .append(speaker).append(": ").append(text)
                .append("\n\n");
    }
}
