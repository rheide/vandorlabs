package com.vandorlabs.tiles;

import com.vandorlabs.blocks.BlockProgrammableTrapdoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldServerMulti;
import net.minecraft.world.chunk.*;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import java.lang.reflect.Field;

/** Conservative live palette rejection: unknown storage always uses the ordinary scan. */
public final class TrapdoorSectionPresence {
    private static final Field PALETTE=field("palette","field_186022_c");
    private static final Field BITS=field("bits","field_186024_e");
    private TrapdoorSectionPresence() { }

    public static boolean mayContain(World world,int x0,int y0,int z0,int x1,int y1,int z1) {
        Class<?> type=world.getClass();
        // Custom worlds may synthesize block states independently of chunk storage.
        if(type!=WorldServer.class && type!=WorldServerMulti.class
                && !type.getName().equals("net.minecraft.client.multiplayer.WorldClient"))return true;
        return mayContainLoaded(world.getChunkProvider(),x0,y0,z0,x1,y1,z1);
    }

    /** Uses loaded-chunk lookup exclusively; no terrain or tile entity is requested. */
    public static boolean mayContainLoaded(IChunkProvider provider,int x0,int y0,int z0,int x1,int y1,int z1) {
        if(y1<0 || y0>255)return false;
        int firstY=Math.max(0,y0>>4),lastY=Math.min(15,y1>>4);
        int firstX=x0>>4,lastX=x1>>4,firstZ=z0>>4,lastZ=z1>>4;
        if((long)(lastX-firstX+1)*(lastZ-firstZ+1)*(lastY-firstY+1)>64)return true;
        for(int x=firstX;x<=lastX;x++)for(int z=firstZ;z<=lastZ;z++) {
            Chunk chunk=provider.getLoadedChunk(x,z);
            if(chunk==null)continue;
            if(chunk.getClass()!=Chunk.class)return true;
            ExtendedBlockStorage[] sections=chunk.getBlockStorageArray();
            if(sections.length!=16)return true;
            for(int y=firstY;y<=lastY;y++) {
                ExtendedBlockStorage section=sections[y];
                if(section!=null && (section.getClass()!=ExtendedBlockStorage.class || mayContain(section.getData())))return true;
            }
        }
        return false;
    }

    /** Scan every possible palette slot, including slots after malformed null entries. */
    public static boolean mayContain(net.minecraft.world.chunk.BlockStateContainer data) {
        if(data==null || data.getClass()!=net.minecraft.world.chunk.BlockStateContainer.class || PALETTE==null || BITS==null)return true;
        try {
            IBlockStatePalette palette=(IBlockStatePalette)PALETTE.get(data);
            int bits=BITS.getInt(data);
            if(palette==null || bits<4 || bits>8)return true;
            if(bits==4?palette.getClass()!=BlockStatePaletteLinear.class:palette.getClass()!=BlockStatePaletteHashMap.class)return true;
            for(int i=0;i<(1<<bits);i++) {
                IBlockState state=palette.getBlockState(i);
                if(state!=null && state.getBlock() instanceof BlockProgrammableTrapdoor)return true;
            }
            return false;
        } catch(IllegalAccessException | RuntimeException unavailable) {
            return true;
        }
    }
    private static Field field(String development,String runtime) {
        for(String name:new String[]{development,runtime})try {
            Field field=net.minecraft.world.chunk.BlockStateContainer.class.getDeclaredField(name);
            field.setAccessible(true);return field;
        } catch(ReflectiveOperationException | RuntimeException unavailable) { }
        return null;
    }
}
