package com.vandorlabs.client;

import com.vandorlabs.GuiHandler;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.blocks.*;
import com.vandorlabs.redstone.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;
import java.io.File;
import java.lang.reflect.*;
import javax.imageio.ImageIO;

/** Opt-in real dialog -> network -> server -> reopened dialog regression. */
final class ChannelListGuiChecks {
    private static final int[] IDS={2,5,0,0,9,1,3,8};
    private static final String[] METHODS={"submit","send","send","sendUpdate","send","submit","sendUpdate","submit"};
    private static final ChannelList EXPECTED=ChannelList.of(14861,14862,14863);
    private static final BlockPos POS=new BlockPos(8,80,8);
    private static int index,stage,ticks;
    private static com.google.common.util.concurrent.ListenableFuture<?> pending;
    static void tick(Minecraft mc,File output) {
        if(stage==9)return;
        try {
            if(++ticks>400)throw new IllegalStateException("channel GUI timeout index="+index+" stage="+stage);
            if(pending!=null){if(!pending.isDone())return;pending.get();pending=null;}
            if(stage==0){
                pending=mc.getIntegratedServer().addScheduledTask(()->{
                    EntityPlayerMP owner=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());
                    owner.closeScreen();owner.capabilities.isCreativeMode=true;owner.capabilities.isFlying=true;owner.sendPlayerAbilities();
                    owner.setPositionAndUpdate(POS.getX()+.5,POS.getY(),POS.getZ()-2);
                    for(BlockPos p:BlockPos.getAllInBox(POS.add(-2,-2,-2),POS.add(2,3,2)))owner.world.setBlockToAir(p);
                    owner.world.setBlockState(POS.down(),Blocks.STONE.getDefaultState(),3);
                    Block block=index==0?Block.REGISTRY.getObject(new ResourceLocation("vandorlabs:rocker_switch")):
                        index==1?ModBlocks.PROGRAMMABLE_LIGHT:index==2?ModBlocks.PROGRAMMABLE_TRIGGER_BLOCK:
                        index==3?ModBlocks.ANIMATED_SCREEN_SELECTOR:index==4?ModBlocks.PROGRAMMABLE_TRAPDOOR:
                        index==5?ModBlocks.PROGRAMMABLE_RAMP:Block.REGISTRY.getObject(new ResourceLocation("vandorlabs:"+(index==6?"programmable_door":"landing_gear")));
                    owner.world.setBlockState(POS,block.getDefaultState(),3);
                    if(block instanceof BlockVandorDoor)owner.world.setBlockState(POS.up(),block.getDefaultState().withProperty(BlockVandorDoor.HALF,BlockDoor.EnumDoorHalf.UPPER),3);
                    if(!(owner.world.getTileEntity(POS) instanceof RedstoneChannelMember))throw new IllegalStateException("missing GUI fixture "+index);
                });stage=1;ticks=0;return;
            }
            if(stage==1 && ticks>20){open(mc);stage=2;ticks=0;return;}
            if(stage==2 && ticks>15 && mc.currentScreen!=null){
                GuiTextField field=field(mc);
                field.setText("14861,");require(ChannelFields.parse(field)==null,"incomplete list accepted");
                field.setText("14863, 14861, 14862, 14861");require(EXPECTED.equals(ChannelFields.parse(field)),"GUI rejects comma list");
                Method send=mc.currentScreen.getClass().getDeclaredMethod(METHODS[index]);send.setAccessible(true);send.invoke(mc.currentScreen);
                stage=3;ticks=0;return;
            }
            if(stage==3 && ticks>20){
                pending=mc.getIntegratedServer().addScheduledTask(()->{
                    RedstoneChannelMember member=(RedstoneChannelMember)mc.getIntegratedServer().getEntityWorld().getTileEntity(POS);
                    require(member!=null && EXPECTED.equals(member.getRedstoneChannels()),"server lost submitted list index="+index);
                });mc.player.closeScreen();stage=4;ticks=0;return;
            }
            if(stage==4 && ticks>10){open(mc);stage=5;ticks=0;return;}
            if(stage==5 && ticks>20 && mc.currentScreen!=null){
                require(EXPECTED.toString().equals(field(mc).getText()),"reopened GUI lost list index="+index);
                ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"shot_channels_"+index+".png"));
                System.out.println("[vandorlabs][reprolab] channel-gui PASS "+mc.currentScreen.getClass().getSimpleName());
                mc.player.closeScreen();index++;stage=0;ticks=0;
                if(index==IDS.length){stage=9;System.out.println("[vandorlabs][reprolab] channel-gui-runtime PASS");mc.shutdown();}
            }
        }catch(Exception e){throw new IllegalStateException("channel dialog regression index="+index+" stage="+stage,e);}
    }
    private static GuiTextField field(Minecraft mc)throws ReflectiveOperationException {
        Field field=mc.currentScreen.getClass().getDeclaredField(index==7?"channel":"channelField");field.setAccessible(true);return (GuiTextField)field.get(mc.currentScreen);
    }
    private static void open(Minecraft mc){pending=mc.getIntegratedServer().addScheduledTask(()->{
        EntityPlayerMP owner=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());
        owner.openGui(VandorLabs.instance,IDS[index],owner.world,POS.getX(),POS.getY(),POS.getZ());
    });}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private ChannelListGuiChecks(){}
}
