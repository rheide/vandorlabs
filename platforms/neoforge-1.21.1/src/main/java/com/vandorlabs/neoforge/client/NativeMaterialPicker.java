package com.vandorlabs.neoforge.client;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.ui.DialogInput;
import com.vandorlabs.ui.TextureListLayout;
import java.util.*;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

/** Native paint/events for the shared texture tree; every dialog reuses this adapter. */
public final class NativeMaterialPicker {
    public record Entry(int id,String label,String category,String texture,float u,float v){}
    private record Special(String label,Runnable action){}
    private final int x,y,width,count;
    private final IntConsumer picked;
    private final Map<Integer,Entry> entries=new LinkedHashMap<>();
    private final Map<Integer,Special> special=new LinkedHashMap<>();
    private final Map<String,List<Integer>> groups=new HashMap<>();
    private final Set<String> expanded=new HashSet<>();
    private final List<String> categories;
    private List<Integer> rows=List.of();
    private int selected,scroll,dragOffset;
    private boolean dragging;
    public NativeMaterialPicker(int x,int y,int width,int count,int selected,IntConsumer picked){this(x,y,width,count,selected,generalEntries(),picked);}
    public NativeMaterialPicker(int x,int y,int width,int count,int selected,List<Entry> entries,IntConsumer picked){
        this.x=x;this.y=y;this.width=width;this.count=Math.max(2,count);this.picked=picked;
        for(Entry entry:entries){this.entries.put(entry.id,entry);groups.computeIfAbsent(entry.category,key->new ArrayList<>()).add(entry.id);}
        for(List<Integer> group:groups.values())group.sort((a,b)->TextureListLayout.compareOptions(this.entries.get(a).label,a,this.entries.get(b).label,b));
        categories=TextureListLayout.categories(groups.keySet());setSelected(selected);
    }
    public static List<Entry> generalEntries(){List<Entry> entries=new ArrayList<>();for(int i=0;i<ModernFinishCatalog.ENTRIES.size();i++){var entry=ModernFinishCatalog.ENTRIES.get(i);if(entry.visible()&&entry.general()&&entry.canonical()==i)entries.add(new Entry(i,entry.label(),entry.category(),entry.texture(),entry.u(),entry.v()));}return entries;}
    public NativeMaterialPicker special(int id,String label,Runnable action){special.put(id,new Special(label,action));rebuild();centerSelected();return this;}
    public void keepView(NativeMaterialPicker previous){if(previous!=null){expanded.addAll(previous.expanded);rebuild();scroll=Math.max(0,Math.min(maximum(),previous.scroll));}}
    private void rebuild(){int[] trailing=new int[special.size()];int i=0;for(int id:special.keySet())trailing[i++]=id;rows=TextureListLayout.rows(categories,groups,expanded,trailing);scroll=Math.max(0,Math.min(maximum(),scroll));}
    public void setSelected(int selected){this.selected=selected;Entry entry=entries.get(selected);if(entry!=null){String path=entry.category;expanded.add(path);while(path.contains("/")){path=path.substring(0,path.lastIndexOf('/'));expanded.add(path);}}rebuild();centerSelected();}
    public void centerSelected(){int index=rows.indexOf(selected);if(index>=0)scroll=Math.max(0,Math.min(maximum(),index-count/2));}
    public int selected(){return selected;}
    public int scroll(){return scroll;}
    public List<Integer> rows(){return List.copyOf(rows);}
    public List<String> categories(){return List.copyOf(categories);}
    private int height(){return count*22;}private int maximum(){return DialogInput.maxScroll(rows.size(),count);}
    private int thumbHeight(){return DialogInput.thumbHeight(height(),count,rows.size());}private int thumbY(){return DialogInput.thumbY(y,height(),thumbHeight(),scroll,maximum());}
    private int visible(int row){int id=rows.get(scroll+row);return special.containsKey(id)?id:TextureListLayout.pinnedChoice(row,id,categories,id>=0?entries.get(id).category:null);}
    public boolean click(double mouseX,double mouseY,int button){
        if(button!=0||mouseY<y||mouseY>=y+height())return false;
        if(mouseX>=x+width&&mouseX<x+width+7&&maximum()>0){dragOffset=mouseY>=thumbY()&&mouseY<thumbY()+thumbHeight()?(int)mouseY-thumbY():thumbHeight()/2;dragging=true;drag(mouseY);return true;}
        if(mouseX<x||mouseX>=x+width)return false;int row=(int)(mouseY-y)/22;if(scroll+row>=rows.size())return true;
        int id=visible(row);if(special.containsKey(id)){selected=id;special.get(id).action.run();}
        else if(id>=0){selected=id;picked.accept(id);}
        else {String category=categories.get(-id-1);if(!expanded.remove(category))expanded.add(category);rebuild();}
        return true;
    }
    public boolean drag(double mouseY){if(!dragging)return false;scroll=DialogInput.scrollForDrag((int)mouseY,y,height(),thumbHeight(),maximum(),dragOffset);return true;}
    public void release(){dragging=false;}
    public boolean wheel(double mouseX,double mouseY,double delta){if(delta==0||mouseX<x||mouseX>=x+width+7||mouseY<y||mouseY>=y+height())return false;scroll=DialogInput.wheel(scroll,maximum(),delta>0?1:-1);return true;}
    public void draw(GuiGraphics graphics,Font font,int mouseX,int mouseY){
        graphics.fill(x-1,y-1,x+width+8,y+height()+1,0xFF000000);graphics.fill(x,y,x+width,y+height(),0xFF0A0A0C);
        for(int row=0;row<count&&scroll+row<rows.size();row++){
            int id=visible(row),yy=y+row*22;boolean selected=id==this.selected,hover=mouseX>=x&&mouseX<x+width&&mouseY>=yy&&mouseY<yy+22;
            if(selected||hover)graphics.fill(x,yy,x+width,yy+22,selected?0xFF2A4A6A:0xFF1A1A20);
            if(special.containsKey(id))graphics.drawString(font,font.plainSubstrByWidth(special.get(id).label,width-6),x+3,yy+7,selected?0xFFFFE08A:0xFFABCFE8);
            else if(id<0){String category=categories.get(-id-1);int indent=TextureListLayout.indent(category);String label=(expanded.contains(category)?"- ":"+ ")+category.substring(category.lastIndexOf('/')+1);graphics.drawString(font,font.plainSubstrByWidth(label,width-6-indent),x+3+indent,yy+7,0xFFABCFE8);}
            else {Entry entry=entries.get(id);float aspect=entry.u/entry.v;int tw=16,th=16;if(aspect<1)tw=Math.max(1,Math.round(16*aspect));else th=Math.max(1,Math.round(16/aspect));
                TextureAtlasSprite sprite=Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(ResourceLocation.parse(entry.texture));graphics.blit(x+3+(16-tw)/2,yy+3+(16-th)/2,0,tw,th,new CroppedSprite(sprite,entry.u,entry.v));
                graphics.drawString(font,font.plainSubstrByWidth(entry.label,width-26),x+23,yy+7,selected?0xFFFFE08A:0xFFD8D8D8);
            }
        }
        if(maximum()>0){graphics.fill(x+width,y,x+width+7,y+height(),0xFF303038);graphics.fill(x+width,thumbY(),x+width+7,thumbY()+thumbHeight(),0xFF808090);}
    }
    private static final class CroppedSprite extends TextureAtlasSprite {
        private final TextureAtlasSprite original;private final float u,v;
        CroppedSprite(TextureAtlasSprite original,float u,float v){super(original.atlasLocation(),original.contents(),1,1,0,0);this.original=original;this.u=u;this.v=v;}
        @Override public float getU0(){return original.getU0();}@Override public float getU1(){return original.getU0()+(original.getU1()-original.getU0())*u;}@Override public float getV0(){return original.getV0();}@Override public float getV1(){return original.getV0()+(original.getV1()-original.getV0())*v;}
    }
}
