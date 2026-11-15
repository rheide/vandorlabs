package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockBattery;
import com.vandorlabs.ports.*;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.tiles.*;
import java.lang.reflect.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Chunk decode must not recursively ask for the block whose tile is being decoded. */
public final class BatteryColdReadChecks {
    private static final class ProbeWorld extends WorldServer {
        boolean ready; IBlockState state; TileEntity tile; int reads,notices;
        private ProbeWorld(){super(null,null,null,0,null);}
        private void requireReady(){if(!ready)throw new AssertionError("Battery touched world during NBT decode/passive policy read");}
        @Override public IBlockState getBlockState(BlockPos pos){requireReady();reads++;return state;}
        @Override public TileEntity getTileEntity(BlockPos pos){requireReady();return tile;}
        @Override public boolean isBlockLoaded(BlockPos pos){requireReady();return true;}
        @Override public net.minecraft.server.MinecraftServer getMinecraftServer(){return null;}
        @Override public void notifyNeighborsOfStateChange(BlockPos pos,net.minecraft.block.Block block,boolean observers){notices++;}
    }
    private static int checks;
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception {
        net.minecraft.init.Bootstrap.register();
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityBattery.class,new ResourceLocation("vandorlabs:battery_block"));
        Field field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);sun.misc.Unsafe unsafe=(sun.misc.Unsafe)field.get(null);
        Method flush=DeferredTileLoad.class.getDeclaredMethod("flushPriority",net.minecraft.world.World.class);flush.setAccessible(true);
        BlockBattery block=new BlockBattery();
        for(EnumFacing facing:EnumFacing.values())for(boolean legacy:new boolean[]{true,false}){
            ProbeWorld world=(ProbeWorld)unsafe.allocateInstance(ProbeWorld.class);world.state=block.getDefaultState().withProperty(BlockBattery.FACING,facing);
            TileEntityBattery battery=new TileEntityBattery();battery.setWorld(world);battery.setPos(new BlockPos(7,64,3));world.tile=battery;
            NBTTagCompound raw=new NBTTagCompound();raw.setString("id","vandorlabs:battery_block");raw.setInteger("x",7);raw.setInteger("y",64);raw.setInteger("z",3);raw.setInteger("Energy",73921);raw.setBoolean("BlockShape",false);raw.setString("FutureBatteryField","retain exactly");NBTTagCompound caps=new NBTTagCompound();caps.setString("opaque:provider","not interpreted");raw.setTag("ForgeCaps",caps);
            ResourcePortSettings explicit=ResourcePortSettings.placed(facing.getIndex()).withTransfer(17,true,ResourcePortSettings.OnTexture.CONNECTED);
            if(!legacy)ResourcePortData.write(raw,explicit,0,ChannelList.EMPTY);
            NBTTagCompound original=raw.copy();battery.readFromNBT(raw);need(!battery.inert(),"valid saved battery accepted");battery.settings();battery.inputSide();battery.getUpdateTag();
            NBTTagCompound saved=battery.writeToNBT(new NBTTagCompound());need(raw.equals(original),"decode leaves caller's exact raw image unchanged");need(saved.getInteger("Energy")==73921&&!saved.getBoolean("BlockShape"),"charge and shape retained");need(saved.getCompoundTag("ForgeCaps").equals(caps)&&saved.getString("FutureBatteryField").equals("retain exactly"),"opaque capability and own envelope retained");need(saved.hasKey(ResourcePortData.FACES)!=legacy,"old policy omission retained; new policy remains explicit");
            battery.onLoad();world.ready=true;flush.invoke(null,world);need(battery.inputSide()==facing,"deferred identity-checked facing matches physical metadata");need(battery.settings().face(facing.getIndex())==ResourcePortSettings.Face.INPUT,"old/new input direction retained");if(!legacy)need(battery.settings().equals(explicit),"explicit saved face policy is unchanged");
            int reads=world.reads;for(int i=0;i<100;i++){battery.settings();battery.inputSide();}need(world.reads==reads,"passive server policy reuses resolved facing without repeated world reads");need(battery.writeToNBT(new NBTTagCompound()).getInteger("Energy")==73921,"deferred binding never changes charge");
        }
        System.out.println("Battery cold-read PASS checks="+checks+" legacy/new policy, all6 facings, zero world reads during decode/passive access, exact opaque charge envelope and deferred binding");
    }
}
