package com.hexvane.cozytalesranching.prefs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The chat rule (see {@link RanchChat}): feedback and flavour lines go through {@code RanchChat.tell} and can be turned
 * off; refusals, confirmations, other players' news, asked-for information and command replies are always sent. This
 * reads the mod's send lines and checks each lang key is sent the way the table below says.
 */
class RanchChatRuleTest {
    static final Path SRC = Path.of("src/main/java/com/hexvane/cozytalesranching");
    static final List<String> PACKAGES = List.of("taming", "care", "house", "houseui", "rehome", "wildlife", "journal");

    /** Lines a player can turn off: they only report what the player can already see happen. */
    static final Set<String> TOGGLED = Set.of(
        "cozyranch_taming.tame.progress", "cozyranch_taming.tame.done", "cozyranch_taming.claim.done",
        "cozyranch_taming.follow.on", "cozyranch_taming.follow.off", "cozyranch_taming.feed.favorite",
        "cozyranch_taming.feed.done", "cozyranch_taming.pet.done", "cozyranch_taming.name.done",
        "cozyranch_taming.crickets.tip", "cozyranch_care.feed.given", "cozyranch_care.trough.added",
        "cozyranch_care.trough.taken", "cozyranch_house.place.done", "cozyranch_house.break.done",
        "cozyranch_house.assign.done", "cozyranch_rehome.butcher.done", "cozyranch_wildlife.arrived",
        "cozyranch_wildlife.arrived.rare");

    /** Never toggled, whatever a future edit does: the player needs these to act or to know why nothing happened. */
    static final Pattern MUST_SHOW = Pattern.compile("no_permission|not_yours|not_owner|not_owned|denied|refused|confirm|incoming|offered");

    record Site(String file, boolean toggled, List<String> keys) {}

    @Test
    void feedbackIsToggledAndEverythingElseAlwaysShows() throws IOException {
        List<Site> sites = sites();
        assertTrue(sites.size() > 40, "found " + sites.size() + " send lines");
        Set<String> toggledSeen = new TreeSet<>();
        for (Site s : sites) {
            for (String key : s.keys()) {
                if (s.toggled()) {
                    assertTrue(TOGGLED.contains(key), s.file() + ": " + key + " goes through RanchChat.tell but is not a feedback line");
                    assertFalse(MUST_SHOW.matcher(key).find(), s.file() + ": " + key + " must always show");
                    toggledSeen.add(key);
                } else {
                    assertFalse(TOGGLED.contains(key), s.file() + ": " + key + " is feedback; send it with RanchChat.tell");
                }
            }
        }
        assertEquals(new TreeSet<>(TOGGLED), toggledSeen, "every feedback line is still sent somewhere");
    }

    @Test
    void journalDiscoveryNotesAreToggled() throws IOException {
        String listeners = Files.readString(SRC.resolve("journal/JournalListeners.java"), StandardCharsets.UTF_8);
        assertTrue(listeners.contains("RanchChat.tell(ref, ServerUiOut.message(notice("));
        assertFalse(listeners.contains(".sendMessage("));
    }

    @Test
    void commandRepliesAreNeverToggled() throws IOException {
        for (String pkg : PACKAGES) {
            try (Stream<Path> files = Files.list(SRC.resolve(pkg))) {
                for (Path f : files.filter(p -> p.getFileName().toString().contains("Command")).toList()) {
                    assertFalse(Files.readString(f, StandardCharsets.UTF_8).contains("RanchChat.tell("), f + " replies to a command");
                }
            }
        }
    }

    static List<Site> sites() throws IOException {
        Pattern key = Pattern.compile("\"(cozyranch_[a-z_]+\\.[a-z_.]+)\"");
        List<Site> out = new ArrayList<>();
        for (String pkg : PACKAGES) {
            try (Stream<Path> files = Files.list(SRC.resolve(pkg))) {
                for (Path f : files.filter(p -> p.toString().endsWith(".java") && !p.getFileName().toString().contains("Command")).sorted().toList()) {
                    List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
                    for (int i = 0; i < lines.size(); i++) {
                        String l = lines.get(i);
                        boolean tell = l.contains("RanchChat.tell(");
                        if (!tell && !l.contains(".sendMessage(")) continue;
                        StringBuilder statement = new StringBuilder(l);
                        for (int j = i + 1; j < lines.size() && !statement.toString().trim().endsWith(";"); j++) statement.append(lines.get(j));
                        List<String> keys = new ArrayList<>();
                        Matcher m = key.matcher(statement);
                        while (m.find()) keys.add(m.group(1));
                        out.add(new Site(f.getFileName().toString(), tell, keys));
                    }
                }
            }
        }
        return out;
    }
}
