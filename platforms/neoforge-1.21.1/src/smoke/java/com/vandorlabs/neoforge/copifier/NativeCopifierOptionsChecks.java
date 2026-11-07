package com.vandorlabs.neoforge.copifier;
import com.vandorlabs.items.DuplifierOptionCatalog;
import com.vandorlabs.items.DuplifierOptionMask;
import net.minecraft.nbt.CompoundTag;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
/** Actual native CompoundTag adapter against the frozen Forge item-NBT digest. */
public final class NativeCopifierOptionsChecks {
    private static final DuplifierOptionMask POLICY=new DuplifierOptionMask(DuplifierOptionCatalog.size());
    public static void main(String[] args)throws Exception{
            MessageDigest digest=MessageDigest.getInstance("SHA-256");int cases=0;
            long[] lows={0,-1,Long.MIN_VALUE,Long.MAX_VALUE,0x123456789abcdef0L};
            int[] counts={-1,0,1,55,56,63,64,65,72,128,Integer.MAX_VALUE};
            for(long low:lows)for(int count:counts)for(int storedLength=0;storedLength<7;storedLength++)for(int types=0;types<8;types++){
                CompoundTag root=new CompoundTag();root.putString("Unrelated","keep");
                if((types&1)==0)root.putLong(NativeCopifierOptions.MASK,low);else root.putInt(NativeCopifierOptions.MASK,(int)low);
                if((types&2)==0)root.putInt("DuplifierApplyOptionCount",count);else root.putLong("DuplifierApplyOptionCount",count);
                int[] stored=new int[storedLength];for(int j=0;j<stored.length;j++)stored[j]=(int)(low>>>(j%2*32))^(j*7919);
                if((types&4)==0)root.putIntArray(NativeCopifierOptions.WORDS,stored);else root.putString(NativeCopifierOptions.WORDS,"wrong");
                long[] words=NativeCopifierOptions.maskWords(root);
                require(POLICY.valid(words),"decoded invalid mask");
                StringBuilder line=new StringBuilder(Arrays.toString(words));
                for(int i=-1;i<=DuplifierOptionCatalog.size();i++)line.append(POLICY.enabled(words,i)?'1':'0');
                CompoundTag saved=NativeCopifierOptions.withMask(root,words);require(Arrays.equals(words,NativeCopifierOptions.maskWords(saved.copy())),"saved tag changed mask");require(saved.getString("Unrelated").equals("keep")&&root.getString("Unrelated").equals("keep"),"opaque data lost");
                CompoundTag updated=NativeCopifierOptions.withMask(saved,low);line.append(Arrays.toString(NativeCopifierOptions.maskWords(updated)));require(!NativeCopifierOptions.connected(root)&&NativeCopifierOptions.connected(NativeCopifierOptions.withConnected(root,true)),"connected copy isolation");require(!NativeCopifierOptions.ignoreDirectionality(root)&&NativeCopifierOptions.ignoreDirectionality(NativeCopifierOptions.withIgnoreDirectionality(root,true)),"direction copy isolation");
                digest.update(line.toString().getBytes(StandardCharsets.UTF_8));cases++;
            }
            StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            require(hash.toString().equals("fa562397c27a9532dfc0eb43d7936b26e20719cc407623e93de0a2eb66d845c8"),"original Source mask digest changed: "+hash);

        String catalog=new com.google.gson.Gson().toJson(new Object[]{DuplifierOptionCatalog.pages(),DuplifierOptionCatalog.options()});
        require(catalog.equals(java.nio.file.Files.readString(java.nio.file.Path.of(args[0]))),"Source dialog catalog differs");
        System.out.println("Native Copifier options PASS Source masks="+cases+" catalog="+DuplifierOptionCatalog.size()+" opaque-copy isolation; no registered item/gameplay claim");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
