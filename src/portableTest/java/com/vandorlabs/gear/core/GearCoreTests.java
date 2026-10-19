package com.vandorlabs.gear.core;

import com.vandorlabs.gear.core.GearMotion;
import com.vandorlabs.gear.core.GearTopology;
import com.vandorlabs.gear.core.GearMotion.Bounds;
import com.vandorlabs.gear.core.GearMotion.Direction;
import com.vandorlabs.gear.core.GearMotion.Mode;
import com.vandorlabs.gear.core.GearMotion.Pose;
import com.vandorlabs.gear.core.GearMotion.Vec;
import com.vandorlabs.gear.core.GearTopology.Assembly;
import com.vandorlabs.gear.core.GearTopology.Cell;
import com.vandorlabs.gear.core.GearTopology.Foot;
import com.vandorlabs.gear.core.GearTopology.FootKind;
import com.vandorlabs.gear.core.GearTopology.Result;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Executable Java 8 tests. No Minecraft, Forge, JUnit, or external dependencies. */
public final class GearCoreTests {
    private static int checks;

    private GearCoreTests() { }

    public static void main(String[] args) {
        topologySizesAndLimits();
        topologyShapesMasksAndIdentity();
        topologyMalformedInput();
        extensionEndpointsAndAttachments();
        cardinalRotationAndRigidity();
        levelFootRotationAndLowerHinge();
        boundsContainTransformedGeometry();
        progressAndReversal();
        for(Direction direction:Direction.values())for(int sample=0;sample<=24;sample++){
            Pose fixed=GearMotion.pose(Mode.STATIC,direction,2,sample/24D);
            Vec p=fixed.transformFootVertex(.2,-2,.8);check(Math.abs(p.x-.2)<1e-9&&Math.abs(p.y+2)<1e-9&&Math.abs(p.z-.8)<1e-9,"Static mount pose is immutable");
            Pose large=GearMotion.pose(Mode.ROTATE_LEVEL,direction,1,sample/24D,GearMotion.RETRACTED_FOOT_Y,9/128D);
            Vec bottom=large.transformFootVertex(.5,-2,.5);check(bottom.y>=-2-1e-9,"Large wheel never dips into the ground during folding");
        }
        numericSafety();
        System.out.println("PASS: " + checks + " core assertions; topology, masks, limits, "
                + "attachments, four fold directions, level-foot swivel, bounds, reversal, and numeric safety.");
    }

    private static void topologySizesAndLimits() {
        for (int count : new int[] { 1, 2, 3, 17, 64 }) {
            List<Cell> feet = rectangularPrefix(count, 8, 3);
            for (FootKind kind : FootKind.values()) {
                Result result = GearTopology.validate(3, kind, feet);
                valid(result, count + "-foot " + kind + " group");
                equal(count, result.assembly().feet().size(), "No member was lost");
                equal(3, result.assembly().columnHeight(), "Column height retained");
                check(result.assembly().kind() == kind, "Foot kind retained");
            }
        }

        invalid(GearTopology.validate(3, FootKind.PLATE, rectangularPrefix(65, 13, 3)),
                "A connected 65-foot group must fail as a whole");
        Result sixteenWide = GearTopology.validate(1, FootKind.SKI, rectangularPrefix(16, 16, 1));
        valid(sixteenWide, "16-block inclusive width is allowed");
        equal(16, sixteenWide.assembly().width(), "Inclusive width");
        invalid(GearTopology.validate(1, FootKind.SKI, rectangularPrefix(17, 17, 1)),
                "17-block width must fail even below the foot-count limit");

        List<Cell> longZ = new ArrayList<Cell>();
        for (int z = -15; z <= 0; z++) longZ.add(new Cell(0, -8, z));
        Result sixteenDeep = GearTopology.validate(8, FootKind.PLATE, longZ);
        valid(sixteenDeep, "Negative offsets and maximum column height");
        equal(16, sixteenDeep.assembly().depth(), "Inclusive negative Z depth");
        longZ.add(new Cell(0, -8, 1));
        invalid(GearTopology.validate(8, FootKind.PLATE, longZ), "17-block Z span must fail");

        for (int height = 1; height <= 8; height++) {
            valid(GearTopology.validate(height, FootKind.PLATE,
                    Collections.singletonList(new Cell(0, -height, 0))), "Column height " + height);
        }
        invalid(GearTopology.validate(0, FootKind.PLATE,
                Collections.singletonList(new Cell(0, 0, 0))), "Zero extenders");
        invalid(GearTopology.validate(9, FootKind.PLATE,
                Collections.singletonList(new Cell(0, -9, 0))), "Nine extenders");
    }

    private static void topologyShapesMasksAndIdentity() {
        Cell center = new Cell(0, -2, 0);
        List<Cell> cross = Arrays.asList(center, new Cell(0, -2, -1),
                new Cell(1, -2, 0), new Cell(0, -2, 1), new Cell(-1, -2, 0));
        Result crossResult = GearTopology.validate(2, FootKind.PLATE, cross);
        valid(crossResult, "Branched footprint");
        Assembly assembly = crossResult.assembly();
        equal(15, assembly.connectionMask(center), "Four-way joint mask");
        equal(GearTopology.SOUTH, assembly.connectionMask(new Cell(0, -2, -1)), "North tip joins south");
        equal(GearTopology.WEST, assembly.connectionMask(new Cell(1, -2, 0)), "East tip joins west");
        equal(GearTopology.NORTH, assembly.connectionMask(new Cell(0, -2, 1)), "South tip joins north");
        equal(GearTopology.EAST, assembly.connectionMask(new Cell(-1, -2, 0)), "West tip joins east");
        equal(0, assembly.connectionMask(new Cell(2, -2, 0)), "Nonmembers cannot gain connections");

        Result elbow = GearTopology.validate(1, FootKind.SKI, Arrays.asList(
                new Cell(0, -1, 0), new Cell(1, -1, 0), new Cell(1, -1, 1)));
        valid(elbow, "L-shaped footprint");
        equal(GearTopology.WEST | GearTopology.SOUTH,
                elbow.assembly().connectionMask(new Cell(1, -1, 0)), "L elbow mask");

        Result pair = GearTopology.validate(4, FootKind.PLATE, Arrays.asList(
                new Cell(0, -4, 0), new Cell(1, -4, 0)));
        valid(pair, "Even-width footprint");
        check(pair.assembly().anchorFoot().equals(new Cell(0, -4, 0)), "Mount remains at its actual block");
        equal(0, pair.assembly().minX(), "No automatic recentering of even-width assembly");
        equal(1, pair.assembly().maxX(), "Second foot retains its placement offset");

        List<Cell> mutable = new ArrayList<Cell>(cross);
        Assembly snapshot = GearTopology.validate(2, FootKind.PLATE, mutable).assembly();
        mutable.clear();
        equal(5, snapshot.feet().size(), "Caller mutation cannot change validated assembly");
        expect(UnsupportedOperationException.class, new Action() {
            public void run() { snapshot.feet().clear(); }
        }, "Returned member list is immutable");
        List<Cell> reversed = new ArrayList<Cell>(cross);
        Collections.reverse(reversed);
        Assembly same = GearTopology.validate(2, FootKind.PLATE, reversed).assembly();
        check(snapshot.feet().equals(same.feet()), "Stable member order independent of discovery order");

        Assembly full = GearTopology.validate(1, FootKind.SKI, rectangularPrefix(64, 8, 1)).assembly();
        equal(GearTopology.EAST | GearTopology.SOUTH,
                full.connectionMask(new Cell(0, -1, 0)), "64-member corner mask");
        equal(15, full.connectionMask(new Cell(3, -1, 3)), "64-member interior mask");
        equal(GearTopology.NORTH | GearTopology.WEST,
                full.connectionMask(new Cell(7, -1, 7)), "64-member opposite corner mask");
    }

    private static void topologyMalformedInput() {
        invalid(GearTopology.validate(1, FootKind.PLATE, Arrays.asList(
                new Cell(0, -1, 0), new Cell(1, -1, 1))), "Diagonal touch does not connect feet");
        invalid(GearTopology.validate(1, FootKind.PLATE, Arrays.asList(
                new Cell(0, -1, 0), new Cell(2, -1, 0))), "Disconnected group");
        invalid(GearTopology.validate(1, FootKind.PLATE, Arrays.asList(
                new Cell(0, -1, 0), new Cell(1, -2, 0))), "Nonplanar group");
        invalid(GearTopology.validate(1, FootKind.PLATE,
                Collections.singletonList(new Cell(1, -1, 0))), "Missing anchored foot");
        invalid(GearTopology.validate(1, FootKind.PLATE, Arrays.asList(
                new Cell(0, -1, 0), new Cell(0, -1, 0))), "Duplicates are rejected");
        invalid(GearTopology.validate(1, Arrays.asList(
                new Foot(new Cell(0, -1, 0), FootKind.PLATE),
                new Foot(new Cell(1, -1, 0), FootKind.SKI))), "Mixed plate and ski group");
        invalid(GearTopology.validate(1, FootKind.PLATE, Arrays.asList(
                new Cell(Integer.MIN_VALUE, -1, 0), new Cell(Integer.MAX_VALUE, -1, 0))),
                "Integer span overflow cannot bypass footprint limit");
        invalid(GearTopology.validate(1, FootKind.PLATE, Collections.<Cell>emptyList()), "Empty group");
        invalid(GearTopology.validate(1, FootKind.PLATE, null), "Missing group");
        invalid(GearTopology.validate(1, FootKind.PLATE, Collections.<Cell>singletonList(null)), "Null member");
        invalid(GearTopology.validate(1, null, Collections.singletonList(new Cell(0, -1, 0))), "Null kind");
    }

    private static void extensionEndpointsAndAttachments() {
        for (int height = 1; height <= 8; height++) {
            Pose deployed = GearMotion.pose(Mode.EXTEND, Direction.NORTH, height, 1.0);
            Pose stowed = GearMotion.pose(Mode.EXTEND, Direction.NORTH, height, 0.0);
            close(-height, deployed.footY(), "Deployed foot is under final extender");
            close(3.0 / 16.0, stowed.footY(), "Retracted foot bottom fits above base of mount block");
            close(8.0 / 16.0, stowed.rodBottomY(), "Retracted socket endpoint");
            close(9.0 / 16.0, stowed.rodTopY(), "Rod remains attached to sleeve");
            close(1.0 / 16.0, stowed.rodTopY() - stowed.rodBottomY(), "One-pixel collapsed rod");

            for (int step = 0; step <= 20; step++) {
                Pose pose = GearMotion.pose(Mode.EXTEND, Direction.WEST, height, step / 20.0);
                Vec socket = pose.transformFootVertex(0.5, -height + 5.0 / 16.0, 0.5);
                Vec rodBottom = pose.transformRodVertex(0.5, -height + 5.0 / 16.0, 0.5);
                same(socket, rodBottom, "Foot socket and rod stay attached throughout travel");
                same(new Vec(0.5, 9.0 / 16.0, 0.5),
                        pose.transformRodVertex(0.5, 9.0 / 16.0, 0.5), "Rod top stays fixed");
                check(pose.rodBottomY() < pose.rodTopY(), "Rod length stays positive");
                same(new Vec(0.25, 0.7, 0.25), pose.transformRigidVertex(0.25, 0.7, 0.25),
                        "Extension adds no rigid-part rotation");
                close(4.0, distance(pose.transformFootVertex(-1.0, -height, 0.0),
                        pose.transformFootVertex(3.0, -height, 0.0)), "Joined feet retain spacing");
            }
        }
    }

    private static void cardinalRotationAndRigidity() {
        Vec pivot = new Vec(0.5, 9.0 / 16.0, 0.5);
        close(GearMotion.ROD_TOP_Y, GearMotion.PIVOT_Y,
                "Hinge and stationary rod-top connection share one height");
        for (Direction direction : Direction.values()) {
            Pose stowed = GearMotion.pose(Mode.ROTATE, direction, 3, 0.0);
            Vec folded = stowed.transformFootVertex(0.5, -3.0, 0.5);
            close(pivot.y, folded.y, "Foot rotates to the pivot's horizontal plane");
            double radius = 3.0 + 9.0 / 16.0;
            switch (direction) {
                case NORTH: same(new Vec(0.5, pivot.y, 0.5 - radius), folded, "North folds to -Z"); break;
                case EAST: same(new Vec(0.5 + radius, pivot.y, 0.5), folded, "East folds to +X"); break;
                case SOUTH: same(new Vec(0.5, pivot.y, 0.5 + radius), folded, "South folds to +Z"); break;
                case WEST: same(new Vec(0.5 - radius, pivot.y, 0.5), folded, "West folds to -X"); break;
                default: throw new AssertionError("Unhandled direction");
            }

            for (int height = 1; height <= 8; height++) {
                Vec sample = new Vec(1.25, -height + 0.1875, -0.125);
                same(sample, GearMotion.pose(Mode.ROTATE, direction, height, 1.0)
                        .transformFootVertex(sample.x, sample.y, sample.z), "Deployed rotation is identity");
                for (int step = 0; step <= 20; step++) {
                    Pose pose = GearMotion.pose(Mode.ROTATE, direction, height, step / 20.0);
                    close(-height, pose.footY(), "Rotation retains original foot coordinates");
                    close(-height + 5.0 / 16.0, pose.rodBottomY(), "Rotation retains deployed rod length");
                    same(pivot, pose.transformRigidVertex(pivot.x, pivot.y, pivot.z), "Top hinge pivot is invariant");
                    Vec moved = pose.transformFootVertex(sample.x, sample.y, sample.z);
                    close(distance(pivot, sample), distance(pivot, moved), "Rotation preserves pivot radius");
                    Vec second = new Vec(-2.5, -height, 1.0);
                    Vec movedSecond = pose.transformFootVertex(second.x, second.y, second.z);
                    close(distance(sample, second), distance(moved, movedSecond), "Arbitrary joined-member distance is invariant");
                    same(moved, pose.transformRigidVertex(sample.x, sample.y, sample.z),
                            "Moving feet, socket, and rod share the same rotation");
                    same(pose.transformFootVertex(0.5, -height + 5.0 / 16.0, 0.5),
                            pose.transformRodVertex(0.5, -height + 5.0 / 16.0, 0.5),
                            "Rotating rod remains attached to foot socket");
                    same(pivot, pose.transformRodVertex(0.5, GearMotion.ROD_TOP_Y, 0.5),
                            "Rod upper endpoint stays attached inside the stationary housing");
                    // The actual pillar asset is six pixels wide (5..11).
                    // Its complete swept body stays below y=12/16, leaving
                    // at least two pixels below the fixed cap at y=14/16.
                    Bounds rod = new Bounds(5.0 / 16.0, -height + 5.0 / 16.0, 5.0 / 16.0,
                            11.0 / 16.0, 9.0 / 16.0, 11.0 / 16.0);
                    Bounds movingRod = pose.transformRigidBounds(rod);
                    check(movingRod.maxY <= 12.0 / 16.0 + 1e-9,
                            "Rotating pillar body cannot protrude through the fixed mounting cap");
                    check(14.0 / 16.0 - movingRod.maxY >= 2.0 / 16.0 - 1e-9,
                            "Fixed cap retains at least two pixels of pillar clearance");
                }
            }
        }
    }

    private static void boundsContainTransformedGeometry() {
        // Deliberately asymmetric: rotate a long ski and an offset multi-foot
        // footprint, including sample points within faces, not just corners.
        Bounds source = new Bounds(-7.0, -8.0, -0.25, 8.0, -7.625, 8.25);
        for (Mode mode : Mode.values()) {
            for (Direction direction : Direction.values()) {
                for (int step = 0; step <= 10; step++) {
                    Pose pose = GearMotion.pose(mode, direction, 8, step / 10.0);
                    Bounds transformed = pose.transformFootBounds(source);
                    for (int ix = 0; ix <= 4; ix++) {
                        for (int iy = 0; iy <= 2; iy++) {
                            for (int iz = 0; iz <= 4; iz++) {
                                double x = source.minX + (source.maxX - source.minX) * ix / 4.0;
                                double y = source.minY + (source.maxY - source.minY) * iy / 2.0;
                                double z = source.minZ + (source.maxZ - source.minZ) * iz / 4.0;
                                check(transformed.contains(pose.transformFootVertex(x, y, z)),
                                        "Transformed bounds contain entire moving footprint");
                            }
                        }
                    }
                }
            }
        }
    }

    private static void levelFootRotationAndLowerHinge() {
        Vec upperHinge = new Vec(0.5, 9.0 / 16.0, 0.5);
        for (Direction direction : Direction.values()) {
            for (int height = 1; height <= 8; height++) {
                Vec ankle = new Vec(0.5, -height + 5.0 / 16.0, 0.5);
                Vec padBottom = new Vec(0.5, -height, 0.5);
                Pose stowed = GearMotion.pose(Mode.ROTATE_LEVEL, direction, height, 0.0);
                Vec foldedPad = stowed.transformFootVertex(padBottom.x, padBottom.y, padBottom.z);
                double reach = height + 4.0 / 16.0;
                double foldedPadY = 4.0 / 16.0;
                switch (direction) {
                    case NORTH: same(new Vec(0.5, foldedPadY, 0.5 - reach), foldedPad,
                            "Level pad stows north without tipping"); break;
                    case EAST: same(new Vec(0.5 + reach, foldedPadY, 0.5), foldedPad,
                            "Level pad stows east without tipping"); break;
                    case SOUTH: same(new Vec(0.5, foldedPadY, 0.5 + reach), foldedPad,
                            "Level pad stows south without tipping"); break;
                    case WEST: same(new Vec(0.5 - reach, foldedPadY, 0.5), foldedPad,
                            "Level pad stows west without tipping"); break;
                    default: throw new AssertionError("Unhandled direction");
                }
                same(padBottom, GearMotion.pose(Mode.ROTATE_LEVEL, direction, height, 1.0)
                        .transformFootVertex(padBottom.x, padBottom.y, padBottom.z),
                        "Level pad deploys at its construction position");

                for (int step = 0; step <= 20; step++) {
                    double progress = step / 20.0;
                    Pose level = GearMotion.pose(Mode.ROTATE_LEVEL, direction, height, progress);
                    Pose ordinary = GearMotion.pose(Mode.ROTATE, direction, height, progress);
                    close(ordinary.angleDegrees(), level.angleDegrees(), "Level mode uses the same arm swing angle");
                    close(-height + 5.0 / 16.0, level.rodBottomY(),
                            "Level arm retains its deployed local endpoint");
                    Vec armAnkle = level.transformRigidVertex(ankle.x, ankle.y, ankle.z);
                    Vec socket = level.transformFootVertex(ankle.x, ankle.y, ankle.z);
                    same(armAnkle, socket, "Counter-rotating lower socket remains attached to arm endpoint");
                    same(armAnkle, level.transformRodVertex(ankle.x, ankle.y, ankle.z),
                            "Rod helper follows rigid swinging arm in level mode");
                    same(armAnkle, ordinary.transformRigidVertex(ankle.x, ankle.y, ankle.z),
                            "Adding lower swivel does not change upper arm path");
                    same(upperHinge, level.transformRigidVertex(upperHinge.x, upperHinge.y, upperHinge.z),
                            "Level-mode upper hinge remains fixed");
                    close(reach, distance(upperHinge, armAnkle), "Swinging arm keeps fixed hinge-to-ankle reach");

                    Vec a = new Vec(-2.0, -height + 0.125, 3.0);
                    Vec b = new Vec(4.0, -height + 0.125, -1.0);
                    Vec movedA = level.transformFootVertex(a.x, a.y, a.z);
                    Vec movedB = level.transformFootVertex(b.x, b.y, b.z);
                    close(distance(a, b), distance(movedA, movedB), "Entire level foot group keeps rigid spacing");
                    same(new Vec(0.0, 1.0, 0.0), difference(
                            level.transformFootVertex(a.x, a.y + 1.0, a.z), movedA),
                            "Foot normal stays world-up throughout swing");
                    same(new Vec(1.0, 0.0, 0.0), difference(
                            level.transformFootVertex(a.x + 1.0, a.y, a.z), movedA),
                            "Level foot east-west tangent never rotates");
                    same(new Vec(0.0, 0.0, 1.0), difference(
                            level.transformFootVertex(a.x, a.y, a.z + 1.0), movedA),
                            "Level foot north-south tangent never rotates");

                    Vec sharedShift = difference(socket, ankle);
                    same(sharedShift, difference(movedA, a), "Remote member uses shared ankle translation");
                    same(sharedShift, difference(movedB, b), "Opposite member has no independent pivot");
                    close(movedA.y, movedB.y, "Separated pad surfaces stay coplanar");
                }
            }
        }
    }

    private static void progressAndReversal() {
        close(0.0, GearMotion.smoothstep(0.0), "Stowed easing endpoint");
        close(0.5, GearMotion.smoothstep(0.5), "Easing midpoint");
        close(1.0, GearMotion.smoothstep(1.0), "Deployed easing endpoint");
        check(GearMotion.smoothstep(1e-5) < 1e-8, "Motion eases smoothly near stowed endpoint");
        check(1.0 - GearMotion.smoothstep(1.0 - 1e-5) < 1e-8,
                "Motion eases smoothly near deployed endpoint");

        double progress = GearMotion.advance(0.0, true, 7.0, 20.0);
        close(0.35, progress, "Partial forward motion");
        double immediateReverse = GearMotion.advance(progress, false, 0.0, 20.0);
        close(progress, immediateReverse, "Changing target without elapsed time never resets progress");
        for (Mode mode : Mode.values()) {
            for (Direction direction : Direction.values()) {
                Vec before = GearMotion.pose(mode, direction, 5, progress).transformFootVertex(3.0, -5.0, 2.0);
                Vec after = GearMotion.pose(mode, direction, 5, immediateReverse).transformFootVertex(3.0, -5.0, 2.0);
                same(before, after, "Midanimation reversal has no pose jump");
                Vec armBefore = GearMotion.pose(mode, direction, 5, progress)
                        .transformRigidVertex(0.5, -5.0 + 5.0 / 16.0, 0.5);
                Vec armAfter = GearMotion.pose(mode, direction, 5, immediateReverse)
                        .transformRigidVertex(0.5, -5.0 + 5.0 / 16.0, 0.5);
                same(armBefore, armAfter, "Reversal also preserves the rigid arm's lower hinge pose");
            }
        }
        progress = GearMotion.advance(progress, false, 1.0, 20.0);
        close(0.30, progress, "Reverse continues from existing progress");
        close(0.0, GearMotion.advance(progress, false, 100.0, 20.0), "Stow overshoot clamps");
        close(1.0, GearMotion.advance(progress, true, 100.0, 20.0), "Deploy overshoot clamps");
        close(0.325, GearMotion.interpolate(0.35, 0.30, 0.5), "Tick interpolation follows reversal");
        close(0.35, GearMotion.interpolate(0.35, 0.30, 0.0), "Previous tick endpoint");
        close(0.30, GearMotion.interpolate(0.35, 0.30, 1.0), "Current tick endpoint");
    }

    private static void numericSafety() {
        close(0.0, GearMotion.pose(Mode.EXTEND, Direction.NORTH, 1, -100.0).deployProgress(),
                "Finite progress below zero clamps");
        close(1.0, GearMotion.pose(Mode.ROTATE, Direction.SOUTH, 8, 100.0).deployProgress(),
                "Finite progress above one clamps");
        close(1.0, GearMotion.advance(0.0, true, Double.MAX_VALUE, Double.MIN_VALUE),
                "Finite time inputs with overflowing ratio saturate safely");
        for (final double bad : new double[] { Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY }) {
            expect(IllegalArgumentException.class, new Action() {
                public void run() { GearMotion.pose(Mode.EXTEND, Direction.NORTH, 1, bad); }
            }, "Nonfinite pose progress rejected");
            expect(IllegalArgumentException.class, new Action() {
                public void run() { new Vec(bad, 0.0, 0.0); }
            }, "Nonfinite vertices rejected");
        }
        expect(IllegalArgumentException.class, new Action() {
            public void run() { GearMotion.pose(Mode.EXTEND, Direction.NORTH, 9, 0.0); }
        }, "Out-of-range motion height rejected");
        expect(IllegalArgumentException.class, new Action() {
            public void run() { GearMotion.pose(null, Direction.NORTH, 1, 0.0); }
        }, "Missing motion mode rejected");
        expect(IllegalArgumentException.class, new Action() {
            public void run() { GearMotion.advance(0.5, true, -1.0, 20.0); }
        }, "Negative elapsed time rejected");
        expect(IllegalArgumentException.class, new Action() {
            public void run() { GearMotion.advance(0.5, true, 1.0, 0.0); }
        }, "Zero duration rejected");
        expect(IllegalArgumentException.class, new Action() {
            public void run() { new Bounds(1.0, 0.0, 0.0, -1.0, 1.0, 1.0); }
        }, "Reversed bounds rejected");
    }

    private static List<Cell> rectangularPrefix(int count, int width, int height) {
        List<Cell> result = new ArrayList<Cell>();
        for (int index = 0; index < count; index++) {
            result.add(new Cell(index % width, -height, index / width));
        }
        return result;
    }

    private static void valid(Result result, String message) {
        check(result.isValid(), message + ": " + result.error());
        check(result.assembly() != null, "Valid result contains assembly");
        check(result.error().isEmpty(), "Valid result has no error");
    }

    private static void invalid(Result result, String message) {
        check(!result.isValid(), message);
        check(result.assembly() == null, "Invalid result exposes no partial assembly");
        check(!result.error().isEmpty(), "Invalid result explains its cause");
    }

    private static void same(Vec expected, Vec actual, String message) {
        close(expected.x, actual.x, message + " (x)");
        close(expected.y, actual.y, message + " (y)");
        close(expected.z, actual.z, message + " (z)");
    }

    private static double distance(Vec a, Vec b) {
        double x = a.x - b.x;
        double y = a.y - b.y;
        double z = a.z - b.z;
        return Math.sqrt(x * x + y * y + z * z);
    }

    private static Vec difference(Vec a, Vec b) {
        return new Vec(a.x - b.x, a.y - b.y, a.z - b.z);
    }

    private static void close(double expected, double actual, String message) {
        check(!Double.isNaN(actual) && Math.abs(expected - actual) < 1e-9,
                message + "; expected " + expected + ", got " + actual);
    }

    private static void equal(int expected, int actual, String message) {
        check(expected == actual, message + "; expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private interface Action { void run(); }

    private static void expect(Class<? extends Throwable> type, Action action, String message) {
        checks++;
        try {
            action.run();
        } catch (Throwable throwable) {
            if (type.isInstance(throwable)) return;
            throw new AssertionError(message + "; wrong exception: " + throwable, throwable);
        }
        throw new AssertionError(message + "; expected " + type.getSimpleName());
    }
}
