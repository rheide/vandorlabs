package com.vandorlabs.vehicle;

import com.vandorlabs.coupler.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Actual installed Source sample callbacks; independent of the captured-control implementation. */
public final class CouplerInputSourceChecks {
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileCoupler.class,new ResourceLocation("vandorlabs:square_coupler"));BlockCoupler block=new BlockCoupler();net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.register(block);
        Class<?> memory=Class.forName("com.vandorlabs.client.NonRenderingChecks$MemoryWorld");Constructor<?> ctor=memory.getDeclaredConstructor(boolean.class);ctor.setAccessible(true);World world=(World)ctor.newInstance(false);Method clear=memory.getDeclaredMethod("clear");clear.setAccessible(true);BlockPos pos=new BlockPos(8,32,8);StringBuilder rows=new StringBuilder();int profiles=0,steps=0;
        for(boolean invert:new boolean[]{false,true})for(int manual=0;manual<3;manual++)for(int initial=0;initial<2;initial++)for(int channel=0;channel<16;channel++)for(int local=0;local<16;local++){
            clear.invoke(world);world.setBlockState(pos,block.getDefaultState(),2);TileCoupler tile=(TileCoupler)world.getTileEntity(pos);NBTTagCompound saved=new NBTTagCompound();saved.setString("id","vandorlabs:square_coupler");saved.setInteger("x",pos.getX());saved.setInteger("y",pos.getY());saved.setInteger("z",pos.getZ());saved.setBoolean("CouplerInverted",invert);saved.setBoolean("CouplerReceived",initial!=0);if(manual!=0){saved.setBoolean("CouplerManualOverride",true);saved.setBoolean("CouplerManualRequested",manual==2);}tile.readFromNBT(saved);if(!tile.getPos().equals(pos)||world.getTileEntity(pos)!=tile)throw new AssertionError("Source sample target is not installed at its saved coordinate");
            for(int step=0;step<4;step++){world.setBlockState(pos.east(),(local&(1<<step))!=0?Blocks.REDSTONE_BLOCK.getDefaultState():Blocks.AIR.getDefaultState(),2);tile.setChannelSignal((channel&(1<<step))!=0);NBTTagCompound actual=tile.writeToNBT(new NBTTagCompound());if(step==0&&manual==0&&!invert&&initial==0&&channel==1&&local==0&&!tile.requested())throw new AssertionError("Actual installed channel callback did not run");if(step==0&&manual==0&&!invert&&initial==0&&channel==0&&local==1&&!tile.requested())throw new AssertionError("Actual installed neighbor power callback did not run");rows.append(invert?1:0).append(',').append(manual).append(',').append(initial).append(',').append(channel).append(',').append(local).append(',').append(step).append(',').append(tile.requested()?1:0).append(',').append(actual.getBoolean("CouplerReceived")?1:0).append(',').append(actual.getBoolean("CouplerManualOverride")?1:0).append(',').append(actual.getBoolean("CouplerManualRequested")?1:0).append(',').append(world.getBlockState(pos).getValue(BlockCoupler.LOCKED)?1:0).append('\n');steps++;}
            tile.invalidate();profiles++;
        }
        byte[] bytes=rows.toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII);Path output=Paths.get("build/test-artifacts/coupler-input-source.csv");Files.createDirectories(output.getParent());Files.write(output,bytes);StringBuilder hash=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(bytes))hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        if(!hash.toString().equals("4598c908436266a8959ef39e2180ae0b9019ae35fe6e22b16837f8881b12f724"))throw new AssertionError("Actual Source Coupler input drift "+hash);
        System.out.println("Actual Source Coupler input PASS profiles="+profiles+" steps="+steps+" bytes="+bytes.length+" sha256="+hash+" channel/local/manual/edge-history/legacy-request-absence/physical-lock");
    }
}
