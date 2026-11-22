package com.vandorlabs.neoforge.vehicle;

import com.vandorlabs.persistence.JournalFiles;
import com.vandorlabs.persistence.JournalPublication;
import com.vandorlabs.persistence.JournalWorker;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

/** A parked vehicle's identity stays retired even if an older entity chunk is encountered later. */
final class NativeVehicleRetirements {
    private static final int SCHEMA = 1;
    private static final int SIZE = Integer.BYTES + 6 * Long.BYTES;
    private static final Map<NativeGroundVehicle, Admission> LOADED = new WeakHashMap<>();

    enum State { PENDING, ACTIVE, RETIRED, FAILED }

    private static final class Admission {
        State state = State.PENDING;
        Future<Boolean> read;
    }

    static State admission(ServerLevel world, NativeGroundVehicle craft) {
        var decision = LOADED.computeIfAbsent(craft, unused -> new Admission());
        if (decision.state != State.PENDING) return decision.state;
        var identity = craft.getUUID();
        var owner = craft.owner();
        if (owner == null) {
            decision.state = State.FAILED;
            return decision.state;
        }
        if (decision.read == null) {
            var path = path(world, identity);
            try {
                decision.read = JournalWorker.submit(() -> {
                    var record = read(path);
                    if (record == null) return false;
                    if (record.getInt() != SCHEMA || !identity.equals(uuid(record)) || !owner.equals(uuid(record))) {
                        throw new IOException("Invalid vehicle retirement record");
                    }
                    return true;
                });
            } catch (RejectedExecutionException busy) {
                return State.PENDING;
            }
        } else if (decision.read.isDone()) {
            try {
                decision.state = decision.read.get() ? State.RETIRED : State.ACTIVE;
            } catch (ExecutionException unavailable) {
                decision.state = State.FAILED;
                System.err.println("[vandorlabs] Vehicle retirement check retained " + identity + ": " + unavailable.getCause());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        return decision.state;
    }

    static void active(NativeGroundVehicle actor) {
        var decision = new Admission();
        decision.state = State.ACTIVE;
        LOADED.put(actor, decision);
    }

    /** Compare the complete record before deleting a rollback's exact retirement. */
    static Future<Void> checkpoint(ServerLevel world, UUID identity, UUID owner, UUID operation, boolean rollback) {
        var path = path(world, identity);
        byte[] expected = ByteBuffer.allocate(SIZE).putInt(SCHEMA).putLong(identity.getMostSignificantBits())
            .putLong(identity.getLeastSignificantBits()).putLong(owner.getMostSignificantBits())
            .putLong(owner.getLeastSignificantBits()).putLong(operation.getMostSignificantBits())
            .putLong(operation.getLeastSignificantBits()).array();
        return JournalWorker.submit(() -> {
            var current = read(path);
            if (current != null && !java.util.Arrays.equals(expected, current.array())) {
                throw new IOException("Another vehicle retirement owns " + identity);
            }
            if (rollback) {
                if (current != null) JournalPublication.delete(path);
                else {
                    try { JournalFiles.syncDirectory(path.getParent()); }
                    catch (NoSuchFileException absent) { /* The retirement directory was never created. */ }
                }
            } else if (current == null) {
                JournalPublication.write(path, expected, false);
            } else {
                JournalFiles.syncDirectory(path.getParent());
            }
            return null;
        });
    }

    private static ByteBuffer read(Path path) throws IOException {
        try (var input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes(SIZE + 1);
            if (bytes.length != SIZE) throw new IOException("Vehicle retirement record size");
            return ByteBuffer.wrap(bytes);
        } catch (NoSuchFileException absent) {
            return null;
        }
    }

    private static UUID uuid(ByteBuffer data) {
        return new UUID(data.getLong(), data.getLong());
    }

    private static Path path(ServerLevel world, UUID identity) {
        var name = identity.toString();
        return world.getServer().getWorldPath(LevelResource.ROOT).resolve("vandorlabs/vehicle-retirements")
            .resolve(name.substring(0, 2)).resolve(name + ".bin");
    }

    private NativeVehicleRetirements() {}
}
