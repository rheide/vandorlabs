package com.vandorlabs.redstone;

import com.vandorlabs.items.ItemRedstoneTransmitter;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.tiles.TileEntityRedstoneChannel;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Real installed development modules become settings; released saves never gain hardware. */
public final class TransmitterMigrationChecks {
    public static void run(){
        NBTTagCompound released=new TileEntityRedstoneChannel().writeToNBT(new NBTTagCompound());
        ChannelData.write(released,ChannelList.of(41,73));
        NBTTagCompound caps=new NBTTagCompound();caps.setString("foreign:opaque","preserve me");released.setTag("ForgeCaps",caps);
        require(TransmitterSlots.normalize(released)==released,"released configured tiles stay byte-for-byte unchanged");
        NBTTagCompound old=released.copy(),forge=new NBTTagCompound(),slots=new NBTTagCompound();
        ItemStack item=new ItemStack(ModItems.REDSTONE_TRANSMITTER,1,1);ItemRedstoneTransmitter.configure(item,ChannelList.of(41,73));
        slots.setTag("main",item.writeToNBT(new NBTTagCompound()));forge.setTag("VandorTransmitters",slots);old.setTag("ForgeData",forge);
        NBTTagCompound original=old.copy(),migrated=TransmitterSlots.normalize(old);
        require(old.equals(original)&&migrated.getCompoundTag("ForgeCaps").equals(caps),"caller and opaque capabilities preserved");
        require(!migrated.getCompoundTag("ForgeData").hasKey("VandorTransmitters"),"obsolete item inventory removed");
        TileEntityRedstoneChannel loaded=new TileEntityRedstoneChannel();loaded.readFromNBT(old);
        require(TransmitterSlots.channels(loaded).equals(ChannelList.of(41,73))&&TransmitterSlots.range(loaded)==64,"installed module channels and range survive actual tile read");
        NBTTagCompound saved=loaded.writeToNBT(new NBTTagCompound());loaded.readFromNBT(saved);
        require(TransmitterSlots.range(loaded)==64&&TransmitterSlots.channels(loaded).equals(ChannelList.of(41,73)),"built-in settings survive roundtrip");
        require(TransmitterSlots.normalize(migrated)==migrated,"conversion is idempotent");
        NBTTagCompound explicit=old.copy(),all=new NBTTagCompound(),main=new NBTTagCompound();main.setInteger("Range",8);main.setIntArray("Channels",new int[]{99});main.setBoolean("Local",false);all.setTag("main",main);explicit.getCompoundTag("ForgeData").setTag("VandorTransmission",all);
        NBTTagCompound retained=TransmitterSlots.normalize(explicit).getCompoundTag("ForgeData").getCompoundTag("VandorTransmission").getCompoundTag("main");
        require(retained.getInteger("Range")==64&&java.util.Arrays.equals(retained.getIntArray("Channels"),new int[]{99})&&!retained.getBoolean("Local"),"legacy host resets range64 while preserving explicit channels and local setting");
        NBTTagCompound noModule=explicit.copy();noModule.getCompoundTag("ForgeData").removeTag("VandorTransmitters");require(TransmitterSlots.normalize(noModule)==noModule&&noModule.getCompoundTag("ForgeData").getCompoundTag("VandorTransmission").getCompoundTag("main").getInteger("Range")==8,"no-module built-in range remains unchanged");
        NBTTagCompound cockpit=old.copy();cockpit.setString("id","vandorlabs:cockpit");
        net.minecraft.nbt.NBTTagList controls=new net.minecraft.nbt.NBTTagList();NBTTagCompound control=new NBTTagCompound();control.setString("Id","toggle");controls.appendTag(control);cockpit.setTag("CockpitControls",controls);
        NBTTagCompound perControl=new NBTTagCompound();perControl.setTag("cockpit_toggle",slots.getTag("main"));cockpit.getCompoundTag("ForgeData").setTag("VandorTransmitters",perControl);
        NBTTagCompound convertedCockpit=TransmitterSlots.normalize(cockpit);
        require(ChannelData.read(convertedCockpit.getTagList("CockpitControls",10).getCompoundTagAt(0),0).equals(ChannelList.of(41,73)),"old per-control targets survive shared built-in range conversion");
        require(convertedCockpit.getCompoundTag("ForgeData").getCompoundTag("VandorTransmission").getCompoundTag("main").getInteger("Range")==64,"old per-control range becomes shared built-in range");
        NBTTagCompound screen=old.copy();screen.setString("id","vandorlabs:programmable_viewscreen");NBTTagCompound surface=new NBTTagCompound();net.minecraft.nbt.NBTTagList rows=new net.minecraft.nbt.NBTTagList();rows.appendTag(new NBTTagCompound());surface.setTag("Rows",rows);screen.setTag("RedstoneSecondary",surface);NBTTagCompound rowSlots=new NBTTagCompound();rowSlots.setTag("row_1_0",slots.getTag("main"));screen.getCompoundTag("ForgeData").setTag("VandorTransmitters",rowSlots);
        require(ChannelData.read(TransmitterSlots.normalize(screen).getCompoundTag("RedstoneSecondary").getTagList("Rows",10).getCompoundTagAt(0),0).equals(ChannelList.of(41,73)),"old secondary row targets survive conversion");
        savedServerLayouts();
        System.out.println("[vandorlabs][reprolab] transmitter-migration PASS released-settings-no-items installed-module-to-built-in channels range reload opaque-ForgeCaps");
    }
    /** Relevant fields from saved server layouts, without locations, titles or owner metadata. */
    private static void savedServerLayouts(){
        int[] targets={8009,8010,8011,8012,8003};
        NBTTagCompound screen=new NBTTagCompound(),forge=new NBTTagCompound(),modules=new NBTTagCompound(),surface=new NBTTagCompound();
        net.minecraft.nbt.NBTTagList rows=new net.minecraft.nbt.NBTTagList();
        for(int i=0;i<targets.length;i++){
            NBTTagCompound row=new NBTTagCompound();ChannelData.write(row,ChannelList.of(targets[i]));
            row.setInteger("Level",i==4?15:0);row.setIntArray("Levels",new int[]{i==4?15:0});rows.appendTag(row);
            modules.setTag(i==0?"main":"row_0_"+i,savedModule(targets[i]));
        }
        surface.setTag("Rows",rows);screen.setTag("RedstonePrimary",surface);screen.setString("id","vandorlabs:programmable_viewscreen");
        forge.setTag("VandorTransmitters",modules);forge.setBoolean("VandorSharedScreenTransmitter",true);screen.setTag("ForgeData",forge);
        NBTTagCompound migrated=TransmitterSlots.normalize(screen),settings=migrated.getCompoundTag("ForgeData").getCompoundTag("VandorTransmission").getCompoundTag("main");
        require(settings.getInteger("Range")==64&&java.util.Arrays.equals(settings.getIntArray("Channels"),new int[]{8009}),"saved five-row host restores main range and shared default");
        require(migrated.getCompoundTag("RedstonePrimary").equals(surface),"saved per-row channels and ON level remain exact");
        for(String id:new String[]{"redstone_channel","redstone_light","programmable_ramp"}){
            int channel=id.equals("redstone_light")?8010:1010;
            NBTTagCompound source=new NBTTagCompound(),data=new NBTTagCompound(),inventory=new NBTTagCompound();source.setString("id","vandorlabs:"+id);ChannelData.write(source,ChannelList.of(channel));
            inventory.setTag("main",savedModule(channel));data.setTag("VandorTransmitters",inventory);
            NBTTagCompound explicit=new NBTTagCompound();
            if(id.equals("redstone_channel")){NBTTagCompound config=new NBTTagCompound();config.setInteger("Range",256);config.setIntArray("Channels",new int[]{1010});config.setBoolean("Local",true);explicit.setTag("main",config);data.setTag("VandorTransmission",explicit);}
            source.setTag("ForgeData",data);NBTTagCompound normalized=TransmitterSlots.normalize(source);
            NBTTagCompound config=normalized.getCompoundTag("ForgeData").getCompoundTag("VandorTransmission").getCompoundTag("main");
            require(config.getInteger("Range")==64&&java.util.Arrays.equals(config.getIntArray("Channels"),new int[]{channel}),"saved "+id+" restores exact output settings");
            require(ChannelData.read(normalized,0).equals(ChannelList.of(channel)),"saved "+id+" input channels remain independent");
            if(id.equals("redstone_channel"))require(config.getBoolean("Local")&&java.util.Arrays.equals(config.getIntArray("Channels"),new int[]{1010}),"saved existing Local/Channels settings remain exact while range resets64");
            require(!normalized.getCompoundTag("ForgeData").hasKey("VandorTransmitters"),"saved "+id+" no longer owns hardware");
        }
        System.out.println("[vandorlabs][reprolab] saved-server-transmission-layouts PASS five-independent-screen-rows active-level main-settings light-input ramp-input installed-range64 existing-local-setting");
    }
    private static NBTTagCompound savedModule(int channel){
        ItemStack item=new ItemStack(ModItems.REDSTONE_TRANSMITTER,1,2);ItemRedstoneTransmitter.configure(item,ChannelList.of(channel));
        NBTTagCompound saved=item.writeToNBT(new NBTTagCompound()),caps=new NBTTagCompound();caps.setTag("astralsorcery:cap_item_amulet_holder",new NBTTagCompound());saved.setTag("ForgeCaps",caps);return saved;
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("transmitter migration: "+message);}
    private TransmitterMigrationChecks(){}
}
