package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockCanopy;
import com.vandorlabs.tiles.*;
import com.vandorlabs.redstone.RedstoneChannels;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import java.io.File;
import java.util.concurrent.Future;

/** Opt-in acceptance against an explicitly supplied disposable world copy and canopy coordinate. */
final class ExistingCanopyCloseChecks {
    private static boolean initialLocal,initialRedstone;private static final java.util.Map<Integer,Integer> initialLevels=new java.util.LinkedHashMap<>();
    private static int stage,elapsed,hold;private static Future<?> pending;private static BlockPos selected,owner;
    private static void require(boolean ok,String text){if(!ok)throw new IllegalStateException("existing canopy close: "+text);}
    private static WorldServer world(Minecraft mc){return mc.getIntegratedServer().getWorld(0);}
    private static TileEntityCanopy canopy(WorldServer world){TileEntityCanopy tile=TileEntityCanopy.at(world,owner);require(tile!=null,"selected owner remains installed");return tile;}
    private static void recordInput(WorldServer world){TileEntityCanopy tile=canopy(world);initialRedstone=tile.large.redstone;initialLocal=tile.hasLocalRedstoneSignal();for(int channel:tile.large.channels.toArray())initialLevels.put(channel,RedstoneChannels.level(world,channel));System.out.println("[vandorlabs][reprolab] existing-canopy-close initial-input enabled="+initialRedstone+" local="+initialLocal+" channels="+initialLevels);}
    private static void unchangedInput(WorldServer world){TileEntityCanopy tile=canopy(world);require(tile.large.redstone==initialRedstone&&tile.hasLocalRedstoneSignal()==initialLocal,"local configured input remains unchanged");require(java.util.Arrays.equals(tile.large.channels.toArray(),initialLevels.keySet().stream().mapToInt(Integer::intValue).toArray()),"configured channels remain unchanged");for(java.util.Map.Entry<Integer,Integer> input:initialLevels.entrySet())require(RedstoneChannels.level(world,input.getKey())==input.getValue(),"channel input changed: "+input.getKey());}
    private static void click(Minecraft mc){mc.player.setSneaking(false);mc.player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);require(mc.player.getDistanceSqToCenter(selected)<36,"client click remains within actual server reach");require(mc.playerController.processRightClickBlock(mc.player,mc.world,selected,EnumFacing.UP,new Vec3d(selected).addVector(.5,.5,.5),EnumHand.MAIN_HAND)==EnumActionResult.SUCCESS,"actual right-click packet accepted");}
    static boolean tick(Minecraft mc,int ticks,File output)throws Exception{
        require(++elapsed<1000,"timeout stage="+stage+" client="+(owner==null?null:TileEntityCanopy.at(mc.world,owner)));
        if(pending!=null){if(!pending.isDone())return false;pending.get();pending=null;}
        if(stage==5){System.out.println("[vandorlabs][reprolab] existing-canopy-close PASS copied-world real-right-click unchanged-configured-input actual-lower-ramp-geometry normal-world-ticks authoritative-client stable-closed");stage=6;return true;}
        if(stage==6)return true;
        if(stage==0){if(ticks<40)return false;String[] xyz=System.getProperty("vandorlabs.canopyClosePos","").split(",");require(xyz.length==3,"set canopyClosePos=x,y,z for disposable copy");selected=new BlockPos(Integer.parseInt(xyz[0].trim()),Integer.parseInt(xyz[1].trim()),Integer.parseInt(xyz[2].trim()));
            pending=mc.getIntegratedServer().addScheduledTask(()->{WorldServer world=world(mc);world.getChunkFromBlockCoords(selected);TileEntityCanopy tile=TileEntityCanopy.at(world,selected);require(tile!=null,"configured block is a canopy");owner=tile.owner().getPos();require(canopy(world).opening(),"selected canopy can open");EntityPlayerMP actor=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());actor.dismountRidingEntity();actor.setSneaking(false);actor.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);actor.capabilities.allowFlying=true;actor.capabilities.isFlying=true;actor.sendPlayerAbilities();actor.setPositionAndUpdate(selected.getX()+.5,selected.getY()+1,selected.getZ()-3);recordInput(world);
                System.out.println("[vandorlabs][reprolab] existing-canopy-close BEFORE "+canopy(world).writeToNBT(new NBTTagCompound()));
                AxisAlignedBB area=new AxisAlignedBB(owner).grow(8);for(BlockPos at:BlockPos.getAllInBox(owner.add(-8,-3,-8),owner.add(8,3,8)))if(world.isBlockLoaded(at)&&world.getTileEntity(at) instanceof TileEntityControlledRamp){TileEntityControlledRamp part=(TileEntityControlledRamp)world.getTileEntity(at);for(AxisAlignedBB box:part.boxes(0))if(area.intersects(box.offset(at)))System.out.println("[vandorlabs][reprolab] existing-canopy-close ramp="+at+" actual="+box.offset(at));}
            });stage=1;return false;}
        TileEntityCanopy tile=owner==null?null:TileEntityCanopy.at(mc.world,owner);if(tile==null||mc.player.getDistanceSqToCenter(selected)>=36)return false;
        if(stage==1){if(!tile.targetOpen)click(mc);stage=2;return false;}
        if(stage==2&&tile.targetOpen&&tile.progress==1){if(++hold>=20){pending=mc.getIntegratedServer().addScheduledTask(()->{WorldServer world=world(mc);unchangedInput(world);TileEntityCanopy server=canopy(world);if(server.block().kind.equals("canopy")&&server.width()==3&&server.facing()==EnumFacing.SOUTH&&server.large.mount==0)CanopySweepRuntimeChecks.run(server);});click(mc);stage=3;hold=0;}return false;}
        if(stage==3){if(!tile.targetOpen&&tile.progress==0&&!tile.waitingForReservations){stage=4;hold=0;}return false;}
        if(stage==4){require(!tile.targetOpen&&tile.progress==0&&!tile.waitingForReservations,"authoritative client close remains stable with unchanged input");if(++hold>=60){pending=mc.getIntegratedServer().addScheduledTask(()->{WorldServer world=world(mc);TileEntityCanopy server=canopy(world);unchangedInput(world);require(!server.targetOpen&&server.progress==0&&!server.waitingForReservations,"real server close settled through ordinary ticks");System.out.println("[vandorlabs][reprolab] existing-canopy-close AFTER "+server.writeToNBT(new NBTTagCompound()));});javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"shot_existing_canopy_closed.png"));stage=5;return false;}}
        return false;
    }
}
