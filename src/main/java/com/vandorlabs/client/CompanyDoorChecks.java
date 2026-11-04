package com.vandorlabs.client;

import com.vandorlabs.tiles.ScreenHousingTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;

final class CompanyDoorChecks {
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("company doors: "+why);}
    static void run(Minecraft mc,File output)throws Exception{
        String[] companies={"pinnacle","ashiro_mai","paladyne","vektor","seyr","vandor"},labels={"Pinnacle","Ashiro-Mai","Paladyne","Vektor","Seyr","Vandor"};int cases=0;
        for(int company=0;company<companies.length;company++)for(String design:new String[]{"standard","security","light"}){
            int choice=ScreenHousingTextures.screenIndex("company_doors/"+companies[company]+"/"+design);require(choice>0&&("Doors/"+labels[company]).equals(ScreenHousingTextures.menuCategory(choice)),"company submenu missing");
            for(int detail=0;detail<3;detail++){HousingTextureList picker=HousingTextureList.forDoors(detail,20,40,220,choice);java.lang.reflect.Field field=HousingTextureList.class.getDeclaredField("options");field.setAccessible(true);require(((Map<?,?>)field.get(picker)).containsKey(choice)&&picker.selected()==choice,"new design absent from picker size "+detail);}
            BufferedImage source;try(java.io.InputStream in=mc.getResourceManager().getResource(new ResourceLocation("vandorlabs","textures/blocks/company_doors/"+companies[company]+"/"+design+".png")).getInputStream()){source=ImageIO.read(in);}require(source.getWidth()==128&&source.getHeight()==256&&source.getColorModel().hasAlpha(),"standard door resolution/alpha");if(design.equals("light")){int transparent=0;for(int y=0;y<256;y++)for(int x=0;x<128;x++)if((source.getRGB(x,y)>>>24)<20)transparent++;require(transparent>50,"authored Light window openings lost alpha");}
            paired(mc,output,choice,companies[company]+"-"+design);
            for(boolean large:new boolean[]{false,true}){DoorAppearanceRuntimeChecks.Fixture fixture=new DoorAppearanceRuntimeChecks.Fixture(mc,large,2);try{BufferedImage original=DoorAppearanceRuntimeChecks.capture(mc,fixture,false,0,false);fixture.tile.setFaceTexture(choice);fixture.tile.setTextureLayout(0);BufferedImage selected=DoorAppearanceRuntimeChecks.capture(mc,fixture,false,0,false);int difference=0;for(int y=0;y<384;y++)for(int x=0;x<384;x++)if(original.getRGB(x,y)!=selected.getRGB(x,y))difference++;require(difference>1000,"company artwork invisible in real leaf renderer");ImageIO.write(selected,"png",new File(output,"company-door-"+companies[company]+"-"+design+(large?"-large":"")+".png"));cases++;if(design.equals("light")){require(fixture.tile.hasCompanyGlass()&&fixture.tile.shouldRenderInPass(1),"Light artwork participates in translucent glazing pass");for(int direction:new int[]{0,3})for(float progress:new float[]{0,.6F}){fixture.tile.configure(2,1,true,direction,true,true,false,0,false);BufferedImage clear,glazed;try{CompanyDoorGlazing.enabled=false;clear=DoorAppearanceRuntimeChecks.capture(mc,fixture,false,progress,false);}finally{CompanyDoorGlazing.enabled=true;}glazed=DoorAppearanceRuntimeChecks.capture(mc,fixture,false,progress,false);int reflections=0;for(int y=0;y<384;y++)for(int x=0;x<384;x++)if(clear.getRGB(x,y)!=glazed.getRGB(x,y))reflections++;require(reflections>40,"faint glazing visible at rest/in motion company="+companies[company]+" size="+large+" pose="+progress+" pixels="+reflections);ImageIO.write(glazed,"png",new File(output,"company-glass-"+companies[company]+(large?"-large":"")+"-"+direction+"-"+progress+".png"));}}}finally{fixture.close();}}
        }
        System.out.println("[vandorlabs][reprolab] company-doors PASS designs=18 rendered="+cases+" six-company-subfolders all-picker-sizes alpha-windows native128x256 normal/large sliding/X closed/moving native-glass shimmer screenshots");
    }
    private static void paired(Minecraft mc,File output,int choice,String name)throws Exception{
        for(boolean large:new boolean[]{false,true}){DoorAppearanceRuntimeChecks.Fixture f=new DoorAppearanceRuntimeChecks.Fixture(mc,large,2,!large);try{
            f.tile.setFaceTexture(choice);for(int layout=0;layout<3;layout++){f.tile.setTextureLayout(layout);BufferedImage image=DoorAppearanceRuntimeChecks.capture(mc,f,false,0,false);int different=0,total=0;
                for(int y=40;y<344;y++)for(int x=40;x<174;x++){int a=image.getRGB(x,y),b=image.getRGB(image.getWidth()-1-x,y);int delta=Math.abs((a>>16&255)-(b>>16&255))+Math.abs((a>>8&255)-(b>>8&255))+Math.abs((a&255)-(b&255));if(delta>30)different++;total++;}
                require(different<total/15,"paired company leaves must mirror "+name+" large="+large+" layout="+layout+" mismatched="+different+"/"+total);if(layout==0)ImageIO.write(image,"png",new File(output,"company-paired-"+name+(large?"-large":"")+".png"));
            }
        }finally{f.close();}}
    }
    private CompanyDoorChecks(){}
}
