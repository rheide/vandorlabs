package com.vandorlabs.client;

import com.vandorlabs.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.items.*;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.render.ScreenSurface;
import com.vandorlabs.tiles.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.client.CPacketEntityAction;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;

/** Vanilla Duplifier interactions and live propulsion editor packets. */
final class ScreenCopyAndParticleRuntimeChecks {
    private static final BlockPos SOURCE=new BlockPos(8,80,8),TARGET=SOURCE.east(3);
    private static int stage,ticks,level=1;
    private static com.google.common.util.concurrent.ListenableFuture<?> pending;
    static void tick(Minecraft mc,File output){
        try{
            if(++ticks>400)throw new IllegalStateException("copy/particle timeout stage="+stage);
            if(pending!=null){if(!pending.isDone())return;pending.get();pending=null;}
            if(stage==0){pending=mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP p=owner(mc);p.closeScreen();p.world.setBlockState(SOURCE,ModBlocks.PROGRAMMABLE_CONSOLE.getDefaultState(),3);p.world.setBlockState(TARGET,ModBlocks.PROGRAMMABLE_CONSOLE.getDefaultState(),3);
                List<String> labels=new ArrayList<>();List<ChannelList> channels=new ArrayList<>();for(int i=0;i<8;i++){labels.add("Row "+(i+1));channels.add(ChannelList.of(20001+i,20021+i));}
                TileEntityAnimatedScreenSelector source=screen(p,SOURCE);source.redstoneScreen(0).configure("Bridge",labels,channels,0);source.redstoneScreen(1).configure("Deck",Arrays.asList("Deck lights"),Arrays.asList(ChannelList.of(20101,20102)),0);
                ItemStack tool=new ItemStack(ModItems.DUPLIFIER);NBTTagCompound tag=new NBTTagCompound();tag.setLong(DuplifierApplyOptions.TAG,0);tool.setTagCompound(tag);p.inventory.setInventorySlotContents(0,tool);held(p,0);aimScreen(p,SOURCE);
            });next(1);return;}
            if(stage==1 && ticks>25){sneak(mc,true);click(mc,SOURCE);next(2);return;}
            if(stage==2 && ticks>15){sneak(mc,false);pending=mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP p=owner(mc);require(p.getHeldItemMainhand().getSubCompound(ItemDuplifier.SETTINGS_TAG)!=null,"real Duplifier did not capture screen");aimScreen(p,TARGET);
            });next(3);return;}
            if(stage==3 && ticks>25){click(mc,TARGET);next(4);return;}
            if(stage==4 && ticks>20){pending=mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP p=owner(mc);TileEntityAnimatedScreenSelector source=screen(p,SOURCE),target=screen(p,TARGET);
                require(target.redstoneConfiguration().equals(source.redstoneConfiguration()),"real old-mask Duplifier lost header, labels, channel lists or second surface");
                ItemStack picked=((BlockAnimatedScreenSelector)p.world.getBlockState(TARGET).getBlock()).createConfiguredDrop(target);
                NBTTagCompound saved=picked.getSubCompound("BlockEntityTag");require(saved.getCompoundTag("RedstonePrimary").getString("Title").equals("Bridge") && !saved.getCompoundTag("RedstonePrimary").getTagList("Rows",10).getCompoundTagAt(0).hasKey("LatchedChannels"),"picked screen lost header or retained live latch");
            });next(5);return;}
            if(stage==5 && Boolean.getBoolean("vandorlabs.redstoneScreenFocused")){System.out.println("[vandorlabs][reprolab] integrated-screen-duplifier-runtime PASS both surfaces, eight rows and old-mask tool");mc.shutdown();next(99);return;}
            if(stage==5){System.out.println("[vandorlabs][reprolab] integrated-screen-duplifier-runtime PASS both surfaces, eight rows and old-mask tool");pending=mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP p=owner(mc);net.minecraft.block.Block block=net.minecraft.block.Block.REGISTRY.getObject(new ResourceLocation("vandorlabs","rocket_thruster"));p.world.setBlockState(SOURCE,block.getDefaultState().withProperty(BlockPropulsionLight.FACING,EnumFacing.NORTH),3);
                TileEntityRedstoneLight tile=(TileEntityRedstoneLight)p.world.getTileEntity(SOURCE);tile.setManualMode(1,true);held(p,8);p.connection.setPlayerLocation(SOURCE.getX()+.5,SOURCE.getY()+.5-p.getEyeHeight(),SOURCE.getZ()-2.5,0,0);
            });next(6);return;}
            if(stage==6 && ticks>25){openParticles(mc);next(7);return;}
            if(stage==7 && ticks>15 && mc.currentScreen instanceof GuiRedstoneChannel){GuiRedstoneChannel gui=(GuiRedstoneChannel)mc.currentScreen;gui.actionPerformed(new GuiButton(2,0,0,"Particles"));gui.actionPerformed(new GuiButton(1,0,0,"Done"));next(8);return;}
            if(stage==8 && ticks>30){pending=mc.getIntegratedServer().addScheduledTask(()->require(((TileEntityRedstoneLight)owner(mc).world.getTileEntity(SOURCE)).getParticleLevel()==level%4,"live particle GUI lost level"));next(9);return;}
            if(stage==9){require(((TileEntityRedstoneLight)mc.world.getTileEntity(SOURCE)).getParticleLevel()==level%4,"particle level did not reach client");ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"shot_particles_"+TileEntityRedstoneLight.PARTICLE_LEVELS[level%4].toLowerCase(java.util.Locale.ROOT)+".png"));if(level++<4){openParticles(mc);next(7);}else{System.out.println("[vandorlabs][reprolab] propulsion-particle-gui-runtime PASS Off Light Medium Heavy persisted and synchronized");mc.shutdown();next(99);}return;}
        }catch(Exception e){throw new IllegalStateException("screen copy/particle live check stage="+stage,e);}
    }
    private static void next(int value){stage=value;ticks=0;}
    private static EntityPlayerMP owner(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());}
    private static TileEntityAnimatedScreenSelector screen(EntityPlayerMP p,BlockPos pos){return (TileEntityAnimatedScreenSelector)p.world.getTileEntity(pos);}
    private static void held(EntityPlayerMP p,int slot){p.inventory.currentItem=slot;p.inventoryContainer.detectAndSendChanges();p.connection.sendPacket(new net.minecraft.network.play.server.SPacketHeldItemChange(slot));}
    private static void sneak(Minecraft mc,boolean on){mc.player.setSneaking(on);mc.player.connection.sendPacket(new CPacketEntityAction(mc.player,on?CPacketEntityAction.Action.START_SNEAKING:CPacketEntityAction.Action.STOP_SNEAKING));}
    private static void aimScreen(EntityPlayerMP p,BlockPos pos){
        TileEntityAnimatedScreenSelector tile=screen(p,pos);net.minecraft.block.state.IBlockState state=p.world.getBlockState(pos);ScreenSurface.Quad q=RedstoneScreenInteractions.surface(state,tile,0);
        double u=30/128D,v=(RedstoneScreenInteractions.rowTop(state.getBlock(),0)+5)/(double)RedstoneScreenInteractions.displayHeight(state.getBlock(),0);
        Vec3d hit=new Vec3d(pos.getX()+(q.topRight.x+(q.topLeft.x-q.topRight.x)*u)/16,pos.getY()+(q.topRight.y+(q.bottomRight.y-q.topRight.y)*v)/16,pos.getZ()+(q.topRight.z+(q.bottomRight.z-q.topRight.z)*v)/16);
        Vec3d eye=hit.addVector(0,q.ny*2,q.nz*2),look=hit.subtract(eye);
        p.connection.setPlayerLocation(eye.x,eye.y-p.getEyeHeight(),eye.z,(float)Math.toDegrees(Math.atan2(-look.x,look.z)),(float)-Math.toDegrees(Math.atan2(look.y,Math.sqrt(look.x*look.x+look.z*look.z))));
    }
    private static void click(Minecraft mc,BlockPos pos){RayTraceResult hit=mc.objectMouseOver;require(hit!=null && hit.typeOfHit==RayTraceResult.Type.BLOCK && pos.equals(hit.getBlockPos()),"crosshair missed copy target");mc.playerController.processRightClickBlock(mc.player,mc.world,pos,hit.sideHit,hit.hitVec,EnumHand.MAIN_HAND);}
    private static void openParticles(Minecraft mc){pending=mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP p=owner(mc);p.openGui(VandorLabs.instance,GuiHandler.GUI_REDSTONE_CHANNEL,p.world,SOURCE.getX(),SOURCE.getY(),SOURCE.getZ());});}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
