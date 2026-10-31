package com.vandorlabs.client;

import com.vandorlabs.tiles.GlassPlayback;
import com.vandorlabs.tiles.ScreenHousingTextures;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;

/** The same three playback modes and speeds used by Programmable Screen. */
final class GlassPlaybackPane {
    static boolean available(int material){return material>=0&&"Screens".equals(ScreenHousingTextures.category(material));}
    static void init(ProgrammableDialogLayout layout,GlassPlayback playback,List<GuiButton> buttons){
        String[] modes={"off","static","animated"},speeds={"slow","normal","fast"};
        for(int i=0;i<3;i++){
            GuiButton mode=layout.choice(700+i,i,3,66,I18n.format("gui.vandorlabs.selector."+modes[i]));mode.enabled=playback.mode()!=i;buttons.add(mode);
            GuiButton speed=layout.choice(710+i,i,3,100,I18n.format("gui.vandorlabs.selector."+speeds[i]));speed.enabled=playback.speed()!=i;buttons.add(speed);
        }
    }
    static void draw(GuiMaterialPicker host){
        net.minecraft.client.gui.FontRenderer font=net.minecraft.client.Minecraft.getMinecraft().fontRenderer;
        int x=host.getGuiLeft()+host.getXSize()-166,y=host.getGuiTop();font.drawString("Display",x,y+54,0xFFFFFF);font.drawString("Speed",x,y+88,0xFFFFFF);
    }
    static boolean action(GuiButton button,GlassPlayback playback){
        if(button.id>=700&&button.id<=702){playback.set(button.id-700,playback.speed());return true;}
        if(button.id>=710&&button.id<=712){playback.set(playback.mode(),button.id-710);return true;}
        return false;
    }
}
