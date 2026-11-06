package com.vandorlabs.client;
import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.tiles.*;
import com.vandorlabs.redstone.*;
import net.minecraft.nbt.*;
/** Pulse hide/restore must retain a bounded deadline, not a permanently indexed source. */
public final class ScreenPulseReenableChecks {
 public static void main(String[] args){net.minecraft.init.Bootstrap.register();NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);net.minecraft.util.math.BlockPos pos=new net.minecraft.util.math.BlockPos(0,80,0);world.setBlockState(pos,ModBlocks.ANIMATED_SCREEN_SELECTOR.getDefaultState(),2);RedstoneScreenContents controls=((TileEntityAnimatedScreenSelector)world.getTileEntity(pos)).redstoneScreen(0);NBTTagList rows=new NBTTagList();NBTTagCompound row=new NBTTagCompound();row.setString("Label","Pulse");row.setBoolean("Pulse",true);row.setIntArray("RedstoneChannels",new int[]{73151});rows.appendTag(row);if(!controls.applyRowConfiguration("Pulse",rows))throw new AssertionError("source configuration");world.time=100;controls.toggleRow(0);need(controls.rows().get(0).pulseUntil()==120,"source absolute deadline");need(RedstoneChannels.level(world,73151)==15,"source pulse begins");controls.setEnabled(false);tick(world);world.time=105;controls.setEnabled(true);need(controls.rows().get(0).pulseUntil()==120,"restoration does not restart deadline");world.tileLoadGuard=true;ScreenPulseActivity.activate(controls);world.tileLoadGuard=false;need(RedstoneChannels.level(world,73151)==15,"source pulse resumes remaining deadline");world.time=120;tick(world);need(RedstoneChannels.level(world,73151)==0,"re-enabled pulse expired after original 20-tick deadline actual="+RedstoneChannels.level(world,73151));controls.invalidate();System.out.println("Standalone Screen pulse re-enable PASS");}
 private static void tick(NonRenderingChecks.MemoryWorld world){ScreenPulseActivity.tick(new net.minecraftforge.fml.common.gameevent.TickEvent.WorldTickEvent(net.minecraftforge.fml.relauncher.Side.SERVER,net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END,world));}
 private static void need(boolean value,String why){if(!value)throw new AssertionError(why);}
}
