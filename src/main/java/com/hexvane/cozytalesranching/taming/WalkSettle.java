package com.hexvane.cozytalesranching.taming;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.movement.MotionKind;
import com.hypixel.hytale.server.npc.movement.controllers.MotionController;
import com.hypixel.hytale.server.npc.movement.controllers.MotionControllerBase;
import com.hypixel.hytale.server.npc.role.Role;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/**
 * A guard against an owned animal looping its walk while it stands still (fix4, "the bat just loops its walking
 * animation"). The client picks Walk from the movement states the server sends, so a server that kept saying
 * {@code walking} for a still animal would show exactly that.
 * <p>
 * No such case was found on the server in fix4 (the one a live harness showed was the harness dropping its last
 * updates for the animal): the walk controller runs a move step every tick, even an empty one, and that
 * step sets STANDING on the ground. This stays as a cheap net and a probe. Once a second (the follow pass) an owned
 * animal that the server calls walking or running, that has not moved for a whole pass, whose body steering asks for no
 * translation and which stands on the ground is put back to standing: the controller's motion kind and the movement
 * states, as a standing step would leave them. Its next real step sets them again as usual. Each time it does that it
 * logs one line (at most one a minute per animal), so a playtest log shows whether the server ever got there.
 * <p>
 * Runs from the follow pass's world task (outside the tick), so it writes the components directly.
 */
final class WalkSettle {
    /** Horizontal movement (blocks) over one pass below which the animal counts as standing still. */
    static final double STILL_BLOCKS = 0.02;
    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger("CozyTalesRanching");

    private record Last(double x, double z) {}

    private static final Map<UUID, Last> LAST = new ConcurrentHashMap<>();
    /** Last time a settle was logged per animal: at most one line a minute each, so a repeat cannot flood the log. */
    private static final Map<UUID, Long> LOGGED = new ConcurrentHashMap<>();
    private static final long LOG_EVERY_MS = 60_000L;

    private WalkSettle() {}

    static void forget(@Nonnull UUID animalId) {
        LAST.remove(animalId);
        LOGGED.remove(animalId);
    }

    /**
     * The rule, pure. True when the server says the animal walks or runs while it has stood still for a whole pass
     * (it had a position last pass and moved less than {@link #STILL_BLOCKS} since), nothing steers it and it is on
     * the ground (a falling or flying animal is left to the engine).
     */
    static boolean stuckWalking(boolean walking, boolean running, boolean onGround, boolean steeringIdle,
                                boolean hadLast, double dx, double dz) {
        if (!(walking || running) || !onGround || !steeringIdle || !hadLast) return false;
        return dx * dx + dz * dz < STILL_BLOCKS * STILL_BLOCKS;
    }

    /** The standing states the walk controller writes for a standing NPC (MotionControllerBase.updateStandingStates). */
    static void standStill(@Nonnull MovementStates m) {
        m.idle = true;
        m.horizontalIdle = true;
        m.walking = false;
        m.running = false;
        m.sprinting = false;
        m.falling = false;
        m.swimming = false;
        m.jumping = false;
        m.climbing = false;
    }

    /** One pass for one owned animal. Cost: three component reads; a write only when it is stuck walking. */
    static void check(@Nonnull UUID animalId, @Nonnull Ref<EntityStore> animal, @Nonnull Vector3d pos, @Nonnull Store<EntityStore> store) {
        Last before = LAST.put(animalId, new Last(pos.x, pos.z));
        MovementStatesComponent msc = store.getComponent(animal, MovementStatesComponent.getComponentType());
        NPCEntity npc = store.getComponent(animal, NPCEntity.getComponentType());
        if (msc == null || npc == null) return;
        Role role = npc.getRole();
        if (role == null) return;
        MovementStates m = msc.getMovementStates();
        boolean steeringIdle = role.getBodySteering().getTranslation().lengthSquared() == 0;
        double dx = before == null ? 0 : pos.x - before.x();
        double dz = before == null ? 0 : pos.z - before.z();
        if (!stuckWalking(m.walking, m.running, m.onGround, steeringIdle, before != null, dx, dz)) return;
        MotionController controller = role.getActiveMotionController();
        MotionKind kind = controller instanceof MotionControllerBase base ? base.getMotionKind() : null;
        long now = System.currentTimeMillis();
        Long logged = LOGGED.get(animalId);
        if (logged == null || now - logged >= LOG_EVERY_MS) {
            LOGGED.put(animalId, now);
            LOG.info(String.format(java.util.Locale.ROOT,
                "Ranch animal %s stood still while walking (motion %s, body %s, controller %s): settles to standing",
                animalId, kind, role.getSteeringMotionName(), controller == null ? "?" : controller.getType()));
        }
        if (controller instanceof MotionControllerBase base && kind != MotionKind.STANDING && kind != MotionKind.DROPPING) {
            base.setMotionKind(MotionKind.STANDING);
        }
        standStill(m);
    }
}
