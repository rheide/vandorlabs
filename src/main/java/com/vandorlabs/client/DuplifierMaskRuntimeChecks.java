package com.vandorlabs.client;

import com.vandorlabs.items.*;
import com.vandorlabs.network.MessageDuplifierOptions;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import io.netty.buffer.*;
import java.util.Arrays;

/** Legacy bit positions, independent bit64, persistent item choices and bounded wire decoding. */
final class DuplifierMaskRuntimeChecks {
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Duplifier scalable mask: "+message);}
    static void run(){
        require(DuplifierApplyOptions.OPTIONS[61].key.equals(ProgrammableSettings.GLASS_TINT) && DuplifierApplyOptions.OPTIONS[62].key.equals(ProgrammableSettings.GLASS_MATERIAL) && DuplifierApplyOptions.OPTIONS[63].key.equals(ProgrammableSettings.BLOCK_CONFIGURATION),"existing saved bits61..63 retain meanings");
        require(DuplifierApplyOptions.OPTIONS[64].key.equals("MoatMode"),"Moat appends at64");
        ItemStack tool=new ItemStack(ModItems.DUPLIFIER);NBTTagCompound old=new NBTTagCompound();old.setLong(DuplifierApplyOptions.TAG,0x1020408102040810L);old.setInteger("DuplifierApplyOptionCount",64);tool.setTagCompound(old);
        long[] words=DuplifierApplyOptions.maskWords(tool);require(words[0]==old.getLong(DuplifierApplyOptions.TAG) && DuplifierApplyOptions.enabled(words,64),"64-bit save preserves all choices and enables new option");
        words[1]=0;DuplifierApplyOptions.setMask(tool,words);tool=new ItemStack(tool.writeToNBT(new NBTTagCompound()));require(Arrays.equals(words,DuplifierApplyOptions.maskWords(tool)),"disabled bit64 survives item save/reload");
        DuplifierApplyOptions.setMask(tool,1L);require(!DuplifierApplyOptions.enabled(DuplifierApplyOptions.maskWords(tool),64) && DuplifierApplyOptions.mask(tool)==1,"legacy first-word setter preserves newer disabled option");
        NBTTagCompound captured=new NBTTagCompound();for(DuplifierApplyOptions.Option option:DuplifierApplyOptions.OPTIONS)captured.setBoolean(option.key,true);
        NBTTagCompound selected=DuplifierApplyOptions.selected(captured,DuplifierApplyOptions.maskWords(tool));require(selected.hasKey(ProgrammableSettings.PRIMARY_TEXTURE) && !selected.hasKey("MoatMode") && !selected.hasKey(ProgrammableSettings.BLOCK_CONFIGURATION),"bit0 never aliases bit64 during real selection");
        words=new long[]{0,1};DuplifierApplyOptions.setMask(tool,words);selected=DuplifierApplyOptions.selected(captured,DuplifierApplyOptions.maskWords(tool));require(selected.hasKey("MoatMode") && !selected.hasKey(ProgrammableSettings.PRIMARY_TEXTURE),"bit64 enabled independently of bit0");
        ByteBuf encoded=Unpooled.buffer();try{new MessageDuplifierOptions(words,true).toBytes(encoded);MessageDuplifierOptions packet=new MessageDuplifierOptions();packet.fromBytes(encoded);require(packetValid(packet) && Arrays.equals(words,packetWords(packet)),"extended packet preserves every word");}finally{encoded.release();}
        encoded=Unpooled.buffer();try{encoded.writeLong(0).writeBoolean(false);MessageDuplifierOptions packet=new MessageDuplifierOptions();packet.fromBytes(encoded);require(packetValid(packet) && packetWords(packet)[0]==0 && DuplifierApplyOptions.enabled(packetWords(packet),64),"legacy9-byte packet retains old choices and defaults new option on");}finally{encoded.release();}
        encoded=Unpooled.buffer();try{encoded.writeLong(0).writeBoolean(false).writeByte(2).writeLong(2);MessageDuplifierOptions packet=new MessageDuplifierOptions();packet.fromBytes(encoded);require(!packetValid(packet),"unknown high bits rejected");}finally{encoded.release();}
        encoded=Unpooled.buffer();try{encoded.writeLong(0).writeBoolean(false).writeByte(255);MessageDuplifierOptions packet=new MessageDuplifierOptions();packet.fromBytes(encoded);require(!packetValid(packet),"oversized packet count rejected");}finally{encoded.release();}
        System.out.println("[vandorlabs][reprolab] duplifier-scalable-mask PASS legacy-indices61-63 bit64-independent item-save packet-roundtrip legacy-packet malformed-bounds");
    }
    private static boolean packetValid(MessageDuplifierOptions packet){return net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(MessageDuplifierOptions.class,packet,"valid");}
    private static long[] packetWords(MessageDuplifierOptions packet){return net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(MessageDuplifierOptions.class,packet,"mask");}
    private DuplifierMaskRuntimeChecks(){}
}
