package com.vandorlabs.coupler;

import net.minecraft.util.math.BlockPos;

/** Reserved Forge GUI coordinates are outside Minecraft's valid world bounds. */
public final class CouplerGuiAddress {
    private CouplerGuiAddress(){}
    public static boolean captured(int z){return z>Integer.MIN_VALUE&&z<=Integer.MIN_VALUE+0x800000;}
    public static boolean validRoot(int packed){return (packed&0xffc0c0c0)==0;}
    public static boolean validCell(int packed){return (packed&0xff808080)==0;}
    public static int root(BlockPos pos){return pack(pos,63);}
    public static int cellZ(BlockPos pos){return Integer.MIN_VALUE+1+pack(pos,127);}
    private static int pack(BlockPos pos,int limit){if(pos.getX()<0||pos.getY()<0||pos.getZ()<0||pos.getX()>limit||pos.getY()>limit||pos.getZ()>limit)throw new IllegalArgumentException("Coupler GUI address exceeds saved layout bounds");return pos.getX()|(pos.getY()<<8)|(pos.getZ()<<16);}
    public static BlockPos unpack(int packed){return new BlockPos(packed&255,(packed>>>8)&255,(packed>>>16)&255);}
}
