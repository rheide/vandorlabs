package com.vandorlabs.client;

import com.vandorlabs.screen.VehicleStatusLayout;
import com.vandorlabs.vehicle.VehicleStatus;

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
    private static final java.util.Map<ScreenSurface.Quad,FloatBuffer> MATRICES=new java.util.WeakHashMap<>();
    private static FloatBuffer matrix(ScreenSurface.Quad q,int height){
        FloatBuffer b=BufferUtils.createFloatBuffer(16);
        b.put(new float[]{(float)(q.topLeft.x-q.topRight.x)/128,0,0,0,
            0,(float)(q.bottomRight.y-q.topRight.y)/height,(float)(q.bottomRight.z-q.topRight.z)/height,0,
            0,q.ny,q.nz,0,(float)q.topRight.x,(float)q.topRight.y+q.ny*.02F,(float)q.topRight.z+q.nz*.02F,1});b.flip();return b;
    }
    static void draw(RedstoneScreenContents tile,IBlockState state){
        ScreenSurface.Quad q=FrameSurfaceRenderer.content(tile.tile(),RedstoneScreenInteractions.surface(state,tile.tile(),tile.slot()));
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
        rect(buffer,0,0,128,height,0,0xFF0A141D);if(header)rect(buffer,3,3,125,20,.01,0xFF23465A);
        for(int i=0;i<count;i++){
            RedstoneScreenContents.Row row=tile.rows().get(i);int y=top+i*RedstoneScreenInteractions.ROW_HEIGHT;
            rect(buffer,RedstoneScreenInteractions.ROW_LEFT,y,RedstoneScreenInteractions.ROW_RIGHT,y+10,.01,!row.slider && row.active()?0xFF174C3B:0xFF1C2B37);
            if(row.status)battery(buffer,y,row.level());
            else if(row.slider){
                for(int segment=0;segment<row.segments();segment++){
                    int width=RedstoneScreenContents.SLIDER_RIGHT-RedstoneScreenContents.SLIDER_LEFT;
                    int left=RedstoneScreenContents.SLIDER_LEFT+width*segment/row.segments(),right=RedstoneScreenContents.SLIDER_LEFT+width*(segment+1)/row.segments();
                    rect(buffer,left,y,right,y+10,.02,0xFF91B5C8);
                    rect(buffer,left+1,y+1,right-1,y+9,.03,row.selectedSegment()==segment?(segment==0?0xFFFFB33B:0xFF38DDE4):(segment==0?0xFF4D3820:0xFF183B46));
                }
            }else rect(buffer,98,y,120,y+10,.02,row.active()?0xFF30C58A:0xFF45576A);
        }
        Tessellator.getInstance().draw();GlStateManager.enableTexture2D();
        FontRenderer font=Minecraft.getMinecraft().fontRenderer;
        if(header)text(font,font.trimStringToWidth(tile.title(),RedstoneScreenText.TITLE_WIDTH),8,8,0xC8EEFF);
        if(tile.rows().isEmpty()){
            text(font,"No controls",8,40,0xB8C8D0);
            text(font,"Configure to add",8,54,0x748C9C);
        }
        for(int i=0;i<count;i++){
            RedstoneScreenContents.Row row=tile.rows().get(i);int y=top+i*RedstoneScreenInteractions.ROW_HEIGHT;
            text(font,font.trimStringToWidth(row.label,row.slider?RedstoneScreenContents.SLIDER_LABEL_WIDTH:RedstoneScreenText.LABEL_WIDTH),8,y+1,row.active()?0xDEFFF0:0xB8C8D8);
            if(!row.status&&!row.slider)text(font,row.active()?"ON":row.pulse?"GO":"OFF",100,y+1,row.active()?0x082419:0xE0E8EF);
        }
        GlStateManager.color(1,1,1,1);GlStateManager.disableBlend();GlStateManager.popMatrix();
    }
    static void battery(BufferBuilder buffer,int y,int level){
        rect(buffer,96,y,119,y+10,.02,0xFF91B5C8);
        rect(buffer,119,y+3,121,y+7,.02,0xFF91B5C8);
        rect(buffer,97,y+1,118,y+9,.03,0xFF142732);
        for(int i=0;i<5;i++){
            int left=98+4*i;
            rect(buffer,left,y+2,left+3,y+8,.04,level>3*i?0xFF46D4B5:0xFF33434D);
        }
    }
    static void drawVehicle(com.vandorlabs.tiles.TileEntityAnimatedScreenSelector tile,IBlockState state,int slot){
        int height=RedstoneScreenInteractions.displayHeight(state.getBlock(),slot);
        ScreenSurface.Quad q=FrameSurfaceRenderer.content(tile,RedstoneScreenInteractions.surface(state,tile,slot));
        FloatBuffer transform=MATRICES.get(q);if(transform==null){transform=matrix(q,height);MATRICES.put(q,transform);}
        GlStateManager.pushMatrix();transform.rewind();GlStateManager.multMatrix(transform);
        drawVehicleContents(tile.getWorld(),tile.getPos(),height);GlStateManager.popMatrix();
    }
    static void drawVehicleContents(net.minecraft.world.World world,net.minecraft.util.math.BlockPos pos,int height){
        boolean missing=!VehicleStatus.hasController(world,pos);
        com.vandorlabs.vehicle.EntityGroundVehicle craft=world instanceof com.vandorlabs.vehicle.VehicleWorld?((com.vandorlabs.vehicle.VehicleWorld)world).craft:null;
        com.vandorlabs.tiles.TileEntityVehicleController controller=missing?null:VehicleStatus.controller(world,pos);
        boolean busy=!missing&&(craft!=null?craft.transferInProgress:controller!=null&&controller.converting);
        boolean hover=craft!=null&&craft.hover;
        boolean compact=height<=64;
        boolean cruise=craft!=null&&craft.cruise.active();
        boolean flightTelemetry=craft!=null&&(craft.flying()||craft.takeoffRoll());
        boolean emergency=!missing&&!busy&&VehicleStatus.offersEmergencyPark(world,Minecraft.getMinecraft().player);
        com.vandorlabs.tiles.TileEntityAutopilot autopilot=missing||busy||flightTelemetry||cruise?null:VehicleStatus.find(world,pos);
        boolean on=autopilot!=null&&autopilot.enabled();
        VehicleStatusLayout layout=VehicleStatusLayout.forHeight(height);
        int selected=craft==null?VehicleStatusLayout.PARK:craft.takeoffRoll()?VehicleStatusLayout.FLIGHT:craft.vehicleMode();
        int available=missing||busy?0:VehicleStatus.availableModes(world,pos,Minecraft.getMinecraft().player);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,240,240);
        GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.enableTexture2D();
        bindWhite();
        BufferBuilder buffer=Tessellator.getInstance().getBuffer();buffer.begin(org.lwjgl.opengl.GL11.GL_QUADS,BlockSurfaceFormat.get());
        rect(buffer,0,0,128,height,0,craft==null||missing?0xFF362B19:craft.flying()?0xFF222947:hover?0xFF152D48:0xFF153A29);
        if(busy){
            int progress=(int)(VehicleShapeRenderer.ticks()*3)%90;
            rect(buffer,6,layout.footerTop,122,layout.footerBottom,.01,VehicleStatusLayout.ENABLED_COLOR);
            rect(buffer,6+progress,layout.footerTop,32+progress,layout.footerBottom,.02,VehicleStatusLayout.ACTIVE_COLOR);
        }
        if(autopilot!=null)rect(buffer,6,layout.autopilotTop,122,layout.autopilotBottom,.01,on?VehicleStatusLayout.ACTIVE_COLOR:VehicleStatusLayout.ENABLED_COLOR);
        for(int mode=0;mode<VehicleStatusLayout.MODE_COUNT;mode++)rect(buffer,layout.left(mode),layout.top(mode),
                layout.right(mode),layout.bottom(mode),.01,layout.buttonColor(available,mode,selected));
        if(autopilot!=null)rect(buffer,6,layout.footerTop,48,layout.footerBottom,.01,VehicleStatusLayout.ENABLED_COLOR);
        if(emergency)rect(buffer,52,layout.footerTop,122,layout.footerBottom,.01,0xFF883C41);
        Tessellator.getInstance().draw();
        FontRenderer font=Minecraft.getMinecraft().fontRenderer;
        String name=missing?"Vehicle Controller missing":controller==null||controller.vehicleName.isEmpty()?"VEHICLE STATUS":controller.vehicleName;
        for(int mode=0;mode<VehicleStatusLayout.MODE_COUNT;mode++){
            int color=!layout.enabled(available,mode)?0xA0AAB0:mode==selected?0x072D24:0xFFFFFF;
            String label=VehicleStatusLayout.label(mode);
            text(font,label,layout.centerX(mode)-font.getStringWidth(label)/2,layout.centerY(mode)-4,color);
        }
        if(!compact)text(font,font.trimStringToWidth(name,112),8,7,missing?0xFFD19B:0xE1EEFF);
        if(busy){
            if(compact)statusText(font,name,8,3,112,0xE1EEFF);
            statusText(font,"Converting...",8,layout.footerTop+2,112,0xFFFFFF);
        }else{
            String motion="";
            if(flightTelemetry){
                String state=craft.flightLanded()?"Landed":craft.descendingFromFlight()?"Landing":craft.descendingToFlightLimit()?"Descending":craft.takeoffRoll()?"Takeoff roll":"Flight";
                motion=state+"  "+oneDecimal(Math.abs(craft.driveSpeed)*20)+" m/s";
            }
            if(autopilot!=null){
                statusText(font,on?"AUTOPILOT ON":"Autopilot OFF",8,layout.autopilotTop+2,112,on?0x072D24:0xFFFFFF);
                statusText(font,"Next >",8,layout.footerTop+2,38,0xFFFFFF);
            }else if(compact)statusText(font,flightTelemetry?motion:name,8,3,112,0xE1EEFF);
            if(emergency)statusText(font,"Emergency Park",55,layout.footerTop+2,64,0xFFFFFF);
            if(craft!=null&&!missing){
                double clearance=cruise?Double.POSITIVE_INFINITY:craft.groundClearance();
                String elevation="Y "+oneDecimal(craft.flightAltitude());
                com.vandorlabs.vehicle.VehicleFlightReadout readout=craft.flightReadout();
                String fuel=VehicleStatusLayout.fuelText(readout.fuel,readout.capacity);
                String location=cruise?"X "+Math.round(craft.cruise.x())+"  Z "+Math.round(craft.cruise.z())
                        :Double.isFinite(clearance)?(compact?"AGL ":"Ground clearance ")+oneDecimal(clearance)+" m":"";
                if(compact){
                    statusText(font,elevation,8,43,48,0xC1E8FF);
                    statusText(font,fuel,57,43,63,0xC1E8FF);
                    if(autopilot==null)statusText(font,location,8,54,emergency?40:112,0xC1E8FF);
                }else{
                    statusText(font,elevation,8,79,50,0xC1E8FF);
                    statusText(font,autopilot==null?motion:autopilot.status,59,79,61,0xCEE9ED);
                    statusText(font,location,8,89,112,0xC1E8FF);
                    statusText(font,fuel,8,99,112,0xC1E8FF);
                }
            }
        }
        GlStateManager.color(1,1,1,1);GlStateManager.disableBlend();
    }

    private static String oneDecimal(double value){
        long units=Math.round(value*10),absolute=Math.abs(units);
        return (units<0?"-":"")+(absolute/10)+"."+(absolute%10);
    }

    private static void statusText(FontRenderer font,String value,int x,int y,int width,int color){
        if(value.isEmpty())return;
        float scale=Math.min(.92F,width/(float)Math.max(1,font.getStringWidth(value)));
        GlStateManager.pushMatrix();GlStateManager.translate(x,y,0);GlStateManager.scale(scale,scale,1);
        text(font,value,0,0,color);GlStateManager.popMatrix();
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
