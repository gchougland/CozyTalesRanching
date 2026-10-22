package com.hexvane.cozytalesranching.breeding;

import com.hexvane.cozytalesranching.RanchPermissions;
import com.hexvane.cozytalesranching.animal.AnimalRecord;
import com.hexvane.cozytalesranching.animal.Ranch;
import com.hexvane.cozytalesranching.house.HouseRecord;
import com.hexvane.cozytalesranching.species.SpeciesDefinition;
import com.hexvane.cozytalesranching.species.SpeciesRegistry;
import com.hexvane.cozytalesranching.species.SpeciesRegistry.RoleKind;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/**
 * {@code /ranch breed testhouse <species> [--traits=a+b] [--role=<ranch role>]}: a house record at the admin's feet with two adult residents
 * asleep inside, five hearts and full happiness, ready to breed. A test fixture: it places no block.
 */
final class BreedTestHouseCommand extends AbstractPlayerCommand {
    private final RequiredArg<String> speciesArg;
    private final OptionalArg<String> traitsArg;
    private final OptionalArg<String> roleArg;

    BreedTestHouseCommand() {
        super("testhouse", "cozyranch_breeding.commands.breed.testhouse.desc");
        this.requirePermission(RanchPermissions.ADMIN);
        this.speciesArg = withRequiredArg("species", "cozyranch_breeding.commands.breed.testhouse.arg.species", ArgTypes.STRING);
        this.traitsArg = withOptionalArg("traits", "cozyranch_breeding.commands.breed.testhouse.arg.traits", ArgTypes.STRING);
        this.roleArg = withOptionalArg("role", "cozyranch_breeding.commands.breed.testhouse.arg.role", ArgTypes.STRING);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        String speciesId = context.get(speciesArg);
        SpeciesDefinition species = SpeciesRegistry.byId(speciesId).orElse(null);
        if (species == null) {
            context.sendMessage(Message.translation("cozyranch_breeding.debug.unknownSpecies").param("species", speciesId));
            return;
        }
        TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
        if (t == null) return;
        Vector3d pos = t.getPosition();
        HouseRecord house = Ranch.houses().create(playerRef.getUuid(), species.Id, species.HouseBlock, world.getName(),
            (int) Math.floor(pos.x), (int) Math.floor(pos.y), (int) Math.floor(pos.z), 0);
        house.capacity = species.HouseCapacity;
        Ranch.houses().markDirty(house);

        TraitBook book = TraitRegistry.get();
        List<String> traits = context.provided(traitsArg)
            ? Arrays.stream(context.get(traitsArg).split("[,+;]")).map(String::trim).filter(book::has).toList()
            : List.of();
        String role = species.RanchRole;
        if (context.provided(roleArg)) {
            String wanted = context.get(roleArg);
            if (!SpeciesRegistry.byRole(wanted).map(m -> m.species() == species && m.kind() == RoleKind.RANCH).orElse(false)) {
                context.sendMessage(Message.translation("cozyranch_breeding.debug.testhouse.badRole").param("role", wanted));
                return;
            }
            role = wanted;
        }
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            AnimalRecord r = Ranch.animals().create(species.Id, playerRef.getUuid(), world.getName(), false);
            r.houseId = house.houseId;
            r.state = AnimalRecord.State.STORED;
            r.hearts = 5;
            r.happiness = 1f;
            r.traits = new ArrayList<>(traits);
            r.spawnRole = role.isEmpty() ? null : role;
            r.lastX = house.doorX;
            r.lastY = house.doorY;
            r.lastZ = house.doorZ;
            Ranch.animals().markDirty(r);
            ids.add(r.animalId.toString());
        }
        context.sendMessage(Message.translation("cozyranch_breeding.debug.testhouse.done")
            .param("house", house.houseId.toString())
            .param("species", species.Id)
            .param("parents", String.join(", ", ids))
            .param("traits", traits.isEmpty() ? "-" : String.join(", ", traits)));
    }
}
