package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.model.pipeline.ForgeBlockModelRenderer;
import net.minecraftforge.common.property.IExtendedBlockState;
import java.io.*;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.*;

/** Compare chunk records against the released frame meshes before any world or GL transform. */
final class GlassFrameGeometryChecks {
    private static final BlockPos POS=new BlockPos(8,80,8);
    private static String fixture;
    private static int cases,vertices;
    static void run(Minecraft mc,File output)throws Exception{
        cases=vertices=0;
        BlockProgrammableGlass block=(BlockProgrammableGlass)Block.getBlockFromName("vandorlabs:programmable_glass");
        int plain=choice("frames/frames-screen-full/frame_plain_1254"),light=choice("frames/frames-door-window/lights-cyan-1");
        int interior=choice("light_alloy_hull"),exterior=choice("dark_wall_panel");
        int[] fronts={-1,plain,light};
        for(boolean rotated:new boolean[]{false,true})for(int depth=0;depth<3;depth++)for(int front:fronts)for(int shape=0;shape<6;shape++){
            EnumFacing facing=rotated?EnumFacing.EAST:EnumFacing.NORTH;
            fixture="flat rotated="+rotated+" depth="+depth+" front="+front+" contour="+shape;
            List<VehicleStructure.Cell> cells=new ArrayList<>();IBlockState state=state(block,facing,depth);
            add(cells,state,POS,facing,front,interior,exterior);
            EnumFacing right=facing.rotateY();
            if(shape==1||shape==3)add(cells,state,POS.offset(right),facing,front,interior,exterior);
            if(shape==2||shape==3)add(cells,state,POS.up(),facing,front,interior,exterior);
            if(shape>=4)for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)if((x!=0||y!=0)&&!(shape==5&&x==1&&y==1))add(cells,state,POS.offset(right,x).up(y),facing,front,interior,exterior);
            VehicleWorld world=new VehicleWorld(mc.world,VehicleStructure.display(cells));
            try{
                TileEntityProgrammableGlass tile=(TileEntityProgrammableGlass)world.getTileEntity(POS);
                int[] masks={15,13,7,133,0,128};require(GlassSurfaceGroups.frameMask(tile)==masks[shape],"local contour mask");
                GlassFrameModel.Mesh mesh=compare(tile,state,DefaultVertexFormats.BLOCK);
                require(!GlassFrameChunks.supported(tile,state)&&!GlassFrameChunks.rendered(tile),"carried frame entered native publication");
                if(shape==0){
                    if(front==plain)require(!mesh.translucent.isEmpty(),"authored partial alpha lost blending");
                    if(front<0){
                        // The stock front PNG has antialiased alpha (1..254); only the
                        // selected alloy and wall-panel side materials are fully opaque.
                        require(!mesh.translucent.isEmpty()&&!mesh.cutout.isEmpty(),"stock front/custom side alpha partition missing");
                        TextureAtlasSprite rail=mc.getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/space_doors/medium/double_frame_metal");
                        for(BakedQuad quad:mesh.translucent)require(quad.getSprite()==rail,"opaque side material moved into blending");
                        for(BakedQuad quad:mesh.cutout)require(quad.getSprite()!=rail,"antialiased stock front lost blending");
                    }
                    lightAndLayers(mc,block,world,tile,state,mesh);
                    if(!rotated&&depth==0&&front==plain)formats(tile,state,mesh);
                }
            }finally{GlassSurfaceGroups.unload(world);}
        }
        for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int depth=0;depth<3;depth++)for(int shape=0;shape<4;shape++)for(int front:new int[]{-1,plain,light}){
            fixture="junction facing="+facing+" depth="+depth+" topology="+shape+" front="+front;
            List<VehicleStructure.Cell> cells=new ArrayList<>();IBlockState state=state(block,facing,depth);
            add(cells,state,POS,facing,front,front<0?-1:interior,front<0?-1:exterior);
            EnumFacing arm=facing.rotateY();IBlockState armState=state(block,arm,depth);
            if(shape!=1)add(cells,armState,POS.offset(facing),arm,front,interior,exterior);
            if(shape!=0)add(cells,armState,POS.offset(facing.getOpposite()),arm,front,interior,exterior);
            if(shape>=2)add(cells,state,POS.offset(facing.rotateYCCW()),facing,front,interior,exterior);
            if(shape==2)add(cells,state,POS.offset(facing.rotateY()),facing,front,interior,exterior);
            VehicleWorld world=new VehicleWorld(mc.world,VehicleStructure.display(cells));
            try{
                TileEntityProgrammableGlass tile=(TileEntityProgrammableGlass)world.getTileEntity(POS);
                require(PanelJoins.flatCorner(state,world,POS)!=null,"missing junction fixture");
                require((PanelJoins.flatBar(state,world,POS)!=null)==(shape==3),"T-bar fixture shape");
                GlassFrameModel.Mesh mesh=compare(tile,state,DefaultVertexFormats.BLOCK);
                require(mesh.translucent.isEmpty(),"released opaque corner/bar changed blend policy");
                lightAndLayers(mc,block,world,tile,state,mesh);
            }finally{GlassSurfaceGroups.unload(world);}
        }
        String result="glass-frame-geometry PASS cases="+cases+" vertices="+vertices+" positions UVs normals sprites two-sided flat contours corners cross T-bar depth facing alpha layers uniform/emissive light immutable formats carried fallback";
        try(PrintWriter report=new PrintWriter(new File(output,"glass-frame-geometry.txt"),"UTF-8")){report.println(result);}
        System.out.println("[vandorlabs][reprolab] "+result);
    }
    private static int choice(String name){int value=ScreenHousingTextures.screenIndex(name);require(value>=0&&ScreenHousingTextures.texture(value).equals("vandorlabs:blocks/"+name),"missing catalog fixture "+name);return value;}
    private static IBlockState state(BlockProgrammableGlass block,EnumFacing facing,int depth){return block.getDefaultState().withProperty(BlockGlassWall.ROTATED,facing.getAxis()==EnumFacing.Axis.X).withProperty(BlockGlassWall.DEPTH,depth);}
    private static void add(List<VehicleStructure.Cell> cells,IBlockState state,BlockPos pos,EnumFacing facing,int front,int interior,int exterior){
        TileEntityProgrammableGlass tile=new TileEntityProgrammableGlass();tile.setPos(pos);tile.setPanelFacing(facing);tile.frameAppearance.front=front;tile.frameAppearance.interior=interior;tile.frameAppearance.exterior=exterior;
        cells.add(new VehicleStructure.Cell(pos,state,tile.writeToNBT(new NBTTagCompound())));
    }
    private static GlassFrameModel.Mesh compare(TileEntityProgrammableGlass tile,IBlockState state,VertexFormat format){
        GlassFrameModel.Mesh mesh=GlassFrameModel.build(tile,state,format);Recorder source=new Recorder();
        BlockProgrammableWall.FlatCorner corner=PanelJoins.flatCorner(state,tile.getWorld(),tile.getPos()),bar=PanelJoins.flatBar(state,tile.getWorld(),tile.getPos());
        double angle,depth;
        if(corner==null){GlassFrameMesh.get(tile).draw(source,0,0);angle=state.getValue(BlockGlassWall.ROTATED)?-90:0;depth=PanelDepth.offset(state.getValue(BlockGlassWall.DEPTH));}
        else{
            angle=180-PanelJoins.flatFacing(state,tile.getWorld(),tile.getPos()).getHorizontalAngle();depth=0;
            if(bar==null)TEProgrammableGlass.meshes(tile,state,corner).frame.draw(source,0,0);
            else{
                FlatPanelBarRenderer.Material stem=FlatPanelBarRenderer.material(tile,state),cross=FlatPanelBarRenderer.barMaterial(tile,state,bar.frontArm);
                FlatPanelBarRenderer.part(stem.opaque,0,0,bar.frontArm,stem.depth,stem.left,stem.width,.02).draw(source,0,0);
                FlatPanelBarRenderer.part(cross.opaque,3,5,bar.frontArm,cross.depth,cross.left,cross.width,.02).draw(source,0,0);
            }
        }
        require(mesh.quads.size()*2==source.points.size(),"two-sided vertex count actual="+mesh.quads.size()*4+" source="+source.points.size());
        require(mesh.format!=format&&mesh.format.equals(format),"format was not snapshotted");
        Set<TextureAtlasSprite> sprites=expectedSprites(tile);double sine=Math.sin(Math.toRadians(angle)),cosine=Math.cos(Math.toRadians(angle));
        for(int q=0;q<mesh.quads.size();q+=2){
            BakedQuad quad=mesh.quads.get(q),back=mesh.quads.get(q+1);int[] data=quad.getVertexData(),reverse=back.getVertexData();
            int stride=mesh.format.getIntegerSize(),uv=mesh.format.getUvOffsetById(0)/4;
            require(data.length==stride*4&&reverse.length==data.length,"record stride mismatch");
            require(quad.getSprite()==back.getSprite()&&sprites.contains(quad.getSprite()),"authored sprite identity lost");
            require(!quad.shouldApplyDiffuseLighting()&&quad.getTintIndex()==-1,"frame color policy changed");
            for(int v=0;v<4;v++){
                float[] point=source.points.get(q*2+v);double x=point[0]/16D-.5,z=(point[2]+depth)/16D-.5;
                int at=v*stride;near(Float.intBitsToFloat(data[at]),.5+cosine*x+sine*z,"X");near(Float.intBitsToFloat(data[at+1]),point[1]/16D,"Y");near(Float.intBitsToFloat(data[at+2]),.5-sine*x+cosine*z,"Z");
                require(data[at+uv]==Float.floatToRawIntBits(point[3])&&data[at+uv+1]==Float.floatToRawIntBits(point[4]),"atlas UV changed");
                TextureAtlasSprite sprite=quad.getSprite();require(point[3]>=sprite.getMinU()-1e-6&&point[3]<=sprite.getMaxU()+1e-6&&point[4]>=sprite.getMinV()-1e-6&&point[4]<=sprite.getMaxV()+1e-6,"UV outside assigned sprite");
                double nx=cosine*point[5]+sine*point[7],nz=-sine*point[5]+cosine*point[7];
                require(quad.getFace()==EnumFacing.getFacingFromVector((float)nx,point[6],(float)nz),"face does not follow source normal");
                if(mesh.format.hasNormal()){
                    int packed=data[at+mesh.format.getNormalOffset()/4];near((byte)packed/127D,quantize(nx),"NX");near((byte)(packed>>8)/127D,quantize(point[6]),"NY");near((byte)(packed>>16)/127D,quantize(nz),"NZ");
                }
                require(data[at+mesh.format.getColorOffset()/4]==-1,"vertex color changed");
                for(int word=0;word<stride;word++)require(data[at+word]==reverse[((4-v)&3)*stride+word],"reverse winding changed attached attributes");
            }
        }
        require(mesh.cutout.size()+mesh.translucent.size()==mesh.quads.size(),"layer split lost/duplicated geometry");
        Set<BakedQuad> partition=Collections.newSetFromMap(new IdentityHashMap<>());partition.addAll(mesh.cutout);partition.addAll(mesh.translucent);require(partition.size()==mesh.quads.size(),"layer partition overlaps");
        cases++;vertices+=source.points.size();return mesh;
    }
    private static Set<TextureAtlasSprite> expectedSprites(TileEntityProgrammableGlass tile){
        Set<TextureAtlasSprite> result=Collections.newSetFromMap(new IdentityHashMap<>());
        net.minecraft.client.renderer.texture.TextureMap atlas=Minecraft.getMinecraft().getTextureMapBlocks();
        String[] names={tile.frameAppearance.rail("vandorlabs:blocks/space_doors/medium/double_frame_metal"),tile.frameAppearance.interior("vandorlabs:blocks/programmable_glass/metal_side"),tile.frameAppearance.exterior("vandorlabs:blocks/programmable_glass/metal_side"),tile.frameAppearance.front<0?"vandorlabs:blocks/space_doors/medium/double_frame_metal":ScreenHousingTextures.texture(tile.frameAppearance.front)};
        for(String name:names){TextureAtlasSprite sprite=atlas.getAtlasSprite(name);result.add(sprite);for(int side=0;side<4;side++)result.add(FrameEdgeTextures.edge(sprite,side));}return result;
    }
    private static void formats(TileEntityProgrammableGlass tile,IBlockState state,GlassFrameModel.Mesh initial){
        int width=initial.format.getIntegerSize();int[] retained=initial.quads.get(0).getVertexData().clone();
        VertexFormat mutable=VehicleVertexFormatRuntimeChecks.format(true);GlassFrameModel.Mesh extended=compare(tile,state,mutable);int extendedWidth=extended.format.getIntegerSize();
        mutable.clear();for(VertexFormatElement element:VehicleVertexFormatRuntimeChecks.format(false).getElements())mutable.addElement(element);
        require(extended.format.getIntegerSize()==extendedWidth&&extended.quads.get(0).getVertexData().length==4*extendedWidth,"mutable global format rewrote retained records");
        GlassFrameModel.Mesh restored=compare(tile,state,mutable);require(restored.format.getIntegerSize()==mutable.getIntegerSize(),"format switch retained old width");
        require(initial.format.getIntegerSize()==width&&Arrays.equals(retained,initial.quads.get(0).getVertexData()),"another format build mutated prior mesh");
    }
    private static void lightAndLayers(Minecraft mc,BlockProgrammableGlass block,VehicleWorld world,TileEntityProgrammableGlass tile,IBlockState state,GlassFrameModel.Mesh mesh){
        int emission=PanelJoins.flatCorner(state,world,POS)==null?tile.frameAppearance.light():0;
        tile.frameChunkMesh=mesh;tile.frameChunkLight=emission;
        IBlockAccess access=(IBlockAccess)Proxy.newProxyInstance(GlassFrameGeometryChecks.class.getClassLoader(),new Class[]{IBlockAccess.class},(proxy,method,args)->{
            if(method.getReturnType()==int.class&&Arrays.equals(method.getParameterTypes(),new Class[]{BlockPos.class,int.class})){BlockPos p=(BlockPos)args[0];return ((4+Math.floorMod(p.getX(),6))<<20)|(Math.max((Integer)args[1],2+(p.getZ()&3))<<4);}
            return method.invoke(world,args);
        });
        IBlockState extended=block.getExtendedState(block.getActualState(state,access,POS),access,POS);int expected=access.getCombinedLight(POS,emission);if(emission>0)expected=(expected&0xffff0000)|240;
        for(EnumFacing face:EnumFacing.VALUES)require(extended.getPackedLightmapCoords(access,POS.offset(face))==expected,"frame substituted neighbour light");
        require(((IExtendedBlockState)block.getExtendedState(state,world,POS)).getValue(BlockProgrammableGlass.FRAME_MESH)==null,"carried access exposed native mesh");
        final int[] delegated={0};IBakedModel base=new IBakedModel(){
            public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long seed){delegated[0]++;return mesh.quads;}
            public boolean isAmbientOcclusion(){return true;}public boolean isGui3d(){return true;}public boolean isBuiltInRenderer(){return false;}
            public TextureAtlasSprite getParticleTexture(){return mc.getTextureMapBlocks().getMissingSprite();}public ItemOverrideList getOverrides(){return ItemOverrideList.NONE;}
        };
        GlassFrameModel model=new GlassFrameModel(base);BlockRenderLayer previous=MinecraftForgeClient.getRenderLayer();
        try{
            ForgeHooksClient.setRenderLayer(BlockRenderLayer.SOLID);require(model.getQuads(extended,null,0)==mesh.quads&&delegated[0]==1,"stock delegate lost");
            ForgeHooksClient.setRenderLayer(BlockRenderLayer.CUTOUT_MIPPED);require(model.getQuads(extended,null,0)==mesh.cutout,"cutout routing");
            ForgeHooksClient.setRenderLayer(BlockRenderLayer.CUTOUT);require(model.getQuads(extended,null,0)==mesh.cutout,"OptiFine no-mipmap cutout routing");
            ForgeHooksClient.setRenderLayer(BlockRenderLayer.TRANSLUCENT);require(model.getQuads(extended,null,0)==mesh.translucent,"translucent routing");
            require(!model.isAmbientOcclusion(extended)&&model.isAmbientOcclusion(state),"custom/default AO policy");
            for(EnumFacing face:EnumFacing.VALUES)require(model.getQuads(extended,face,0).isEmpty(),"custom side quads duplicated");
        }finally{ForgeHooksClient.setRenderLayer(previous);}
        IBakedModel full=new IBakedModel(){
            public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long seed){return side==null?mesh.quads:Collections.emptyList();}
            public boolean isAmbientOcclusion(){return false;}public boolean isGui3d(){return true;}public boolean isBuiltInRenderer(){return false;}
            public TextureAtlasSprite getParticleTexture(){return mc.getTextureMapBlocks().getMissingSprite();}public ItemOverrideList getOverrides(){return ItemOverrideList.NONE;}
        };
        for(BlockModelRenderer renderer:new BlockModelRenderer[]{new BlockModelRenderer(mc.getBlockColors()),new ForgeBlockModelRenderer(mc.getBlockColors())}){
            BufferBuilder buffer=new BufferBuilder(1<<16);buffer.begin(7,DefaultVertexFormats.BLOCK);renderer.renderModelFlat(access,full,extended,POS,buffer,false,0);buffer.finishDrawing();
            require(buffer.getVertexCount()==mesh.quads.size()*4,"terrain renderer count");ByteBuffer bytes=buffer.getByteBuffer();VertexFormat format=buffer.getVertexFormat();
            for(int v=0;v<buffer.getVertexCount();v++)require(bytes.getInt(v*format.getNextOffset()+format.getUvOffsetById(1))==expected,"terrain renderer changed uniform/emissive light");
        }
        tile.frameChunkMesh=null;tile.frameChunkLight=0;
    }
    private static final class Recorder extends BufferBuilder {
        final List<float[]> points=new ArrayList<>();private final float[] point=new float[8];
        Recorder(){super(16);}
        @Override public BufferBuilder pos(double x,double y,double z){point[0]=(float)x;point[1]=(float)y;point[2]=(float)z;return this;}
        @Override public BufferBuilder tex(double u,double v){point[3]=(float)u;point[4]=(float)v;return this;}
        @Override public BufferBuilder normal(float x,float y,float z){point[5]=x;point[6]=y;point[7]=z;return this;}
        @Override public BufferBuilder color(int r,int g,int b,int a){return this;}
        @Override public BufferBuilder lightmap(int sky,int block){return this;}
        @Override public void endVertex(){points.add(point.clone());}
    }
    private static double quantize(double value){return (byte)(int)(Math.rint(value*127*1e6)/1e6)/127D;}
    private static void near(double actual,double expected,String field){require(Double.isFinite(actual)&&Double.isFinite(expected)&&Math.abs(actual-expected)<1e-6,field+" "+actual+" != "+expected);}
    private static void require(boolean value,String reason){if(!value)throw new IllegalStateException("Glass frame geometry "+fixture+": "+reason);}
    private GlassFrameGeometryChecks(){}
}
