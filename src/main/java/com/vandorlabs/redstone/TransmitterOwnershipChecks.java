package com.vandorlabs.redstone;

import com.vandorlabs.items.*;
import com.vandorlabs.tiles.TileEntityWirelessRedstone;
import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import java.util.*;

/** Actual native Survival harvest and explosion drops must each leave exactly one usable module. */
public final class TransmitterOwnershipChecks {
    private TransmitterOwnershipChecks(){}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("transmitter ownership: "+message);}
    private static AxisAlignedBB area(BlockPos p){return new AxisAlignedBB(p.getX()-3,p.getY()-3,p.getZ()-3,p.getX()+4,p.getY()+4,p.getZ()+4);}
    private static int modules(ItemStack stack){int count=ItemRedstoneTransmitter.valid(stack)?stack.getCount():0;net.minecraft.nbt.NBTTagCompound host=stack.getSubCompound("BlockEntityTag");if(host!=null){net.minecraft.nbt.NBTTagCompound saved=host.getCompoundTag("ForgeData").getCompoundTag("VandorTransmitters");for(String key:saved.getKeySet()){ItemStack module=new ItemStack(saved.getCompoundTag(key));if(ItemRedstoneTransmitter.valid(module))count+=module.getCount();}}return count;}
    public static void run(WorldServer world,EntityPlayerMP actor){
        BlockPos mine=new BlockPos(2180,160,2180),creative=mine.east(16),blast=mine.east(32);Block block=Block.getBlockFromName("vandorlabs:redstone_transmitter_block");GameType mode=actor.interactionManager.getGameType();ItemStack held=actor.getHeldItemMainhand().copy();Vec3d before=actor.getPositionVector();Set<UUID> old=new HashSet<>();for(BlockPos p:new BlockPos[]{mine,creative,blast}){world.getChunkFromBlockCoords(p);require(world.isAirBlock(p),"isolated drop cell empty");for(EntityItem entity:world.getEntitiesWithinAABB(EntityItem.class,area(p)))old.add(entity.getUniqueID());}
        ItemStack module=new ItemStack(ModItems.REDSTONE_TRANSMITTER,1,1);ItemRedstoneTransmitter.configure(module,ChannelList.of(65285));module.setStackDisplayName("Configured transmitter ownership fixture");
        try{
            actor.setGameType(GameType.SURVIVAL);actor.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(Items.DIAMOND_PICKAXE));actor.setPosition(mine.getX()+.5,mine.getY(),mine.getZ()-2);
            world.setBlockState(mine,block.getDefaultState(),3);TransmitterSlots.set((TileEntityWirelessRedstone)world.getTileEntity(mine),module);TileEntityWirelessRedstone protectedSource=(TileEntityWirelessRedstone)world.getTileEntity(mine);net.minecraft.nbt.NBTTagCompound original=protectedSource.writeToNBT(new net.minecraft.nbt.NBTTagCompound());TransmitterDrops.protectionProbe(new net.minecraftforge.event.world.BlockEvent.BreakEvent(world,mine,world.getBlockState(mine),actor));require(original.equals(protectedSource.writeToNBT(new net.minecraft.nbt.NBTTagCompound())),"permission probe is read-only for module inventory");net.minecraftforge.event.world.BlockEvent.BreakEvent cancelled=new net.minecraftforge.event.world.BlockEvent.BreakEvent(world,mine,world.getBlockState(mine),actor);cancelled.setCanceled(true);TransmitterDrops.breaking(cancelled);require(original.equals(protectedSource.writeToNBT(new net.minecraft.nbt.NBTTagCompound())),"cancelled harvest retains exact module ownership");require(actor.interactionManager.tryHarvestBlock(mine)&&world.isAirBlock(mine),"native Survival harvest completes");check(world,mine,module,old,"mining");
            actor.setGameType(GameType.CREATIVE);actor.setPosition(creative.getX()+.5,creative.getY(),creative.getZ()-2);world.setBlockState(creative,block.getDefaultState(),3);TransmitterSlots.set((TileEntityWirelessRedstone)world.getTileEntity(creative),module);require(actor.interactionManager.tryHarvestBlock(creative)&&world.isAirBlock(creative),"native Creative harvest completes");check(world,creative,module,old,"Creative inventory mining");actor.setPosition(mine.getX()+.5,mine.getY(),mine.getZ()-2);
            world.setBlockState(blast,block.getDefaultState(),3);TransmitterSlots.set((TileEntityWirelessRedstone)world.getTileEntity(blast),module);world.newExplosion(null,blast.getX()+.5,blast.getY()+.5,blast.getZ()+.5,4,false,true);require(world.isAirBlock(blast),"native explosion destroys host");check(world,blast,module,old,"explosion");
            System.out.println("[vandorlabs][reprolab] redstone-module-ownership PASS read-only-probe canceled-harvest native-survival-creative-harvest explosion exact-one-module configured-NBT no-host-item-duplication");
        }finally{actor.setHeldItem(EnumHand.MAIN_HAND,held);actor.setGameType(mode);actor.setPosition(before.x,before.y,before.z);for(BlockPos p:new BlockPos[]{mine,creative,blast}){world.setBlockToAir(p);for(EntityItem entity:world.getEntitiesWithinAABB(EntityItem.class,area(p)))if(!old.contains(entity.getUniqueID()))entity.setDead();}}
    }
    private static void check(WorldServer world,BlockPos pos,ItemStack module,Set<UUID> old,String phase){int count=0;for(EntityItem entity:world.getEntitiesWithinAABB(EntityItem.class,area(pos)))if(!old.contains(entity.getUniqueID())){ItemStack stack=entity.getItem();count+=modules(stack);if(ItemRedstoneTransmitter.valid(stack))require(ItemStack.areItemStackTagsEqual(stack,module)&&stack.getMetadata()==module.getMetadata(),phase+" retains exact item configuration");}require(count==1,phase+" usable module owners="+count);}
}
