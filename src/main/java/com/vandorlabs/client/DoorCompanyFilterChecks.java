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
            GuiSpaceDoor parent=new GuiSpaceDoor(tile);parent.inventorySlots.windowId=-99;mc.displayGuiScreen(parent);parent.setWorldAndResolution(mc,size[0],size[1]);ProgrammableDialogLayout layout=new ProgrammableDialogLayout(size[0],size[1]);parent.actionPerformed(new net.minecraft.client.gui.GuiButton(193,0,0,"Security"));
            for(int company=1;company<7;company++){
                parent.setWorldAndResolution(mc,size[0],size[1]);
                ItemStack sample=new ItemStack(ModItems.ACCESS_CARD,4,company);mc.player.inventory.setInventorySlotContents(0,sample);
                allowed.setInt(parent,127);for(int step=0;step<company;step++)parent.mouseClicked(layout.controlsX+140,layout.top+140,0);
                if(mc.currentScreen!=parent||allowed.getInt(parent)!=(1<<company)||mc.player.inventory.getStackInSlot(0).getCount()!=4)throw new IllegalStateException("Inline company selection changed window, item or company");
                String expected=new String[]{"Any card","Pinnacle","Ashiro","Paladyne","Vektor","Seyr","Vandor"}[company];
                if(!expected.equals(parent.companyLabel()))throw new IllegalStateException("Selected company name missing from door dialog");
                parent.setWorldAndResolution(mc,size[0],size[1]);
                parent.mouseClicked(layout.controlsX+40,layout.top+140,1);if(allowed.getInt(parent)!=127)throw new IllegalStateException("Right-click must clear filter to any card");cases++;
                if(!"Any card".equals(parent.companyLabel()))throw new IllegalStateException("Cleared filter label is stale");
                for(int button=0;button<2;button++){allowed.setInt(parent,1<<company);parent.mouseClicked(layout.controlsX+40,layout.top+140,button);if(allowed.getInt(parent)!=127)throw new IllegalStateException("Company label click must clear filter");}
            }
            net.minecraft.client.shader.Framebuffer frame=new net.minecraft.client.shader.Framebuffer(size[0]*2,size[1]*2,true);
            org.lwjgl.opengl.GL11.glPushAttrib(org.lwjgl.opengl.GL11.GL_ALL_ATTRIB_BITS);
            net.minecraft.client.renderer.GlStateManager.matrixMode(5889);net.minecraft.client.renderer.GlStateManager.pushMatrix();net.minecraft.client.renderer.GlStateManager.loadIdentity();net.minecraft.client.renderer.GlStateManager.ortho(0,size[0],size[1],0,1000,3000);
            net.minecraft.client.renderer.GlStateManager.matrixMode(5888);net.minecraft.client.renderer.GlStateManager.pushMatrix();net.minecraft.client.renderer.GlStateManager.loadIdentity();net.minecraft.client.renderer.GlStateManager.translate(0,0,-2000);
            try{
                allowed.setInt(parent,1<<1);
                frame.bindFramebuffer(true);net.minecraft.client.renderer.GlStateManager.clear(16640);parent.drawScreen(-1,-1,0);
                javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(frame.framebufferWidth,frame.framebufferHeight,frame),"png",new java.io.File(output,"door-company-filter-"+size[0]+".png"));
            }finally{
                net.minecraft.client.renderer.GlStateManager.popMatrix();net.minecraft.client.renderer.GlStateManager.matrixMode(5889);net.minecraft.client.renderer.GlStateManager.popMatrix();net.minecraft.client.renderer.GlStateManager.matrixMode(5888);org.lwjgl.opengl.GL11.glPopAttrib();frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);
            }
        }}finally{mc.player.inventory.setInventorySlotContents(0,held);mc.displayGuiScreen(previous);mc.player.openContainer=mc.player.inventoryContainer;}
        for(int company=1;company<7;company++){
            TileEntitySpaceDoor door=new TileEntitySpaceDoor();door.configureSecurity(2,1<<company);TileEntitySpaceDoor saved=new TileEntitySpaceDoor();saved.readFromNBT(door.writeToNBT(new NBTTagCompound()));
            if(saved.keyReader().effectiveAllowed()!=(1<<company)||saved.keyReader(1).effectiveAllowed()!=(1<<company))throw new IllegalStateException("Company filter persistence");
            for(int other=0;other<7;other++)if(saved.keyReader().authorized(new ItemStack(ModItems.ACCESS_CARD,1,other))!=(other==company))throw new IllegalStateException("Company filter authorization");
        }
        System.out.println("[vandorlabs][reprolab] door-company-filter PASS selections="+cases+" two dialog sizes six companies company-name label left/right-click clear inventory-safe selection both-reader persistence authorization");
    }
}
