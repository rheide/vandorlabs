package com.vandorlabs.blocks;

/** Top-facing propulsion fixture with joined square models up to 4x4. */
public class BlockConnectedHoverPropulsionLight extends BlockConnectedPropulsionLight {
    private static final PropertyConnectedPart HOVER_PART =
            PropertyConnectedPart.create("part", 4);

    public BlockConnectedHoverPropulsionLight(String name, float depth) {
        super(name, true, depth, 4);
    }

    @Override public PropertyConnectedPart partProperty() { return HOVER_PART; }
}
