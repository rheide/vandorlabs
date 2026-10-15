package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.canopy.*;
import com.vandorlabs.items.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Configuration transfers exercise the actual tool and configured-item paths. */
final class DuplifierReleaseRuntimeChecks {
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Duplifier release: "+message);}
    private static void controller(World world,BlockPos source,BlockPos target,EntityPlayer player){
        world.setBlockState(source,ModBlocks.VEHICLE_CONTROLLER.getDefaultState(),2);world.setBlockState(target,ModBlocks.VEHICLE_CONTROLLER.getDefaultState(),2);
        TileEntityVehicleController from=(TileEntityVehicleController)world.getTileEntity(source),to=(TileEntityVehicleController)world.getTileEntity(target);
        from.owner=player.getUniqueID();from.setTaxiMode(false);from.configure(2,0,world);from.setHeight(3);from.setVehicleChannels(ChannelList.of(443,444),world);
        ChannelList[] modes={ChannelList.of(445),ChannelList.of(446,447),ChannelList.of(448)};for(int mode=0;mode<3;mode++)from.setModeChannels(mode,modes[mode]);
        NBTTagCompound settings=ProgrammableSettings.capture(world,source);
        require(!settings.hasKey(ProgrammableSettings.CHAIR_HEIGHT)&&!settings.getCompoundTag(ProgrammableSettings.BLOCK_CONFIGURATION).hasKey("TaxiMode"),"controller capture excludes seat Height and Taxi");
        require(ProgrammableSettings.apply(world,target,settings,player),"controller world copy apply");
        require(to.getRedstoneMode()==2&&to.getRedstoneChannels().equals(from.getRedstoneChannels())&&to.getHeight()==0&&to.isTaxiMode(),"controller copy transfers trigger and toggle channels independently from seat settings");
        for(int mode=0;mode<3;mode++)require(to.modeChannels(mode).equals(modes[mode]),"controller world mode channels "+mode);
        ItemStack item=ProgrammableSettings.applyToItem(new ItemStack(ModBlocks.VEHICLE_CONTROLLER),settings);require(!item.isEmpty(),"controller configured item");
        NBTTagCompound data=item.getSubCompound("BlockEntityTag");require(data!=null&&!data.hasUniqueId("VehicleOwner"),"controller configured item has no copied world owner");
        TileEntityVehicleController crafted=new TileEntityVehicleController();crafted.readFromNBT(data);
        require(crafted.controlsInitialized()&&crafted.getRedstoneMode()==2&&crafted.getRedstoneChannels().equals(from.getRedstoneChannels())&&crafted.getHeight()==0&&crafted.isTaxiMode(),"controller item persists trigger and toggle channels only");
        for(int mode=0;mode<3;mode++)require(crafted.modeChannels(mode).equals(modes[mode]),"controller item mode channels "+mode);
    }
    static void run(EntityPlayer player){
        DuplifierMaskRuntimeChecks.run();
        World world=player.world;BlockPos source=new BlockPos(80,130,80),target=source.east(12);
        try{
            for(String kind:new String[]{"canopy","front_canopy_glass","large_front_canopy_glass","regular_canopy_glass","angled_front_1x1","angled_front_1x2","space_visor_2x1","space_visor_3x1","space_visor_5x1","space_visor_5x1_double_height"}){
                Block found=Block.getBlockFromName("vandorlabs:"+kind);require(found instanceof BlockCanopy,"registered canopy "+kind);
                BlockCanopy block=(BlockCanopy)found;
                LargeCanopySettings settings=new LargeCanopySettings();settings.material=ScreenHousingTextures.DEFAULT_TRAPDOOR;settings.tileMaterial=true;settings.shade=13;settings.tint=2;
                settings.depth=32;settings.scale=45;settings.openable=true;settings.hinge=0;settings.motion=1;settings.slideForward=true;settings.angle=2;settings.redstone=true;settings.channels=ChannelList.of(431,432);
                ItemStack placed=new ItemStack(block);NBTTagCompound root=new NBTTagCompound();settings.write(root);placed.setTagCompound(root);
                world.setBlockState(source,block.getDefaultState(),2);block.onBlockPlacedBy(world,source,block.getDefaultState(),player,placed);
                world.setBlockState(target,block.getDefaultState(),2);block.onBlockPlacedBy(world,target,block.getDefaultState(),player,new ItemStack(block));
                TileEntityCanopy from=TileEntityCanopy.at(world,source),to=TileEntityCanopy.at(world,target);
                ItemStack tool=new ItemStack(ModItems.DUPLIFIER);
                BlockPos follower=from.cells().get(from.cells().size()-1);
                require(ItemDuplifier.copyFrom(world,follower,tool)!=null,"copy follower "+kind);
                NBTTagCompound captured=tool.getSubCompound(ItemDuplifier.SETTINGS_TAG);
                require(ItemDuplifier.applyTo(world,target,tool,player),"tool apply "+kind);
                require(to.large.material==settings.material && to.large.tileMaterial && to.large.tint==2 && to.large.shade==13,"complete finish "+kind);
                if(block.opening())require(to.large.motion==1 && to.large.slideForward && to.large.angle==2 && to.large.redstone && to.large.channels.equals(settings.channels),"opening and channels "+kind);
                if(from.resizable())require(to.large.depth==32 && to.large.scale==45 && to.large.openable && to.large.hinge==0 && to.large.redstone && to.large.channels.equals(settings.channels),"front shape and channels "+kind);
                ItemStack crafted=ProgrammableSettings.applyToItem(new ItemStack(block),captured);require(!crafted.isEmpty(),"configured item "+kind);
                LargeCanopySettings itemSettings=new LargeCanopySettings();itemSettings.read(crafted.getTagCompound());require(itemSettings.material==settings.material && itemSettings.tileMaterial && itemSettings.tint==2,"crafted finish "+kind);
                require(!crafted.hasTagCompound() || !crafted.getTagCompound().hasKey("BlockEntityTag"),"no world ownership in canopy item");
                NBTTagCompound only=DuplifierApplyOptions.selected(captured,1L<<63);require(!only.hasKey(ProgrammableSettings.GLASS_MATERIAL),"configuration mask independent of finish");
                if(block.opening() || from.resizable())require(only.hasKey(ProgrammableSettings.BLOCK_CONFIGURATION),"64th option survives mask");
                world.setBlockToAir(source);world.setBlockToAir(target);
            }
            world.setBlockState(source,ModBlocks.PILOT_SEAT.getDefaultState(),2);world.setBlockState(target,ModBlocks.PILOT_SEAT.getDefaultState(),2);
            TileEntityPilotSeat seat=(TileEntityPilotSeat)world.getTileEntity(source);seat.configure(2,3,world);seat.setTaxiMode(false);seat.setVehicleChannels(ChannelList.of(433,434),world);
            NBTTagCompound captured=ProgrammableSettings.capture(world,source);
            require(ProgrammableSettings.apply(world,target,captured,player),"pilot apply");TileEntityPilotSeat copy=(TileEntityPilotSeat)world.getTileEntity(target);
            require(copy.getHeight()==3 && !copy.isTaxiMode() && copy.getRedstoneMode()==1 && copy.getRedstoneChannels().isEmpty(),"pilot transfers Height and Taxi without legacy redstone controls");
            require(!captured.hasKey(ProgrammableSettings.TRIGGER)&&!captured.hasKey(ProgrammableSettings.CHANNEL),"seat capture excludes moved controller redstone settings");
            ItemStack crafted=ProgrammableSettings.applyToItem(new ItemStack(ModBlocks.PILOT_SEAT),captured);require(!crafted.isEmpty(),"pilot configured item");
            NBTTagCompound data=crafted.getSubCompound("BlockEntityTag");require(data.getInteger("PilotHeight")==3 && data.getInteger("PilotRedstoneMode")==1 && com.vandorlabs.redstone.ChannelData.read(data,0).isEmpty() && !data.getBoolean("TaxiMode") && !data.hasUniqueId("VehicleOwner"),"pilot item transfers only Height and Taxi without owner");
            controller(world,source,target,player);
            Block gear=Block.getBlockFromName("vandorlabs:landing_gear");require(gear!=null,"gear exists");
            NBTTagCompound gearSettings=new NBTTagCompound();gearSettings.setInteger(ProgrammableSettings.GEAR_SIZE,4);gearSettings.setInteger(ProgrammableSettings.GEAR_LENGTH,64);gearSettings.setInteger(ProgrammableSettings.GEAR_MODE,2);gearSettings.setIntArray(ProgrammableSettings.CHANNEL,new int[]{435,436});
            crafted=ProgrammableSettings.applyToItem(new ItemStack(gear),gearSettings);require(!crafted.isEmpty() && crafted.getSubCompound("BlockEntityTag").getInteger("GearSize")==4,"largest gear configured item");
            ItemStack legacy=new ItemStack(ModItems.DUPLIFIER);NBTTagCompound mask=new NBTTagCompound();mask.setLong(DuplifierApplyOptions.TAG,0);mask.setInteger("DuplifierApplyOptionCount",63);legacy.setTagCompound(mask);
            require(DuplifierApplyOptions.mask(legacy)==Long.MIN_VALUE,"new configuration option preserves old disabled choices");DuplifierApplyOptions.setMask(legacy,0);require(DuplifierApplyOptions.mask(legacy)==0,"64-option disabled mask persists");
        }finally{for(BlockPos pos:BlockPos.getAllInBox(source.add(-8,-8,-8),target.add(8,8,8)))world.setBlockToAir(pos);}
        System.out.println("[vandorlabs][reprolab] duplifier-release PASS canopy-motion shape hinges finishes layout channels follower-roots pilot-height-taxi controller-trigger-toggle-three-mode-channels world-item-copy crafting largest-gear scalable-mask legacy-bit-compatibility");
    }
}
