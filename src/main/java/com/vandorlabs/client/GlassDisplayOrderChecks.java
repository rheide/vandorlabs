package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.ForgeHooksClient;
import org.lwjgl.opengl.GL11;

/** Reported joined Normal screen in front of Strong solid-material glass, opposite tile orders. */
final class GlassDisplayOrderChecks {
    static final double CAMERA_X=12.438,CAMERA_Z=15.623;
    private static final BlockPos ROOT=new BlockPos(3,3,3);private static final int SIZE=320;
    private static VehicleWorld fixture(Minecraft mc,int width,boolean framed){
        List<VehicleStructure.Cell> cells=new ArrayList<>();cells.add(new VehicleStructure.Cell(BlockPos.ORIGIN,ModBlocks.PILOT_SEAT.getDefaultState(),null));
        IBlockState state=Block.getBlockFromName("vandorlabs:programmable_glass").getDefaultState();
        for(int layer=0;layer<2;layer++)for(int x=0;x<width;x++)for(int y=0;y<2;y++){
            BlockPos at=ROOT.add(x,y,layer*2);TileEntityProgrammableGlass tile=new TileEntityProgrammableGlass();tile.setPos(at);tile.setAppearance(layer==0?1:2,framed);tile.setTextureLayout(0);tile.setJoin(true);
            tile.setMaterial(layer==0?ScreenHousingTextures.screenIndex("company_paladyne_full_biology_static"):CustomBlockMaterials.choice(new ItemStack(Blocks.IRON_BLOCK)));
            cells.add(new VehicleStructure.Cell(at,state,tile.writeToNBT(new NBTTagCompound())));
        }
        return new VehicleWorld(mc.world,new VehicleStructure(cells,BlockPos.ORIGIN,EnumFacing.NORTH,1));
    }
    private static void layer(VehicleWorld world,int layer,int pass){
        for(VehicleStructure.Cell cell:world.structure.cells)if(cell.pos.getZ()==ROOT.getZ()+layer*2){TileEntity tile=world.getTileEntity(cell.pos);if(tile!=null&&tile.shouldRenderInPass(pass))TileEntityRendererDispatcher.instance.render(tile,cell.pos.getX(),cell.pos.getY(),cell.pos.getZ(),1);}
    }
    /** mode 0 is the explicit rear-then-front reference; 1/2 are opposite Forge tile orders. */
    private static BufferedImage draw(Minecraft mc,VehicleWorld world,int width,boolean right,int mode){
        int previousPass=net.minecraftforge.client.MinecraftForgeClient.getRenderPass();TileEntityRendererDispatcher d=TileEntityRendererDispatcher.instance;net.minecraft.world.World old=d.world;double ox=d.entityX,oy=d.entityY,oz=d.entityZ;
        int oldFbo=GL11.glGetInteger(org.lwjgl.opengl.EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT);TurntableRenderState saved=new TurntableRenderState();
        Framebuffer fbo=new Framebuffer(SIZE,SIZE,true);GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(-width*.7,width*.7,-width*.7,width*.7,-30,30);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        try{
            if(mode>0)GlazingRenderOrder.begin(world);
            fbo.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.depthMask(true);GlStateManager.clearColor(.1F,.16F,.23F,1);GlStateManager.clear(16640);GlStateManager.enableDepth();GlStateManager.disableLighting();GlStateManager.disableFog();GlStateManager.disableCull();GlStateManager.enableTexture2D();GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);GlStateManager.color(1,1,1,1);mc.entityRenderer.disableLightmap();
            GlStateManager.rotate(8,1,0,0);GlStateManager.rotate(right?162:198,0,1,0);GlStateManager.translate(-ROOT.getX()-width/2D,-ROOT.getY()-1,-ROOT.getZ()-.5);
            d.world=world;d.entityX=ROOT.getX()+width/2D+(right?8:-8);d.entityY=ROOT.getY()+2;d.entityZ=ROOT.getZ()-12;
            for(int pass=0;pass<2;pass++){
                ForgeHooksClient.setRenderPass(pass);
                if(mode==1){layer(world,0,pass);layer(world,1,pass);}else{layer(world,1,pass);layer(world,0,pass);}
            }
            GL11.glFinish();return ScreenShotHelper.createScreenshot(SIZE,SIZE,fbo);
        }finally{GlazingRenderOrder.end();ForgeHooksClient.setRenderPass(previousPass);d.world=old;d.entityX=ox;d.entityY=oy;d.entityZ=oz;GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER,oldFbo);saved.close();fbo.deleteFramebuffer();}
    }
    private static void animation(Minecraft mc,File output)throws Exception{
        long time=mc.world.getTotalWorldTime();VehicleWorld world=fixture(mc,3,false);
        try{
            for(VehicleStructure.Cell cell:world.structure.cells)if(cell.pos.getZ()==ROOT.getZ())((TileEntityProgrammableGlass)world.getTileEntity(cell.pos)).glassPlayback.set(2,1);
            BufferedImage[] frames=new BufferedImage[2];
            for(int phase=0;phase<2;phase++){mc.world.setTotalWorldTime(phase*12);final int sample=phase;CockpitVisualMotion.authoredFrame(()->frames[sample]=draw(mc,world,3,false,1));ImageIO.write(frames[phase],"png",new File(output,"glass-display-order_animated_phase_"+phase+".png"));}
            if(difference(frames[0],frames[1])<50)throw new IllegalStateException("Glass Display did not visibly animate");
            for(VehicleStructure.Cell cell:world.structure.cells)if(cell.pos.getZ()==ROOT.getZ())((TileEntityProgrammableGlass)world.getTileEntity(cell.pos)).glassPlayback.set(0,1);
            BufferedImage off=draw(mc,world,3,false,1);ImageIO.write(off,"png",new File(output,"glass-display-order_off.png"));if(difference(frames[0],off)<500)throw new IllegalStateException("Glass Display OFF did not change artwork");
            System.out.println("[vandorlabs][reprolab] glass-display-playback PASS joined-real-render static-mesh-remap animated-phases OFF same-frame-clock");
        }finally{mc.world.setTotalWorldTime(time);}
    }
    private static int difference(BufferedImage a,BufferedImage b){int n=0;for(int y=0;y<a.getHeight();y++)for(int x=0;x<a.getWidth();x++){int p=a.getRGB(x,y),q=b.getRGB(x,y);if(Math.abs((p>>16&255)-(q>>16&255))>2||Math.abs((p>>8&255)-(q>>8&255))>2||Math.abs((p&255)-(q&255))>2)n++;}return n;}
    static VehicleWorld perpendicular(Minecraft mc,boolean canopy)throws Exception{
        java.nio.file.Path dir=java.nio.file.Paths.get(System.getProperty("vandorlabs.legacyVehicleFixture","../fixtures/legacy-vehicle-controller-2.6.nbt")).getParent();
        NBTTagCompound saved;try(java.io.InputStream in=java.nio.file.Files.newInputStream(dir.resolve("glass-perpendicular-2.7.nbt"))){saved=net.minecraft.nbt.CompressedStreamTools.readCompressed(in);}
        List<VehicleStructure.Cell> cells=new ArrayList<>();net.minecraft.nbt.NBTTagList list=saved.getTagList("Cells",10);
        for(int i=0;i<list.tagCount();i++){NBTTagCompound c=list.getCompoundTagAt(i);BlockPos pos=new BlockPos(c.getInteger("x"),c.getInteger("y"),c.getInteger("z"));Block block=Block.getBlockFromName(c.getString("Block"));if(canopy&&pos.getZ()!=14)continue;cells.add(new VehicleStructure.Cell(pos,block.getStateFromMeta(c.getInteger("Meta")),c.getCompoundTag("Tile")));}
        if(canopy){BlockPos pos=new BlockPos(4,3,10);TileEntityCanopy tile=new TileEntityCanopy();tile.setPos(pos);tile.anchor=pos;tile.large.depth=16;tile.large.scale=71;tile.large.shade=14;tile.large.tint=1;NBTTagCompound tag=tile.writeToNBT(new NBTTagCompound());tile.large.write(tag);cells.add(new VehicleStructure.Cell(pos,Block.getBlockFromName("vandorlabs:front_canopy_glass").getDefaultState().withProperty(BlockCanopy.FACING,EnumFacing.NORTH),tag));}
        return new VehicleWorld(mc.world,VehicleStructure.display(cells));
    }
    private static BufferedImage perpendicularDraw(Minecraft mc,VehicleWorld world,float yaw,int mode){
        int passBefore=net.minecraftforge.client.MinecraftForgeClient.getRenderPass(),fboBefore=GL11.glGetInteger(org.lwjgl.opengl.EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT);
        TileEntityRendererDispatcher d=TileEntityRendererDispatcher.instance;net.minecraft.world.World before=d.world;double x=d.entityX,y=d.entityY,z=d.entityZ;TurntableRenderState state=new TurntableRenderState();Framebuffer fbo=new Framebuffer(640,400,true);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();org.lwjgl.util.glu.GLU.gluPerspective(95,1.6F,.1F,80);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        try{
            if(mode==1||mode==2)GlazingRenderOrder.begin(world);fbo.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.depthMask(true);GlStateManager.clearColor(.1F,.16F,.23F,1);GlStateManager.clear(16640);GlStateManager.enableDepth();GlStateManager.disableLighting();GlStateManager.disableFog();GlStateManager.enableTexture2D();GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);GlStateManager.color(1,1,1,1);mc.entityRenderer.disableLightmap();
            d.world=world;d.entityX=CAMERA_X;d.entityY=3;d.entityZ=CAMERA_Z;GlStateManager.rotate(yaw+180,0,1,0);GlStateManager.translate(-CAMERA_X,-4.62,-CAMERA_Z);
            List<VehicleStructure.Cell> cells=new ArrayList<>(world.structure.cells);cells.sort((a,b)->{int plane=Integer.compare(a.pos.getZ()==14?1:0,b.pos.getZ()==14?1:0);
                if(plane!=0)return plane;
                if(a.pos.getZ()==14&&b.pos.getZ()==14){int depth=Integer.compare(PanelDepth.offset(a.state.getValue(BlockGlassWall.DEPTH)),PanelDepth.offset(b.state.getValue(BlockGlassWall.DEPTH)));if(depth!=0)return depth;}
                return a.pos.compareTo(b.pos);});if(mode==2)Collections.reverse(cells);
            for(int pass=0;pass<(mode==0?1:2);pass++){ForgeHooksClient.setRenderPass(pass);for(VehicleStructure.Cell c:cells){if(mode==3&&c.pos.getZ()==14||mode==4&&c.pos.getZ()!=14)continue;TileEntity tile=world.getTileEntity(c.pos);if(tile.shouldRenderInPass(pass))d.render(tile,c.pos.getX(),c.pos.getY(),c.pos.getZ(),1);}}
            if(mode==0){
                // Known separated layers: independently order each layer, explicitly
                // finish the rear layer before allowing any foreground pane to draw.
                // This does not assume arbitrary tile order is correct at panel seams.
                for(int layer=0;layer<2;layer++){
                    GlazingRenderOrder.begin(world);ForgeHooksClient.setRenderPass(0);
                    for(VehicleStructure.Cell c:cells)if((c.pos.getZ()==14)==(layer==1)){
                        TileEntity tile=world.getTileEntity(c.pos);GlazingRenderOrder.handle(tile,c.pos.getX(),c.pos.getY(),c.pos.getZ(),1,-1,1);
                    }
                    ForgeHooksClient.setRenderPass(1);
                    for(VehicleStructure.Cell c:cells)if((c.pos.getZ()==14)==(layer==1)){
                        TileEntity tile=world.getTileEntity(c.pos);if(tile.shouldRenderInPass(1))d.render(tile,c.pos.getX(),c.pos.getY(),c.pos.getZ(),1);
                    }
                    GlazingRenderOrder.end();
                }
            }
            GL11.glFinish();return ScreenShotHelper.createScreenshot(640,400,fbo);
        }finally{GlazingRenderOrder.end();ForgeHooksClient.setRenderPass(passBefore);d.world=before;d.entityX=x;d.entityY=y;d.entityZ=z;GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER,fboBefore);state.close();fbo.deleteFramebuffer();}
    }
    private static void perpendicularCheck(Minecraft mc,File output)throws Exception{
        Map<String,BufferedImage> uncluttered=new HashMap<>();
        for(boolean clutter:new boolean[]{false,true})for(boolean canopy:new boolean[]{false,true}){VehicleWorld world=perpendicular(mc,canopy);
        if(clutter){
            List<VehicleStructure.Cell> cells=new ArrayList<>(world.structure.cells);
            for(int i=0;i<129;i++){
                BlockPos pos=i<90?new BlockPos(30+i,100,30):new BlockPos(120,100,30+i-90);TileEntityProgrammableGlass tile=new TileEntityProgrammableGlass();tile.setPos(pos);
                cells.add(new VehicleStructure.Cell(pos,Block.getBlockFromName("vandorlabs:programmable_glass").getDefaultState().withProperty(BlockGlassWall.ROTATED,i<90),tile.writeToNBT(new NBTTagCompound())));
            }
            world=new VehicleWorld(mc.world,VehicleStructure.display(cells));
        }
        BufferedImage rear=perpendicularDraw(mc,world,110,3),front=perpendicularDraw(mc,world,110,4),both=perpendicularDraw(mc,world,110,0);
        ImageIO.write(rear,"png",new File(output,"glass-display-order_rear_only_"+canopy+".png"));System.out.println("[vandorlabs] glass rear-layer canopy="+canopy+" visible-composite="+difference(front,both));
        for(float yaw:new float[]{90.5F,89.9F,90,90.1F,94,85,100,110,125,140}){
            BufferedImage ref=perpendicularDraw(mc,world,yaw,0),forward=perpendicularDraw(mc,world,yaw,1),reverse=perpendicularDraw(mc,world,yaw,2);
            ImageIO.write(ref,"png",new File(output,"glass-display-order_perpendicular_"+clutter+"_"+canopy+"_"+yaw+"_reference.png"));ImageIO.write(forward,"png",new File(output,"glass-display-order_perpendicular_"+clutter+"_"+canopy+"_"+yaw+"_sorted.png"));
            String key=canopy+":"+yaw;
            if(!clutter)uncluttered.put(key,ref);
            else if(difference(uncluttered.get(key),ref)>16||difference(uncluttered.get(key),forward)>16)throw new IllegalStateException("Off-camera glazing changed visible composition "+key);
            int a=difference(ref,forward),b=difference(ref,reverse);System.out.println("[vandorlabs] glass-perpendicular clutter="+clutter+" canopy="+canopy+" yaw="+yaw+" forward="+a+" reverse="+b);if(a>16||b>16)throw new IllegalStateException("Finite glass planes yaw="+yaw+" mismatch="+a+"/"+b);
        }}
    }
    static void run(Minecraft mc,File output)throws Exception{
        for(int width:new int[]{3,5})for(boolean framed:new boolean[]{false,true})for(boolean right:new boolean[]{false,true}){
            VehicleWorld world=fixture(mc,width,framed);BufferedImage reference=draw(mc,world,width,right,0),forward=draw(mc,world,width,right,1),reverse=draw(mc,world,width,right,2);String name="w"+width+"_"+framed+"_"+right;
            ImageIO.write(reference,"png",new File(output,"glass-display-order_"+name+"_reference.png"));ImageIO.write(forward,"png",new File(output,"glass-display-order_"+name+"_forward.png"));ImageIO.write(reverse,"png",new File(output,"glass-display-order_"+name+"_reverse.png"));
            int changed=difference(reference,forward);System.out.println("[vandorlabs][reprolab] glass-display-order case="+name+" forward-mismatch="+changed+" reverse-mismatch="+difference(reference,reverse));
            if(changed>16||difference(reference,reverse)>16)throw new IllegalStateException("Glass screen behind-glass order differs: "+name+" pixels="+changed);
        }
        animation(mc,output);
        perpendicularCheck(mc,output);
        System.out.println("[vandorlabs][reprolab] glass-display-order PASS joined-Normal-screen Strong-solid-background widths=3,5 framed-bare left-right opposite-tile-orders");
    }
}
