package com.vandorlabs.client;

import com.vandorlabs.tiles.ScreenHousingTextures;
import com.vandorlabs.render.ScreenSurface;
import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.VertexFormat;

/** Selected-row and atlas-coordinate regressions for the shared artwork picker. */
final class ArtworkPickerRuntimeChecks {
    @SuppressWarnings("unchecked") static void run()throws Exception{
        List<HousingTextureList.Option> entries=new ArrayList<>();
        for(int i=0;i<60;i++)entries.add(new HousingTextureList.Option(i,"Texture "+i,"Frame/Interior","minecraft:blocks/stone"));
        HousingTextureList previous=new HousingTextureList(0,0,200,0,8,entries).visibleRows(4);
        HousingTextureList next=new HousingTextureList(0,0,200,59,8,entries).visibleRows(4).keepView(previous);
        Field scroll=HousingTextureList.class.getDeclaredField("scroll"),rows=HousingTextureList.class.getDeclaredField("rows");scroll.setAccessible(true);rows.setAccessible(true);
        int at=((List<Integer>)rows.get(next)).indexOf(59),top=scroll.getInt(next);
        require(at>top&&at<top+4,"tab switch must reveal selected texture below pinned heading");
        HousingTextureList surfaces=HousingTextureList.forFrameSurfaces(0,0,200,-1,8);
        Field options=HousingTextureList.class.getDeclaredField("options");options.setAccessible(true);
        Map<Integer,HousingTextureList.Option> offered=(Map<Integer,HousingTextureList.Option>)options.get(surfaces);
        int general=0,selectedGeneral=-1;
        for(int i=0;i<ScreenHousingTextures.IDS.length;i++)if(ScreenHousingTextures.visible(i)&&HousingTextureList.generalTexture(i)){
            int choice=ScreenHousingTextures.choiceAt(i);general++;selectedGeneral=choice;
            require(offered.containsKey(choice),"general material missing from frame surface picker");
            require(com.vandorlabs.tiles.FrameAppearance.validSurface(choice),"server or save rejects offered general finish");
            require(!com.vandorlabs.tiles.FrameAppearance.valid(choice),"general finish leaked into Front validation");
        }
        require(general>0,"general finish fixture");
        HousingTextureList material=HousingTextureList.forFrameSurfaces(0,0,200,selectedGeneral,8).visibleRows(4);
        int selectedRow=((List<Integer>)rows.get(material)).indexOf(selectedGeneral),selectedTop=scroll.getInt(material);
        require(material.click(4,(selectedRow-selectedTop)*HousingTextureList.ROW_HEIGHT+4,0)&&material.picked()&&material.selected()==selectedGeneral,"actual general frame finish selection");
        int halves=0,full=0;
        for(int i=0;i<ScreenHousingTextures.IDS.length;i++)if("Frame".equals(ScreenHousingTextures.category(i))){
            boolean half=ScreenHousingTextures.entry(i).has("frameAspect")&&ScreenHousingTextures.entry(i).get("frameAspect").getAsDouble()>1.01;
            require(HousingTextureList.frameAllowed(i,false)==!half,"glass/unsplit screen half-frame filter");
            require(HousingTextureList.frameAllowed(i,true),"split screen retains frame choice");
            if(half)halves++;else full++;
        }
        require(halves>0&&full>0,"full and half frame fixtures");
        for(boolean ceiling:new boolean[]{false,true})for(double[] uv:new double[][]{{.2,.3,.4,.5},{.2,.3}}){
            BufferBuilder buffer=new BufferBuilder(2048);VertexFormat format=BlockSurfaceFormat.get();buffer.begin(7,format);
            TEAnimatedScreenSelector.drawDiagonalInputQuad(buffer,ScreenSurface.quad(ScreenSurface.Kind.DIAGONAL,false),uv,ceiling);buffer.finishDrawing();
            int stride=format.getNextOffset(),offset=format.getUvOffsetById(0);double minU=10,maxU=-10;
            for(int i=0;i<4;i++){double u=buffer.getByteBuffer().getFloat(i*stride+offset);minU=Math.min(minU,u);maxU=Math.max(maxU,u);}
            require(Math.abs(minU-(uv.length>2?uv[2]:0))<.000001&&Math.abs(maxU-(uv.length>2?uv[3]:1))<.000001,"diagonal static atlas and animated strip U bounds");buffer.reset();
        }
        System.out.println("[vandorlabs][reprolab] artwork-picker PASS tab-selected-visible general-frame-surfaces full-half-filter diagonal-atlas-strip-uv floor-ceiling");
    }
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("Artwork picker: "+message);}
}
