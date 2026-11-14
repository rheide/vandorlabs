package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.GL11;

/** Real authored aperture polygons, transforms, UVs and conservative grouping boundaries. */
public final class PortholeRetainedGeometryChecks {
    private static int checks,cases;
    private static String fixture;
    private static void need(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message+" ["+fixture+"]");}
    private static TileEntityAnimatedScreenSelector put(NonRenderingChecks.MemoryWorld world,BlockPos pos,IBlockState state,int shape,int mode){
        TileEntityAnimatedScreenSelector tile=new TileEntityAnimatedScreenSelector();tile.setJoinPortholes(true);tile.setPortholeShape(shape);
        tile.setGlassMaterial(-1);tile.setGlassShade(19);tile.setGlassAppearance(1,false);tile.setDiagonalGeometry(mode,0);
        tile.setWorld(world);tile.setPos(pos);world.states.put(pos,state);world.tiles.put(pos,tile);return tile;
    }
    private static int[] records(StaticSurfaceMesh mesh){
        BufferBuilder out=new BufferBuilder(4096);out.begin(GL11.GL_QUADS,BlockSurfaceFormat.get());mesh.draw(out,128,32);out.finishDrawing();return out.getVertexState().getRawBuffer();
    }
    private static double value(int[] records,int at){return Float.intBitsToFloat(records[at]);}
    private static List<String> footprint(StaticSurfaceMesh mesh,TextureAtlasSprite sprite){
        int[] raw=records(mesh);VertexFormat format=BlockSurfaceFormat.get();int stride=format.getIntegerSize(),uv=format.getUvOffsetById(0)/4;
        List<String> result=new ArrayList<>();
        for(int i=0;i<raw.length;i+=stride)result.add(Math.round(value(raw,i)*10000)+":"+Math.round(value(raw,i+1)*10000)+":"
                +Math.round(sprite.getUnInterpolatedU((float)value(raw,i+uv))*10000)+":"+Math.round(sprite.getUnInterpolatedV((float)value(raw,i+uv+1))*10000));
        Collections.sort(result);return result;
    }
    private static void parallel(StaticSurfaceMesh mesh,int rotation,BlockPos pos,boolean expected){
        need((mesh.portholePlanes(rotation,pos)!=null)==expected,"parallel plane signature misclassified exact pane geometry");
    }
    private static void compareModelTransform(TileEntityAnimatedScreenSelector tile,IBlockState state,PortholeRetainedGeometry.Surfaces surface){
        BlockProgrammableWall block=(BlockProgrammableWall)state.getBlock();
        TextureAtlasSprite wall=TEAnimatedScreenSelector.wallSprite(tile),metal=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/programmable_glass/metal_side");
        EnumFacing facing=state.getValue(BlockProgrammableWall.FACING);
        PortholeHex.Slice slice=TEAnimatedScreenSelector.portholeGroup(tile,state).slice(tile.getPos(),facing.rotateY(),EnumFacing.UP);
        StaticSurfaceMesh.Capture original=StaticSurfaceMesh.independentCapture();TEAnimatedScreenSelector.drawConfiguredWall(original,tile,state,wall,metal,slice);
        int[] before=records(original.finish()),after=records(surface.housing);int stride=BlockSurfaceFormat.get().getIntegerSize();
        need(before.length==after.length,"retained housing changed actual emitted topology");
        boolean full=block instanceof BlockProgrammablePortholeBlock;double depth=full?-24:PanelDepth.offset(state.getValue(BlockProgrammableWall.DEPTH)),scale=full?4:1;
        for(int i=0;i<before.length;i+=stride){
            need(Math.abs(value(before,i)-value(after,i))<1E-6&&Math.abs(value(before,i+1)-value(after,i+1))<1E-6,"baking changed housing X/Y");
            need(Math.abs(value(before,i+2)*scale+depth-value(after,i+2))<1E-5,"baked model depth differs from legacy GL transform");
            need(before[i+4]==after[i+4]&&before[i+5]==after[i+5],"housing atlas UV changed during transform");
        }
        need(surface.rotation==((int)(180-facing.getHorizontalAngle())/90&3),"model facing quarter-turn differs from renderer");
    }
    static void run(){
        BlockProgrammableWall flat=new BlockProgrammableWall("retained_porthole_geometry",BlockProgrammableWall.Shape.PORTHOLE);
        BlockProgrammableWall full=new BlockProgrammablePortholeBlock();
        BlockProgrammableWall diagonal=new BlockProgrammableWall("retained_diagonal_porthole_geometry",BlockProgrammableWall.Shape.DIAGONAL_PORTHOLE);
        BlockPos origin=new BlockPos(8,80,8);
        for(BlockProgrammableWall block:new BlockProgrammableWall[]{flat,full})for(EnumFacing facing:EnumFacing.HORIZONTALS)
            for(int depth=0;depth<3;depth++)for(int shape=0;shape<4;shape++){
                fixture="flat/full="+(block==full)+" facing="+facing+" depth="+depth+" shape="+shape;
                NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);
                IBlockState state=block.getDefaultState().withProperty(BlockProgrammableWall.FACING,facing).withProperty(BlockProgrammableWall.DEPTH,depth);
                TileEntityAnimatedScreenSelector tile=put(world,origin,state,shape,0);
                PortholeRetainedGeometry.Surfaces baseline=PortholeRetainedGeometry.capture(tile,state,false),optimized=PortholeRetainedGeometry.capture(tile,state,true);
                need(optimized.single,"ordinary flat palette did not select compensated center sheet");
                need(baseline.housing.sameGeometry(optimized.housing),"pane optimization altered opaque aperture/rim");
                compareModelTransform(tile,state,baseline);
                need(footprint(baseline.pane,PortholePane.sprite(tile)).equals(footprint(optimized.pane,PortholePane.retainedPalette(tile,state).sprite)),"single sheet filled the aperture or changed glimmer UVs");
                double plane=block==full?8:8+PanelDepth.offset(depth);
                need(optimized.pane.inside(-1,-1,plane-1E-5,17,17,plane+1E-5),"optimized glass is not the actual center plane");
                parallel(optimized.pane,optimized.rotation,origin,true);
                need(optimized.housing.box(7.9,7.9,-1,8.1,8.1,17,false).vertexCount()==0,"housing filled the aperture center");
                for(int tint:new int[]{0,1,2,GlassTextures.OPAQUE}){
                    tile.setGlassAppearance(tint,false);PortholeRetainedGeometry.Surfaces selected=PortholeRetainedGeometry.capture(tile,state,true);
                    need(selected.single==(tint!=GlassTextures.OPAQUE),"opaque pane incorrectly collapsed or transparent pane excluded");
                    if(tint==GlassTextures.OPAQUE)need(selected.pane.sameGeometry(PortholeRetainedGeometry.capture(tile,state,false).pane),"opaque palette changed original thickness");
                }
                tile.setGlassMaterial(ScreenHousingTextures.screenIndex("light_alloy_hull"));tile.setGlassAppearance(1,false);
                need(tile.getGlassMaterial()>=0,"custom artwork fixture missing");
                PortholeRetainedGeometry.Surfaces custom=PortholeRetainedGeometry.capture(tile,state,true);
                need(!custom.single&&custom.pane.sameGeometry(PortholeRetainedGeometry.capture(tile,state,false).pane),"custom artwork changed its original mapped aperture");
                TEAnimatedScreenSelector.unloadGroups(world);cases++;
            }
        for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean inverted:new boolean[]{false,true})for(int mode=0;mode<3;mode++)for(int shape=0;shape<4;shape++){
            fixture="diagonal facing="+facing+" inverted="+inverted+" mode="+mode+" shape="+shape;
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);
            IBlockState state=diagonal.getDefaultState().withProperty(BlockProgrammableWall.FACING,facing).withProperty(BlockProgrammableWall.INVERTED,inverted);
            TileEntityAnimatedScreenSelector tile=put(world,origin,state,shape,mode);
            PortholeRetainedGeometry.Surfaces baseline=PortholeRetainedGeometry.capture(tile,state,false),optimized=PortholeRetainedGeometry.capture(tile,state,true);
            need(!optimized.single&&baseline.housing.sameGeometry(optimized.housing)&&baseline.pane.sameGeometry(optimized.pane),"projected aperture changed its clipping, depth or UVs");
            need(optimized.housing.vertexCount()>0&&optimized.pane.vertexCount()>0,"diagonal porthole lost actual geometry");
            TEAnimatedScreenSelector.unloadGroups(world);cases++;
        }
        for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int depth=0;depth<3;depth++)for(int topology=0;topology<4;topology++){
            fixture="junction facing="+facing+" depth="+depth+" topology="+topology;
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);
            IBlockState state=flat.getDefaultState().withProperty(BlockProgrammableWall.FACING,facing).withProperty(BlockProgrammableWall.DEPTH,depth);
            TileEntityAnimatedScreenSelector tile=put(world,origin,state,0,0);
            IBlockState arm=state.withProperty(BlockProgrammableWall.FACING,facing.rotateY());
            if(topology!=1)put(world,origin.offset(facing),arm,0,0);
            if(topology!=0)put(world,origin.offset(facing.getOpposite()),arm,0,0);
            if(topology>=2)put(world,origin.offset(facing.rotateYCCW()),state,0,0);
            if(topology==2)put(world,origin.offset(facing.rotateY()),state,0,0);
            need(PanelJoins.flatCorner(state,world,origin)!=null,"junction fixture did not form a corner");
            need((PanelJoins.flatBar(state,world,origin)!=null)==(topology==3),"T-bar fixture did not form its center-owned bar");
            PortholeRetainedGeometry.Surfaces baseline=PortholeRetainedGeometry.capture(tile,state,false),optimized=PortholeRetainedGeometry.capture(tile,state,true);
            need(baseline.housing.sameGeometry(optimized.housing),"retaining bent aperture changed arms, stem or cap");
            need(optimized.pane.vertexCount()>0,"retained corner/T-bar lost clipped glass");
            if(topology==3){
                BlockProgrammableWall.FlatCorner bar=PanelJoins.flatBar(state,world,origin);
                FlatPanelBarRenderer.Material stem=FlatPanelBarRenderer.material(tile,state,true),cross=FlatPanelBarRenderer.barMaterial(tile,state,bar.frontArm,true);
                StaticSurfaceMesh stemPane=FlatPanelBarRenderer.part(stem.transparent,0,0,bar.frontArm,stem.depth,stem.left,stem.width);
                StaticSurfaceMesh crossPane=FlatPanelBarRenderer.part(cross.transparent,3,5,bar.frontArm,cross.depth,cross.left,cross.width);
                long[] signature=optimized.pane.portholePlanes(optimized.rotation,origin);
                if(signature!=null)System.out.println("Porthole T clipping "+fixture+" stemVertices="+stemPane.vertexCount()+" barVertices="+crossPane.vertexCount()+" planes="+Arrays.toString(signature));
                need(signature==null||stemPane.vertexCount()==0||crossPane.vertexCount()==0,"nonparallel surviving T-bar panes shared a plane signature");
            }
            TEAnimatedScreenSelector.unloadGroups(world);cases++;
        }
        DiagonalWallMeshCache.clear();
        System.out.println("PortholeRetainedGeometry PASS cases="+cases+" checks="+checks+" actual flat/full/diagonal/bent/T-bar aperture, depth/facing transforms, clipped center-sheet glimmer UV, custom/opaque thickness and noncoplanar boundaries");
    }
    public static void main(String[] args)throws Exception{GlassPanePipelineChecks.initialize();run();}
    private PortholeRetainedGeometryChecks(){}
}
