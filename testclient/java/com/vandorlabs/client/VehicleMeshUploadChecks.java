package com.vandorlabs.client;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Complete vertex-record parity and bounded admission, independent of Minecraft and OpenGL. */
public final class VehicleMeshUploadChecks {
    private static void require(boolean yes, String message) { if (!yes) throw new AssertionError(message); }
    private static void rejects(Runnable action, String message) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError(message);
    }

    private static void records(int stride) {
        int count = 20000, total = count * stride, positionOffset = stride == 28 ? 8 : 20;
        ByteBuffer source = ByteBuffer.allocate(total).order(ByteOrder.nativeOrder());
        for (int word = 0; word < total / 4; word++) source.putInt(word * 4, word * 12345 + 6789);
        for (int vertex = 0; vertex < count; vertex++) {
            int at = vertex * stride + positionOffset;
            source.putFloat(at, vertex % 101 - 50);
            source.putFloat(at + 4, vertex % 53 - 26);
            source.putFloat(at + 8, vertex % 31 - 15);
        }
        byte[] original = new byte[total];
        for (int i = 0; i < total; i++) original[i] = source.get(i);
        int offset = 0, batches = 0;
        while (offset < total) {
            int bytes = VehicleMeshUpload.nextBytes(total, offset, stride, false);
            require(bytes > 0 && bytes <= VehicleMeshUpload.BATCH_BYTES && bytes % (stride * 4) == 0,
                    "opaque slice must contain complete quads within the byte limit");
            ByteBuffer slice = VehicleMeshUpload.slice(source, offset, bytes);
            require(slice.position() == 0 && slice.remaining() == bytes && slice.order() == source.order(), "slice bounds/order");
            for (int i = 0; i < bytes; i++) if (slice.get(i) != original[offset + i])
                throw new AssertionError("vertex or extended payload changed at byte " + (offset + i));
            offset += bytes; batches++;
        }
        require(batches == (stride == 28 ? 3 : 5), "unexpected 20,000-vertex batch count");
        require(offset == total && source.position() == 0 && source.limit() == total, "slice changed source bounds");
        require(VehicleMeshUpload.nextBytes(total, total, stride, false) == 0, "completed stream produced more bytes");
        require(VehicleMeshUpload.nextBytes(total, 0, stride, true) == total, "translucent stream was split");
        source.position(12);
        VehicleMeshUpload.slice(source, stride * 4, stride * 4);
        require(source.position() == 12 && source.limit() == total, "slice moved an existing source cursor");
        source.position(0);
        for (int i = 0; i < total; i++) if (source.get(i) != original[i])
            throw new AssertionError("source payload was modified at byte " + i);

        VehicleMeshUpload.Bounds bounds = new VehicleMeshUpload.Bounds();
        require(!bounds.scan(source, count, stride, positionOffset, 0), "zero-work scan consumed vertices");
        int scans = 1;
        while (!bounds.scan(source, count, stride, positionOffset, 31)) scans++;
        require(scans == (count + 30) / 31, "bounds scan did not honor its per-call limit");
        require(bounds.minX == -50 && bounds.maxX == 50 && bounds.minY == -26 && bounds.maxY == 26
                && bounds.minZ == -15 && bounds.maxZ == 15, "offset-aware incremental bounds differ");
        require(bounds.scan(source, count, stride, positionOffset, 31), "completed bounds scan regressed");
        require(new VehicleMeshUpload.Bounds().scan(source, 0, stride, positionOffset, 31), "empty bounds scan did not finish");
        System.out.println("vehicle-mesh-upload records PASS stride=" + stride + " vertices=" + count
                + " bytes=" + total + " batches=" + batches + " position-offset=" + positionOffset
                + " bounds-scans=" + scans + "; exact payload, source cursor/order and translucent stream preserved");
    }

    private static void budget() {
        VehicleMeshUpload.Budget budget = new VehicleMeshUpload.Budget();
        require(!budget.allows(0, false) && !budget.allows(-1, true), "nonpositive upload admitted");
        for (int i = 0; i < 4; i++) {
            require(budget.allows(VehicleMeshUpload.BATCH_BYTES, false), "fitting opaque submission rejected");
            budget.submitted(VehicleMeshUpload.BATCH_BYTES, 1);
        }
        require(!budget.allows(112, false), "frame byte limit exceeded");
        require(!budget.allows(VehicleMeshUpload.FRAME_BYTES + 112, true), "oversized translucent upload bypassed prior work");
        budget.reset();
        require(budget.allows(112, false), "frame reset did not replenish");
        budget.submitted(112, VehicleMeshUpload.FRAME_NANOS - 1);
        require(budget.allows(112, false), "positive time remainder rejected");
        budget.submitted(112, 1);
        require(!budget.allows(112, false) && !budget.allows(112, true), "frame time limit ignored");
        budget.reset();
        int large = VehicleMeshUpload.FRAME_BYTES + 112;
        require(!budget.allows(large, false), "oversized opaque upload admitted");
        require(budget.allows(large, true), "first oversized translucent stream would starve");
        budget.submitted(large, 1);
        require(!budget.allows(112, false) && !budget.allows(large, true), "large translucent upload did not consume the frame");
        budget.reset();
        budget.submitted(112, 1);
        require(!budget.allows(large, true), "oversized translucent stream was not first-only");
        budget.reset();
        require(budget.allows(VehicleMeshUpload.FRAME_BYTES, true), "reset retained old byte/time/submission state");
        System.out.println("vehicle-mesh-upload budget PASS byte/time limits, reset and first-only oversized translucent exception");
    }

    private static void invalidInput() {
        rejects(() -> VehicleMeshUpload.nextBytes(112, 0, 0, false), "zero stride accepted");
        rejects(() -> VehicleMeshUpload.nextBytes(112, 0, -28, false), "negative stride accepted");
        rejects(() -> VehicleMeshUpload.nextBytes(112, 0, Integer.MAX_VALUE, false), "quad-stride overflow accepted");
        rejects(() -> VehicleMeshUpload.nextBytes(-112, 0, 28, false), "negative total accepted");
        rejects(() -> VehicleMeshUpload.nextBytes(113, 0, 28, false), "partial final quad accepted");
        rejects(() -> VehicleMeshUpload.nextBytes(112, 1, 28, false), "unaligned offset accepted");
        rejects(() -> VehicleMeshUpload.nextBytes(112, -112, 28, false), "negative offset accepted");
        rejects(() -> VehicleMeshUpload.nextBytes(112, 224, 28, false), "offset beyond stream accepted");
        rejects(() -> VehicleMeshUpload.nextBytes(113, 0, 28, true), "translucent input bypassed quad validation");
        ByteBuffer source = ByteBuffer.allocate(112);
        rejects(() -> VehicleMeshUpload.slice(source, -1, 4), "negative slice origin accepted");
        rejects(() -> VehicleMeshUpload.slice(source, 0, 113), "slice beyond capacity accepted");
        require(source.position() == 0 && source.limit() == 112, "invalid slice modified the source");
        require(VehicleMeshUpload.nextBytes(0, 0, 28, false) == 0, "empty valid stream was rejected");
    }

    public static void main(String[] args) {
        records(28); records(56); budget(); invalidInput();
        System.out.println("vehicle-mesh-upload PASS CPU byte/bounds/admission checks; actual GPU checks remain in the live fixture");
    }
    private VehicleMeshUploadChecks() { }
}
