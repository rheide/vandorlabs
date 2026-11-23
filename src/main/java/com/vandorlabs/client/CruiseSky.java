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
    // Separate from Minecraft's world frustum: the atlas uses a scaled projection.
    private static final net.minecraft.client.renderer.culling.ClippingHelperImpl skyFrustum=
            new net.minecraft.client.renderer.culling.ClippingHelperImpl();
    private static final Map<Tile,ResourceLocation> tiles=new LinkedHashMap<>();
    private static final List<Tile> renderOrder=new ArrayList<>();
    private static final Map<Tile,byte[]> elevations=new HashMap<>();
    private static final Map<Tile,Integer> peaks=new HashMap<>();
    private static final Map<Tile,List<Tile>> parents=new HashMap<>();
    private static final int[] parentSpan=new int[13],parentFade=new int[13];
    private static final double[] parentEdge=new double[13];
    private static int parentCount;
    private static double overlap(Tile tile,double altitude,double margin){
        double interior=coveredRadius[Integer.numberOfTrailingZeros(tile.step)];
        double peak=peaks.getOrDefault(tile,63);
        return Math.max(0,interior*Math.min(1,(altitude-margin-peak)/Math.max(1,altitude-margin-62))-2*margin);
    }
    private static double underlay(double distance,double edge){
        return edge==0?1:Math.max(0,Math.min(1,(distance-edge*.90)/(edge*.10)));
    }
    private static double detail(double distance,int span,int radius){
        return radius==0?1:Math.max(0,Math.min(1,(radius-distance/span)*2.5));
    }
    private static long footprintKey=Long.MIN_VALUE;
    private static final double[] coveredRadius=new double[13];
    private static final double[] xs=new double[32],zs=new double[32];
    private static void updateCoverage(double x,double z,int near){
        int span=near*MessageCruiseTerrain.SIZE;
        long key=new net.minecraft.util.math.BlockPos(Math.floor(x/span),near,Math.floor(z/span)).toLong();
        if(key==footprintKey)return;
        footprintKey=key;
        Arrays.fill(coveredRadius,0);
        for(int level:CruiseAtlas.scales(near)){
            int radius=CruiseAtlas.radius(level,near),size=level*MessageCruiseTerrain.SIZE;
            int cx=(int)Math.floor(x/size),cz=(int)Math.floor(z/size);
            boolean complete=true;
            for(int i=-radius;i<=radius&&complete;i++)for(int j=-radius;j<=radius;j++)
                if(!tiles.containsKey(new Tile((cx+i)*size,(cz+j)*size,level))){complete=false;break;}
            if(complete){
                // Stay inside the full-colour portion of the finer mesh including its edge interpolation.
                double interior=(radius-.55)*size;
                for(int index=Integer.numberOfTrailingZeros(level)+1;index<coveredRadius.length;index++)
                    coveredRadius[index]=Math.max(coveredRadius[index],interior);
            }
        }
    }
    /** Add the hole edges to the regular relief grid so even a very coarse tile can omit its centre. */
    private static int axis(double[] axis,double start,int span,int cells,int stride,double hole,double edge){
        int count=0;
        for(int c=0;c<=cells;c+=stride)axis[count++]=start+span*c/(double)cells;
        if(hole>0){
            int base=count;
            for(int i=0;i<4;i++){
                double cut=i==0?-edge:i==1?-hole:i==2?hole:edge;
                if(cut>start&&cut<start+span)axis[count++]=cut;
            }
            if(count!=base)Arrays.sort(axis,0,count);
        }
        return count;
    }
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
        footprintKey=Long.MIN_VALUE;tiles.clear();elevations.clear();peaks.clear();renderOrder.clear();parents.clear();owner=null;
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
        footprintKey=Long.MIN_VALUE;
        tiles.put(tile,location);
        if(image.heights!=null){
            elevations.put(tile,image.heights);
            int peak=63;
            for(byte height:image.heights)peak=Math.max(peak,height&255);
            peaks.put(tile,peak);
        }
        if(tiles.size()>CruiseAtlas.RESIDENT_TILES){
            Tile oldest=tiles.keySet().iterator().next();
            mc.getTextureManager().deleteTexture(tiles.remove(oldest));
            elevations.remove(oldest);peaks.remove(oldest);
        }
        renderOrder.clear();renderOrder.addAll(tiles.keySet());
        renderOrder.sort(Comparator.comparingInt((Tile t)->t.step).reversed());
        parents.clear();
        for(Tile t:renderOrder)for(Tile coarse:renderOrder){
            if(coarse.step<=t.step)break;
            if(covers(coarse,t.x,t.z))parents.computeIfAbsent(t,key->new ArrayList<>()).add(coarse);
        }
    }
    private static final int GL12_CLAMP_TO_EDGE=org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||owner==null)return;
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.player==null||mc.player.getRidingEntity()!=owner||!owner.cruise.active()&&(!owner.flying()||owner.flightAltitude()<200))clear();
    }
    @SubscribeEvent public static void render(RenderWorldLastEvent event){draw(event,false,true);}
    /** Draw the same mesh fully opaque over alternate backdrops for the live LOD boundary check. */
    public static int coverMask(RenderWorldLastEvent event,boolean prune){return draw(event,true,prune);}
    private static int draw(RenderWorldLastEvent event,boolean mask,boolean prune){
        Minecraft mc=Minecraft.getMinecraft();
        if(owner==null||mc.player==null||mc.player.getRidingEntity()!=owner||!owner.flying()||owner.dimension!=0)return 0;
        float partial=event.getPartialTicks();
        boolean cruising=owner.cruise.active();
        double physicalX=owner.lastTickPosX+partial*(owner.posX-owner.lastTickPosX);
        double physicalZ=owner.lastTickPosZ+partial*(owner.posZ-owner.lastTickPosZ);
        double physicalY=owner.lastTickPosY+partial*(owner.posY-owner.lastTickPosY);
        double altitude=owner.cruise.altitude()+physicalY-owner.posY;
        double opacity=cruising?1:FlightEnvelope.seedOpacity(altitude,owner.cruise.handoffAltitude());
        if(opacity<=0)return 0;
        boolean earlyBlend=opacity<.8;
        if(mask)opacity=1;
        int trimmed=0;
        if(CruiseTransitionProfile.ENABLED&&!CruiseTransitionProfile.startSeed())return 0;
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
            skyFrustum.init();
            if(!cruising)updateCoverage(x,z,step);
            double eyeMargin=mc.gameSettings.thirdPersonView==0?4:VehicleClient.cameraDistance()+4;
            for(Tile t:renderOrder){
                int span=t.step*MessageCruiseTerrain.SIZE;
                double dx=t.x-x,dz=t.z-z;
                if(dx>horizon||dz>horizon||dx+span< -horizon||dz+span< -horizon)continue;
                List<Tile> ancestors=parents.get(t);
                boolean fade=ancestors!=null;
                int radius=CruiseAtlas.radius(t.step,step);
                if(fade&&(dx>=radius*span||dz>=radius*span||dx+span<=-radius*span||dz+span<=-radius*span))continue;
                if(!skyFrustum.isBoxInFrustum(dx,y-63,dz,dx+span,y+192,dz+span))continue;
                mc.getTextureManager().bindTexture(tiles.get(t));
                BufferBuilder b=Tessellator.getInstance().getBuffer();
                byte[] heights=elevations.get(t);
                int cells=heights==null?8:MessageCruiseTerrain.HEIGHT_GRID-1;
                // Early in the blend the visible native terrain supplies the fine relief.
                // Keep every colour sample; use every second relief vertex on the overlay.
                int stride=earlyBlend?2:1;
                // Relief can rise above the coarser surface. Keep a guard beneath the
                // finer outer edge for height parallax, camera orbit and incomplete tiles.
                double overlap=cruising||!prune?0:overlap(t,altitude,eyeMargin);
                double hole=overlap*.90;
                if(hole>0&&dx<hole&&dz<hole&&dx+span>-hole&&dz+span>-hole)trimmed++;
                if(hole>0&&dx>=-hole&&dz>=-hole&&dx+span<=hole&&dz+span<=hole)continue;
                parentCount=0;
                if(!cruising&&prune&&opacity<1&&ancestors!=null){
                    for(Tile parent:ancestors){
                        parentSpan[parentCount]=parent.step*MessageCruiseTerrain.SIZE;
                        parentFade[parentCount]=parents.containsKey(parent)?CruiseAtlas.radius(parent.step,step):0;
                        parentEdge[parentCount++]=overlap(parent,altitude,eyeMargin);
                    }
                }
                int nx=axis(xs,dx,span,cells,stride,hole,overlap),nz=axis(zs,dz,span,cells,stride,hole,overlap);
                // Composite atmosphere under each tile on its own mesh so distant ridges
                // never protrude through a flat fog plane. Only pure haze fades into the sky.
                double farX=Math.max(Math.abs(dx),Math.abs(dx+span));
                double farZ=Math.max(Math.abs(dz),Math.abs(dz+span));
                // Clear air contributes zero fog to every vertex of these tiles.
                boolean clearTile=farX*farX+farZ*farZ<=horizon*horizon*.37*.37;
                for(int pass=clearTile?1:0;pass<2;pass++){
                    boolean atmosphere=pass==0;
                    if(atmosphere)GlStateManager.disableTexture2D();else GlStateManager.enableTexture2D();
                    b.begin(GL11.GL_TRIANGLE_STRIP,DefaultVertexFormats.POSITION_TEX_COLOR);
                    boolean previous=false;
                    for(int row=0;row<nz-1;row++){
                        boolean cut=hole>0&&zs[row]>=-hole&&zs[row+1]<=hole;
                        int col=0;
                        while(col<nx-1){
                            if(cut&&xs[col]>=-hole&&xs[col+1]<=hole){col++;continue;}
                            int first=col++;
                            while(col<nx-1&&!(cut&&xs[col]>=-hole&&xs[col+1]<=hole))col++;
                            // Repeat the previous end and next start to join independent strips.
                            if(previous)vertex(b,dx,dz,span,xs[first],zs[row],y,opacity,fade?radius:0,heights,horizon,atmosphere,fog,overlap);
                            for(int c=first;c<=col;c++){
                                vertex(b,dx,dz,span,xs[c],zs[row],y,opacity,fade?radius:0,heights,horizon,atmosphere,fog,overlap);
                                vertex(b,dx,dz,span,xs[c],zs[row+1],y,opacity,fade?radius:0,heights,horizon,atmosphere,fog,overlap);
                            }
                            vertex(b,dx,dz,span,xs[col],zs[row+1],y,opacity,fade?radius:0,heights,horizon,atmosphere,fog,overlap);
                            previous=true;
                        }
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
            if(CruiseTransitionProfile.ENABLED)CruiseTransitionProfile.endSeed();
        }
        return trimmed;
    }
    private static void vertex(BufferBuilder b,double dx,double dz,int span,double x,double z,double y,double opacity,int fadeRadius,byte[] heights,double horizon,boolean atmosphere,net.minecraft.util.math.Vec3d fog,double overlap){
        double u=(x-dx)/span,v=(z-dz)/span;
        // The next coarser tile extends underneath this entire tile. Feather the level
        // change around the aircraft so individual square tile boundaries stay invisible.
        double distance=Math.max(Math.abs(x),Math.abs(z));
        double detail=detail(distance,span,fadeRadius);
        double radius=Math.sqrt(x*x+z*z)/horizon;
        double visibility=Math.max(0,Math.min(1,(1-radius)/.14));
        double weight=opacity*visibility;
        // Preserve the amount of native terrain visible through the original multilevel
        // blend. Carry any omitted parent's opacity into the next available finer layer.
        double originalTransmission=1,remainingTransmission=1;
        for(int i=0;i<parentCount;i++){
            originalTransmission*=1-weight*detail(distance,parentSpan[i],parentFade[i]);
            remainingTransmission+=(originalTransmission-remainingTransmission)*underlay(distance,parentEdge[i]);
        }
        double nominal=weight*detail;
        double total=remainingTransmission>0
                ?1-originalTransmission*(1-nominal)/remainingTransmission:nominal;
        total=Math.max(0,Math.min(1,total))*underlay(distance,overlap);
        double clear=Math.max(0,Math.min(1,(.92-radius)/.55));
        double surface=total*clear*clear*(3-2*clear);
        double alpha=atmosphere?(total>=1?1:(total-surface)/(1-surface)):surface;
        int r=atmosphere?(int)(255*fog.x):255,g=atmosphere?(int)(255*fog.y):255,blue=atmosphere?(int)(255*fog.z):255;
        double ground=0;
        if(heights!=null){
            int grid=MessageCruiseTerrain.HEIGHT_GRID;
            double gu=u*(grid-1),gv=v*(grid-1);
            int col=Math.min(grid-2,Math.max(0,(int)gu)),row=Math.min(grid-2,Math.max(0,(int)gv));
            int i=row*grid+col;
            double a=heights[i]&255,bx=heights[i+1]&255,c=heights[i+grid]&255,d=heights[i+grid+1]&255;
            double top=a+(bx-a)*(gu-col),bottom=c+(d-c)*(gu-col);
            ground=(top+(bottom-top)*(gv-row)-63)*detail;
        }
        b.pos(x,y+ground,z).tex(u,v).color(r,g,blue,(int)Math.round(255*alpha)).endVertex();
    }
    private CruiseSky(){}
}
