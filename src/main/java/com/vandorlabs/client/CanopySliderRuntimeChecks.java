package com.vandorlabs.client;

import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.config.GuiSlider;

/** Exercise the real press/render/drag/release path, including coalesced live updates. */
final class CanopySliderRuntimeChecks {
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("canopy slider: "+message);}
    static void check(Minecraft mc,GuiLargeCanopy gui)throws Exception{
        Field sent=GuiLargeCanopy.class.getDeclaredField("sent");sent.setAccessible(true);
        List<GuiButton> buttons=net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(GuiScreen.class,gui,"buttonList","field_146292_n");
        for(int id:new int[]{0,1}){
            final int controlId=id;
            GuiSlider slider=(GuiSlider)buttons.stream().filter(button->button.id==controlId).findFirst().get();
            int y=slider.y+slider.height/2,x=slider.x+4;
            gui.mouseClicked(x,y,0);
            require(slider.dragging && buttons.contains(slider),"press retains the active slider");
            String previous=(String)sent.get(gui);int updates=0;
            for(int step=0;step<=32;step++){
                // Depth sweeps 0..80; inner size sweeps 10..40 without another press.
                double value=id==0?step/32D:step/96D;
                x=slider.x+4+(int)Math.round(value*(slider.width-8));
                gui.drawScreen(x,y,0);
                if(step%4==0 && step<32)gui.updateScreen();
                require(slider.dragging && buttons.contains(slider),"drag survives rendering and live updates");
                int expected=(int)Math.round(slider.minValue+(slider.maxValue-slider.minValue)*(x-slider.x-4)/(slider.width-8D));
                require(slider.getValueInt()==expected,"thumb follows every drag position");
                String current=(String)sent.get(gui);if(!current.equals(previous)){updates++;previous=current;}
            }
            gui.mouseReleased(x,y,0);
            require(!slider.dragging,"release ends dragging");
            require(updates<=5,"world updates are coalesced rather than sent on every frame");
            require(((String)sent.get(gui)).startsWith(id==0?"80:":"80:40:"),"release flushes the final setting");
            int settled=slider.getValueInt();gui.drawScreen(slider.x+4,y,0);
            require(slider.getValueInt()==settled,"moving the pointer after release leaves the value unchanged");
        }
        System.out.println("[vandorlabs][reprolab] canopy-slider-drag PASS depth inner-size continuous-drag throttled-updates release-flush");
    }
    private CanopySliderRuntimeChecks(){}
}
