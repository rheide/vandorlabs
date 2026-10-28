package com.vandorlabs.client;

import com.vandorlabs.container.ContainerCockpit;
import com.vandorlabs.tiles.TileEntityCockpit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;
import java.awt.Rectangle;
import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

/** Render every cockpit page and check interactive bounds at small and normal GUI sizes. */
final class CockpitDialogRuntimeChecks {
    static void run(Minecraft mc,File output)throws Exception{run(mc,output,false);}
    static void runStandalone(Minecraft mc,File output)throws Exception{run(mc,output,true);}
    private static void run(Minecraft mc,File output,boolean standalone)throws Exception{
        GuiScreen previous=mc.currentScreen;int checked=0;
        Field redstone=GuiCockpit.class.getDeclaredField("redstonePage"),surfaceRedstone=GuiCockpit.class.getDeclaredField("surfaceRedstone"),receiverField=GuiCockpit.class.getDeclaredField("receiverPane"),rowsField=GuiCockpit.class.getDeclaredField("rowsPane");redstone.setAccessible(true);surfaceRedstone.setAccessible(true);receiverField.setAccessible(true);rowsField.setAccessible(true);
        Field page=GuiCockpit.class.getDeclaredField("page"),fields=GuiCockpit.class.getDeclaredField("fields");page.setAccessible(true);fields.setAccessible(true);
        Field buttons;try{buttons=GuiScreen.class.getDeclaredField("buttonList");}catch(NoSuchFieldException mapped){buttons=GuiScreen.class.getDeclaredField("field_146292_n");}buttons.setAccessible(true);
        try{
            for(int model=0;model<com.vandorlabs.cockpit.CockpitData.IDS.length;model++){
                TileEntityCockpit tile;
                if(standalone){tile=new TileEntityCockpit(com.vandorlabs.cockpit.CockpitData.IDS[model]);tile.setWorld(mc.world);tile.setPos(new BlockPos(2048+model*6,128,2048));}
                else tile=(TileEntityCockpit)mc.world.getTileEntity(new BlockPos(222+model*6,30,120));
                require(tile!=null,"fixture synchronized");GuiCockpit gui=new GuiCockpit(new ContainerCockpit(tile,null));gui.inventorySlots.windowId=-77;mc.displayGuiScreen(gui);
                int firstCategory=999,selectedCategory=-1;for(Object raw:(List<?>)buttons.get(gui)){GuiButton button=(GuiButton)raw;if(button.id>=200&&button.id<206){firstCategory=Math.min(firstCategory,button.id);if(!button.enabled)selectedCategory=button.id;}}
                require(firstCategory==selectedCategory,"new "+tile.design()+" dialog opens leftmost available category");
                page.setInt(gui,(tile.data().seat==null?0:1)+1);gui.initGui();
                java.lang.reflect.Method openRows=GuiCockpit.class.getDeclaredMethod("redstone");openRows.setAccessible(true);openRows.invoke(gui);
                require(mc.currentScreen==gui&&rowsField.get(gui) instanceof RedstoneRowsPane,"Redstone artwork selection edits shared rows in the original screen");require(gui.inventorySlots.windowId==-77,"inline controls retain original authenticated window");
                int count=(tile.data().seat==null?0:1)+1+tile.displays.size()+tile.controls.size();
                for(int[] size:new int[][]{{320,240},{460,340},{new ScaledResolution(mc).getScaledWidth(),new ScaledResolution(mc).getScaledHeight()}})for(int index=0;index<=count;index++)for(int variant=0;variant<3;variant++){
                    int display=index-(tile.data().seat==null?0:1)-1;if(variant>0&&(display<0||display>=tile.displays.size()))continue;surfaceRedstone.setBoolean(gui,variant==1);Field controls=GuiCockpit.class.getDeclaredField("surfaceControls");controls.setAccessible(true);controls.setBoolean(gui,variant==2);
                    redstone.setBoolean(gui,index==count);page.setInt(gui,index==count?0:index);gui.setWorldAndResolution(mc,size[0],size[1]);
                    if(size[0]==320)checkDrag(gui);
                    ReceiverPane receiver=(ReceiverPane)receiverField.get(gui);if(variant==1){require(receiver!=null&&receiver.getClass()==ReceiverPane.class&&receiver.button().getClass()==RedstoneActivationButton.class,"cockpit reuses native receiver pane and activation button");int mode=tile.displays.get(display).activation();GuiButton activation=receiver.button();gui.mouseClicked(activation.x+2,activation.y+2,0);require(tile.displays.get(display).activation()==(mode+1)%3,"shared receiver left-cycle");activation=((ReceiverPane)receiverField.get(gui)).button();gui.mouseClicked(activation.x+2,activation.y+2,1);require(tile.displays.get(display).activation()==mode,"shared receiver right-cycle");receiver=(ReceiverPane)receiverField.get(gui);}

                    RedstoneRowsPane rowPane=(RedstoneRowsPane)rowsField.get(gui);if(variant==2)require(rowPane!=null&&rowPane.getClass()==RedstoneRowsPane.class,"same shared inline control editor");
                    List<Rectangle> occupied=new ArrayList<>();
                    for(Object raw:(List<?>)buttons.get(gui)){GuiButton button=(GuiButton)raw;if(button.visible&&button instanceof DialogTabButton)require(mc.fontRenderer.getStringWidth(button.displayString)<=button.width-4,"tab label fits at "+size[0]);if(button.visible)add(occupied,new Rectangle(button.x,button.y,button.width,button.height),size);}
                    if(rowPane!=null){for(GuiTextField field:new GuiTextField[]{rowPane.labelField(),rowPane.channelField()})if(field.getVisible())add(occupied,new Rectangle(field.x,field.y,field.width,field.height),size);}
                    if(receiver!=null){GuiTextField field=receiver.field();add(occupied,new Rectangle(field.x,field.y,field.width,field.height),size);}
                    for(Object raw:(List<?>)fields.get(gui)){GuiTextField field=(GuiTextField)raw;add(occupied,new Rectangle(field.x,field.y,field.width,field.height),size);}
                    mc.getFramebuffer().bindFramebuffer(true);net.minecraft.client.renderer.GlStateManager.clear(16640);mc.entityRenderer.setupOverlayRendering();gui.drawScreen(-1,-1,0);
                    if(index==0 || index==(tile.data().seat==null?0:1) || index==(tile.data().seat==null?0:1)+1 || index==(tile.data().seat==null?0:1)+1+tile.displays.size())javax.imageio.ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"shot_utility22_cockpit_dialog_"+model+"_"+size[0]+"_"+index+".png"));
                    checked++;
                }
            }
        }finally{mc.displayGuiScreen(previous);if(previous==null)mc.player.openContainer=mc.player.inventoryContainer;}
        System.out.println("[vandorlabs][reprolab] cockpit-dialog PASS "+checked+" pages/resolutions visible control bounds and overlap checks");
    }
    private static void checkDrag(GuiCockpit gui)throws Exception{
        Field field=GuiCockpit.class.getDeclaredField("textures");field.setAccessible(true);HousingTextureList list=(HousingTextureList)field.get(gui);if(list==null)return;
        int x=value(list,"x"),y=value(list,"y"),width=value(list,"width"),count=value(list,"count");
        Field rows=HousingTextureList.class.getDeclaredField("rows");rows.setAccessible(true);if(((List<?>)rows.get(list)).size()<=count)return;
        gui.mouseClicked(x+width+3,y+1,0);gui.mouseClickMove(x+width+3,y+count*HousingTextureList.ROW_HEIGHT-1,0,100);
        require(value(list,"scroll")>0,"actual mouse drag scrolls texture list");gui.mouseReleased(x+width+3,y,0);
    }
    private static int value(Object object,String name)throws Exception{Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.getInt(object);}
    private static void add(List<Rectangle> occupied,Rectangle next,int[] size){require(next.x>=0 && next.y>=0 && next.x+next.width<=size[0] && next.y+next.height<=size[1],"control outside screen "+next);for(Rectangle other:occupied)require(!next.intersects(other),"overlapping controls "+next+" and "+other);occupied.add(next);}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("cockpit-dialog: "+message);}
}
