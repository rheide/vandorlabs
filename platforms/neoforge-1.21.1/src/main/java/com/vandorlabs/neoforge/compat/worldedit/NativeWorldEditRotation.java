package com.vandorlabs.neoforge.compat.worldedit;

import com.mojang.logging.LogUtils;
import com.vandorlabs.neoforge.propulsion.NativePropulsionBlock;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/** Optional WorldEdit 7 bridge for rotations that change a native block ID. */
public final class NativeWorldEditRotation {
    private static final AtomicBoolean REPORTED_FAILURE = new AtomicBoolean();
    private static final Rotation[] TURNS = {
        Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90
    };

    public static Object transform(Object holder, Object transformation) {
        var api = OptionalApi.INSTANCE;
        if (api == null || !api.affineType.isInstance(transformation)) return null;
        try {
            return api.rotateWedge(holder, transformation);
        } catch (ReflectiveOperationException | LinkageError failure) {
            report(failure);
            return null;
        }
    }

    private static void report(Throwable failure) {
        if (REPORTED_FAILURE.compareAndSet(false, true)) {
            LogUtils.getLogger().warn("Could not apply native wedge rotations with the installed WorldEdit", failure);
        }
    }

    private static Rotation quarterTurn(double[] matrix) {
        if (matrix.length != 12 || !matches(matrix[1], 0) || !matches(matrix[4], 0)
            || !matches(matrix[5], 1) || !matches(matrix[6], 0) || !matches(matrix[9], 0)) return null;
        for (var turn : TURNS) {
            var east = turn.rotate(Direction.EAST);
            var south = turn.rotate(Direction.SOUTH);
            if (matches(matrix[0], east.getStepX()) && matches(matrix[8], east.getStepZ())
                && matches(matrix[2], south.getStepX()) && matches(matrix[10], south.getStepZ())) return turn;
        }
        return null;
    }

    private static boolean matches(double actual, int expected) {
        return Math.abs(actual - expected) < 1e-8;
    }

    private static final class OptionalApi {
        private static final OptionalApi INSTANCE = load();
        private final Class<?> affineType, baseBlockType, stateType;
        private final Method blockType, blockId, immutableState, fromWorldEdit, toWorldEdit, coefficients, nbt, withNbt;

        private OptionalApi() throws ReflectiveOperationException {
            affineType = type("math.transform.AffineTransform");
            baseBlockType = type("world.block.BaseBlock");
            stateType = type("world.block.BlockState");
            var holderType = type("world.block.BlockStateHolder");
            var adapter = type("neoforge.NeoForgeAdapter");
            blockType = holderType.getMethod("getBlockType");
            blockId = type("world.block.BlockType").getMethod("id");
            immutableState = holderType.getMethod("toImmutableState");
            fromWorldEdit = adapter.getMethod("adapt", stateType);
            toWorldEdit = adapter.getMethod("adapt", BlockState.class);
            coefficients = affineType.getMethod("coefficients");
            nbt = baseBlockType.getMethod("getNbtReference");
            withNbt = stateType.getMethod("toBaseBlock", type("util.concurrency.LazyReference"));
        }

        private Object rotateWedge(Object holder, Object transformation) throws ReflectiveOperationException {
            String id = (String) blockId.invoke(blockType.invoke(holder));
            if (!id.startsWith("vandorlabs:") || !id.contains("_wedge")) return null;
            boolean fullBlock = baseBlockType.isInstance(holder);
            if (!fullBlock && !stateType.isInstance(holder)) return null;
            var current = (BlockState) fromWorldEdit.invoke(null, immutableState.invoke(holder));
            if (!(current.getBlock() instanceof NativePropulsionBlock wedge) || wedge.definition().shape != 2
                || current.getValue(NativePropulsionBlock.FACING).getAxis() != Direction.Axis.Y) return null;
            var rotation = quarterTurn((double[]) coefficients.invoke(transformation));
            if (rotation == null) return null;
            var rotated = toWorldEdit.invoke(null, current.rotate(rotation));
            return fullBlock ? withNbt.invoke(rotated, nbt.invoke(holder)) : rotated;
        }

        private static OptionalApi load() {
            try {
                return new OptionalApi();
            } catch (ReflectiveOperationException | LinkageError failure) {
                report(failure);
                return null;
            }
        }

        private static Class<?> type(String name) throws ClassNotFoundException {
            return Class.forName("com.sk89q.worldedit." + name);
        }
    }

    private NativeWorldEditRotation() {}
}
