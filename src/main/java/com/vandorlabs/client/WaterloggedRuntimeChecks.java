package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

final class WaterloggedRuntimeChecks {
    static void run(World world){
        net.minecraft.world.WorldServer serverWorld=(net.minecraft.world.WorldServer)world;
        VanillaTextureCopyChecks.run(serverWorld);StairConfigurationChecks.server(serverWorld,serverWorld.getMinecraftServer().getPlayerList().getPlayers().get(0));DoorWaterGushChecks.run(serverWorld);
        FluidPlacementChecks.run((net.minecraft.world.WorldServer)world);
        OpeningFluidRuntimeChecks.run(world);
        SpongeFluidChecks.run(world);
        BlockPos pos=new BlockPos(146,230,146);
        try{
            for(Block block:new Block[]{ModBlocks.PROGRAMMABLE_SLAB,ModBlocks.PROGRAMMABLE_STAIRS})for(boolean upper:new boolean[]{false,true})for(EnumFacing facing:EnumFacing.HORIZONTALS){
                IBlockState state=block.getDefaultState();
                state=block instanceof BlockProgrammableSlab?state.withProperty(BlockProgrammableSlab.HALF,upper?BlockSlab.EnumBlockHalf.TOP:BlockSlab.EnumBlockHalf.BOTTOM):state.withProperty(BlockStairs.HALF,upper?BlockStairs.EnumHalf.TOP:BlockStairs.EnumHalf.BOTTOM).withProperty(BlockStairs.FACING,facing);
                world.setBlockState(pos,state);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(pos);tile.setWaterlogged(true);
                double volume=0;for(AxisAlignedBB gap:WaterloggedParts.gaps(world,pos))volume+=(gap.maxX-gap.minX)*(gap.maxY-gap.minY)*(gap.maxZ-gap.minZ);
                require(Math.abs(volume-(block instanceof BlockProgrammableSlab?.5:.25))<1e-6,"water volume follows solid shape");
                require(Boolean.TRUE.equals(block.isAABBInsideMaterial(world,pos,new AxisAlignedBB(pos),Material.WATER)),"water physics hook");
                TileEntityAnimatedScreenSelector copy=new TileEntityAnimatedScreenSelector();copy.readFromNBT(tile.writeToNBT(new NBTTagCompound()));require(copy.isWaterlogged(),"waterlogging persists");
                tile.setWaterlogged(false);require(!WaterloggedParts.wet(world,pos),"drained variant");
            }
            net.minecraft.world.WorldServer server=(net.minecraft.world.WorldServer)world;
            net.minecraft.entity.player.EntityPlayerMP player=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(server);boolean creative=player.capabilities.isCreativeMode;net.minecraft.item.ItemStack old=player.getHeldItemMainhand();player.capabilities.isCreativeMode=false;
            try{
                String[] panels={"programmable_wall","programmable_porthole_wall","programmable_glass","programmable_diagonal_wall","programmable_diagonal_glass"};
                for(String id:panels)for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int mode=0;mode<3;mode++)for(boolean inverted:new boolean[]{false,true}){
                    Block block=Block.getBlockFromName("vandorlabs:"+id);IBlockState state=block.getDefaultState();
                    if(block instanceof BlockProgrammableGlass)state=state.withProperty(BlockGlassWall.ROTATED,facing.getAxis()==EnumFacing.Axis.X).withProperty(BlockGlassWall.DEPTH,mode);
                    else state=state.withProperty(BlockProgrammableWall.FACING,facing).withProperty(BlockProgrammableWall.INVERTED,inverted).withProperty(BlockProgrammableWall.DEPTH,mode);
                    world.setBlockState(pos,state);net.minecraft.tileentity.TileEntity tile=world.getTileEntity(pos);
                    if(tile instanceof com.vandorlabs.tiles.TileEntityProgrammableGlass)((com.vandorlabs.tiles.TileEntityProgrammableGlass)tile).setPanelFacing(facing);
                    if(block instanceof BlockProgrammableWall&&((BlockProgrammableWall)block).isDiagonalShape())((TileEntityAnimatedScreenSelector)tile).setDiagonalGeometry(mode,0);
                    ContainedFluids fluids=WaterloggedParts.data(tile);WaterloggedParts.Volume volume=WaterloggedParts.volume(world,pos);
                    for(int side=0;side<2;side++)if(volume.hasSide(side)){
                        WaterloggedParts.Cell cell=null;for(WaterloggedParts.Cell candidate:volume.cells)if(candidate.side==side){cell=candidate;break;}
                        Vec3d hit=cell.box.getCenter().add(new Vec3d(pos));
                        player.setHeldItem(EnumHand.MAIN_HAND,new net.minecraft.item.ItemStack(side==0?net.minecraft.init.Items.WATER_BUCKET:net.minecraft.init.Items.LAVA_BUCKET));
                        net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event=new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player,EnumHand.MAIN_HAND,pos,EnumFacing.UP,hit);WaterloggedParts.bucket(event);
                        require(event.isCanceled()&&event.getCancellationResult()==EnumActionResult.SUCCESS,"Bucket fills selected side "+id+" "+mode+" "+facing+" "+side);
                        require(fluids.get(side)!=null&&player.getHeldItemMainhand().getItem()==net.minecraft.init.Items.BUCKET,"One bucket consumed");
                        require(Boolean.TRUE.equals(block.isAABBInsideMaterial(world,pos,cell.box.offset(pos),side==0?Material.WATER:Material.LAVA)),"Side material physics");
                        player.setHeldItem(EnumHand.MAIN_HAND,new net.minecraft.item.ItemStack(net.minecraft.init.Items.WATER_BUCKET));WaterloggedParts.bucket(new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player,EnumHand.MAIN_HAND,pos,EnumFacing.UP,hit));require(player.getHeldItemMainhand().getItem()==net.minecraft.init.Items.WATER_BUCKET,"Occupied side never consumes another bucket");
                    }
                    net.minecraft.nbt.NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());net.minecraft.tileentity.TileEntity restored=net.minecraft.tileentity.TileEntity.create(world,saved);require(WaterloggedParts.data(restored).any(),"Wall liquid save/load");
                    for(int side=0;side<2;side++)if(fluids.occupied(side)){
                        WaterloggedParts.Cell cell=null;for(WaterloggedParts.Cell candidate:volume.cells)if(candidate.side==side){cell=candidate;break;}
                        player.setHeldItem(EnumHand.MAIN_HAND,new net.minecraft.item.ItemStack(net.minecraft.init.Items.BUCKET));WaterloggedParts.bucket(new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player,EnumHand.MAIN_HAND,pos,EnumFacing.UP,cell.box.getCenter().add(new Vec3d(pos))));
                        require(!fluids.occupied(side)&&player.getHeldItemMainhand().getItem()==(side==0?net.minecraft.init.Items.WATER_BUCKET:net.minecraft.init.Items.LAVA_BUCKET),"Drain returns matching liquid from only selected side");
                    }
                    world.setBlockToAir(pos);
                }
                TileEntityAnimatedScreenSelector legacy=new TileEntityAnimatedScreenSelector();NBTTagCompound oldData=new NBTTagCompound();oldData.setBoolean("Waterlogged",true);legacy.readFromNBT(oldData);require(legacy.fluids.get(0).getFluid()==net.minecraftforge.fluids.FluidRegistry.WATER,"Legacy waterlogged saves migrate");
            }finally{player.capabilities.isCreativeMode=creative;player.setHeldItem(EnumHand.MAIN_HAND,old);}
            System.out.println("[vandorlabs][reprolab] wall-fluids PASS five panel families four facings three depths/slopes both inversions independent water/lava buckets persistence legacy migration");
        }finally{world.setBlockToAir(pos);}
        System.out.println("[vandorlabs][reprolab] waterlogged-parts PASS slab stair halves facings clipped-volume water-physics persistence");
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
