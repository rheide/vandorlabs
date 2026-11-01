package com.vandorlabs.client;

import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;
import com.vandorlabs.vehicle.VehicleStructure;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

/** Native chunk and TESR composition of the saved glazing arrangement, also under shaders. */
final class GlassNativeSceneChecks {
    private static final BlockPos BASE=new BlockPos(112,80,112);
    private static final float[] ANGLES={90.5F,94,110};
    private static int stage,frames,angle;
    private static long sortTotal,sortMax;private static int samples;
    private static List<VehicleStructure.Cell> cells;
    static boolean tick(Minecraft mc,File output)throws Exception{
        if(stage==3)return true;
        mc.gameSettings.pauseOnLostFocus=false;mc.player.closeScreen();mc.playerController.setGameType(net.minecraft.world.GameType.CREATIVE);
        if(stage==0){
            if(Boolean.getBoolean("vandorlabs.glassExistingScene")){
                cells=Collections.emptyList();
                mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());p.setGameType(net.minecraft.world.GameType.CREATIVE);p.capabilities.isFlying=false;p.sendPlayerAbilities();p.connection.setPlayerLocation(-133.562,83,200.623,90.5F,0);}).get();stage=1;return false;
            }
            cells=GlassDisplayOrderChecks.perpendicular(mc,false).structure.cells;
            mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());
                p.setGameType(net.minecraft.world.GameType.CREATIVE);p.capabilities.isFlying=true;p.sendPlayerAbilities();
                for(BlockPos at:BlockPos.getAllInBox(BASE.add(-1,0,-1),BASE.add(17,8,17)))p.world.setBlockToAir(at);
                for(VehicleStructure.Cell c:cells){BlockPos at=BASE.add(c.pos);p.world.setBlockState(at,c.state,3);if(c.hasTileData()){NBTTagCompound tag=c.tileData();tag.setInteger("x",at.getX());tag.setInteger("y",at.getY());tag.setInteger("z",at.getZ());p.world.removeTileEntity(at);TileEntity tile=TileEntity.create(p.world,tag);p.world.setTileEntity(at,tile);tile.onLoad();p.world.notifyBlockUpdate(at,c.state,c.state,3);}}
                p.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,net.minecraft.item.ItemStack.EMPTY);p.inventoryContainer.detectAndSendChanges();p.world.setWorldTime(6000);p.world.getWorldInfo().setRaining(false);
                p.connection.setPlayerLocation(BASE.getX()+GlassDisplayOrderChecks.CAMERA_X,BASE.getY()+3,BASE.getZ()+GlassDisplayOrderChecks.CAMERA_Z,110,0);
            }).get();stage=1;return false;
        }
        if(++frames>600)throw new IllegalStateException("Native glass scene did not settle stage="+stage+" player="+mc.player.getPosition());
        if(Boolean.getBoolean("vandorlabs.glassExistingScene")&&!(mc.world.getTileEntity(new BlockPos(-134,83,199)) instanceof com.vandorlabs.tiles.TileEntityProgrammableGlass))return false;
        for(VehicleStructure.Cell c:cells)if(mc.world.getBlockState(BASE.add(c.pos)).getBlock()!=c.state.getBlock()||mc.world.getTileEntity(BASE.add(c.pos))==null)return false;
        mc.player.rotationYaw=mc.player.prevRotationYaw=ANGLES[angle];mc.player.rotationPitch=mc.player.prevRotationPitch=0;
        GlazingRenderOrder.begin(mc.world);try{mc.entityRenderer.updateCameraAndRender(1,System.nanoTime());}finally{GlazingRenderOrder.end();}mc.renderGlobal.updateChunks(System.nanoTime()+50000000L);
        if(frames>10){sortTotal+=GlazingRenderOrder.sortNanos;sortMax=Math.max(sortMax,GlazingRenderOrder.sortNanos);samples++;}
        if(frames<40)return false;
        System.out.println("[vandorlabs] glazing-scene yaw="+ANGLES[angle]+" "+GlazingRenderOrder.diagnostics+" late="+GlazingRenderOrder.lateDraws+" sort-us-mean="+(sortTotal/Math.max(1,samples)/1000)+" max="+(sortMax/1000));
        ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"glass-display-order_native_"+ANGLES[angle]+".png"));
        if(++angle<ANGLES.length){frames=0;sortTotal=sortMax=0;samples=0;return false;}
        stage=3;System.out.println("[vandorlabs] glass-native-scene "+(Boolean.getBoolean("vandorlabs.glassExistingScene")?"CAPTURE":"PASS")+" native chunk/TESR composition camera angles="+Arrays.toString(ANGLES));return true;
    }
    private GlassNativeSceneChecks(){}
}
