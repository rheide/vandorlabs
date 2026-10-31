package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import java.nio.*;
import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;
import org.lwjgl.opengl.GL11;

/** Emitted triangles must leave the aperture open and connect both sides at authored inner edges. */
final class TrapdoorFrameChecks {
 static void run(Minecraft mc,File output)throws Exception{
  solidMaterials(mc);
  int choice=ScreenHousingTextures.screenIndex("frames/frames-door-window/lights-cyan-1"),cases=0;
  TextureAtlasSprite sprite=mc.getTextureMapBlocks().getAtlasSprite(ScreenHousingTextures.fullTexture(choice));
  for(boolean diagonal:new boolean[]{false,true})for(int position=0;position<3;position++)for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean inverted:new boolean[]{false,true})for(boolean sliding:new boolean[]{false,true})for(int layout=0;layout<3;layout++){
   Block block=Block.getBlockFromName(diagonal?"vandorlabs:programmable_diagonal_trapdoor":"vandorlabs:programmable_trapdoor");
   IBlockState state=block.getDefaultState().withProperty(BlockTrapDoor.FACING,facing).withProperty(BlockTrapDoor.HALF,inverted?BlockTrapDoor.DoorHalf.TOP:BlockTrapDoor.DoorHalf.BOTTOM);
   TileEntityProgrammableTrapdoor seed=diagonal?new TileEntityProgrammableDiagonalTrapdoor():new TileEntityProgrammableTrapdoor();seed.configure(choice,position,sliding,0,0);seed.setTextureLayout(layout);
   VehicleWorld world=new VehicleWorld(mc.world,VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN,state,seed.writeToNBT(new NBTTagCompound())))));
   TileEntityProgrammableTrapdoor tile=(TileEntityProgrammableTrapdoor)world.getTileEntity(BlockPos.ORIGIN);
   for(double pose:new double[]{0,.5,1}){
    double[][] corners=diagonal?BlockProgrammableDiagonalTrapdoor.corners(state,(TileEntityProgrammableDiagonalTrapdoor)tile,pose):tile.corners(state,pose);
    double[][] inverse=inverse(corners),uv=TEProgrammableTrapdoor.materialCoordinates(tile,state);int v=diagonal&&position!=2?1:2,uAxis=axis(uv,0),vAxis=axis(uv,v),depth=3-uAxis-vAxis;
    BufferBuilder buffer=new BufferBuilder(65536);buffer.begin(GL11.GL_QUADS,BlockSurfaceFormat.get());TEProgrammableTrapdoor.drawConfiguredLeaf(buffer,sprite,tile,state,pose,0xf000f0);buffer.finishDrawing();
    ByteBuffer bytes=buffer.getByteBuffer();int stride=BlockSurfaceFormat.get().getNextOffset(),walls=0;
    for(int i=0;i<buffer.getVertexCount();i+=4){double[][] p=new double[4][3];for(int j=0;j<4;j++)for(int a=0;a<3;a++){p[j][a]=inverse[a][3];for(int b=0;b<3;b++)p[j][a]+=inverse[a][b]*bytes.getFloat((i+j)*stride+b*4);}
     boolean broad=true;double min=1,max=0;for(double[] q:p){broad&=Math.abs(q[depth]-p[0][depth])<1e-5;min=Math.min(min,q[depth]);max=Math.max(max,q[depth]);}
     if(broad)require(!inside(p[0],p[1],p[2],uAxis,vAxis)&&!inside(p[0],p[2],p[3],uAxis,vAxis),"opaque triangle covers aperture "+diagonal+"/"+position+"/"+facing+"/"+pose);
     else if(max-min>.99){boolean inner=false;for(int a:new int[]{uAxis,vAxis}){boolean plane=p[0][a]>1e-4&&p[0][a]<.9999;for(double[] q:p)plane&=Math.abs(q[a]-p[0][a])<1e-5;inner|=plane;}if(inner)walls++;}
    }
    require(walls>=4,"missing extruded inside border walls="+walls+" diagonal="+diagonal+" position="+position+" facing="+facing+" inverted="+inverted+" sliding="+sliding+" layout="+layout+" pose="+pose+" uv="+Arrays.deepToString(uv));cases++;
    if(facing==EnumFacing.NORTH&&!inverted&&!sliding&&layout==1&&pose==0){capture(mc,output,tile,state,sprite,inverse,uAxis,vAxis,depth,(diagonal?"diagonal":"regular")+"-"+position,false);capture(mc,output,tile,state,sprite,inverse,uAxis,vAxis,depth,(diagonal?"diagonal":"regular")+"-"+position,true);}
   }
  }
  System.out.println("[vandorlabs][reprolab] trapdoor-frame-aperture PASS cases="+cases+" regular diagonal all mounts facings inversions hinge/slide closed/moving/open all layouts inner-walls");
 }
 private static void solidMaterials(Minecraft mc){
  LinkedHashSet<Integer> choices=new LinkedHashSet<>();choices.add(ScreenHousingTextures.screenIndex("cargo_doors/low/reinforced_leaf"));
  for(int i=0;i<ScreenHousingTextures.IDS.length;i++)if(ScreenHousingTextures.visible(i)&&(!FrameAppearance.valid(i)||!FrameAppearance.border(i)))choices.add(i);
  require(ScreenHousingTextures.texture(choices.iterator().next()).endsWith("door_reinforced_leaf_low_half"),"reported virtual Reinforced Leaf sprite missing");
  int cases=0;for(int choice:choices){require(choice>=0,"reported Reinforced Leaf choice missing");
   TextureAtlasSprite sprite=mc.getTextureMapBlocks().getAtlasSprite(ScreenHousingTextures.texture(choice));require(sprite!=mc.getTextureMapBlocks().getMissingSprite(),"solid leaf sprite missing: "+choice);
   for(boolean diagonal:new boolean[]{false,true})for(int position=0;position<3;position++){
    Block block=Block.getBlockFromName(diagonal?"vandorlabs:programmable_diagonal_trapdoor":"vandorlabs:programmable_trapdoor");IBlockState state=block.getDefaultState();
    TileEntityProgrammableTrapdoor seed=diagonal?new TileEntityProgrammableDiagonalTrapdoor():new TileEntityProgrammableTrapdoor();seed.configure(choice,position,false,0,0);
    VehicleWorld world=new VehicleWorld(mc.world,VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN,state,seed.writeToNBT(new NBTTagCompound())))));
    TileEntityProgrammableTrapdoor tile=(TileEntityProgrammableTrapdoor)world.getTileEntity(BlockPos.ORIGIN);BufferBuilder b=new BufferBuilder(4096);b.begin(GL11.GL_QUADS,BlockSurfaceFormat.get());TEProgrammableTrapdoor.drawConfiguredLeaf(b,sprite,tile,state,0,0xf000f0);b.finishDrawing();
    double[][] corners=diagonal?BlockProgrammableDiagonalTrapdoor.corners(state,(TileEntityProgrammableDiagonalTrapdoor)tile,0):tile.corners(state,0),transform=inverse(corners),uv=TEProgrammableTrapdoor.materialCoordinates(tile,state);int component=diagonal&&position!=2?1:2,u=axis(uv,0),v=axis(uv,component),depth=3-u-v,stride=BlockSurfaceFormat.get().getNextOffset();boolean[] covered=new boolean[2];ByteBuffer bytes=b.getByteBuffer();
    for(int vertex=0;vertex<b.getVertexCount();vertex+=4){double[][] q=new double[4][3];for(int j=0;j<4;j++)for(int a=0;a<3;a++){q[j][a]=transform[a][3];for(int k=0;k<3;k++)q[j][a]+=transform[a][k]*bytes.getFloat((vertex+j)*stride+k*4);}for(int side=0;side<2;side++){boolean plane=true;for(double[] point:q)plane&=Math.abs(point[depth]-side)<1e-4;if(plane&&(inside(q[0],q[1],q[2],u,v)||inside(q[0],q[2],q[3],u,v)))covered[side]=true;}}
    require(covered[0]&&covered[1],"ordinary material leaf must cover its center on both sides, choice="+choice+" diagonal="+diagonal+" position="+position+" vertices="+b.getVertexCount());cases++;
   }
  }
  System.out.println("[vandorlabs][reprolab] trapdoor-solid-materials PASS cases="+cases+" exact reported Reinforced Leaf virtual-half sprite, all visible non-cutout catalog choices regular/diagonal all positions solid leaf centers");
 }
 private static boolean inside(double[] a,double[] b,double[] c,int u,int v){double area=cross(a,b,c,u,v);if(Math.abs(area)<1e-8)return false;double[] p=new double[3];p[u]=.473;p[v]=.519;double x=cross(p,b,c,u,v)/area,y=cross(a,p,c,u,v)/area,z=cross(a,b,p,u,v)/area;return x>1e-5&&y>1e-5&&z>1e-5;}
 private static double cross(double[] a,double[] b,double[] c,int u,int v){return (b[u]-a[u])*(c[v]-a[v])-(b[v]-a[v])*(c[u]-a[u]);}
 private static int axis(double[][] uv,int component){int best=0;for(int a=1;a<3;a++)if(Math.abs(uv[1<<a][component]-uv[0][component])>Math.abs(uv[1<<best][component]-uv[0][component]))best=a;return best;}
 private static double[][] inverse(double[][] corners){double[][] basis=new double[3][3],out=new double[3][4];for(int a=0;a<3;a++)for(int b=0;b<3;b++)basis[a][b]=corners[1<<a][b]-corners[0][b];for(int a=0;a<3;a++){double[] b=basis[(a+1)%3],c=basis[(a+2)%3];for(int j=0;j<3;j++)out[a][j]=b[(j+1)%3]*c[(j+2)%3]-b[(j+2)%3]*c[(j+1)%3];double det=0;for(int j=0;j<3;j++)det+=out[a][j]*basis[a][j];for(int j=0;j<3;j++){out[a][j]/=det;out[a][3]-=out[a][j]*corners[0][j];}}return out;}
 private static void capture(Minecraft mc,File output,TileEntityProgrammableTrapdoor tile,IBlockState state,TextureAtlasSprite sprite,double[][] inverse,int u,int v,int depth,String name,boolean oblique)throws Exception{
  Framebuffer target=new Framebuffer(256,256,true);GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(0,1,0,1,-32,32);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
  try(TurntableRenderState saved=new TurntableRenderState()){
   target.bindFramebuffer(true);GlStateManager.loadIdentity();FloatBuffer camera=org.lwjgl.BufferUtils.createFloatBuffer(16);int[] rows={u,v,depth};for(int col=0;col<4;col++)for(int row=0;row<4;row++)camera.put(row==3?(col==3?1:0):(float)(inverse[rows[row]][col]*(row==2?.1:1)));camera.flip();if(oblique){GlStateManager.translate(.5,.5,0);GlStateManager.rotate(25,1,0,0);GlStateManager.rotate(-25,0,1,0);GlStateManager.translate(-.5,-.5,0);}GL11.glMultMatrix(camera);
   GlStateManager.depthMask(true);GlStateManager.clearColor(.2F,.05F,.3F,1);GlStateManager.clear(16640);GlStateManager.enableDepth();GlStateManager.disableCull();GlStateManager.disableFog();GlStateManager.disableLighting();GlStateManager.enableTexture2D();GlStateManager.enableAlpha();GlStateManager.alphaFunc(GL11.GL_GREATER,.1F);GlStateManager.disableBlend();mc.entityRenderer.disableLightmap();mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
   BufferBuilder b=Tessellator.getInstance().getBuffer();b.setTranslation(0,0,0);b.begin(GL11.GL_QUADS,BlockSurfaceFormat.get());TEProgrammableTrapdoor.drawConfiguredLeaf(b,sprite,tile,state,0,0xf000f0);Tessellator.getInstance().draw();GL11.glFinish();java.awt.image.BufferedImage image=net.minecraft.util.ScreenShotHelper.createScreenshot(256,256,target);
   int center=image.getRGB(128,128);require(Math.abs((center>>16&255)-51)<3&&Math.abs((center>>8&255)-13)<3&&Math.abs((center&255)-77)<3,"framebuffer aperture has backing");ImageIO.write(image,"png",new File(output,"trapdoor-frame-open-"+name+(oblique?"-oblique":"")+".png"));
  }finally{GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);target.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
 }
 private static void require(boolean value,String why){if(!value)throw new IllegalStateException("Trapdoor frame: "+why);}
}
