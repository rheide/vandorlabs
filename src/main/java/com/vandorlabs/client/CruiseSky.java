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
        for(int scale:CruiseAtlas.scales(step)){
            int span=CruiseAtlas.SIZE*scale;
            int x=(int)Math.floor(owner.cruise.x()/span),z=(int)Math.floor(owner.cruise.z()/span);
            int radius=CruiseAtlas.radius(scale,step);
            for(int i=-radius;i<=radius;i++)for(int j=-radius;j<=radius;j++)
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
        // An oblique distant texel can cover many seed samples. Filter that footprint so
        // a pitching camera does not flicker between unrelated fine colours.
        GlStateManager.bindTexture(texture.getGlTextureId());
        org.lwjgl.opengl.ContextCapabilities caps=org.lwjgl.opengl.GLContext.getCapabilities();
        boolean mipmaps=caps.OpenGL30||caps.GL_EXT_framebuffer_object;
        if(mipmaps){
            int levels=Integer.numberOfTrailingZeros(MessageCruiseTerrain.SIZE);
            GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D,org.lwjgl.opengl.GL12.GL_TEXTURE_MAX_LEVEL,levels);
            GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D,org.lwjgl.opengl.GL12.GL_TEXTURE_MAX_LOD,levels);
            if(caps.OpenGL30)org.lwjgl.opengl.GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            else org.lwjgl.opengl.EXTFramebufferObject.glGenerateMipmapEXT(GL11.GL_TEXTURE_2D);
        }
        texture.setBlurMipmap(true,mipmaps);
        GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12_CLAMP_TO_EDGE);
        GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12_CLAMP_TO_EDGE);
        tiles.put(tile,location);
        if(image.heights!=null)elevations.put(tile,image.heights);
        if(tiles.size()>CruiseAtlas.RESIDENT_TILES){
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
        double opacity=cruising?1:FlightEnvelope.seedOpacity(altitude,owner.cruise.handoffAltitude());
        if(opacity<=0)return;
        net.minecraft.client.renderer.entity.RenderManager camera=mc.getRenderManager();
        double x=camera.viewerPosX+(cruising?owner.cruise.view(true,partial)-physicalX:0);
        double z=camera.viewerPosZ+(cruising?owner.cruise.view(false,partial)-physicalZ:0);
        double y=63-altitude-(camera.viewerPosY-physicalY);
        int step=CruiseAtlas.nearStep(altitude);
        double horizon=CruiseAtlas.horizon(altitude);
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
            net.minecraft.util.math.Vec3d fog=mc.world.getFogColor(partial);
            for(Tile t:renderOrder){
                int span=t.step*MessageCruiseTerrain.SIZE;
                double dx=t.x-x,dz=t.z-z;
                if(dx>horizon||dz>horizon||dx+span< -horizon||dz+span< -horizon)continue;
                boolean fade=backed.contains(t);
                int radius=CruiseAtlas.radius(t.step,step);
                if(fade&&(dx>=radius*span||dz>=radius*span||dx+span<=-radius*span||dz+span<=-radius*span))continue;
                mc.getTextureManager().bindTexture(tiles.get(t));
                BufferBuilder b=Tessellator.getInstance().getBuffer();
                byte[] heights=elevations.get(t);
                int divisions=heights==null?8:MessageCruiseTerrain.HEIGHT_GRID-1;
                // Composite atmosphere under each tile on its own mesh so distant ridges
                // never protrude through a flat fog plane. Only pure haze fades into the sky.
                double farX=Math.max(Math.abs(dx),Math.abs(dx+span));
                double farZ=Math.max(Math.abs(dz),Math.abs(dz+span));
                // Clear air contributes zero fog to every vertex of these tiles.
                boolean clearTile=farX*farX+farZ*farZ<=horizon*horizon*.37*.37;
                for(int pass=clearTile?1:0;pass<2;pass++){
                    boolean atmosphere=pass==0;
                    if(atmosphere)GlStateManager.disableTexture2D();else GlStateManager.enableTexture2D();
                    b.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_TEX_COLOR);
                    for(int row=0;row<divisions;row++)for(int col=0;col<divisions;col++){
                        vertex(b,dx,dz,span,col,row,divisions,y,opacity,fade?radius:0,heights,horizon,atmosphere,fog);
                        vertex(b,dx,dz,span,col,row+1,divisions,y,opacity,fade?radius:0,heights,horizon,atmosphere,fog);
                        vertex(b,dx,dz,span,col+1,row+1,divisions,y,opacity,fade?radius:0,heights,horizon,atmosphere,fog);
                        vertex(b,dx,dz,span,col+1,row,divisions,y,opacity,fade?radius:0,heights,horizon,atmosphere,fog);
                    }
                    Tessellator.getInstance().draw();
                }
            }
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
    private static void vertex(BufferBuilder b,double dx,double dz,int span,int col,int row,int divisions,double y,double opacity,int fadeRadius,byte[] heights,double horizon,boolean atmosphere,net.minecraft.util.math.Vec3d fog){
        double u=col/(double)divisions,v=row/(double)divisions,x=dx+u*span,z=dz+v*span;
        // The next coarser tile extends underneath this entire tile. Feather the level
        // change around the aircraft so individual square tile boundaries stay invisible.
        double detail=fadeRadius==0?1:Math.max(0,Math.min(1,(fadeRadius-Math.max(Math.abs(x),Math.abs(z))/span)*2.5));
        double radius=Math.sqrt(x*x+z*z)/horizon;
        double visibility=Math.max(0,Math.min(1,(1-radius)/.14));
        double total=opacity*detail*visibility;
        double clear=Math.max(0,Math.min(1,(.92-radius)/.55));
        double surface=total*clear*clear*(3-2*clear);
        double alpha=atmosphere?(total>=1?1:(total-surface)/(1-surface)):surface;
        int r=atmosphere?(int)(255*fog.x):255,g=atmosphere?(int)(255*fog.y):255,blue=atmosphere?(int)(255*fog.z):255;
        double ground=heights==null?0:((heights[row*MessageCruiseTerrain.HEIGHT_GRID+col]&255)-63)*detail;
        b.pos(x,y+ground,z).tex(u,v).color(r,g,blue,(int)Math.round(255*alpha)).endVertex();
    }
    private CruiseSky(){}
}
