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
    private static final int[] IDS={2,5,0,0,9,1,3,8,2};
    private static final String[] METHODS={"submit","send","send","sendUpdate","send","submit","sendUpdate","submit","submit"};
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
                        index==5?ModBlocks.PROGRAMMABLE_RAMP:index==8?Block.REGISTRY.getObject(new ResourceLocation("vandorlabs:ion_drive")):Block.REGISTRY.getObject(new ResourceLocation("vandorlabs:"+(index==6?"programmable_door":"landing_gear")));
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
                if(index==1)for(int i=0;i<3;i++)((GuiProgrammableLight)mc.currentScreen).actionPerformed(new net.minecraft.client.gui.GuiButton(102,0,0,"Signal brightness"));
                if(index==2){((GuiProgrammableTrigger)mc.currentScreen).actionPerformed(new net.minecraft.client.gui.GuiButton(101,0,0,"Signal bands"));((GuiProgrammableTrigger)mc.currentScreen).actionPerformed(new net.minecraft.client.gui.GuiButton(102,0,0,"Exact level"));}
                if(index==8){((GuiRedstoneChannel)mc.currentScreen).actionPerformed(new net.minecraft.client.gui.GuiButton(5,0,0,"Signal brightness"));((GuiRedstoneChannel)mc.currentScreen).actionPerformed(new net.minecraft.client.gui.GuiButton(6,0,0,"Threshold"));}
                Method send=mc.currentScreen.getClass().getDeclaredMethod(METHODS[index]);send.setAccessible(true);send.invoke(mc.currentScreen);
                stage=3;ticks=0;return;
            }
            if(stage==3 && ticks>20){
                pending=mc.getIntegratedServer().addScheduledTask(()->{
                    RedstoneChannelMember member=(RedstoneChannelMember)mc.getIntegratedServer().getEntityWorld().getTileEntity(POS);
                    require(member!=null && EXPECTED.equals(member.getRedstoneChannels()),"server lost submitted list index="+index);
                    if(index==1)require(((com.vandorlabs.tiles.TileEntityProgrammableLight)member).isSignalBrightness(),"light brightness packet");
                    if(index==2)require(((com.vandorlabs.tiles.TileEntityProgrammableTrigger)member).isLevelStates() && ((com.vandorlabs.tiles.TileEntityProgrammableTrigger)member).getExactLevel()==0,"trigger levels packet");
                    if(index==8)require(((com.vandorlabs.tiles.TileEntityRedstoneLight)member).isSignalBrightness() && ((com.vandorlabs.tiles.TileEntityRedstoneLight)member).getParticleThreshold()==9,"propulsion levels packet");
                });mc.player.closeScreen();stage=4;ticks=0;return;
            }
            if(stage==4 && ticks>10){open(mc);stage=5;ticks=0;return;}
            if(stage==5 && ticks>20 && mc.currentScreen!=null){
                if(index==1 || index==8){Field signal=mc.currentScreen.getClass().getDeclaredField("signalBrightness");signal.setAccessible(true);require(signal.getBoolean(mc.currentScreen),"signal brightness lost on reopen");}
                if(index==2){Field exact=mc.currentScreen.getClass().getDeclaredField("exact");exact.setAccessible(true);require(exact.getInt(mc.currentScreen)==0,"exact signal lost on reopen");}
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
