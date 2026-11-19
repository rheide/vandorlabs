package com.vandorlabs.gear;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** One ordinary Core formation/arm activation path per existing payload mode, with real disk journals. */
@net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
public final class ArmCoreLiveChecks {
    private static final java.lang.reflect.Field TELEPORT=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(net.minecraft.network.NetHandlerPlayServer.class,"targetPos","field_184362_y");
    private static final BlockPos ROOT=new BlockPos(2735,110,240);
    private static final ResourceLocation CAPS=ArmCoreFixture.CAPS;
    private static int profile,stage,ticks,checks,prepared;private static boolean clicked;private static Vec3d camera,aim;private static BlockPos mineCell;private static NBTTagCompound captured;private static BlockPos neighbour;private static String serverState="not polled";
    private static EnumFacing mount(){return profile==1?EnumFacing.EAST:EnumFacing.UP;}
    private static GearBlock block(){return profile==0?GearRegistry.GEAR_EXTENDER:GearRegistry.GEAR_SWING_ARM;}
    private static void need(boolean ok,String why){checks++;if(!ok)throw new IllegalStateException("Core arm profile="+profile+" stage="+stage+" ticks="+ticks+": "+why);}
    private static GearTileEntity root(WorldServer w){TileEntity raw=w.getTileEntity(ROOT);need(raw instanceof GearTileEntity,"controller installed");return (GearTileEntity)raw;}
    private static void location(net.minecraft.entity.player.EntityPlayerMP p,Vec3d eyes,Vec3d target){Vec3d d=target.subtract(eyes);p.connection.setPlayerLocation(eyes.x,eyes.y-p.getEyeHeight(),eyes.z,(float)Math.toDegrees(Math.atan2(-d.x,d.z)),(float)-Math.toDegrees(Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z))));camera=new Vec3d(eyes.x,eyes.y-p.getEyeHeight(),eyes.z);aim=target;p.motionX=p.motionY=p.motionZ=0;}
    private static void rootLocation(net.minecraft.entity.player.EntityPlayerMP p){location(p,new Vec3d(ROOT).addVector(.5,.5,profile==1?4.5:-3.5),new Vec3d(ROOT).addVector(.5,.5,.5));}
    private static boolean synced(Minecraft mc){return mc.playerController.getCurrentGameType()==GameType.CREATIVE&&mc.player.inventory.currentItem==0&&mc.player.getHeldItemMainhand().isEmpty()&&camera!=null&&mc.player.getDistanceSq(camera.x,camera.y,camera.z)<.04;}
    private static boolean teleportPending(net.minecraft.entity.player.EntityPlayerMP player){try{return TELEPORT.get(player.connection)!=null;}catch(IllegalAccessException failure){throw new IllegalStateException("Teleport readiness probe",failure);}}
    private static boolean readyToUse(Minecraft mc)throws Exception{
        TileEntity client=mc.world.getTileEntity(ROOT);if(!(client instanceof GearTileEntity)||mc.world.getBlockState(ROOT).getBlock()!=block()||((GearTileEntity)client).mountDirection()!=mount()||((GearTileEntity)client).progress()!=(stage==1?1:0))return false;
        return mc.getIntegratedServer().callFromMainThread(()->{net.minecraft.entity.player.EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);return !teleportPending(p)&&p.interactionManager.getGameType()==GameType.CREATIVE&&p.inventory.currentItem==0&&p.getHeldItemMainhand().isEmpty()&&p.getDistanceSq(camera.x,camera.y,camera.z)<.0001;}).get();
    }
    private static void ownership(GearTileEntity arm){ArmPayload p=arm.payload();need(p!=null&&p.blocks.cells.size()==27,"all27 reservations follow contacted Part");ArmCorePayload.validate(p.blocks);int owners=0;for(VehicleStructure.Cell c:p.blocks.cells){need(c.tileData().getCompoundTag("ForgeCaps").getCompoundTag(CAPS.toString()).getInteger("Representation")==22,"original opaque physical capability retained");if(c.state.getBlock() instanceof BlockConstructorCore){owners++;need(new ItemStack(c.tileData().getCompoundTag("InsertedToken")).getItem()==com.vandorlabs.items.ModItems.DATA_CHIP,"one inserted chip retained");}}need(owners==1,"one complete formed owner");}
    public static boolean tick(Minecraft mc)throws Exception{
        if("true".equals(System.getenv("VANDOR_LABS_OBSERVER_ARM_POWER_ONLY")))return ObserverArmPowerChecks.tick(mc);
        if(Boolean.getBoolean("vandorlabs.armCoreGapOnly"))return ArmCoreGapLiveChecks.tick(mc);
        if(Boolean.getBoolean("vandorlabs.armCoreUseOnly"))return ArmCoreUseLiveChecks.tick(mc);
        if(profile==3){System.out.println("[vandorlabs] arm-live PASS arm-core checks="+checks+" ordinary placement contacted Part wholeCore Extender Rotating both modes native yaw edits captured mining/use refusal opaque capabilities async journals reload reversal");return true;}
        ArmCoreFixture.enableCaps();mc.displayGuiScreen(null);need(++ticks<700,"bounded motion mode="+mc.playerController.getCurrentGameType()+" slot="+mc.player.inventory.currentItem+" held="+mc.player.getHeldItemMainhand()+" pos="+mc.player.getPositionVector()+" camera="+camera+" synced="+synced(mc)+" clicked="+clicked+" server="+serverState);
        if((stage==1||stage==5)&&!clicked&&synced(mc)&&readyToUse(mc)){mc.player.connection.sendPacket(new net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock(ROOT,mount(),EnumHand.MAIN_HAND,.5F,.5F,.5F));clicked=true;}
        if(stage==3&&synced(mc)&&readyToUse(mc)&&mc.objectMouseOver!=null&&mc.objectMouseOver.hitInfo instanceof ArmPayloadHarvest.Target){ArmPayloadHarvest.Target target=(ArmPayloadHarvest.Target)mc.objectMouseOver.hitInfo;if(target.root.equals(ROOT)&&target.cell.equals(mineCell)){javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new java.io.File(System.getProperty("vandorlabs.reprolab"),"arm_core_captured.png"));mc.playerController.clickBlock(ROOT,mc.objectMouseOver.sideHit);stage=4;ticks=0;}}
        return mc.getIntegratedServer().callFromMainThread(()->run(mc.getIntegratedServer().getWorld(0),mc.getIntegratedServer().getPlayerList().getPlayers().get(0))).get();
    }
    private static boolean run(WorldServer w,net.minecraft.entity.player.EntityPlayerMP player){
        if(stage==0){
            neighbour=ArmCoreFixture.setup(w,player,profile);prepared=0;ArmTransactions.testFault=point->{if(point.equals("prepared")){need(w.getMinecraftServer().isCallingFromMinecraftThread(),"journal preparation callback on server thread");prepared++;}};
            rootLocation(player);player.inventoryContainer.detectAndSendChanges();stage=1;ticks=0;clicked=false;return false;
        }
        GearTileEntity arm=root(w);boolean locked=ArmTransactions.locked(w,ROOT);serverState="progress="+arm.progress()+" target="+arm.target()+" lock="+locked+" teleportPending="+teleportPending(player)+" payload="+(arm.payload()==null?"none":arm.payload().materialized()?"native":"captured")+" error="+arm.error()+" pos="+player.getPositionVector()+" mode="+player.interactionManager.getGameType()+" held="+player.getHeldItemMainhand();if(ticks%100==0)System.out.println("[vandorlabs] arm-core poll profile="+profile+" stage="+stage+" clicked="+clicked+" "+serverState);if(locked)return false;need(ticks<250||arm.error().isEmpty(),"motion error="+arm.error());
        if(stage==1){if(!clicked||arm.progress()!=0||arm.payload()==null)return false;ownership(arm);need(arm.payload().materialized()==(profile!=2),"supported native versus fractional endpoint");NBTTagCompound saved=arm.writeToNBT(new NBTTagCompound());arm.readFromNBT(saved);arm.operator(player);ownership(arm);
            if(arm.payload().materialized()){TileEntityConstructor nativeCore=null;for(BlockPos at:arm.payload().placedCells(arm))if(w.getTileEntity(at) instanceof TileEntityConstructor)nativeCore=(TileEntityConstructor)w.getTileEntity(at);need(nativeCore!=null,"one native Core at grid endpoint");nativeCore.cycleActivity();rootLocation(player);stage=5;clicked=false;ticks=0;return false;}
            captured=arm.payload().write();VehicleWorld view=arm.payload().view(arm);for(TileEntity t:view.tiles.values())if(t instanceof TileEntityConstructor){BlockPos at=t.getPos();((TileEntityConstructor)t).core().onBlockActivated(view,at,view.getBlockState(at),player,EnumHand.MAIN_HAND,EnumFacing.NORTH,.5F,.5F,.5F);need(captured.equals(arm.payload().write()),"captured activation cannot alter saved Core or chip");}
            for(VehicleStructure.Cell cell:arm.payload().blocks.cells){Vec3d center=arm.payload().transform(arm,0,cell.pos.getX()+.5,cell.pos.getY()+.5,cell.pos.getZ()+.5).add(new Vec3d(ROOT));for(EnumFacing side:EnumFacing.values()){Vec3d eyes=center.add(new Vec3d(side.getDirectionVec()).scale(3));RayTraceResult hit=GearInteractions.trace(w,eyes,center);if(hit!=null&&hit.hitInfo instanceof ArmPayloadHarvest.Target){mineCell=((ArmPayloadHarvest.Target)hit.hitInfo).cell;location(player,eyes,hit.hitVec);stage=3;ticks=0;return false;}}}throw new IllegalStateException("No actual captured Core ray");
        }

        if(stage==4){if(ticks<12)return false;need(captured.equals(arm.payload().write()),"actual captured mining packet refuses partial dismantle");ownership(arm);rootLocation(player);stage=5;clicked=false;ticks=0;return false;}
        if(stage==5){need(w.getBlockState(neighbour).getBlock()==Blocks.COBBLESTONE,"adjacent decoration not captured");if(!clicked||arm.progress()!=1||arm.payload()==null||!arm.payload().materialized())return false;ownership(arm);BlockPos owner=null;for(BlockPos at:arm.payload().placedCells(arm))if(w.getTileEntity(at) instanceof TileEntityConstructor)owner=at;need(owner!=null&&((TileEntityConstructor)w.getTileEntity(owner)).formed(),"return restores complete native Core");need(prepared>0,"real asynchronous journal reached durable preparation");need(((TileEntityConstructor)w.getTileEntity(owner)).getStackInSlot(0).getItem()==com.vandorlabs.items.ModItems.DATA_CHIP,"native chip exactly once");if(profile==2){BlockPos part=null;for(BlockPos at:arm.payload().placedCells(arm))if(w.getTileEntity(at) instanceof TileEntityConstructorPart){part=at;break;}need(part!=null&&player.interactionManager.tryHarvestBlock(part),"ordinary native Part mining dismantles complete machine");need(!((TileEntityConstructor)w.getTileEntity(owner)).formed()&&((TileEntityConstructor)w.getTileEntity(owner)).getStackInSlot(0).isEmpty(),"native dismantle returns chip and clears formed owner");int chips=0;for(net.minecraft.entity.item.EntityItem item:w.getEntitiesWithinAABB(net.minecraft.entity.item.EntityItem.class,new AxisAlignedBB(ROOT).grow(8)))if(item.getItem().getItem()==com.vandorlabs.items.ModItems.DATA_CHIP)chips+=item.getItem().getCount();need(chips==1,"native dismantle drops inserted chip once");}
            ArmTransactions.testFault=null;System.out.println("[vandorlabs] arm-core case PASS profile="+profile+" native="+(profile!=2)+" cells=27 journals=real");profile++;stage=0;ticks=0;return false;}
        return false;
    }
    private ArmCoreLiveChecks(){}
}
