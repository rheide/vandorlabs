package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.canopy.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.util.*;

/** Genuine seated vanilla clicks and vehicle packets for both canopy motions. */
final class SeatedCanopyClickRuntimeChecks {
    private static final BlockPos SEAT=new BlockPos(128,30,128),CANOPY=SEAT.up().north();
    private static int mode,ticks,phase;
    private static EntityGroundVehicle serverCraft;
    private static BlockPos capturedCanopy;
    private static void require(boolean ok,String text){if(!ok)throw new IllegalStateException("seated canopy: "+text+" case="+mode);}
    private static EntityPlayerMP player(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayers().get(0);}
    private static void setup(Minecraft mc){
        EntityPlayerMP player=player(mc);World world=player.world;player.dismountRidingEntity();if(serverCraft!=null){serverCraft.setDead();serverCraft=null;}
        for(BlockPos pos:BlockPos.getAllInBox(SEAT.add(-5,-1,-5),SEAT.add(5,5,5)))world.setBlockToAir(pos);
        world.setBlockState(SEAT.down(),Blocks.STONE.getDefaultState(),2);
        BlockPilotSeat seat=(BlockPilotSeat)ModBlocks.PILOT_SEAT;world.setBlockState(SEAT,seat.getDefaultState(),2);seat.onBlockPlacedBy(world,SEAT,seat.getDefaultState(),player,new ItemStack(seat));
        TileEntityPilotSeat pilot=(TileEntityPilotSeat)world.getTileEntity(SEAT);pilot.setTaxiMode(false);pilot.setHeight(1);
        LargeCanopySettings settings=new LargeCanopySettings();settings.angle=1;settings.motion=mode%3==2?1:0;settings.slideForward=mode%3==1;
        for(String kind:new String[]{"canopy","regular_canopy_glass"}){
            BlockCanopy block=(BlockCanopy)Block.getBlockFromName("vandorlabs:"+kind);BlockPos pos=kind.equals("canopy")?CANOPY:CANOPY.south(2);
            ItemStack item=new ItemStack(block);NBTTagCompound tag=new NBTTagCompound();settings.write(tag);item.setTagCompound(tag);
            world.setBlockState(pos,block.getDefaultState(),2);block.onBlockPlacedBy(world,pos,block.getDefaultState(),player,item);
        }
        player.setSneaking(false);player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);
        if(mode<3){
            seat.onBlockActivated(world,SEAT,world.getBlockState(SEAT),player,EnumHand.MAIN_HAND,EnumFacing.NORTH,.5F,.5F,.5F);
            require(player.getRidingEntity() instanceof com.vandorlabs.entity.EntityChairSeat,"parked seat boards");player.getRidingEntity().updatePassenger(player);
            TileEntityCanopy tile=TileEntityCanopy.at(world,CANOPY).owner();tile.toggle();for(int tick=0;tick<16;tick++)tile.update();tile.sync();
        }else{
            BlockPos origin=SEAT.down().north();Set<BlockPos> positions=new LinkedHashSet<>();positions.add(SEAT.down());positions.add(SEAT);positions.addAll(TileEntityCanopy.at(world,CANOPY).cells());positions.add(CANOPY.south(2));
            List<VehicleStructure.Cell> cells=new ArrayList<>();for(BlockPos pos:positions){net.minecraft.tileentity.TileEntity tile=world.getTileEntity(pos);cells.add(new VehicleStructure.Cell(pos.subtract(origin),world.getBlockState(pos),tile==null?null:VehicleTileData.translated(tile.writeToNBT(new NBTTagCompound()),BlockPos.ORIGIN.subtract(origin))));}
            serverCraft=new EntityGroundVehicle(world);serverCraft.install(new VehicleStructure(cells,SEAT.subtract(origin),EnumFacing.NORTH,1));
            for(BlockPos pos:BlockPos.getAllInBox(new BlockPos(154,39,154),new BlockPos(166,39,166)))world.setBlockState(pos,Blocks.STONE.getDefaultState(),2);
            serverCraft.setPosition(160,40,160);require(world.spawnEntity(serverCraft),"craft spawns");require(serverCraft.board(player),"captured seat boards");serverCraft.updatePassenger(player);
            capturedCanopy=CANOPY.subtract(origin);require(VehicleCanopies.toggle(serverCraft,capturedCanopy),"captured canopy opens");for(int tick=0;tick<16;tick++)VehicleCanopyTestSteps.step(serverCraft);
        }
        player.connection.setPlayerLocation(player.posX,player.posY,player.posZ,0,-30);
    }
    static boolean tick(Minecraft mc)throws Exception{
        if(++ticks==1 && phase==0){mc.gameSettings.thirdPersonView=0;mc.gameSettings.hideGUI=false;mc.player.noClip=false;mc.getIntegratedServer().addScheduledTask(()->setup(mc)).get();phase=1;}
        if(phase==1 && ticks>=40){
            require(mc.player.isRiding(),"client has mounted passenger");
            TileEntityCanopy tile;
            EntityGroundVehicle craft=mc.player.getRidingEntity() instanceof EntityGroundVehicle?(EntityGroundVehicle)mc.player.getRidingEntity():null;
            if(mode>=3 && (craft==null || craft.structure==null)){require(ticks<200,"captured structure arrives");return false;}
            tile=mode<3?TileEntityCanopy.at(mc.world,CANOPY):TileEntityCanopy.at(craft.view,capturedCanopy);
            if(tile==null || tile.progress!=1){require(ticks<200,"open canopy pose arrives");return false;}
            tile=tile.owner();boolean selected=false;mc.player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);
            for(CanopyMesh.Face face:tile.faces())for(int triangle=1;triangle<face.vertices.length-1 && !selected;triangle++){
                Vec3d a=face.vertices[0],b=face.vertices[triangle],c=face.vertices[triangle+1];if(b.subtract(a).crossProduct(c.subtract(a)).lengthSquared()<1e-12)continue;
                Vec3d target=CanopyMesh.rotate(a.add(b).add(c).scale(1D/3),tile.facing()).add(new Vec3d(tile.getPos()));if(craft!=null)target=craft.toWorld(target);
                Vec3d look=target.subtract(mc.player.getPositionEyes(1)).normalize();float yaw=(float)Math.toDegrees(Math.atan2(-look.x,look.z)),pitch=(float)-Math.toDegrees(Math.asin(MathHelper.clamp(look.y,-1,1)));
                mc.player.rotationYaw=mc.player.prevRotationYaw=yaw;mc.player.rotationPitch=mc.player.prevRotationPitch=pitch;
                if(craft==null){mc.entityRenderer.getMouseOver(1);OffsetTrapdoorSelection.updateTarget(1);selected=mc.objectMouseOver!=null && mc.objectMouseOver.typeOfHit==RayTraceResult.Type.BLOCK && mc.objectMouseOver.getBlockPos().equals(CANOPY);}
                else{VehicleInteraction.Target hit=VehicleInteraction.pointed(mc.player);selected=hit!=null && hit.craft==craft && hit.hit.action==VehicleSelection.Action.CANOPY;}
                if(selected){mc.getIntegratedServer().addScheduledTask(()->{player(mc).rotationYaw=yaw;player(mc).rotationPitch=pitch;}).get();if(craft!=null)mc.objectMouseOver=new RayTraceResult(RayTraceResult.Type.MISS,mc.player.getPositionEyes(1),EnumFacing.UP,BlockPos.ORIGIN);net.minecraftforge.fml.relauncher.ReflectionHelper.findMethod(Minecraft.class,"rightClickMouse","func_147121_ag").invoke(mc);}
            }
            require(selected,"seated pilot can target an open canopy surface");phase=2;ticks=0;
        }
        if(phase==2 && ticks>=30){
            mc.getIntegratedServer().addScheduledTask(()->{TileEntityCanopy tile=mode<3?TileEntityCanopy.at(player(mc).world,CANOPY):TileEntityCanopy.at(serverCraft.view,capturedCanopy);require(!tile.owner().targetOpen && tile.owner().progress==0,"real seated click closes on server");require(player(mc).isRiding(),"click keeps pilot seated");}).get();
            System.out.println("[vandorlabs][reprolab] seated-canopy-click PASS "+(mode<3?"parked":"vehicle")+" motion="+(mode%3));mode++;phase=0;ticks=0;
            if(mode==6){mc.getIntegratedServer().addScheduledTask(()->{player(mc).dismountRidingEntity();if(serverCraft!=null)serverCraft.setDead();}).get();return true;}
        }
        return false;
    }
}
