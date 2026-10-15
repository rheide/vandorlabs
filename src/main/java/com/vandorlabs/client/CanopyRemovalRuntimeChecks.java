package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockCanopy;
import com.vandorlabs.tiles.TileEntityCanopy;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.*;

/** Exercise Chunk's replacement-before-break lifecycle, including stale old tiles. */
final class CanopyRemovalRuntimeChecks {
    private static final BlockPos BASE=new BlockPos(8,100,8);
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("canopy removal: "+message);}
    private static void clear(World world){for(BlockPos p:BlockPos.getAllInBox(BASE.add(-5,-1,-5),BASE.add(6,4,6)))world.setBlockToAir(p);}
    static void check(World world) {
        EntityPlayerMP player=(EntityPlayerMP)world.playerEntities.stream().filter(p->p instanceof EntityPlayerMP).findFirst().get();
        for(int motion=0;motion<2;motion++)
        for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int width=1;width<=3;width++)for(int removed=0;removed<width;removed++)for(boolean follower:new boolean[]{false,true}) {
            clear(world);BlockCanopy block=(BlockCanopy)Block.getBlockFromName("vandorlabs:canopy");
            IBlockState state=block.getDefaultState().withProperty(BlockCanopy.FACING,facing);
            for(int column=0;column<width;column++) {
                BlockPos p=BASE.offset(facing.rotateY(),column);world.setBlockState(p,state,2);ItemStack configured=new ItemStack(block);NBTTagCompound settings=new NBTTagCompound();settings.setInteger("CanopyMotion",motion);configured.setTagCompound(settings);block.onBlockPlacedBy(world,p,state,null,configured);
            }
            List<TileEntityCanopy> originals=new ArrayList<>();
            for(int column=0;column<width;column++)for(BlockPos p:block.cells(BASE.offset(facing.rotateY(),column),facing))originals.add(TileEntityCanopy.at(world,p));
            BlockPos root=BASE.offset(facing.rotateY(),removed),hit=follower?root.offset(facing.getOpposite()):root;
            require(player.interactionManager.tryHarvestBlock(hit),"real harvest succeeds");
            for(BlockPos p:block.cells(root,facing))require(world.isAirBlock(p) && TileEntityCanopy.at(world,p)==null,"harvest removes entire constituent and all tile proxies");
            for(int column=0;column<width;column++)if(column!=removed) {
                TileEntityCanopy survivor=TileEntityCanopy.at(world,BASE.offset(facing.rotateY(),column));
                require(survivor!=null && survivor.owner().block()==block,"survivor has live owner");
                survivor.update();survivor.writeToNBT(new NBTTagCompound());survivor.boxes();
            }
            for(TileEntityCanopy old:originals){old.writeToNBT(new NBTTagCompound());old.update();}
        }
        clear(world);
        BlockCanopy block=(BlockCanopy)Block.getBlockFromName("vandorlabs:canopy");
        IBlockState state=block.getDefaultState().withProperty(BlockCanopy.FACING,EnumFacing.WEST);
        world.setBlockState(BASE,state,2);block.onBlockPlacedBy(world,BASE,state,null,new ItemStack(block));
        List<BlockPos> footprint=block.cells(BASE,EnumFacing.WEST);
        world.setBlockState(BASE,Blocks.STONE.getDefaultState(),3);
        require(world.getBlockState(BASE).getBlock()==Blocks.STONE,"replacement block survives old-state teardown");
        for(BlockPos p:footprint)if(!p.equals(BASE))require(world.isAirBlock(p),"canopy followers removed");
        // Model a tile left behind by an older failed removal. Persisting and
        // ticking its former follower must not dereference the air owner's state.
        TileEntityCanopy orphan=new TileEntityCanopy();orphan.anchor=BASE;orphan.pair=BASE;
        BlockPos stale=BASE.up();orphan.setWorld(world);orphan.setPos(stale);
        world.getChunkFromBlockCoords(stale).getTileEntityMap().put(stale,orphan);
        orphan.writeToNBT(new NBTTagCompound());orphan.update();
        require(world.getTileEntity(stale)==null && world.getBlockState(BASE).getBlock()==Blocks.STONE,"orphan tile cleanup preserves foreign replacement");
        clear(world);
        CanopyTargetedHarvestRuntimeChecks.check(world,player);
        OpenedFrontCanopyRemovalChecks.check(world,player);
        System.out.println("[vandorlabs][reprolab] canopy-removal PASS real-harvest root/follower widths=1,2,3 facings=4 replacement old-tiles orphan-persistence");
    }
}
