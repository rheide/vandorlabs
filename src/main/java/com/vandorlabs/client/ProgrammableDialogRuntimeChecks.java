package com.vandorlabs.client;

import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

/** Check actual initialized pickers at the minimum GUI size and a taller viewport. */
final class ProgrammableDialogRuntimeChecks {
    static void check(Minecraft mc) {
        GuiScreen gui=mc.currentScreen;
        if(!(gui instanceof GuiProgrammableWall || gui instanceof GuiProgrammableTrapdoor
                || gui instanceof GuiSpaceDoor || gui instanceof GuiProgrammableLight
                || gui instanceof GuiAnimatedScreenSelector || gui instanceof GuiProgrammableInput
                || gui instanceof GuiProgrammableHalfConsole || gui instanceof GuiProgrammableTrigger
                || gui instanceof GuiRedstoneChannel))return;
        int width=gui.width,height=gui.height;
        try {
            for(int[] size:new int[][]{{320,240},{460,340},{width,height}}) {
                gui.setWorldAndResolution(mc,size[0],size[1]);
                List<GuiButton> buttons=net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(GuiScreen.class,gui,"buttonList","field_146292_n");
                for(GuiButton a:buttons) {
                    if(!a.visible)continue;
                    require(a.width<=200,"button exceeds Minecraft texture width");
                    require(a.x>=0 && a.y>=0 && a.x+a.width<=gui.width && a.y+a.height<=gui.height,"button outside viewport");
                    for(GuiButton b:buttons)if(a!=b && b.visible)require(!overlaps(a.x,a.y,a.width,a.height,b.x,b.y,b.width,b.height),"buttons overlap: "+a.id+"/"+b.id);
                }
                for(Field f:gui.getClass().getDeclaredFields()) {
                    f.setAccessible(true);Object value=f.get(gui);
                    if(value instanceof HousingTextureList) {
                        checkList((HousingTextureList)value,buttons);
                        if(gui instanceof GuiSpaceDoor || gui instanceof GuiProgrammableTrapdoor)
                            checkDoorDesigns((HousingTextureList)value);
                        if(gui instanceof GuiProgrammableLight) {
                            Field options=HousingTextureList.class.getDeclaredField("options");options.setAccessible(true);
                            for(Object option:((java.util.Map<?,?>)options.get(value)).values()) {
                                HousingTextureList.Option item=(HousingTextureList.Option)option;
                                if("Lights".equals(com.vandorlabs.tiles.ScreenHousingTextures.category(item.choice)))
                                    require(!item.label.endsWith(" On"),"light label retains On suffix");
                            }
                        }
                    }
                    if(value instanceof ScreenTextureList) {
                        Field list=ScreenTextureList.class.getDeclaredField("list");list.setAccessible(true);
                        HousingTextureList picker=(HousingTextureList)list.get(value);checkList(picker,buttons);
                        Field input=ScreenTextureList.class.getDeclaredField("input");input.setAccessible(true);
                        Field options=HousingTextureList.class.getDeclaredField("options");options.setAccessible(true);
                        boolean controls=input.getBoolean(value);int artwork=0;
                        for(Object option:((java.util.Map<?,?>)options.get(picker)).values()) {
                            HousingTextureList.Option item=(HousingTextureList.Option)option;
                            if("Screens".equals(com.vandorlabs.tiles.ScreenHousingTextures.category(item.choice))) {
                                require(ScreenTextureList.artwork(item.choice,controls),"wrong height of screen artwork");
                                require(item.category.equals(controls?"Controls":"Screens"),"wrong screen category");artwork++;
                            }
                        }
                        require(artwork>0,"missing screen artwork category");
                    }
                    if(value instanceof GuiTextField) {
                        GuiTextField field=(GuiTextField)value;
                        for(GuiButton b:buttons)if(b.visible)require(!overlaps(field.x-1,field.y-1,field.width+2,field.height+2,b.x,b.y,b.width,b.height),"channel overlaps button "+b.id);
                    }
                }
                if(gui instanceof GuiSpaceDoor || gui instanceof GuiProgrammableTrapdoor) {
                    Field detail=gui.getClass().getDeclaredField(gui instanceof GuiSpaceDoor?"detail":"doorDetail");detail.setAccessible(true);
                    int initial=detail.getInt(gui),sizeId=gui instanceof GuiSpaceDoor?11:9;
                    GuiButton sizeButton=null;for(GuiButton b:buttons)if(b.id==sizeId)sizeButton=b;
                    require(sizeButton!=null,"missing door size button");
                    for(int step=1;step<=3;step++) {
                        click(gui,sizeButton.x+sizeButton.width/2,sizeButton.y+sizeButton.height/2);
                        require(detail.getInt(gui)==(initial+step)%2,"door size button failed");
                        for(Field f:gui.getClass().getDeclaredFields()){f.setAccessible(true);Object value=f.get(gui);if(value instanceof HousingTextureList)checkDoorDesigns((HousingTextureList)value);}
                    }
                }
                // Exercise tabs through the actual mouse path, then restore the original tab.
                Field tab=null;try{tab=gui.getClass().getDeclaredField("textureTab");tab.setAccessible(true);}catch(NoSuchFieldException ignored){}
                if(tab!=null) {
                    int original=tab.getInt(gui);
                    for(GuiButton b:buttons)if(b.visible && b.id>=90 && b.id<=93) {
                        click(gui,b.x+b.width/2,b.y+b.height/2);
                        require(tab.getInt(gui)==b.id-90,"tab click failed");
                        for(GuiButton a:buttons)if(a.visible)for(GuiButton other:buttons)if(a!=other && other.visible)
                            require(!overlaps(a.x,a.y,a.width,a.height,other.x,other.y,other.width,other.height),"active tab controls overlap");
                    }
                    for(GuiButton b:buttons)if(b.id==90+original)click(gui,b.x+b.width/2,b.y+b.height/2);
                }
                if(gui instanceof GuiProgrammableWall) {
                    Field face=GuiProgrammableWall.class.getDeclaredField("faceTarget");face.setAccessible(true);int original=face.getInt(gui);
                    int count=0;
                    for(GuiButton b:new java.util.ArrayList<>(buttons))if(b.id>=120 && b.id<=126) {
                        click(gui,b.x+b.width/2,b.y+b.height/2);require(face.getInt(gui)==b.id-121,"face button click failed");count++;
                    }
                    for(GuiButton b:new java.util.ArrayList<>(buttons))if(b.id==127) {
                        click(gui,b.x+b.width/2,b.y+b.height/2);require(face.getInt(gui)==-2,"side button click failed");
                    }
                    if(count>0) {
                        require(count==1 || count==7,"face selector lacks Main or one of six faces");
                        ((GuiProgrammableWall)gui).actionPerformed(new GuiButton(121+original,0,0,""));
                    }
                }
            }
        } catch(ReflectiveOperationException | java.io.IOException e){throw new IllegalStateException("dialog layout inspection failed",e);}
        finally{gui.setWorldAndResolution(mc,width,height);}
        System.out.println("[vandorlabs][reprolab] programmable-dialog-layout PASS "+gui.getClass().getSimpleName());
    }
    private static void checkDoorDesigns(HousingTextureList list)throws ReflectiveOperationException {
        Field options=HousingTextureList.class.getDeclaredField("options");options.setAccessible(true);
        java.util.Set<Integer> designs=new java.util.HashSet<>();
        for(Object option:((java.util.Map<?,?>)options.get(list)).values()) {
            HousingTextureList.Option item=(HousingTextureList.Option)option;
            com.google.gson.JsonObject entry=com.vandorlabs.tiles.ScreenHousingTextures.entry(item.choice);
            if(entry!=null && entry.has("design")) {
                require(designs.add(entry.get("design").getAsInt()),"duplicate door design in texture picker");
                require(!item.label.matches(".* (Small|Medium|Large)$"),"door size remains in design label");
            }
        }
        boolean large=designs.stream().anyMatch(design->design>=com.vandorlabs.tiles.TileEntitySpaceDoor.FIRST_DOUBLE_DESIGN);
        int expected=large?com.vandorlabs.tiles.TileEntitySpaceDoor.DESIGNS.length:com.vandorlabs.tiles.TileEntitySpaceDoor.FIRST_DOUBLE_DESIGN;
        require(designs.size()==expected,"door picker missing designs");
        for(int detail=0;detail<2;detail++) {
            HousingTextureList sized=HousingTextureList.forDoors(detail,0,0,100,list.selected(),large);
            java.util.Set<Integer> sizedDesigns=new java.util.HashSet<>();
            for(Object option:((java.util.Map<?,?>)options.get(sized)).values()) {
                HousingTextureList.Option item=(HousingTextureList.Option)option;
                com.google.gson.JsonObject entry=com.vandorlabs.tiles.ScreenHousingTextures.entry(item.choice);
                if(entry!=null && entry.has("design")) {
                    require(entry.get("detail").getAsInt()==detail,"wrong door size in picker");
                    require(sizedDesigns.add(entry.get("design").getAsInt()),"duplicate sized door design");
                    int next=HousingTextureList.doorSizeChoice(item.choice,(detail+1)%2);
                    require(com.vandorlabs.tiles.ScreenHousingTextures.entry(next).get("design").getAsInt()==entry.get("design").getAsInt(),"size change replaced door design");
                }
            }
            require(sizedDesigns.size()==expected,"sized door picker missing designs");
        }
    }
    private static void checkList(HousingTextureList list,List<GuiButton> buttons)throws ReflectiveOperationException {
        Field options=HousingTextureList.class.getDeclaredField("options");options.setAccessible(true);
        for(Object entry:((java.util.Map<?,?>)options.get(list)).values())
            require(com.vandorlabs.tiles.ScreenHousingTextures.visible(((HousingTextureList.Option)entry).choice),"hidden texture in picker");
        int x=integer(list,"x"),y=integer(list,"y"),width=integer(list,"width"),count=integer(list,"count");
        require(count>=7,"texture list has fewer than seven rows");
        for(GuiButton b:buttons)if(b.visible)require(!overlaps(x-1,y-1,width+9,count*HousingTextureList.ROW_HEIGHT+2,b.x,b.y,b.width,b.height),"texture list overlaps button "+b.id);
    }
    private static int integer(Object object,String name)throws ReflectiveOperationException {Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.getInt(object);}
    private static void click(GuiScreen gui,int x,int y)throws java.io.IOException {
        if(gui instanceof GuiProgrammableTrapdoor)((GuiProgrammableTrapdoor)gui).mouseClicked(x,y,0);
        else if(gui instanceof GuiSpaceDoor)((GuiSpaceDoor)gui).mouseClicked(x,y,0);
        else if(gui instanceof GuiProgrammableWall)((GuiProgrammableWall)gui).mouseClicked(x,y,0);
        else if(gui instanceof GuiProgrammableLight)((GuiProgrammableLight)gui).mouseClicked(x,y,0);
        else if(gui instanceof GuiAnimatedScreenSelector)((GuiAnimatedScreenSelector)gui).mouseClicked(x,y,0);
        else if(gui instanceof GuiProgrammableInput)((GuiProgrammableInput)gui).mouseClicked(x,y,0);
        else if(gui instanceof GuiProgrammableTrigger)((GuiProgrammableTrigger)gui).mouseClicked(x,y,0);
        else if(gui instanceof GuiProgrammableHalfConsole)((GuiProgrammableHalfConsole)gui).mouseClicked(x,y,0);
    }
    private static boolean overlaps(int x,int y,int w,int h,int bx,int by,int bw,int bh){return x<bx+bw && bx<x+w && y<by+bh && by<y+h;}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException("Programmable dialog: "+message);}
}
