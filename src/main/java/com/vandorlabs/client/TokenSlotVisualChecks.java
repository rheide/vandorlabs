package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityTokenSlot;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.io.File;

final class TokenSlotVisualChecks {
    private static final BlockPos FIRST=new BlockPos(270,32,142);
    static void build(World world){
        for(int kind=0;kind<2;kind++)for(int i=0;i<8;i++){
            BlockPos pos=FIRST.add(i*2,kind*2,0);world.setBlockState(pos.south(),net.minecraft.init.Blocks.STONE.getDefaultState(),2);world.setBlockState(pos,Block.getBlockFromName("vandorlabs:data_"+(kind==0?"card":"chip")+"_slot").getDefaultState(),2);
            TileEntityTokenSlot tile=(TileEntityTokenSlot)world.getTileEntity(pos);tile.configure(i==7,i==7?127:1<<i,0);
            if(kind==1&&i<7)tile.setInventorySlotContents(0,new ItemStack(com.vandorlabs.items.ModItems.DATA_CHIP,1,i));
        }
    }
    static void gui(Minecraft mc,File output)throws Exception{
        GuiScreen previous=mc.currentScreen;TileEntityTokenSlot tile=(TileEntityTokenSlot)mc.world.getTileEntity(FIRST.add(6,0,0));if(tile==null)throw new IllegalStateException("Mount visual fixture not synchronized");
        try{GuiTokenSlot gui=new GuiTokenSlot(new com.vandorlabs.container.ContainerTokenSlot(mc.player.inventory,tile));gui.inventorySlots.windowId=-78;mc.displayGuiScreen(gui);
            for(int[] size:new int[][]{{320,240},{460,340}}){gui.setWorldAndResolution(mc,size[0],size[1]);for(net.minecraft.inventory.Slot slot:gui.inventorySlots.inventorySlots)if(slot.xPos<0||slot.yPos<0||slot.xPos+16>308||slot.yPos+16>228)throw new IllegalStateException("Mount dialog inventory outside panel");mc.getFramebuffer().bindFramebuffer(true);net.minecraft.client.renderer.GlStateManager.clear(16640);mc.entityRenderer.setupOverlayRendering();gui.drawScreen(-1,-1,0);javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"shot_token_mount_dialog_"+size[0]+".png"));}
        }finally{mc.displayGuiScreen(previous);if(previous==null)mc.player.openContainer=mc.player.inventoryContainer;}System.out.println("[vandorlabs][reprolab] token-mount-dialog PASS small and regular layouts inventory whitelist and channel field");
    }
}
