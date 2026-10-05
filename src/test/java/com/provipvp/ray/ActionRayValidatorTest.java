package com.provipvp.ray;

import com.provipvp.rotation.GcdRotator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deckt die eine Entscheidung ab, die vor jeder Kampf-Aktion fallen muss: der Strahl aus der
 * WIRKLICH gesendeten Rotation muss auf das beabsichtigte Ziel zeigen.
 */
class ActionRayValidatorTest {

    private static final Vec3 EYE = new Vec3(0.0, 0.9, 0.0);

    /** Spieler-Trefferbox 2.5 Bloecke vor dem Auge, auf Aughoehe. */
    private static final ActionRayValidator.Box TARGET =
        new ActionRayValidator.Box(2.2, 0.0, -0.3, 2.8, 1.8, 0.3);

    /** Leere Welt: nichts steht im Weg. */
    private static final ActionRayValidator.World EMPTY = pos -> false;

    private static ActionRayValidator.World worldOf(BlockPos... solid) {
        Set<BlockPos> cells = new HashSet<>(List.of(solid));
        return cells::contains;
    }

    @Test
    void rotationPointingAtTheTargetPasses() {
        // Yaw -90 sieht in Minecraft nach +X, Pitch 0 ist waagerecht.
        ActionRayValidator.Verdict verdict = ActionRayValidator.validateAttack(
            new GcdRotator.Rotation(-90, 0), EYE, TARGET, ReachPolicy.Action.MELEE, EMPTY);

        assertTrue(verdict.valid(), "Fehler: " + verdict.failure());
        assertEquals(ActionRayValidator.Failure.NONE, verdict.failure());
        assertEquals(0.0, verdict.angleErrorDegrees(), 1e-6);
        assertEquals(2.5, verdict.distance(), 1e-9);
    }

    @Test
    void rotationPointingTwentyDegreesOffFails() {
        // Der Winkel, den ein Rotations-Queue-Verspaeter oder ein unsauber berechneter Winkel
        // typischerweise erzeugt: zu kurz, zu lang, oder schlicht in die falsche Richtung gegessen.
        ActionRayValidator.Verdict verdict = ActionRayValidator.validateAttack(
            new GcdRotator.Rotation(-110, 0), EYE, TARGET, ReachPolicy.Action.MELEE, EMPTY);

        assertFalse(verdict.valid());
        assertEquals(ActionRayValidator.Failure.AIM_MISS, verdict.failure());
        assertEquals(20.0, verdict.angleErrorDegrees(), 1e-6);
    }

    @Test
    void verdictFollowsTheSentRotationEvenWhenTheCameraPointsElsewhere() {
        GcdRotator.Rotation onTarget = new GcdRotator.Rotation(-90, 0);
        GcdRotator.Rotation offTarget = new GcdRotator.Rotation(-110, 0);

        // Gesendet daneben, Kamera daneben: durchgefallen - so darf es nicht aussehen.
        ActionRayValidator.Verdict bothOff = ActionRayValidator.validateAttack(
            offTarget, offTarget, EYE, TARGET, ReachPolicy.Action.MELEE, EMPTY);
        assertFalse(bothOff.valid());
        assertEquals(ActionRayValidator.Failure.AIM_MISS, bothOff.failure());

        // Gesendet daneben, Kamera richtig: das Urteil bleibt "daneben", die Diagnose meldet
        // "die Kamera haette getroffen" - genau das Muster eines Rotations-Queue-Bugs.
        ActionRayValidator.Verdict sentOff = ActionRayValidator.validateAttack(
            offTarget, onTarget, EYE, TARGET, ReachPolicy.Action.MELEE, EMPTY);
        assertFalse(sentOff.valid(), "die Kamera darf das Urteil nicht drehen");
        assertEquals(ActionRayValidator.Failure.AIM_MISS, sentOff.failure());
        assertTrue(sentOff.cameraWouldHit());

        // Und umgekehrt: gesendet richtig, Kamera daneben - gueltig, die Kamera waere daneben.
        ActionRayValidator.Verdict sentOn = ActionRayValidator.validateAttack(
            onTarget, offTarget, EYE, TARGET, ReachPolicy.Action.MELEE, EMPTY);
        assertTrue(sentOn.valid(), "Fehler: " + sentOn.failure());
        assertFalse(sentOn.cameraWouldHit());
    }

    @Test
    void crystalBeyondEntityInteractionRangeIsRejectedBeforeAnythingElse() {
        // End Crystal auf 3.4 Bloecke: der Bot wuerde ihn schlagen wollen, der Server laesst es nicht.
        ActionRayValidator.Box crystal = new ActionRayValidator.Box(3.1, 0.0, -1.0, 3.7, 1.8, 1.0);

        ActionRayValidator.Verdict verdict = ActionRayValidator.validateAttack(
            new GcdRotator.Rotation(-90, 0), EYE, crystal, ReachPolicy.Action.BREAK_ENTITY, EMPTY);

        assertFalse(verdict.valid());
        assertEquals(ActionRayValidator.Failure.OUT_OF_REACH, verdict.failure());
        assertEquals(3.4, verdict.distance(), 1e-9);
    }

    @Test
    void aWallBetweenEyeAndTargetBlocksTheAction() {
        ActionRayValidator.Verdict verdict = ActionRayValidator.validateAttack(
            new GcdRotator.Rotation(-90, 0), EYE, TARGET, ReachPolicy.Action.MELEE,
            worldOf(new BlockPos(1, 0, 0)));

        assertFalse(verdict.valid());
        assertEquals(ActionRayValidator.Failure.OCCLUDED, verdict.failure());
    }

    @Test
    void blockInteractionChecksTheFaceTheRayActuallyHits() {
        // Mauer aus zwei Bloecken, Spieler davor. Yaw 180 sieht nach -Z und trifft damit die
        // Suedseite des vorderen Blocks - die einzige Flaeche, ueber die sich die freie Zelle
        // dahinter setzen laesst.
        ActionRayValidator.World world = worldOf(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0));
        Vec3 eye = new Vec3(0.5, 0.7, 2.5);
        GcdRotator.Rotation sent = new GcdRotator.Rotation(180, 0);

        ActionRayValidator.Verdict hit = ActionRayValidator.validateBlockInteraction(
            sent, eye, new BlockPos(0, 0, 0), Direction.SOUTH, world);
        assertTrue(hit.valid(), "Fehler: " + hit.failure());

        // Dieselbe Rotation zielt auf den Nachbarblock - der Cursor waere dann ausserhalb des
        // Fensters, obwohl die Rotation in dieselbe Richtung zeigt.
        ActionRayValidator.Verdict neighbour = ActionRayValidator.validateBlockInteraction(
            sent, eye, new BlockPos(1, 0, 0), Direction.NORTH, world);
        assertFalse(neighbour.valid());
        assertEquals(ActionRayValidator.Failure.FACE_MISS, neighbour.failure());
    }

    @Test
    void blockInteractionBeyondPlacementRangeIsRejected() {
        // Dieselbe Mauer, aber der Spieler steht 5 Bloecke weg: Platzieren ist hier raus, auch
        // wenn die Rotation sauber auf den Block zeigt.
        ActionRayValidator.World world = worldOf(new BlockPos(0, 0, 0));
        Vec3 eye = new Vec3(0.5, 0.7, 5.5);

        ActionRayValidator.Verdict verdict = ActionRayValidator.validateBlockInteraction(
            new GcdRotator.Rotation(180, 0), eye, new BlockPos(0, 0, 0), Direction.SOUTH, world);

        assertFalse(verdict.valid());
        assertEquals(ActionRayValidator.Failure.OUT_OF_REACH, verdict.failure());
    }
}