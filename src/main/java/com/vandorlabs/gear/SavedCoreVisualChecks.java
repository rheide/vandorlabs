package com.vandorlabs.gear;

import com.vandorlabs.gear.client.ArmCoreTransitionObserver;
import java.io.File;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** Read-only saved test4 folded Oculus witness; retain all terrain, settings and captured ownership. */
@net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
public final class SavedCoreVisualChecks {
    private static final BlockPos ROOT=new BlockPos(-73,83,145);
    private static final java.lang.reflect.Field TELEPORT=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(net.minecraft.network.NetHandlerPlayServer.class,"targetPos","field_184362_y");
    private static int stage,ticks,checks,startFrame,startDraws,closeDraws,arc=-1,startMissing;private static boolean finished,terrainClear;private static float heading=Float.NaN;private static Vec3d feet,center;private static NBTTagCompound original;
    private static void need(boolean ok,String why){checks++;if(!ok)throw new IllegalStateException("saved-core-visual stage="+stage+" ticks="+ticks+": "+why+" "+ArmCoreTransitionObserver.visibility());}
    private static GearTileEntity root(World world){TileEntity raw=world.getTileEntity(ROOT);need(raw instanceof GearTileEntity,"saved physical controller exists");return (GearTileEntity)raw;}
    private static boolean pending(EntityPlayerMP p){try{return TELEPORT.get(p.connection)!=null;}catch(IllegalAccessException error){throw new IllegalStateException(error);}}
    private static void location(EntityPlayerMP actor,Vec3d next){feet=next;Vec3d eye=next.addVector(0,actor.getEyeHeight(),0),d=center.subtract(eye);actor.connection.setPlayerLocation(next.x,next.y,next.z,Float.isNaN(heading)?(float)Math.toDegrees(Math.atan2(-d.x,d.z)):heading,Float.isNaN(heading)?(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))):0);actor.motionX=actor.motionY=actor.motionZ=0;terrainClear=actor.world.rayTraceBlocks(eye,center,false,true,false)==null;}
    private static boolean ready(Minecraft mc)throws Exception{TileEntity raw=mc.world.getTileEntity(ROOT);if(!(raw instanceof GearTileEntity))return false;GearTileEntity client=(GearTileEntity)raw;if(client.payload()==null||client.payload().blocks.cells.size()!=27||client.mode()!=com.vandorlabs.gear.core.GearMotion.Mode.ROTATE||client.progress()!=0||!client.isOperational())return false;return feet!=null&&mc.playerController.getCurrentGameType()==GameType.CREATIVE&&mc.player.inventory.currentItem==0&&mc.player.getHeldItemMainhand().isEmpty()&&mc.player.capabilities.isFlying&&mc.player.getDistanceSq(feet.x,feet.y,feet.z)<.0001&&mc.getIntegratedServer().callFromMainThread(()->{EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);return !pending(p)&&p.interactionManager.getGameType()==GameType.CREATIVE&&p.inventory.currentItem==0&&p.getHeldItemMainhand().isEmpty()&&p.getDistanceSq(feet.x,feet.y,feet.z)<.0001;}).get();}
    public static boolean tick(Minecraft mc,File output)throws Exception{
        if(finished)return true;mc.gameSettings.pauseOnLostFocus=false;mc.gameSettings.viewBobbing=false;mc.gameSettings.fovSetting=arc<6?70:90;mc.displayGuiScreen(null);if(++ticks<70)return false;need(ticks<1200,"bounded normal scene/camera setup client="+mc.player.getPositionVector());
        if(stage==5)return finish();
        if(stage==0){
            mc.getIntegratedServer().callFromMainThread(()->{WorldServer w=mc.getIntegratedServer().getWorld(0);for(int x=-6;x<=-4;x++)for(int z=8;z<=9;z++)w.getChunkFromChunkCoords(x,z);GearTileEntity arm=root(w);need(arm.progress()==0&&arm.mode()==com.vandorlabs.gear.core.GearMotion.Mode.ROTATE&&arm.mountDirection()==EnumFacing.UP&&arm.hingeMode()==com.vandorlabs.gear.core.ArmHinge.Mode.DOUBLE&&arm.direction()==com.vandorlabs.gear.core.GearMotion.Direction.SOUTH&&arm.kinkDirection()==com.vandorlabs.gear.core.GearMotion.Direction.SOUTH,"exact saved ROTATE/UP/Double/SOUTH+SOUTH pose");need(arm.payload()!=null&&!arm.payload().materialized()&&arm.payload().blocks.cells.size()==27,"saved single captured Core27");ArmCorePayload.validate(arm.payload().blocks);original=arm.payload().write();AxisAlignedBB b=arm.payload().blocks.bounds;center=arm.payload().transform(arm,0,(b.minX+b.maxX)/2,(b.minY+b.maxY)/2,(b.minZ+b.maxZ)/2).add(new Vec3d(ROOT));EntityPlayerMP p=w.getMinecraftServer().getPlayerList().getPlayers().get(0);p.setGameType(GameType.CREATIVE);p.setSneaking(false);p.capabilities.allowFlying=true;p.capabilities.isFlying=true;p.sendPlayerAbilities();p.inventory.currentItem=0;p.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);p.setHeldItem(EnumHand.OFF_HAND,ItemStack.EMPTY);p.inventoryContainer.detectAndSendChanges();p.connection.sendPacket(new net.minecraft.network.play.server.SPacketHeldItemChange(0));return null;}).get();
            Set<BlockPos> cells=mc.getIntegratedServer().callFromMainThread(()->{GearTileEntity arm=root(mc.getIntegratedServer().getWorld(0));return arm.payload().placedCells(arm);}).get();ArmCoreTransitionObserver.installMachine(output,ROOT,cells,null);ArmCoreTransitionObserver.start();
            mc.getIntegratedServer().callFromMainThread(()->{location(mc.getIntegratedServer().getPlayerList().getPlayers().get(0),new Vec3d(-75,83,139));return null;}).get();stage=1;return false;
        }
        if(stage==1||stage==3){if(!ready(mc)||!Float.isNaN(heading)&&Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees(mc.player.rotationYaw-heading))>.01)return false;startFrame=ArmCoreTransitionObserver.frames();startDraws=ArmCoreTransitionObserver.capturedDraws();startMissing=ArmCoreTransitionObserver.missingVisibleFrames();stage++;return false;}
        if(ArmCoreTransitionObserver.frames()-startFrame<6)return false;
        if(stage==2){need(ArmCoreTransitionObserver.payloadInFrustum(),"close ss3 camera actually frames the saved posed Core");closeDraws=ArmCoreTransitionObserver.capturedDraws()-startDraws;need(closeDraws>0,"positive actual Core geometry before screenshot heading arc");capture(mc,"saved-core-close.png");if(Boolean.getBoolean("vandorlabs.savedCoreAdmissionGuards"))ArmCoreTransitionObserver.checkAdmissionGuards(root(mc.world));nextArc(mc);return false;}
        need(stage==4,"finite screenshot heading arc");int draws=ArmCoreTransitionObserver.capturedDraws()-startDraws,missing=ArmCoreTransitionObserver.missingVisibleFrames()-startMissing;capture(mc,"saved-core-arc-"+arc+".png");System.out.println("[vandorlabs][saved-core-visual] arc="+arc+" yaw="+heading+" fov="+mc.gameSettings.fovSetting+" feet="+feet+" draws="+draws+" missing="+missing+" terrainClear="+terrainClear+" "+ArmCoreTransitionObserver.visibility());
        mc.getIntegratedServer().callFromMainThread(()->{GearTileEntity arm=root(mc.getIntegratedServer().getWorld(0));need(arm.progress()==0&&!arm.target()&&original.equals(arm.payload().write()),"saved captured ownership/settings unchanged during camera-only observation");return null;}).get();
        if(terrainClear&&missing>=2){ArmCoreTransitionObserver.finish();need(false,"actual viewport-clipped Core BODY disappeared across "+missing+" frames at screenshot heading arc="+arc+" yaw="+heading+" draws="+draws);}
        need(ArmCoreTransitionObserver.duplicateOwnerFrames()==0,"one actual main-frame captured Core owner regardless root/fallback admission order");
        if(arc<11){nextArc(mc);return false;}return finish();
    }
    private static void capture(Minecraft mc,String name)throws Exception{javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(mc.mcDataDir,name));}
    private static boolean finish()throws Exception{ArmCoreTransitionObserver.finish();finished=true;System.out.println("[vandorlabs][saved-core-visual] PASS checks="+checks+" actual BODY clipping screenshotN/NE/E70/90 close/back saved27/ROTATE/Double/noWorldMutation");return true;}
    private static void nextArc(Minecraft mc)throws Exception{
        while(++arc<12){heading=new float[]{180,225,270}[(arc%6)/2];boolean clear=mc.getIntegratedServer().callFromMainThread(()->{
            EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);double distance=(arc&1)==0?0:3,angle=Math.toRadians(heading);Vec3d target=new Vec3d(-75+distance*Math.sin(angle),83,139-distance*Math.cos(angle));AxisAlignedBB body=new AxisAlignedBB(target.x-.3,target.y,target.z-.3,target.x+.3,target.y+p.height,target.z+.3);List<AxisAlignedBB> collisions=p.world.getCollisionBoxes(p,body);
            if(!collisions.isEmpty()){System.out.println("[vandorlabs][saved-core-visual] occupied invented camera arc="+arc+" feet="+target+" body="+body+" collisions="+collisions.subList(0,Math.min(6,collisions.size()))+" count="+collisions.size());need(distance>0&&heading!=180,"reported feet and proven failing north/back camera must remain unskipped");return false;}
            location(p,target);return true;
        }).get();if(clear){stage=3;return;}}
        stage=5;
    }
    private SavedCoreVisualChecks(){}
}
