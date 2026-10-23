package com.vandorlabs.client;

import com.vandorlabs.vehicle.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.gear.GearTileEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.block.Block;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import java.io.*;
import java.util.*;

/** Opt-in reproduction from a private, read-only block export in a disposable test world. */
final class ExistingVehicleHoverChecks {
    private static final BlockPos ORIGIN=new BlockPos(184,100,48);
    private static UUID id;private static int stage,ticks,scenario,total;private static double startY,startX,startZ;private static float startYaw;
    private static EntityPlayerMP player(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayers().get(0);}
    private static EntityGroundVehicle server(Minecraft mc){return (EntityGroundVehicle)mc.getIntegratedServer().getWorld(0).getEntityFromUuid(id);}
    private static EntityGroundVehicle client(Minecraft mc){for(EntityGroundVehicle craft:VehicleLookup.loaded(mc.world))if(craft.getUniqueID().equals(id))return craft;return null;}
    private static void key(net.minecraft.client.settings.KeyBinding binding,boolean value){KeyBinding.setKeyBindState(binding.getKeyCode(),value);}
    private static void keys(Minecraft mc){key(mc.gameSettings.keyBindForward,false);key(mc.gameSettings.keyBindBack,false);key(mc.gameSettings.keyBindRight,false);key(mc.gameSettings.keyBindLeft,false);key(mc.gameSettings.keyBindJump,false);key(mc.gameSettings.keyBindSneak,false);key(mc.gameSettings.keyBindSprint,false);}
    static boolean tick(Minecraft mc,File output)throws Exception{
        require(++total<1200,"timeout stage="+stage+" scenario="+scenario);mc.gameSettings.pauseOnLostFocus=false;
        if(stage==0){keys(mc);mc.displayGuiScreen(null);prepare(mc);stage=1;ticks=0;return false;}
        EntityGroundVehicle craft=client(mc);ticks++;
        if(stage==1&&ticks>25&&craft!=null&&craft.structure!=null){mc.getIntegratedServer().addScheduledTask(()->require(server(mc).board(player(mc)),"board actual cockpit")).get();stage=2;ticks=0;}
        else if(stage==2&&ticks>25&&mc.player.getRidingEntity()==craft){require(craft.getControllingPassenger()==mc.player,"client recognizes controlling pilot");require(craft.hover&&!craft.transferInProgress,"direct Hover is released for input");startY=craft.posY;key(mc.gameSettings.keyBindJump,true);stage=3;ticks=0;}
        else if(stage==3&&ticks>=40){key(mc.gameSettings.keyBindJump,false);mc.getIntegratedServer().addScheduledTask(()->{EntityGroundVehicle actual=server(mc);require(actual.posY>startY+1,"Space ascends with deploy-on-"+(scenario==0?"ON":"OFF")+" gear; ack="+actual.acknowledgedInput()+" hover="+actual.hover+" hold="+actual.transferInProgress+" y="+actual.posY+" start="+startY+" target="+actual.hoverTarget+" dy="+actual.motionY+" loaded="+actual.collision.loaded(actual.world,actual.posX,actual.posY,actual.posZ,0,.18,0,actual.rotationYaw)+" bounds="+actual.collision.bounds+" inputs="+diagnostic(actual)+" blockers="+blockers(actual));}).get();startX=craft.posX;startZ=craft.posZ;key(mc.gameSettings.keyBindForward,true);stage=4;ticks=0;}
        else if(stage==4&&ticks>=40){key(mc.gameSettings.keyBindForward,false);mc.getIntegratedServer().addScheduledTask(()->{EntityGroundVehicle actual=server(mc);require(Math.hypot(actual.posX-startX,actual.posZ-startZ)>.5,"W drives in Hover");}).get();startYaw=craft.rotationYaw;key(mc.gameSettings.keyBindRight,true);stage=5;ticks=0;}
        else if(stage==5&&ticks>=25){key(mc.gameSettings.keyBindRight,false);mc.getIntegratedServer().addScheduledTask(()->require(Math.abs(MathHelper.wrapDegrees(server(mc).rotationYaw-startYaw))>5,"A/D controls yaw")).get();javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"actual-plate-hover-"+scenario+".png"));keys(mc);mc.getIntegratedServer().addScheduledTask(()->{EntityGroundVehicle actual=server(mc);actual.setTransferInProgress(true);player(mc).dismountRidingEntity();actual.setDead();}).get();System.out.println("[vandorlabs][reprolab] actual-plate-hover-case PASS controller required; real client Space/W/A-D; gear trigger="+(scenario+1));if(++scenario==2){System.out.println("[vandorlabs][reprolab] actual-plate-hover PASS private build controller absence/presence and both redstone deployment modes");return true;}stage=0;ticks=0;}
        return false;
    }
    private static void prepare(Minecraft mc)throws Exception{
        final NBTTagCompound fixture;try(InputStream in=new FileInputStream(System.getProperty("vandorlabs.hoverFixture"))){fixture=CompressedStreamTools.readCompressed(in);}
        mc.getIntegratedServer().addScheduledTask(()->{
            try{
                WorldServer world=mc.getIntegratedServer().getWorld(0);EntityPlayerMP actor=player(mc);actor.dismountRidingEntity();
                for(BlockPos at:BlockPos.getAllInBox(ORIGIN.add(-16,-1,-16),ORIGIN.add(24,14,24))){world.getChunkFromBlockCoords(at);world.setBlockState(at,at.getY()==ORIGIN.getY()-1?net.minecraft.init.Blocks.STONE.getDefaultState():net.minecraft.init.Blocks.AIR.getDefaultState(),2);}
                int[] old=fixture.getIntArray("Origin");BlockPos delta=ORIGIN.subtract(new BlockPos(old[0],old[1],old[2])),seat=null;NBTTagList blocks=fixture.getTagList("Blocks",10);List<BlockPos> positions=new ArrayList<>();
                for(int i=0;i<blocks.tagCount();i++){NBTTagCompound cell=blocks.getCompoundTagAt(i);int[] xyz=cell.getIntArray("Pos");BlockPos pos=ORIGIN.add(xyz[0],xyz[1],xyz[2]);Block block=Block.getBlockFromName(cell.getString("Name"));require(block!=null,"fixture block available "+cell.getString("Name"));world.setBlockState(pos,block.getStateFromMeta(cell.getInteger("Meta")),2);positions.add(pos);}
                for(int i=0;i<blocks.tagCount();i++){NBTTagCompound cell=blocks.getCompoundTagAt(i);BlockPos pos=positions.get(i);TileEntity tile=world.getTileEntity(pos);if(tile!=null&&cell.hasKey("Tile",10))tile.readFromNBT(VehicleTileData.translated(cell.getCompoundTag("Tile"),delta));if(tile instanceof TileEntityCockpit){seat=pos;((TileEntityCockpit)tile).owner=actor.getUniqueID();}}
                require(seat!=null,"actual cockpit exported");actor.setPositionAndUpdate(seat.getX()+.5,seat.getY()+1,seat.getZ()+2);
                VehicleScan missing=new VehicleScan(world,seat);for(int i=0;i<1000&&!missing.step(256);i++);require(missing.error==null&&missing.result!=null,"actual source scan "+missing.error);require(!missing.result.hasDedicatedController(),"actual build lacks dedicated controller");
                boolean denied=false;try{VehicleTransfer.assemble(world,missing.result,missing.origin,actor);}catch(IllegalArgumentException expected){denied=expected.getMessage().equals("Vehicle Controller missing");}require(denied&&missing.result.matches(world,missing.origin),"missing controller refuses assembly without changing source blocks");
                BlockPos controller=null;attachment:for(BlockPos pos:positions)if(world.getBlockState(pos).getBlock()==com.vandorlabs.blocks.ModBlocks.PROGRAMMABLE_BLOCK)for(net.minecraft.util.EnumFacing side:net.minecraft.util.EnumFacing.values())if(world.isAirBlock(pos.offset(side))&&VehicleScan.touches(world,pos,world.getBlockState(pos),side)){controller=pos.offset(side);break attachment;}require(controller!=null,"isolated controller attachment available");world.setBlockState(controller,Block.getBlockFromName("vandorlabs:vehicle_controller").getDefaultState(),2);((TileEntityVehicleController)world.getTileEntity(controller)).owner=actor.getUniqueID();
                VehicleScan scan=new VehicleScan(world,seat);for(int i=0;i<1000&&!scan.step(256);i++);require(scan.error==null&&scan.result!=null&&scan.result.hasDedicatedController(),"same build finds added controller "+scan.error);
                for(BlockPos pos:positions)require(scan.result.at(pos.subtract(scan.origin))!=null,"every attached component captured: "+world.getBlockState(pos)+" at "+pos);
                EntityGroundVehicle craft=VehicleTransfer.assemble(world,scan.result,scan.origin,actor);require(craft.hover&&craft.structure.hasNonWheeledGear,"plate-only assembly enters Hover directly");
                for(TileEntity raw:new ArrayList<>(craft.view.tiles.values()))if(raw instanceof GearTileEntity&&((GearTileEntity)raw).isController()){GearTileEntity gear=(GearTileEntity)raw;NBTTagCompound settings=gear.settings();settings.setInteger("Trigger",scenario+1);gear.configure(settings);}
                id=craft.getUniqueID();
            }catch(Exception error){throw new IllegalStateException("Actual plate Hover reproduction",error);}
        }).get();
    }
    private static String blockers(EntityGroundVehicle craft){String result="";for(net.minecraft.util.math.AxisAlignedBB local:craft.collision.boxes){AxisAlignedBB body=local.offset(craft.posX,craft.posY,craft.posZ);for(AxisAlignedBB obstacle:craft.world.getCollisionBoxes(craft,body.expand(0,.18,0))){if(obstacle.calculateYOffset(body,.18)<.179){BlockPos at=new BlockPos((obstacle.minX+obstacle.maxX)/2,(obstacle.minY+obstacle.maxY)/2,(obstacle.minZ+obstacle.maxZ)/2);result+=" hull="+body+" obstacle="+obstacle+" state="+craft.world.getBlockState(at);if(result.length()>2500)return result;}}}return result;}
    private static String diagnostic(EntityGroundVehicle craft){try{String result="";for(String name:new String[]{"vertical","verticalAt","lastInput","forward","brake"}){java.lang.reflect.Field f=EntityGroundVehicle.class.getDeclaredField(name);f.setAccessible(true);result+=name+"="+f.get(craft)+",";}return result+"time="+craft.world.getTotalWorldTime();}catch(Exception error){return error.toString();}}
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("Actual plate Hover: "+message);}
}
