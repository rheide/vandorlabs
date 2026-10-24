package com.vandorlabs.client;

import com.vandorlabs.blocks.RedstoneScreenInteractions;
import com.vandorlabs.render.ScreenSurface;
import com.vandorlabs.tiles.RedstoneScreenContents;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.*;
import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;

/** Text and buttons share an affine 128-pixel display plane with hit testing. */
final class RedstoneScreenRenderer {
    /** Display lists can change driver bindings without updating Minecraft's texture cache. */
    static void bindWhite(){GlStateManager.enableLighting();GlStateManager.disableLighting();GlStateManager.color(1,1,1,1);
        GlStateManager.enableCull();GlStateManager.disableCull();
        // UI vertices are already in the display's local matrix. A different tile
        // may have left an offset on the singleton buffer instead of Forge's batch buffer.
        Tessellator.getInstance().getBuffer().setTranslation(0,0,0);
        // Native/batched tile renderers can leave the driver on the lightmap unit
        // while Minecraft's cache still reports the atlas unit. Force both transitions.
        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.disableTexture2D();GlStateManager.enableTexture2D();
        GlStateManager.bindTexture(0);Minecraft.getMinecraft().getTextureManager().bindTexture(RenderIdentifierCache.white());}
    private static final java.util.Map<RedstoneScreenContents,com.vandorlabs.ui.RedstoneScreenOverlay.Cache> PLANS=new java.util.WeakHashMap<>();
    private static final com.vandorlabs.ui.RedstoneScreenOverlay.RowAccess<RedstoneScreenContents.Row> ROWS=new com.vandorlabs.ui.RedstoneScreenOverlay.RowAccess<RedstoneScreenContents.Row>(){
        public String label(RedstoneScreenContents.Row row){return row.label;}public boolean slider(RedstoneScreenContents.Row row){return row.slider;}public boolean pulse(RedstoneScreenContents.Row row){return row.pulse;}public boolean active(RedstoneScreenContents.Row row){return row.active();}public int segments(RedstoneScreenContents.Row row){return row.segments();}public int selected(RedstoneScreenContents.Row row){return row.selectedSegment();}
    };
    private static final java.util.Map<ScreenSurface.Quad,FloatBuffer> MATRICES=new java.util.IdentityHashMap<>();
    private static FloatBuffer matrix(ScreenSurface.Quad q,int height){
        FloatBuffer b=BufferUtils.createFloatBuffer(16);
        b.put(new float[]{(float)(q.topLeft.x-q.topRight.x)/128,0,0,0,
            0,(float)(q.bottomRight.y-q.topRight.y)/height,(float)(q.bottomRight.z-q.topRight.z)/height,0,
            0,q.ny,q.nz,0,(float)q.topRight.x,(float)q.topRight.y+q.ny*.02F,(float)q.topRight.z+q.nz*.02F,1});b.flip();return b;
    }
    static void draw(RedstoneScreenContents tile,IBlockState state){
        ScreenSurface.Quad q=RedstoneScreenInteractions.surface(state,tile.tile(),tile.slot());
        int height=RedstoneScreenInteractions.displayHeight(state.getBlock(),tile.slot()),count=Math.min(tile.rows().size(),tile.maxRows());
        FloatBuffer transform=MATRICES.get(q);
        if(transform==null){transform=matrix(q,height);MATRICES.put(q,transform);}
        int top=RedstoneScreenInteractions.rowTop(state.getBlock(),tile.slot());
        boolean header=!RedstoneScreenInteractions.half(state.getBlock(),tile.slot());
        GlStateManager.pushMatrix();transform.rewind();GlStateManager.multMatrix(transform);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,240,240);
        GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);
        // Submit every solid panel together, before the font pass. Small depth
        // layers prevent coplanar text/background flicker at oblique angles.
        GlStateManager.enableTexture2D();
        bindWhite();
        BufferBuilder buffer=Tessellator.getInstance().getBuffer();
        buffer.begin(org.lwjgl.opengl.GL11.GL_QUADS,BlockSurfaceFormat.get());
        com.vandorlabs.ui.RedstoneScreenOverlay.Cache cache=PLANS.computeIfAbsent(tile,ignored->new com.vandorlabs.ui.RedstoneScreenOverlay.Cache());
        com.vandorlabs.ui.RedstoneScreenOverlay plan=cache.plan(com.vandorlabs.ui.RedstoneScreenLayout.of(!header),tile.title(),tile.rows(),ROWS);
        for(int i=0;i<plan.solids.size();i++){com.vandorlabs.ui.RedstoneScreenOverlay.Rect r=plan.solids.get(i);rect(buffer,r.left,r.top,r.right,r.bottom,r.layer,r.color);}
        Tessellator.getInstance().draw();GlStateManager.enableTexture2D();
        FontRenderer font=Minecraft.getMinecraft().fontRenderer;
        for(int i=0;i<plan.text.size();i++){com.vandorlabs.ui.RedstoneScreenOverlay.Text t=plan.text.get(i);text(font,t.maxWidth>0?font.trimStringToWidth(t.value,t.maxWidth):t.value,t.x,t.y,t.color);}
        GlStateManager.color(1,1,1,1);GlStateManager.disableBlend();GlStateManager.popMatrix();
    }
    static void drawVehicle(com.vandorlabs.tiles.TileEntityAnimatedScreenSelector tile,IBlockState state,int slot){
        int height=RedstoneScreenInteractions.displayHeight(state.getBlock(),slot),top=RedstoneScreenInteractions.rowTop(state.getBlock(),slot);
        ScreenSurface.Quad q=RedstoneScreenInteractions.surface(state,tile,slot);
        FloatBuffer transform=MATRICES.get(q);if(transform==null){transform=matrix(q,height);MATRICES.put(q,transform);}
        GlStateManager.pushMatrix();transform.rewind();GlStateManager.multMatrix(transform);
        drawVehicleContents(tile.getWorld(),tile.getPos(),height,top);GlStateManager.popMatrix();
    }
    static void drawVehicleContents(net.minecraft.world.World world,net.minecraft.util.math.BlockPos pos,int height,int top){
        if(!com.vandorlabs.vehicle.VehicleStatus.hasController(world,pos)){
            GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);bindWhite();BufferBuilder missing=Tessellator.getInstance().getBuffer();missing.begin(org.lwjgl.opengl.GL11.GL_QUADS,BlockSurfaceFormat.get());rect(missing,0,0,128,height,0,0xff362b19);Tessellator.getInstance().draw();
            FontRenderer font=Minecraft.getMinecraft().fontRenderer;java.util.List<String> lines=font.listFormattedStringToWidth("Vehicle Controller missing",112);int y=Math.max(4,(height-lines.size()*10)/2);for(String line:lines){text(font,line,8,y,0xffd19b);y+=10;}GlStateManager.color(1,1,1,1);GlStateManager.disableBlend();return;
        }
        com.vandorlabs.vehicle.EntityGroundVehicle craft=world instanceof com.vandorlabs.vehicle.VehicleWorld?((com.vandorlabs.vehicle.VehicleWorld)world).craft:null;
        boolean hover=craft!=null&&craft.hover;
        com.vandorlabs.tiles.TileEntityAutopilot autopilot=com.vandorlabs.vehicle.VehicleStatus.find(world,pos);
        boolean on=autopilot!=null&&autopilot.enabled();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,240,240);
        GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.enableTexture2D();
        bindWhite();
        BufferBuilder buffer=Tessellator.getInstance().getBuffer();buffer.begin(org.lwjgl.opengl.GL11.GL_QUADS,BlockSurfaceFormat.get());
        rect(buffer,0,0,128,height,0,craft==null?0xFF362B19:hover?0xFF152D48:0xFF153A29);
        if(autopilot!=null)rect(buffer,6,top,122,top+10,.01,on?0xFF37D8AD:0xFF495C69);
        int mode=craft==null?0:hover?2:1;
        for(int i=0;i<3;i++)rect(buffer,6+i*39,top+12,43+i*39,top+22,.01,i==mode?0xFF37D8AD:0xFF495C69);
        if(autopilot!=null)rect(buffer,6,top+36,48,top+46,.01,0xFF495C69);Tessellator.getInstance().draw();
        FontRenderer font=Minecraft.getMinecraft().fontRenderer;
        if(height>64)text(font,"VEHICLE STATUS",8,8,0xE1EEFF);
        if(autopilot!=null)text(font,on?"AUTOPILOT ON":"Autopilot OFF",8,top+1,on?0x072D24:0xFFFFFF);
        String[] modes={"Block","Taxi","Hover"};for(int i=0;i<3;i++)text(font,modes[i],8+i*39,top+13,i==mode?0x072D24:0xFFFFFF);
        if(autopilot!=null)text(font,font.trimStringToWidth(autopilot.status,114),8,top+25,0xCEE9ED);
        if(autopilot!=null)text(font,"Next >",8,top+37,0xFFFFFF);
        if(hover){double clearance=craft.groundClearance();text(font,Double.isFinite(clearance)?String.format(java.util.Locale.ROOT,"Height: %.1f blocks",clearance):"Height: 32+ blocks",8,top+47,0xC1E8FF);}
        GlStateManager.color(1,1,1,1);GlStateManager.disableBlend();
    }

    static void rect(BufferBuilder b,int left,int top,int right,int bottom,double z,int color){
        int r=color>>16&255,g=color>>8&255,blue=color&255,a=color>>>24;
        b.pos(left,bottom,z).color(r,g,blue,a).tex(.5,.5).lightmap(240,240).normal(0,0,1).endVertex();b.pos(right,bottom,z).color(r,g,blue,a).tex(.5,.5).lightmap(240,240).normal(0,0,1).endVertex();
        b.pos(right,top,z).color(r,g,blue,a).tex(.5,.5).lightmap(240,240).normal(0,0,1).endVertex();b.pos(left,top,z).color(r,g,blue,a).tex(.5,.5).lightmap(240,240).normal(0,0,1).endVertex();
    }
    static void text(FontRenderer font,String value,int x,int y,int color){
        GlStateManager.pushMatrix();GlStateManager.translate(0,0,.04F);
        font.drawString(value,x,y,color);GlStateManager.popMatrix();
    }
    private RedstoneScreenRenderer(){}
}
