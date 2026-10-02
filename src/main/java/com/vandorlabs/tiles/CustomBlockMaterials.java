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
    public static int choice(ItemStack stack){Block block=block(stack);return block==null?-1:identifier(block,stack.getItem() instanceof ItemDoor?0:stack.getMetadata() & 15);}
}
