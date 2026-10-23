package com.vandorlabs.client;

import com.vandorlabs.tiles.*;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import java.io.File;

final class SecurityAnimationDocumentation {
    private static void inserted(NBTTagCompound tag,ItemStack token,int age,boolean returning,long now){
        tag.setBoolean("AnyToken",false);tag.setInteger("AllowedTokens",8);tag.setInteger("TokenBehavior",returning?3:0);tag.setLong("TokenInsertStarted",now-age);
        if(age>=0&&(!returning||age<20))tag.setTag("InsertedToken",token.writeToNBT(new NBTTagCompound()));else tag.removeTag("InsertedToken");
        if(returning&&age>=0&&age<20)tag.setUniqueId("TokenReturnTo",java.util.UUID.fromString("8afda66c-e56d-4c52-906b-273a2b853c3b"));else{tag.removeTag("TokenReturnToMost");tag.removeTag("TokenReturnToLeast");}
        tag.setLong("TokenPulseUntil",age>=8&&age<28?now+1:0);
    }
    static void run(Minecraft mc,File output)throws Exception{
        for(boolean chip:new boolean[]{false,true})for(boolean returning:new boolean[]{false,true}){
            TileEntityTokenSlot source=(TileEntityTokenSlot)mc.world.getTileEntity(new BlockPos(270,chip?34:32,142));NBTTagCompound saved=source.writeToNBT(new NBTTagCompound());
            DocumentationRenderCapture.capture(output,(chip?"chip":"card")+(returning?"-return-pulse":"-stay-inserted"),64,.9,15,155,.5,.65,.5,frame->{
                int age=frame<8?-1:frame-8;NBTTagCompound data=saved.copy();inserted(data,new ItemStack(chip?com.vandorlabs.items.ModItems.DATA_CHIP:com.vandorlabs.items.ModItems.ACCESS_CARD,1,3),age,returning,mc.world.getTotalWorldTime());if(!returning&&frame>=52)data.removeTag("InsertedToken");
                TileEntityTokenSlot tile=(TileEntityTokenSlot)net.minecraft.tileentity.TileEntity.create(mc.world,data);tile.setWorld(mc.world);TETokenSlot.frame(chip,EnumFacing.NORTH);new TETokenSlot().render(tile,0,0,0,0,-1,1);
            });
        }
        TileEntitySpaceDoor tile=(TileEntitySpaceDoor)mc.world.getTileEntity(new BlockPos(298,33,142));NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());
        try{for(boolean returning:new boolean[]{false,true})DocumentationRenderCapture.capture(output,"door-security-"+(returning?"pulse-return":"stay-inserted"),returning?150:90,1.9,14,155,.5,.95,.5,frame->{
            int age=frame<8?-1:frame-8;NBTTagCompound data=saved.copy();data.setInteger("DoorSecurityMode",returning?1:2);
            NBTTagCompound reader=data.getCompoundTag("DoorKeyReader");inserted(reader,new ItemStack(com.vandorlabs.items.ModItems.ACCESS_CARD,1,3),age,returning,mc.world.getTotalWorldTime());
            int close=returning?116:56;if(returning)reader.setLong("TokenPulseUntil",frame>=16&&frame<close?mc.world.getTotalWorldTime()+1:0);if(!returning&&frame>=close)reader.removeTag("InsertedToken");data.setTag("DoorKeyReader",reader);tile.readFromNBT(data);
            float open=(float)Math.max(0,Math.min(1,(frame-16)/9D));if(frame>=close)open=(float)Math.max(0,1-(frame-close)/9D);
            TESlidingDoor.renderSpaceDoor(tile,mc.world.getBlockState(tile.getPos()),EnumFacing.SOUTH,open,0,0,0);
        });}finally{tile.readFromNBT(saved);}
    }
}
