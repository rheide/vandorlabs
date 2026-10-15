package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityPilotSeat;
import com.vandorlabs.vehicle.*;
import com.vandorlabs.items.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import java.util.*;

/** Actual captured-seat sneak-use and setting packets while parking is unavailable. */
final class CapturedPilotInspectionChecks {
    private static final BlockPos BASE=new BlockPos(40,112,8),SEAT=new BlockPos(2,1,1);
    private static final BlockPos OTHER=SEAT.east(3);
    private static BlockPos selected(){return scenario%2==0?SEAT:OTHER;}
    private static UUID id,initialToken;private static int state,ticks,scenario;private static boolean finished;
    private static void require(boolean ok,String what){if(!ok)throw new IllegalStateException("captured pilot inspection: "+what);}
    private static EntityPlayerMP player(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayers().get(0);}
    private static EntityGroundVehicle server(Minecraft mc){return (EntityGroundVehicle)mc.getIntegratedServer().getWorld(0).getEntityFromUuid(id);}
    private static EntityGroundVehicle client(Minecraft mc){for(EntityGroundVehicle craft:VehicleLookup.loaded(mc.world))if(craft.getUniqueID().equals(id))return craft;return null;}
    private static Object field(Object object,String name)throws Exception{java.lang.reflect.Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}
    private static VehicleNetwork.Packet header(Minecraft mc)throws Exception{return (VehicleNetwork.Packet)field(field(mc.currentScreen,"data"),"header");}
    private static void setup(Minecraft mc)throws Exception{mc.getIntegratedServer().addScheduledTask(()->{
        WorldServer world=mc.getIntegratedServer().getWorld(0);EntityPlayerMP player=player(mc);player.setSneaking(false);player.dismountRidingEntity();player.setHeldItem(EnumHand.MAIN_HAND,scenario==3?new ItemStack(ModItems.CONFIGURIZER):ItemStack.EMPTY);
        for(BlockPos at:BlockPos.getAllInBox(BASE.add(-5,-1,-5),BASE.add(10,6,10)))world.setBlockToAir(at);
        for(BlockPos at:BlockPos.getAllInBox(BASE.add(-5,-1,-5),BASE.add(10,-1,10)))world.setBlockState(at,Blocks.STONE.getDefaultState(),2);
        List<VehicleStructure.Cell> cells=new ArrayList<>();
        for(BlockPos at:new BlockPos[]{SEAT,OTHER}){TileEntityPilotSeat seat=new TileEntityPilotSeat();seat.setPos(at);seat.owner=player.getUniqueID();seat.setTaxiMode(false);
            cells.add(new VehicleStructure.Cell(at,ModBlocks.PILOT_SEAT.getDefaultState(),seat.writeToNBT(new NBTTagCompound())));cells.add(new VehicleStructure.Cell(at.up(),ModBlocks.PILOT_SEAT.getDefaultState().withProperty(BlockPilotSeat.UPPER,true),null));}
        String[] lifts={"antigravity_plate","repulsor_array","vertical_hover_thruster"};for(int i=0;i<3;i++)cells.add(new VehicleStructure.Cell(new BlockPos(i,0,2),net.minecraft.block.Block.getBlockFromName("vandorlabs:"+lifts[i]).getDefaultState(),null));
        cells.add(new VehicleStructure.Cell(new BlockPos(4,0,2),net.minecraft.block.Block.getBlockFromName("vandorlabs:impulse_engine").getDefaultState(),null));
        EntityGroundVehicle craft=new EntityGroundVehicle(world);craft.install(new VehicleStructure(cells,SEAT,EnumFacing.NORTH,3));craft.rotationYaw=scenario==1?37:scenario==0||scenario==3?90:0;craft.setPosition(BASE.getX(),BASE.getY(),BASE.getZ());craft.hover=scenario==0||scenario==3;craft.hoverTarget=craft.posY;require(world.spawnEntity(craft),"fixture spawned");id=craft.getUniqueID();
        if(scenario==2)world.setBlockState(BASE.add(4,0,2),Blocks.IRON_BLOCK.getDefaultState(),2);
        player.capabilities.allowFlying=true;player.capabilities.isFlying=true;player.sendPlayerAbilities();player.setPositionAndUpdate(BASE.getX()-2,BASE.getY()+2,BASE.getZ()-2);
    }).get();}
    private static void click(Minecraft mc,EntityGroundVehicle craft)throws Exception{
        boolean sneak=scenario!=3;mc.player.setSneaking(sneak);mc.player.connection.sendPacket(new net.minecraft.network.play.client.CPacketEntityAction(mc.player,sneak?net.minecraft.network.play.client.CPacketEntityAction.Action.START_SNEAKING:net.minecraft.network.play.client.CPacketEntityAction.Action.STOP_SNEAKING));
        Vec3d eye=craft.toWorld(new Vec3d(selected()).addVector(.5,1.2,-2)),aim=craft.toWorld(new Vec3d(selected()).addVector(.5,1.2,.5)),delta=aim.subtract(eye);
        float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));mc.player.setPositionAndRotation(eye.x,eye.y-mc.player.getEyeHeight(),eye.z,yaw,0);mc.player.connection.sendPacket(new net.minecraft.network.play.client.CPacketPlayer.PositionRotation(mc.player.posX,mc.player.posY,mc.player.posZ,yaw,0,false));
        VehicleInteraction.Target target=VehicleInteraction.pointed(mc.player);require(target!=null && target.craft==craft && target.hit.action==VehicleSelection.Action.BOARD && target.hit.cell.pos.equals(selected().up()),"actual selected upper seat reached before click scenario="+scenario);require(VehicleInteraction.supported(mc.player,target),"captured sneak-seat accepted by client interaction filter");
        mc.objectMouseOver=new RayTraceResult(craft,aim);net.minecraftforge.fml.relauncher.ReflectionHelper.findMethod(Minecraft.class,"rightClickMouse","func_147121_ag").invoke(mc);
    }
    static boolean tick(Minecraft mc)throws Exception{
        if(finished)return true;require(++ticks<400,"timeout state="+state+" scenario="+scenario);
        if(state==0){mc.displayGuiScreen(null);setup(mc);state=1;ticks=0;return false;}
        if(state==1 && ticks>=30){EntityGroundVehicle craft=client(mc);if(craft==null||craft.structure==null)return false;click(mc,craft);state=2;ticks=0;return false;}
        if(state==2 && ticks>=30){
            java.util.List<GuiButton> controls=VehicleTaxiRuntimeChecks.previewLayout(mc);require(controls.stream().noneMatch(button->button.id==0),"seat panel contains no controller conversion action");
            VehicleNetwork.Packet header=header(mc);require(header.forward==0,"seat settings metadata");VehicleStructure shown=(VehicleStructure)field(field(mc.currentScreen,"data"),"structure");require(shown.seat.equals(BlockPos.ORIGIN)&&shown.cells.size()==1,"seat panel payload contains only selected seat normalized to origin");initialToken=header.token;
            require(controls.size()==2&&controls.stream().allMatch(button->button.id==1||button.id==5),"selected seat exposes Height and Done only");
            VehicleTaxiRuntimeChecks.button(mc,5);state=3;ticks=0;return false;
        }
        if(state==3 && ticks>=25){final UUID currentToken=header(mc).token;mc.getIntegratedServer().addScheduledTask(()->{EntityGroundVehicle craft=server(mc);TileEntityPilotSeat seat=(TileEntityPilotSeat)craft.view.getTileEntity(selected());require(seat.getHeight()==1,"canonical selected seat height applied in unavailable parking pose");BlockPos other=selected().equals(SEAT)?OTHER:SEAT;require(((TileEntityPilotSeat)craft.view.getTileEntity(other)).getHeight()==0,"other pilot height stays independent");require(!craft.isDead,"height settings preserve captured craft");protection(mc,currentToken,UUID.randomUUID());}).get();state=6;ticks=0;return false;}
        if(state==6 && ticks>=15){require(header(mc).forward==0,"child ACK preserves seat-only panel identity");java.io.File output=new java.io.File(System.getProperty("vandorlabs.reprolab"));VehicleTaxiRuntimeChecks.screenshot(mc,output,"shot_captured_pilot_settings_only_"+scenario+".png");VehicleTaxiRuntimeChecks.button(mc,1);state=7;ticks=0;return false;}
        if(state==7 && ticks>=15){require(mc.currentScreen==null,"Done closes captured height dialog");mc.getIntegratedServer().addScheduledTask(()->{server(mc).setDead();player(mc).setSneaking(false);}).get();scenario++;if(scenario<4){state=0;ticks=0;return false;}finished=true;System.out.println("[vandorlabs][reprolab] captured-pilot-inspection PASS real-barehand-shift upper-seat Hover noncardinal blocked-parking Configurizer height-only independent-selected-seats screenshots");return true;}
        return false;
    }
    private static void protection(Minecraft mc,UUID current,UUID stale){
        EntityGroundVehicle craft=server(mc);EntityPlayerMP player=player(mc);NBTTagCompound before=craft.structure.write();
        VehicleNetwork.Packet packet=new VehicleNetwork.Packet(VehicleNetwork.HEIGHT);packet.entity=craft.getEntityId();packet.token=stale;packet.origin=BlockPos.ORIGIN;packet.strafe=3;VehicleService.configureHeight(player,packet);
        packet.token=current;packet.origin=BlockPos.ORIGIN.up();VehicleService.configureHeight(player,packet);packet.origin=BlockPos.ORIGIN;
        boolean blocked=craft.ownershipBlocked;craft.ownershipBlocked=true;try{VehicleService.configureHeight(player,packet);}finally{craft.ownershipBlocked=blocked;}
        boolean dead=craft.isDead;craft.isDead=true;try{VehicleService.configureHeight(player,packet);}finally{craft.isDead=dead;}
        double x=player.posX,y=player.posY,z=player.posZ;player.setPosition(x+100,y,z);try{VehicleService.configureHeight(player,packet);}finally{player.setPosition(x,y,z);}
        com.vandorlabs.redstone.ChannelList[] modes={com.vandorlabs.redstone.ChannelList.of(19999),com.vandorlabs.redstone.ChannelList.EMPTY,com.vandorlabs.redstone.ChannelList.EMPTY};VehicleService.configureModeChannels(player,current,craft.getEntityId(),BlockPos.ORIGIN.up(),modes);
        require(before.equals(craft.structure.write()),"stale/forged/ownership-blocked/dead/out-of-reach edits preserve exact captured snapshot");
    }
    private CapturedPilotInspectionChecks(){}
}
