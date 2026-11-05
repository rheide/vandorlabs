package com.vandorlabs.redstone;

import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import com.vandorlabs.tiles.TileEntityRedstoneChannel;
import com.vandorlabs.tiles.TileEntityWirelessRedstone;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntity;

/** Real Forge typed tags, saved schema and existing identity-cache behavior. */
public final class TransmissionSettingsAdapterChecks {
    private static void need(boolean value,String detail){if(!value)throw new AssertionError(detail);}
    private static NBTTagCompound settings(TileEntity tile,NBTTagCompound main){
        NBTTagCompound all=new NBTTagCompound();all.setTag("main",main);tile.getTileData().setTag("VandorTransmission",all);return main;
    }
    public static void main(String[] args){
        net.minecraft.init.Bootstrap.register();
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityRedstoneChannel.class,new net.minecraft.util.ResourceLocation("vandorlabs:transmission_settings_fixture"));
        TileEntityRedstoneChannel output=new TileEntityRedstoneChannel();output.setRedstoneChannels(ChannelList.of(73));
        TileEntityAnimatedScreenSelector screen=new TileEntityAnimatedScreenSelector();screen.setRedstoneChannels(ChannelList.of(91));
        TileEntityWirelessRedstone receiver=new TileEntityWirelessRedstone();receiver.setRedstoneChannels(ChannelList.of(92));
        need(TransmitterSlots.RANGES==TransmissionSettings.RANGES,"public range array identity");
        need(TransmitterSlots.channels(output).equals(ChannelList.of(73))&&TransmitterSlots.range(output)==16&&TransmitterSlots.localRedstone(output),"pure output defaults");
        need(TransmitterSlots.channels(screen)==ChannelList.EMPTY&&TransmitterSlots.range(screen)==16&&!TransmitterSlots.localRedstone(screen),"screen receiver does not acquire new output/local channels");
        need(TransmitterSlots.channels(receiver)==ChannelList.EMPTY&&TransmitterSlots.range(receiver)==-1&&TransmitterSlots.localRedstone(receiver),"unsupported wireless receiver defaults");
        NBTTagCompound wrong=new NBTTagCompound();wrong.setString("Channels","73");wrong.setTag("Range",new NBTTagLong(8));wrong.setInteger("Local",0);settings(output,wrong);settings(screen,wrong.copy());
        need(TransmitterSlots.channels(output).equals(ChannelList.of(73))&&TransmitterSlots.range(output)==16&&TransmitterSlots.localRedstone(output),"wrong NBT types use host defaults");
        need(!TransmitterSlots.localRedstone(screen),"numeric Local of wrong type cannot enable screen output");
        NBTTagCompound invalid=new NBTTagCompound();invalid.setIntArray("Channels",new int[]{-1});invalid.setInteger("Range",3);invalid.setBoolean("Local",false);settings(output,invalid);
        need(TransmitterSlots.channels(output)==ChannelList.EMPTY&&TransmitterSlots.range(output)==16&&!TransmitterSlots.localRedstone(output),"typed malformed settings use explicit empty/range16/localfalse");
        settings(receiver,invalid.copy());need(TransmitterSlots.range(receiver)==16,"typed invalid saved range remains16 even for unsupported receiver");
        NBTTagCompound maximum=new NBTTagCompound();maximum.setIntArray("Channels",new int[65]);settings(output,maximum);need(TransmitterSlots.channels(output)==ChannelList.EMPTY,"65 raw channels rejected before deduplication");
        NBTTagCompound canonical=new NBTTagCompound();canonical.setIntArray("Channels",new int[]{9,0,3,9});canonical.setInteger("Range",8);settings(output,canonical);
        ChannelList cached=TransmitterSlots.channels(output);need(cached.equals(ChannelList.of(3,9))&&cached==TransmitterSlots.channels(output),"cached channel identity reused");
        NBTTagCompound next=canonical.copy();next.setIntArray("Channels",new int[]{41});settings(output,next);need(TransmitterSlots.channels(output).equals(ChannelList.of(41))&&cached.equals(ChannelList.of(3,9)),"new tag identity invalidates cached channels");
        output.getTileData().setString("foreign:opaque","preserve");NBTTagCompound before=output.getTileData().copy();
        try{TransmitterSlots.configure(output,null,16,true);throw new AssertionError("accepted null channels");}catch(IllegalArgumentException expected){}
        try{TransmitterSlots.configure(output,ChannelList.EMPTY,0,true);throw new AssertionError("accepted invalid range");}catch(IllegalArgumentException expected){}
        need(before.equals(output.getTileData()),"invalid configuration mutated NBT");
        TransmitterSlots.configure(output,ChannelList.EMPTY,8,false);
        NBTTagCompound written=output.getTileData().getCompoundTag("VandorTransmission").getCompoundTag("main");
        need(written.getKeySet().size()==3&&written.hasKey("Channels",11)&&written.getIntArray("Channels").length==0&&written.hasKey("Range",3)&&written.getInteger("Range")==8&&written.hasKey("Local",1)&&!written.getBoolean("Local"),"exact built-in schema and explicit clearing");
        need("preserve".equals(output.getTileData().getString("foreign:opaque"))&&!output.getTileData().hasKey("VandorTransmitters"),"opaque siblings retained; no inventory introduced");
        NBTTagCompound saved=output.writeToNBT(new NBTTagCompound()),snapshot=saved.copy();TileEntityRedstoneChannel reloaded=new TileEntityRedstoneChannel();reloaded.readFromNBT(saved);
        need(saved.equals(snapshot)&&TransmitterSlots.channels(reloaded)==ChannelList.EMPTY&&TransmitterSlots.range(reloaded)==8&&!TransmitterSlots.localRedstone(reloaded),"actual tile save/reload retains empty output and Local setting without mutating input");
        NBTTagCompound unchanged=reloaded.getTileData().copy();TransmitterSlots.configure(reloaded,ChannelList.EMPTY,8,false);need(unchanged.equals(reloaded.getTileData()),"idempotent configure");
        System.out.println("Transmission settings adapters PASS actual typed NBT defaults malformed arrays cache schema save/reload and no inventory");
    }
}
