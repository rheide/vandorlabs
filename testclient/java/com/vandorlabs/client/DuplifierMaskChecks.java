package com.vandorlabs.client;

import com.vandorlabs.items.DuplifierApplyOptions;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Real legacy item-NBT observations, including pre-multiword masks and malformed typed tags. */
final class DuplifierMaskChecks {
    static void run(){
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");int cases=0;
            long[] lows={0,-1,Long.MIN_VALUE,Long.MAX_VALUE,0x123456789abcdef0L};
            int[] counts={-1,0,1,55,56,63,64,65,72,128,Integer.MAX_VALUE};
            for(long low:lows)for(int count:counts)for(int storedLength=0;storedLength<7;storedLength++)for(int types=0;types<8;types++){
                ItemStack item=new ItemStack(Items.STICK);NBTTagCompound root=new NBTTagCompound();
                if((types&1)==0)root.setLong(DuplifierApplyOptions.TAG,low);else root.setInteger(DuplifierApplyOptions.TAG,(int)low);
                if((types&2)==0)root.setInteger("DuplifierApplyOptionCount",count);else root.setLong("DuplifierApplyOptionCount",count);
                int[] stored=new int[storedLength];for(int j=0;j<stored.length;j++)stored[j]=(int)(low>>>(j%2*32))^(j*7919);
                if((types&4)==0)root.setIntArray(DuplifierApplyOptions.WORDS_TAG,stored);else root.setString(DuplifierApplyOptions.WORDS_TAG,"wrong");
                item.setTagCompound(root);long[] words=DuplifierApplyOptions.maskWords(item);
                require(DuplifierApplyOptions.valid(words),"decoded invalid mask");
                StringBuilder line=new StringBuilder(Arrays.toString(words));
                for(int i=-1;i<=DuplifierApplyOptions.OPTIONS.length;i++)line.append(DuplifierApplyOptions.enabled(words,i)?'1':'0');
                DuplifierApplyOptions.setMask(item,words);require(Arrays.equals(words,DuplifierApplyOptions.maskWords(new ItemStack(item.writeToNBT(new NBTTagCompound())))),"saved item changed mask");
                DuplifierApplyOptions.setMask(item,low);line.append(Arrays.toString(DuplifierApplyOptions.maskWords(item)));
                digest.update(line.toString().getBytes(StandardCharsets.UTF_8));cases++;
            }
            StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            require(hash.toString().equals("fa562397c27a9532dfc0eb43d7936b26e20719cc407623e93de0a2eb66d845c8"),"original Source mask digest changed: "+hash);
            System.out.println("Duplifier mask Source PASS cases="+cases+" options="+DuplifierApplyOptions.OPTIONS.length+" sha256="+hash);
        }catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
