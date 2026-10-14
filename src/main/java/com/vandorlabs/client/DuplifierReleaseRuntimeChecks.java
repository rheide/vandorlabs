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
    static void run(EntityPlayer player){
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
            require(copy.getHeight()==3 && copy.getRedstoneMode()==2 && !copy.isTaxiMode() && copy.getRedstoneChannels().equals(seat.getRedstoneChannels()),"all pilot settings");
            ItemStack crafted=ProgrammableSettings.applyToItem(new ItemStack(ModBlocks.PILOT_SEAT),captured);require(!crafted.isEmpty(),"pilot configured item");
            NBTTagCompound data=crafted.getSubCompound("BlockEntityTag");require(data.getInteger("PilotHeight")==3 && data.getInteger("PilotRedstoneMode")==2 && !data.getBoolean("TaxiMode") && !data.hasUniqueId("VehicleOwner"),"pilot item settings without owner");
            Block gear=Block.getBlockFromName("vandorlabs:landing_gear");require(gear!=null,"gear exists");
            NBTTagCompound gearSettings=new NBTTagCompound();gearSettings.setInteger(ProgrammableSettings.GEAR_SIZE,4);gearSettings.setInteger(ProgrammableSettings.GEAR_LENGTH,64);gearSettings.setInteger(ProgrammableSettings.GEAR_MODE,2);gearSettings.setIntArray(ProgrammableSettings.CHANNEL,new int[]{435,436});
            crafted=ProgrammableSettings.applyToItem(new ItemStack(gear),gearSettings);require(!crafted.isEmpty() && crafted.getSubCompound("BlockEntityTag").getInteger("GearSize")==4,"largest gear configured item");
            ItemStack legacy=new ItemStack(ModItems.DUPLIFIER);NBTTagCompound mask=new NBTTagCompound();mask.setLong(DuplifierApplyOptions.TAG,0);mask.setInteger("DuplifierApplyOptionCount",63);legacy.setTagCompound(mask);
            require(DuplifierApplyOptions.mask(legacy)==Long.MIN_VALUE,"new configuration option preserves old disabled choices");DuplifierApplyOptions.setMask(legacy,0);require(DuplifierApplyOptions.mask(legacy)==0,"64-option disabled mask persists");
        }finally{for(BlockPos pos:BlockPos.getAllInBox(source.add(-8,-8,-8),target.add(8,8,8)))world.setBlockToAir(pos);}
        System.out.println("[vandorlabs][reprolab] duplifier-release PASS canopy-motion shape hinges finishes layout channels follower-roots pilot-settings crafting largest-gear selective-64-bit-mask");
    }
}
