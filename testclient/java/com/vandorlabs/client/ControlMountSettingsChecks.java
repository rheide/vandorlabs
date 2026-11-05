package com.vandorlabs.client;

import com.vandorlabs.tiles.TileEntityRedstoneChannel;
import net.minecraft.nbt.*;
import net.minecraft.util.ResourceLocation;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Frozen pre-extraction actual tile/configuration output, not a second implementation of the codec. */
public final class ControlMountSettingsChecks {
    private static final String GOLDEN="b89233dd8ca629091ca69247c3b7d9529baea031ec16f41414e49f89bb1b412f";
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static String canonical(NBTBase value){
        if(!(value instanceof NBTTagCompound))return value.toString();NBTTagCompound tag=(NBTTagCompound)value;StringBuilder text=new StringBuilder("{");
        for(String key:new TreeSet<>(tag.getKeySet()))text.append(key).append(':').append(canonical(tag.getTag(key))).append(';');return text.append('}').toString();
    }
    private static NBTTagCompound mount(int height,int tilt,int direction){NBTTagCompound tag=new NBTTagCompound();tag.setInteger("ControlMountVersion",2);tag.setInteger("BaseHeight",height);tag.setInteger("BaseTilt",tilt);tag.setInteger("TiltDirection",direction);return tag;}
    private static TileEntityRedstoneChannel read(NBTTagCompound tag){TileEntityRedstoneChannel tile=new TileEntityRedstoneChannel();tile.readMount(mount(2,3,1));tile.readFromNBT(tag);return tile;}
    private static void fields(NBTTagCompound tag,int height,int tilt,int direction,String message){TileEntityRedstoneChannel tile=read(tag);require(tile.getBaseHeight()==height&&tile.getBaseTilt()==tilt&&tile.getTiltDirection()==direction,message);}
    private static void signalFields(NBTTagCompound tag,int height,int tilt,int direction,String message){com.vandorlabs.tiles.TileEntitySignalControl tile=new com.vandorlabs.tiles.TileEntitySignalControl();tile.readMount(mount(2,3,1));tile.readFromNBT(tag);require(tile.getBaseHeight()==height&&tile.getBaseTilt()==tilt&&tile.getTiltDirection()==direction,message);}
    public static void main(String[] ignored)throws Exception{
        net.minecraft.init.Bootstrap.register();net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityRedstoneChannel.class,new ResourceLocation("vandorlabs","control_mount_fixture"));
        NBTTagCompound empty=new NBTTagCompound();fields(empty,0,0,0,"missing tags reset to flat defaults");
        NBTTagCompound old=mount(3,3,2);old.removeTag("ControlMountVersion");fields(old,3,1,2,"unversioned 15-degree legacy preset migrates to first supported tilt");
        old.setString("ControlMountVersion","not an integer");fields(old,3,3,2,"any present version suppresses migration");
        NBTTagCompound mixed=new NBTTagCompound();mixed.setByte("BaseHeight",(byte)1);mixed.setShort("BaseTilt",(short)3);mixed.setLong("TiltDirection",2);fields(mixed,1,1,2,"NBT numeric types retain getInteger conversion");
        mixed.setBoolean("ControlMountVersion",false);fields(mixed,1,3,2,"present false-byte version suppresses migration");
        NBTTagCompound wrong=mount(3,3,1);wrong.setString("BaseHeight","3");wrong.setString("BaseTilt","3");wrong.setString("TiltDirection","1");fields(wrong,0,0,0,"wrong scalar types read as zero instead of preserving previous values");
        fields(mount(4,2,1),2,3,1,"invalid height preserves complete previous triple");fields(mount(1,-1,2),2,3,1,"invalid tilt preserves complete previous triple");fields(mount(1,2,4),2,3,1,"invalid direction preserves complete previous triple");
        signalFields(new NBTTagCompound(),0,0,0,"signal subclass missing fields remain flat");
        signalFields(mount(4,2,1),0,0,0,"signal subclass invalid triple resets flat rather than preserving its previous mount");
        NBTTagCompound signalOld=mount(3,3,2);signalOld.removeTag("ControlMountVersion");signalFields(signalOld,3,1,2,"signal subclass retains old tilt migration");signalOld.setString("ControlMountVersion","old");signalFields(signalOld,3,3,2,"signal subclass retains any-version-presence semantics");
        MessageDigest digest=MessageDigest.getInstance("SHA-256");int cases=0;
        for(int version=0;version<4;version++)for(int h=-1;h<=4;h++)for(int t=-1;t<=4;t++)for(int d=-1;d<=4;d++){
            NBTTagCompound tag=mount(h,t,d);if(version==0)tag.removeTag("ControlMountVersion");else if(version==1)tag.setInteger("ControlMountVersion",0);else if(version==2)tag.setString("ControlMountVersion","old");else tag.setBoolean("ControlMountVersion",false);
            tag.setInteger("Channel",7);tag.setIntArray("RedstoneChannels",new int[]{3,7});tag.setBoolean("LocalOn",true);tag.setBoolean("ChannelInitialized",true);tag.setInteger("MountRotation",5);tag.setIntArray("LatchedChannels",new int[]{7});tag.setIntArray("LatchedLevels",new int[]{-1,23});
            NBTTagCompound forge=new NBTTagCompound();forge.setString("OtherModField","opaque");tag.setTag("ForgeData",forge);
            TileEntityRedstoneChannel tile=read(tag);NBTTagCompound configuration=tile.configuration();
            require(configuration.getKeySet().equals(new HashSet<>(Arrays.asList("Channel","RedstoneChannels","ControlMountVersion","BaseHeight","BaseTilt","TiltDirection"))),"configured item schema changed");
            NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());require(saved.getInteger("ControlMountVersion")==2,"mount writer version changed");require(saved.getCompoundTag("ForgeData").getString("OtherModField").equals("opaque"),"unrelated Forge data lost");
            ItemStackRoundTrip.check(configuration);digest.update((canonical(configuration)+canonical(saved)+"\n").getBytes(StandardCharsets.UTF_8));cases++;
        }
        StringBuilder hash=new StringBuilder();for(byte part:digest.digest())hash.append(String.format("%02x",part&255));
        require(hash.toString().equals(GOLDEN),"pre-extraction actual-NBT output changed: "+hash);
        System.out.println("Control mount actual NBT PASS cases="+cases+" golden="+hash+" old/malformed typed fields, prior-state fallback, configured item payload and untouched source/Forge data");
    }
    private static final class ItemStackRoundTrip {
        static void check(NBTTagCompound config){net.minecraft.item.ItemStack item=new net.minecraft.item.ItemStack(net.minecraft.init.Blocks.STONE);item.setTagInfo("RedstoneChannelSettings",config.copy());net.minecraft.item.ItemStack restored=new net.minecraft.item.ItemStack(item.writeToNBT(new NBTTagCompound()));require(restored.getSubCompound("RedstoneChannelSettings").equals(config),"configured item NBT roundtrip changed mount data");}
    }
}
