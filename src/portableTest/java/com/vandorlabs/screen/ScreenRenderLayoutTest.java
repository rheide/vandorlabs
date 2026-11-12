package com.vandorlabs.screen;

import com.vandorlabs.render.ScreenSurface;

/** Discovery indices must follow the actual display vertices after their owner transform. */
public final class ScreenRenderLayoutTest {
    public static void main(String[] args) {
        int cases = 0, reversedColumns = 0, reversedRows = 0;
        for (ScreenFamily family : ScreenFamily.values()) {
            if (!ScreenJoinGeometry.supports(family, false)) continue;
            for (int metadata = 0; metadata < family.stateCount(); metadata++) {
                ScreenFamily.State state = family.decode(metadata);
                for (boolean ceiling : new boolean[] {false, true}) {
                    ScreenJoinGeometry.Basis basis = ScreenJoinGeometry.basis(family, state, ceiling);
                    double[] pose = new double[16];
                    ScreenRenderPose.write(family, state, ceiling, -1, pose);
                    ScreenSurface.Quad face = null;
                    for (ScreenRenderGeometry.Surface surface : ScreenRenderGeometry.of(
                            family, state, false, -1, ceiling, -1).surfaces)
                        if (surface.frame) face = surface.quad;
                    require(face != null, "missing display surface");
                    double right = dot(pose, face.topRight, face.topLeft, basis, 0);
                    double up = dot(pose, face.topLeft, face.bottomLeft, basis, 2);
                    require(Math.abs(right) > 0 && Math.abs(up) > 0, "degenerate display axes");
                    boolean flipX = ScreenRenderLayout.mirrorColumns(family, state.facing);
                    boolean flipY = ScreenRenderLayout.mirrorRows(family, state.facing, ceiling);
                    require((right < 0) == flipX, "columns disagree with transformed vertices: " + family + "/" + metadata);
                    require((up < 0) == flipY, "rows disagree with transformed vertices: " + family + "/" + metadata + "/" + ceiling);
                    if (flipX) reversedColumns++;
                    if (flipY) reversedRows++;
                    for (int[] size : new int[][] {{2, 2}, {4, 2}, {2, 4}})
                        for (int row = 0; row < size[1]; row++)
                            for (int col = 0; col < size[0]; col++) {
                                int x = flipX ? size[0] - 1 - col : col;
                                int y = flipY ? size[1] - 1 - row : row;
                                // The lower/upper index selects the physical outer edge,
                                // independently of texture U direction and block facing.
                                require((x == 0) == (right > 0 ? col == 0 : col == size[0] - 1), "left border inside group");
                                require((y == 0) == (up > 0 ? row == 0 : row == size[1] - 1), "bottom border inside group");
                            }
                    cases++;
                }
            }
        }
        require(reversedColumns > 0 && reversedRows > 0, "vertical-mount negative controls not exercised");
        System.out.println("PASS: Screen render lattice matches transformed artwork/frame vertices in " + cases
                + " family/mount states; 2x2, 4x2, 2x4 outer borders; former reversed axes=" + reversedColumns + "/" + reversedRows);
    }

    private static double dot(double[] pose, ScreenSurface.Vertex a, ScreenSurface.Vertex b,
            ScreenJoinGeometry.Basis basis, int step) {
        double x = a.x - b.x, y = a.y - b.y, z = a.z - b.z;
        return (pose[0] * x + pose[4] * y + pose[8] * z) * basis.dx(step)
                + (pose[1] * x + pose[5] * y + pose[9] * z) * basis.dy(step)
                + (pose[2] * x + pose[6] * y + pose[10] * z) * basis.dz(step);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
