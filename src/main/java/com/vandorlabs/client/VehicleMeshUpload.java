package com.vandorlabs.client;

import java.nio.ByteBuffer;

/** Bounded opaque submissions and incremental bounds scans over complete, unmodified vertex records. */
final class VehicleMeshUpload {
    static final int BATCH_BYTES = 256 * 1024, FRAME_BYTES = 1024 * 1024;
    static final long FRAME_NANOS = 1_000_000L;

    static int nextBytes(int total, int offset, int stride, boolean translucent) {
        if (stride <= 0 || stride > Integer.MAX_VALUE / 4) throw new IllegalArgumentException("Invalid vertex stride");
        int quad = stride * 4;
        if (offset < 0 || total < offset || total % quad != 0 || offset % quad != 0)
            throw new IllegalArgumentException("Incomplete quad records");
        // A translucent layer must retain one globally sortable vertex stream.
        if (translucent) return total - offset;
        int capacity = Math.max(1, BATCH_BYTES / quad) * quad;
        return Math.min(total - offset, capacity);
    }

    static ByteBuffer slice(ByteBuffer source, int offset, int bytes) {
        ByteBuffer view = source.duplicate().order(source.order());
        view.position(offset);
        view.limit(Math.addExact(offset, bytes));
        return view.slice().order(source.order());
    }

    static final class Budget {
        private int bytes, submissions;
        private long nanos;
        Budget() { reset(); }
        void reset() { bytes = FRAME_BYTES; nanos = FRAME_NANOS; submissions = 0; }
        boolean allows(int size, boolean translucent) {
            if (size <= 0 || nanos <= 0) return false;
            if (size <= bytes) return true;
            // Never strand an existing large translucent layer or change its blend order.
            return translucent && submissions == 0 && size > FRAME_BYTES;
        }
        void submitted(int size, long elapsed) {
            bytes -= size;
            nanos -= Math.max(0, elapsed);
            submissions++;
        }
    }

    static final class Bounds {
        private int vertex;
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        boolean scan(ByteBuffer data, int count, int stride, int positionOffset, int limit) {
            int end = vertex + Math.min(Math.max(0, limit), count - vertex);
            for (; vertex < end; vertex++) {
                int at = vertex * stride + positionOffset;
                float x = data.getFloat(at), y = data.getFloat(at + 4), z = data.getFloat(at + 8);
                minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
            }
            return vertex == count;
        }
    }

    private VehicleMeshUpload() {}
}
