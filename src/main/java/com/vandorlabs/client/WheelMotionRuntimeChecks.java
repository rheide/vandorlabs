package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityLandingGear;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import org.lwjgl.opengl.GL11;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.Color;
import java.io.File;
import java.util.*;

/** Actual isolated GL captures, so camera motion cannot masquerade as wheel animation. */
public final class WheelMotionRuntimeChecks {
    private static final BlockPos ROOT=new BlockPos(4,4,4);
    private static final int PIXELS=256;
    private static final String[] POSES={"parked","neutral","forward","reverse","left","right","stopped","Hover","Hover moved"};
    private static int variant,startupTicks,settledTicks;
    private static BlockPos pendingParked;
    private static BlockTelescopicLandingGear gear;
    private static BufferedImage montage;
    private static BlockPos parked;
    private static IBlockState parkedBefore;
    private static NBTTagCompound parkedTileBefore;
    private static void require(boolean yes,String message){if(!yes)throw new IllegalStateException("wheel motion: "+message);}
    public static void begin(Minecraft mc){
        variant=startupTicks=settledTicks=0;gear=null;parked=pendingParked=null;
        for(Block block:Block.REGISTRY)if(block instanceof BlockTelescopicLandingGear){gear=(BlockTelescopicLandingGear)block;break;}
        require(gear!=null,"landing gear registered");
        montage=new BufferedImage(POSES.length*PIXELS,8*(PIXELS+24),BufferedImage.TYPE_INT_RGB);
        directionMath(mc);
    }
    private static VehicleStructure snapshot(Minecraft mc,int kind){
        java.util.List<VehicleStructure.Cell> cells=new ArrayList<>();
        cells.add(new VehicleStructure.Cell(BlockPos.ORIGIN,ModBlocks.PILOT_SEAT.getDefaultState(),null));
        if(kind<3){
            BlockWheel wheel=(BlockWheel)Block.getBlockFromName("vandorlabs:wheel_"+new String[]{"small","medium","large"}[kind]);
            IBlockState state=wheel.getDefaultState().withProperty(BlockWheel.FACING,EnumFacing.WEST);
            java.util.List<BlockPos> members=wheel.members(ROOT,state);
            for(int n=0;n<members.size();n++)cells.add(new VehicleStructure.Cell(members.get(n),state.withProperty(BlockWheel.PART,n),null));
        }else{
            TileEntityLandingGear tile=new TileEntityLandingGear();tile.setPos(ROOT);
            NBTTagCompound data=tile.writeToNBT(new NBTTagCompound());data.setInteger("GearSize",kind-3);data.setInteger("ExtensionPixels",16);data.setInteger("RedstoneMode",0);data.setFloat("Progress",1);
            cells.add(new VehicleStructure.Cell(ROOT,gear.getDefaultState().withProperty(BlockTelescopicLandingGear.EXTENDED,true),data));
        }
        return new VehicleStructure(cells,BlockPos.ORIGIN,EnumFacing.NORTH,1);
    }
    private static void directionMath(Minecraft mc){
        EntityGroundVehicle probe=new EntityGroundVehicle(mc.world);probe.install(snapshot(mc,0));
        double x=mc.player.posX,y=mc.player.posY,z=mc.player.posZ;
        require(VehicleWheelMotion.samplePose(probe,new VehiclePose(x,y,z,0))==0,"initial sample is stationary");
        double forward=VehicleWheelMotion.samplePose(probe,new VehiclePose(x,y,z-.3,0));
        require(Math.abs(forward-.3)<1e-8,"forward displacement accumulates positively");
        require(Math.abs(VehicleWheelMotion.samplePose(probe,new VehiclePose(x,y,z,0)))<1e-8,"reverse displacement unwinds travel");
        require(Math.abs(VehicleWheelMotion.samplePose(probe,new VehiclePose(x+.3,y,z,0)))<1e-8,"lateral displacement does not spin wheels");
        double stopped=VehicleWheelMotion.samplePose(probe,new VehiclePose(x+.3,y,z,0));
        require(stopped==VehicleWheelMotion.samplePose(probe,new VehiclePose(x+.3,y,z,0)),"stationary sampling cannot advance rotation");
        probe.hover=true;probe.steering=1;
        require(stopped==VehicleWheelMotion.samplePose(probe,new VehiclePose(x+.3,y,z-.4,0))&&VehicleWheelMotion.steer(probe)==0,"Hover freezes rolling and steering");
        probe.hover=false;
        require(stopped==VehicleWheelMotion.samplePose(probe,new VehiclePose(x+10,y,z-10,0)),"teleports do not create rotation bursts");
        require(VehicleWheelMotion.roll(.3,.5,1)==-VehicleWheelMotion.roll(-.3,.5,1),"forward and reverse angles oppose each other");
        require(VehicleWheelMotion.roll(.3,.5,-1)==-VehicleWheelMotion.roll(.3,.5,1),"opposite axle orientation reverses roll");
        require(Math.abs(VehicleWheelMotion.roll(.3,1,1)*2-VehicleWheelMotion.roll(.3,.5,1))<1e-5,"radius controls rotation distance");
        probe.steering=-1;float left=VehicleWheelMotion.steer(probe);probe.steering=1;require(left==-VehicleWheelMotion.steer(probe)&&left!=0,"left and right steering oppose each other");probe.setDead();
    }
    private static void finite(AxisAlignedBB box,String name){
        require(box!=null&&Double.isFinite(box.minX)&&Double.isFinite(box.minY)&&Double.isFinite(box.minZ)&&Double.isFinite(box.maxX)&&Double.isFinite(box.maxY)&&Double.isFinite(box.maxZ),"finite "+name+" bounds");
        require(box.maxX>box.minX&&box.maxY>box.minY&&box.maxZ>box.minZ,"nonempty "+name+" bounds");
    }
    private static void textureBounds(Minecraft mc,IBlockState state){
        IBakedModel model=mc.getBlockRendererDispatcher().getBlockModelShapes().getModelForState(state);
        require(model!=mc.getBlockRendererDispatcher().getBlockModelShapes().getModelManager().getMissingModel(),"wheel has a baked model");
        java.util.List<BakedQuad> quads=new ArrayList<>(model.getQuads(state,null,0));
        for(EnumFacing side:EnumFacing.values())quads.addAll(model.getQuads(state,side,0));
        require(!quads.isEmpty(),"wheel baked quads exist");
        for(BakedQuad quad:quads){int stride=quad.getFormat().getIntegerSize(),uv=quad.getFormat().getUvOffsetById(0)/4;int[] vertices=quad.getVertexData();
            for(int n=0;n<4;n++){double u=Float.intBitsToFloat(vertices[n*stride+uv]),v=Float.intBitsToFloat(vertices[n*stride+uv+1]);
                require(Double.isFinite(u)&&Double.isFinite(v)&&u>=quad.getSprite().getMinU()-1e-6&&u<=quad.getSprite().getMaxU()+1e-6&&v>=quad.getSprite().getMinV()-1e-6&&v<=quad.getSprite().getMaxV()+1e-6,"baked wheel UV remains inside its stitched sprite: "+quad.getSprite().getIconName());}
        }
    }
    private static BufferedImage render(Minecraft mc,EntityGroundVehicle craft,int kind,boolean placed,boolean whole){
        Framebuffer target=new Framebuffer(PIXELS,PIXELS,true);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        double extent=kind>=6?2.5:2.1;
        GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(-extent,extent,-extent,extent,-20,20);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        double oldEntityX=TileEntityRendererDispatcher.instance.entityX,oldEntityY=TileEntityRendererDispatcher.instance.entityY,oldEntityZ=TileEntityRendererDispatcher.instance.entityZ;
        net.minecraft.world.World oldWorld=TileEntityRendererDispatcher.instance.world;
        try{
            target.bindFramebuffer(true);GlStateManager.depthMask(true);GlStateManager.clearColor(0,0,0,1);GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
            GlStateManager.enableDepth();GlStateManager.disableBlend();GlStateManager.enableTexture2D();GlStateManager.disableLighting();GlStateManager.disableCull();GlStateManager.color(1,1,1,1);
            mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            GlStateManager.loadIdentity();GlStateManager.rotate(18,1,0,0);GlStateManager.rotate(60,0,1,0);
            double centerX=ROOT.getX()+(kind==7?1:kind<3?.75:.5),centerY=ROOT.getY()+(kind==2?1:kind<3?.5:-.25),centerZ=ROOT.getZ()+(kind==2?0:kind==7?1:.5);
            GlStateManager.translate(-centerX,-centerY,-centerZ);
            if(whole){RenderGroundVehicle.beginFrame();new RenderGroundVehicle(mc.getRenderManager()).doRender(craft,0,0,0,0,1);}
            else if(kind<3){
                if(placed){GlStateManager.pushMatrix();GlStateManager.translate(ROOT.getX(),ROOT.getY(),ROOT.getZ());mc.getBlockRendererDispatcher().getBlockModelRenderer().renderModelBrightnessColor(mc.getBlockRendererDispatcher().getBlockModelShapes().getModelForState(craft.structure.at(ROOT).state),1,1,1,1);GlStateManager.popMatrix();}
                else VehicleWheelRenderer.render(craft,Collections.singletonList(craft.structure.at(ROOT)),1);
            }else{
                TileEntityLandingGear tile=(TileEntityLandingGear)(placed?mc.world.getTileEntity(parked):craft.view.getTileEntity(ROOT));
                TileEntityRendererDispatcher.instance.world=tile.getWorld();TileEntityRendererDispatcher.instance.entityX=centerX;TileEntityRendererDispatcher.instance.entityY=centerY;TileEntityRendererDispatcher.instance.entityZ=centerZ;
                TELandingGear renderer=(TELandingGear)(Object)TileEntityRendererDispatcher.instance.getRenderer(tile);
                require(renderer!=null,"landing gear renderer registered");renderer.render(tile,ROOT.getX(),ROOT.getY(),ROOT.getZ(),1,-1,1);
            }
            GL11.glFinish();return ScreenShotHelper.createScreenshot(PIXELS,PIXELS,target);
        }finally{
            TileEntityRendererDispatcher.instance.world=oldWorld;TileEntityRendererDispatcher.instance.entityX=oldEntityX;TileEntityRendererDispatcher.instance.entityY=oldEntityY;TileEntityRendererDispatcher.instance.entityZ=oldEntityZ;
            GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);GL11.glPopAttrib();
            GlStateManager.enableDepth();GlStateManager.depthMask(true);GlStateManager.enableCull();GlStateManager.enableLighting();GlStateManager.color(1,1,1,1);
            target.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);
        }
    }
    private static int difference(BufferedImage a,BufferedImage b,boolean silhouette){int changed=0;for(int y=0;y<PIXELS;y++)for(int x=0;x<PIXELS;x++){int aa=a.getRGB(x,y)&0xffffff,bb=b.getRGB(x,y)&0xffffff;if(silhouette?(aa==0)!=(bb==0):aa!=bb)changed++;}return changed;}
    private static int occupied(BufferedImage image){int count=0;for(int y=0;y<PIXELS;y++)for(int x=0;x<PIXELS;x++)if((image.getRGB(x,y)&0xffffff)!=0)count++;return count;}
    public static boolean tick(Minecraft mc,File output)throws Exception{
        if(variant>=8)return true;
        if(parked==null){
            require(++startupTicks<200,"spawn/reference chunk readiness timeout player="+mc.player.getPosition());
            BlockPos candidate=mc.player.getPosition().up(8).toImmutable();
            if(!mc.world.isBlockLoaded(mc.player.getPosition())||!mc.world.isBlockLoaded(candidate)){pendingParked=null;settledTicks=0;return false;}
            if(!candidate.equals(pendingParked)){pendingParked=candidate;settledTicks=0;return false;}
            if(++settledTicks<3)return false;
            // A client player exists before its first authoritative spawn teleport.
            // Keep the real placed reference in the settled, loaded spawn chunk.
            parked=candidate;parkedBefore=mc.world.getBlockState(parked);TileEntity old=mc.world.getTileEntity(parked);parkedTileBefore=old==null?null:old.writeToNBT(new NBTTagCompound());
        }
        if(variant==0)WheelItemRuntimeChecks.run(mc,output);
        int kind=variant;VehicleStructure snapshot=snapshot(mc,kind);EntityGroundVehicle craft=new EntityGroundVehicle(mc.world);craft.install(snapshot);
        double baseX=parked.getX()-ROOT.getX(),baseY=parked.getY()-ROOT.getY(),baseZ=parked.getZ()-ROOT.getZ();craft.setPosition(baseX,baseY,baseZ);craft.lastTickPosX=baseX;craft.lastTickPosY=baseY;craft.lastTickPosZ=baseZ;
        craft.view.origin=new BlockPos(baseX,baseY,baseZ);finite(craft.getRenderBoundingBox(),"craft");
        VehicleStructure.Cell root=snapshot.at(ROOT);
        if(kind<3)textureBounds(mc,root.state);else finite(craft.view.getTileEntity(ROOT).getRenderBoundingBox(),"landing gear");
        boolean installed=mc.world.setBlockState(parked,root.state,2);
        require(mc.world.getBlockState(parked)==root.state,"parked reference state installed kind="+kind+" at="+parked+" loaded="+mc.world.isBlockLoaded(parked)+" changed="+installed+" expected="+root.state+" actual="+mc.world.getBlockState(parked));
        if(kind>=3){TileEntity tile=mc.world.getTileEntity(parked);if(tile==null){tile=gear.createTileEntity(mc.world,root.state);mc.world.setTileEntity(parked,tile);}require(tile instanceof TileEntityLandingGear,"parked reference gear tile installed");NBTTagCompound tag=VehicleTileData.translated(root.tileData(),parked.subtract(ROOT));tile.readFromNBT(tag);}
        VehicleWheelMotion.sample(craft,1);
        BufferedImage[] images=new BufferedImage[POSES.length];images[0]=render(mc,craft,kind,true,false);images[1]=render(mc,craft,kind,false,false);
        double[] travel={.29,-.29,.29,.29,.29,.7,1.0};float[] steering={0,0,-1,1,0,1,-1};
        for(int p=2;p<POSES.length;p++){
            craft.hover=p>=7;craft.steering=steering[p-2];craft.setPosition(baseX,baseY,baseZ-travel[p-2]);craft.lastTickPosX=craft.posX;craft.lastTickPosY=craft.posY;craft.lastTickPosZ=craft.posZ;
            VehicleWheelMotion.sample(craft,1);images[p]=render(mc,craft,kind,false,false);
        }
        String name=kind<3?"wheel_"+new String[]{"small","medium","large"}[kind]:"gear_"+BlockTelescopicLandingGear.SIZES[kind-3];
        for(int p=0;p<images.length;p++)require(occupied(images[p])>200,name+" visible in "+POSES[p]);
        require(difference(images[1],images[2],false)>20,name+" forward motion changes actual rendered wheel pixels");
        require(difference(images[2],images[3],false)>20,name+" reverse pose differs from forward");
        require(difference(images[4],images[5],false)>20,name+" left/right steering changes rendered pixels");
        require(difference(images[2],images[6],false)<=4,name+" stopped wheels retain their travel angle");
        require(difference(images[6],images[7],false)<=4&&difference(images[7],images[8],false)<=4,name+" Hover freezes roll and centers steering");
        if(kind<3){
            craft.hover=false;craft.steering=0;int before=VehicleWheelRenderer.draws;
            BufferedImage dedicated=render(mc,craft,kind,false,false),whole=null;for(int warm=0;warm<4;warm++)whole=render(mc,craft,kind,false,true);
            require(VehicleWheelRenderer.draws>before,"full vehicle renderer invokes moving wheel path");
            require(difference(dedicated,whole,true)<100,"moving wheel is not duplicated in static hull mesh: "+name);
        }
        Graphics2D graphics=montage.createGraphics();graphics.setColor(Color.WHITE);
        for(int p=0;p<images.length;p++){graphics.drawImage(images[p],p*PIXELS,kind*(PIXELS+24)+24,null);graphics.drawString(name+" / "+POSES[p],p*PIXELS+5,kind*(PIXELS+24)+17);}graphics.dispose();
        craft.setDead();System.out.println("[vandorlabs][reprolab] wheel-render-variant PASS "+name+" parked/captured forward/reverse left/right stationary Hover texture-bounds");
        if(++variant<8)return false;
        javax.imageio.ImageIO.write(montage,"png",new File(output,"shot_wheel_gear_motion_matrix.png"));
        mc.world.removeTileEntity(parked);mc.world.setBlockState(parked,parkedBefore,2);if(parkedTileBefore!=null){TileEntity restored=TileEntity.create(mc.world,parkedTileBefore);if(restored!=null)mc.world.setTileEntity(parked,restored);}
        RenderGroundVehicle.cleanup(mc.world);
        System.out.println("[vandorlabs][reprolab] wheel-motion-client PASS three-wheel/five-gear sizes signed-distance actual-frame-differences steer stop Hover baked-atlas-UVs finite-bounds no-static-duplicates");return true;
    }
    private WheelMotionRuntimeChecks(){}
}
