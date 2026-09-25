package com.vandorlabs.client;

import java.util.*;

/** Deterministic largest-first rectangle partition of occupied wall cells. */
final class PortholeRectangles {
    static long cell(int x, int y) { return ((long) x << 32) | (y & 0xffffffffL); }

    static final class Rect {
        final int x, y, width, height;
        Rect(int x, int y, int width, int height) {
            this.x = x; this.y = y; this.width = width; this.height = height;
        }
        boolean betterThan(Rect other) {
            if (other == null) return true;
            int area = width * height, otherArea = other.width * other.height;
            if (area != otherArea) return area > otherArea;
            if (width != other.width) return width > other.width;
            if (y != other.y) return y < other.y;
            return x < other.x;
        }
    }

    static List<Rect> partition(Set<Long> cells) {
        Set<Long> remaining = new HashSet<>(cells);
        List<Rect> result = new ArrayList<>();
        while (!remaining.isEmpty()) {
            Rect best = largest(remaining);
            result.add(best);
            for (int y = best.y; y < best.y + best.height; y++)
                for (int x = best.x; x < best.x + best.width; x++)
                    remaining.remove(cell(x, y));
        }
        return result;
    }

    private static Rect largest(Set<Long> cells) {
        TreeMap<Integer, TreeSet<Integer>> rows = new TreeMap<>();
        for (long cell : cells)
            rows.computeIfAbsent((int) cell, key -> new TreeSet<>()).add((int) (cell >> 32));
        Map<Integer, Integer> previous = Collections.emptyMap();
        int previousY = Integer.MIN_VALUE;
        Rect best = null;
        for (Map.Entry<Integer, TreeSet<Integer>> row : rows.entrySet()) {
            int y = row.getKey();
            Map<Integer, Integer> heights = new HashMap<>();
            // Each stack entry is the start X and height of a histogram bar.
            Deque<int[]> stack = new ArrayDeque<>();
            Integer lastX = null;
            for (int x : row.getValue()) {
                if (lastX != null && x != lastX + 1) {
                    best = flush(stack, lastX + 1, y, best);
                }
                int height = y == previousY + 1 ? previous.getOrDefault(x, 0) + 1 : 1;
                heights.put(x, height);
                int start = x;
                while (!stack.isEmpty() && stack.peekLast()[1] >= height) {
                    int[] bar = stack.removeLast();
                    Rect candidate = new Rect(bar[0], y - bar[1] + 1, x - bar[0], bar[1]);
                    if (candidate.betterThan(best)) best = candidate;
                    start = bar[0];
                }
                stack.addLast(new int[] {start, height});
                lastX = x;
            }
            best = flush(stack, lastX + 1, y, best);
            previous = heights;
            previousY = y;
        }
        return best;
    }

    private static Rect flush(Deque<int[]> stack, int endX, int y, Rect best) {
        while (!stack.isEmpty()) {
            int[] bar = stack.removeLast();
            Rect candidate = new Rect(bar[0], y - bar[1] + 1, endX - bar[0], bar[1]);
            if (candidate.betterThan(best)) best = candidate;
        }
        return best;
    }
}
