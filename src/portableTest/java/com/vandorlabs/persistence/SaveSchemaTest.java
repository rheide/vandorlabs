package com.vandorlabs.persistence;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

/** Freezes on-disk field identities so a refactor cannot silently orphan old worlds. */
public final class SaveSchemaTest {
    private static Set<String> strings(Class<?> schema) throws Exception {
        Set<String> values = new TreeSet<>();
        for (Field field : schema.getDeclaredFields()) {
            if (field.getType() == String.class && Modifier.isStatic(field.getModifiers())) {
                values.add((String) field.get(null));
            }
        }
        return values;
    }

    private static void expect(Class<?> schema, String... expected) throws Exception {
        Set<String> actual = strings(schema);
        Set<String> wanted = new TreeSet<>(Arrays.asList(expected));
        if (!actual.equals(wanted)) {
            throw new AssertionError(schema.getSimpleName() + " save fields changed: " + actual);
        }
    }

    public static void main(String[] args) throws Exception {
        if (!"VandorDataVersion".equals(SaveSchema.DATA_VERSION)) {
            throw new AssertionError("data-version field changed");
        }
        expect(SaveSchema.Screen.class,
                "selectedScreen", "redstoneEnabled", "displayMode", "framed",
                "animationSpeedIndex", "inputPanel", "secondaryInputPanel",
                "wallPosition", "smallInput");
        expect(SaveSchema.Ramp.class,
                "ControllerVersion", "Controller", "ControllerX", "ControllerY",
                "ControllerZ", "Source", "SourceState", "SourceY", "Row",
                "Length", "Drop", "Segments", "Duration", "Low", "High", "Top",
                "Elevator", "Open", "Moving", "StartPose", "StartTick", "StartOffset", "EndOffset", "TreadPixels",
                "TravelAxis", "ExtendSegments", "Origins", "SpeedMode",
                "LastStepTick", "Status", "ActivateOnPower", "Slow", "Error",
                "MinAlong", "Facing", "RampDirection", "Latched", "SignalKnown",
                "RecoveryPending", "Original", "OriginalState", "Owner", "OwnerId", "Sources", "Cells",
                "Pos", "X", "Y", "Z");
        expect(SaveSchema.Redstone.class,
                "RedstoneChannel", "ChannelSignal", "LocalOn", "ChannelInitialized",
                "ManualOn", "ParticleStream", "LightInitialized", "MountRotation");
        if (SaveSchema.Screen.VERSION != 1 || SaveSchema.Ramp.CONTROLLER_VERSION != 8
                || SaveSchema.Ramp.CELL_VERSION != 6 || SaveSchema.Redstone.VERSION != 2) {
            throw new AssertionError("save schema version changed without updating its contract test");
        }
        System.out.println("Save schema compatibility PASS");
    }
}
