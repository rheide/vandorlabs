package com.vandorlabs.redstone;

import com.vandorlabs.VandorLabs;
import com.vandorlabs.items.ItemRedstoneTransmitter;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.*;
import java.util.*;

/** Detach module inventory before configured host drops can copy it, including custom harvest overrides. */
@Mod.EventBusSubscriber(modid=VandorLabs.MODID)
public final class TransmitterDrops {
    private static final ThreadLocal<Integer> PROBES=new ThreadLocal<>();
    private TransmitterDrops(){}
    /** Protection events are also used by read-only preflight; they must never eject inventory. */
    public static boolean protectionProbe(BlockEvent.BreakEvent event){Integer previous=PROBES.get();PROBES.set(previous==null?1:previous+1);try{return net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event);}finally{if(previous==null)PROBES.remove();else PROBES.set(previous);}}
    private static boolean locked(World world,BlockPos pos,TileEntity tile){return com.vandorlabs.gear.ArmTransactions.locked(world,pos)||com.vandorlabs.copier.CopyTransactions.locked(world,pos)||com.vandorlabs.vehicle.VehicleCouplingTransfer.locked(world,pos)||tile instanceof com.vandorlabs.surface.SurfaceController&&((com.vandorlabs.surface.SurfaceController)tile).surfaceLocked();}
    /** Block drops carry settings; inserted hardware is separate inventory in the durable harvest journal. */
    public static void retainedDrops(TileEntity tile,List<ItemStack> drops){
        for(ItemStack drop:drops){NBTTagCompound host=drop.getSubCompound("BlockEntityTag");if(host==null||!host.getCompoundTag("ForgeData").hasKey("VandorTransmitters",10))continue;drop.setTagCompound(drop.getTagCompound().copy());host=drop.getSubCompound("BlockEntityTag");NBTTagCompound forge=host.getCompoundTag("ForgeData");forge.removeTag("VandorTransmitters");if(forge.hasNoTags())host.removeTag("ForgeData");}
        if(tile==null)return;NBTTagCompound saved=TransmitterSlots.savedSlots(tile);for(String key:saved.getKeySet()){ItemStack module=new ItemStack(saved.getCompoundTag(key));if(ItemRedstoneTransmitter.valid(module))drops.add(module);}
    }

    private static boolean eject(World world,BlockPos pos){
        if(world.isRemote||!world.isBlockLoaded(pos))return true;TileEntity tile=world.getTileEntity(pos);
        // Breaking an authored part returns the hardware owned by its canonical assembly root.
        com.vandorlabs.tiles.TileEntityCockpit cockpit=com.vandorlabs.cockpit.CockpitAssembly.root(world,pos);if(cockpit!=null)tile=cockpit;
        net.minecraft.block.state.IBlockState state=world.getBlockState(pos);
        if(state.getBlock() instanceof com.vandorlabs.blocks.BlockLargeProgrammableDoor){com.vandorlabs.tiles.TileEntityLargeProgrammableDoor root=((com.vandorlabs.blocks.BlockLargeProgrammableDoor)state.getBlock()).root(world,pos);if(root!=null)tile=root;}
        else if(state.getBlock() instanceof com.vandorlabs.blocks.BlockVandorDoor&&state.getValue(com.vandorlabs.blocks.BlockVandorDoor.HALF)==net.minecraft.block.BlockDoor.EnumDoorHalf.UPPER&&world.isBlockLoaded(pos.down()))tile=world.getTileEntity(pos.down());
        if(tile instanceof com.vandorlabs.tiles.TileEntityCanopy)tile=((com.vandorlabs.tiles.TileEntityCanopy)tile).owner();
        if(tile instanceof com.vandorlabs.tiles.TileEntityLandingGear){BlockPos owner=((com.vandorlabs.tiles.TileEntityLandingGear)tile).owner();if(owner!=null&&world.isBlockLoaded(owner))tile=world.getTileEntity(owner);}
        if(tile==null||!world.isBlockLoaded(tile.getPos()))return true;pos=tile.getPos();
        NBTTagCompound saved=TransmitterSlots.savedSlots(tile);List<ItemStack> items=new ArrayList<>();for(String key:saved.getKeySet()){ItemStack stack=new ItemStack(saved.getCompoundTag(key));if(ItemRedstoneTransmitter.valid(stack))items.add(stack);}
        if(items.isEmpty())return true;if(!(tile instanceof RedstoneChannelMember)||locked(world,pos,tile))return false;
        List<RedstoneChannelMember> members=new ArrayList<>();if(tile instanceof RedstoneChannelMember)members.add((RedstoneChannelMember)tile);
        if(tile instanceof com.vandorlabs.tiles.TileEntityCockpit)members.addAll(((com.vandorlabs.tiles.TileEntityCockpit)tile).controls);
        if(tile instanceof com.vandorlabs.tiles.TileEntityAnimatedScreenSelector){com.vandorlabs.tiles.TileEntityAnimatedScreenSelector selector=(com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)tile;for(int side=0;side<2;side++)if(selector.hasRedstoneScreen(side))members.addAll(selector.redstoneScreen(side).rows());}
        RedstoneChannels.beginTransfer(world);
        try{tile.getTileData().removeTag("VandorTransmitters");tile.markDirty();for(RedstoneChannelMember member:members){RedstoneChannels.channelChanged(member,member.getRedstoneChannels());RedstoneChannels.inputChanged(member);}}
        finally{RedstoneChannels.endTransfer(world);}
        // Source data no longer owns these items when native item-entity publication starts.
        // Captures/parking bypass block harvest and retain their original module NBT instead.
        for(ItemStack item:items)net.minecraft.block.Block.spawnAsEntity(world,pos,item);return true;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void breaking(BlockEvent.BreakEvent event){if(!event.isCanceled()&&PROBES.get()==null&&!eject(event.getWorld(),event.getPos()))event.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void exploding(ExplosionEvent.Detonate event){if(!event.getWorld().isRemote)for(Iterator<BlockPos> blocks=event.getAffectedBlocks().iterator();blocks.hasNext();)if(!eject(event.getWorld(),blocks.next()))blocks.remove();}
}
