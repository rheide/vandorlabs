package com.vandorlabs.items;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

/** The digest is captured from real Forge ItemStack/NBT reads before extraction. */
public final class DuplifierOptionMaskTest {
    public static void main(String[] args)throws Exception{
        DuplifierOptionMask policy=new DuplifierOptionMask(74);
        MessageDigest digest=MessageDigest.getInstance("SHA-256");int cases=0;
        long[] lows={0,-1,Long.MIN_VALUE,Long.MAX_VALUE,0x123456789abcdef0L};
        int[] counts={-1,0,1,55,56,63,64,65,72,128,Integer.MAX_VALUE};
        for(long low:lows)for(int count:counts)for(int length=0;length<7;length++)for(int types=0;types<8;types++){
            int[] stored=new int[length];for(int j=0;j<stored.length;j++)stored[j]=(int)(low>>>(j%2*32))^(j*7919);
            long[] words=policy.read((types&1)==0,low,(types&2)==0?count:56,(types&4)==0?stored:null);
            check(policy.valid(words),"valid decoded words");
            StringBuilder line=new StringBuilder(Arrays.toString(words));
            for(int i=-1;i<=74;i++)line.append(policy.enabled(words,i)?'1':'0');
            check(Arrays.equals(words,policy.read(true,words[0],74,policy.encode(words))),"saved words roundtrip");
            words[0]=low&policy.lowMask();line.append(Arrays.toString(words));
            digest.update(line.toString().getBytes(StandardCharsets.UTF_8));cases++;
        }
        StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        check(hash.toString().equals("fa562397c27a9532dfc0eb43d7936b26e20719cc407623e93de0a2eb66d845c8"),"Source item-NBT mask digest: "+hash);
        // Independent growth boundaries also cover the exact 64-bit catalog edge.
        for(int count:new int[]{1,56,63,64,65,74,128,129}){
            DuplifierOptionMask p=new DuplifierOptionMask(count);long[] all=p.legacyWords(-1);
            check(p.valid(all),"trimmed all");check(!p.valid(new long[0]),"reject short words");
            check(!p.enabled(0L,-1)&&!p.enabled(0L,count),"outside legacy bounds");
            for(int i=0;i<count;i++)check(p.enabled(all,i),"new options enabled");
            if(count%64!=0){all[all.length-1]|=Long.MIN_VALUE;check(!p.valid(all),"reject unused bits");}
        }
        System.out.println("Copifier mask PASS Source cases="+cases+" sha256="+hash+" append-only word boundaries");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
