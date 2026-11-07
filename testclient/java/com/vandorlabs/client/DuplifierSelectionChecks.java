package com.vandorlabs.client;

import com.vandorlabs.items.DuplifierApplyOptions;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.nbt.NBTTagCompound;

/** Capture exact copied-setting filtering independently of world application and item ownership. */
final class DuplifierSelectionChecks {
    static final String[] DEPENDENT={"trapdoor_hinge_side","trapdoor_rotation_flipped","trapdoor_slide_into_wall","trapdoor_slide_over_surface","diagonal_full_width","texture_layout","light_off_face_texture","door_texture_layout","trapdoor_texture_layout","glass_layout","ramp_start_half","ramp_end_half","primary_kind"};
    static void run(){try{
        List<long[]> masks=new ArrayList<>();masks.add(DuplifierApplyOptions.legacyWords(-1));masks.add(new long[]{0,0});masks.add(DuplifierApplyOptions.legacyWords(0));
        for(int i=0;i<DuplifierApplyOptions.OPTIONS.length;i++){
            long[] one=new long[2];one[i/64]=1L<<(i%64);masks.add(one);
            long[] minus=DuplifierApplyOptions.legacyWords(-1);minus[i/64]&=~(1L<<(i%64));masks.add(minus);
        }
        Random random=new Random(0x492a08L);for(int i=0;i<256;i++)masks.add(new long[]{random.nextLong(),random.nextLong()&1023});
        masks.add(new long[0]);masks.add(new long[]{0});
        MessageDigest digest=MessageDigest.getInstance("SHA-256");int cases=0;
        for(long[] mask:masks)for(int variant=0;variant<5;variant++){
            NBTTagCompound captured=new NBTTagCompound();
            for(DuplifierApplyOptions.Option option:DuplifierApplyOptions.OPTIONS)captured.setString(option.key,"wrong-typed-but-present");
            for(String key:DEPENDENT)captured.setInteger(key,91);
            if(variant==1)for(String key:new ArrayList<>(captured.getKeySet()))if((key.hashCode()&1)==0)captured.removeTag(key);
            if(variant==2)for(String key:new ArrayList<>(captured.getKeySet()))if((key.hashCode()&1)!=0)captured.removeTag(key);
            if(variant==3)for(DuplifierApplyOptions.Option option:DuplifierApplyOptions.OPTIONS)captured.removeTag(option.key);
            if(variant==4)captured=new NBTTagCompound();
            NBTTagCompound opaque=new NBTTagCompound();opaque.setIntArray("private",new int[]{3,7,9});captured.setTag("OpaqueExtension",opaque);
            NBTTagCompound original=captured.copy();NBTTagCompound selected=DuplifierApplyOptions.selected(captured,mask);
            digest.update(String.join(",",new TreeSet<>(selected.getKeySet())).getBytes(StandardCharsets.UTF_8));
            for(String key:selected.getKeySet())require(selected.getTag(key).equals(original.getTag(key)),"modified selected value");
            selected.getCompoundTag("OpaqueExtension").setInteger("edited",1);require(captured.equals(original),"source mutated by filtering/caller");cases++;
        }
        require(DuplifierApplyOptions.selected(null,new long[0])==null,"null capture");
        StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(Locale.ROOT,"%02x",b&255));
        require(hash.toString().equals("04aef1d90eec9cf0a9589ec47c44e34738dfbac478833d9c90541a25e4125949"),"Source selection changed: "+hash);
        System.out.println("Copifier selection Source PASS cases="+cases+" sha256="+hash);
    }catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
