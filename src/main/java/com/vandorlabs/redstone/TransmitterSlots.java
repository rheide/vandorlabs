package com.vandorlabs.redstone;

import com.vandorlabs.items.ItemRedstoneTransmitter;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

/** Slot contents live in ForgeData, preserving them through every tile's normal snapshot path. */
public final class TransmitterSlots {
    private static final String KEY="VandorTransmitters";
    public static final String SCHEMA="TransmitterSchema";
    private static final java.util.Set<String> OUTPUT_TYPES=new java.util.HashSet<>(java.util.Arrays.asList(
            "redstone_channel","signal_control","wireless_redstone","token_slot","programmable_pressure_plate",
            "autopilot","waypoint","programmable_ramp","landing_gear","canopy","programmable_trapdoor",
            "programmable_diagonal_trapdoor","sliding_door","programmable_door","large_programmable_door",
            "programmable_viewscreen","programmable_light","programmable_trigger_block","redstone_light","cockpit"));
    /** Marks even an empty new slot, so removal or a later channel edit never grants another item. */
    public static void written(NBTTagCompound tag){tag.setInteger(SCHEMA,1);}
    /** Canonicalize a previous save without changing its opaque capabilities or caller-owned NBT. */
    public static NBTTagCompound normalize(NBTTagCompound source){
        String id=source.getString("id");
        if(!id.startsWith("vandorlabs:")||!OUTPUT_TYPES.contains(id.substring(11))||source.hasKey(SCHEMA))return source;
        if(!source.hasKey("x",3)||!source.hasKey("y",3)||!source.hasKey("z",3))return source;
        NBTTagCompound tag=source.copy();written(tag);
        NBTTagCompound forge=tag.getCompoundTag("ForgeData"),slots=forge.getCompoundTag(KEY);
        boolean hadSlots=forge.hasKey(KEY,10);
        ChannelList channels=ChannelData.read(tag,Math.max(0,tag.getInteger("RedstoneChannel")));
        if(id.equals("vandorlabs:canopy"))channels=tag.getBoolean("CanopyRedstone")?ChannelData.read(tag,"CanopyChannels",ChannelList.EMPTY):ChannelList.EMPTY;
        if(id.equals("vandorlabs:cockpit")){
            java.util.SortedSet<Integer> union=new java.util.TreeSet<>();
            net.minecraft.nbt.NBTTagList controls=tag.getTagList("CockpitControls",10);
            for(int i=0;i<controls.tagCount();i++){
                NBTTagCompound control=controls.getCompoundTagAt(i);String key="cockpit_"+control.getString("Id");
                ChannelList targets=ChannelData.read(control,0);
                if(targets.isEmpty()&&slots.hasKey(key,10)){targets=ItemRedstoneTransmitter.channels(new ItemStack(slots.getCompoundTag(key)));ChannelData.write(control,targets);}
                if(!control.hasKey("LevelChannels",11))control.setIntArray("LevelChannels",targets.toArray());
                for(int channel:targets.toArray())union.add(channel);
            }
            int[] selected=new int[Math.min(ChannelList.MAX_CHANNELS,union.size())];int i=0;for(int channel:union){if(i==selected.length)break;selected[i++]=channel;}channels=ChannelList.of(selected);
            // Move one existing item, preserving all remaining inventory entries for recovery.
            if(!slots.hasKey("main",10)){
                String best=null;int range=-2;
                for(String key:new java.util.TreeSet<>(slots.getKeySet()))if(key.startsWith("cockpit_")){
                    ItemStack item=new ItemStack(slots.getCompoundTag(key));int candidate=ItemRedstoneTransmitter.range(item);if(candidate==0)candidate=Integer.MAX_VALUE;
                    if(candidate>range&&ItemRedstoneTransmitter.valid(item)){range=candidate;best=key;}
                }
                if(best!=null){slots.setTag("main",slots.getTag(best));slots.removeTag(best);}
            }
        }
        for(int surface=0;!hadSlots&&surface<2;surface++){
            net.minecraft.nbt.NBTTagList rows=tag.getCompoundTag(surface==0?"RedstonePrimary":"RedstoneSecondary").getTagList("Rows",10);
            for(int row=0;row<Math.min(com.vandorlabs.tiles.RedstoneScreenContents.MAX_ROWS,rows.tagCount());row++)
                migrateSlot(slots,"row_"+surface+"_"+row,ChannelData.read(rows.getCompoundTagAt(row),0));
        }
        if(id.equals("vandorlabs:landing_gear")&&(tag.getBoolean("GearHasOwner")||tag.getInteger("GearOwnerDistance")>0))channels=ChannelList.EMPTY;
        if(!hadSlots&&!channels.isEmpty()&&!slots.hasKey("main",10)){
            migrateSlot(slots,"main",channels);
        }
        if(!slots.hasNoTags()){forge.setTag(KEY,slots);tag.setTag("ForgeData",forge);}
        return tag;
    }

    private static void migrateSlot(NBTTagCompound slots,String key,ChannelList channels){
        if(channels.isEmpty()||slots.hasKey(key,10))return;
        ItemStack item=new ItemStack(com.vandorlabs.items.ModItems.REDSTONE_TRANSMITTER,1,2);
        ItemRedstoneTransmitter.configure(item,channels);slots.setTag(key,item.writeToNBT(new NBTTagCompound()));
    }
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
    public static boolean outputOnly(RedstoneChannelMember member){if(member instanceof com.vandorlabs.tiles.TileEntityCockpit.Control)return false;TileEntity tile=member.channelTile();return member instanceof RedstoneChannelLatch&&!(tile instanceof com.vandorlabs.tiles.TileEntityWirelessRedstone&&!((com.vandorlabs.tiles.TileEntityWirelessRedstone)tile).transmitter())||tile instanceof com.vandorlabs.tiles.TileEntityTokenSlot||tile instanceof com.vandorlabs.plate.PlateTile||tile instanceof com.vandorlabs.tiles.TileEntityWaypoint;}
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
        if(tile instanceof com.vandorlabs.tiles.TileEntityCockpit)((com.vandorlabs.tiles.TileEntityCockpit)tile).sharedTransmitterChanged();
        if(tile.getWorld() instanceof com.vandorlabs.vehicle.VehicleWorld){com.vandorlabs.vehicle.VehicleWorld view=(com.vandorlabs.vehicle.VehicleWorld)tile.getWorld();if(view.craft!=null&&!view.parent.isRemote)view.craft.signals.transmitterChanged(member);}
        if(tile.getWorld()!=null&&!tile.getWorld().isRemote){net.minecraft.block.state.IBlockState state=tile.getWorld().getBlockState(tile.getPos());tile.getWorld().notifyBlockUpdate(tile.getPos(),state,state,2);}
    }
}
