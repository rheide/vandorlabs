package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockCanopy;
import com.vandorlabs.tiles.TileEntityCanopy;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Genuine Minecraft left clicks must send the selected constituent to the server. */
final class CanopyTargetedHarvestClientChecks {
    private static final BlockPos BASE=new BlockPos(8,100,8);
    private static int fixture,phase,ticks;
    private static java.util.List<BlockPos> formerFootprint;
    private static AxisAlignedBB formerRegion;
    private static void require(boolean value,String what){if(!value)throw new IllegalStateException("canopy harvest click: "+what+" fixture="+fixture);}
    private static BlockCanopy block(){return (BlockCanopy)Block.getBlockFromName("vandorlabs:"+(fixture<6?"canopy":fixture<12?"long_canopy":fixture<16?"front_canopy_glass":"large_front_canopy_glass"));}
    private static EnumFacing facing(){return fixture<12?(fixture%6<3?EnumFacing.NORTH:EnumFacing.EAST):EnumFacing.HORIZONTALS[fixture%4];}
    private static int column(){return fixture<12?fixture%3:0;}
    static boolean tick(Minecraft mc)throws Exception{
        if(fixture>=20)return true;
        BlockCanopy block=block();EnumFacing facing=facing();
        if(phase==0){
            mc.displayGuiScreen(null);mc.player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);
            mc.getIntegratedServer().addScheduledTask(()->{
                World world=mc.getIntegratedServer().getWorld(0);EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);
                player.interactionManager.setGameType(net.minecraft.world.GameType.CREATIVE);
                int radius=fixture<12?6:12;
                for(BlockPos cell:BlockPos.getAllInBox(BASE.add(-radius,-radius,-radius),BASE.add(radius,radius,radius)))world.setBlockToAir(cell);
                IBlockState state=block.getDefaultState().withProperty(BlockCanopy.FACING,facing);
                for(int x=0;x<(fixture<12?3:1);x++){
                    BlockPos root=BASE.offset(facing.rotateY(),x);world.setBlockState(root,state,3);
                    com.vandorlabs.canopy.LargeCanopySettings settings=new com.vandorlabs.canopy.LargeCanopySettings();settings.motion=fixture%6<3?0:1;settings.angle=0;
                    if(fixture>=12){settings.depth=32;settings.mount=fixture%4;settings.hinge=fixture%4;settings.openable=true;}
                    NBTTagCompound n=new NBTTagCompound();settings.write(n);ItemStack stack=new ItemStack(block);stack.setTagCompound(n);block.onBlockPlacedBy(world,root,state,null,stack);
                }
                TileEntityCanopy owner=TileEntityCanopy.at(world,BASE);formerFootprint=new java.util.ArrayList<>();
                for(int x=0;x<(fixture<12?3:1);x++)formerFootprint.addAll(TileEntityCanopy.at(world,BASE.offset(facing.rotateY(),x)).cells());
                formerRegion=owner.selectionBounds();owner.progress=owner.previous=fixture>=12||fixture%6>=3?1:0;owner.targetOpen=owner.progress==1;formerRegion=formerRegion.union(owner.selectionBounds());owner.sync();
            }).get();
            phase=1;ticks=0;return false;
        }
        if(++ticks<20)return false;
        if(phase==1){
            TileEntityCanopy tile=TileEntityCanopy.at(mc.world,BASE);require(tile!=null,"client assembly synchronized");
            RayTraceResult aimed=CanopyTargetedHarvestRuntimeChecks.aim(mc.world,mc.player,tile.owner(),fixture<12?column():block.width/2);require(aimed!=null,"visible shell can be aimed kind="+block.kind+" facing="+facing+" pose="+tile.progress+" settings="+tile.large.key()+" bounds="+tile.selectionBounds());
            mc.player.prevPosX=mc.player.lastTickPosX=mc.player.posX;mc.player.prevPosY=mc.player.lastTickPosY=mc.player.posY;mc.player.prevPosZ=mc.player.lastTickPosZ=mc.player.posZ;
            mc.player.prevRotationYaw=mc.player.rotationYaw;mc.player.prevRotationPitch=mc.player.rotationPitch;
            mc.entityRenderer.getMouseOver(1);mc.playerController.updateController();
            BlockPos expected=BASE.offset(facing.rotateY(),column());require(mc.objectMouseOver!=null && mc.objectMouseOver.typeOfHit==RayTraceResult.Type.BLOCK && mc.objectMouseOver.getBlockPos().equals(expected),"actual mouse-over selects requested component actual="+(mc.objectMouseOver==null?"null":mc.objectMouseOver.getBlockPos())+" expected="+expected);
            mc.player.connection.sendPacket(new net.minecraft.network.play.client.CPacketPlayer.PositionRotation(mc.player.posX,mc.player.posY,mc.player.posZ,mc.player.rotationYaw,mc.player.rotationPitch,false));
            net.minecraftforge.fml.relauncher.ReflectionHelper.findMethod(Minecraft.class,"clickMouse","func_147116_af").invoke(mc);
            phase=2;ticks=0;return false;
        }
        mc.getIntegratedServer().addScheduledTask(()->{
            World world=mc.getIntegratedServer().getWorld(0);
            if(fixture<12){
                for(int x=0;x<3;x++)for(BlockPos cell:block.cells(BASE.offset(facing.rotateY(),x),facing))require(x==column()?world.isAirBlock(cell):world.getBlockState(cell).getBlock()==block,"server harvest removes requested column only cell="+cell);
                for(int x=0;x<3;x++)for(BlockPos cell:block.cells(BASE.offset(facing.rotateY(),x),facing))world.setBlockToAir(cell);
            }else{
                for(BlockPos cell:formerFootprint)require(world.isAirBlock(cell) && TileEntityCanopy.at(world,cell)==null,"server opened-front helper removed cell="+cell+" state="+world.getBlockState(cell)+" tile="+world.getTileEntity(cell));
                require(world.getCollisionBoxes(null,formerRegion.shrink(.00001)).isEmpty(),"server opened-front swept collision cleared");
            }
        }).get();
        if(fixture>=12){
            for(BlockPos cell:formerFootprint)require(mc.world.isAirBlock(cell) && TileEntityCanopy.at(mc.world,cell)==null,"client opened-front helper removed cell="+cell+" state="+mc.world.getBlockState(cell)+" tile="+mc.world.getTileEntity(cell));
            require(mc.world.getCollisionBoxes(null,formerRegion.shrink(.00001)).isEmpty(),"client opened-front swept collision cleared");
        }
        fixture++;phase=0;ticks=0;
        if(fixture==20)System.out.println("[vandorlabs][reprolab] canopy-harvest-clicks PASS real-Minecraft-left-click server-packet both-depths left-middle-right closed-slide rotated-open-hinge opened-front-widths3,5 mounts4 client-server-helper-collision-removal");
        return fixture>=20;
    }
    private CanopyTargetedHarvestClientChecks(){}
}
