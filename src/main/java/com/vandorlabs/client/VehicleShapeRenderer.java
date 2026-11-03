package com.vandorlabs.client;

import com.vandorlabs.animation.VisualClock;
import com.vandorlabs.vehicle.VehicleShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;

/** Small monochrome solid voxel model. No block models, textures, worlds or tile renderers. */
public final class VehicleShapeRenderer {
    private static final java.util.Map<int[],VehicleShape> SHAPES=new java.util.LinkedHashMap<>(128,.75F,true);
    public static VehicleShape shape(net.minecraft.nbt.NBTTagCompound tag){if(tag==null)return null;int[] bits=tag.getIntArray("VehicleShape");if(bits.length!=VehicleShape.WORDS)return null;VehicleShape shape=SHAPES.get(bits);if(shape==null){if(SHAPES.size()>=128){java.util.Iterator<int[]> it=SHAPES.keySet().iterator();it.next();it.remove();}shape=VehicleShape.read(tag);SHAPES.put(bits,shape);}return shape;}
    private static final VisualClock CLOCK=new VisualClock();
    private static final java.util.Map<VehicleShape,Mesh[]> MESHES=new java.util.LinkedHashMap<>(32,.75F,true);
    private static final java.util.Map<Object,Motion> MOTION=new java.util.WeakHashMap<>();
    private static int retained;
    static int meshBuilds;
    private static final class Mesh{final int list,bytes;Mesh(int list,int bytes){this.list=list;this.bytes=bytes;}}
    private static final class Motion{double tick,angle;int speed;Motion(double tick,int speed){this.tick=tick;this.speed=speed;}}
    public static double angle(Object owner,int speed){return angleAt(owner,ticks(),speed);}
    static double angleAt(Object owner,double tick,int speed){Motion motion=MOTION.get(owner);if(motion==null){motion=new Motion(tick,speed);MOTION.put(owner,motion);}motion.angle+=(tick-motion.tick)*(.6*(1<<motion.speed));motion.tick=tick;motion.speed=Math.max(0,Math.min(2,speed));return motion.angle;}
    static void clear(){for(Mesh[] variants:MESHES.values())for(Mesh mesh:variants)if(mesh!=null)GLAllocation.deleteDisplayLists(mesh.list);MESHES.clear();MOTION.clear();SHAPES.clear();retained=0;}
    private VehicleShapeRenderer(){}
    public static double ticks(){return CLOCK.sample(System.nanoTime(),Minecraft.getMinecraft().isGamePaused());}
    public static void draw(VehicleShape shape,float cx,float cy,float width,float height){
        draw(shape,cx,cy,width,height,ticks()*1.2);
    }
    // Generic, Pinnacle, Ashiro, Paladyne, Vektor, Seyr, Vandor. Bright shaded previews.
    private static final int[] COLORS={0xeeeeee,0xffd34d,0xff6868,0x8aff75,0xa78bff,0xff9b45,0xa5e0e6};
    static int chipColor(net.minecraft.item.ItemStack chip){int company=chip.getMetadata();return COLORS[company>=0&&company<COLORS.length?company:0];}
    public static void draw(VehicleShape shape,float cx,float cy,float width,float height,double angle){draw(shape,cx,cy,width,height,angle,6);}
    public static void drawChip(VehicleShape shape,float cx,float cy,float width,float height,double angle,net.minecraft.item.ItemStack chip){int company=chip.getMetadata();draw(shape,cx,cy,width,height,angle,company>=0&&company<COLORS.length?company:0);}
    private static void draw(VehicleShape shape,float cx,float cy,float width,float height,double angle,int company){
        if(shape==null)return;
        GlStateManager.pushMatrix();try(TurntableRenderState saved=new TurntableRenderState()){
            GlStateManager.translate(cx,cy,.5);GlStateManager.scale(1,1,.005);
            float scale=Math.min(width/20,height/20);GlStateManager.scale(scale,-scale,scale);
            GlStateManager.rotate(22,1,0,0);GlStateManager.rotate((float)angle,0,1,0);GlStateManager.translate(-shape.centerX,-shape.centerY,-shape.centerZ);
            GlStateManager.disableTexture2D();GlStateManager.disableLighting();GlStateManager.disableCull();GlStateManager.enableDepth();GlStateManager.depthMask(true);
            Mesh[] variants=MESHES.get(shape);
            Mesh mesh=variants==null?null:variants[company];
            if(mesh==null){
                // Display lists retain immutable position/colour vertices; rotations only change the matrix.
                int list=GLAllocation.generateDisplayLists(1);org.lwjgl.opengl.GL11.glNewList(list,org.lwjgl.opengl.GL11.GL_COMPILE);
                int bytes;try{bytes=build(shape,COLORS[company]);}finally{org.lwjgl.opengl.GL11.glEndList();}
                mesh=new Mesh(list,bytes);meshBuilds++;
                while(!MESHES.isEmpty()&&(MESHES.size()>=128||retained+bytes>8*1024*1024)){java.util.Iterator<Mesh[]> it=MESHES.values().iterator();Mesh[] removed=it.next();it.remove();for(Mesh old:removed)if(old!=null){retained-=old.bytes;GLAllocation.deleteDisplayLists(old.list);}}
                variants=MESHES.get(shape);if(variants==null){variants=new Mesh[COLORS.length];MESHES.put(shape,variants);}variants[company]=mesh;retained+=bytes;
            }
            GlStateManager.callList(mesh.list);
        }finally{GlStateManager.popMatrix();}
    }
    private static int build(VehicleShape shape,int color){
            BufferBuilder b=Tessellator.getInstance().getBuffer();b.setTranslation(0,0,0);b.begin(7,DefaultVertexFormats.POSITION_COLOR);
            for(int y=0;y<12;y++)for(int z=0;z<12;z++)for(int x=0;x<12;x++)if(shape.occupied(x,y,z)){
                if(z==0||!shape.occupied(x,y,z-1))face(b,x,y,z,x,y+1,z,x+1,y+1,z,x+1,y,z,shade(color,85,165,16),shade(color,140,224,8),shade(color,165,230,0));
                if(z==11||!shape.occupied(x,y,z+1))face(b,x,y,z+1,x+1,y,z+1,x+1,y+1,z+1,x,y+1,z+1,shade(color,112,165,16),shade(color,181,224,8),shade(color,200,230,0));
                if(x==0||!shape.occupied(x-1,y,z))face(b,x,y,z,x,y,z+1,x,y+1,z+1,x,y+1,z,shade(color,65,165,16),shade(color,119,224,8),shade(color,150,230,0));
                if(x==11||!shape.occupied(x+1,y,z))face(b,x+1,y,z,x+1,y+1,z,x+1,y+1,z+1,x+1,y,z+1,shade(color,90,165,16),shade(color,156,224,8),shade(color,180,230,0));
                if(y==11||!shape.occupied(x,y+1,z))face(b,x,y+1,z,x,y+1,z+1,x+1,y+1,z+1,x+1,y+1,z,shade(color,165,165,16),shade(color,224,224,8),shade(color,230,230,0));
                if(y==0||!shape.occupied(x,y-1,z))face(b,x,y,z,x+1,y,z,x+1,y,z+1,x,y,z+1,shade(color,50,165,16),shade(color,90,224,8),shade(color,110,230,0));
            }
            int bytes=b.getVertexCount()*DefaultVertexFormats.POSITION_COLOR.getNextOffset();Tessellator.getInstance().draw();return bytes;
    }
    private static int shade(int color,int light,int base,int shift){return Math.min(255,((color>>shift)&255)*light/base);}
    private static void face(BufferBuilder b,double x,double y,double z,double a,double c,double d,double e,double f,double g,double h,double i,double j,int r,int green,int blue){
        b.pos(x,y,z).color(r,green,blue,255).endVertex();b.pos(a,c,d).color(r,green,blue,255).endVertex();b.pos(e,f,g).color(r,green,blue,255).endVertex();b.pos(h,i,j).color(r,green,blue,255).endVertex();
    }
}
