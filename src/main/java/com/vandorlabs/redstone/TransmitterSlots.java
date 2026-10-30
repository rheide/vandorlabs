package com.vandorlabs.redstone;

import com.vandorlabs.items.ItemRedstoneTransmitter;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

/** Built-in settings and recoverable legacy hardware persist through normal ForgeData snapshots. */
public final class TransmitterSlots {
    private static final String KEY="VandorTransmitters";
    private static final String SETTINGS="VandorTransmission";
    public static final int[] RANGES={4,8,16,32,64,128,256};
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
        if(id.equals("vandorlabs:programmable_viewscreen")&&source.hasKey(SCHEMA))return sharedScreen(source);
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
        return id.equals("vandorlabs:programmable_viewscreen")?sharedScreen(tag):tag;
    }

    /** Move one old per-row module; all surplus items remain saved for normal block drops. */
    private static NBTTagCompound sharedScreen(NBTTagCompound source){
        NBTTagCompound originalForge=source.getCompoundTag("ForgeData");
        if(originalForge.getBoolean("VandorSharedScreenTransmitter"))return source;
        NBTTagCompound result=source.copy(),forge=result.getCompoundTag("ForgeData");forge.setBoolean("VandorSharedScreenTransmitter",true);result.setTag("ForgeData",forge);
        NBTTagCompound old=forge.getCompoundTag(KEY);
        if(old.hasKey("main",10))return result;
        String best=null;int range=-2;
        for(String key:new java.util.TreeSet<>(old.getKeySet()))if(key.startsWith("row_")){
            ItemStack item=new ItemStack(old.getCompoundTag(key));int candidate=ItemRedstoneTransmitter.range(item);if(candidate==0)candidate=Integer.MAX_VALUE;
            if(ItemRedstoneTransmitter.valid(item)&&candidate>range){best=key;range=candidate;}
        }
        if(best==null)return result;
        old.setTag("main",old.getTag(best));old.removeTag(best);return result;
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
    private static NBTTagCompound savedSettings(TileEntity tile,String slot){
        try{
            NBTTagCompound data=(NBTTagCompound)CUSTOM_DATA.get(tile);
            if(data==null||!data.hasKey(SETTINGS,10))return NO_DATA;
            NBTTagCompound all=data.getCompoundTag(SETTINGS);
            return all.hasKey(slot,10)?all.getCompoundTag(slot):NO_DATA;
        }catch(IllegalAccessException brokenForge){throw new IllegalStateException("Cannot read transmission settings",brokenForge);}
    }
    private static NBTTagCompound configuration(RedstoneChannelMember member){return savedSettings(member.channelTile(),member.transmitterSlot());}
    private static final java.util.Map<RedstoneChannelMember,SavedChannels> CONFIG_CACHE=java.util.Collections.synchronizedMap(new java.util.WeakHashMap<RedstoneChannelMember,SavedChannels>());
    private static final class SavedChannels {
        final NBTTagCompound identity;final ChannelList channels;
        SavedChannels(NBTTagCompound tag){identity=tag;channels=ChannelData.read(tag,"Channels",ChannelList.EMPTY);}
    }
    public static boolean supported(RedstoneChannelMember member){
        return outputOnly(member)||member instanceof com.vandorlabs.tiles.TileEntityCockpit
                ||member instanceof com.vandorlabs.tiles.TileEntityCockpit.Control
                ||member instanceof com.vandorlabs.tiles.TileEntityAnimatedScreenSelector
                ||member instanceof com.vandorlabs.tiles.RedstoneScreenContents.Row;
    }
    public static ChannelList channels(RedstoneChannelMember member){
        NBTTagCompound configured=configuration(member);
        if(configured.hasKey("Channels",11)){SavedChannels cached=CONFIG_CACHE.get(member);if(cached==null||cached.identity!=configured){cached=new SavedChannels(configured);CONFIG_CACHE.put(member,cached);}return cached.channels;}
        Cached legacy=settings(member);
        if(legacy.range>=0)return legacy.channels;
        return outputOnly(member)?member.getRedstoneChannels():ChannelList.EMPTY;
    }
    public static boolean validRange(int range){for(int allowed:RANGES)if(range==allowed)return true;return false;}
    public static int range(RedstoneChannelMember member){
        NBTTagCompound configured=configuration(member);
        if(configured.hasKey("Range",3)){int value=configured.getInteger("Range");return validRange(value)?value:16;}
        int legacy=settings(member).range;
        return legacy>=0?(legacy==0?256:legacy):supported(member)?16:-1;
    }
    public static boolean localRedstone(TileEntity tile){
        NBTTagCompound configured=savedSettings(tile,"main");
        if(configured.hasKey("Local",1))return configured.getBoolean("Local");
        // Enabling new physical outputs must be deliberate in existing worlds.
        return !(tile instanceof com.vandorlabs.tiles.TileEntityAnimatedScreenSelector
                ||tile instanceof com.vandorlabs.tiles.TileEntityCockpit
                ||tile instanceof com.vandorlabs.tiles.TileEntityWirelessRedstone&&((com.vandorlabs.tiles.TileEntityWirelessRedstone)tile).transmitter());
    }
    /** Combined physical output for hosts with several independent controls. */
    public static int localOutput(net.minecraft.world.IBlockAccess world,net.minecraft.util.math.BlockPos pos){
        TileEntity tile=world.getTileEntity(pos);if(tile==null||!localRedstone(tile))return 0;int level=0;
        if(tile instanceof com.vandorlabs.tiles.TileEntityCockpit){
            for(com.vandorlabs.tiles.TileEntityCockpit.Control control:((com.vandorlabs.tiles.TileEntityCockpit)tile).controls)if(control.active())level=Math.max(level,control.level);
        }else if(tile instanceof com.vandorlabs.tiles.TileEntityAnimatedScreenSelector){
            com.vandorlabs.tiles.TileEntityAnimatedScreenSelector screen=(com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)tile;
            for(int side=0;side<2;side++)if(screen.hasRedstoneScreen(side))for(com.vandorlabs.tiles.RedstoneScreenContents.Row row:screen.redstoneScreen(side).rows())level=Math.max(level,row.slider?row.level():row.active()?15:0);
        }
        return Math.max(0,Math.min(15,level));
    }
    public static boolean localRedstone(net.minecraft.world.IBlockAccess world,net.minecraft.util.math.BlockPos pos){TileEntity tile=world.getTileEntity(pos);return tile==null||localRedstone(tile);}
    /** Settings are independent of the retained legacy inventory, which is still returned on mining. */
    public static void configure(RedstoneChannelMember member,ChannelList channels,int range,boolean local){
        if(channels==null||!validRange(range)||!supported(member))throw new IllegalArgumentException("Invalid transmission settings");
        TileEntity tile=member.channelTile();boolean previousLocal=localRedstone(tile);NBTTagCompound data=tile.getTileData(),all=data.getCompoundTag(SETTINGS),next=new NBTTagCompound();
        next.setIntArray("Channels",channels.toArray());next.setInteger("Range",range);next.setBoolean("Local",local);
        all.setTag(member.transmitterSlot(),next);data.setTag(SETTINGS,all);
        set(member,get(member));
        if(previousLocal!=local)notifyLocalOutput(tile);
    }
    /** Notify native output and support conduction without requesting unloaded chunks. */
    public static void notifyLocalOutput(TileEntity tile){
        net.minecraft.world.World world=tile.getWorld();if(world==null||world.isRemote||!world.isBlockLoaded(tile.getPos()))return;
        net.minecraft.util.math.BlockPos pos=tile.getPos();net.minecraft.block.Block block=world.getBlockState(pos).getBlock();
        for(net.minecraft.util.EnumFacing face:net.minecraft.util.EnumFacing.values()){
            net.minecraft.util.math.BlockPos neighbor=pos.offset(face);if(!world.isBlockLoaded(neighbor))continue;
            world.neighborChanged(neighbor,block,pos);
            if(world.getBlockState(neighbor).isNormalCube())for(net.minecraft.util.EnumFacing side:net.minecraft.util.EnumFacing.values()){
                net.minecraft.util.math.BlockPos target=neighbor.offset(side);if(world.isBlockLoaded(target))world.neighborChanged(target,block,neighbor);
            }
        }
    }
    public static boolean outputOnly(RedstoneChannelMember member){if(member instanceof com.vandorlabs.tiles.TileEntityCockpit.Control||member instanceof com.vandorlabs.tiles.RedstoneScreenContents.Row)return false;TileEntity tile=member.channelTile();return member instanceof RedstoneChannelLatch&&!(tile instanceof com.vandorlabs.tiles.TileEntityWirelessRedstone&&!((com.vandorlabs.tiles.TileEntityWirelessRedstone)tile).transmitter())||tile instanceof com.vandorlabs.tiles.TileEntityTokenSlot||tile instanceof com.vandorlabs.plate.PlateTile||tile instanceof com.vandorlabs.tiles.TileEntityWaypoint;}
    public static void set(RedstoneChannelMember member,ItemStack stack){
        net.minecraft.world.World world=member.channelTile().getWorld();
        boolean placed=world!=null&&!world.isRemote;
        if(placed)RedstoneChannels.beginTransfer(world);
        try{setContents(member,stack);}finally{if(placed)RedstoneChannels.endTransfer(world);}
    }
    private static void setContents(RedstoneChannelMember member,ItemStack stack){
        if(!stack.isEmpty()&&!(stack.getItem() instanceof ItemRedstoneTransmitter))throw new IllegalArgumentException("Not a redstone transmitter");
        TileEntity tile=member.channelTile();
        // Shared rows keep their channel lists when their range module changes.
        // Remove their old indexed output before replacing that module; otherwise
        // register() sees unchanged subscriptions and retains stale sources.
        if(tile instanceof com.vandorlabs.tiles.TileEntityAnimatedScreenSelector){
            com.vandorlabs.tiles.TileEntityAnimatedScreenSelector screen=(com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)tile;
            for(int side=0;side<2;side++)for(com.vandorlabs.tiles.RedstoneScreenContents.Row row:screen.redstoneScreen(side).rows())RedstoneChannels.unregister(row);
        }
        RedstoneChannels.unregister(member);
        NBTTagCompound data=tile.getTileData(),slots=data.getCompoundTag(KEY);
        if(stack.isEmpty())slots.removeTag(member.transmitterSlot());else {ItemStack copy=stack.copy();copy.setCount(1);slots.setTag(member.transmitterSlot(),copy.writeToNBT(new NBTTagCompound()));}
        data.setTag(KEY,slots);CACHE.remove(member);
        // Pure controls subscribe to their inserted item's channels, receivers keep their input selection.
        if(outputOnly(member))
            member.setRedstoneChannels(channels(member));
        tile.markDirty();RedstoneChannels.register(member);
        if(tile instanceof com.vandorlabs.tiles.TileEntityAnimatedScreenSelector){
            com.vandorlabs.tiles.TileEntityAnimatedScreenSelector screen=(com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)tile;
            for(int i=0;i<2;i++){
                screen.redstoneScreen(i).finishLoading();
                if(tile.getWorld() instanceof com.vandorlabs.vehicle.VehicleWorld){com.vandorlabs.vehicle.VehicleWorld view=(com.vandorlabs.vehicle.VehicleWorld)tile.getWorld();if(view.craft!=null&&!view.parent.isRemote)for(com.vandorlabs.tiles.RedstoneScreenContents.Row row:screen.redstoneScreen(i).rows())view.craft.signals.transmitterChanged(row);}
            }
        }
        if(tile instanceof com.vandorlabs.tiles.TileEntityCockpit)((com.vandorlabs.tiles.TileEntityCockpit)tile).sharedTransmitterChanged();
        if(tile.getWorld() instanceof com.vandorlabs.vehicle.VehicleWorld){com.vandorlabs.vehicle.VehicleWorld view=(com.vandorlabs.vehicle.VehicleWorld)tile.getWorld();if(view.craft!=null&&!view.parent.isRemote)view.craft.signals.transmitterChanged(member);}
        if(tile.getWorld()!=null&&!tile.getWorld().isRemote){net.minecraft.block.state.IBlockState state=tile.getWorld().getBlockState(tile.getPos());tile.getWorld().notifyBlockUpdate(tile.getPos(),state,state,2);}
    }
}
