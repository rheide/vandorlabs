package com.vandorlabs.client;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.tiles.TileEntityTokenSlot;
import com.vandorlabs.container.ContainerTokenSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
final class MountCompanyFilterChecks {
    static void run(Minecraft mc,java.io.File output)throws Exception{
        ItemStack held=mc.player.inventory.getStackInSlot(0);GuiScreen previous=mc.currentScreen;int cases=0;
        try{for(boolean chip:new boolean[]{false,true})for(int[] size:new int[][]{{320,240},{460,340}}){
            TileEntityTokenSlot tile=(TileEntityTokenSlot)mc.world.getTileEntity(new BlockPos(270,chip?34:32,142));if(tile==null)throw new IllegalStateException("Mount filter fixture missing");
            GuiTokenSlot gui=new GuiTokenSlot(new ContainerTokenSlot(mc.player.inventory,tile));gui.inventorySlots.windowId=-99;mc.displayGuiScreen(gui);gui.setWorldAndResolution(mc,size[0],size[1]);
            int left=(size[0]-Math.min(308,size[0]-12))/2+12,top=(size[1]-160)/2+104;
            for(int company=1;company<7;company++){
                ItemStack sample=new ItemStack(chip?ModItems.DATA_CHIP:ModItems.ACCESS_CARD,5,company);sample.setStackDisplayName("Filter inventory preservation");mc.player.inventory.setInventorySlotContents(0,sample);
                gui.setWorldAndResolution(mc,size[0],size[1]);gui.mouseClicked(left+140,top+8,0);
                if(!(mc.currentScreen instanceof GuiCustomTexture))throw new IllegalStateException("Mount must use door's sample picker");
                GuiCustomTexture picker=(GuiCustomTexture)mc.currentScreen;picker.setWorldAndResolution(mc,size[0],size[1]);int px=(size[0]-252)/2,py=(size[1]-198)/2;
                picker.mouseClicked(px+47,py+143,0);picker.mouseReleased(px+120,py+40,0);
                if(mc.currentScreen!=gui||gui.acceptedCompany()!=1<<company||!ItemStack.areItemStacksEqual(sample,mc.player.inventory.getStackInSlot(0)))throw new IllegalStateException("Mount sample must select one company without moving inventory");
                String expected=new String[]{"","Pinnacle","Ashiro","Paladyne","Vektor","Seyr","Vandor"}[company];if(!expected.equals(gui.companyLabel()))throw new IllegalStateException("Mount company label");
                gui.setWorldAndResolution(mc,size[0],size[1]);gui.mouseClicked(left+140,top+8,1);if(gui.acceptedCompany()!=127)throw new IllegalStateException("Right-click sample clears mount filter");
                gui.mouseClicked(left+140,top+8,0);picker=(GuiCustomTexture)mc.currentScreen;picker.setWorldAndResolution(mc,size[0],size[1]);picker.mouseClicked(px+47,py+143,0);picker.mouseReleased(px+120,py+40,0);
                gui.setWorldAndResolution(mc,size[0],size[1]);gui.mouseClicked(left+30,top+8,company%2);if(gui.acceptedCompany()!=127||!gui.companyLabel().equals(chip?"Any chip":"Any card"))throw new IllegalStateException("Label click clears to any credential");
                // The other credential family must never be accepted by the sample picker.
                mc.player.inventory.setInventorySlotContents(0,new ItemStack(chip?ModItems.ACCESS_CARD:ModItems.DATA_CHIP,5,company));gui.mouseClicked(left+140,top+8,0);picker=(GuiCustomTexture)mc.currentScreen;picker.setWorldAndResolution(mc,size[0],size[1]);picker.mouseClicked(px+47,py+143,0);picker.mouseReleased(px+120,py+40,0);
                if(mc.currentScreen!=picker)throw new IllegalStateException("Wrong credential family accepted");picker.keyTyped('\0',org.lwjgl.input.Keyboard.KEY_ESCAPE);cases++;
            }
        }}finally{mc.player.inventory.setInventorySlotContents(0,held);mc.displayGuiScreen(previous);mc.player.openContainer=mc.player.inventoryContainer;}
        System.out.println("[vandorlabs][reprolab] mount-company-filter PASS cases="+cases+" both families six companies two layouts inventory-safe sample picker left/right clear rejects wrong family");
    }
}
