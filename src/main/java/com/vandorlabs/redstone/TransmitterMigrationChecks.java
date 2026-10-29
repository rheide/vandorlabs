package com.vandorlabs.redstone;

import com.vandorlabs.items.ItemRedstoneTransmitter;
import com.vandorlabs.tiles.TileEntityRedstoneChannel;
import com.vandorlabs.vehicle.*;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.util.math.BlockPos;

/** Old saved outputs migrate once; their original opaque capability bytes remain untouched. */
public final class TransmitterMigrationChecks {
    private static void require(boolean value,String text){if(!value)throw new IllegalStateException("transmitter migration: "+text);}
    public static void run(){
        NBTTagCompound old=new TileEntityRedstoneChannel().writeToNBT(new NBTTagCompound());old.removeTag(TransmitterSlots.SCHEMA);ChannelData.write(old,ChannelList.of(41,73));old.setInteger("RedstoneChannel",41);
        NBTTagCompound caps=new NBTTagCompound();caps.setString("foreign:opaque","preserve me");old.setTag("ForgeCaps",caps);NBTTagCompound original=old.copy();
        NBTTagCompound migrated=TransmitterSlots.normalize(old);ItemStack module=main(migrated);
        require(ItemRedstoneTransmitter.valid(module)&&module.getMetadata()==2&&module.getCount()==1,"legacy configured source receives exactly one Long Range item");
        require(ItemRedstoneTransmitter.channels(module).equals(ChannelList.of(41,73)),"all configured channels migrate");require(old.equals(original)&&migrated.getCompoundTag("ForgeCaps").equals(caps),"caller snapshot and opaque ForgeCaps unchanged");
        require(TransmitterSlots.normalize(migrated).equals(migrated),"normalization idempotent");
        TileEntityRedstoneChannel loaded=new TileEntityRedstoneChannel();loaded.readFromNBT(old);require(TransmitterSlots.get(loaded).getMetadata()==2,"actual tile read installs migrated inventory");
        TransmitterSlots.set(loaded,ItemStack.EMPTY);NBTTagCompound removed=loaded.writeToNBT(new NBTTagCompound());loaded.readFromNBT(removed);require(TransmitterSlots.get(loaded).isEmpty()&&removed.hasKey(TransmitterSlots.SCHEMA),"removed module stays absent across save/load");
        ChannelData.write(removed,ChannelList.of(999));loaded.readFromNBT(removed);require(TransmitterSlots.get(loaded).isEmpty(),"new channel configuration cannot replenish removed item");
        TileEntityRedstoneChannel fresh=new TileEntityRedstoneChannel();NBTTagCompound freshSave=fresh.writeToNBT(new NBTTagCompound());ChannelData.write(freshSave,ChannelList.of(99));fresh.readFromNBT(freshSave);require(TransmitterSlots.get(fresh).isEmpty(),"new configured blocks receive no free item");
        NBTTagCompound blank=original.copy();ChannelData.write(blank,ChannelList.EMPTY);blank.setInteger("RedstoneChannel",0);require(main(TransmitterSlots.normalize(blank)).isEmpty(),"unconfigured legacy source grants no item");
        NBTTagCompound priorRemoval=original.copy(),emptyInventory=new NBTTagCompound();emptyInventory.setTag("VandorTransmitters",new NBTTagCompound());priorRemoval.setTag("ForgeData",emptyInventory);require(main(TransmitterSlots.normalize(priorRemoval)).isEmpty(),"earlier empty slot inventory proves removal even before schema marker");
        NBTTagCompound partial=original.copy();partial.removeTag("x");require(!TransmitterSlots.normalize(partial).hasKey(TransmitterSlots.SCHEMA),"portable partial settings are not old placed tiles");
        VehicleStructure.Cell cell=new VehicleStructure.Cell(BlockPos.ORIGIN,Blocks.STONE.getDefaultState(),old);require(cell.tileData().equals(migrated),"previous snapshot canonicalizes before exact own-field comparison");
        require(VehicleTileData.translated(old,BlockPos.ORIGIN).equals(migrated),"copy/rotation translation shares canonical migration");
        NBTTagCompound screen=original.copy();screen.setString("id","vandorlabs:programmable_viewscreen");screen.setInteger("RedstoneChannel",0);ChannelData.write(screen,ChannelList.EMPTY);NBTTagCompound surface=new NBTTagCompound();NBTTagList rows=new NBTTagList();for(int channel:new int[]{51,52}){NBTTagCompound row=new NBTTagCompound();ChannelData.write(row,ChannelList.of(channel));rows.appendTag(row);}surface.setTag("Rows",rows);screen.setTag("RedstonePrimary",surface);
        NBTTagCompound screenSlots=TransmitterSlots.normalize(screen).getCompoundTag("ForgeData").getCompoundTag("VandorTransmitters");require(screenSlots.getKeySet().size()==2&&ItemRedstoneTransmitter.channels(new ItemStack(screenSlots.getCompoundTag("row_0_1"))).equals(ChannelList.of(52)),"each separate legacy screen row keeps its own configured module");
        NBTTagCompound shared=TransmitterSlots.normalize(screen);shared.getCompoundTag("ForgeData").getCompoundTag("VandorTransmitters").removeTag("main");
        NBTTagCompound sharedAfterRemoval=TransmitterSlots.normalize(shared);require(!sharedAfterRemoval.getCompoundTag("ForgeData").getCompoundTag("VandorTransmitters").hasKey("main",10)&&sharedAfterRemoval.getCompoundTag("ForgeData").getCompoundTag("VandorTransmitters").hasKey("row_0_1",10),"removed shared module stays empty while surplus legacy items remain recoverable");
        System.out.println("[vandorlabs][reprolab] transmitter-migration PASS old configured blank fresh remove reload reconfigure copy snapshot opaque-ForgeCaps separate-screen-rows");
    }
    private static ItemStack main(NBTTagCompound tag){return new ItemStack(tag.getCompoundTag("ForgeData").getCompoundTag("VandorTransmitters").getCompoundTag("main"));}
    private TransmitterMigrationChecks(){}
}
