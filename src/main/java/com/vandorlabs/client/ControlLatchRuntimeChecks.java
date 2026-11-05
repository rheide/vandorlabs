package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockVandorSwitch;
import com.vandorlabs.redstone.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;

/** Narrow real interaction packets through binary, numeric partial banks and a momentary source. */
final class ControlLatchRuntimeChecks {
    private static final BlockPos ROCKER=new BlockPos(16,100,16),SLIDER=ROCKER.east(4),BUTTON=ROCKER.south(4);
    private static final int A=64301,B=64302,C=64303,D=64304,E=64305;
    private static int ticks,stage,at,checks;
    private static void require(boolean value,String message){checks++;if(!value)throw new IllegalStateException("control latch: "+message);}
    private static void server(Minecraft mc,Runnable action)throws Exception{mc.getIntegratedServer().addScheduledTask(action).get();}
    private static WorldServer world(Minecraft mc){return mc.getIntegratedServer().getWorld(0);}
    private static TileEntityRedstoneChannel tile(WorldServer world,BlockPos pos){return (TileEntityRedstoneChannel)world.getTileEntity(pos);}
    private static void click(Minecraft mc,BlockPos pos){EnumActionResult result=mc.playerController.processRightClickBlock(mc.player,mc.world,pos,EnumFacing.UP,new Vec3d(pos.getX()+.5,pos.getY()+.04,pos.getZ()+.5),EnumHand.MAIN_HAND);require(result==EnumActionResult.SUCCESS,"real interaction refused stage="+stage);at=ticks;stage++;}
    static boolean tick(Minecraft mc)throws Exception{
        if(mc.player==null||mc.world==null)return false;
        if(++ticks>600)throw new IllegalStateException("control latch timeout stage="+stage+" mode="+mc.playerController.getCurrentGameType()+" selected="+mc.player.inventory.currentItem+" held="+mc.player.getHeldItemMainhand()+" pos="+mc.player.getPosition());
        if(stage==0){
            server(mc,()->{WorldServer w=world(mc);varSetup(w);net.minecraft.entity.player.EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);player.interactionManager.setGameType(GameType.SURVIVAL);player.inventory.clear();player.inventory.currentItem=0;player.setSneaking(false);player.connection.setPlayerLocation(17,100,17,0,30);player.sendContainerToPlayer(player.inventoryContainer);});
            mc.playerController.setGameType(GameType.SURVIVAL);mc.player.inventory.currentItem=0;mc.player.inventory.clear();mc.player.setSneaking(false);stage=1;at=ticks;return false;
        }
        if(stage==1){if(ticks<at+20||mc.playerController.getCurrentGameType()!=GameType.SURVIVAL||mc.player.inventory.currentItem!=0||!mc.player.getHeldItemMainhand().isEmpty()||mc.player.getDistanceSq(17,100,17)>.2||!(mc.world.getTileEntity(ROCKER) instanceof TileEntityRedstoneChannel))return false;click(mc,ROCKER);return false;}
        if(ticks<at+6)return false;
        if(stage==2){server(mc,()->{WorldServer w=world(mc);require(tile(w,ROCKER).getOutputLevel()==15&&tile(w,SLIDER).getOutputLevel()==0&&tile(w,SLIDER).localSignalLevel(B)==15&&tile(w,SLIDER).localSignalLevel(C)==0,"rockerON creates partial numeric peer bank");});click(mc,SLIDER);}
        else if(stage==3){server(mc,()->{WorldServer w=world(mc);require(tile(w,ROCKER).getOutputLevel()==3&&tile(w,SLIDER).getOutputLevel()==3&&tile(w,ROCKER).latchedLevel(A)==15&&tile(w,ROCKER).latchedLevel(B)==3,"numeric low does not amplify overlapping rocker bank");});click(mc,SLIDER);}
        else if(stage==4){server(mc,()->{WorldServer w=world(mc);require(tile(w,ROCKER).getOutputLevel()==8&&tile(w,SLIDER).getOutputLevel()==8,"numeric middle uses minimum bank output");});click(mc,ROCKER);}
        else if(stage==5){server(mc,()->{WorldServer w=world(mc);TileEntityRedstoneChannel slider=tile(w,SLIDER);require(tile(w,ROCKER).getOutputLevel()==0&&slider.getOutputLevel()==0&&slider.localSignalLevel(B)==0&&slider.localSignalLevel(C)==8,"OFF only overlapping channels retains independent numeric channel");NBTTagCompound saved=slider.writeToNBT(new NBTTagCompound());TileEntitySignalControl restored=new TileEntitySignalControl();restored.readFromNBT(saved);require(restored.latchedLevel(B)==0&&restored.latchedLevel(C)==8&&restored.getOutputLevel()==0&&saved.equals(restored.writeToNBT(new NBTTagCompound())),"actual partial saved bank exact reload");});click(mc,ROCKER);}
        else if(stage==6){server(mc,()->{WorldServer w=world(mc);require(tile(w,ROCKER).getOutputLevel()==15&&tile(w,SLIDER).getOutputLevel()==8&&RedstoneChannels.level(w,SLIDER,C)==8,"ON reset preserves other peer channel level");});click(mc,BUTTON);}
        else if(stage==7){server(mc,()->{WorldServer w=world(mc);require(tile(w,BUTTON).isLocalOn()&&tile(w,BUTTON).getOutputLevel()==15&&RedstoneChannels.level(w,BUTTON,D)==15,"momentary real press emits15");});stage=8;}
        else if(stage==8&&ticks>at+45){server(mc,()->{WorldServer w=world(mc);require(!tile(w,BUTTON).isLocalOn()&&tile(w,BUTTON).getOutputLevel()==0&&RedstoneChannels.level(w,BUTTON,D)==0&&RedstoneChannels.level(w,BUTTON,E)==0,"ordinary scheduled momentary release withdraws bank");});System.out.println("[vandorlabs][reprolab] control-latch-levels-live PASS checks="+checks+" real survival rocker and slider packets partial numeric bank min output exact saved reload momentary release");return true;}
        return false;
    }
    private static void varSetup(WorldServer world){
        for(int x=14;x<=22;x++)for(int z=14;z<=22;z++)world.setBlockState(new BlockPos(x,99,z),Blocks.STONE.getDefaultState(),3);
        String[] ids={"rocker_switch","wall_slider","push_button"};BlockPos[] positions={ROCKER,SLIDER,BUTTON};
        for(int i=0;i<ids.length;i++){Block block=Block.getBlockFromName("vandorlabs:"+ids[i]);world.setBlockState(positions[i],block.getDefaultState().withProperty(BlockVandorSwitch.FACING,EnumFacing.UP),3);}
        tile(world,ROCKER).setRedstoneChannels(ChannelList.of(A,B));tile(world,SLIDER).setRedstoneChannels(ChannelList.of(B,C));tile(world,BUTTON).setRedstoneChannels(ChannelList.of(D,E));((TileEntitySignalControl)tile(world,SLIDER)).configureLimits(3,13);DeferredTileLoad.flush(world);
    }
}
