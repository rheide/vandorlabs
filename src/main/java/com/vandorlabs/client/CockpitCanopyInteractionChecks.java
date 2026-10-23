package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockCanopy;
import com.vandorlabs.cockpit.*;
import com.vandorlabs.container.*;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.tiles.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Actual Configurizer events and reversible seat movement in a shared canopy cell. */
final class CockpitCanopyInteractionChecks {
    private static void require(boolean value,String what){if(!value)throw new IllegalStateException("cockpit canopy interaction: "+what);}
    static void configuration(TileEntityCockpit cockpit,TileEntityCanopy canopy,EntityPlayerMP player){
        if(!canopy.configurable())return;
        World world=cockpit.getWorld();Vec3d old=player.getPositionVector();float yaw=player.rotationYaw,pitch=player.rotationPitch;ItemStack held=player.getHeldItemMainhand();boolean creative=player.capabilities.isCreativeMode;
        try{for(boolean mode:new boolean[]{false,true}){
            player.capabilities.isCreativeMode=mode;player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(ModItems.CONFIGURIZER));
            BlockPos cell=canopy.getPos();aim(player,new Vec3d(cell).addVector(.5,2.5,.5));
            RayTraceResult hit=world.rayTraceBlocks(player.getPositionEyes(1),player.getPositionEyes(1).add(player.getLookVec().scale(5)),false,false,false);
            require(hit!=null&&world.getBlockState(hit.getBlockPos()).getBlock() instanceof BlockCanopy,"visible canopy ray");
            click(player,hit);require(player.openContainer instanceof ContainerLargeCanopy&&((ContainerLargeCanopy)player.openContainer).tile==canopy.owner(),"Configurator opens canopy above cockpit in "+(mode?"creative":"survival"));player.closeScreen();
            java.util.List<AxisAlignedBB> body=CockpitAssembly.boxes(world,cell);require(!body.isEmpty(),"shared cockpit geometry");AxisAlignedBB box=body.get(0).offset(cell);
            aim(player,new Vec3d((box.minX+box.maxX)/2,box.maxY+.01,(box.minZ+box.maxZ)/2));
            hit=world.rayTraceBlocks(player.getPositionEyes(1),player.getPositionEyes(1).add(player.getLookVec().scale(3)),false,false,false);require(hit!=null,"visible cockpit ray");
            click(player,hit);require(player.openContainer instanceof ContainerCockpit&&((ContainerCockpit)player.openContainer).tile==cockpit,"Configurator still opens exposed cockpit through shared cell");player.closeScreen();
        }}finally{player.closeScreen();player.setHeldItem(EnumHand.MAIN_HAND,held);player.capabilities.isCreativeMode=creative;player.setPosition(old.x,old.y,old.z);player.rotationYaw=yaw;player.rotationPitch=pitch;}
        System.out.println("[vandorlabs][reprolab] cockpit-canopy-configurizer PASS "+canopy.block().kind+"/"+canopy.facing());
    }
    private static void aim(EntityPlayerMP player,Vec3d eye){player.setPosition(eye.x,eye.y-player.getEyeHeight(),eye.z);player.rotationPitch=90;player.rotationYaw=0;}
    private static void click(EntityPlayerMP player,RayTraceResult hit){BlockPos p=hit.getBlockPos();require(player.interactionManager.processRightClickBlock(player,player.world,player.getHeldItemMainhand(),EnumHand.MAIN_HAND,p,hit.sideHit,(float)(hit.hitVec.x-p.getX()),(float)(hit.hitVec.y-p.getY()),(float)(hit.hitVec.z-p.getZ()))==EnumActionResult.SUCCESS,"actual Configurizer click");}
    static void movement(TileEntityCockpit cockpit,EntityPlayerMP player){
        World world=cockpit.getWorld();BlockPos shared=null;for(BlockPos cell:CockpitReservations.rear(cockpit,cockpit.facing()))if(cell.getY()==cockpit.getPos().getY()+1){shared=cell;break;}require(shared!=null,"raised rear seat sweep");
        NBTTagCompound original=cockpit.writeToNBT(new NBTTagCompound());Vec3d old=player.getPositionVector();
        try{player.setPosition(cockpit.getPos().getX()+5,cockpit.getPos().getY(),cockpit.getPos().getZ()+5);
            for(String id:new String[]{"canopy","long_canopy"}){
                BlockCanopy block=(BlockCanopy)net.minecraft.block.Block.getBlockFromName("vandorlabs:"+id);BlockPos anchor=shared.offset(cockpit.facing(),block.length-1);
                require(block.fits(world,anchor,cockpit.facing()),"canopy fixture fits");world.setBlockState(anchor,block.getDefaultState().withProperty(BlockCanopy.FACING,cockpit.facing()),2);block.onBlockPlacedBy(world,anchor,world.getBlockState(anchor),player,new ItemStack(block));
                TileEntityCanopy canopy=TileEntityCanopy.at(world,anchor);java.util.List<BlockPos> cells=new java.util.ArrayList<>(canopy.cells());
                require(cockpit.toggleSeat(player),"seat extends beneath "+id+": "+cockpit.seatBlockReason);finish(cockpit);
                require(CockpitAssembly.root(world,shared)==cockpit,"shared rear canopy resolves extended seat");require(!CockpitAssembly.boxes(world,shared).isEmpty(),"extended seat collision survives shared canopy");
                cockpit.readFromNBT(cockpit.writeToNBT(new NBTTagCompound()));require(CockpitAssembly.root(world,shared)==cockpit,"shared rear survives saved seat state");
                require(cockpit.toggleSeat(player),"seat returns beneath "+id);finish(cockpit);CockpitReservations.tick(cockpit);
                for(BlockPos cell:cells)require(world.getBlockState(cell).getBlock()==block,"return releases only seat helpers, never canopy");
                world.destroyBlock(anchor,false);cockpit.readFromNBT(original);
            }
        }finally{player.setPosition(old.x,old.y,old.z);cockpit.readFromNBT(original);}
        System.out.println("[vandorlabs][reprolab] cockpit-canopy-seat PASS "+cockpit.facing()+" extend return persistence collision canopy preservation");
    }
    private static void finish(TileEntityCockpit tile){NBTTagCompound tag=tile.writeToNBT(new NBTTagCompound());tag.getCompoundTag("CockpitSeatSlide").setLong("Started",tile.getWorld().getTotalWorldTime()-CockpitActuator.DURATION);tile.readFromNBT(tag);}
}
