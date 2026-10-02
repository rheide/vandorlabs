package com.vandorlabs.tiles;

import net.minecraft.block.Block;
import net.minecraft.item.*;
import java.util.zip.CRC32;

/** Stable registry-name and metadata identities for copied block artwork. */
public final class CustomBlockMaterials {
    public static final int ID_BASE=0x20000000;
    private CustomBlockMaterials(){ }
    public static boolean isCustom(int id){return id>=ID_BASE && id<FilesystemTextures.ID_BASE;}
    public static int identifier(Block block,int metadata) {
        CRC32 crc=new CRC32();try{crc.update((block.getRegistryName()+"@"+metadata).getBytes("UTF-8"));}catch(java.io.UnsupportedEncodingException e){throw new AssertionError(e);}
        return ID_BASE | ((int)crc.getValue() & 0x1fffffff);
    }
    public static Block block(ItemStack stack) {
        if(stack.isEmpty())return null;
        if(stack.getItem() instanceof ItemBlock)return ((ItemBlock)stack.getItem()).getBlock();
        if(stack.getItem() instanceof ItemDoor)try{return (Block)net.minecraftforge.fml.relauncher.ReflectionHelper.findField(ItemDoor.class,"block","field_179236_a").get(stack.getItem());}catch(ReflectiveOperationException e){return null;}
        return null;
    }
    public static int choice(ItemStack stack){
        Block block=block(stack);if(block==null)return -1;
        if(block.getRegistryName()!=null && "vandorlabs".equals(block.getRegistryName().getResourceDomain())) {
            net.minecraft.nbt.NBTTagCompound tag=stack.getSubCompound("BlockEntityTag");
            if(tag!=null)for(String key:new String[]{"LightFaceTexture","DoorFaceTexture","PropulsionSideTexture","housingTexture"})
                if(tag.hasKey(key,3) && tag.getInteger(key)>=0)return ScreenHousingTextures.clamp(tag.getInteger(key));
            if(block instanceof com.vandorlabs.blocks.BlockProgrammableLight)return ScreenHousingTextures.lightIndex(tag==null?0:tag.getInteger("LightTexture"));
            if(block instanceof com.vandorlabs.blocks.BlockConfigurableSpaceDoor)return ScreenHousingTextures.doorIndex(tag==null?2:tag.hasKey("SpaceDoorDesign",3)?tag.getInteger("SpaceDoorDesign"):2,tag==null?1:tag.hasKey("SpaceDoorDetail",3)?tag.getInteger("SpaceDoorDetail"):1);
            if(block instanceof com.vandorlabs.blocks.BlockAnimatedScreenSelector || block instanceof com.vandorlabs.blocks.BlockProgrammableTrapdoor)return 0;
        }
        return identifier(block,stack.getItem() instanceof ItemDoor?0:stack.getMetadata() & 15);
    }
}
