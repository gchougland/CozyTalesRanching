package com.hexvane.cozytalesranching.persist;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

/**
 * The one way every ranch save file is written: the new text to {@code <file>.tmp}, forced to disk; the current file
 * renamed to {@code <file>.prev}; then the temp file renamed over the real one. Renames move no data, so a save costs
 * one write of the new text and nothing more (the old way copied the whole previous file every time).
 *
 * <p>Crash safety: before the first rename the main file is untouched (a half-written temp file is ignored and
 * deleted by the readers). Between the two renames there is no main file, but the temp file is whole (it was forced
 * before either rename): {@link #heal} puts it in place, or the {@code .prev} copy if the temp file does not parse.
 * Every store calls {@link #healDir} before it lists its folder, so no save is ever lost to that moment.
 */
public final class SafeWrite {
    public static final String TMP = ".tmp";
    public static final String PREV = ".prev";

    private SafeWrite() {}

    /** Atomic replace. Throws on failure; the old file (or its {@code .prev}) stays whole in that case. */
    public static void write(@Nonnull Path file, @Nonnull String text) throws IOException {
        Path parent = file.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path tmp = sibling(file, TMP);
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            ByteBuffer buf = ByteBuffer.wrap(bytes);
            while (buf.hasRemaining()) ch.write(buf);
            ch.force(true);
        }
        if (Files.isRegularFile(file)) move(file, sibling(file, PREV));
        move(tmp, file);
    }

    /**
     * Repairs an interrupted {@link #write}: when {@code file} is missing but its temp file is there, the temp file
     * becomes the main file if it is whole JSON, else the {@code .prev} copy is copied back. A missing file with no
     * temp file is left alone (nothing was being written; it may have been deleted on purpose). Never throws.
     */
    public static void heal(@Nonnull Path file) {
        if (Files.exists(file)) return;
        Path tmp = sibling(file, TMP);
        if (!Files.isRegularFile(tmp)) return;
        try {
            if (isWholeJson(tmp)) {
                move(tmp, file);
                return;
            }
            Path prev = sibling(file, PREV);
            if (Files.isRegularFile(prev)) Files.copy(prev, file);
        } catch (IOException | RuntimeException ignored) {
            // the readers still fall back to .prev
        }
    }

    /** {@link #heal} for every interrupted save in a folder ({@code <name>.tmp} with no {@code <name>}). Never throws. */
    public static void healDir(@Nonnull Path dir) {
        if (!Files.isDirectory(dir)) return;
        List<Path> temps;
        try (Stream<Path> s = Files.list(dir)) {
            temps = s.filter(p -> p.getFileName().toString().endsWith(TMP)).toList();
        } catch (IOException | RuntimeException e) {
            return;
        }
        for (Path tmp : temps) {
            String name = tmp.getFileName().toString();
            heal(tmp.resolveSibling(name.substring(0, name.length() - TMP.length())));
        }
    }

    static boolean isWholeJson(@Nonnull Path p) {
        try {
            String text = Files.readString(p, StandardCharsets.UTF_8);
            if (!text.isEmpty() && text.charAt(0) == '﻿') text = text.substring(1);
            if (text.isBlank()) return false;
            JsonParser.parseString(text);
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private static void move(@Nonnull Path from, @Nonnull Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Nonnull
    public static Path sibling(@Nonnull Path file, @Nonnull String suffix) {
        return file.resolveSibling(file.getFileName() + suffix);
    }
}
