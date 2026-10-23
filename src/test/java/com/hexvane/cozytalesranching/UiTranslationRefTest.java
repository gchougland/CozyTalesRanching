package com.hexvane.cozytalesranching;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** The client's .ui parser reads a {@code %key} only as letters, digits and dots; anything else fails the whole page load. */
class UiTranslationRefTest {
    private static final Pattern REF = Pattern.compile("%([^\\s;,)]+)");
    private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9.]+");

    @Test
    void uiTranslationKeysUseOnlyLettersDigitsAndDots() throws IOException {
        List<String> bad = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/resources/Common/UI"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".ui")).toList()) {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    Matcher m = REF.matcher(lines.get(i));
                    while (m.find()) {
                        if (!ALLOWED.matcher(m.group(1)).matches()) bad.add(file + ":" + (i + 1) + " %" + m.group(1));
                    }
                }
            }
        }
        assertEquals(List.of(), bad, "set these texts from Java instead");
    }
}
