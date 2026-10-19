package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.gear.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import java.util.*;

/** Saved blocks and existing stacks convert without overwriting neighbors or losing constituent parts. */
final class LegacyGearMigrationRuntimeChecks {
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("legacy gear migration: "+message);}
    private static void clear(WorldServer world,BlockPos root){for(BlockPos at:BlockPos.getAllInBox(root.add(-3,-6,-3),root.add(4,2,4)))if(!world.isAirBlock(at))world.setBlockToAir(at);}
    private static TileEntityLandingGear place(WorldServer world,BlockPos root,BlockTelescopicLandingGear block,int size,EnumFacing facing,int pixels,boolean deployed){
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)world.setBlockState(root.add(x,1,z),Blocks.IRON_BLOCK.getDefaultState());
        world.setBlockState(root,block.getDefaultState().withProperty(BlockVandorDirectional.FACING,facing));
        TileEntityLandingGear tile=(TileEntityLandingGear)world.getTileEntity(root);
        require(tile.configure(1,com.vandorlabs.redstone.ChannelList.of(571,572),pixels,size),"legacy fixture settings");
        require(block.setExtended(world,root,deployed),"legacy fixture target");tile.progress=tile.previous=deployed?pixels/16F:0;return tile;
    }
    static void run(WorldServer world,EntityPlayerMP player){
        BlockPos at=new BlockPos(600,160,240);BlockTelescopicLandingGear block=(BlockTelescopicLandingGear)Block.getBlockFromName("vandorlabs:landing_gear");
        require(block.getCreativeTabToDisplayOn()==null,"legacy integrated gear hidden from creative");
        require(net.minecraft.item.crafting.CraftingManager.REGISTRY.getObject(new ResourceLocation("vandorlabs:landing_gear"))==null,"legacy integrated gear recipe removed");
        try{for(int size=0;size<5;size++)for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int pixels:new int[]{8,32})for(boolean deployed:new boolean[]{false,true}){
            clear(world,at);TileEntityLandingGear old=place(world,at,block,size,facing,pixels,deployed);
            GearTileEntity root=LegacyGearMigration.convert(old);require(root!=null,"convert size="+size+" facing="+facing+" travel="+pixels+" target="+deployed);
            require(root.armWidth()==(size>=3?2:1)&&root.assembly().columnHeight()==Math.max(1,(pixels+15)/16),"matching size and whole-block stroke");
            require(root.target()==deployed&&root.progress()==(deployed?1:0)&&root.triggerMode==1&&root.usesLegacyInput()&&root.getRedstoneChannels().equals(com.vandorlabs.redstone.ChannelList.of(571,572)),"pose and automation preserved");
            root.update();require(root.target()==deployed&&root.progress()==(deployed?1:0),"unchanged signal retains migrated manual pose");
            for(BlockPos member:root.members())require(world.getBlockState(member).getBlock() instanceof GearBlock,"all new membership uses modular blocks");
            for(BlockPos pos:BlockPos.getAllInBox(at.add(-1,-5,-1),at.add(1,0,1)))require(world.getBlockState(pos).getBlock()!=block,"old helper cells retired");
            require(LegacyGearMigration.convert(old)==null,"stale source cannot duplicate conversion");
        }
        clear(world,at);TileEntityLandingGear blocked=place(world,at,block,0,EnumFacing.NORTH,16,false);world.setBlockState(at.down(),Blocks.OBSIDIAN.getDefaultState());NBTTagCompound before=blocked.writeToNBT(new NBTTagCompound());
        require(LegacyGearMigration.convert(blocked)==null&&world.getTileEntity(at)==blocked&&before.equals(blocked.writeToNBT(new NBTTagCompound()))&&world.getBlockState(at.down()).getBlock()==Blocks.OBSIDIAN,"blocked destination preserves source NBT and neighboring blocks");
        world.setBlockToAir(at.down());blocked.readFromNBT(before);blocked.onLoad();DeferredTileLoad.flush(world);require(world.getBlockState(at).getBlock()==GearRegistry.GEAR_EXTENDER,"saved gear converts at the deferred chunk-load boundary");
        for(boolean deployed:new boolean[]{false,true}){
            clear(world,at);TileEntityLandingGear first=place(world,at,block,0,EnumFacing.NORTH,16,deployed);GearTileEntity one=LegacyGearMigration.convert(first);require(one!=null,"first adjacent support migrates");
            NBTTagCompound turned=one.writeToNBT(new NBTTagCompound());turned.setString("GearDirection","EAST");one.readFromNBT(turned);
            TileEntityLandingGear second=place(world,at.east(),block,0,EnumFacing.NORTH,16,deployed);GearTileEntity joined=LegacyGearMigration.convert(second);
            require(joined!=null&&joined.columns().size()==2&&joined.assembly().feet().size()==2&&joined.direction()==com.vandorlabs.gear.core.GearMotion.Direction.EAST&&joined.target()==deployed,"adjacent migrated supports join without changing pose, direction or external membership");
        }
        items(world,player,block);
        }finally{clear(world,at);}
        System.out.println("[vandorlabs][reprolab] legacy-gear-migration PASS 80 size/facing/stroke/pose cases channels blocked neighbors deferred load full inventory and dropped stacks");
    }
    private static void items(WorldServer world,EntityPlayerMP player,BlockTelescopicLandingGear block){
        ItemStack old=new ItemStack(block,3);NBTTagCompound data=new NBTTagCompound();data.setInteger("GearSize",4);data.setInteger("ExtensionPixels",40);data.setInteger("RedstoneMode",2);com.vandorlabs.redstone.ChannelData.write(data,com.vandorlabs.redstone.ChannelList.of(13,17));old.setTagInfo("BlockEntityTag",data);
        List<ItemStack> parts=LegacyGearMigration.items(old);require(parts.size()==2&&parts.get(0).getItem()==Item.getItemFromBlock(GearRegistry.LARGE_GEAR_EXTENDER)&&parts.get(0).getCount()==9&&parts.get(1).getItem()==Item.getItemFromBlock(GearRegistry.LARGE_LANDING_WHEEL)&&parts.get(1).getCount()==3,"configured stacks yield every arm section and wheel");
        require(parts.get(0).getSubCompound("ModularGearSettings").getInteger("Trigger")==2&&com.vandorlabs.redstone.ChannelList.parse(parts.get(0).getSubCompound("ModularGearSettings").getString("Channels")).equals(com.vandorlabs.redstone.ChannelList.of(13,17)),"configured stack automation preserved");
        NBTTagList inventory=player.inventory.writeToNBT(new NBTTagList());int ticks=player.ticksExisted;Set<UUID> existing=new HashSet<>();for(Entity entity:world.loadedEntityList)existing.add(entity.getUniqueID());
        try{
            for(int slot=0;slot<36;slot++)player.inventory.setInventorySlotContents(slot,new ItemStack(Blocks.STONE,64));player.inventory.setInventorySlotContents(0,old.copy());player.ticksExisted=0;
            LegacyGearMigration.inventory(new net.minecraftforge.fml.common.gameevent.TickEvent.PlayerTickEvent(net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END,player));
            require(player.inventory.getStackInSlot(0).getItem()==parts.get(0).getItem()&&player.inventory.getStackInSlot(0).getCount()==9,"inventory arm conversion");
            int wheels=0;for(Entity entity:world.loadedEntityList)if(!existing.contains(entity.getUniqueID())&&entity instanceof EntityItem){ItemStack stack=((EntityItem)entity).getItem();if(stack.getItem()==parts.get(1).getItem())wheels+=stack.getCount();}require(wheels==3,"full inventory drops every wheel rather than deleting it");
            EntityItem dropped=new EntityItem(world,player.posX,player.posY+1,player.posZ,old.copy());require(world.spawnEntity(dropped),"legacy dropped fixture");LegacyGearMigration.finishDrops(world);
            require(dropped.getItem().getItem()==parts.get(0).getItem()&&dropped.getItem().getCount()==9,"dropped stack keeps original entity identity while converting arms");
        }finally{player.ticksExisted=ticks;player.inventory.readFromNBT(inventory);player.inventory.markDirty();player.inventoryContainer.detectAndSendChanges();for(Entity entity:new ArrayList<>(world.loadedEntityList))if(!existing.contains(entity.getUniqueID())&&entity instanceof EntityItem)world.removeEntityDangerously(entity);}
    }
}
