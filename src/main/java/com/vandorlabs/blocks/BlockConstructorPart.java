package com.vandorlabs.blocks;

import com.vandorlabs.tiles.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Reserved model cells have no inventory form; break the core to recover the frame. */
public final class BlockConstructorPart extends BlockVandor {
    public BlockConstructorPart(){super("constructor_part");setCreativeTab(null);setHardness(5);setResistance(10);setLightOpacity(0);}
    @Override public boolean removedByPlayer(IBlockState state,World world,BlockPos pos,net.minecraft.entity.player.EntityPlayer player,boolean harvest){
        if(!world.isRemote){TileEntity raw=world.getTileEntity(pos);if(raw instanceof TileEntityConstructorPart){TileEntity core=world.getTileEntity(((TileEntityConstructorPart)raw).owner());if(core instanceof TileEntityConstructor)((TileEntityConstructor)core).dismantle();}
            IBlockState restored=world.getBlockState(pos);if(!player.capabilities.isCreativeMode&&!world.isAirBlock(pos))restored.getBlock().dropBlockAsItem(world,pos,restored,0);
        }
        return world.setBlockToAir(pos);
    }
    @Override public boolean isOpaqueCube(IBlockState state){return false;}
    private TileEntityConstructor controller(World world,BlockPos pos){TileEntity raw=world.getTileEntity(pos);if(!(raw instanceof TileEntityConstructorPart))return null;BlockPos owner=((TileEntityConstructorPart)raw).owner();if(!world.isBlockLoaded(owner))return null;TileEntity tile=world.getTileEntity(owner);return tile instanceof TileEntityConstructor?(TileEntityConstructor)tile:null;}
    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,net.minecraft.entity.player.EntityPlayer player,net.minecraft.util.EnumHand hand,net.minecraft.util.EnumFacing side,float x,float y,float z){TileEntityConstructor root=controller(world,pos);return root!=null&&root.core().onBlockActivated(world,root.getPos(),world.getBlockState(root.getPos()),player,hand,side,x,y,z);}
    @Override public boolean canProvidePower(IBlockState state){return true;}
    @Override public int getWeakPower(IBlockState state,net.minecraft.world.IBlockAccess access,BlockPos pos,net.minecraft.util.EnumFacing side){if(!(access instanceof World))return 0;TileEntityConstructor root=controller((World)access,pos);return root==null?0:root.getOutputLevel();}
    @Override public boolean isFullCube(IBlockState state){return false;}
    @Override public boolean hasTileEntity(IBlockState state){return true;}
    @Override public TileEntity createTileEntity(World world,IBlockState state){return new TileEntityConstructorPart();}
    @Override public net.minecraft.item.Item getItemDropped(IBlockState state,java.util.Random random,int fortune){return net.minecraft.init.Items.AIR;}
    @Override public void breakBlock(World world,BlockPos pos,IBlockState state){
        TileEntity raw=world.getTileEntity(pos);if(raw instanceof TileEntityConstructorPart){BlockPos owner=((TileEntityConstructorPart)raw).owner();if(world.isBlockLoaded(owner)){TileEntity core=world.getTileEntity(owner);if(core instanceof TileEntityConstructor)((TileEntityConstructor)core).dismantle();}}
        super.breakBlock(world,pos,state);
    }
}
