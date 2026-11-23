package com.vandorlabs.client;

import com.vandorlabs.navigation.CruiseAtlas;
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
    public static boolean buffered(){
        if(owner==null)return false;
        for(Tile t:tiles.keySet())if(covers(t,owner.cruise.x(),owner.cruise.z()))return true;
        return false;
    }
    private static void clear(){
        for(ResourceLocation loc:tiles.values())Minecraft.getMinecraft().getTextureManager().deleteTexture(loc);
        tiles.clear();renderOrder.clear();backed.clear();owner=null;
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
        if(tiles.size()>96){
            Iterator<ResourceLocation> it=tiles.values().iterator();
            mc.getTextureManager().deleteTexture(it.next());it.remove();
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
        if(owner==null||mc.player==null||mc.player.getRidingEntity()!=owner||!owner.cruise.active())return;
        float partial=event.getPartialTicks();
        double x=owner.cruise.view(true,partial),z=owner.cruise.view(false,partial);
        double altitude=owner.cruise.altitude()+(1-partial)*(owner.lastTickPosY-owner.posY);
        int step=CruiseAtlas.nearStep(altitude);
        double horizon=step*16.0*MessageCruiseTerrain.SIZE;
        double scale=Math.max(32,mc.gameSettings.renderDistanceChunks*12)/horizon;
        double y=-Math.max(.15,(altitude-63)*scale);
        GlStateManager.pushMatrix();
        GlStateManager.depthMask(false);
        GL11.glDepthRange(1,1);
        boolean lit=GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean blend=GL11.glIsEnabled(GL11.GL_BLEND);
        int shading=GL11.glGetInteger(GL11.GL_SHADE_MODEL);
        try{
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
                    mc.getTextureManager().bindTexture(tiles.get(t));
                    BufferBuilder b=Tessellator.getInstance().getBuffer();
                    b.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_TEX_COLOR);
                    int divisions=8;
                    boolean fade=backed.contains(t);
                    for(int row=0;row<divisions;row++)for(int col=0;col<divisions;col++){
                        vertex(b,dx,dz,span,col,row,divisions,horizon,y,scale,fade);
                        vertex(b,dx,dz,span,col,row+1,divisions,horizon,y,scale,fade);
                        vertex(b,dx,dz,span,col+1,row+1,divisions,horizon,y,scale,fade);
                        vertex(b,dx,dz,span,col+1,row,divisions,horizon,y,scale,fade);
                    }
                    Tessellator.getInstance().draw();
            }
        }finally{
            if(lit)GlStateManager.enableLighting();
            if(!blend)GlStateManager.disableBlend();
            GlStateManager.shadeModel(shading);
            GL11.glDepthRange(0,1);GlStateManager.depthMask(true);GlStateManager.popMatrix();
        }
    }
    private static void vertex(BufferBuilder b,double dx,double dz,int span,int col,int row,int divisions,double horizon,double y,double scale,boolean fade){
        double u=col/(double)divisions,v=row/(double)divisions,x=dx+u*span,z=dz+v*span;
        double distance=Math.sqrt(x*x+z*z)/horizon;
        double haze=Math.max(0,Math.min(1,(distance-.06)/.94));
        // The next coarser tile extends underneath this entire tile. Feather the level
        // change around the aircraft so individual square tile boundaries stay invisible.
        double detail=fade?Math.max(0,Math.min(1,(1-Math.max(Math.abs(x),Math.abs(z))/span)*2.5)):1;
        int opacity=(int)(255*(1-haze)*(1-haze)*detail);
        b.pos(x*scale,y,z*scale).tex(u,v).color(255,255,255,opacity).endVertex();
    }
    private CruiseSky(){}
}
