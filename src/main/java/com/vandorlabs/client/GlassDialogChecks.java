package com.vandorlabs.client;

import com.vandorlabs.tiles.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.util.math.BlockPos;

/** Actual current glass widgets, panes and immediate client preview before/after shared row extraction. */
final class GlassDialogChecks {
    private static final BlockPos POS=new BlockPos(12,100,12);private static int ticks;private static boolean prepared;
    private static final java.lang.reflect.Field BUTTONS=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(GuiScreen.class,"buttonList","field_146292_n");
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Glass dialog: "+message);}
    @SuppressWarnings("unchecked") private static List<GuiButton> buttons(GuiScreen gui)throws Exception{return (List<GuiButton>)BUTTONS.get(gui);}
    private static GuiButton button(GuiScreen gui,int id)throws Exception{for(GuiButton b:buttons(gui))if(b.id==id)return b;throw new IllegalStateException("Missing glass widget="+id);}
    private static boolean has(GuiScreen gui,int id)throws Exception{for(GuiButton b:buttons(gui))if(b.id==id)return true;return false;}
    private static void click(GuiProgrammableGlass gui,int id,int mouse)throws Exception{GuiButton b=button(gui,id);gui.mouseClicked(b.x+2,b.y+2,mouse);}
    private static int material(boolean playback){for(int i=0;i<ScreenHousingTextures.IDS.length;i++)if(ScreenHousingTextures.category(i).equals("Screens")==playback)return ScreenHousingTextures.choiceAt(i);throw new IllegalStateException("No reference material");}
    static boolean tick(Minecraft mc)throws Exception{
        require(++ticks<400,"client tile timed out");if(!prepared){mc.getIntegratedServer().addScheduledTask(()->{net.minecraft.world.WorldServer world=mc.getIntegratedServer().getWorld(0);world.setBlockState(POS,Block.getBlockFromName("vandorlabs:programmable_glass").getDefaultState(),3);mc.getIntegratedServer().getPlayerList().getPlayers().get(0).connection.setPlayerLocation(12,100,9,0,0);}).get();prepared=true;return false;}
        if(!(mc.world.getTileEntity(POS) instanceof TileEntityProgrammableGlass))return false;TileEntityProgrammableGlass tile=(TileEntityProgrammableGlass)mc.world.getTileEntity(POS);int cases=0;String[] tints={"Mild","Normal","Strong","Opaque"},layouts={"Stretch","Tile","Fit (Aspect)"};int screen=material(true),plain=material(false);
        for(int material:new int[]{-1,plain,screen})for(int tint=0;tint<4;tint++)for(int layout=0;layout<3;layout++){
            tile.setMaterial(material);tile.setTextureLayout(layout);tile.setAppearance(tint,true);tile.glassPlayback.set(1,1);GuiProgrammableGlass gui=new GuiProgrammableGlass(tile);mc.displayGuiScreen(gui);int left=gui.getGuiLeft(),top=gui.getGuiTop(),controls=left+gui.getXSize()-166,w=(gui.getXSize()-22)/4;String[] names={"Glass","Front","Interior","Exterior"};
            for(int i=0;i<4;i++){GuiButton b=button(gui,196+i);require(b.x==left+12+i*w&&b.y==top+30&&b.width==w-2&&b.height==18&&b.displayString.equals(names[i])&&b.enabled==(i!=0),"current four glass tabs");}
            GuiButton join=button(gui,240),tintButton=button(gui,4),hidden=button(gui,5);require(join.x==controls&&join.y==top+(material>=0?102:54)&&join.width==154&&join.height==20&&join.displayString.equals(tile.textureJoin().directionLabel()),"join row");require(tintButton.x==controls&&tintButton.y==top+(material>=0?54:102)&&tintButton.displayString.equals("Tint: "+tints[tint]),"four tint row");require(!hidden.visible&&hidden.id==5&&hidden.y==top+102&&hidden.displayString.equals("Framed"),"hidden compatibility button5");require(has(gui,6)==(material>=0)&&has(gui,700)==GlassPlaybackPane.available(material),"layout/playback availability");
            if(material>=0){require(button(gui,6).displayString.equals("Texture: "+layouts[layout])&&button(gui,6).y==top+78,"layout row");click(gui,6,1);require(tile.getTextureLayout()==(layout+2)%3,"layout right previous immediate preview");}
            click(gui,4,1);require(tile.getTint()==(tint+3)%4&&button(gui,4).displayString.equals("Tint: "+tints[(tint+3)%4]),"tint right previous includes Opaque");int before=tile.textureJoin().direction;click(gui,240,1);require(tile.textureJoin().direction==Math.floorMod(before-1,6)&&tile.isJoin()==(tile.textureJoin().direction!=0)&&tile.getSize()==0,"join reverse cycle and immediate preview");cases++;
        }
        tile.setMaterial(screen);tile.glassPlayback.set(1,1);GuiProgrammableGlass gui=new GuiProgrammableGlass(tile);mc.displayGuiScreen(gui);int controls=gui.getGuiLeft()+gui.getXSize()-166,top=gui.getGuiTop();String[] modes={"Off","Static","Animated"},speeds={"Slow","Normal","Fast"};for(int i=0;i<3;i++){GuiButton mode=button(gui,700+i),speed=button(gui,710+i);require(mode.x==controls+i*52&&mode.y==top+150&&mode.width==50&&mode.height==18&&mode.displayString.equals(modes[i])&&mode.enabled==(i!=1),"playback mode row");require(speed.x==controls+i*52&&speed.y==top+174&&speed.displayString.equals(speeds[i])&&speed.enabled==(i!=1),"playback speed row");}click(gui,702,0);click(gui,710,0);require(tile.glassPlayback.mode()==2&&tile.glassPlayback.speed()==0,"playback immediate preview");
        for(int page=1;page<4;page++){click(gui,196+page,0);require(!button(gui,196+page).enabled&&!has(gui,240)&&has(gui,3003)&&has(gui,3008),"frame face pane retained page="+page);boolean before=tile.isFramed();click(gui,3008,1);require(tile.isFramed()!=before&&button(gui,3008).displayString.equals(tile.isFramed()?"Framed":"Bare"),"live framing callback page="+page);}
        click(gui,196,0);gui.choose(HousingTextureList.CANOPY_GLASS_BASE+19);require(tile.getShade()==19&&tile.getMaterial()==-1&&!has(gui,700),"Gold shade selection clears material and playback controls");gui.choose(screen);require(tile.getMaterial()==screen&&tile.getShade()==19&&tile.glassPlayback.mode()==2&&tile.glassPlayback.speed()==0,"material choice retains dormant shade and playback");mc.displayGuiScreen(null);
        System.out.println("[vandorlabs][reprolab] glass-dialog legacy PASS cases="+cases+" four current tabs/tints layout reverse cycles hidden5 frame panes playback and immediate preview");return true;
    }
}
