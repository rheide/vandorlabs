package com.vandorlabs.client;

import com.vandorlabs.container.ContainerCockpit;
import com.vandorlabs.tiles.TileEntityCockpit;
import com.vandorlabs.cockpit.CockpitData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.util.math.*;
import java.io.File;
import java.lang.reflect.Field;

/** Real dialog initialization and left/right cycling on the physical throttle page. */
final class CockpitControlDialogChecks {
    static void run(Minecraft mc,File output)throws Exception{
        TileEntityCockpit tile=new TileEntityCockpit();tile.setWorld(mc.world);tile.setPos(new BlockPos(2048,128,2048));
        ContainerCockpit container=new ContainerCockpit(tile,null);container.windowId=-77;
        Field page=GuiCockpit.class.getDeclaredField("page");page.setAccessible(true);
        Field buttons;try{buttons=GuiScreen.class.getDeclaredField("buttonList");}catch(NoSuchFieldException e){buttons=GuiScreen.class.getDeclaredField("field_146292_n");}buttons.setAccessible(true);
        Vec3d original=mc.player.getPositionVector();float yaw=mc.player.rotationYaw,pitch=mc.player.rotationPitch;GuiScreen previous=mc.currentScreen;
        try{
            for(int i=0;i<tile.data().surfaces.size();i++){
                CockpitData.Surface surface=tile.data().surfaces.get(i);Vec3d target=surface.point(.5,.5).add(new Vec3d(tile.getPos())),eye=target.add(surface.normal.scale(.1));
                look(mc,eye,target);GuiCockpit gui=new GuiCockpit(container);require(page.getInt(gui)==3,"new dialog starts on leftmost Screen tab "+surface.id);
            }
            for(java.util.Map.Entry<String,Vec3d> part:tile.data().pivots.entrySet())if(part.getKey().startsWith("throttle_")){
                Vec3d eye=new Vec3d(tile.data().seat.x,tile.data().riderY+mc.player.getEyeHeight(),tile.data().seat.z),target=part.getValue().addVector(0,Math.cos(Math.toRadians(22.5))*4.5/16,Math.sin(Math.toRadians(22.5))*4.5/16);
                look(mc,eye.add(new Vec3d(tile.getPos())),target.add(new Vec3d(tile.getPos())));GuiCockpit gui=new GuiCockpit(container);
                require(page.getInt(gui)==3,"new dialog starts on leftmost Screen tab "+part.getKey());
            }
            TileEntityCockpit.Control throttle=tile.control("throttle_left");int index=tile.controls.indexOf(throttle);
            for(int[] size:new int[][]{{854,480},{480,270}}){
                GuiCockpit gui=new GuiCockpit(container);page.setInt(gui,2+tile.displays.size()+index);gui.setWorldAndResolution(mc,size[0],size[1]);
                java.util.List<GuiButton> list=(java.util.List<GuiButton>)buttons.get(gui);GuiButton low=null,high=null;
                for(GuiButton b:list){require(b.id!=40,"physical throttle must not offer Toggle/Pulse");if(b.id==42)low=b;if(b.id==43)high=b;require(b.x>=0&&b.y>=0&&b.x+b.width<=size[0]&&b.y+b.height<=size[1],"throttle dialog bounds");}
                require(low!=null&&high!=null,"Thruster Lever low/high controls");
                gui.mouseClicked(low.x+2,low.y+2,0);require(throttle.min==6,"left click increases low limit");gui.mouseClicked(low.x+2,low.y+2,1);require(throttle.min==5,"right click decreases low limit");
                gui.drawScreen(0,0,0);javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"cockpit-throttle-dialog-"+size[0]+".png"));
            }
            DocumentationRenderCapture.capture(output,"cockpit-throttle-layout",2,2.15,28,-28,1,.65,.1,frame->{tile.setThrottleCount(frame==0?1:2);com.vandorlabs.client.cockpits.CockpitRenderer.renderAnimated(tile.modelId(),0,0,0,net.minecraft.util.EnumFacing.NORTH,0x00f000f0,id->tile.visualDetent(id,0),tile.sideTexture(),tile.getHeight());});
        }finally{mc.player.setPosition(original.x,original.y,original.z);mc.player.rotationYaw=yaw;mc.player.rotationPitch=pitch;mc.displayGuiScreen(previous);}
        System.out.println("[vandorlabs][reprolab] cockpit-control-dialog PASS leftmost initial Screen tab, four-step limits, left/right cycling and single/twin model captures");
    }
    private static void look(Minecraft mc,Vec3d eye,Vec3d target){mc.player.setPosition(eye.x,eye.y-mc.player.getEyeHeight(),eye.z);Vec3d d=target.subtract(eye);mc.player.rotationYaw=(float)Math.toDegrees(Math.atan2(-d.x,d.z));mc.player.rotationPitch=(float)-Math.toDegrees(Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z)));}
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("Cockpit control dialog: "+message);}
}
