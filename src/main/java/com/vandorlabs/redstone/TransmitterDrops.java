package com.vandorlabs.redstone;

import com.vandorlabs.VandorLabs;
import com.vandorlabs.items.ItemRedstoneTransmitter;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.*;
import java.util.*;

/** Mining must return installed modules as well as the host block, including logical-control slots. */
@Mod.EventBusSubscriber(modid=VandorLabs.MODID)
public final class TransmitterDrops {
    private static final Map<World,Map<BlockPos,List<ItemStack>>> PENDING=new WeakHashMap<>();
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void breaking(BlockEvent.BreakEvent event){if(event.getWorld().isRemote||event.isCanceled()||event.getPlayer().isCreative())return;TileEntity tile=event.getWorld().getTileEntity(event.getPos());if(tile==null)return;NBTTagCompound slots=TransmitterSlots.savedSlots(tile);List<ItemStack> stacks=new ArrayList<>();for(String key:slots.getKeySet()){ItemStack stack=new ItemStack(slots.getCompoundTag(key));if(ItemRedstoneTransmitter.valid(stack))stacks.add(stack);}if(stacks.isEmpty())return;Map<BlockPos,List<ItemStack>> pending=PENDING.computeIfAbsent(event.getWorld(),w->new LinkedHashMap<>());if(pending.size()>=256)pending.clear();pending.put(event.getPos().toImmutable(),stacks);}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void harvested(BlockEvent.HarvestDropsEvent event){if(event.getWorld().isRemote)return;Map<BlockPos,List<ItemStack>> pending=PENDING.get(event.getWorld());if(pending==null)return;List<ItemStack> stacks=pending.remove(event.getPos());if(stacks!=null){
        // Configured cockpit and programmable drops may already carry these exact modules.
        // Keep one owner: return only modules not retained inside a dropped host's NBT.
        for(ItemStack drop:event.getDrops()){NBTTagCompound host=drop.getSubCompound("BlockEntityTag");if(host==null)continue;NBTTagCompound slots=host.getCompoundTag("ForgeData").getCompoundTag("VandorTransmitters");for(String key:slots.getKeySet()){ItemStack retained=new ItemStack(slots.getCompoundTag(key));for(java.util.Iterator<ItemStack> iterator=stacks.iterator();iterator.hasNext();)if(ItemStack.areItemStacksEqual(retained,iterator.next())){iterator.remove();break;}}}
        event.getDrops().addAll(stacks);
    }if(pending.isEmpty())PENDING.remove(event.getWorld());}
    @SubscribeEvent public static void unloaded(WorldEvent.Unload event){PENDING.remove(event.getWorld());}
}
