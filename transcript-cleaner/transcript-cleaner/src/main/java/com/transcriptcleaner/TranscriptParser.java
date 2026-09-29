package com.transcriptcleaner;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reconhece linhas de numeração, timestamp e speaker, replicando o
 * comportamento das expressões regulares originais em Python:
 *
 * <pre>
 * numbering_pattern = re.compile(r'^\d+$')
 * timestamp_pattern = re.compile(
 *     r'^(\d{2}:\d{2}:\d{2}\.\d{3}) --> (\d{2}:\d{2}:\d{2}\.\d{3}$)'
 * )
 * speaker_pattern = re.compile(r"^([\w\s\'\-/()]+):\s+(.+)")
 * </pre>
 *
 * <p>Decisão de design (ver docs/migration/differences.md): para os padrões
 * de numeração e timestamp usamos {@code \d} no sentido ASCII padrão do Java
 * (o Python, em modo Unicode, também casaria outros dígitos decimais
 * Unicode, mas isso não tem relevância prática em arquivos .vtt/.txt reais
 * e evitar isso reduz falsos positivos). Já para o padrão de speaker,
 * habilitamos {@link Pattern#UNICODE_CHARACTER_CLASS} para que {@code \w} e
 * {@code \s} se comportem como no Python (necessário para nomes acentuados
 * como "João", "José").
 *
 * <p>Todos os padrões são usados com {@link Matcher#lookingAt()}, que é o
 * equivalente direto de {@code re.match()} do Python: ancora apenas o
 * início da string (o fim só é exigido quando o próprio padrão contém
 * {@code $}).
 */
public final class TranscriptParser {

    private static final Pattern NUMBERING_PATTERN = Pattern.compile("^\\d+$");

    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile(
            "^(\\d{2}:\\d{2}:\\d{2}\\.\\d{3}) --> (\\d{2}:\\d{2}:\\d{2}\\.\\d{3}$)");

    private static final Pattern SPEAKER_PATTERN = Pattern.compile(
            "^([\\w\\s'\\-/()]+):\\s+(.+)", Pattern.UNICODE_CHARACTER_CLASS);

    private TranscriptParser() {
        // utilitária, sem estado
    }

    /** Equivalente a {@code numbering_pattern.match(line)}. */
    public static boolean isNumberingLine(String line) {
        return NUMBERING_PATTERN.matcher(line).lookingAt();
    }

    /**
     * Equivalente a {@code timestamp_pattern.match(line)}, retornando o
     * primeiro grupo (timestamp de início) quando há match. O segundo
     * timestamp (fim) é reconhecido pela regex mas, assim como no Python,
     * nunca é utilizado.
     */
    public static Optional<String> matchTimestamp(String line) {
        Matcher matcher = TIMESTAMP_PATTERN.matcher(line);
        if (matcher.lookingAt()) {
            return Optional.of(matcher.group(1));
        }
        return Optional.empty();
    }

    /** Equivalente a {@code speaker_pattern.match(line)}. */
    public static Optional<SpeakerMatch> matchSpeaker(String line) {
        Matcher matcher = SPEAKER_PATTERN.matcher(line);
        if (matcher.lookingAt()) {
            return Optional.of(new SpeakerMatch(matcher.group(1), matcher.group(2)));
        }
        return Optional.empty();
    }

    /** Par (speaker, texto) capturado pelo padrão de speaker. */
    public record SpeakerMatch(String speaker, String text) {
    }
}
