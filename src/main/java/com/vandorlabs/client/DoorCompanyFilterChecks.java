package com.vandorlabs.client;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.tiles.TileEntitySpaceDoor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
final class DoorCompanyFilterChecks {
    static void run(Minecraft mc,java.io.File output)throws Exception{
        TileEntitySpaceDoor tile=(TileEntitySpaceDoor)mc.world.getTileEntity(new BlockPos(298,33,142));if(tile==null)throw new IllegalStateException("Company filter fixture not synchronized");
        java.lang.reflect.Field allowed=GuiSpaceDoor.class.getDeclaredField("securityAllowed");allowed.setAccessible(true);
        ItemStack held=mc.player.inventory.getStackInSlot(0);GuiScreen previous=mc.currentScreen;int cases=0;
        try{for(int[] size:new int[][]{{320,240},{460,340}}){
            GuiSpaceDoor parent=new GuiSpaceDoor(tile);parent.inventorySlots.windowId=-99;mc.displayGuiScreen(parent);parent.setWorldAndResolution(mc,size[0],size[1]);ProgrammableDialogLayout layout=new ProgrammableDialogLayout(size[0],size[1]);
            for(int company=1;company<7;company++){
                parent.setWorldAndResolution(mc,size[0],size[1]);
                ItemStack sample=new ItemStack(ModItems.DATA_CARD,4,company);mc.player.inventory.setInventorySlotContents(0,sample);
                parent.mouseClicked(layout.controlsX+140,layout.top+200,0);if(!(mc.currentScreen instanceof GuiCustomTexture))throw new IllegalStateException("Company slot must open inventory sample picker");
                GuiCustomTexture picker=(GuiCustomTexture)mc.currentScreen;picker.setWorldAndResolution(mc,size[0],size[1]);int left=(size[0]-252)/2,top=(size[1]-198)/2;
                picker.mouseClicked(left+47,top+143,0);picker.mouseReleased(left+120,top+40,0);
                if(mc.currentScreen!=parent||allowed.getInt(parent)!=(1<<company)||mc.player.inventory.getStackInSlot(0).getCount()!=4)throw new IllegalStateException("Company sample drag/drop consumed item or selected wrong company");
                parent.setWorldAndResolution(mc,size[0],size[1]);
                parent.mouseClicked(layout.controlsX+140,layout.top+200,1);if(allowed.getInt(parent)!=127)throw new IllegalStateException("Right-click must clear filter to any card");cases++;
            }
            mc.getFramebuffer().bindFramebuffer(true);net.minecraft.client.renderer.GlStateManager.clear(16640);mc.entityRenderer.setupOverlayRendering();parent.drawScreen(-1,-1,0);
            javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new java.io.File(output,"door-company-filter-"+size[0]+".png"));
        }}finally{mc.player.inventory.setInventorySlotContents(0,held);mc.displayGuiScreen(previous);mc.player.openContainer=mc.player.inventoryContainer;}
        for(int company=1;company<7;company++){
            TileEntitySpaceDoor door=new TileEntitySpaceDoor();door.configureSecurity(2,1<<company);TileEntitySpaceDoor saved=new TileEntitySpaceDoor();saved.readFromNBT(door.writeToNBT(new NBTTagCompound()));
            if(saved.keyReader().effectiveAllowed()!=(1<<company)||saved.keyReader(1).effectiveAllowed()!=(1<<company))throw new IllegalStateException("Company filter persistence");
            for(int other=0;other<7;other++)if(saved.keyReader().authorized(new ItemStack(ModItems.DATA_CARD,1,other))!=(other==company))throw new IllegalStateException("Company filter authorization");
        }
        System.out.println("[vandorlabs][reprolab] door-company-filter PASS drags="+cases+" two dialog sizes six companies any-card clear inventory-safe samples both-reader persistence authorization");
    }
}
