package com.hexvane.cozytalesranching.species;

import com.google.gson.Gson;
import com.hexvane.cozytalesranching.config.RanchConfig;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

/**
 * Species by id and by NPC role id. Read once at start and on reload; lookups are plain map reads, so
 * calling them from a tick is free. Only roles listed here are ever touched by the mod.
 */
public final class SpeciesRegistry {
    /** Bundled species, in load order. Adding a species = a JSON file plus its id here. */
    public static final String[] BUNDLED = {"Cow", "Chicken", "Pig", "Sheep", "Frog"};
    private static final String RESOURCE_DIR = "/Server/CozyTalesRanching/Species/";
    private static final Logger LOG = Logger.getLogger("CozyTalesRanching");
    private static final Gson GSON = new Gson();

    public enum RoleKind { WILD, WILD_BABY, ADOPT, ADOPT_BABY, RANCH, RANCH_BABY }

    public record RoleMatch(@Nonnull SpeciesDefinition species, @Nonnull RoleKind kind) {}

    private static volatile Map<String, SpeciesDefinition> byId = Map.of();
    private static volatile Map<String, RoleMatch> byRole = Map.of();

    private SpeciesRegistry() {}

    public static synchronized void load(@Nonnull Path dataDirectory) {
        Map<String, SpeciesDefinition> ids = new LinkedHashMap<>();
        for (String name : BUNDLED) {
            try (InputStream in = SpeciesRegistry.class.getResourceAsStream(RESOURCE_DIR + name + ".json")) {
                if (in == null) {
                    LOG.warning("Bundled species file missing: " + name);
                    continue;
                }
                put(ids, read(new InputStreamReader(in, StandardCharsets.UTF_8)), "bundled " + name);
            } catch (IOException | RuntimeException e) {
                LOG.log(Level.WARNING, "Could not read bundled species " + name, e);
            }
        }
        Path overrides = dataDirectory.resolve("Species");
        if (Files.isDirectory(overrides)) {
            try (Stream<Path> files = Files.list(overrides)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                    try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                        put(ids, read(r), file.toString());
                    } catch (IOException | RuntimeException e) {
                        LOG.log(Level.WARNING, "Could not read species override " + file, e);
                    }
                }
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Could not list " + overrides, e);
            }
        }
        RanchConfig config = RanchConfig.get();
        ids.values().removeIf(s -> !config.isSpeciesEnabled(s.Id));

        Map<String, RoleMatch> roles = new LinkedHashMap<>();
        for (SpeciesDefinition s : ids.values()) {
            s.WildRoles.forEach(r -> roles.put(r, new RoleMatch(s, RoleKind.WILD)));
            s.WildBabyRoles.forEach(r -> roles.put(r, new RoleMatch(s, RoleKind.WILD_BABY)));
            if (config.adoptVanillaTamed()) {
                s.AdoptRoles.forEach(r -> roles.put(r, new RoleMatch(s, RoleKind.ADOPT)));
                s.AdoptBabyRoles.forEach(r -> roles.put(r, new RoleMatch(s, RoleKind.ADOPT_BABY)));
            }
            if (!s.RanchRole.isEmpty()) roles.put(s.RanchRole, new RoleMatch(s, RoleKind.RANCH));
            if (!s.RanchBabyRole.isEmpty()) roles.put(s.RanchBabyRole, new RoleMatch(s, RoleKind.RANCH_BABY));
            s.RanchRoleVariants.values().forEach(r -> roles.putIfAbsent(r, new RoleMatch(s, RoleKind.RANCH)));
        }
        if (config.adoptVanillaTamed()) {
            config.extraAdoptRoles().forEach((role, speciesId) -> {
                SpeciesDefinition s = ids.get(speciesId);
                if (s == null) LOG.warning("ExtraAdoptRoles: unknown species '" + speciesId + "' for role " + role);
                else if (!config.isRoleIgnored(role)) roles.putIfAbsent(role, new RoleMatch(s, RoleKind.ADOPT));
            });
        }
        roles.keySet().removeIf(config::isRoleIgnored);
        byId = Map.copyOf(ids);
        byRole = Map.copyOf(roles);
        LOG.info("Loaded " + ids.size() + " species covering " + roles.size() + " NPC roles");
    }

    @Nonnull
    public static Optional<SpeciesDefinition> byId(@Nonnull String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** The species and kind for an NPC role, or empty for every role the mod must leave alone. */
    @Nonnull
    public static Optional<RoleMatch> byRole(@Nonnull String roleId) {
        return Optional.ofNullable(byRole.get(roleId));
    }

    @Nonnull
    public static Collection<SpeciesDefinition> all() {
        return byId.values();
    }

    private static SpeciesDefinition read(Reader reader) {
        SpeciesDefinition s = GSON.fromJson(reader, SpeciesDefinition.class);
        if (s == null || s.Id == null || s.Id.isBlank()) throw new IllegalArgumentException("species file has no Id");
        return s.normalise();
    }

    private static void put(Map<String, SpeciesDefinition> ids, SpeciesDefinition s, String from) {
        if (ids.put(s.Id, s) != null) LOG.info("Species " + s.Id + " replaced by " + from);
    }
}
