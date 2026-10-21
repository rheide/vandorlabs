package com.vandorlabs.client;

import com.vandorlabs.tiles.TileEntityCockpit;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import java.io.File;

final class CockpitAnimationDocumentation {
    static void run(Minecraft mc,File output)throws Exception{
        TileEntityCockpit tile=(TileEntityCockpit)mc.world.getTileEntity(new BlockPos(222,30,128));NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());
        try{for(String mechanism:new String[]{"ScreenLift","SeatSlide"}){
            DocumentationRenderCapture.capture(output,"cockpit-"+(mechanism.equals("ScreenLift")?"screens":"seat"),80,2.15,28,-28,1,.65,.1,frame->{
                NBTTagCompound tag=saved.copy();tag.setInteger("CockpitSideTexture",-1);NBTTagCompound motion=new NBTTagCompound();boolean returning=frame>=40;motion.setBoolean("Target",!returning);motion.setDouble("From",returning?1:0);motion.setLong("Started",mc.world.getTotalWorldTime()-Math.max(0,(frame%40)-5));tag.setTag("Cockpit"+mechanism,motion);tile.readFromNBT(tag);new TECockpit().render(tile,0,0,0,0,-1,1);
            });
        }}finally{tile.readFromNBT(saved);}
        TileEntityCockpit chair=(TileEntityCockpit)mc.world.getTileEntity(new BlockPos(228,30,128));NBTTagCompound chairSaved=chair.writeToNBT(new NBTTagCompound());
        try{DocumentationRenderCapture.capture(output,"advanced-chair-height",64,1.35,20,145,.5,.65,.5,frame->{chair.readFromNBT(chairSaved);chair.setSideTexture(-1);chair.setHeight(new int[]{0,1,2,3,2,1,0,0}[frame/8]);new TECockpit().render(chair,0,0,0,0,-1,1);});}finally{chair.readFromNBT(chairSaved);}
    }
}
