package com.transcriptcleaner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * Equivalente Java de:
 *
 * <pre>
 * if __name__ == "__main__":
 *     user_input = input("Enter the file path: ").strip()
 *     transcript_list_file = user_input.encode("unicode_escape").decode()
 *     transcript_loop(transcript_list_file)
 * </pre>
 *
 * <p>A etapa {@code encode("unicode_escape").decode()} foi deliberadamente
 * removida — ver docs/migration/differences.md para a análise completa.
 * Em resumo: essa transformação duplica caracteres de barra invertida
 * (transforma {@code \} em {@code \\}) e não é necessária para o fluxo de
 * leitura de caminhos via {@link java.nio.file.Path}, que já lida
 * corretamente com caminhos Windows/Unix em UTF-8. Mantê-la introduziria um
 * bug (corrompendo caminhos Windows reais) sem nenhum ganho funcional.
 */
public final class Main {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        LoggingConfigurator.configure();

        String userInput = readUserInput();
        TranscriptProcessor processor = new TranscriptProcessor(new TranscriptCleaner());

        try {
            processor.processList(userInput);
        } catch (IOException e) {
            // Replica o comportamento do Python: apenas FileNotFoundError é
            // tratado dentro do fluxo normal; qualquer outra falha de I/O ao
            // ler o arquivo de lista é reportada e encerra a aplicação.
            LOG.severe("Unexpected error while reading the list file: " + e.getMessage());
        }
    }

    private static String readUserInput() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            System.out.print("Enter the file path: ");
            String line = reader.readLine();
            return line == null ? "" : line.strip();
        } catch (IOException e) {
            LOG.severe("Failed to read input: " + e.getMessage());
            return "";
        }
    }
}
