package com.transcriptcleaner;

import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Configura o {@code java.util.logging} raiz do pacote da aplicação para
 * imprimir apenas a mensagem (sem timestamp, nível ou nome de classe),
 * aproximando a saída do {@code print(...)} usado no script Python
 * original, sem espalhar {@code System.out.println} pela lógica de
 * negócio.
 */
final class LoggingConfigurator {

    private LoggingConfigurator() {
    }

    static void configure() {
        Logger root = Logger.getLogger("com.transcriptcleaner");
        root.setUseParentHandlers(false);

        for (var handler : root.getHandlers()) {
            root.removeHandler(handler);
        }

        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.ALL);
        handler.setFormatter(new Formatter() {
            @Override
            public String format(LogRecord record) {
                return record.getMessage() + System.lineSeparator();
            }
        });

        root.addHandler(handler);
        root.setLevel(Level.ALL);
    }
}
