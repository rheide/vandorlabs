package com.vandorlabs.client;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.lwjgl.opengl.GL11;
/** Actual native door models: independent jambs, square pixels, glazing and prepared/batched parity. */
final class DoorAppearanceRuntimeChecks {
 static final class Fixture {
  final VehicleWorld world;final TileEntitySpaceDoor tile;final IBlockState state;final int width,height;
  Fixture(Minecraft mc,boolean large,int design){this(mc,large,design,false);}
  Fixture(Minecraft mc,boolean large,int design,boolean pair){
   Block block=Block.getBlockFromName(large?"vandorlabs:large_programmable_door":"vandorlabs:programmable_door");width=large?3:pair?2:1;height=large?3:2;
   state=block.getDefaultState().withProperty(BlockVandorDoor.FACING,EnumFacing.SOUTH).withProperty(BlockVandorDoor.HALF,BlockDoor.EnumDoorHalf.LOWER).withProperty(BlockVandorDoor.HINGE,BlockDoor.EnumHingePosition.RIGHT).withProperty(BlockConnectingDetailedDoor.PAIRED,large||pair);
   List<VehicleStructure.Cell> cells=new ArrayList<>();for(int x=0;x<width;x++)for(int y=0;y<height;y++){
    TileEntitySpaceDoor t=large?new TileEntityLargeProgrammableDoor():new TileEntitySpaceDoor();t.setPos(new BlockPos(x,y,0));t.configure(design,1,true,0,true,true,false,0,false);if(large)((TileEntityLargeProgrammableDoor)t).assign(BlockPos.ORIGIN);
    cells.add(new VehicleStructure.Cell(t.getPos(),state.withProperty(BlockVandorDoor.HALF,y==0?BlockDoor.EnumDoorHalf.LOWER:BlockDoor.EnumDoorHalf.UPPER),t.writeToNBT(new NBTTagCompound())));
   }
   world=new VehicleWorld(mc.world,VehicleStructure.display(cells));tile=(TileEntitySpaceDoor)world.getTileEntity(BlockPos.ORIGIN);
  }
  void close(){TEAnimatedScreenSelector.unloadGroups(world);}
 }
 static void run(Minecraft mc,File output)throws Exception{
  benchmarkCaches(output);
  customMirror(mc,output);
  CompanyDoorChecks.run(mc,output);
  worldGeometryDistance(mc);
  int bg=ScreenHousingTextures.screenIndex("frames/frames-door-window/vandor_bg"),inside=ScreenHousingTextures.screenIndex("materials/cool_steel"),cases=0;
  try(PrintWriter report=new PrintWriter(new File(output,"door-render-costs.csv"))){report.println("size,design,frame_quads_per_leaf,leaf_quads_per_leaf,glass_quads_per_leaf,assembly_tiles,opaque_batch_available");
   for(boolean large:new boolean[]{false,true})for(int design:new int[]{2,15,16,29}){
    Fixture f=new Fixture(mc,large,design);String prefix=(large?"large":"normal")+"-"+TileEntitySpaceDoor.DESIGNS[design];
    try{
     if(design!=2){
      DoorRenderModels.Entry leaf=DoorRenderModels.get(f.state.getBlock(),f.tile.metadata(large,false,1));int nativeRim=0,selectedRim=0;
      for(int side=-1;side<6;side++)for(BakedQuad q:leaf.model.getQuads(null,side<0?null:EnumFacing.getFront(side),0))if(FrameDoorModel.interiorQuad(q,leaf.model))nativeRim++;
      f.tile.frameAppearance.interior=inside;IBakedModel selectedLeaf=leaf.framed(f.tile.frameAppearance,large?1.5:1);
      for(int side=-1;side<6;side++)for(BakedQuad q:selectedLeaf.getQuads(null,side<0?null:EnumFacing.getFront(side),0))if(q.getSprite().getIconName().equals(ScreenHousingTextures.fullTexture(inside)))selectedRim++;
      require(nativeRim>0&&selectedRim>=nativeRim,"leaf inner rim finish missing "+prefix);
     }
     for(boolean oblique:new boolean[]{false,true}){
      f.tile.frameAppearance.front=-1;f.tile.frameAppearance.interior=-1;BufferedImage original=capture(mc,f,oblique,0,false);
      for(int variant=1;variant<=3;variant++){
       f.tile.frameAppearance.front=(variant&1)!=0?bg:-1;f.tile.frameAppearance.interior=(variant&2)!=0?inside:-1;BufferedImage selected=capture(mc,f,oblique,0,false);
       ImageIO.write(selected,"png",new File(output,"door-frames-"+prefix+"-"+variant+(oblique?"-oblique":"")+".png"));
       if((variant&1)!=0||oblique)require(difference(original,selected)>10,"frame finish invisible "+prefix+" variant="+variant+" oblique="+oblique);
       cases++;
      }
     }
     if(design==2){boolean available=OpaqueDoorBatch.available(f.tile);if(!net.minecraftforge.fml.client.FMLClientHandler.instance().hasOptifine()){
       require(available,"custom frames lost supported opaque batch "+prefix);BufferedImage prepared=capture(mc,f,false,.5F,true),normal=capture(mc,f,false,.5F,false);ImageIO.write(prepared,"png",new File(output,"door-frames-"+prefix+"-batched-moving.png"));ImageIO.write(normal,"png",new File(output,"door-frames-"+prefix+"-regular-moving.png"));require(difference(prepared,normal)<200,"batched custom frame changes pixels "+prefix+" pixels="+difference(prepared,normal));
      }
     }
     double scale=large?1.5:1;int[] counts=new int[3];for(int part=0;part<3;part++){DoorRenderModels.Entry entry=DoorRenderModels.get(f.state.getBlock(),f.tile.metadata(large,false,part));IBakedModel model=part==0?FrameDoorModel.get(entry.model,f.tile.frameAppearance,scale):entry.model;for(int side=-1;side<6;side++)counts[part]+=model.getQuads(null,side<0?null:EnumFacing.getFront(side),0).size();}
     report.println((large?"large":"normal")+","+design+","+counts[0]+","+counts[1]+","+counts[2]+","+f.world.tiles.size()+","+OpaqueDoorBatch.available(f.tile));
     if(design!=2){
      for(int tint=0;tint<GlassTextures.TINT_COUNT;tint++){
       f.tile.setGlassAppearance(new DoorGlassAppearance(-1,12,tint,1));BufferedImage glass=capture(mc,f,false,0,false);ImageIO.write(glass,"png",new File(output,"door-glass-"+prefix+"-"+GlassTextures.tintLabel(tint)+".png"));
       require(f.tile.opaqueGlass()==(tint==GlassTextures.OPAQUE),"door glazing pass policy");cases++;
      }
      f.tile.setGlassAppearance(new DoorGlassAppearance(inside,0,GlassTextures.OPAQUE,1));ImageIO.write(capture(mc,f,true,.5F,false),"png",new File(output,"door-glass-"+prefix+"-material-moving.png"));
      f.tile.configure(design,1,true,3,true,true,false,0,false);if(large)ImageIO.write(capture(mc,f,false,.5F,false),"png",new File(output,"door-glass-"+prefix+"-X-moving.png"));
     }
    }finally{f.close();}
   }
  }
  System.out.println("[vandorlabs][reprolab] door-appearance-render PASS cases="+cases+" native normal/large Standard Light/Dark independent frame finishes square material native glass4 tints moving/X split batch parity");
 }
 private static void worldGeometryDistance(Minecraft mc){
  for(String id:new String[]{"programmable_wall","programmable_glass","programmable_porthole_wall","programmable_porthole_block","programmable_diagonal_glass","programmable_door","large_programmable_door"}){
   Block block=Block.getBlockFromName("vandorlabs:"+id);require(block!=null,"distance fixture registry "+id);IBlockState state=block.getDefaultState();net.minecraft.tileentity.TileEntity fresh=block.createTileEntity(mc.world,state);require(fresh!=null,"distance fixture tile "+id);fresh.setPos(BlockPos.ORIGIN);VehicleWorld view=new VehicleWorld(mc.world,VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN,state,fresh.writeToNBT(new NBTTagCompound())))));net.minecraft.tileentity.TileEntity tile=view.getTileEntity(BlockPos.ORIGIN);require(tile.getMaxRenderDistanceSquared()==Double.MAX_VALUE,"world geometry retains tile distance cutoff "+id);
  }
  System.out.println("[vandorlabs][reprolab] world-geometry-distance PASS walls glass flat/diagonal portholes normal/large doors chunk/frustum visibility no64-block-cutoff");
 }
 private static void customMirror(Minecraft mc,File output)throws Exception{
  int count=0;java.lang.reflect.Field data=StaticSurfaceMesh.class.getDeclaredField("vertices");data.setAccessible(true);
  for(boolean large:new boolean[]{false,true})for(int mode=0;mode<3;mode++)for(Block material:new Block[]{net.minecraft.init.Blocks.OAK_DOOR,net.minecraft.init.Blocks.FURNACE}){
   Fixture f=new Fixture(mc,large,2,!large);try{int choice=CustomBlockMaterials.choice(material==net.minecraft.init.Blocks.OAK_DOOR?new net.minecraft.item.ItemStack(net.minecraft.init.Items.OAK_DOOR):new net.minecraft.item.ItemStack(material));require(CustomBlockMaterials.isCustom(choice),"fixture must select a real custom block item "+material.getRegistryName());f.tile.setFaceTexture(choice);f.tile.setTextureLayout(mode);
    for(boolean right:new boolean[]{false,true}){SelectedDoorGeometry geometry=DoorRenderModels.get(f.state.getBlock(),f.tile.metadata(true,right,1)).selected();StaticSurfaceMesh original=SelectedDoorFaceCache.get(geometry,choice,mode,large?1.5:1,false),mirror=SelectedDoorFaceCache.get(geometry,choice,mode,large?1.5:1,true);float[] a=(float[])data.get(original),b=(float[])data.get(mirror);require(a.length==b.length&&a.length>0,"mirrored material geometry missing");double sum=geometry.bounds[0]+geometry.bounds[3];for(int q=0;q<a.length;q+=32)for(int v=0;v<4;v++){int x=q+v*8,y=q+(3-v)*8;require(Math.abs(b[x]-(sum-a[y]))<1e-6&&b[x+1]==a[y+1]&&b[x+2]==a[y+2]&&b[x+3]==a[y+3]&&b[x+4]==a[y+4],"custom artwork must reflect across leaf including split upper/lower door sprites");}require(SelectedDoorFaceCache.get(geometry,choice,mode,large?1.5:1,true)==mirror,"mirrored mesh cache reused");count++;}
    BufferedImage image=capture(mc,f,false,0,false);ImageIO.write(image,"png",new File(output,"door-custom-mirrored-"+(large?"large":"paired")+"-"+material.getRegistryName().getResourcePath()+"-"+mode+".png"));
    if(material==net.minecraft.init.Blocks.OAK_DOOR&&mode==0){int different=0,total=0;for(int y=40;y<344;y++)for(int x=40;x<174;x++){int a=image.getRGB(x,y),b=image.getRGB(image.getWidth()-1-x,y);int delta=Math.abs((a>>16&255)-(b>>16&255))+Math.abs((a>>8&255)-(b>>8&255))+Math.abs((a&255)-(b&255));if(delta>30)different++;total++;}require(different<total/15,"actual paired/large custom door pixels must mirror around centre: "+different+"/"+total);}
   }finally{f.close();}
  }
  System.out.println("[vandorlabs][reprolab] door-custom-mirror PASS cases="+count+" normal/large both-hands Fit/Tile/Auto asymmetric-block/vanilla-door split-sprites cached-geometry");
 }
 private static volatile Object benchmarkSink;
 private static void benchmarkCaches(File output)throws Exception{
  Block block=Block.getBlockFromName("vandorlabs:programmable_door");int metadata=TileEntitySpaceDoor.metadata(2,1,true,false,false,0,true);FrameAppearance frame=new FrameAppearance();frame.front=ScreenHousingTextures.screenIndex("frames/frames-door-window/vandor_bg");frame.interior=ScreenHousingTextures.screenIndex("light_alloy_hull");
  double[] times=new double[31];long[] bytes=new long[31];for(int round=-20;round<31;round++){long startBytes=BenchmarkAllocations.currentThreadBytes(),start=System.nanoTime();for(int i=0;i<10000;i++){DoorRenderModels.Entry entry=DoorRenderModels.get(block,metadata);benchmarkSink=entry.framed(frame,1);}long elapsed=System.nanoTime()-start,used=BenchmarkAllocations.currentThreadBytes()-startBytes;if(round>=0){times[round]=elapsed/1e6;bytes[round]=used;}}
  Arrays.sort(times);Arrays.sort(bytes);try(PrintWriter report=new PrintWriter(new File(output,"door-cache-costs.csv"))){report.println("lookups,p50_ms,p95_ms,allocated_bytes");report.println("10000,"+times[15]+","+times[29]+","+bytes[15]);}
  System.out.println("[vandorlabs][reprolab] door-cache-cost lookups=10000 median-ms="+times[15]+" allocated-bytes="+bytes[15]);
 }
 private static int difference(BufferedImage a,BufferedImage b){int n=0;for(int y=0;y<a.getHeight();y++)for(int x=0;x<a.getWidth();x++){int p=a.getRGB(x,y),q=b.getRGB(x,y);if(Math.abs((p>>16&255)-(q>>16&255))+Math.abs((p>>8&255)-(q>>8&255))+Math.abs((p&255)-(q&255))>15)n++;}return n;}
 static BufferedImage capture(Minecraft mc,Fixture f,boolean oblique,float progress,boolean batch){
  int size=384;Framebuffer target=new Framebuffer(size,size,true);int previous=MinecraftForgeClient.getRenderPass();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();double padding=oblique?.6:.15;GlStateManager.ortho(-padding,f.width+padding,-padding,f.height+padding,-32,32);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
  try(TurntableRenderState saved=new TurntableRenderState()){
   target.bindFramebuffer(true);GlStateManager.loadIdentity();if(oblique){GlStateManager.translate(f.width/2D,f.height/2D,0);GlStateManager.rotate(35,0,1,0);GlStateManager.rotate(-12,1,0,0);GlStateManager.translate(-f.width/2D,-f.height/2D,0);}
   GlStateManager.depthMask(true);GlStateManager.clearColor(.12F,.18F,.25F,1);GlStateManager.clear(16640);GlStateManager.enableDepth();GlStateManager.disableCull();GlStateManager.disableFog();GlStateManager.disableLighting();GlStateManager.enableTexture2D();GlStateManager.enableAlpha();GlStateManager.color(1,1,1,1);mc.entityRenderer.disableLightmap();Tessellator.getInstance().getBuffer().setTranslation(0,0,0);
   ForgeHooksClient.setRenderPass(0);
   // Chunk batches inherit the world atlas binding; creating an FBO binds its own texture.
   mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
   if(batch){BufferBuilder b=Tessellator.getInstance().getBuffer();b.begin(GL11.GL_QUADS,net.minecraft.client.renderer.vertex.DefaultVertexFormats.BLOCK);OpaqueDoorBatch.draw(f.tile,f.state,progress,0,0,0,b);Tessellator.getInstance().draw();}
   else {TESlidingDoor.renderSpaceDoor(f.tile,f.state,EnumFacing.SOUTH,progress,0,0,0);if(f.width==2)TESlidingDoor.renderSpaceDoor(f.tile,f.state.withProperty(BlockVandorDoor.HINGE,BlockDoor.EnumHingePosition.LEFT),EnumFacing.SOUTH,progress,1,0,0);}
   if(f.tile.hasGlass()&&!f.tile.opaqueGlass()){ForgeHooksClient.setRenderPass(1);TESlidingDoor.renderSpaceDoor(f.tile,f.state,EnumFacing.SOUTH,progress,0,0,0);}
   GL11.glFinish();return net.minecraft.util.ScreenShotHelper.createScreenshot(size,size,target);
  }finally{ForgeHooksClient.setRenderPass(previous);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);target.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
 }
 private static void require(boolean value,String why){if(!value)throw new IllegalStateException("Door appearance: "+why);}
}
