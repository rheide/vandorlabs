package com.vandorlabs.gear.client;

import com.vandorlabs.gear.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.nbt.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import java.io.File;
import java.util.*;

/** Inspect actual emitted records: the upper mesh stays fixed while the lower mesh bends. */
@net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
public final class ArmMiddleRenderChecks {
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("arm-middle-render: "+message);}
    private static int vertices(String name)throws Exception{
        java.lang.reflect.Field meshes=GearModels.class.getDeclaredField("MESHES");meshes.setAccessible(true);Object mesh=((Map<?,?>)meshes.get(null)).get(name);require(mesh!=null,"baked mesh exists "+name);java.lang.reflect.Field quads=mesh.getClass().getDeclaredField("quads");quads.setAccessible(true);return ((List<?>)quads.get(mesh)).size()*4;
    }
    private static double[] records(GearTileEntity tile,double phase){
        NBTTagCompound data=tile.writeToNBT(new NBTTagCompound());data.setDouble("GearProgress",phase);tile.readFromNBT(data);
        BufferBuilder buffer=new BufferBuilder(65536);buffer.begin(7,DefaultVertexFormats.BLOCK);new GearRenderer().renderUncached(tile,0,0,0,0,buffer);buffer.finishDrawing();int count=buffer.getVertexCount(),stride=DefaultVertexFormats.BLOCK.getIntegerSize()*4;double[] out=new double[count*3];java.nio.ByteBuffer bytes=buffer.getByteBuffer();for(int v=0;v<count;v++)for(int a=0;a<3;a++){out[v*3+a]=bytes.getFloat(v*stride+a*4);require(Double.isFinite(out[v*3+a]),"emitted vertex is finite");}return out;
    }
    public static void run(Minecraft mc,File output)throws Exception{
        int upper=vertices("swing_linkage_upper"),lower=vertices("swing_linkage_lower");require(upper>0&&lower>0&&upper+lower==vertices("swing_linkage"),"split meshes preserve all authored quads");int fixed=vertices("mount")+vertices("sleeve");int cases=0;
        for(GearBlock block:new GearBlock[]{GearRegistry.GEAR_SWING_ARM,GearRegistry.LARGE_GEAR_SWING_ARM})for(EnumFacing mount:EnumFacing.values())for(boolean rotates:new boolean[]{false,true}){
            GearTileEntity seed=new GearTileEntity();seed.setPos(BlockPos.ORIGIN);NBTTagCompound data=seed.writeToNBT(new NBTTagCompound());data.setBoolean("GearController",true);data.setBoolean("GearOperational",true);data.setInteger("GearHeight",1);data.setString("GearKind","NONE");data.setString("GearMode",rotates?"ROTATE":"ROTATE_LEVEL");data.setString("ArmMountDirection",mount.name());data.setBoolean("ArmMiddleHinge",true);data.setBoolean("ArmRotatePayload",rotates);data.setString("GearError","");NBTTagList feet=new NBTTagList();feet.appendTag(new NBTTagIntArray(new int[]{0,-1,0}));data.setTag("GearFeet",feet);
            VehicleStructure snapshot=VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN,block.getDefaultState().withProperty(GearBlock.ASSEMBLED,true),data)));GearTileEntity tile=(GearTileEntity)new VehicleWorld(mc.world,snapshot).getTileEntity(BlockPos.ORIGIN);
            double[] rest=records(tile,1),bent=records(tile,0);require(rest.length==bent.length&&rest.length>=(fixed+upper+lower)*3,"complete arm emitted in both poses");for(int i=0;i<(fixed+upper)*3;i++)require(rest[i]==bent[i],"mounted housing and upper linkage remain fixed "+mount);int changed=0;for(int i=(fixed+upper)*3;i<(fixed+upper+lower)*3;i++)if(Math.abs(rest[i]-bent[i])>1e-6)changed++;require(changed>lower,"lower mesh rotates about elbow "+mount);
            if(mount==EnumFacing.DOWN&&rotates)capture(mc,output,tile,block.sizeClass());cases++;
        }
        runFootFrames(mc);
        System.out.println("[vandorlabs][reprolab] arm-middle-render PASS cases="+cases+" actual emitted upper fixed lower moving all six mounts regular/Large rotate/level exact authored quad count rendered FBO");
    }
    private static double[] suffix(double[] values,int vertices){return java.util.Arrays.copyOfRange(values,values.length-vertices*3,values.length);}
    private static double[] released(Minecraft mc,VehicleStructure.Cell foot)throws Exception{
        NBTTagCompound data=foot.tileData();data.removeTag("GearOwnerOffset");data.setBoolean("GearController",false);VehicleStructure snapshot=VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(foot.pos,foot.state.withProperty(GearBlock.ASSEMBLED,false),data)));VehicleWorld world=new VehicleWorld(mc.world,snapshot);GearTileEntity tile=(GearTileEntity)world.getTileEntity(foot.pos);
        if(tile.mountDirection()!=EnumFacing.DOWN)return records(tile,1);
        // DOWN uses the real baked block path; sideways feet use the TESR path.
        net.minecraft.block.state.IBlockState state=world.getBlockState(foot.pos);net.minecraft.client.renderer.block.model.IBakedModel model=mc.getBlockRendererDispatcher().getModelForState(state.getActualState(world,foot.pos));List<net.minecraft.client.renderer.block.model.BakedQuad> quads=new ArrayList<>();quads.addAll(model.getQuads(state,null,0));for(EnumFacing face:EnumFacing.values())quads.addAll(model.getQuads(state,face,0));double[] out=new double[quads.size()*12];int n=0;for(net.minecraft.client.renderer.block.model.BakedQuad quad:quads){int[] bytes=quad.getVertexData();int stride=quad.getFormat().getIntegerSize();for(int v=0;v<4;v++)for(int axis=0;axis<3;axis++)out[n++]=Float.intBitsToFloat(bytes[v*stride+axis]);}return out;
    }
    private static double[] quarter(double[] source,int turns){double[] result=source.clone();for(int n=0;n<source.length;n+=3){double x=source[n],z=source[n+2];switch(turns){case 1:result[n]=1-z;result[n+2]=x;break;case 2:result[n]=1-x;result[n+2]=1-z;break;case 3:result[n]=z;result[n+2]=1-x;break;default:break;}}return result;}
    private static Map<String,Integer> points(double[] vertices){Map<String,Integer> points=new TreeMap<>();for(int n=0;n<vertices.length;n+=3){String key=Math.round(vertices[n]*100000)+","+Math.round(vertices[n+1]*100000)+","+Math.round(vertices[n+2]*100000);points.put(key,points.getOrDefault(key,0)+1);}return points;}
    private static void sameVertices(double[] expected,double[] actual,boolean ordered,String detail){require(expected.length==actual.length,"same emitted vertex count "+detail);if(ordered){for(int n=0;n<expected.length;n++)require(Math.abs(expected[n]-actual[n])<2e-6,"ordered authored vertex covariance "+detail+" component="+n+" expected="+expected[n]+" actual="+actual[n]);}else require(points(expected).equals(points(actual)),"baked emitted vertex covariance "+detail);}
    public static void runFootFrames(Minecraft mc)throws Exception{
        int cases=0;BlockPos anchor=new BlockPos(1,3,1);
        for(GearBlock block:new GearBlock[]{GearRegistry.LANDING_PLATE,GearRegistry.LANDING_SKI,GearRegistry.LANDING_WHEEL})for(EnumFacing mount:EnumFacing.values()){
            BlockPos foot=anchor.add(ArmOrientation.vector(mount,new BlockPos(0,-1,0)));GearTileEntity seed=new GearTileEntity();seed.setPos(anchor);NBTTagCompound root=seed.writeToNBT(new NBTTagCompound());root.setBoolean("GearController",true);root.setBoolean("GearOperational",true);root.setInteger("GearHeight",1);root.setString("GearKind",block.kind().name());root.setString("GearMode","ROTATE");root.setBoolean("ArmRotatePayload",true);root.setString("ArmMountDirection",mount.name());root.setInteger("GearFootFacing",EnumFacing.NORTH.getHorizontalIndex());root.setInteger("GearSourceFootFacing",EnumFacing.NORTH.getHorizontalIndex());if(mount!=EnumFacing.DOWN)root.setString(ArmFootComponentFrame.SOURCE_KEY,mount.name());NBTTagList feet=new NBTTagList();feet.appendTag(new NBTTagIntArray(new int[]{0,-1,0}));root.setTag("GearFeet",feet);
            GearTileEntity part=new GearTileEntity();part.setPos(foot);NBTTagCompound data=part.writeToNBT(new NBTTagCompound());data.setLong("GearOwnerOffset",anchor.subtract(foot).toLong());data.setString("ArmMountDirection",mount.name());data.setString(ArmFootComponentFrame.KEY,mount.name());VehicleStructure snapshot=VehicleStructure.display(java.util.Arrays.asList(new VehicleStructure.Cell(anchor,GearRegistry.GEAR_SWING_ARM.getDefaultState().withProperty(GearBlock.ASSEMBLED,true),root),new VehicleStructure.Cell(foot,block.getDefaultState().withProperty(GearBlock.ASSEMBLED,true),data)));VehicleWorld view=new VehicleWorld(mc.world,snapshot);
            int count=block.kind()==GearBlock.Kind.WHEEL?vertices("wheel_bracket")+vertices("wheel_connector")+vertices("wheel_tyre"):vertices(block.kind()==GearBlock.Kind.SKI?"ski_0":"plate_0");double[] bound=suffix(records((GearTileEntity)view.getTileEntity(anchor),1),count),loose=released(mc,snapshot.at(foot));
            for(int turn=0;turn<4;turn++){VehicleStructure rotated=VehicleRotation.rotate(snapshot,view,turn);BlockPos nextRoot=VehicleRotation.cell(anchor,(int)snapshot.bounds.maxX,(int)snapshot.bounds.maxZ,turn),nextFoot=VehicleRotation.cell(foot,(int)snapshot.bounds.maxX,(int)snapshot.bounds.maxZ,turn);VehicleWorld next=new VehicleWorld(mc.world,rotated);String detail=block.kind()+" mount="+mount+" yaw="+turn;sameVertices(quarter(bound,turn),suffix(records((GearTileEntity)next.getTileEntity(nextRoot),1),count),block.kind()!=GearBlock.Kind.PLATE,"bound "+detail);sameVertices(quarter(loose,turn),released(mc,rotated.at(nextFoot)),mount!=EnumFacing.DOWN&&block.kind()!=GearBlock.Kind.PLATE,"released "+detail);cases++;}
        }
        System.out.println("[vandorlabs][reprolab] arm-foot-frame-render PASS cases="+cases+" actual bound and released emitted vertices VehicleRotation all regular families six mounts four headings including UP ski nose; DOWN native baked and side TESR paths");
    }
    private static void capture(Minecraft mc,File output,GearTileEntity tile,int size)throws Exception{
        records(tile,.5);Framebuffer frame=new Framebuffer(384,384,true);GlStateManager.matrixMode(5889);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(-2.2,2.2,-2.2,2.2,-10,10);GlStateManager.matrixMode(5888);GlStateManager.pushMatrix();
        try(com.vandorlabs.client.TurntableRenderState saved=new com.vandorlabs.client.TurntableRenderState()){
            frame.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.depthMask(true);GlStateManager.clearColor(.125F,.1875F,.25F,1);GlStateManager.clear(16640);GlStateManager.enableTexture2D();GlStateManager.enableDepth();GlStateManager.disableLighting();GlStateManager.disableCull();mc.entityRenderer.disableLightmap();GlStateManager.rotate(20,1,0,0);GlStateManager.rotate(35,0,1,0);GearRenderer renderer=new GearRenderer();renderer.setRendererDispatcher(net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher.instance);renderer.render(tile,-.5,.3,-.5,0,-1,1);org.lwjgl.opengl.GL11.glFinish();java.awt.image.BufferedImage image=net.minecraft.util.ScreenShotHelper.createScreenshot(384,384,frame);int ink=0;int back=image.getRGB(0,0);for(int y=0;y<384;y++)for(int x=0;x<384;x++)if(image.getRGB(x,y)!=back)ink++;require(ink>500,"actual bent arm renders visible pixels size="+size);javax.imageio.ImageIO.write(image,"png",new File(output,"shot_arm_middle_hinge_"+size+".png"));
        }finally{GlStateManager.popMatrix();GlStateManager.matrixMode(5889);GlStateManager.popMatrix();GlStateManager.matrixMode(5888);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
    }
    private ArmMiddleRenderChecks(){}
}
