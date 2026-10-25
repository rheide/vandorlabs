package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.client.constructors.TEConstructor;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.tiles.TileEntityConstructor;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import org.lwjgl.opengl.GL11;
import java.awt.image.BufferedImage;
import java.io.File;

/** Pixel evidence for native reader retention and authored texture planes. */
final class MachineDisplayVisualChecks {
    private static final int SIZE=128;
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("Machine pixels: "+why);}
    static void run(Minecraft mc,File output)throws Exception{
        BlockPos pos=new BlockPos(mc.player.posX+2,90,mc.player.posZ+2);int readers=0,surfaces=0;
        try{for(FrameMachineDesign spec:FrameMachineDesign.all())if(spec.hasChip()||spec.surfaces.length>0)for(EnumFacing facing:EnumFacing.HORIZONTALS){
            BlockConstructorCore block=(BlockConstructorCore)Block.getBlockFromName("vandorlabs:"+spec.block);mc.world.setBlockState(pos,block.getDefaultState().withProperty(BlockVandorDirectional.FACING,facing).withProperty(BlockConstructorCore.FORMED,true),2);TileEntityConstructor tile=(TileEntityConstructor)mc.world.getTileEntity(pos);
            NBTTagCompound tag=tile.writeToNBT(new NBTTagCompound());tag.setBoolean("NavigationSecurity",spec.hasChip());tag.setBoolean("NavigationScreenOn",false);tag.setLong("TokenInsertStarted",mc.world.getTotalWorldTime());tag.setLong("TokenInsertionSequence",1);tile.readFromNBT(tag);
            if(spec.hasChip()){
                MachineMountSurface mount=tile.mount();BufferedImage empty=reader(mc,tile,mount,0,false);
                tag.setTag("InsertedToken",new ItemStack(ModItems.DATA_CHIP,1,4).writeToNBT(new NBTTagCompound()));tag.setBoolean("NavigationScreenOn",true);tile.readFromNBT(tag);
                BufferedImage inserting=reader(mc,tile,mount,0,false),inserted=reader(mc,tile,mount,8,false),retained=reader(mc,tile,mount,40,false),reference=reader(mc,tile,mount,40,true);
                require(differences(inserting,inserted,0,SIZE)>100,"native insertion moves into holder "+spec.design+facing);
                require(differences(empty,inserted,52,76)>30,"chip remains visible in holder "+spec.design+facing);
                if(differences(inserted,retained,0,SIZE)!=0){
                    javax.imageio.ImageIO.write(inserted,"png",new File(output,"reader-before-"+spec.design+"-"+facing+".png"));
                    javax.imageio.ImageIO.write(retained,"png",new File(output,"reader-retained-"+spec.design+"-"+facing+".png"));
                }
                require(differences(inserted,retained,0,SIZE)==0,"retained chip stays after animation "+spec.design+facing);
                require(differences(inserted,reference,52,76)==0,"same native mount contents and proportions "+spec.design+facing);
                tag.setBoolean("NavigationSecurity",false);tile.readFromNBT(tag);require(tile.mount()!=null&&!tile.isEmpty()&&!tile.screenSettings().security,"legacy Security false leaves visible data holder and retained chip");
                BufferedImage dataOnly=reader(mc,tile,mount,40,false);require(differences(dataOnly,inserted,0,SIZE)==0,"legacy Security false cannot hide or change native data holder "+spec.design+facing);
                tag.setBoolean("NavigationSecurity",true);tile.readFromNBT(tag);require(differences(inserted,reader(mc,tile,mount,40,false),0,SIZE)==0,"legacy Security true is ignored without changing retained holder");
                if(facing==EnumFacing.NORTH){javax.imageio.ImageIO.write(inserted,"png",new File(output,"machine_reader_"+spec.design+".png"));javax.imageio.ImageIO.write(dataOnly,"png",new File(output,"machine_reader_data_only_"+spec.design+".png"));}readers++;
            }
            int[] choices=new int[spec.surfaces.length];java.util.Arrays.fill(choices,-1);
            for(int slot=0;slot<choices.length;slot++){
                choices[slot]=-1;tag.setIntArray("MachineSurfaceTextures",choices);tag.setBoolean("NavigationScreenOn",true);tile.readFromNBT(tag);BufferedImage off=surface(mc,tile,slot);
                choices[slot]=NavigationRuntimeChecks.artwork();tag.setIntArray("MachineSurfaceTextures",choices);tile.readFromNBT(tag);BufferedImage on=surface(mc,tile,slot);
                tag.setBoolean("NavigationScreenOn",false);tile.readFromNBT(tag);BufferedImage inactive=surface(mc,tile,slot);require(differences(off,inactive,40,88)==0,"inactive surface stays black inside actual model");tag.setBoolean("NavigationScreenOn",true);tile.readFromNBT(tag);require(differences(off,on,10,118)>500,"selected artwork on authored "+spec.design+spec.surfaces[slot].id+facing);
                if(facing==EnumFacing.NORTH)javax.imageio.ImageIO.write(on,"png",new File(output,"machine_surface_"+spec.design+"_"+slot+".png"));choices[slot]=-1;surfaces++;
            }
        }}finally{mc.world.setBlockToAir(pos);}
        System.out.println("[vandorlabs] machine-display-pixels PASS reader-poses="+readers+" surfaces="+surfaces+" native insertion retained data chip always-visible mount legacy-security-ignored exact authored screen/keyboard planes four facings");
    }
    private static int differences(BufferedImage a,BufferedImage b,int min,int max){int count=0;for(int x=min;x<max;x++)for(int y=min;y<max;y++)if(a.getRGB(x,y)!=b.getRGB(x,y))count++;return count;}
    private static BufferedImage reader(Minecraft mc,TileEntityConstructor tile,MachineMountSurface mount,double age,boolean reference)throws Exception{
        Vec3d center=mount.center.subtract(new Vec3d(tile.getPos()));return capture(mc,.45,.45,()->{
            int turn=com.vandorlabs.navigation.NavigationLayout.Facing.valueOf(tile.facing().name()).modelYRotation;GlStateManager.rotate(180+turn,0,1,0);
            com.vandorlabs.client.constructors.MachineVisualMotion.at(0,()->TokenSlotVisualMotion.at(age,()->{
                if(reference)TETokenSlot.nativeMount(tile,mount,-mount.center.x,-mount.center.y,-mount.center.z,0);
                else{GlStateManager.translate(-center.x,-center.y,-center.z);TEConstructor renderer=(TEConstructor)net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher.instance.<TileEntityConstructor>getRenderer(tile);renderer.render(tile,0,0,0,0,-1,1);}
            }));
        });
    }
    private static BufferedImage surface(Minecraft mc,TileEntityConstructor tile,int slot)throws Exception{
        FrameMachineDesign spec=tile.core().specification();FrameMachineDesign.Surface surface=spec.surfaces[slot];Vec3d[] p=new Vec3d[4];
        for(int i=0;i<4;i++){double[] point=surface.corners[i];p[i]=MachineMountSurface.rotate(new Vec3d(point[0],point[1],point[2]),tile.facing(),spec.width,spec.depth).subtract(new Vec3d(tile.pivot()));}
        Vec3d center=p[0].add(p[2]).scale(.5),u=p[1].subtract(p[0]).normalize(),v=p[3].subtract(p[0]).normalize(),n=u.crossProduct(v).normalize();
        java.nio.FloatBuffer matrix=org.lwjgl.BufferUtils.createFloatBuffer(16);matrix.put(new float[]{(float)u.x,(float)v.x,(float)n.x,0,(float)u.y,(float)v.y,(float)n.y,0,(float)u.z,(float)v.z,(float)n.z,0,0,0,0,1}).flip();
        return capture(mc,p[1].distanceTo(p[0])*.6,p[3].distanceTo(p[0])*.6,()->{GlStateManager.multMatrix(matrix);GlStateManager.translate(-center.x,-center.y,-center.z);TEConstructor renderer=(TEConstructor)net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher.instance.<TileEntityConstructor>getRenderer(tile);com.vandorlabs.client.constructors.MachineVisualMotion.at(0,()->renderer.render(tile,0,0,0,0,-1,1));});
    }
    private static BufferedImage capture(Minecraft mc,double halfWidth,double halfHeight,Runnable render)throws Exception{
        Framebuffer frame=new Framebuffer(SIZE,SIZE,true);TurntableRenderState saved=new TurntableRenderState();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(-halfWidth,halfWidth,-halfHeight,halfHeight,-16,16);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();GlStateManager.loadIdentity();
        try{frame.bindFramebuffer(true);GlStateManager.clearColor(.12F,.16F,.2F,1);GlStateManager.depthMask(true);GlStateManager.clear(16640);GlStateManager.enableDepth();GlStateManager.disableFog();GlStateManager.disableLighting();render.run();return net.minecraft.util.ScreenShotHelper.createScreenshot(SIZE,SIZE,frame);}
        finally{GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);saved.close();frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);GlStateManager.depthMask(true);GlStateManager.color(1,1,1,1);}
    }
}
