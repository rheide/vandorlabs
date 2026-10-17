package com.vandorlabs.blocks;

import com.vandorlabs.tiles.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Reserved model cells have no inventory form; break the core to recover the frame. */
public final class BlockConstructorPart extends BlockVandor {
    public BlockConstructorPart(){super("constructor_part");setCreativeTab(null);setBlockUnbreakable();setResistance(6000000);setLightOpacity(0);}
    @Override public boolean isOpaqueCube(IBlockState state){return false;}
    @Override public boolean isFullCube(IBlockState state){return false;}
    @Override public boolean hasTileEntity(IBlockState state){return true;}
    @Override public TileEntity createTileEntity(World world,IBlockState state){return new TileEntityConstructorPart();}
    @Override public net.minecraft.item.Item getItemDropped(IBlockState state,java.util.Random random,int fortune){return net.minecraft.init.Items.AIR;}
    @Override public void breakBlock(World world,BlockPos pos,IBlockState state){
        TileEntity raw=world.getTileEntity(pos);if(raw instanceof TileEntityConstructorPart){BlockPos owner=((TileEntityConstructorPart)raw).owner();if(world.isBlockLoaded(owner)){TileEntity core=world.getTileEntity(owner);if(core instanceof TileEntityConstructor)((TileEntityConstructor)core).dismantle();}}
        super.breakBlock(world,pos,state);
    }
}
