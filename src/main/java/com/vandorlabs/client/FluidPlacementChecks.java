package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fluids.*;

/** Uses Forge's actual item placement/snapshot events, including two-block doors. */
final class FluidPlacementChecks {
    static void run(WorldServer world){
        net.minecraft.entity.player.EntityPlayerMP player=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(world);
        ItemStack held=player.getHeldItemMainhand();boolean creative=player.capabilities.isCreativeMode;float yaw=player.rotationYaw;
        BlockPos pos=new BlockPos(1006,230,1006);int cases=0;
        java.util.Set<Block> liquids=new java.util.LinkedHashSet<>(java.util.Arrays.asList(Blocks.WATER,Blocks.FLOWING_WATER,Blocks.LAVA,Blocks.FLOWING_LAVA));
        for(Fluid fluid:FluidRegistry.getRegisteredFluids().values())if(!fluid.isGaseous()&&fluid.getBlock()!=null)liquids.add(fluid.getBlock());
        try{player.capabilities.isCreativeMode=false;
            for(String id:new String[]{"programmable_slab","programmable_stairs","programmable_wall","programmable_porthole_wall","programmable_glass","programmable_diagonal_wall","programmable_diagonal_porthole","programmable_diagonal_glass","programmable_door","programmable_trapdoor","programmable_diagonal_trapdoor"})
            for(Block liquid:liquids)for(int direction=0;direction<4;direction++){
                Block block=Block.getBlockFromName("vandorlabs:"+id);require(block!=null,"placement fixture registered "+id);
                world.setBlockToAir(pos);for(EnumFacing face:EnumFacing.values())world.setBlockToAir(pos.offset(face));world.setBlockState(pos.down(),Blocks.STONE.getDefaultState(),2);
                IBlockState fluid=liquid.getDefaultState();if(liquid==Blocks.FLOWING_WATER||liquid==Blocks.FLOWING_LAVA)fluid=fluid.withProperty(net.minecraft.block.BlockLiquid.LEVEL,4);
                world.setBlockState(pos,fluid,2);world.setBlockState(pos.up(),fluid,2);player.rotationYaw=direction*90;
                for(EnumFacing face:EnumFacing.HORIZONTALS)world.setBlockState(pos.offset(face),fluid,2);
                ItemStack stack=new ItemStack(block);require(!stack.isEmpty(),"placement item "+id);player.setHeldItem(EnumHand.MAIN_HAND,stack);
                EnumActionResult result=stack.onItemUse(player,world,pos.down(),EnumHand.MAIN_HAND,EnumFacing.UP,.5F,1,.5F);
                require(result==EnumActionResult.SUCCESS&&world.getBlockState(pos).getBlock()==block,"real item placement "+id+" "+liquid+" result="+result+" state="+world.getBlockState(pos));
                require(stack.isEmpty(),"successful submerged placement consumes one item "+id);
                Fluid expected=liquid==Blocks.WATER||liquid==Blocks.FLOWING_WATER?FluidRegistry.WATER:liquid==Blocks.LAVA||liquid==Blocks.FLOWING_LAVA?FluidRegistry.LAVA:FluidRegistry.lookupFluidForBlock(liquid);require(expected!=null,"registered placement fluid "+liquid);
                for(int side=0;side<2;side++)if(WaterloggedParts.volume(world,pos).hasSide(side)){
                    FluidStack captured=WaterloggedParts.data(world.getTileEntity(pos)).get(side);
                    double[] plane=OpeningFluids.supports(world.getBlockState(pos))?OpeningFluids.plane(world,pos):null;
                    boolean floorSide=block instanceof BlockProgrammableTrapdoor&&Math.abs(plane[1])>Math.max(Math.abs(plane[0]),Math.abs(plane[2]))&&side==OpeningFluids.side(world,pos,new net.minecraft.util.math.Vec3d(.5,-.01,.5));
                    require(floorSide?captured==null:captured!=null&&captured.getFluid()==expected,"capture immersed fluid side / retain dry floor clearance "+id+" "+liquid+" side="+side);
                }
                if(id.equals("programmable_slab")||id.equals("programmable_stairs"))require(!WaterloggedParts.data(world.getTileEntity(pos)).occupied(1),"partial block must not store an unreachable second bucket");
                if(id.equals("programmable_door")){require(world.getBlockState(pos.up()).getBlock()==block,"door upper placed");require(WaterloggedParts.data(world.getTileEntity(pos.up()))!=WaterloggedParts.data(world.getTileEntity(pos)),"door halves store captured fluid independently");}
                clearTicks(world,pos);cases++;
            }
            boundaryChecks(world,player,pos);
            doorHeightChecks(world,player,pos);
        }finally{
            player.capabilities.isCreativeMode=creative;player.rotationYaw=yaw;player.setHeldItem(EnumHand.MAIN_HAND,held);world.setBlockToAir(pos);for(EnumFacing face:EnumFacing.values())world.setBlockToAir(pos.offset(face));
            java.util.Set<?> pending=net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(WorldServer.class,world,"pendingTickListEntriesHashSet","field_73064_N");
            java.util.TreeSet<?> ordered=net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(WorldServer.class,world,"pendingTickListEntriesTreeSet","field_73065_O");
            require(pending.size()==ordered.size(),"placement fixture corrupted scheduled ticks before cleanup: "+pending.size()+" / "+ordered.size());
            clearTicks(world,pos);
            require(pending.size()==ordered.size(),"placement fixture scheduled tick cleanup mismatch");
        }
        System.out.println("[vandorlabs][reprolab] fluid-item-placement PASS cases="+cases+" liquids="+liquids.size()+" source/flowing water/lava registered mod liquids eleven families four directions independent door halves");
    }
    private static void doorHeightChecks(WorldServer world,net.minecraft.entity.player.EntityPlayerMP player,BlockPos pos){
        Block block=Block.getBlockFromName("vandorlabs:programmable_door");int cases=0;
        for(int direction=0;direction<4;direction++)for(int wet=0;wet<3;wet++){
            world.setBlockToAir(pos);world.setBlockToAir(pos.up());for(int y=0;y<2;y++)for(EnumFacing face:EnumFacing.HORIZONTALS)world.setBlockToAir(pos.up(y).offset(face));world.setBlockState(pos.down(),Blocks.STONE.getDefaultState(),2);
            for(int y=0;y<2;y++)world.setBlockState(pos.up(y),wet==2||wet==y?Blocks.WATER.getDefaultState():Blocks.AIR.getDefaultState(),2);
            player.rotationYaw=direction*90;ItemStack item=new ItemStack(block);player.setHeldItem(EnumHand.MAIN_HAND,item);
            require(item.onItemUse(player,world,pos.down(),EnumHand.MAIN_HAND,EnumFacing.UP,.5F,1,.5F)==EnumActionResult.SUCCESS,"partial immersion door placement");
            for(int y=0;y<2;y++)for(int side=0;side<2;side++)require(WaterloggedParts.data(world.getTileEntity(pos.up(y))).occupied(side)==(wet==2||wet==y),"door captures only replaced cell y="+y+" wet="+wet);
            for(int y=0;y<2;y++){
                net.minecraft.tileentity.TileEntity tile=world.getTileEntity(pos.up(y));net.minecraft.tileentity.TileEntity copy=net.minecraft.tileentity.TileEntity.create(world,tile.writeToNBT(new net.minecraft.nbt.NBTTagCompound()));
                require(WaterloggedParts.data(copy).any()==(wet==2||wet==y),"per-cell fluid persistence");
            }
            clearTicks(world,pos);cases++;
        }
        System.out.println("[vandorlabs][reprolab] door-fluid-height PASS cases="+cases+" real placement lower-only upper-only both four facings per-cell persistence");
    }
    private static void clearTicks(WorldServer world,BlockPos pos){
        // Do not accumulate many different scheduled block types in the same test cell.
        // NextTickListEntry's Comparable ordering differs from equality; Java 8 HashSet
        // tree bins then cannot reliably remove this artificial pile of obsolete ticks.
        world.getPendingBlockUpdates(new net.minecraft.world.gen.structure.StructureBoundingBox(pos.add(-3,-2,-3),pos.add(3,3,3)),true);
    }
    private static ItemStack panel(Block block,int mode){ItemStack item=new ItemStack(block);net.minecraft.nbt.NBTTagCompound settings=new net.minecraft.nbt.NBTTagCompound();settings.setBoolean("DiagonalFullWidth",mode==1);settings.setBoolean("DiagonalHalfHeight",mode==2);item.setTagInfo("BlockEntityTag",settings);return item;}
    private static void boundaryChecks(WorldServer world,net.minecraft.entity.player.EntityPlayerMP player,BlockPos pos){int cases=0;
        for(String id:new String[]{"programmable_wall","programmable_porthole_wall","programmable_glass","programmable_diagonal_wall","programmable_diagonal_porthole","programmable_diagonal_glass"})for(int mode=0;mode<3;mode++)for(int direction=0;direction<4;direction++){
            Block block=Block.getBlockFromName("vandorlabs:"+id);boolean shallow=id.contains("diagonal")&&mode==2;EnumFacing support=shallow?EnumFacing.WEST:EnumFacing.DOWN;player.rotationYaw=direction*90;
            world.setBlockToAir(pos);for(EnumFacing face:EnumFacing.values())world.setBlockToAir(pos.offset(face));world.setBlockState(pos.offset(support),Blocks.STONE.getDefaultState(),2);
            ItemStack probe=panel(block,mode);player.setHeldItem(EnumHand.MAIN_HAND,probe);require(probe.onItemUse(player,world,pos.offset(support),EnumHand.MAIN_HAND,support.getOpposite(),.5F,.5F,.5F)==EnumActionResult.SUCCESS,"boundary probe placed");
            IBlockState placed=world.getBlockState(pos);EnumFacing normal=shallow?EnumFacing.UP:PanelJoins.flatFacing(placed,world,pos);
            int first=WaterloggedParts.side(world,pos,new net.minecraft.util.math.Vec3d(.5,.5,.5).add(new net.minecraft.util.math.Vec3d(normal.getDirectionVec()).scale(.51)));
            clearTicks(world,pos);
            for(boolean submerged:new boolean[]{false,true})for(int wetSide=0;wetSide<2;wetSide++)for(boolean mixed:new boolean[]{false,true}){
                world.setBlockToAir(pos);for(EnumFacing face:EnumFacing.values())world.setBlockToAir(pos.offset(face));world.setBlockState(pos.offset(support),Blocks.STONE.getDefaultState(),2);
                EnumFacing wet=wetSide==first?normal:normal.getOpposite();world.setBlockState(pos.offset(wet),Blocks.WATER.getDefaultState(),2);
                if(mixed)world.setBlockState(pos.offset(wet.getOpposite()),Blocks.LAVA.getDefaultState(),2);
                world.setBlockState(pos,submerged?Blocks.WATER.getDefaultState():Blocks.AIR.getDefaultState(),2);
                ItemStack item=panel(block,mode);player.setHeldItem(EnumHand.MAIN_HAND,item);require(item.onItemUse(player,world,pos.offset(support),EnumHand.MAIN_HAND,support.getOpposite(),.5F,.5F,.5F)==EnumActionResult.SUCCESS,"boundary item placed "+id);
                ContainedFluids data=WaterloggedParts.data(world.getTileEntity(pos));
                require(data.get(wetSide)!=null&&data.get(wetSide).getFluid()==FluidRegistry.WATER,"wet boundary captured "+id+" mode="+mode+" side="+wetSide+" submerged="+submerged);
                require(mixed?data.get(1-wetSide)!=null&&data.get(1-wetSide).getFluid()==FluidRegistry.LAVA:!data.occupied(1-wetSide),"opposite boundary remains dry or preserves lava "+id+" mode="+mode+" side="+wetSide+" submerged="+submerged);clearTicks(world,pos);cases++;
            }
        }
        System.out.println("[vandorlabs][reprolab] fluid-boundary-placement PASS cases="+cases+" air/water replaced cells one wet face dry opposite mixed liquids all panel shapes directions");
    }
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
}
