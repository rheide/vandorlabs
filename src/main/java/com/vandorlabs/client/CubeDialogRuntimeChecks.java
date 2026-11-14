package com.vandorlabs.client;

import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import com.vandorlabs.ui.ProgrammableBlockDialog;
import com.vandorlabs.ui.ProgrammableDialogLayout;
import java.io.File;
import java.lang.reflect.Field;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

/** Actual cube widgets, reverse option clicks and face packets in the material focus. */
final class CubeDialogRuntimeChecks {
    private static int stage,originalLayout,originalMain;
    private CubeDialogRuntimeChecks() { }
    static boolean tick(Minecraft mc,File output,BlockPos pos)throws Exception{
        require(mc.currentScreen instanceof GuiProgrammableWall,"cube editor disappeared stage="+stage);
        GuiProgrammableWall gui=(GuiProgrammableWall)mc.currentScreen;
        TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)mc.world.getTileEntity(pos);
        if(stage==0){
            originalLayout=tile.getMainTextureLayout();originalMain=tile.getHousingTexture();
            layout(mc,gui,tile);scroll(gui);click(gui,103,1);
        }else if(stage==1){check(mc,pos,false,-1,-1,Math.floorMod(originalLayout-1,3));click(gui,103,0);
        }else if(stage==2){check(mc,pos,false,-1,-1,originalLayout);click(gui,106,0);
        }else if(stage==3){check(mc,pos,true,-1,-1,originalLayout);click(gui,120,0);layout(mc,gui,tile);captureScales(mc,gui,output);click(gui,123,0);pick(gui,3);
        }else if(stage==4){check(mc,pos,true,2,3,originalLayout);click(gui,108,0);
        }else if(stage==5){check(mc,pos,true,2,-1,originalLayout);click(gui,124,0);pick(gui,4);
        }else if(stage==6){check(mc,pos,true,3,4,originalLayout);click(gui,106,1);
        }else if(stage==7){check(mc,pos,false,3,4,originalLayout);require(tile.getHousingTexture()==originalMain,"face editing changed main");click(gui,240,0);
        }else if(stage==8){joinCheck(mc,pos,2,0);click(gui,241,0);
        }else if(stage==9){joinCheck(mc,pos,2,1);click(gui,240,1);
        }else if(stage==10){joinCheck(mc,pos,1,1);require(!button(gui,241).enabled,"On disables candidate filter without discarding it");click(gui,240,1);
        }else if(stage==11){joinCheck(mc,pos,0,1);click(gui,240,0);click(gui,240,0);click(gui,241,1);
        }else if(stage==12){joinCheck(mc,pos,2,0);click(gui,240,1);
        }else if(stage==13){joinCheck(mc,pos,1,0);com.vandorlabs.network.MessageTextureJoin malformed=new com.vandorlabs.network.MessageTextureJoin();io.netty.buffer.ByteBuf bytes=io.netty.buffer.Unpooled.buffer();try{bytes.writeLong(pos.toLong());bytes.writeByte(255);bytes.writeByte(0);malformed.fromBytes(bytes);com.vandorlabs.network.PacketHandler.INSTANCE.sendToServer(malformed);}finally{bytes.release();}
        }else if(stage==14){joinCheck(mc,pos,1,0);layout(mc,gui,tile);TextureJoinDialogRuntimeChecks.run(mc,output);System.out.println("[vandorlabs][reprolab] cube-dialog-live PASS current Appearance row compact320 regular layout seven face tabs real left/right option clicks wheel/drag face/inherit packets stored disabled overrides NBT reload; directional join/parity C2S/S2C reverse cycling latent filter and malformed refusal");return true;}
        stage++;return false;
    }
    private static void joinCheck(Minecraft mc,BlockPos pos,int direction,int parity)throws Exception{
        mc.getIntegratedServer().addScheduledTask(()->{
            TileEntityAnimatedScreenSelector host=(TileEntityAnimatedScreenSelector)mc.getIntegratedServer().getWorld(0).getTileEntity(pos);
            require(host.textureJoin().direction==direction&&host.textureJoin().parity==parity,"server join stage="+stage+" expected="+direction+"/"+parity+" actual="+host.textureJoin().direction+"/"+host.textureJoin().parity);
            TileEntityAnimatedScreenSelector reloaded=new TileEntityAnimatedScreenSelector();reloaded.readFromNBT(host.writeToNBT(new NBTTagCompound()));require(reloaded.textureJoin().direction==direction&&reloaded.textureJoin().parity==parity,"saved join stage="+stage);
        }).get();
        TileEntityAnimatedScreenSelector host=(TileEntityAnimatedScreenSelector)mc.world.getTileEntity(pos);require(host.textureJoin().direction==direction&&host.textureJoin().parity==parity,"client join stage="+stage);
    }
    private static void layout(Minecraft mc,GuiProgrammableWall gui,TileEntityAnimatedScreenSelector tile)throws Exception{
        ScaledResolution resolution=new ScaledResolution(mc);int actualWidth=resolution.getScaledWidth(),actualHeight=resolution.getScaledHeight();
        int oldScale=mc.gameSettings.guiScale;mc.gameSettings.guiScale=2;ScaledResolution gui2=new ScaledResolution(mc);mc.gameSettings.guiScale=3;ScaledResolution gui3=new ScaledResolution(mc);mc.gameSettings.guiScale=oldScale;
        for(int[] size:new int[][]{{320,240},{gui2.getScaledWidth(),gui2.getScaledHeight()},{gui3.getScaledWidth(),gui3.getScaledHeight()},{actualWidth,actualHeight}}){
            gui.setWorldAndResolution(mc,size[0],size[1]);
            ProgrammableDialogLayout p=new ProgrammableDialogLayout(size[0],size[1]);
            ProgrammableBlockDialog.State state=new ProgrammableBlockDialog.State(tile.getHousingTexture(),tile.getMainTextureLayout(),tile.getFaceTextures().enabled,tile.getFaceTextures().choices(),-1);
            GuiButton join=button(gui,240),parity=button(gui,241),done=button(gui,100);
            require(join.displayString.equals(tile.textureJoin().directionLabel())&&parity.displayString.equals(tile.textureJoin().parityLabel()),"join captions");
            require(parity.y>=join.y+join.height&&parity.y+parity.height<done.y,"join group stays above Done");
            require(parity.enabled==tile.textureJoin().directional(),"coordinate filter only active for directional joins");
            for(ProgrammableBlockDialog.Control c:ProgrammableBlockDialog.controls(p,state,"Done")){
                GuiButton b=button(gui,c.id);require(b.x==c.x&&b.y==c.y&&b.width==c.width&&b.height==c.height,"actual widget bounds "+c.id);
                require(b.displayString.equals(c.label),"actual widget label "+c.id+" "+b.displayString);
            }
        }
    }
    private static void scroll(GuiProgrammableWall gui)throws Exception{
        HousingTextureList picker=gui.materialPicker();int x=field("x").getInt(picker),y=field("y").getInt(picker),width=field("width").getInt(picker),count=field("count").getInt(picker);
        @SuppressWarnings("unchecked") List<Integer> rows=(List<Integer>)field("rows").get(picker);int maximum=Math.max(0,rows.size()-count);require(maximum>0,"scroll fixture lacks rows");
        field("scroll").setInt(picker,0);require(picker.wheel(x+2,y+2,-120),"cube wheel event refused");require(field("scroll").getInt(picker)==1,"cube wheel did not advance one row");
        int height=count*22;gui.mouseClicked(x+width+2,y+height-1,0);gui.mouseClickMove(x+width+2,y+height+100,0,10);gui.mouseReleased(x+width+2,y+height+100,0);
        require(field("scroll").getInt(picker)==maximum,"scrollbar cannot reach last row");
        gui.mouseClicked(x+width+2,y+1,0);gui.mouseClickMove(x+width+2,y-100,0,10);gui.mouseReleased(x+width+2,y-100,0);require(field("scroll").getInt(picker)==0,"scrollbar cannot reach first row");
    }
    private static void pick(GuiProgrammableWall gui,int choice)throws Exception{
        HousingTextureList picker=gui.materialPicker();picker.setSelected(choice);field("selected").setInt(picker,-1);
        @SuppressWarnings("unchecked") List<Integer> rows=(List<Integer>)field("rows").get(picker);int row=rows.indexOf(choice)-field("scroll").getInt(picker);require(row>=0&&row<field("count").getInt(picker),"face material row not visible");
        gui.mouseClicked(field("x").getInt(picker)+8,field("y").getInt(picker)+row*22+8,0);
    }
    private static void check(Minecraft mc,BlockPos pos,boolean enabled,int face,int choice,int layout)throws Exception{
        mc.getIntegratedServer().addScheduledTask(()->{
            EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)player.world.getTileEntity(pos);
            require(tile.getFaceTextures().enabled==enabled&&tile.getMainTextureLayout()==layout,"server settings stage="+stage);
            if(face>=0)require(tile.getFaceTextures().choice(face)==choice,"server face stage="+stage);
            NBTTagCompound tag=tile.writeToNBT(new NBTTagCompound());TileEntityAnimatedScreenSelector reloaded=new TileEntityAnimatedScreenSelector();reloaded.readFromNBT(tag);
            require(reloaded.getFaceTextures().equals(tile.getFaceTextures())&&reloaded.getMainTextureLayout()==layout,"saved cube settings stage="+stage);
        }).get();
        TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)mc.world.getTileEntity(pos);require(tile.getFaceTextures().enabled==enabled&&tile.getMainTextureLayout()==layout,"client settings stage="+stage);
        if(face>=0)require(tile.getFaceTextures().choice(face)==choice,"client face stage="+stage);
    }
    private static void captureScales(Minecraft mc,GuiProgrammableWall gui,File output)throws Exception{
        int oldScale=mc.gameSettings.guiScale;
        try{for(int scale:new int[]{2,3}){mc.gameSettings.guiScale=scale;ScaledResolution size=new ScaledResolution(mc);gui.setWorldAndResolution(mc,size.getScaledWidth(),size.getScaledHeight());capture(mc,gui,output,"material-cube-face-controls-gui"+scale+".png");}}
        finally{mc.gameSettings.guiScale=oldScale;ScaledResolution size=new ScaledResolution(mc);gui.setWorldAndResolution(mc,size.getScaledWidth(),size.getScaledHeight());}
    }
    private static void capture(Minecraft mc,GuiProgrammableWall gui,File output,String name)throws Exception{
        mc.getFramebuffer().framebufferClear();mc.getFramebuffer().bindFramebuffer(true);mc.entityRenderer.setupOverlayRendering();mc.entityRenderer.disableLightmap();net.minecraft.client.renderer.GlStateManager.disableLighting();net.minecraft.client.renderer.GlStateManager.color(1,1,1,1);gui.drawScreen(-1,-1,0);
        ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,name));
    }
    private static GuiButton button(GuiProgrammableWall gui,int id)throws Exception{
        Field field=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(GuiScreen.class,"buttonList","field_146292_n");
        @SuppressWarnings("unchecked") List<GuiButton> buttons=(List<GuiButton>)field.get(gui);for(GuiButton b:buttons)if(b.id==id)return b;throw new IllegalStateException("Missing cube button "+id);
    }
    private static void click(GuiProgrammableWall gui,int id,int mouse)throws Exception{GuiButton b=button(gui,id);require(b.enabled,"disabled cube control "+id);gui.mouseClicked(b.x+3,b.y+3,mouse);}
    private static Field field(String name)throws Exception{Field f=HousingTextureList.class.getDeclaredField(name);f.setAccessible(true);return f;}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Cube dialog: "+message);}
}
