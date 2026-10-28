package com.vandorlabs.client;

import com.vandorlabs.gear.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Keep the authenticated dialog open when its clicked section becomes an endpoint payload. */
public final class GearActivationLiveChecks {
    private static final BlockPos ROOT=new BlockPos(8,130,8);
    private static int stage,ticks;
    private static GuiModularGear screen;
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("arm-activation stage="+stage+" tick="+ticks+": "+message);}
    private static EntityPlayerMP actor(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayers().get(0);}
    public static boolean tick(Minecraft mc)throws Exception{
        if(stage==4)return true;
        require(++ticks<400,"bounded activation and journal completion");
        if(stage==0){mc.getIntegratedServer().callFromMainThread(()->{
            EntityPlayerMP p=actor(mc);WorldServer w=p.getServerWorld();
            for(BlockPos at:BlockPos.getAllInBox(ROOT.add(-4,-2,-4),ROOT.add(4,5,4)))w.setBlockToAir(at);
            w.setBlockState(ROOT.down(),Blocks.STONE.getDefaultState(),3);
            p.setGameType(net.minecraft.world.GameType.CREATIVE);p.capabilities.isFlying=true;p.sendPlayerAbilities();p.inventory.currentItem=0;
            p.connection.setPlayerLocation(ROOT.getX()-2.5,ROOT.getY()+1,ROOT.getZ()-2.5,0,0);
            p.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(GearRegistry.GEAR_EXTENDER,2));
            for(BlockPos support:new BlockPos[]{ROOT.down(),ROOT})require(p.getHeldItemMainhand().onItemUse(p,w,support,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F)==EnumActionResult.SUCCESS,"normal supported placement");
            w.setBlockState(ROOT.up(2),Blocks.STONE.getDefaultState(),3);w.setBlockState(ROOT.east(),Blocks.REDSTONE_BLOCK.getDefaultState(),3);
            GearTileEntity arm=(GearTileEntity)w.getTileEntity(ROOT);arm.operator(p);require(arm.rebuild(),"column forms");
            p.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(com.vandorlabs.items.ModItems.CONFIGURIZER));p.inventoryContainer.detectAndSendChanges();return null;
        }).get();stage=1;ticks=0;return false;}
        if(stage==1){if(ticks<20||!(mc.world.getTileEntity(ROOT.up()) instanceof GearTileEntity)||mc.player.getHeldItemMainhand().getItem()!=com.vandorlabs.items.ModItems.CONFIGURIZER||mc.playerController.getCurrentGameType()!=net.minecraft.world.GameType.CREATIVE)return false;
            mc.player.connection.sendPacket(new net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock(ROOT.up(),EnumFacing.NORTH,EnumHand.MAIN_HAND,.5F,.5F,.5F));stage=2;ticks=0;return false;
        }
        if(stage==2){if(!(mc.currentScreen instanceof GuiModularGear))return false;screen=(GuiModularGear)mc.currentScreen;
            java.util.List<net.minecraft.client.gui.GuiButton> buttons=net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(net.minecraft.client.gui.GuiScreen.class,screen,"buttonList","field_146292_n");
            net.minecraft.client.gui.GuiButton tab=buttons.stream().filter(b->b.id==21).findFirst().get();screen.mouseClicked(tab.x+4,tab.y+4,0);
            net.minecraft.client.gui.GuiButton activation=buttons.stream().filter(b->b.id==0).findFirst().get();screen.mouseClicked(activation.x+4,activation.y+4,0);stage=3;ticks=0;return false;
        }
        require(mc.currentScreen==screen,"activation closed the original dialog");
        boolean done=mc.getIntegratedServer().callFromMainThread(()->{GearTileEntity arm=(GearTileEntity)actor(mc).world.getTileEntity(ROOT);require(arm!=null,"controller remains");return arm.triggerMode==1&&arm.progress()==0&&!ArmTransactions.locked(actor(mc).world,ROOT)&&actor(mc).openContainer instanceof com.vandorlabs.container.ContainerModularGear;}).get();
        if(!done||ticks<30)return false;
        mc.player.closeScreen();stage=4;System.out.println("[vandorlabs][reprolab] arm-activation PASS real-configurizer section-dialog mouse-activation powered-retraction same-window asynchronous-journal");return true;
    }
    private GearActivationLiveChecks(){}
}
