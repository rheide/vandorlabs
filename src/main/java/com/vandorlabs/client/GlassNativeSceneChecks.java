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
    private static final int[] ANGLES={94,110,125};
    private static int stage,frames,angle;
    private static List<VehicleStructure.Cell> cells;
    static boolean tick(Minecraft mc,File output)throws Exception{
        if(stage==3)return true;
        mc.gameSettings.pauseOnLostFocus=false;mc.player.closeScreen();mc.playerController.setGameType(net.minecraft.world.GameType.CREATIVE);
        if(stage==0){
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
        for(VehicleStructure.Cell c:cells)if(mc.world.getBlockState(BASE.add(c.pos)).getBlock()!=c.state.getBlock()||mc.world.getTileEntity(BASE.add(c.pos))==null)return false;
        mc.player.rotationYaw=mc.player.prevRotationYaw=ANGLES[angle];mc.player.rotationPitch=mc.player.prevRotationPitch=0;
        GlazingRenderOrder.begin(mc.world);try{mc.entityRenderer.updateCameraAndRender(1,System.nanoTime());}finally{GlazingRenderOrder.end();}mc.renderGlobal.updateChunks(System.nanoTime()+50000000L);
        if(frames<40)return false;
        ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"glass-display-order_native_"+ANGLES[angle]+".png"));
        if(++angle<ANGLES.length){frames=0;return false;}
        stage=3;System.out.println("[vandorlabs] glass-native-scene PASS saved panes actual chunk/TESR world frames camera=94/0 neighboring angle=110,125");return true;
    }
    private GlassNativeSceneChecks(){}
}
