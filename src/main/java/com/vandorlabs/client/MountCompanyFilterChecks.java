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
            gui.actionPerformed(new net.minecraft.client.gui.GuiButton(101,0,0,"Security"));int left=gui.getGuiLeft()+12,top=gui.getGuiTop()+82;
            for(int company=1;company<7;company++){
                ItemStack sample=new ItemStack(chip?ModItems.DATA_CHIP:ModItems.ACCESS_CARD,5,company);sample.setStackDisplayName("Filter inventory preservation");mc.player.inventory.setInventorySlotContents(0,sample);
                gui.setWorldAndResolution(mc,size[0],size[1]);gui.mouseClicked(left+30,top+8,0);for(int step=0;step<company;step++)gui.mouseClicked(left+140,top+8,0);
                if(mc.currentScreen!=gui||gui.acceptedCompany()!=1<<company||!ItemStack.areItemStacksEqual(sample,mc.player.inventory.getStackInSlot(0)))throw new IllegalStateException("Inline company selector changed window, inventory or company");
                String expected=new String[]{"","Pinnacle","Ashiro","Paladyne","Vektor","Seyr","Vandor"}[company];if(!expected.equals(gui.companyLabel()))throw new IllegalStateException("Mount company label");
                gui.mouseClicked(left+140,top+8,1);int previousCompany=company-1;if(gui.acceptedCompany()!=(previousCompany==0?127:1<<previousCompany))throw new IllegalStateException("Right-click selects previous company");
                gui.mouseClicked(left+30,top+8,company%2);if(gui.acceptedCompany()!=127||!gui.companyLabel().equals(chip?"Any chip":"Any card"))throw new IllegalStateException("Label click clears to any credential");cases++;

            }
        }}finally{mc.player.inventory.setInventorySlotContents(0,held);mc.displayGuiScreen(previous);mc.player.openContainer=mc.player.inventoryContainer;}
        System.out.println("[vandorlabs][reprolab] mount-company-filter PASS cases="+cases+" both families six companies two layouts inline selector inventory-safe left/right cycling label-clear");
    }
}
