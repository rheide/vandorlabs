package com.vandorlabs.client;

import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.tiles.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.util.math.BlockPos;
import java.util.List;

/** Frozen actual slab/stair widgets and immediate preview, followed by real container packets. */
final class ShapeDialogChecks {
    private static final BlockPos POS=new BlockPos(12,100,12);private static int ticks;private static boolean prepared,checked;
    private static final java.lang.reflect.Field BUTTONS=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(GuiScreen.class,"buttonList","field_146292_n");
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Shape dialog: "+message);}
    @SuppressWarnings("unchecked") private static List<GuiButton> buttons(GuiScreen gui)throws Exception{return (List<GuiButton>)BUTTONS.get(gui);}
    private static GuiButton button(GuiScreen gui,int id)throws Exception{for(GuiButton b:buttons(gui))if(b.id==id)return b;throw new IllegalStateException("Missing shape widget="+id);}
    private static void click(GuiProgrammableWall gui,int id,int mouse)throws Exception{GuiButton b=button(gui,id);gui.mouseClicked(b.x+2,b.y+2,mouse);}
    static boolean tick(Minecraft mc,java.io.File output)throws Exception{
        if(checked)return StairDialogLiveChecks.tick(mc,output);
        require(++ticks<400,"client tile timed out");
        if(!prepared){mc.getIntegratedServer().addScheduledTask(()->{net.minecraft.world.WorldServer world=mc.getIntegratedServer().getWorld(0);world.setBlockState(POS,ModBlocks.PROGRAMMABLE_STAIRS.getDefaultState(),3);mc.getIntegratedServer().getPlayerList().getPlayers().get(0).connection.setPlayerLocation(12,100,9,0,0);}).get();prepared=true;return false;}
        if(!(mc.world.getTileEntity(POS) instanceof TileEntityAnimatedScreenSelector))return false;
        TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)mc.world.getTileEntity(POS);int cases=0;
        for(int tread:new int[]{1,2,4,8,16})for(int roll=0;roll<3;roll++)for(boolean faces:new boolean[]{false,true}){
            tile.setStairGeometry(tread,roll);tile.setSideTexture(3);tile.setFaceTextures(new FaceTextures(faces,new int[]{1,2,3,4,5,6}));
            GuiProgrammableWall gui=new GuiProgrammableWall(mc.player.inventory,tile);mc.displayGuiScreen(gui);int left=gui.getGuiLeft(),top=gui.getGuiTop(),span=faces?gui.getXSize()-24:gui.getXSize()-197,count=faces?8:2,w=span/count;
            String[] labels={"Main","Sides","Down","Up","Front","Back","Left","Right"};
            for(int i=0;i<count;i++){GuiButton b=button(gui,i==0?120:i==1?127:119+i);require(b.x==left+12+i*w&&b.y==top+54&&b.width==(i==count-1?span-i*w-2:w-2)&&b.height==18&&b.displayString.equals(labels[i]),"side/face tabs");}
            require(button(gui,130).displayString.equals("Step size: "+tread+" px")&&button(gui,131).displayString.equals("Orientation: "+(roll==0?"Normal":"Sideways")),"saved tread/roll label");
            net.minecraftforge.fml.client.config.GuiSlider slider=(net.minecraftforge.fml.client.config.GuiSlider)button(gui,130);require(slider.getValueInt()==Math.min(3,Integer.numberOfTrailingZeros(tread)),"saved16 clamps only slider index");
            click(gui,131,1);require(tile.stairRoll()==(roll==0?1:0),"right orientation is exact current binary toggle");
            click(gui,127,0);require(button(gui,128).enabled&&button(gui,128).displayString.equals("Use default side texture"),"side reset row");click(gui,128,0);require(tile.getSideTexture()==-1&&!button(gui,128).enabled,"side reset immediate");
            int layout=tile.getMainTextureLayout();click(gui,103,1);require(tile.getMainTextureLayout()==(layout+2)%3,"layout right previous immediate");
            click(gui,106,1);require(tile.getFaceTextures().enabled!=faces,"face override toggle immediate");cases++;
        }
        mc.displayGuiScreen(null);checked=true;System.out.println("[vandorlabs][reprolab] shape-dialog legacy PASS cases="+cases+" exact side tabs reset IDs saved16 roll2 reverse options immediate preview");return false;
    }
}
