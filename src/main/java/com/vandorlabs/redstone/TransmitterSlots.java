package com.vandorlabs.redstone;

import com.vandorlabs.items.ItemRedstoneTransmitter;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

/** Slot contents live in ForgeData, preserving them through every tile's normal snapshot path. */
public final class TransmitterSlots {
    private static final String KEY="VandorTransmitters";
    // Forge's public getter creates and persists an empty ForgeData compound on reads.
    // This Forge-added field has the same unobfuscated name in production and development.
    private static final java.lang.reflect.Field CUSTOM_DATA=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(TileEntity.class,"customTileData");
    private static final NBTTagCompound NO_DATA=new NBTTagCompound();
    public static NBTTagCompound savedSlots(TileEntity tile){try{NBTTagCompound data=(NBTTagCompound)CUSTOM_DATA.get(tile);return data==null||!data.hasKey(KEY,10)?NO_DATA:data.getCompoundTag(KEY);}catch(IllegalAccessException brokenForge){throw new IllegalStateException("Cannot read Forge tile custom data",brokenForge);}}
    private static final java.util.Map<RedstoneChannelMember,Cached> CACHE=java.util.Collections.synchronizedMap(new java.util.WeakHashMap<RedstoneChannelMember,Cached>());
    private static final class Cached {final NBTTagCompound identity;final ChannelList channels;final int range;Cached(NBTTagCompound identity){this.identity=identity;ItemStack stack=new ItemStack(identity);channels=ItemRedstoneTransmitter.channels(stack);range=ItemRedstoneTransmitter.range(stack);}}
    private static Cached settings(RedstoneChannelMember member){NBTTagCompound slots=savedSlots(member.channelTile());if(!slots.hasKey(member.transmitterSlot(),10))return EMPTY;NBTTagCompound tag=slots.getCompoundTag(member.transmitterSlot());Cached cached=CACHE.get(member);if(cached==null||cached.identity!=tag){cached=new Cached(tag);CACHE.put(member,cached);}return cached;}
    private static final Cached EMPTY=new Cached(new NBTTagCompound());
    private TransmitterSlots(){}
    public static ItemStack get(RedstoneChannelMember member){
        NBTTagCompound slots=savedSlots(member.channelTile());
        return new ItemStack(slots.getCompoundTag(member.transmitterSlot()));
    }
    public static ChannelList channels(RedstoneChannelMember member){return settings(member).channels;}
    public static int range(RedstoneChannelMember member){return settings(member).range;}
    public static boolean outputOnly(RedstoneChannelMember member){TileEntity tile=member.channelTile();return member instanceof RedstoneChannelLatch&&!(tile instanceof com.vandorlabs.tiles.TileEntityWirelessRedstone&&!((com.vandorlabs.tiles.TileEntityWirelessRedstone)tile).transmitter())||tile instanceof com.vandorlabs.tiles.TileEntityTokenSlot||tile instanceof com.vandorlabs.plate.PlateTile||tile instanceof com.vandorlabs.tiles.TileEntityWaypoint;}
    public static void set(RedstoneChannelMember member,ItemStack stack){
        if(!stack.isEmpty()&&!(stack.getItem() instanceof ItemRedstoneTransmitter))throw new IllegalArgumentException("Not a redstone transmitter");
        TileEntity tile=member.channelTile();
        RedstoneChannels.unregister(member);
        NBTTagCompound data=tile.getTileData(),slots=data.getCompoundTag(KEY);
        if(stack.isEmpty())slots.removeTag(member.transmitterSlot());else {ItemStack copy=stack.copy();copy.setCount(1);slots.setTag(member.transmitterSlot(),copy.writeToNBT(new NBTTagCompound()));}
        data.setTag(KEY,slots);CACHE.remove(member);
        // Pure controls subscribe to their inserted item's channels, receivers keep their input selection.
        if(outputOnly(member))
            member.setRedstoneChannels(channels(member));
        tile.markDirty();RedstoneChannels.register(member);
        if(tile.getWorld() instanceof com.vandorlabs.vehicle.VehicleWorld){com.vandorlabs.vehicle.VehicleWorld view=(com.vandorlabs.vehicle.VehicleWorld)tile.getWorld();if(view.craft!=null&&!view.parent.isRemote)view.craft.signals.transmitterChanged(member);}
        if(tile.getWorld()!=null&&!tile.getWorld().isRemote){net.minecraft.block.state.IBlockState state=tile.getWorld().getBlockState(tile.getPos());tile.getWorld().notifyBlockUpdate(tile.getPos(),state,state,2);}
    }
}
