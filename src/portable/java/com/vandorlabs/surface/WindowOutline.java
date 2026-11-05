package com.vandorlabs.surface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pixel-space hexagon shared by a connected group of porthole blocks. */
public class WindowOutline {
    private static final double EPS = 1.0E-7;
    public static final int HEXAGON = 0, OCTAGON = 1, SQUARE = 2, ROUND = 3;
    public static boolean validShape(int shape) { return shape >= HEXAGON && shape <= ROUND; }
    public final double[][] vertices;

    public WindowOutline(double[][] vertices) { this.vertices = vertices; }

    public WindowOutline(int columns, int rows) { this(columns, rows, HEXAGON); }

    public WindowOutline(int columns, int rows, int shape) {
        this(columns, rows, shape, 0);
    }

    public WindowOutline(int columns, int rows, int shape, int extraBorder) {
        if (columns < 1 || rows < 1) throw new IllegalArgumentException("empty porthole group");
        if (!validShape(shape)) throw new IllegalArgumentException("unknown porthole shape");
        if (extraBorder < 0 || extraBorder > 1)
            throw new IllegalArgumentException("invalid porthole border");
        double width = columns * 16D;
        double height = rows * 16D;
        double x0 = 2 + extraBorder, x1 = width - x0;
        double y0 = 4 + extraBorder;
        double y1 = height - y0;
        if (shape == SQUARE) {
            vertices = new double[][] {{x0,y0},{x1,y0},{x1,y1},{x0,y1}};
        } else if (shape == OCTAGON) {
            double bevel = Math.min((x1-x0)/4D,(y1-y0)/4D);
            vertices = new double[][] {
                    {x0+bevel,y0},{x1-bevel,y0},{x1,y0+bevel},{x1,y1-bevel},
                    {x1-bevel,y1},{x0+bevel,y1},{x0,y1-bevel},{x0,y0+bevel}
            };
        } else if (shape == ROUND) {
            vertices = pixelEllipse((int)x0, (int)y0, (int)x1, (int)y1);
        } else if (columns == 1 && rows == 1) {
            double shoulder = (x1 - x0) / 4D;
            vertices = new double[][] {
                    {x0 + shoulder, y0}, {x1 - shoulder, y0}, {x1, 8},
                    {x1 - shoulder, y1}, {x0 + shoulder, y1}, {x0, 8}
            };
        } else if (width >= height) {
            double bevel = Math.min((x1 - x0) * .27D, (y1 - y0) * .48D);
            vertices = new double[][] {
                    {x0 + bevel, y0}, {x1 - bevel, y0}, {x1, height / 2D},
                    {x1 - bevel, y1}, {x0 + bevel, y1}, {x0, height / 2D}
            };
        } else {
            double bevel = Math.min((y1 - y0) * .27D, (x1 - x0) * .48D);
            vertices = new double[][] {
                    {width / 2D, y0}, {x1, y0 + bevel}, {x1, y1 - bevel},
                    {width / 2D, y1}, {x0, y1 - bevel}, {x0, y0 + bevel}
            };
        }
    }

    /** Rasterize at one world pixel per cell, regardless of assembly size. */
    private static double[][] pixelEllipse(int x0, int y0, int x1, int y1) {
        int rows = y1 - y0;
        int[] left = new int[rows], right = new int[rows];
        double cx = (x0 + x1) / 2D, cy = (y0 + y1) / 2D;
        double rx = (x1 - x0) / 2D, ry = rows / 2D;
        for (int row = 0; row < rows; row++) {
            double dy = (y0 + row + .5D - cy) / ry;
            double reach = rx * Math.sqrt(Math.max(0, 1 - dy * dy));
            left[row] = Math.max(x0, (int)Math.ceil(cx - reach - .5D));
            right[row] = Math.min(x1, (int)Math.floor(cx + reach - .5D) + 1);
        }
        List<double[]> outline = new ArrayList<>();
        outline.add(new double[]{left[0], y0});
        outline.add(new double[]{right[0], y0});
        for (int row = 0; row < rows; row++) {
            outline.add(new double[]{right[row], y0 + row + 1});
            if (row + 1 < rows && right[row + 1] != right[row])
                outline.add(new double[]{right[row + 1], y0 + row + 1});
        }
        outline.add(new double[]{left[rows - 1], y1});
        for (int row = rows - 1; row >= 0; row--) {
            outline.add(new double[]{left[row], y0 + row});
            if (row > 0 && left[row - 1] != left[row])
                outline.add(new double[]{left[row - 1], y0 + row});
        }
        outline.remove(outline.size() - 1); // Closing vertex is implicit.
        return outline.toArray(new double[outline.size()][]);
    }

    /** Caller-owned local geometry without constructing an assembly-sized ROUND graph.
     * Clipping retains the original global vertex order and floating-point operations. */
    public static Slice localSlice(int columns, int rows, int shape, int extraBorder,
            int column, int row) {
        if (columns < 1 || rows < 1 || columns > Integer.MAX_VALUE / 16
                || rows > Integer.MAX_VALUE / 16 || column < 0 || column >= columns
                || row < 0 || row >= rows || !validShape(shape)
                || extraBorder < 0 || extraBorder > 1)
            throw new IllegalArgumentException("invalid porthole slice");
        if (shape != ROUND) return new WindowOutline(columns, rows, shape, extraBorder).slice(column, row);
        EllipseStream ellipse = new EllipseStream(columns, rows, extraBorder, column, row);
        double[] lastX = new double[3], lastY = new double[3];
        List<double[]> polygon = new ArrayList<double[]>();
        // Each clip stage starts at the last output of its preceding stage, exactly
        // as the list implementation. Earlier passes find those three scalars.
        for (int stages = 1; stages <= 4; stages++) {
            ClipStream clip = new ClipStream(stages, ellipse, lastX, lastY,
                    stages == 4 ? polygon : null);
            ellipse.emit(clip);
            if (!clip.emitted) { polygon.clear(); break; }
            if (stages < 4) { lastX[stages - 1] = clip.lastX; lastY[stages - 1] = clip.lastY; }
        }
        if (polygon.size() < 3 || Math.abs(area(polygon)) < EPS)
            polygon = Collections.emptyList();
        EdgeStream edges = new EdgeStream();
        ellipse.emit(edges);
        edges.segment(edges.previousX, edges.previousY, edges.firstX, edges.firstY);
        return new Slice(polygon, bandQuads(polygon, false), edges.edges, bandQuads(polygon, true));
    }

    private interface PointSink { void point(double x, double y); }

    /** Replays pixelEllipse's exact sequence using scalar row spans, not row arrays. */
    private static final class EllipseStream {
        final int x0, x1, y0, y1, rows;
        final double cx, cy, rx, ry, offsetX, offsetY;
        EllipseStream(int columns, int rows, int border, int column, int row) {
            x0 = 2 + border; x1 = columns * 16 - x0;
            y0 = 4 + border; y1 = rows * 16 - y0; this.rows = y1 - y0;
            cx = (x0 + x1) / 2D; cy = (y0 + y1) / 2D;
            rx = (x1 - x0) / 2D; ry = this.rows / 2D;
            offsetX = column * 16D; offsetY = row * 16D;
        }
        double reach(int row) {
            double dy = (y0 + row + .5D - cy) / ry;
            return rx * Math.sqrt(Math.max(0, 1 - dy * dy));
        }
        int left(int row) { return Math.max(x0, (int)Math.ceil(cx - reach(row) - .5D)); }
        int right(int row) { return Math.min(x1, (int)Math.floor(cx + reach(row) - .5D) + 1); }
        double lastX() { return left(0) - offsetX; }
        double lastY() { return y0 + 1 - offsetY; }
        void point(PointSink sink, int x, int y) { sink.point(x - offsetX, y - offsetY); }
        void emit(PointSink sink) {
            point(sink, left(0), y0); point(sink, right(0), y0);
            int right = right(0);
            for (int row = 0; row < rows; row++) {
                point(sink, right, y0 + row + 1);
                if (row + 1 < rows) {
                    int next = right(row + 1);
                    if (next != right) point(sink, next, y0 + row + 1);
                    right = next;
                }
            }
            int left = left(rows - 1);
            point(sink, left, y1);
            for (int row = rows - 1; row >= 0; row--) {
                // The final duplicate closing vertex is removed by pixelEllipse.
                if (row != 0) point(sink, left, y0 + row);
                if (row > 0) {
                    int next = left(row - 1);
                    if (next != left) point(sink, next, y0 + row);
                    left = next;
                }
            }
        }
    }

    private static final class ClipStream implements PointSink {
        final int stages;
        final double[] previousX = new double[4], previousY = new double[4];
        final boolean[] previousInside = new boolean[4];
        final List<double[]> output;
        boolean emitted;
        double lastX, lastY;
        ClipStream(int stages, EllipseStream ellipse, double[] lastX, double[] lastY,
                List<double[]> output) {
            this.stages = stages; this.output = output;
            for (int stage = 0; stage < stages; stage++) {
                previousX[stage] = stage == 0 ? ellipse.lastX() : lastX[stage - 1];
                previousY[stage] = stage == 0 ? ellipse.lastY() : lastY[stage - 1];
                previousInside[stage] = inside(value(stage, previousX[stage], previousY[stage]),
                        boundary(stage), (stage & 1) == 0);
            }
        }
        private static double value(int stage, double x, double y) { return stage < 2 ? x : y; }
        private static double boundary(int stage) { return (stage & 1) == 0 ? 0 : 16; }
        public void point(double x, double y) { point(0, x, y); }
        private void point(int stage, double x, double y) {
            if (stage == stages) {
                emitted = true; lastX = x; lastY = y;
                if (output != null) output.add(new double[] {x, y});
                return;
            }
            double current = value(stage, x, y);
            boolean currentInside = inside(current, boundary(stage), (stage & 1) == 0);
            if (currentInside != previousInside[stage]) {
                double fraction = (boundary(stage) - value(stage, previousX[stage], previousY[stage]))
                        / (current - value(stage, previousX[stage], previousY[stage]));
                point(stage + 1,
                        previousX[stage] + fraction * (x - previousX[stage]),
                        previousY[stage] + fraction * (y - previousY[stage]));
            }
            if (currentInside) point(stage + 1, x, y);
            previousX[stage] = x; previousY[stage] = y;
            previousInside[stage] = currentInside;
        }
    }

    private static final class EdgeStream implements PointSink {
        final List<double[]> edges = new ArrayList<double[]>();
        final double[] t = new double[2];
        double previousX, previousY, firstX, firstY;
        boolean first = true;
        public void point(double x, double y) {
            if (first) { first = false; firstX = x; firstY = y; }
            else segment(previousX, previousY, x, y);
            previousX = x; previousY = y;
        }
        void segment(double x0, double y0, double x1, double y1) {
            double dx = x1 - x0, dy = y1 - y0;
            t[0] = 0; t[1] = 1;
            if (!clipTest(-dx, x0, t) || !clipTest(dx, 16 - x0, t)
                    || !clipTest(-dy, y0, t) || !clipTest(dy, 16 - y0, t)
                    || t[1] - t[0] < EPS) return;
            edges.add(new double[] {x0 + t[0] * dx, y0 + t[0] * dy,
                    x0 + t[1] * dx, y0 + t[1] * dy});
        }
    }

    public Slice slice(int column, int row) {
        double[][] local = new double[vertices.length][2];
        for (int i = 0; i < vertices.length; i++) {
            local[i][0] = vertices[i][0] - column * 16D;
            local[i][1] = vertices[i][1] - row * 16D;
        }
        List<double[]> polygon = new ArrayList<>();
        Collections.addAll(polygon, local);
        polygon = clipPolygon(polygon, 0, 0, true);
        polygon = clipPolygon(polygon, 0, 16, false);
        polygon = clipPolygon(polygon, 1, 0, true);
        polygon = clipPolygon(polygon, 1, 16, false);
        if (polygon.size() < 3 || Math.abs(area(polygon)) < EPS)
            polygon = Collections.emptyList();

        List<double[]> frame = bandQuads(polygon, false);
        List<double[]> edges = new ArrayList<>();
        for (int i = 0; i < local.length; i++) {
            double[] a = local[i], b = local[(i + 1) % local.length];
            double[] segment = clipSegment(a[0], a[1], b[0], b[1]);
            if (segment != null) edges.add(segment);
        }
        return new Slice(polygon, frame, edges, bandQuads(polygon, true));
    }

    private static List<double[]> clipPolygon(List<double[]> input, int axis,
            double boundary, boolean keepGreater) {
        List<double[]> output = new ArrayList<>();
        if (input.isEmpty()) return output;
        double[] previous = input.get(input.size() - 1);
        boolean previousInside = inside(previous[axis], boundary, keepGreater);
        for (double[] current : input) {
            boolean currentInside = inside(current[axis], boundary, keepGreater);
            if (currentInside != previousInside) {
                double fraction = (boundary - previous[axis])
                        / (current[axis] - previous[axis]);
                output.add(new double[] {
                        previous[0] + fraction * (current[0] - previous[0]),
                        previous[1] + fraction * (current[1] - previous[1])
                });
            }
            if (currentInside) output.add(current);
            previous = current;
            previousInside = currentInside;
        }
        return output;
    }

    private static boolean inside(double value, double boundary, boolean greater) {
        return greater ? value >= boundary - EPS : value <= boundary + EPS;
    }

    private static double area(List<double[]> polygon) {
        double area = 0;
        for (int i = 0; i < polygon.size(); i++) {
            double[] a = polygon.get(i), b = polygon.get((i + 1) % polygon.size());
            area += a[0] * b[1] - b[0] * a[1];
        }
        return area / 2D;
    }

    /** Both surfaces use the same row spans, including the square step corners. */
    private static List<double[]> bandQuads(List<double[]> polygon, boolean glass) {
        List<double[]> result = new ArrayList<>();
        List<Double> breaks = new ArrayList<>();
        breaks.add(0D);
        breaks.add(16D);
        for (double[] point : polygon) breaks.add(point[1]);
        Collections.sort(breaks);
        for (int i = 0; i < breaks.size() - 1; i++) {
            double low = breaks.get(i), high = breaks.get(i + 1);
            if (high - low < EPS) continue;
            double[] span = bandSpan(polygon, low, high);
            if (span == null) {
                if (!glass) result.add(new double[] {0, low, 16, low, 16, high, 0, high});
                continue;
            }
            double leftBottom = clamp(span[0]), rightBottom = clamp(span[1]);
            double leftTop = clamp(span[2]), rightTop = clamp(span[3]);
            if (glass) result.add(new double[] {leftBottom, low, rightBottom, low,
                    rightTop, high, leftTop, high});
            else {
                if (leftBottom + leftTop > EPS)
                    result.add(new double[] {0, low, leftBottom, low, leftTop, high, 0, high});
                if (rightBottom + rightTop < 32 - EPS)
                    result.add(new double[] {rightBottom, low, 16, low, 16, high, rightTop, high});
            }
        }
        return result;
    }

    private static double[] bandSpan(List<double[]> polygon, double low, double high) {
        double middle = (low + high) / 2D;
        double[] span = {Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (int i = 0; i < polygon.size(); i++) {
            double[] a = polygon.get(i), b = polygon.get((i + 1) % polygon.size());
            if (Math.abs(a[1] - b[1]) < EPS
                    || middle <= Math.min(a[1], b[1])
                    || middle >= Math.max(a[1], b[1])) continue;
            double bottom = a[0] + (low - a[1]) * (b[0] - a[0]) / (b[1] - a[1]);
            double top = a[0] + (high - a[1]) * (b[0] - a[0]) / (b[1] - a[1]);
            span[0] = Math.min(span[0], bottom); span[1] = Math.max(span[1], bottom);
            span[2] = Math.min(span[2], top); span[3] = Math.max(span[3], top);
        }
        return span[0] == Double.POSITIVE_INFINITY ? null : span;
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(16, value));
    }

    private static double[] clipSegment(double x0, double y0, double x1, double y1) {
        double dx = x1 - x0, dy = y1 - y0;
        double[] t = {0, 1};
        if (!clipTest(-dx, x0, t) || !clipTest(dx, 16 - x0, t)
                || !clipTest(-dy, y0, t) || !clipTest(dy, 16 - y0, t)
                || t[1] - t[0] < EPS) return null;
        return new double[] {x0 + t[0] * dx, y0 + t[0] * dy,
                x0 + t[1] * dx, y0 + t[1] * dy};
    }

    private static boolean clipTest(double p, double q, double[] t) {
        if (Math.abs(p) < EPS) return q >= -EPS;
        double ratio = q / p;
        if (p < 0) t[0] = Math.max(t[0], ratio);
        else t[1] = Math.min(t[1], ratio);
        return t[0] <= t[1] + EPS;
    }

    public static final class Slice {
        public final List<double[]> polygon;
        public final List<double[]> frameQuads;
        public final List<double[]> hexEdges;
        public final List<double[]> glassQuads;

        Slice(List<double[]> polygon, List<double[]> frameQuads,
                List<double[]> hexEdges, List<double[]> glassQuads) {
            this.polygon = polygon;
            this.frameQuads = frameQuads;
            this.hexEdges = hexEdges;
            this.glassQuads = glassQuads;
        }

        /** Interval occupied by glass where a pane crosses a block edge. */
        public double[] edgeOpening(int axis, double boundary) {
            double minimum = Double.POSITIVE_INFINITY;
            double maximum = Double.NEGATIVE_INFINITY;
            for (double[] point : polygon) {
                if (Math.abs(point[axis] - boundary) < EPS) {
                    double other = point[1 - axis];
                    minimum = Math.min(minimum, other);
                    maximum = Math.max(maximum, other);
                }
            }
            return maximum - minimum > EPS ? new double[] {minimum, maximum} : null;
        }
    }
}
