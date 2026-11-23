package com.vandorlabs.client;

import com.vandorlabs.navigation.CruiseAtlas;
import com.vandorlabs.physics.FlightEnvelope;
import com.vandorlabs.navigation.CruiseAtlas.Tile;
import com.vandorlabs.network.MessageCruiseTerrain;
import com.vandorlabs.vehicle.EntityGroundVehicle;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.GL11;

/** Geographic tiles use one projection at every level of detail. */
@Mod.EventBusSubscriber(modid="vandorlabs",value=Side.CLIENT)
public final class CruiseSky {
    private static EntityGroundVehicle owner;
    private static final Map<Tile,ResourceLocation> tiles=new LinkedHashMap<>();
    private static final List<Tile> renderOrder=new ArrayList<>();
    private static final Map<Tile,byte[]> elevations=new HashMap<>();
    private static final Set<Tile> backed=new HashSet<>();
    private static boolean covers(Tile t,double x,double z){
        int span=t.step*MessageCruiseTerrain.SIZE;
        return x>=t.x&&x<t.x+span&&z>=t.z&&z<t.z+span;
    }
    public static boolean ready(){return owner!=null&&owner.cruise.active()&&buffered();}
    public static boolean detailed(){
        if(!ready())return false;
        int step=CruiseAtlas.nearStep(owner.cruise.altitude());
        for(Tile t:tiles.keySet())if(t.step==step&&covers(t,owner.cruise.x(),owner.cruise.z()))return true;
        return false;
    }
    public static boolean covered(){
        if(!detailed())return false;
        int step=CruiseAtlas.nearStep(owner.cruise.altitude());
        for(int scale:new int[]{step,step*4,step*16}){
            int span=CruiseAtlas.SIZE*scale;
            int x=(int)Math.floor(owner.cruise.x()/span),z=(int)Math.floor(owner.cruise.z()/span);
            for(int i=-1;i<=1;i++)for(int j=-1;j<=1;j++)
                if(!tiles.containsKey(new Tile((x+i)*span,(z+j)*span,scale)))return false;
        }
        return true;
    }
    public static boolean reliefAtCenter(){
        if(owner==null)return false;
        for(Tile t:elevations.keySet())if(t.step==4&&covers(t,owner.cruise.x(),owner.cruise.z()))return true;
        return false;
    }
    public static boolean buffered(){
        if(owner==null)return false;
        for(Tile t:tiles.keySet())if(covers(t,owner.cruise.x(),owner.cruise.z()))return true;
        return false;
    }
    private static void clear(){
        for(ResourceLocation loc:tiles.values())Minecraft.getMinecraft().getTextureManager().deleteTexture(loc);
        tiles.clear();elevations.clear();renderOrder.clear();backed.clear();owner=null;
    }
    public static void receive(MessageCruiseTerrain image){
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.player==null||!(mc.player.getRidingEntity() instanceof EntityGroundVehicle))return;
        EntityGroundVehicle craft=(EntityGroundVehicle)mc.player.getRidingEntity();
        if(craft.getEntityId()!=image.entity)return;
        if(owner!=craft){clear();owner=craft;}
        Tile tile=new Tile(image.x,image.z,image.step);
        if(tiles.get(tile)!=null)return;
        DynamicTexture texture=new DynamicTexture(MessageCruiseTerrain.SIZE,MessageCruiseTerrain.SIZE);
        System.arraycopy(image.pixels,0,texture.getTextureData(),0,image.pixels.length);
        ResourceLocation location=mc.getTextureManager().getDynamicTextureLocation("flight_terrain",texture);
        texture.updateDynamicTexture();
        texture.setBlurMipmap(true,false);
        GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12_CLAMP_TO_EDGE);
        GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12_CLAMP_TO_EDGE);
        tiles.put(tile,location);
        if(image.heights!=null)elevations.put(tile,image.heights);
        if(tiles.size()>96){
            Tile oldest=tiles.keySet().iterator().next();
            mc.getTextureManager().deleteTexture(tiles.remove(oldest));
            elevations.remove(oldest);
        }
        renderOrder.clear();renderOrder.addAll(tiles.keySet());
        renderOrder.sort(Comparator.comparingInt((Tile t)->t.step).reversed());
        backed.clear();
        for(Tile t:renderOrder)for(Tile coarse:renderOrder){
            if(coarse.step<=t.step)break;
            if(covers(coarse,t.x,t.z)){backed.add(t);break;}
        }
    }
    private static final int GL12_CLAMP_TO_EDGE=org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||owner==null)return;
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.player==null||mc.player.getRidingEntity()!=owner||!owner.cruise.active()&&(!owner.flying()||owner.flightAltitude()<200))clear();
    }
    @SubscribeEvent public static void render(RenderWorldLastEvent event){
        Minecraft mc=Minecraft.getMinecraft();
        if(owner==null||mc.player==null||mc.player.getRidingEntity()!=owner||!owner.flying()||owner.dimension!=0)return;
        float partial=event.getPartialTicks();
        boolean cruising=owner.cruise.active();
        double physicalX=owner.lastTickPosX+partial*(owner.posX-owner.lastTickPosX);
        double physicalZ=owner.lastTickPosZ+partial*(owner.posZ-owner.lastTickPosZ);
        double physicalY=owner.lastTickPosY+partial*(owner.posY-owner.lastTickPosY);
        double altitude=owner.cruise.altitude()+physicalY-owner.posY;
        double opacity=cruising?1:FlightEnvelope.seedOpacity(altitude);
        if(opacity<=0)return;
        net.minecraft.client.renderer.entity.RenderManager camera=mc.getRenderManager();
        double x=camera.viewerPosX+(cruising?owner.cruise.view(true,partial)-physicalX:0);
        double z=camera.viewerPosZ+(cruising?owner.cruise.view(false,partial)-physicalZ:0);
        double y=63-altitude-(camera.viewerPosY-physicalY);
        int step=CruiseAtlas.nearStep(altitude);
        double horizon=step*16.0*MessageCruiseTerrain.SIZE;
        double scale=Math.max(32,mc.gameSettings.renderDistanceChunks*12)/horizon;
        // Scale in eye space. Real and seed coastlines then share a sightline, including
        // in third person or a banked seat, while the horizon fits the native far clip.
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.scale(scale,scale,scale);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.depthMask(false);
        // In the transition band the seed surface must pass the depth of native terrain.
        // Preserve the nearer aircraft geometry. In cruise the stars and sky are at depth one.
        GL11.glDepthRange(cruising?1:Math.max(0,1-3*scale),1);
        boolean alphaTest=GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        boolean lit=GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean blend=GL11.glIsEnabled(GL11.GL_BLEND);
        int shading=GL11.glGetInteger(GL11.GL_SHADE_MODEL);
        try{
            GlStateManager.disableAlpha();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770,771,1,0);
            GlStateManager.shadeModel(GL11.GL_SMOOTH);
            GlStateManager.disableLighting();
            GlStateManager.color(1,1,1,1);
            // Larger tiles cover the horizon while finer ones arrive and during altitude transitions.
            for(Tile t:renderOrder){
                int span=t.step*MessageCruiseTerrain.SIZE;
                double dx=t.x-x,dz=t.z-z;
                if(dx>horizon||dz>horizon||dx+span< -horizon||dz+span< -horizon)continue;
                boolean fade=backed.contains(t);
                if(fade&&(dx>=span||dz>=span||dx<=-2*span||dz<=-2*span))continue;
                mc.getTextureManager().bindTexture(tiles.get(t));
                BufferBuilder b=Tessellator.getInstance().getBuffer();
                b.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_TEX_COLOR);
                byte[] heights=elevations.get(t);
                int divisions=heights==null?8:MessageCruiseTerrain.HEIGHT_GRID-1;
                for(int row=0;row<divisions;row++)for(int col=0;col<divisions;col++){
                    vertex(b,dx,dz,span,col,row,divisions,y,opacity,fade,heights);
                    vertex(b,dx,dz,span,col,row+1,divisions,y,opacity,fade,heights);
                    vertex(b,dx,dz,span,col+1,row+1,divisions,y,opacity,fade,heights);
                    vertex(b,dx,dz,span,col+1,row,divisions,y,opacity,fade,heights);
                }
                Tessellator.getInstance().draw();
            }
            haze(mc,partial,horizon,y,opacity);
        }finally{
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            if(alphaTest)GlStateManager.enableAlpha();
            if(lit)GlStateManager.enableLighting();
            if(!blend)GlStateManager.disableBlend();
            GlStateManager.shadeModel(shading);
            GL11.glDepthRange(0,1);GlStateManager.depthMask(true);
        }
    }
    private static void vertex(BufferBuilder b,double dx,double dz,int span,int col,int row,int divisions,double y,double opacity,boolean fade,byte[] heights){
        double u=col/(double)divisions,v=row/(double)divisions,x=dx+u*span,z=dz+v*span;
        // The next coarser tile extends underneath this entire tile. Feather the level
        // change around the aircraft so individual square tile boundaries stay invisible.
        double detail=fade?Math.max(0,Math.min(1,(1-Math.max(Math.abs(x),Math.abs(z))/span)*2.5)):1;
        int alpha=(int)Math.round(255*opacity*detail);
        double ground=heights==null?0:((heights[row*MessageCruiseTerrain.HEIGHT_GRID+col]&255)-63)*detail;
        b.pos(x,y+ground,z).tex(u,v).color(255,255,255,alpha).endVertex();
    }
    /** Tint the distant surface after drawing opaque land and water. Fading the base
     * texture itself reveals stars through the ocean whenever there is no coarser tile. */
    private static void haze(Minecraft mc,float partial,double horizon,double y,double opacity){
        net.minecraft.util.math.Vec3d fog=mc.world.getFogColor(partial);
        int r=(int)(255*fog.x),g=(int)(255*fog.y),blue=(int)(255*fog.z);
        BufferBuilder b=Tessellator.getInstance().getBuffer();
        GlStateManager.disableTexture2D();
        try{
            b.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_COLOR);
            for(int row=-12;row<12;row++)for(int col=-12;col<12;col++){
                hazeVertex(b,col,row,horizon,y,opacity,r,g,blue);
                hazeVertex(b,col,row+1,horizon,y,opacity,r,g,blue);
                hazeVertex(b,col+1,row+1,horizon,y,opacity,r,g,blue);
                hazeVertex(b,col+1,row,horizon,y,opacity,r,g,blue);
            }
            Tessellator.getInstance().draw();
        }finally{GlStateManager.enableTexture2D();}
    }
    private static void hazeVertex(BufferBuilder b,int col,int row,double horizon,double y,double opacity,int r,int g,int blue){
        double distance=Math.sqrt(col*col+row*row)/8;
        double clear=Math.max(0,Math.min(1,(1-distance)/.94));
        b.pos(col*horizon/8,y,row*horizon/8).color(r,g,blue,(int)(255*opacity*(1-clear*clear))).endVertex();
    }
    private CruiseSky(){}
}
