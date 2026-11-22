package com.vandorlabs.neoforge.vehicle.client;

import com.vandorlabs.neoforge.client.NativeDialogButton;
import com.vandorlabs.neoforge.vehicle.NativeVehicleBindings;
import com.vandorlabs.neoforge.vehicle.NativeVehicleMenu;
import com.vandorlabs.physics.FlightEnvelope;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeVehicleControllerScreen extends AbstractContainerScreen<NativeVehicleMenu> {
    private final DecimalFormat format=new DecimalFormat("#,##0.0",DecimalFormatSymbols.getInstance(Locale.ROOT));
    private Button action,scan,flightTab;
    private boolean flight;

    public NativeVehicleControllerScreen(NativeVehicleMenu menu,Inventory inventory,Component title) {
        super(menu,inventory,title);
        imageWidth=258; imageHeight=187;
        titleLabelX=12; titleLabelY=10;
    }
    public Button actionButton(){return action;}
    public Button scanButton(){return scan;}
    public Button flightTab(){return flightTab;}

    @SubscribeEvent public static void register(RegisterMenuScreensEvent event) {
        event.register(NativeVehicleBindings.MENU,NativeVehicleControllerScreen::new);
    }

    @Override protected void init() {super.init(); controls();}
    private void controls() {
        clearWidgets();
        button(12,28,105,"Vehicle",b->{flight=false;controls();}).active=flight;
        flightTab=button(121,28,125,"Flight",b->{flight=true;controls();});
        flightTab.active=!flight;
        scan=null;
        if(menu.controller!=null) scan=button(12,155,85,"Scan craft",b->send(NativeVehicleMenu.SCAN));
        action=button(menu.controller==null?12:100,155,menu.controller==null?234:146,
                menu.controller!=null?"Assemble in Taxi Mode":"Park as blocks",b->send(NativeVehicleMenu.CONVERT));
    }
    private Button button(int x,int y,int w,String label,Button.OnPress press) {
        return addRenderableWidget(NativeDialogButton.builder(Component.literal(label),press).bounds(leftPos+x,topPos+y,w,20).build());
    }
    private void send(int action) {
        if(minecraft.gameMode!=null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId,action);
    }
    @Override protected void renderBg(GuiGraphics graphics,float partial,int mouseX,int mouseY) {
        graphics.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xff27343d);
        graphics.fill(leftPos+2,topPos+2,leftPos+imageWidth-2,topPos+imageHeight-2,0xff18232b);
    }
    @Override protected void renderLabels(GuiGraphics g,int mouseX,int mouseY) {
        g.drawString(font,title,titleLabelX,titleLabelY,0xffe7eff4,false);
        int count=menu.blocks();
        var data=menu.flightReadout();
        if(!flight) {
            text(g,56,menu.craft==null?"Mode: Park":"Mode: "+switch(menu.craft.mode()){case 1->"Hover";case 2->"Flight";default->"Taxi";});
            if(count>0)text(g,74,(menu.craft==null?"Scanned blocks: ":"Carried blocks: ")+count);
        }
        if(data==null) {
            text(g,94,count<0?"Scan failed. See the message in chat.":"Scan the connected craft to read its weight.");
            return;
        }
        if(!flight) {text(g,92,"Actual weight: "+format.format(data.mass()));return;}
        text(g,53,"Actual / effective weight: "+format.format(data.mass())+" / "+format.format(data.effectiveMass()));
        text(g,67,"Maxwell Drives: "+data.drives()+"     Fuel: "+data.fuel()+" / "+data.capacity()+" mB");
        text(g,81,"Available thrust: "+format.format(data.thrust())+"     Takeoff limit: "+format.format(data.takeoffMass()));
        boolean supported=FlightEnvelope.canFly(data.effectiveMass(),data.thrust());
        double ceiling=minecraft.level.dimension()==Level.OVERWORLD?data.ceiling():Math.min(data.ceiling(),minecraft.level.getMaxBuildHeight()-1);
        text(g,95,"Maximum altitude: "+(supported?"Y "+Math.round(ceiling):"unavailable"));
        if(!supported)verse(g,"The runway waits still","Laden wings cannot take flight","Let the cargo go");
        else if(ceiling<FlightEnvelope.MAX_ALTITUDE && minecraft.level.dimension()==Level.OVERWORLD)
            verse(g,"The thin heavens wait","Thrust fades as the air grows thin","Clouds keep their distance");
    }
    private void text(GuiGraphics g,int y,String message){g.drawString(font,message,12,y,0xffc4dbe8,false);}
    private void verse(GuiGraphics g,String a,String b,String c){
        g.drawString(font,a,12,112,0xff9fbcd2,false);g.drawString(font,b,12,124,0xff9fbcd2,false);g.drawString(font,c,12,136,0xff9fbcd2,false);
    }
}
