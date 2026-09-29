package com.transcriptcleaner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Equivalente Java de {@code transcript_loop(list_path_str)}.
 *
 * <p>Lê um arquivo de lista (um caminho de transcript por linha) e delega a
 * limpeza de cada arquivo válido para {@link TranscriptCleaner}. Um arquivo
 * inválido, ausente ou com extensão não suportada é registrado via log e
 * <b>não interrompe</b> o processamento dos demais — mesma garantia do
 * Python original.
 */
public final class TranscriptProcessor {

    private static final Logger LOG = Logger.getLogger(TranscriptProcessor.class.getName());

    private static final String VTT_EXTENSION = ".vtt";
    private static final String TXT_EXTENSION = ".txt";
    private static final String CLEANED_SUFFIX = "_cleaned.txt";

    private final TranscriptCleaner cleaner;

    public TranscriptProcessor(TranscriptCleaner cleaner) {
        this.cleaner = Objects.requireNonNull(cleaner, "cleaner");
    }

    /**
     * @param rawListPathInput caminho (possivelmente com aspas e espaços)
     *                         do arquivo que contém a lista de transcripts.
     * @throws IOException qualquer falha de I/O ao ler o arquivo de lista
     *                      que não seja "arquivo não encontrado" — assim
     *                      como o Python original só trata
     *                      {@code FileNotFoundError} nesse ponto e deixa
     *                      qualquer outra exceção propagar.
     */
    public void processList(String rawListPathInput) throws IOException {
        Path listPath = Path.of(stripQuotes(rawListPathInput.strip()));

        List<String> inputFileLines;
        try {
            inputFileLines = Files.readAllLines(listPath, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            LOG.info(() -> "Error: List file not found at " + listPath);
            return;
        }

        for (String inputFileStr : inputFileLines) {
            processEntry(inputFileStr);
        }
    }

    private void processEntry(String inputFileStr) {
        Path inputPath = Path.of(stripQuotes(inputFileStr.strip()));

        if (!Files.exists(inputPath)) {
            LOG.info(() -> "Error: Input file does not exist: " + inputPath);
            return;
        }

        String extension = extensionOf(inputPath).toLowerCase(Locale.ROOT);
        if (!extension.equals(VTT_EXTENSION) && !extension.equals(TXT_EXTENSION)) {
            LOG.info(() -> "Error: Unsupported file type: " + inputPath.getFileName());
            return;
        }

        Path outputPath = buildOutputPath(inputPath);
        cleaner.clean(inputPath, outputPath);
    }

    /**
     * Equivalente a {@code value.strip().strip('"')} do Python: remove
     * espaços nas extremidades e, em seguida, remove <em>todas</em> as
     * aspas duplas nas extremidades (não apenas uma), sem tocar em aspas no
     * meio da string.
     */
    private static String stripQuotes(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == '"') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == '"') {
            end--;
        }
        return value.substring(start, end);
    }

    private static String extensionOf(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot == -1 ? "" : name.substring(dot);
    }

    /** Equivalente a {@code input_path.with_name(input_path.stem + "_cleaned.txt")}. */
    private static Path buildOutputPath(Path inputPath) {
        String name = inputPath.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String stem = dot == -1 ? name : name.substring(0, dot);
        return inputPath.resolveSibling(stem + CLEANED_SUFFIX);
    }
}
