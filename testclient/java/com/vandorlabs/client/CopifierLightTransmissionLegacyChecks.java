package com.vandorlabs.client;
import com.vandorlabs.blocks.*;
import com.vandorlabs.items.ProgrammableSettings;
import com.vandorlabs.redstone.*;
import com.vandorlabs.tiles.TileEntityProgrammableLight;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.*;
import net.minecraft.util.math.BlockPos;
/** Actual Source configured Light output and neighbor-withdrawal callbacks, in a memory world. */
public final class CopifierLightTransmissionLegacyChecks {
 public static void main(String[] args)throws Exception{PropulsionPlacementLegacyOracle.initialize();int cases=0;for(Block block:ModBlocks.BLOCKS)if(block instanceof BlockProgrammableLight)for(boolean local:new boolean[]{false,true}){NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockPos pos=new BlockPos(4,80,4),other=pos.north(3);world.setBlockState(pos,block.getDefaultState(),2);world.setBlockState(other,block.getDefaultState(),2);TileEntityProgrammableLight source=(TileEntityProgrammableLight)world.getTileEntity(pos),receiver=(TileEntityProgrammableLight)world.getTileEntity(other);NBTTagCompound values=new NBTTagCompound(),tx=new NBTTagCompound();tx.setIntArray("Channels",new int[]{923});tx.setInteger("Range",16);tx.setBoolean("Local",local);values.setTag("Transmission",tx);if(!ProgrammableSettings.apply(world,pos,values)||!TransmitterSlots.channels(source).equals(ChannelList.of(923))||!source.transmittedChannels().isEmpty())throw new AssertionError("Source configured output list");receiver.setRedstoneChannels(ChannelList.of(923));RedstoneChannels.register(receiver);RedstoneChannels.register(source);world.setBlockState(pos.south(),Blocks.REDSTONE_BLOCK.getDefaultState(),2);source.localInputChanged();if(source.localSignalLevel(923)!=15||receiver.getSignalLevel()!=0)throw new AssertionError("Source physical output local="+local+" input="+source.localSignalLevel(923)+" received="+receiver.getSignalLevel());world.setBlockToAir(pos.south());source.localInputChanged();if(receiver.getSignalLevel()!=0)throw new AssertionError("Source withdrawal");source.invalidate();receiver.invalidate();RedstoneChannels.worldUnloaded(new net.minecraftforge.event.world.WorldEvent.Unload(world));cases++;}if(cases!=6)throw new AssertionError("Roles");System.out.println("Actual Source Light transmission PASS cases="+cases+" configured metadata only/Local false+true/no physical repeating");}

}
