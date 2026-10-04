package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.property.IExtendedBlockState;
import java.util.*;

/** Extended-state compatibility against Forge's sequential property updates. */
final class HousingStateChecks {
    static void run() {
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);
        BlockPos pos=new BlockPos(8,100,8);int cases=0;
        for(Block block:new Block[]{ModBlocks.PROGRAMMABLE_BLOCK,ModBlocks.PROGRAMMABLE_SLAB,new BlockProgrammableStorage(),new BlockProgrammableStairs()}) {
            for(IBlockState state:block.getBlockState().getValidStates())for(int settings=0;settings<4;settings++) {
                world.setBlockState(pos,state,2);
                TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(pos);
                tile.setHousingTexture(settings+1);tile.setSideTexture(settings==0?-1:settings+5);
                tile.setSlabTileSides((settings&1)!=0);
                tile.setFaceTextures(new FaceTextures(settings>1,new int[]{-1,1,2,-1,4,5}));
                IExtendedBlockState actual=(IExtendedBlockState)block.getExtendedState(state,world,pos);
                IExtendedBlockState reference=((IExtendedBlockState)state)
                        .withProperty(ProgrammableHousingState.FINISH,tile.getHousingTexture())
                        .withProperty(ProgrammableHousingState.SIDE_FINISH,tile.getSideTexture())
                        .withProperty(ProgrammableHousingState.FACES,tile.getFaceTextures())
                        .withProperty(ProgrammableHousingState.TILE_SIDES,tile.isSlabTileSides()?1:0);
                if(actual.getUnlistedNames().contains(ProgrammableHousingState.VISIBLE))reference=(IExtendedBlockState)
                        ReferenceHousingState.extend(state,world,pos,block instanceof BlockProgrammableSlab);
                same(reference,actual);
                require(actual.getClean()==state,"extended state lost canonical clean state");
                for(IProperty<?> property:state.getPropertyKeys())transitions(property,reference,actual);
                same(reference.withProperty(ProgrammableHousingState.SIDE_FINISH,15),actual.withProperty(ProgrammableHousingState.SIDE_FINISH,15));
                // Construction must not modify the canonical blockstate's optionals.
                for(Optional<?> value:((IExtendedBlockState)state).getUnlistedProperties().values())require(!value.isPresent(),"mutated shared clean state");
                cases++;
            }
            world.clear();
        }
        System.out.println("PASS: "+cases+" housing snapshots match Forge properties, listed transitions, clean states and immutable defaults");
    }
    private static <T extends Comparable<T>> void transitions(IProperty<T> property,IExtendedBlockState expected,IExtendedBlockState actual) {
        for(T value:property.getAllowedValues())same((IExtendedBlockState)expected.withProperty(property,value),
                (IExtendedBlockState)actual.withProperty(property,value));
    }
    private static void same(IExtendedBlockState expected,IExtendedBlockState actual) {
        require(expected.getBlock()==actual.getBlock() && expected.getProperties().equals(actual.getProperties()),"listed properties changed");
        require(expected.getUnlistedProperties().equals(actual.getUnlistedProperties()),"unlisted properties changed");
        require(expected.getClean()==actual.getClean(),"clean state changed");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
