package com.vandorlabs.redstone;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

/** Built-in range, channel and local-output settings; blocks have no transmitter inventory. */
public final class TransmitterSlots {
    private static final String SETTINGS="VandorTransmission";
    public static final int[] RANGES={4,8,16,32,64,128,256};
    private static final java.lang.reflect.Field CUSTOM_DATA=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(TileEntity.class,"customTileData");
    private static final NBTTagCompound NO_DATA=new NBTTagCompound();
    private TransmitterSlots(){}
    /** Upgrade installed development-version modules into settings, without creating inventory. */
    public static NBTTagCompound normalize(NBTTagCompound source){
        NBTTagCompound original=source.getCompoundTag("ForgeData");
        if(!original.hasKey("VandorTransmitters",10))return source;
        NBTTagCompound result=source.copy(),forge=result.getCompoundTag("ForgeData");
        NBTTagCompound modules=forge.getCompoundTag("VandorTransmitters"),settings=forge.getCompoundTag(SETTINGS);
        boolean installed=false;ChannelList main=ChannelList.EMPTY;
        for(String slot:new java.util.TreeSet<>(modules.getKeySet())){
            net.minecraft.item.ItemStack item=new net.minecraft.item.ItemStack(modules.getCompoundTag(slot));
            int range=com.vandorlabs.items.ItemRedstoneTransmitter.range(item);
            if(range<0)continue;installed=true;
            ChannelList channels=com.vandorlabs.items.ItemRedstoneTransmitter.channels(item);
            if(slot.equals("main"))main=channels;
            if(slot.startsWith("cockpit_")){
                net.minecraft.nbt.NBTTagList controls=result.getTagList("CockpitControls",10);
                for(int i=0;i<controls.tagCount();i++){
                    NBTTagCompound control=controls.getCompoundTagAt(i);
                    if(slot.equals("cockpit_"+control.getString("Id"))&&ChannelData.read(control,0).isEmpty()){
                        ChannelData.write(control,channels);
                        if(!control.hasKey("LevelChannels",11))control.setIntArray("LevelChannels",channels.toArray());
                    }
                }
            }else if(slot.startsWith("row_")){
                String[] coordinates=slot.split("_");
                if(coordinates.length==3)try{
                    int side=Integer.parseInt(coordinates[1]),row=Integer.parseInt(coordinates[2]);
                    if(side>=0&&side<2){net.minecraft.nbt.NBTTagList rows=result.getCompoundTag(side==0?"RedstonePrimary":"RedstoneSecondary").getTagList("Rows",10);
                        if(row>=0&&row<rows.tagCount()&&ChannelData.read(rows.getCompoundTagAt(row),0).isEmpty())ChannelData.write(rows.getCompoundTagAt(row),channels);}
                }catch(NumberFormatException invalidSlot){/* Ignore an unrecognized development slot. */}
            }
        }
        if(installed){
            NBTTagCompound configured=settings.getCompoundTag("main");
            configured.setInteger("Range",64);
            if(!configured.hasKey("Channels",11)&&!main.isEmpty())configured.setIntArray("Channels",main.toArray());
            settings.setTag("main",configured);forge.setTag(SETTINGS,settings);
        }
        forge.removeTag("VandorTransmitters");forge.removeTag("VandorSharedScreenTransmitter");result.removeTag("TransmitterSchema");
        if(forge.hasNoTags())result.removeTag("ForgeData");else result.setTag("ForgeData",forge);
        return result;
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
        return outputOnly(member)?member.getRedstoneChannels():ChannelList.EMPTY;
    }
    public static boolean validRange(int range){for(int allowed:RANGES)if(range==allowed)return true;return false;}
    public static int range(RedstoneChannelMember member){
        NBTTagCompound configured=configuration(member);
        if(configured.hasKey("Range",3)){int value=configured.getInteger("Range");return validRange(value)?value:16;}
        return supported(member)?16:-1;
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
    /** Configure the built-in transmitter without consuming or storing an item. */
    public static void configure(RedstoneChannelMember member,ChannelList channels,int range,boolean local){
        if(channels==null||!validRange(range)||!supported(member))throw new IllegalArgumentException("Invalid transmission settings");
        if(channels.equals(channels(member))&&range==range(member)&&local==localRedstone(member.channelTile())
                &&(!outputOnly(member)||channels.equals(member.getRedstoneChannels())))return;
        TileEntity tile=member.channelTile();boolean previousLocal=localRedstone(tile);NBTTagCompound data=tile.getTileData(),all=data.getCompoundTag(SETTINGS),next=new NBTTagCompound();
        next.setIntArray("Channels",channels.toArray());next.setInteger("Range",range);next.setBoolean("Local",local);
        all.setTag(member.transmitterSlot(),next);data.setTag(SETTINGS,all);
        settingsChanged(member);
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
    public static void settingsChanged(RedstoneChannelMember member){
        net.minecraft.world.World world=member.channelTile().getWorld();
        boolean placed=world!=null&&!world.isRemote;
        if(placed)RedstoneChannels.beginTransfer(world);
        try{publishSettings(member);}finally{if(placed)RedstoneChannels.endTransfer(world);}
    }
    private static void publishSettings(RedstoneChannelMember member){
        TileEntity tile=member.channelTile();
        // Shared rows retain their channel lists when built-in transmission changes.
        // Remove their old indexed output before changing these settings; otherwise
        // register() sees unchanged subscriptions and retains stale sources.
        if(tile instanceof com.vandorlabs.tiles.TileEntityAnimatedScreenSelector){
            com.vandorlabs.tiles.TileEntityAnimatedScreenSelector screen=(com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)tile;
            for(int side=0;side<2;side++)for(com.vandorlabs.tiles.RedstoneScreenContents.Row row:screen.redstoneScreen(side).rows())RedstoneChannels.unregister(row);
        }
        RedstoneChannels.unregister(member);
        // Pure controls use built-in output channels; receivers retain their input selection.
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
