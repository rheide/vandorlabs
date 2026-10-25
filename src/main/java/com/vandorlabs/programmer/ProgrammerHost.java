package com.vandorlabs.programmer;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Common-side contract implemented by the assembled programmer controller. */
public interface ProgrammerHost {
    /** Only one TileEntity submits the complete machine. */
    boolean isProgrammerController();

    ProgrammerStatus getProgrammerStatus();

    /** NORTH/EAST/SOUTH/WEST only. */
    EnumFacing getProgrammerFacing();

    /** Minimum world XYZ of the rotated whole assembly; E/W extent is 1 x 2 x 2 XYZ. */
    BlockPos getProgrammerOrigin();

    /**
     * OR of ProgrammerCardSlot masks. Default is empty sockets.
     * Set a bit only for an occupied slot if using the supplied generic cards.
     * Keep it zero when the host draws the actual inventory items separately.
     */
    default int getProgrammerExampleCardMask() { return 0; }
}
