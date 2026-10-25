package com.vandorlabs.client;

import com.vandorlabs.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.client.constructors.*;
import com.vandorlabs.container.ContainerTokenSlot;
import com.vandorlabs.drives.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

/** Actual native emitter frames, drive controls and cosmetic particle origins/colors. */
public final class DriveClientChecks {
    private static int checks;
    private static void require(boolean ok,String why){checks++;if(!ok)throw new IllegalStateException("Drive client: "+why);}
    private static TileEntityConstructor tile(Minecraft mc,String design,boolean active,int level){
        FrameMachineDesign spec=FrameMachineDesign.get(design);Block core=Block.getBlockFromName("vandorlabs:"+spec.block);TileEntityConstructor source=new TileEntityConstructor();source.setPos(BlockPos.ORIGIN);NBTTagCompound tag=source.writeToNBT(new NBTTagCompound());tag.setBoolean("NavigationScreenOn",active);tag.setBoolean("NavigationRedstone",true);tag.setInteger("MachineActivity",2);tag.setInteger("DriveLevel",level);DriveSettings.DEFAULT.write(tag);
        VehicleWorld view=new VehicleWorld(mc.world,VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN,core.getDefaultState().withProperty(BlockConstructorCore.FORMED,true),tag))));return (TileEntityConstructor)view.getTileEntity(BlockPos.ORIGIN);
    }
    public static void run(Minecraft mc,File output)throws Exception{
        checks=0;for(String design:new String[]{"longburn_drive","meridian_core","oculus_drive"}){
            TileEntityConstructor on=tile(mc,design,true,15),off=tile(mc,design,false,0);require(on.mount()!=null&&off.mount()!=null,"native data holder is present independently of activation");BufferedImage active=capture(mc,on,20),idle=capture(mc,off,20);require(differences(active,idle)>80,"actual drive has distinct native ON/OFF appearance "+design);javax.imageio.ImageIO.write(active,"png",new File(output,"shot_drive_on_"+design+".png"));
            if(design.equals("longburn_drive")){int offWarm=0;for(int rgb:idle.getRGB(0,0,256,256,null,0,256)){int r=rgb>>16&255,g=rgb>>8&255,b=rgb&255;if(r>45&&g>30&&b<g*.9)offWarm++;}require(offWarm>40,"Maxwell inactive nozzle retains subdued warm artwork pixels="+offWarm);javax.imageio.ImageIO.write(idle,"png",new File(output,"shot_drive_off_"+design+".png"));int warm=0;for(int rgb:active.getRGB(0,0,256,256,null,0,256)){int r=rgb>>16&255,g=rgb>>8&255,b=rgb&255;if(r>170&&g>100&&b<g*.9)warm++;}require(warm>40,"Maxwell nozzle visibly uses warm yellow/white Rocket artwork instead of cyan pixels="+warm);}
            if(design.equals("meridian_core")){BufferedImage low=capture(mc,on,0),high=capture(mc,on,20),repeat=capture(mc,on,20);int changed=differences(low,high),cyan=0;for(int y=0;y<256;y++)for(int x=0;x<256;x++)if(low.getRGB(x,y)!=high.getRGB(x,y)){int rgb=high.getRGB(x,y),r=rgb>>16&255,g=rgb>>8&255,b=rgb&255;if(g>r+25&&b>r+25)cyan++;}require(changed>40&&cyan>30,"Meridian actual front cyan lights pulse while native body stays intact changed="+changed+" cyan="+cyan);require(differences(high,repeat)==0,"same pause-aware visual time does not advance pulse or restart on packets");javax.imageio.ImageIO.write(low,"png",new File(output,"shot_drive_meridian_pulse_low.png"));}
            gui(mc,on,output,design);particles(mc,on,design);
        }
        System.out.println("[vandorlabs][reprolab] drives-client PASS assertions="+checks+" actual native ON/OFF Maxwellwarm Meridianpauseawarecyan Oculusshutters EngineGUI320/460 previous/next noSecurity and world/carried particle anchors/colors");
    }
    private static BufferedImage capture(Minecraft mc,TileEntityConstructor tile,double time){
        Framebuffer frame=new Framebuffer(256,256,true);GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(-2.3,2.3,-2.3,2.3,-10,10);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        try(TurntableRenderState saved=new TurntableRenderState()){
            frame.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.depthMask(true);GlStateManager.clearColor(32/255F,48/255F,64/255F,1);GlStateManager.clear(16640);GlStateManager.enableDepth();GlStateManager.disableLighting();GlStateManager.disableFog();GlStateManager.disableCull();GlStateManager.disableBlend();GlStateManager.enableTexture2D();GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);GlStateManager.color(1,1,1,1);mc.entityRenderer.disableLightmap();if(!tile.core().design.equals("longburn_drive"))GlStateManager.rotate(180,0,1,0);GlStateManager.rotate(8,1,0,0);GlStateManager.translate(-tile.core().width/2D,-tile.core().height/2D,-tile.core().depth/2D);
            TEConstructor renderer=new TEConstructor();renderer.setRendererDispatcher(TileEntityRendererDispatcher.instance);MachineVisualMotion.at(time,()->renderer.render(tile,0,0,0,0,-1,1));GL11.glFinish();return net.minecraft.util.ScreenShotHelper.createScreenshot(256,256,frame);
        }finally{GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
    }
    private static int differences(BufferedImage a,BufferedImage b){int total=0;for(int y=0;y<a.getHeight();y++)for(int x=0;x<a.getWidth();x++)if(a.getRGB(x,y)!=b.getRGB(x,y))total++;return total;}
    private static void gui(Minecraft mc,TileEntityConstructor tile,File output,String design)throws Exception{
        final Minecraft client=mc;net.minecraft.inventory.Container old=mc.player.openContainer;
        try{for(int width:new int[]{320,460}){
            class Probe extends GuiNavigationTable {Probe(){super(new ContainerTokenSlot(client.player.inventory,tile));}List<GuiButton> buttons(){return buttonList;}void click(int id)throws Exception{for(GuiButton b:buttonList)if(b.id==id){actionPerformed(b);return;}throw new IllegalStateException("Missing drive control "+id);}void previous(int id)throws Exception{for(GuiButton b:buttonList)if(b.id==id){mouseClicked(b.x+3,b.y+3,1);return;}throw new IllegalStateException("Missing drive control "+id);}}
            Probe gui=new Probe();gui.inventorySlots.windowId=-91;gui.setWorldAndResolution(mc,width,width==320?240:340);require(gui.buttons().stream().noneMatch(b->b.id==2||b.id==5||b.displayString.contains("Security")),"drive dialog offers no security/company controls");gui.click(20+tile.surfaceCount());if(!tile.driveHasParticles())require(gui.buttons().stream().noneMatch(b->b.id==10||b.id==11),"non-Maxwell Engine has no particle intensity or threshold controls");else{require(gui.buttons().stream().anyMatch(b->b.id==10&&b.displayString.equals("Particles: Light")),"Engine tab exposes Rocket intensity choices");gui.previous(10);require(gui.buttons().stream().anyMatch(b->b.id==10&&b.displayString.equals("Particles: Off")),"right-click selects previous particle value");gui.click(10);}gui.click(9);require(gui.buttons().stream().anyMatch(b->b.id==9&&b.displayString.equals("Brightness: Signal")),"same signal-level brightness mode as Rocket");if(tile.driveHasParticles()){gui.previous(11);require(gui.buttons().stream().anyMatch(b->b.id==11&&b.displayString.endsWith("7")),"right-click selects previous particle threshold");}gui.click(16);require(gui.buttons().stream().anyMatch(b->b.id==16&&b.displayString.equals("Activate on: OFF")),"drive inversion control available");javax.imageio.ImageIO.write(DialogRenderCapture.image(mc,gui,width,width==320?240:340),"png",new File(output,"shot_drive_engine_"+design+"_"+width+".png"));
        }}finally{mc.player.openContainer=old;org.lwjgl.input.Keyboard.enableRepeatEvents(false);}
    }
    private static final class ProbeProxy extends CommonProxy {
        int count,warm,white,cyan;Vec3d point;EnumFacing facing;float yaw;boolean rotated;
        private void record(double x,double y,double z,EnumFacing face,int color,boolean rotate,float rotation){count++;point=new Vec3d(x,y,z);facing=face;rotated=rotate;yaw=rotation;if(color==0xFFFFFF)white++;else if(color==0xFFE29A)warm++;else if(color==0x66EEFF)cyan++;}
        @Override public void spawnThrusterParticle(World w,double x,double y,double z,EnumFacing face,int color,int style,float speed,float spread){record(x,y,z,face,color,false,0);}
        @Override public void spawnRotatedThrusterParticle(World w,double x,double y,double z,EnumFacing face,int color,int style,float speed,float spread,float yaw){record(x,y,z,face,color,true,yaw);}
    }
    private static void particles(Minecraft mc,TileEntityConstructor template,String design){
        CommonProxy old=VandorLabs.proxy;ProbeProxy probe=new ProbeProxy();BlockPos pos=new BlockPos(mc.player.posX+4,Math.min(220,mc.player.posY+30),mc.player.posZ);EntityGroundVehicle craft=null;boolean fixture=false;
        try{VandorLabs.proxy=probe;require(mc.world.isAirBlock(pos),"isolated world particle fixture has empty cell");require(mc.world.setBlockState(pos,template.getWorld().getBlockState(template.getPos()),2),"loaded isolated particle fixture installs");fixture=true;TileEntityConstructor placed=(TileEntityConstructor)mc.world.getTileEntity(pos);NBTTagCompound saved=template.writeToNBT(new NBTTagCompound());saved.setInteger("x",pos.getX());saved.setInteger("y",pos.getY());saved.setInteger("z",pos.getZ());saved.setInteger("DriveParticles",!design.equals("longburn_drive")?3:1);placed.readFromNBT(saved);placed.update();require(probe.count==(design.equals("longburn_drive")?8:0)&&!probe.rotated,"placed drive particle policy including legacy Heavy non-Maxwell NBT");if(!design.equals("longburn_drive"))require(placed.driveSettings().particles==0&&placed.writeToNBT(new NBTTagCompound()).getInteger("DriveParticles")==0,"legacy non-Maxwell particles normalize to Off on save");if(design.equals("longburn_drive"))require(probe.warm>0&&probe.white>0&&probe.cyan==0,"Maxwell cosmetic particles are yellow/white");
            probe.count=0;NBTTagCompound legacy=template.writeToNBT(new NBTTagCompound());legacy.setInteger("DriveParticles",!design.equals("longburn_drive")?3:1);VehicleStructure shape=DriveRuntimeChecks.withCrew(Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN,template.getWorld().getBlockState(template.getPos()),legacy)),EnumFacing.NORTH);craft=new EntityGroundVehicle(mc.world);craft.install(shape);craft.setPosition(pos.getX()+20,pos.getY()+10,pos.getZ());craft.rotationYaw=90;TileEntityConstructor carried=(TileEntityConstructor)craft.view.getTileEntity(BlockPos.ORIGIN);carried.update();if(!design.equals("longburn_drive")){require(probe.count==0&&carried.driveSettings().particles==0,"legacy carried non-Maxwell Heavy settings never emit particles");return;}require(probe.count==8&&probe.rotated&&probe.yaw==90,"carried drive uses parent world and rotated cosmetic output without functional thrust");Vec3d expected=craft.toWorld(DriveParticles.center(carried));require(expected.squareDistanceTo(probe.point)<1e-12&&probe.facing==DriveParticles.direction(carried),"emission center and native facing follow actual craft pose");NBTTagCompound off=carried.writeToNBT(new NBTTagCompound());off.setBoolean("NavigationScreenOn",false);carried.readFromNBT(off);probe.count=0;carried.update();require(probe.count==0,"inactive carried drive emits no cosmetic particles");
        }finally{VandorLabs.proxy=old;if(craft!=null){craft.signals.close();craft.setDead();}if(fixture)mc.world.setBlockToAir(pos);}
    }
    private DriveClientChecks(){}
}
