package com.vandorlabs.client;

import com.vandorlabs.physics.FlightEnvelope;
import com.vandorlabs.screen.VehicleInstrument;
import com.vandorlabs.vehicle.EntityGroundVehicle;
import com.vandorlabs.vehicle.VehiclePose;
import com.vandorlabs.vehicle.VehicleStatus;
import com.vandorlabs.vehicle.VehicleWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Locale;

/** Square-pixel instrument faces for the same physical displays as Vehicle Status. */
final class VehicleInstrumentRenderer {
    private static final int EDGE = 0xff6d8796, FACE = 0xff19232d, MARK = 0xffcadddf, POINTER = 0xffffbb52;
    private static final int STEPS = 32;
    private static final double TAU = Math.PI * 2;

    private VehicleInstrumentRenderer() {}

    static void draw(VehicleInstrument instrument, World world, BlockPos pos, int height) {
        draw(instrument,world,pos,height,false);
    }

    static void draw(VehicleInstrument instrument,World world,BlockPos pos,int height,boolean frontGlass){
        EntityGroundVehicle craft = world instanceof VehicleWorld ? ((VehicleWorld)world).craft : null;
        if (craft == null || craft.structure == null) {
            TileEntity controller = VehicleStatus.controller(world, pos);
            double altitude = controller == null ? pos.getY() : controller.getPos().getY();
            EnumFacing facing = controller == null ? EnumFacing.NORTH : world.getBlockState(controller.getPos())
                    .getValue(com.vandorlabs.blocks.BlockVehicleController.FACING);
            drawFace(instrument,height,0,0,heading(facing,0),altitude,0,0,frontGlass);
            return;
        }
        VehiclePose pose = craft.visualPose(Minecraft.getMinecraft().getRenderPartialTicks());
        double ceiling = craft.dimension == 0 ? FlightEnvelope.MAX_ALTITUDE
                : ((VehicleWorld)world).parent.getActualHeight() - craft.structure.bounds.maxY - 1;
        double projected = craft.flightReadout().projectedSpeed(ceiling) * 20;
        drawFace(instrument,height,pose.pitch,pose.bank,heading(craft.structure.facing,pose.yaw),
                craft.flightAltitude(),Math.abs(craft.driveSpeed)*20,projected,frontGlass);
    }

    private static double heading(EnumFacing face, double yaw) {
        return (face.getHorizontalAngle() + 180 + yaw % 360 + 360) % 360;
    }

    static void drawFace(VehicleInstrument instrument, int height, double pitch, double bank, double heading,
                         double altitude, double speed, double projectedMax) {
        drawFace(instrument,height,pitch,bank,heading,altitude,speed,projectedMax,false);
    }

    private static void drawFace(VehicleInstrument instrument,int height,double pitch,double bank,double heading,
                                 double altitude,double speed,double projectedMax,boolean frontGlass){
        if(instrument==VehicleInstrument.HUD){
            drawHud(height,pitch,bank,heading,altitude,speed,frontGlass);
            return;
        }
        boolean compact = height < 96;
        double x = compact ? 96 : 64;
        double y = compact ? height / 2D : height / 2D + 1;
        double radius = compact ? Math.min(29,height / 2D - 3) : Math.min(45,height / 2D - 15);
        if (radius < 2) return;
        double airspeedRange = Math.max(30, Math.ceil(projectedMax/10)*10);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,240,240);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770,771,1,0);
        RedstoneScreenRenderer.bindWhite();
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(7,BlockSurfaceFormat.get());
        RedstoneScreenRenderer.rect(buffer,0,0,128,height,0,0xff101a22);
        arcDisc(buffer,x,y,radius,.01,EDGE,0,TAU,x,y);
        arcDisc(buffer,x,y,radius-1.4,.015,FACE,0,TAU,x,y);
        switch (instrument) {
            case ATTITUDE:
                attitude(buffer,x,y,radius-3,pitch,bank);
                break;
            case COMPASS:
                marks(buffer,x,y,radius,12,360,0);
                needle(buffer,x,y,radius*.68,heading,.035,POINTER);
                break;
            case ALTIMETER:
                marks(buffer,x,y,radius,20,360,0);
                needle(buffer,x,y,radius*.44,altitude*.036,.03,MARK);
                needle(buffer,x,y,radius*.72,altitude*.36,.035,POINTER);
                break;
            case AIRSPEED:
                marks(buffer,x,y,radius,20,270,-135);
                needle(buffer,x,y,radius*.72,-135+270*Math.min(1,speed/airspeedRange),.035,POINTER);
                break;
            default:
                break;
        }
        Tessellator.getInstance().draw();

        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        String title, value, unit;
        switch (instrument) {
            case ATTITUDE:
                title = "ATTITUDE";
                value = String.format(Locale.ROOT,"%+.0f° pitch",pitch);
                unit = String.format(Locale.ROOT,"%+.0f° bank",bank);
                break;
            case COMPASS:
                title = "COMPASS";
                value = String.format(Locale.ROOT,"%03d°",Math.floorMod(Math.round(heading),360L));
                unit = "";
                compassLabels(font,x,y,radius,compact);
                break;
            case ALTIMETER:
                title = "ALTIMETER";
                value = String.format(Locale.ROOT,"%,.0f m",altitude);
                unit = "";
                scaleLabels(font,x,y,radius,compact,0,360,new String[]{"0","2","4","6","8"});
                break;
            case AIRSPEED:
                title = "AIRSPEED";
                value = String.format(Locale.ROOT,"%,.0f m/s",speed);
                unit = projectedMax > 0 ? String.format(Locale.ROOT,"MAX %,.0f",projectedMax) : "";
                scaleLabels(font,x,y,radius,compact,-135,270,new String[]{"0",Long.toString(Math.round(airspeedRange/2)),Long.toString(Math.round(airspeedRange))});
                break;
            default:
                title = value = unit = "";
        }
        if (compact) {
            label(font,title,35,y-14,64,.86F,0xcadddf);
            label(font,value,35,y-3,64,1,0xffffff);
            label(font,unit,35,y+8,64,.82F,0xa7bbca);
        } else {
            label(font,title,x,4,120,1,0xcadddf);
            label(font,value + (unit.isEmpty() ? "" : "   " + unit),x,height-9,120,.86F,0xffffff);
        }
        GlStateManager.color(1,1,1,1);
        GlStateManager.disableBlend();
    }

    /** Transparent flight symbology. The centre stays clear at the horizon. */
    private static void drawHud(int height,double pitch,double bank,double heading,double altitude,double speed,boolean frontGlass){
        final int green=0xff8dffbf;
        double x=frontGlass?45:64,y=height*(frontGlass?.35:.38),angle=Math.toRadians(bank),nx=Math.sin(angle),ny=Math.cos(angle);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,240,240);
        RedstoneScreenRenderer.bindWhite();
        BufferBuilder b=Tessellator.getInstance().getBuffer();
        b.begin(7,BlockSurfaceFormat.get());
        for(int mark=-90;mark<=90;mark+=10){
            double offset=(pitch-mark)*height/(frontGlass?130:120);
            if(Math.abs(offset)>height*(frontGlass?.09:.13))continue;
            double cx=x+nx*offset,cy=y+ny*offset,span=mark==0?(frontGlass?12:22):7;
            line(b,cx-ny*span,cy+nx*span,cx-ny*3,cy+nx*3,1,.03,green);
            line(b,cx+ny*3,cy-nx*3,cx+ny*span,cy-nx*span,1,.03,green);
        }
        line(b,x-7,y,x-2,y,1,.04,POINTER);
        line(b,x-2,y,x,y+2,1,.04,POINTER);
        line(b,x,y+2,x+2,y,1,.04,POINTER);
        line(b,x+2,y,x+7,y,1,.04,POINTER);
        Tessellator.getInstance().draw();
        FontRenderer font=Minecraft.getMinecraft().fontRenderer;
        double headingX=frontGlass?83:64,headingY=height*(frontGlass?.29:.26);
        double speedX=frontGlass?44:28,altX=frontGlass?84:100;
        double valueY=height*(frontGlass?.66:.43);
        hudLabel(font,String.format(Locale.ROOT,"%03d°",Math.floorMod(Math.round(heading),360L)),headingX,headingY,34,1,green);
        hudLabel(font,"SPD",speedX,valueY-9,32,.8F,green);
        hudLabel(font,Math.round(speed)+" m/s",speedX,valueY,34,1,green);
        hudLabel(font,"ALT",altX,valueY-9,32,.8F,green);
        hudLabel(font,Math.round(altitude)+" m",altX,valueY,34,1,green);
        hudLabel(font,String.format(Locale.ROOT,"%+.0f°",pitch),headingX,height*(frontGlass?.38:.54),32,.85F,green);
        GlStateManager.color(1,1,1,1);
    }

    private static void hudLabel(FontRenderer font,String text,double x,double y,double width,float scale,int color){
        // A one-pixel keyline keeps the projected text readable against snow and bright cloud.
        final int shadow=0xff18362a;
        label(font,text,x-.65,y,width,scale,shadow);
        label(font,text,x+.65,y,width,scale,shadow);
        label(font,text,x,y-.65,width,scale,shadow);
        label(font,text,x,y+.65,width,scale,shadow);
        label(font,text,x,y,width,scale,color);
    }

    private static void attitude(BufferBuilder b,double x,double y,double r,double pitch,double bank) {
        double radians = Math.toRadians(bank);
        double nx = Math.sin(radians), ny = Math.cos(radians);
        double offset = Math.max(-r,Math.min(r,pitch*r/50));
        double groundAngle = Math.atan2(ny,nx);
        double half = Math.acos(offset/r);
        arcDisc(b,x,y,r,.02,0xff3c7199,0,TAU,x,y);
        if (half > 0) {
            arcDisc(b,x,y,r,.025,0xff845d40,groundAngle-half,groundAngle+half,x+nx*offset,y+ny*offset);
        }
        for (int degrees=-20; degrees<=20; degrees+=10) {
            double d = offset + degrees*r/50;
            if (Math.abs(d)>=r) continue;
            double length = Math.sqrt(r*r-d*d);
            if (degrees!=0) length = Math.min(length,r*(Math.abs(degrees)==10?.22:.32));
            double cx=x+nx*d, cy=y+ny*d;
            line(b,cx-ny*length,cy+nx*length,cx+ny*length,cy-nx*length,degrees==0?1.3:.65,.028,MARK);
        }
        double wing=r*.58;
        line(b,x-wing,y,x-r*.12,y,1.5,.035,POINTER);
        line(b,x+r*.12,y,x+wing,y,1.5,.035,POINTER);
        line(b,x-1,y,x+1,y,1.5,.035,POINTER);
    }

    private static void marks(BufferBuilder b,double x,double y,double r,int count,double sweep,double start) {
        int end = sweep==360 ? count-1 : count;
        for (int i=0;i<=end;i++) {
            double angle = Math.toRadians(start+i*sweep/count-90);
            double dx = Math.cos(angle), dy = Math.sin(angle);
            double inner = r * (i%(count==12?3:4)==0?.82:.90);
            line(b,x+dx*inner,y+dy*inner,x+dx*(r-2),y+dy*(r-2),.7,.025,MARK);
        }
    }

    private static void needle(BufferBuilder b,double x,double y,double length,double degrees,double z,int color) {
        double angle = Math.toRadians(degrees-90);
        double dx = Math.cos(angle), dy = Math.sin(angle);
        line(b,x-dx*3,y-dy*3,x+dx*length,y+dy*length,1.5,z,color);
        arcDisc(b,x,y,2.1,z+.001,EDGE,0,TAU,x,y);
    }

    private static void compassLabels(FontRenderer font,double x,double y,double r,boolean compact) {
        scaleLabels(font,x,y,r,compact,0,360,new String[]{"N","E","S","W"});
    }

    private static void scaleLabels(FontRenderer font,double x,double y,double r,boolean compact,double start,double sweep,String[] values) {
        double distance = r * .66;
        for (int i=0;i<values.length;i++) {
            double angle = Math.toRadians(start+i*sweep/(sweep==360?values.length:values.length-1)-90);
            label(font,values[i],x+distance*Math.cos(angle),y+distance*Math.sin(angle)-3,r*.56,compact?.62F:.76F,0xcadddf);
        }
    }

    private static void label(FontRenderer font,String text,double x,double y,double width,float scale,int color) {
        if (text.isEmpty()) return;
        int size = font.getStringWidth(text);
        float fit = Math.min(scale,(float)(width/Math.max(1,size)));
        GlStateManager.pushMatrix();
        GlStateManager.translate(x-size*fit/2,y,0);
        GlStateManager.scale(fit,fit,1);
        RedstoneScreenRenderer.text(font,text,0,0,color);
        GlStateManager.popMatrix();
    }

    private static void arcDisc(BufferBuilder b,double x,double y,double radius,double z,int color,double start,double end,double cx,double cy) {
        for (int i=0;i<STEPS;i++) {
            double a=start+(end-start)*i/STEPS, d=start+(end-start)*(i+1)/STEPS;
            vertex(b,cx,cy,z,color);
            vertex(b,x+Math.cos(d)*radius,y+Math.sin(d)*radius,z,color);
            vertex(b,x+Math.cos(a)*radius,y+Math.sin(a)*radius,z,color);
            vertex(b,x+Math.cos(a)*radius,y+Math.sin(a)*radius,z,color);
        }
    }

    private static void line(BufferBuilder b,double x1,double y1,double x2,double y2,double width,double z,int color) {
        double scale = width / (2 * Math.hypot(x2-x1,y2-y1));
        double nx = (y2-y1)*scale, ny = (x1-x2)*scale;
        vertex(b,x1+nx,y1+ny,z,color);
        vertex(b,x2+nx,y2+ny,z,color);
        vertex(b,x2-nx,y2-ny,z,color);
        vertex(b,x1-nx,y1-ny,z,color);
    }

    private static void vertex(BufferBuilder b,double x,double y,double z,int color) {
        b.pos(x,y,z).color(color>>16&255,color>>8&255,color&255,color>>>24).tex(.5,.5).lightmap(240,240).normal(0,0,1).endVertex();
    }
}
