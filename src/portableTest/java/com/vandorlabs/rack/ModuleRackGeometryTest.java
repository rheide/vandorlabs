package com.vandorlabs.rack;

/** Frozen front-face examples, independent of any Minecraft runtime or renderer. */
public final class ModuleRackGeometryTest {
    private static int assertions;

    private ModuleRackGeometryTest() { }

    private static void require(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }

    /** Explicit cardinal mapping: no production transform is used to construct hits. */
    private static int hit(int facing, int clickedFace, double u, double v) {
        switch (facing) {
            case 0: return ModuleRackGeometry.hitSlot(facing, clickedFace, u / 16, 0, v / 16);
            case 1: return ModuleRackGeometry.hitSlot(facing, clickedFace, u / 16, 1, 1 - v / 16);
            case 2: return ModuleRackGeometry.hitSlot(facing, clickedFace, 1 - u / 16, v / 16, 0);
            case 3: return ModuleRackGeometry.hitSlot(facing, clickedFace, u / 16, v / 16, 1);
            case 4: return ModuleRackGeometry.hitSlot(facing, clickedFace, 0, v / 16, u / 16);
            case 5: return ModuleRackGeometry.hitSlot(facing, clickedFace, 1, v / 16, 1 - u / 16);
            default: throw new AssertionError("not a block face");
        }
    }

    public static void main(String[] ignored) {
        int[] opposite = {3, 2, 5, 4};
        float[] pitches = {-90, -46, -45, 0, 45, 46, 90};
        for (int facing = 2; facing <= 5; facing++) {
            int[] expected = {0, 0, opposite[facing - 2], opposite[facing - 2], opposite[facing - 2], 1, 1};
            for (int index = 0; index < pitches.length; index++)
                require(com.vandorlabs.placement.PlayerViewFacing.towardView(pitches[index], facing) == expected[index],
                        "shared player-view policy keeps shallow placements horizontal and steep views vertical");
        }
        for(int front=0;front<6;front++)for(int side=0;side<6;side++) {
            int axis=side<2?1:side<4?2:0;double[] low={.2,.2,.2},high={.8,.8,.8};low[axis]=high[axis]=(side&1)==0?0:1;
            require(ModuleRackGeometry.housingFace(front,side,low[0],low[1],low[2],high[0],high[1],high[2])==(front!=side),"five outer planes only, front always authored");
            low[axis]=high[axis]=(side&1)==0?.0625:.9375;
            require(!ModuleRackGeometry.housingFace(front,side,low[0],low[1],low[2],high[0],high[1],high[2]),"recessed and interior planes never take housing");
        }
        double[] centers = {13.25, 9.75, 6.25, 2.75};
        double[] min = {11.75, 8.25, 4.75, 1.25};
        double[] max = {14.75, 11.25, 7.75, 4.25};
        int[] closedBoundaryMasks = {62, 61, 59, 55, 47, 31};
        require(ModuleRackGeometry.SLOTS == 4 && ModuleRackGeometry.ALL_SLOTS == 15, "exactly four slots");
        require(ModuleRackGeometry.FASCIA_MIN_X == 13 && ModuleRackGeometry.FASCIA_DEPTH == 15,
                "three-pixel fascia recessed one pixel");
        require(ModuleRackGeometry.MODULE_MIN_X == 1.25 && ModuleRackGeometry.MODULE_MAX_X == 12.75,
                "quarter-pixel side clearance within twelve-pixel opening");
        for (int slot = 0; slot < 4; slot++) {
            require(ModuleRackGeometry.slotMinY(slot) == min[slot]
                    && ModuleRackGeometry.slotMaxY(slot) == max[slot]
                    && ModuleRackGeometry.slotCenterY(slot) == centers[slot], "frozen row bounds " + slot);
        }
        for (int facing = 0; facing <= 5; facing++) {
            for (int side = 0; side < 6; side++)
                require(ModuleRackGeometry.hasClosedBoundary(facing, side)
                        == ((closedBoundaryMasks[facing] & (1 << side)) != 0),
                        "all five casing boundaries, including the full-depth right wall, occlude neighbours");
            for (int slot = 0; slot < 4; slot++) {
                for (double u : new double[] {1, 1.25, 7, 12.75, 12.999})
                    require(hit(facing, facing, u, centers[slot]) == slot, "bay hit at facing " + facing + " slot " + slot);
                for (double u : new double[] {13.875, 14.5, 14.875})
                    for (double v : new double[] {centers[slot] - .9375, centers[slot], centers[slot] + .9375})
                        require(hit(facing, facing, u, v) == slot, "button hit and inclusive button edges");
                for (int clickedFace = 0; clickedFace < 6; clickedFace++)
                    if (clickedFace != facing) require(hit(facing, clickedFace, 7, centers[slot]) == -1,
                            "back/side/top/bottom cannot insert");
                for (double u : new double[] {0, .5, .999, 13, 13.25, 13.874, 14.876, 16})
                    require(hit(facing, facing, u, centers[slot]) == -1, "rim and non-button panel reject");
                require(hit(facing, facing, 14.5, centers[slot] - .938) == -1, "below button reject");
                require(hit(facing, facing, 14.5, centers[slot] + .938) == -1, "above button reject");
            }
            for (double v : new double[] {0, .5, .999, 4.25, 4.5, 4.749, 7.75, 8, 8.249, 11.25, 11.5, 11.749, 15, 15.5, 16})
                require(hit(facing, facing, 7, v) == -1, "one-pixel horizontal rim and half-pixel separators reject");
            require(hit(facing, facing, 7, 1.125) == 3, "bottom bay clearance is clickable");
            require(hit(facing, facing, 7, 14.875) == 0, "top bay clearance is clickable");
            for (double invalid : new double[] {Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, -1, 17}) {
                require(hit(facing, facing, invalid, 9.75) == -1, "invalid horizontal coordinate rejected");
                require(hit(facing, facing, 7, invalid) == -1, "invalid vertical coordinate rejected");
            }
        }
        for (int facing : new int[] {-1, 6, 7})
            require(ModuleRackGeometry.hitSlot(facing, facing, .5, .5, .5) == -1, "invalid mounting direction rejected");
        require(Double.isNaN(ModuleRackGeometry.frontX(6, .5, .5))
                && Double.isNaN(ModuleRackGeometry.frontY(-1, .5, .5)), "unknown mapping invalid");
        require(!ModuleRackGeometry.validSlot(-1) && !ModuleRackGeometry.validSlot(4)
                && ModuleRackGeometry.validSlot(0) && ModuleRackGeometry.validSlot(3), "slot index limits");
        require(!ModuleRackGeometry.hasClosedBoundary(-1, 0) && !ModuleRackGeometry.hasClosedBoundary(6, 0)
                && !ModuleRackGeometry.hasClosedBoundary(3, -1) && !ModuleRackGeometry.hasClosedBoundary(3, 6),
                "unknown boundary faces cannot occlude neighbours");
        for(int available:new int[]{284,324})for(int modules=0;modules<=4;modules++) {
            int end=0;
            for(int index=0;index<modules+2;index++) {
                int width=ModuleRackTabs.width(available,modules,index),offset=ModuleRackTabs.offset(available,modules,index);
                require(width>0&&offset==end,"compact tab gap/offset "+available+"/"+modules+"/"+index);
                require(width>=(index==0?42:index==1?48:30),"standard captions and numbered modules fit at320px "+modules+"/"+index);
                end+=width+(index==modules+1?0:ModuleRackTabs.GAP);
            }
            require(end==available,"all tabs retain exact panel bounds");
        }
        System.out.println("ModuleRackGeometry PASS assertions=" + assertions
                + " six facings, slot/button mapping, rims, separators, partial clearances, closed faces and invalid hits");
    }
}
