package com.vandorlabs.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pixel-space hexagon shared by a connected group of porthole blocks. */
final class PortholeHex {
    private static final double EPS = 1.0E-7;
    static final int HEXAGON = 0, OCTAGON = 1, SQUARE = 2, ROUND = 3;
    static boolean validShape(int shape) { return shape >= HEXAGON && shape <= ROUND; }
    final double[][] vertices;

    PortholeHex(double[][] vertices) { this.vertices = vertices; }

    PortholeHex(int columns, int rows) { this(columns, rows, HEXAGON); }

    PortholeHex(int columns, int rows, int shape) {
        if (columns < 1 || rows < 1) throw new IllegalArgumentException("empty porthole group");
        if (!validShape(shape)) throw new IllegalArgumentException("unknown porthole shape");
        double width = columns * 16D;
        double height = rows * 16D;
        double x0 = 2, x1 = width - 2;
        double y0 = 4;
        double y1 = height - 4;
        if (shape == SQUARE) {
            vertices = new double[][] {{x0,y0},{x1,y0},{x1,y1},{x0,y1}};
        } else if (shape == OCTAGON) {
            double bevel = Math.min((x1-x0)/4D,(y1-y0)/4D);
            vertices = new double[][] {
                    {x0+bevel,y0},{x1-bevel,y0},{x1,y0+bevel},{x1,y1-bevel},
                    {x1-bevel,y1},{x0+bevel,y1},{x0,y1-bevel},{x0,y0+bevel}
            };
        } else if (shape == ROUND) {
            double dx = Math.max(1,Math.floor((x1-x0)/6D));
            double dy = Math.max(1,Math.floor((y1-y0)/6D));
            vertices = new double[][] {
                    {x0+2*dx,y0},{x1-2*dx,y0},
                    {x1-2*dx,y0+dy},{x1-dx,y0+dy},
                    {x1-dx,y0+2*dy},{x1,y0+2*dy},
                    {x1,y1-2*dy},{x1-dx,y1-2*dy},
                    {x1-dx,y1-dy},{x1-2*dx,y1-dy},
                    {x1-2*dx,y1},{x0+2*dx,y1},
                    {x0+2*dx,y1-dy},{x0+dx,y1-dy},
                    {x0+dx,y1-2*dy},{x0,y1-2*dy},
                    {x0,y0+2*dy},{x0+dx,y0+2*dy},
                    {x0+dx,y0+dy},{x0+2*dx,y0+dy}
            };
        } else if (columns == 1 && rows == 1) {
            vertices = new double[][] {
                    {5, 4}, {11, 4}, {14, 8}, {11, 12}, {5, 12}, {2, 8}
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

    Slice slice(int column, int row) {
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

        List<double[]> frame = frameQuads(polygon);
        List<double[]> edges = new ArrayList<>();
        for (int i = 0; i < local.length; i++) {
            double[] a = local[i], b = local[(i + 1) % local.length];
            double[] segment = clipSegment(a[0], a[1], b[0], b[1]);
            if (segment != null) edges.add(segment);
        }
        return new Slice(polygon, frame, edges);
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

    private static List<double[]> frameQuads(List<double[]> polygon) {
        List<double[]> result = new ArrayList<>();
        if (polygon.isEmpty()) {
            result.add(new double[] {0, 0, 16, 0, 16, 16, 0, 16});
            return result;
        }
        List<Double> breaks = new ArrayList<>();
        breaks.add(0D);
        breaks.add(16D);
        for (double[] point : polygon) breaks.add(point[1]);
        Collections.sort(breaks);
        for (int i = 0; i < breaks.size() - 1; i++) {
            double low = breaks.get(i), high = breaks.get(i + 1);
            if (high - low < EPS) continue;
            double[] middle = span(polygon, (low + high) / 2D);
            if (middle == null) {
                result.add(new double[] {0, low, 16, low, 16, high, 0, high});
                continue;
            }
            double[] bottom = span(polygon, low);
            double[] top = span(polygon, high);
            if (bottom == null || top == null) continue;
            double leftBottom = clamp(bottom[0]), leftTop = clamp(top[0]);
            double rightBottom = clamp(bottom[1]), rightTop = clamp(top[1]);
            if (leftBottom + leftTop > EPS)
                result.add(new double[] {0, low, leftBottom, low,
                        leftTop, high, 0, high});
            if (rightBottom + rightTop < 32 - EPS)
                result.add(new double[] {rightBottom, low, 16, low,
                        16, high, rightTop, high});
        }
        return result;
    }

    private static double[] span(List<double[]> polygon, double y) {
        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < polygon.size(); i++) {
            double[] a = polygon.get(i), b = polygon.get((i + 1) % polygon.size());
            if (Math.abs(a[1] - b[1]) < EPS) {
                if (Math.abs(y - a[1]) < EPS) {
                    minimum = Math.min(minimum, Math.min(a[0], b[0]));
                    maximum = Math.max(maximum, Math.max(a[0], b[0]));
                }
            } else if (y >= Math.min(a[1], b[1]) - EPS
                    && y <= Math.max(a[1], b[1]) + EPS) {
                double x = a[0] + (y - a[1]) * (b[0] - a[0]) / (b[1] - a[1]);
                minimum = Math.min(minimum, x);
                maximum = Math.max(maximum, x);
            }
        }
        return minimum == Double.POSITIVE_INFINITY ? null
                : new double[] {minimum, maximum};
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

    static final class Slice {
        final List<double[]> polygon;
        final List<double[]> frameQuads;
        final List<double[]> hexEdges;

        Slice(List<double[]> polygon, List<double[]> frameQuads,
                List<double[]> hexEdges) {
            this.polygon = polygon;
            this.frameQuads = frameQuads;
            this.hexEdges = hexEdges;
        }

        /** Interval occupied by glass where a pane crosses a block edge. */
        double[] edgeOpening(int axis, double boundary) {
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
