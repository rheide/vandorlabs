package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.*;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Test-only companion: released whole renderer, actual GL matrices/resources, CPU-only draw submission. */
@Mod(modid="vandorlabs_screen_oracle",name="Screen renderer oracle",version="1",clientSideOnly=true,dependencies="required-after:vandorlabs")
public final class ScreenRoleGLOracle {
 private int ticks,readyTicks;private boolean launched,done;
 @Mod.EventHandler public void init(FMLInitializationEvent event){MinecraftForge.EVENT_BUS.register(this);}
 @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
  if(event.phase!=TickEvent.Phase.END||done)return;Minecraft mc=Minecraft.getMinecraft();mc.gameSettings.pauseOnLostFocus=false;
  if(++ticks>1800)throw new IllegalStateException("Screen GL oracle timeout world="+mc.world+" stage="+(launched?1:0));
  if(mc.world==null){if(!launched&&ticks>40&&mc.getIntegratedServer()==null){launched=true;mc.launchIntegratedServer("screen-render-oracle","Screen renderer oracle",new WorldSettings(719234L,GameType.CREATIVE,false,false,WorldType.FLAT));mc.setIngameFocus();}return;}
  if(mc.player==null||mc.world.getChunkProvider().getLoadedChunk(mc.player.chunkCoordX,mc.player.chunkCoordZ)==null)return;
  if(++readyTicks<40)return;
  done=true;try{if(Boolean.getBoolean("vandorlabs.screenJoinedJournalOracle")){ScreenJoinedJournalGLOracle.capture(mc,new File(mc.mcDataDir,"screen-joined-journal-gl.jsonl"));}else if(Boolean.getBoolean("vandorlabs.screenChipTrayOracle")){ScreenChipTrayGLOracle.capture(mc,new File(mc.mcDataDir,"screen-chip-tray-gl.jsonl"));}else if(Boolean.getBoolean("vandorlabs.screenJournalOracle")){ScreenJournalGLOracle.capture(mc,new File(mc.mcDataDir,"screen-journal-gl.jsonl"));System.out.println("[vandorlabs][screen-journal-source] PASS actual released non-Vehicle journal canvas and font fitting");}else if(Boolean.getBoolean("vandorlabs.screenRowsOracle")){ScreenRowsGLOracle.capture(mc,new File(mc.mcDataDir,"screen-rows-gl.jsonl"));System.out.println("[vandorlabs][screen-rows-source] PASS actual whole-role row/text commands and GL matrices");}else{capture(mc,new File(mc.mcDataDir,"screen-role-gl.jsonl"));System.out.println("[vandorlabs][screen-role-gl] PASS actual released render, GL matrices, texture binding and getter order");}mc.shutdown();}catch(Exception failure){throw new IllegalStateException("Screen GL oracle",failure);}
 }
 public static final class Spy extends TileEntityAnimatedScreenSelector {
  boolean recording;int depth,mask;final List<Integer> order=new ArrayList<>();
  void read(int bit){if(recording){order.add(bit);if(depth==0)mask|=1<<bit;}}
  int integer(int bit,java.util.function.IntSupplier call){read(bit);depth++;try{return call.getAsInt();}finally{depth--;}}
  boolean bool(int bit,java.util.function.BooleanSupplier call){read(bit);depth++;try{return call.getAsBoolean();}finally{depth--;}}
  @Override public int getSignalLevel(){return integer(0,super::getSignalLevel);}
  @Override public int activationState(){return integer(1,super::activationState);}
  @Override public int getEffectiveMode(){return integer(2,super::getEffectiveMode);}
  @Override public int getSurfaceTexture(int slot){return integer(slot==0?3:4,()->super.getSurfaceTexture(slot));}
  @Override public int effectiveMode(int slot){return integer(slot==0?5:6,()->super.effectiveMode(slot));}
  @Override public int effectiveSpeed(int slot){return integer(slot==0?7:8,()->super.effectiveSpeed(slot));}
  @Override public boolean activationOverridesArtwork(){return bool(9,super::activationOverridesArtwork);}
  @Override public boolean splitDisplay(){return bool(10,super::splitDisplay);}
  @Override public int primaryArtworkTexture(){return integer(11,super::primaryArtworkTexture);}
  @Override public int getAnimationSpeedIndex(){return integer(12,super::getAnimationSpeedIndex);}
 }
 private static final class Capture extends BufferBuilder {
  int mode;final FloatBuffer matrix=BufferUtils.createFloatBuffer(16);final List<JsonObject> draws=new ArrayList<>();
  Capture(){super(1<<18);}
  @Override public void begin(int mode,net.minecraft.client.renderer.vertex.VertexFormat format){this.mode=mode;matrix.clear();GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,matrix);super.begin(mode,format);}
  void finish(){finishDrawing();JsonObject draw=new JsonObject();draw.addProperty("mode",mode);draw.addProperty("vertexCount",getVertexCount());draw.addProperty("stride",getVertexFormat().getNextOffset());JsonArray transform=new JsonArray();for(int i=0;i<16;i++)transform.add(Float.floatToRawIntBits(matrix.get(i)));draw.add("modelview",transform);draw.addProperty("texture",texture());JsonArray state=new JsonArray();for(int flag:new int[]{GL11.GL_CULL_FACE,GL11.GL_BLEND,GL11.GL_DEPTH_WRITEMASK,GL11.GL_ALPHA_TEST,GL11.GL_POLYGON_OFFSET_FILL})state.add(GL11.glGetBoolean(flag));for(int parameter:new int[]{GL11.GL_POLYGON_OFFSET_FACTOR,GL11.GL_POLYGON_OFFSET_UNITS,GL11.GL_ALPHA_TEST_REF})state.add(Float.floatToRawIntBits(GL11.glGetFloat(parameter)));draw.add("state",state);JsonArray data=new JsonArray();for(int i=0;i<getVertexCount()*getVertexFormat().getNextOffset();i+=4)data.add(getByteBuffer().getInt(i));draw.add("vertices",data);draws.add(draw);}
 }
 private static String texture(){
  int id=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);TextureManager manager=Minecraft.getMinecraft().getTextureManager();
  try{Field field=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(TextureManager.class,"mapTextureObjects","field_110585_a");for(Map.Entry<?,?> entry:((Map<?,?>)field.get(manager)).entrySet())if(((ITextureObject)entry.getValue()).getGlTextureId()==id)return entry.getKey().toString().replaceFirst("_[0-9]+$","");}catch(IllegalAccessException failure){throw new IllegalStateException(failure);}return "unbound:"+id;
 }
 private static final class CpuTessellator extends Tessellator {final Capture capture;CpuTessellator(Capture capture){super(16);this.capture=capture;}@Override public BufferBuilder getBuffer(){return capture;}@Override public void draw(){capture.finish();}}
 private static void capture(Minecraft mc,File target)throws Exception {
  Field singleton=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(Tessellator.class,"INSTANCE","field_78398_a");Field modifiers=Field.class.getDeclaredField("modifiers");modifiers.setAccessible(true);int flags=singleton.getModifiers();modifiers.setInt(singleton,flags&~Modifier.FINAL);Object original=singleton.get(null);Capture buffer=new Capture();singleton.set(null,new CpuTessellator(buffer));
  BlockPos pos=new BlockPos(mc.player.posX,240,mc.player.posZ);IBlockState previous=mc.world.getBlockState(pos);net.minecraft.tileentity.TileEntity previousTile=mc.world.getTileEntity(pos);long time=mc.world.getTotalWorldTime();int profiles=0,draws=0;Gson gson=new Gson();int front=ScreenHousingTextures.screenIndex("frames/frames-door-window/lights-cyan-1");int custom=ScreenHousingTextures.screenIndex("console_inputs/console_controls_biology_controls_static");if(front<0||custom<0)throw new AssertionError("Source frame/input material missing");
  GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPushMatrix();
  try(PrintWriter out=new PrintWriter(target,"UTF-8")){
   for(com.vandorlabs.screen.ScreenFamily family:com.vandorlabs.screen.ScreenFamily.values()){String id=family.id;BlockAnimatedScreenSelector block=null;for(net.minecraft.block.Block candidate:ModBlocks.BLOCKS)if(candidate.getRegistryName().getResourcePath().equals(id))block=(BlockAnimatedScreenSelector)candidate;if(block==null)throw new AssertionError(id);
    for(IBlockState state:((net.minecraft.block.Block)block).getBlockState().getValidStates())for(int mode=0;mode<3;mode++)for(int variant=0;variant<3;variant++)for(boolean framed:new boolean[]{false,true}){
     mc.world.setBlockState(pos,state,2);NBTTagCompound tag=((net.minecraft.block.BlockContainer)block).createNewTileEntity(null,0).writeToNBT(new NBTTagCompound());tag.setInteger("displayMode",mode);tag.setBoolean("smallInput",variant==1);tag.setInteger("wallPosition",variant==1?2:0);tag.setBoolean("CeilingMounted",variant==2);tag.setInteger("CeilingPosition",1);tag.setBoolean("FrameTile",true);tag.setInteger("FrameFront",framed?front:-1);tag.setInteger("SurfaceTextureLayout",variant);if(variant==1){tag.setInteger("housingTexture",com.vandorlabs.tiles.HousingFinishCatalog.legacyCount()-1);tag.setInteger("PrimarySurfaceTexture",custom);tag.setInteger("SecondarySurfaceTexture",custom);tag.setBoolean("DisplaySplit",true);tag.setBoolean("DisplayFit",true);tag.setInteger("DisplaySplitTop",custom);}if(variant==2){NBTTagCompound activation=new NBTTagCompound();activation.setInteger("Mode",2);activation.setIntArray("Textures",new int[]{-1,-1,-1,-1});activation.setIntArray("Playback",new int[]{0,1,2,mode});activation.setIntArray("Speeds",Boolean.getBoolean("vandorlabs.screenOracleValidActivation")?new int[]{0,1,2,1}:new int[]{0,1,2,3});tag.setTag("DisplayActivation",activation);tag.setInteger("ChannelLevel",15);}Spy tile=new Spy();((net.minecraft.tileentity.TileEntity)tile).readFromNBT(tag);((net.minecraft.tileentity.TileEntity)tile).setWorld(mc.world);((net.minecraft.tileentity.TileEntity)tile).setPos(pos);mc.world.setTileEntity(pos,tile);if(((net.minecraft.tileentity.TileEntity)tile).isInvalid()||mc.world.getTileEntity(pos)!=tile||mc.world.getBlockState(pos)!=state)throw new AssertionError("Source oracle owner was not installed "+id);mc.world.setTotalWorldTime(112);buffer.draws.clear();GL11.glLoadIdentity();tile.recording=true;net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher.instance.getRenderer(tile).render(tile,0,0,0,0,-1,1);tile.recording=false;boolean artwork=false;for(JsonObject draw:buffer.draws)artwork|=draw.get("vertexCount").getAsInt()==4;int bodyVertices=(family==com.vandorlabs.screen.ScreenFamily.DIAGONAL||family==com.vandorlabs.screen.ScreenFamily.HALF_DIAGONAL)?com.vandorlabs.render.ScreenHousingMesh.diagonal(false).quads.length*4:24;if(buffer.draws.size()<2||!artwork||buffer.draws.get(0).get("vertexCount").getAsInt()!=bodyVertices)throw new AssertionError("Missing released draws role="+id+" meta="+((net.minecraft.block.Block)block).getMetaFromState(state)+" mode="+mode+" variant="+variant+" frame="+framed+" draws="+buffer.draws.size()+" invalid="+((net.minecraft.tileentity.TileEntity)tile).isInvalid());
     JsonObject row=new JsonObject();row.addProperty("id",id);row.addProperty("meta",((net.minecraft.block.Block)block).getMetaFromState(state));row.addProperty("mode",mode);row.addProperty("variant",variant);row.addProperty("frame",framed);row.add("getters",gson.toJsonTree(tile.order));int mask=0;for(int bit:tile.order)mask|=1<<bit;row.addProperty("nestedGetterMask",mask);row.addProperty("getterMask",tile.mask);row.add("draws",gson.toJsonTree(buffer.draws));out.println(gson.toJson(row));profiles++;draws+=buffer.draws.size();
    }
   }
  }finally{mc.world.setTotalWorldTime(time);mc.world.setBlockState(pos,previous,2);if(previousTile!=null)mc.world.setTileEntity(pos,previousTile);GL11.glPopMatrix();GL11.glPopAttrib();singleton.set(null,original);modifiers.setInt(singleton,flags);}
  if(target.length()>32*1024*1024)throw new AssertionError("Source role capture exceeded byte budget "+target.length());System.out.println("[vandorlabs][screen-role-gl] profiles="+profiles+" draws="+draws+" bytes="+target.length()+" renderer="+GL11.glGetString(GL11.GL_RENDERER));
 }
}
