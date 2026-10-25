package com.vandorlabs.client;

import com.google.gson.*;
import com.google.gson.stream.JsonWriter;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.util.vector.Matrix4f;

/** Test-only independent a7 capture: intercept actual private TESR draws and actual GL transforms. */
public final class LegacyWallModelGoldens {
    private static final Gson GSON=new Gson();
    private static final List<Case> CASES=new ArrayList<>();
    private static int index,wait;
    private static BlockPos base;
    private static JsonWriter writer;
    private static Field uploader;
    private static Method renderer;
    private static final Map<BlockPos,IBlockState> ORIGINAL=new LinkedHashMap<>();
    private static final Map<BlockPos,NBTTagCompound> ORIGINAL_TILES=new HashMap<>();
    private static final Set<BlockPos> ACTIVE=new LinkedHashSet<>();
    private static final int[][] STYLES={{0,-1,0,-1,1,1},{150,2,1,150,0,0},{26,-1,2,0,2,2}};
    private static final class Case {final int family,facing,depth,shape,style;final String topology;Case(int family,int facing,int depth,int shape,int style,String topology){this.family=family;this.facing=facing;this.depth=depth;this.shape=shape;this.style=style;this.topology=topology;}String key(){return family+"/"+facing+"/"+depth+"/"+shape+"/"+style+"/"+topology;}}
    private static final class Candidate {final TextureAtlasSprite sprite;final String material;Candidate(TextureAtlasSprite sprite,String material){this.sprite=sprite;this.material=material;}}
    private LegacyWallModelGoldens(){}
    private static void need(boolean value,String message){if(!value)throw new IllegalStateException("Legacy wall reference: "+message);}
    public static boolean tick(Minecraft mc,Path output)throws Exception{
        if(mc.world==null||mc.player==null)return false;if(wait++<40)return false;
        if(writer==null){
            base=mc.player.getPosition().add(8,4,8);for(int x=-3;x<=3;x++)for(int y=-1;y<=3;y++)for(int z=-3;z<=3;z++){BlockPos p=base.add(x,y,z);need(mc.world.isBlockLoaded(p),"fixture must use loaded actual client chunks");ORIGINAL.put(p,mc.world.getBlockState(p));TileEntity tile=mc.world.getTileEntity(p);if(tile!=null)ORIGINAL_TILES.put(p,tile.writeToNBT(new NBTTagCompound()));}
            for(BlockPos p:ORIGINAL.keySet())mc.world.setBlockState(p,net.minecraft.init.Blocks.AIR.getDefaultState(),2);
            for(Field f:Tessellator.class.getDeclaredFields())if(WorldVertexBufferUploader.class.isAssignableFrom(f.getType())){uploader=f;f.setAccessible(true);break;}need(uploader!=null,"actual Tessellator uploader field");
            renderer=TEAnimatedScreenSelector.class.getDeclaredMethod("renderProgrammableWall",TileEntityAnimatedScreenSelector.class,IBlockState.class,double.class,double.class,double.class);renderer.setAccessible(true);
            for(int family=0;family<3;family++)for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int depth=0;depth<3;depth++)for(int shape=0;shape<(family==0?1:4);shape++)for(int style=0;style<3;style++)CASES.add(new Case(family,facing.getIndex(),depth,shape,style,"single"));
            for(int family=0;family<3;family++)for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int shape=0;shape<(family==0?1:4);shape++)for(int style=0;style<3;style++)for(String topology:family==0?new String[]{"L","T","cross"}:family==1?new String[]{"joined","hole","L","T","cross"}:new String[]{"joined","hole"})CASES.add(new Case(family,facing.getIndex(),0,shape,style,topology));
            Files.createDirectories(output);writer=new JsonWriter(Files.newBufferedWriter(output.resolve("legacy-wall-emission.json"),StandardCharsets.UTF_8));writer.beginObject();writer.name("format").value(1);writer.name("reference").value("actual-a7-private-TESR-real-atlas-world-and-GL-matrices");writer.name("cases").beginArray();
        }
        try{for(int n=0;n<4&&index<CASES.size();n++,index++)capture(mc,CASES.get(index));writer.flush();}
        catch(Throwable failure){restore(mc);try{writer.close();}catch(Exception ignored){}throw new IllegalStateException("Legacy wall reference case "+CASES.get(index).key(),failure);}
        if(index<CASES.size())return false;writer.endArray();writer.endObject();writer.close();restore(mc);System.out.println("[vandorlabs][reprolab] wall-model-goldens PASS "+index+" actual TESR material/pose/UV/color/light/depth/cull cases; original fixture/uploader/pass restored");return true;
    }
    private static void remember(Minecraft mc,BlockPos pos){need(ORIGINAL.containsKey(pos),"fixture footprint must remain bounded");ACTIVE.add(pos);}
    private static TileEntityAnimatedScreenSelector place(Minecraft mc,BlockPos pos,IBlockState state,Case c,boolean owner){
        remember(mc,pos);mc.world.setBlockState(pos,state,2);TileEntity raw=mc.world.getTileEntity(pos);need(raw instanceof TileEntityAnimatedScreenSelector,"real wall tile factory");TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)raw;int[] style=STYLES[c.style];tile.setHousingTexture(owner?style[0]:1);tile.setSideTexture(owner?style[1]:2);tile.setMainTextureLayout(style[2]);tile.setPortholeShape(c.shape);tile.setJoinPortholes(true);tile.setGlassMaterial(style[3]);tile.setGlassAppearance(style[4],true);tile.setGlassTextureLayout(style[5]);return tile;
    }
    private static void capture(Minecraft mc,Case c)throws Exception{
        for(BlockPos p:ACTIVE)mc.world.setBlockState(p,net.minecraft.init.Blocks.AIR.getDefaultState(),2);ACTIVE.clear();
        Block block=c.family==0?ModBlocks.PROGRAMMABLE_WALL:c.family==1?ModBlocks.PROGRAMMABLE_PORTHOLE_WALL:ModBlocks.PROGRAMMABLE_PORTHOLE_BLOCK;EnumFacing facing=EnumFacing.getFront(c.facing),right=facing.rotateY();IBlockState state=block.getDefaultState().withProperty(BlockProgrammableWall.FACING,facing).withProperty(BlockProgrammableWall.DEPTH,c.depth);
        TileEntityAnimatedScreenSelector tile=place(mc,base,state,c,true);
        if(c.topology.equals("joined")||c.topology.equals("hole")){int size=c.topology.equals("hole")?3:2;for(int y=0;y<size;y++)for(int x=0;x<size;x++)if((x!=0||y!=0)&&(!c.topology.equals("hole")||x!=1||y!=1))place(mc,base.offset(right,x).up(y),state,c,true);}
        else if(!c.topology.equals("single")){place(mc,base.offset(facing),state.withProperty(BlockProgrammableWall.FACING,right),c,false);if(!c.topology.equals("L"))place(mc,base.offset(facing.getOpposite()),state.withProperty(BlockProgrammableWall.FACING,right),c,false);if(!c.topology.equals("L"))place(mc,base.offset(right),state,c,false);if(c.topology.equals("cross"))place(mc,base.offset(right.getOpposite()),state,c,false);}
        Candidate[] candidates={new Candidate(TEAnimatedScreenSelector.wallSprite(tile),Integer.toString(tile.getHousingTexture())),new Candidate(mc.getTextureMapBlocks().getAtlasSprite(block instanceof BlockProgrammableWall&&((BlockProgrammableWall)block).isPortholeShape()&&tile.getSideTexture()>=0?ScreenHousingTextures.texture(tile.getSideTexture()):"vandorlabs:blocks/programmable_glass/metal_side"),Integer.toString(tile.getSideTexture()>=0?tile.getSideTexture():26)),new Candidate(PortholePane.sprite(tile),tile.getGlassMaterial()>=0?Integer.toString(tile.getGlassMaterial()):"stock:"+tile.getGlassShade()+":"+tile.getGlassTint())};
        if(STYLES[c.style][0]==150)need(Math.abs(UnifiedTextureSprites.aspect(candidates[0].sprite)-1)>.01,"choice150 must be actual rectangular artwork");
        JsonObject row=new JsonObject();row.addProperty("key",c.key());row.addProperty("family",c.family);row.addProperty("facing",c.facing);row.addProperty("depth",c.depth);row.addProperty("shape",c.shape);row.addProperty("style",c.style);row.addProperty("topology",c.topology);row.addProperty("ownLight",mc.world.getCombinedLight(base,0));JsonArray materials=new JsonArray();for(Candidate candidate:candidates){JsonObject material=new JsonObject();material.addProperty("choice",candidate.material);material.addProperty("sprite",candidate.sprite.getIconName());material.addProperty("aspect",UnifiedTextureSprites.aspect(candidate.sprite));materials.add(material);}row.add("materials",materials);
        Tessellator tess=Tessellator.getInstance();Object originalUploader=uploader.get(tess);Recorder record=new Recorder(candidates);int oldPass=MinecraftForgeClient.getRenderPass();boolean oldCull=GL11.glIsEnabled(GL11.GL_CULL_FACE),oldLighting=GL11.glIsEnabled(GL11.GL_LIGHTING),oldBlend=GL11.glIsEnabled(GL11.GL_BLEND),oldDepth=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);int oldMode=GL11.glGetInteger(GL11.GL_MATRIX_MODE);GlStateManager.matrixMode(GL11.GL_MODELVIEW);int oldStack=GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH);Matrix4f oldMatrix=matrix();int oldActive=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);int oldTexture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),oldAlphaFunc=GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC),oldShade=GL11.glGetInteger(GL11.GL_SHADE_MODEL);float oldAlphaRef=GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);boolean oldAlpha=GL11.glIsEnabled(GL11.GL_ALPHA_TEST);int oldSrc=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),oldDst=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),oldSrcA=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),oldDstA=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);FloatBuffer oldColor=BufferUtils.createFloatBuffer(16);GL11.glGetFloat(GL11.GL_CURRENT_COLOR,oldColor);float oldX=OpenGlHelper.lastBrightnessX,oldY=OpenGlHelper.lastBrightnessY;
        try{uploader.set(tess,record);for(int pass=0;pass<2;pass++){record.pass=pass;record.inverse=Matrix4f.invert(matrix(),null);ForgeHooksClient.setRenderPass(pass);renderer.invoke(new TEAnimatedScreenSelector(),tile,state,0D,0D,0D);}}
        finally{uploader.set(tess,originalUploader);ForgeHooksClient.setRenderPass(oldPass);if(oldCull)GlStateManager.enableCull();else GlStateManager.disableCull();if(oldLighting)GlStateManager.enableLighting();else GlStateManager.disableLighting();if(oldBlend)GlStateManager.enableBlend();else GlStateManager.disableBlend();GlStateManager.depthMask(oldDepth);GlStateManager.bindTexture(oldTexture);GlStateManager.setActiveTexture(oldActive);OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,oldX,oldY);GlStateManager.alphaFunc(oldAlphaFunc,oldAlphaRef);if(oldAlpha)GlStateManager.enableAlpha();else GlStateManager.disableAlpha();GlStateManager.tryBlendFuncSeparate(oldSrc,oldDst,oldSrcA,oldDstA);GlStateManager.shadeModel(oldShade);GlStateManager.color(oldColor.get(0),oldColor.get(1),oldColor.get(2),oldColor.get(3));GlStateManager.matrixMode(GL11.GL_MODELVIEW);while(GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH)>oldStack)GlStateManager.popMatrix();need(GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH)==oldStack,"renderer must preserve matrix stack depth");FloatBuffer restored=BufferUtils.createFloatBuffer(16);oldMatrix.store(restored);restored.flip();GL11.glLoadMatrix(restored);GlStateManager.matrixMode(oldMode);}
        need(record.batches.size()>0,"interceptor must receive actual uploader draw calls");if(c.family==0&&c.facing==2&&c.depth==0&&c.style==0&&c.topology.equals("single")){need(record.totalVertices==24,"plain baseline must contain six actual source quads");}
        row.add("batches",record.batches);GSON.toJson(row,writer);
    }
    private static Matrix4f matrix(){FloatBuffer data=BufferUtils.createFloatBuffer(16);GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,data);return (Matrix4f)new Matrix4f().load(data);}
    private static JsonArray matrix(Matrix4f value){FloatBuffer data=BufferUtils.createFloatBuffer(16);value.store(data);data.flip();JsonArray result=new JsonArray();while(data.hasRemaining())result.add(data.get());return result;}
    private static final class Recorder extends WorldVertexBufferUploader {
        final Candidate[] candidates;final JsonArray batches=new JsonArray();int pass,totalVertices;Matrix4f inverse;
        Recorder(Candidate[] candidates){this.candidates=candidates;}
        @Override public void draw(BufferBuilder buffer){
            VertexFormat format=buffer.getVertexFormat();ByteBuffer data=buffer.getByteBuffer().duplicate().order(ByteOrder.nativeOrder());int stride=format.getNextOffset(),position=-1,uv=-1,light=-1,color=-1,normal=-1;for(int i=0;i<format.getElementCount();i++){VertexFormatElement element=format.getElement(i);switch(element.getUsage()){case POSITION:position=format.getOffset(i);break;case COLOR:color=format.getOffset(i);break;case UV:if(element.getIndex()==0)uv=format.getOffset(i);else if(element.getIndex()==1)light=format.getOffset(i);break;case NORMAL:normal=format.getOffset(i);break;default:break;}}
            need(position>=0&&uv>=0&&light>=0&&color>=0&&normal>=0,"actual complete wall vertex format");JsonObject batch=new JsonObject();batch.addProperty("pass",pass);batch.addProperty("depthWrite",GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK));batch.addProperty("cull",GL11.glIsEnabled(GL11.GL_CULL_FACE));batch.add("pose",matrix(Matrix4f.mul(inverse,matrix(),null)));JsonArray quads=new JsonArray();
            for(int q=0;q<buffer.getVertexCount();q+=4){Candidate selected=null;for(Candidate candidate:candidates){boolean inside=true;for(int v=0;v<4;v++){int at=(q+v)*stride;float u=data.getFloat(at+uv),vv=data.getFloat(at+uv+4);inside&=u>=candidate.sprite.getMinU()-2E-7&&u<=candidate.sprite.getMaxU()+2E-7&&vv>=candidate.sprite.getMinV()-2E-7&&vv<=candidate.sprite.getMaxV()+2E-7;}if(inside){selected=candidate;break;}}need(selected!=null,"every actual quad must belong to a selected real atlas sprite");JsonObject quad=new JsonObject();quad.addProperty("material",selected.material);JsonArray vertices=new JsonArray();for(int v=0;v<4;v++){int at=(q+v)*stride;JsonArray vertex=new JsonArray();for(int axis=0;axis<3;axis++)vertex.add(data.getFloat(at+position+axis*4));vertex.add((data.getFloat(at+uv)-selected.sprite.getMinU())/(selected.sprite.getMaxU()-selected.sprite.getMinU()));vertex.add((data.getFloat(at+uv+4)-selected.sprite.getMinV())/(selected.sprite.getMaxV()-selected.sprite.getMinV()));for(int axis=0;axis<3;axis++)vertex.add(data.get(at+normal+axis)/127F);for(int axis=0;axis<4;axis++)vertex.add(data.get(at+color+axis)&255);vertex.add(data.getShort(at+light)&65535);vertex.add(data.getShort(at+light+2)&65535);vertices.add(vertex);}quad.add("vertices",vertices);quads.add(quad);}batch.add("quads",quads);batches.add(batch);totalVertices+=buffer.getVertexCount();buffer.reset();
        }
    }
    private static void restore(Minecraft mc){if(mc.world==null)return;for(Map.Entry<BlockPos,IBlockState> entry:ORIGINAL.entrySet()){mc.world.setBlockState(entry.getKey(),entry.getValue(),2);NBTTagCompound tag=ORIGINAL_TILES.get(entry.getKey());TileEntity tile=mc.world.getTileEntity(entry.getKey());if(tag!=null&&tile!=null)tile.readFromNBT(tag);}ACTIVE.clear();}
}
