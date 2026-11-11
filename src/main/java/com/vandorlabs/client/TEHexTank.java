package com.vandorlabs.client;

import com.vandorlabs.tiles.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.*;
import net.minecraftforge.fluids.FluidStack;

/** Axial hollow shell with level fluid and windows on its diagonal sides. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="vandorlabs",value=net.minecraftforge.fml.relauncher.Side.CLIENT)
public final class TEHexTank extends TileEntitySpecialRenderer<TileEntityHexTank> {
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public static void stitch(net.minecraftforge.client.event.TextureStitchEvent.Pre event){GEOMETRY.clear();for(String name:new String[]{"port","tank_empty"})event.getMap().registerSprite(new ResourceLocation("vandorlabs:blocks/matter/"+name));}
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public static void unload(net.minecraftforge.event.world.WorldEvent.Unload event){if(event.getWorld().isRemote)GEOMETRY.clear();}
    private static final double[][] OUTLINE=AxialShellGeometry.HEX;
    private static final double[][] BLOCK=AxialShellGeometry.BLOCK;
    private static final double LIQUID=.895, FLOOR=.04, CEILING=.96;
    private static final HexTankGeometryCache GEOMETRY=new HexTankGeometryCache(TileEntityHexTank.CAPACITY);
    static HexTankGeometryCache geometryCache(){return GEOMETRY;}
    private static TextureAtlasSprite sprite(String name){return Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(name);}
    /** Horizontal hexagons retain flat top/bottom faces, like hexagonal thrusters. */
    static double[] orient(double[] p,EnumFacing f){return AxialShellGeometry.orient(p,f);}
    static double fluidLevel(int amount){return FLOOR+(CEILING-FLOOR)*Math.max(0,Math.min(1,amount/(double)TileEntityHexTank.CAPACITY));}
    private static double[] point(HexTankGeometryCache.Outline outline,int edge,double u,double y,double scale){int next=(edge+1)%outline.size();return new double[]{.5+(outline.x(edge)+(outline.x(next)-outline.x(edge))*u-.5)*scale,y,.5+(outline.z(edge)+(outline.z(next)-outline.z(edge))*u-.5)*scale};}
    private static void quad(BufferBuilder b,TextureAtlasSprite s,int color,double[][] vertices,EnumFacing facing,boolean cap){
        float r=(color>>16&255)/255F,g=(color>>8&255)/255F,blue=(color&255)/255F;
        double[][] uv={{0,0},{0,16},{16,16},{16,0}};
        for(int i=0;i<4;i++){double[] p=orient(vertices[i],facing);b.pos(p[0],p[1],p[2]).tex(s.getInterpolatedU(cap?vertices[i][0]*16:uv[i][0]),s.getInterpolatedV(cap?vertices[i][2]*16:uv[i][1])).color(r,g,blue,1).normal(0,1,0).endVertex();}
    }
    private static void side(HexTankGeometryCache.Outline outline,BufferBuilder b,TextureAtlasSprite s,int color,int edge,double left,double right,double bottom,double top,double scale,EnumFacing facing){quad(b,s,color,new double[][]{point(outline,edge,left,top,scale),point(outline,edge,left,bottom,scale),point(outline,edge,right,bottom,scale),point(outline,edge,right,top,scale)},facing,false);}
    private static void cap(HexTankGeometryCache.Outline outline,BufferBuilder b,TextureAtlasSprite s,int color,double y,double scale,EnumFacing facing){double cx=0,cz=0;for(int i=0;i<outline.size();i++){cx+=outline.x(i);cz+=outline.z(i);}cx=.5+(cx/outline.size()-.5)*scale;cz=.5+(cz/outline.size()-.5)*scale;for(int i=0;i<outline.size();i++)quad(b,s,color,new double[][]{{cx,y,cz},point(outline,i,0,y,scale),point(outline,i,1,y,scale),{cx,y,cz}},facing,true);}
    static double[] window(boolean block,int edge,EnumFacing facing){
        if(facing.getAxis()!=EnumFacing.Axis.Y && (block?edge==0||edge==2:edge==0||edge==3))return null;
        double[][] outline=block?BLOCK:OUTLINE;double[] a=outline[edge],b=outline[(edge+1)%outline.length];
        double half=.125/Math.hypot(b[0]-a[0],b[1]-a[1]);return new double[]{.5-half,.5+half,.375,.625};
    }
    static double[] casingUV(double[][] outline,int edge,double u,double y,boolean tiled,double scale){
        double[] a=outline[edge],b=outline[(edge+1)%outline.length];return new double[]{u*(tiled?Math.hypot(b[0]-a[0],b[1]-a[1])*scale:1)*16,(1-y)*16};
    }
    static float[] casingNormal(double[][] outline,int edge,EnumFacing facing){
        return AxialShellGeometry.normal(outline,edge,facing);
    }
    static double area(double[][] polygon){double sum=0;for(int i=0;i<polygon.length;i++){double[] a=polygon[i],b=polygon[(i+1)%polygon.length];sum+=a[0]*b[1]-b[0]*a[1];}return Math.abs(sum)*.5;}
    static double[][] fluidOutline(boolean block,int amount){return GEOMETRY.fluidOutline(block,amount).copy();}
    private static int axis(EnumFacing facing){return facing.getAxis()==EnumFacing.Axis.X?HexTankGeometryCache.X:facing.getAxis()==EnumFacing.Axis.Z?HexTankGeometryCache.Z:HexTankGeometryCache.Y;}

    /** Resolve artwork and diffuse shading while appending to the live, unmodified vertex format. */
    private static final class GeometrySink implements HexTankGeometryCache.Sink {
        private final BufferBuilder buffer;
        private final TextureAtlasSprite casing,liner,port,glass;
        private TextureAtlasSprite current;
        private float r,g,b,nx,ny,nz;
        GeometrySink(BufferBuilder buffer,TextureAtlasSprite casing,TextureAtlasSprite liner,TextureAtlasSprite port,TextureAtlasSprite glass){this.buffer=buffer;this.casing=casing;this.liner=liner;this.port=port;this.glass=glass;}
        public void beginQuad(int material,int color,boolean diffuse,float nx,float ny,float nz){
            current=material==HexTankGeometryCache.CASING?casing:material==HexTankGeometryCache.LINER?liner:material==HexTankGeometryCache.PORT?port:glass;
            float shade=diffuse?net.minecraftforge.client.model.pipeline.LightUtil.diffuseLight(nx,ny,nz):1;
            r=(color>>16&255)/255F*shade;g=(color>>8&255)/255F*shade;b=(color&255)/255F*shade;this.nx=nx;this.ny=ny;this.nz=nz;
        }
        public void vertex(double x,double y,double z,double u,double v){buffer.pos(x,y,z).tex(current.getInterpolatedU(u),current.getInterpolatedV(v)).color(r,g,b,1).normal(nx,ny,nz).endVertex();}
    }
    @Override public void render(TileEntityHexTank tile,double x,double y,double z,float partial,int stage,float alpha){
        if(tile.getWorld()==null||tile.isInvalid()||!tile.getWorld().getBlockState(tile.getPos()).getProperties().containsKey(com.vandorlabs.blocks.BlockAnimatedScreenSelector.FACING))return;
        com.vandorlabs.render.ConnectedSquare.Part joined=HexTankGroups.part(tile);
        if(joined.x!=0||joined.y!=0)return;
        GlStateManager.pushMatrix();
        try(TurntableRenderState state=new TurntableRenderState()){
            GlStateManager.translate(x,y,z);GlStateManager.disableLighting();GlStateManager.disableCull();GlStateManager.color(1,1,1,1);bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            int light=tile.getWorld().getCombinedLight(tile.getPos(),0);OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,light&65535,light>>16);
            EnumFacing facing=tile.getWorld().getBlockState(tile.getPos()).getValue(com.vandorlabs.blocks.BlockAnimatedScreenSelector.FACING);
            if(facing.getAxis()==EnumFacing.Axis.X)GlStateManager.scale(1,joined.size,joined.size);
            else if(facing.getAxis()==EnumFacing.Axis.Z)GlStateManager.scale(joined.size,joined.size,1);
            else GlStateManager.scale(joined.size,1,joined.size);
            TextureAtlasSprite casing=sprite(ScreenHousingTextures.texture(tile.getHousingTexture())),port=sprite("vandorlabs:blocks/matter/port"),liner=sprite("vandorlabs:blocks/matter/tank_empty");
            Tessellator t=Tessellator.getInstance();BufferBuilder b=t.getBuffer();b.begin(7,DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);
            HexTankGeometryCache.Template geometry=GEOMETRY.template(tile.isBlockShape(),axis(facing),tile.getMainTextureLayout()==1,joined.size);
            GeometrySink sink=new GeometrySink(b,casing,liner,port,sprite("minecraft:blocks/glass"));geometry.shell.emit(sink);t.draw();
            FluidStack fluid=null;int total=0;
            for(int a=0;a<joined.size;a++)for(int c=0;c<joined.size;c++){
                net.minecraft.tileentity.TileEntity raw=tile.getWorld().getTileEntity(tile.getPos().offset(HexTankGroups.right(facing.getAxis()),a).offset(HexTankGroups.up(facing.getAxis()),c));
                if(raw instanceof TileEntityHexTank){FluidStack cell=((TileEntityHexTank)raw).tank.getFluid();if(cell!=null){if(fluid==null)fluid=cell.copy();if(fluid.isFluidEqual(cell))total+=cell.amount;}}
            }
            if(fluid!=null)fluid.amount=total/(joined.size*joined.size);
            GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);
            if(fluid!=null&&fluid.amount>0){
                TextureAtlasSprite contents=sprite(fluid.getFluid().getStill(fluid).toString());double top=fluidLevel(fluid.amount);int color=fluid.getFluid().getColor(fluid);
                b.begin(7,DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);
                HexTankGeometryCache.Outline liquid=facing.getAxis()==EnumFacing.Axis.Y?HexTankGeometryCache.fullOutline(tile.isBlockShape()):GEOMETRY.fluidOutline(tile.isBlockShape(),fluid.amount);double high=facing.getAxis()==EnumFacing.Axis.Y?top:CEILING;
                for(int i=0;i<liquid.size();i++)side(liquid,b,contents,color,i,0,1,FLOOR+.0005,high,LIQUID,facing);
                cap(liquid,b,contents,color,high,LIQUID,facing);cap(liquid,b,contents,color,FLOOR+.0005,LIQUID,facing);t.draw();
            }
            // Actual glass panes, with transparent centers rather than a painted level image.
            b.begin(7,DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);geometry.windows.emit(sink);t.draw();
        }finally{GlStateManager.popMatrix();GlStateManager.resetColor();GlStateManager.color(1,1,1,1);}
    }
}
