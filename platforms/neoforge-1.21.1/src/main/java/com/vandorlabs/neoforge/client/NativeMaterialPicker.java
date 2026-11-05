package com.vandorlabs.neoforge.client;

import com.vandorlabs.neoforge.*;
import com.vandorlabs.ui.*;
import java.util.*;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Native material list view, shared by family screens; owns no settings or packet policy. */
public final class NativeMaterialPicker {
    private final Minecraft minecraft=Minecraft.getInstance();
    private final net.minecraft.client.gui.Font font=minecraft.font;
    private final IntConsumer chosen;
    private int x,y,width,allocatedRows,selected;
    private final Map<net.minecraft.client.renderer.texture.TextureAtlasSprite,CroppedSprite> crops=new IdentityHashMap<>();
    private EditBox search;
    private final List<Integer> options=new ArrayList<>();
    private final Map<Integer,ModernFinishCatalog.Entry> special=new HashMap<>();
    private final Map<String,List<Integer>> groups=new HashMap<>();
    private final Set<String> expanded=new HashSet<>();
    private List<String> categories=List.of();
    private List<Integer> rows=List.of();
    private int scroll,dragOffset;
    private boolean dragging;
    private boolean sampling=true;
    public static final int CUSTOM_ROW=Integer.MIN_VALUE;
    private NativeInventorySamplePicker inventoryPicker;
    public NativeInventorySamplePicker inventoryPicker(){return inventoryPicker;}
    public NativeMaterialPicker(int selected,IntPredicate include,IntConsumer chosen){
        this.selected=selected;this.chosen=chosen;
        for(var choice:ModernFinishCatalog.allChoices()){
            int index=choice.choice();var entry=choice.entry();
            if(entry.visible()&&include.test(index)){groups.computeIfAbsent(entry.category(),key->new ArrayList<>()).add(index);options.add(index);}
        }
        categories=List.copyOf(TextureListLayout.categories(groups.keySet()));
        for(var group:groups.values())group.sort((a,b)->TextureListLayout.compareOptions(ModernFinishCatalog.option(a).label(),a,ModernFinishCatalog.option(b).label(),b));
        reveal();
    }
    /** The legacy frame sentinel is an actual material row, separate from encoded category rows. */
    public NativeMaterialPicker originalArtwork(String texture){return specialMaterial(-1,"Original artwork","Frame",texture);}
    /** UI-only negative choices retain insertion/search order and never become category rows. */
    public NativeMaterialPicker specialMaterial(int id,String label,String category,String texture){
        if(id>=0||id<=-200000)throw new IllegalArgumentException("Special material ID overlaps category domain");
        if(!special.containsKey(id)){special.put(id,new ModernFinishCatalog.Entry("picker_"+id,label,category,true,false,-1,texture,1,1));options.add(special.size()-1,id);groups.computeIfAbsent(category,key->new ArrayList<>()).add(id);}
        categories=List.copyOf(TextureListLayout.categories(groups.keySet()));
        for(var group:groups.values())group.sort((a,b)->TextureListLayout.compareOptions(label(a),a,label(b),b));reveal();return this;
    }
    /** Glass finish lists intentionally omit the inventory sampler in the current editor. */
    public NativeMaterialPicker sampling(boolean enabled){sampling=enabled;if(!enabled)closeInventory();if(search!=null)rebuildRows();return this;}
    private boolean option(int id){return id>=0||special.containsKey(id);}
    private ModernFinishCatalog.Entry entry(int id){var value=special.get(id);return value==null?ModernFinishCatalog.option(id):value;}
    private String label(int id){return entry(id).label();}
    private String category(int id){return entry(id).category();}
    public int selected(){return selected;}
    public int scroll(){return scroll;}
    public List<Integer> rows(){return rows;}
    public EditBox searchField(){return search;}
    public void select(int choice,boolean center){selected=choice;reveal();if(search!=null){rebuildRows();if(center)center();}}
    private void reveal(){if(selected<0&&!special.containsKey(selected))return;TextureListLayout.expandAncestors(expanded,special.containsKey(selected)?category(selected):ModernFinishCatalog.entry(selected).category());}
    private void center(){int index=rows.indexOf(com.vandorlabs.tiles.MaterialChoicePolicy.isCustom(selected)?CUSTOM_ROW:selected);if(index>=0)scroll=TextureListLayout.centeredScroll(index,visible(),maximum());}
    public void closeInventory(){inventoryPicker=null;if(search!=null){search.visible=true;search.active=true;}}
    public void layout(int x,int y,int width,int allocatedRows,boolean center){
        boolean first=search==null;var previous=inventoryPicker;boolean same=this.x==x&&this.y==y&&this.width==width&&this.allocatedRows==allocatedRows;
        String query=search==null?"":search.getValue();this.x=x;this.y=y;this.width=width;this.allocatedRows=allocatedRows;inventoryPicker=null;
        search=new EditBox(font,x+2,y+2,width+3,18,Component.literal("Search textures"));search.setMaxLength(128);search.setHint(Component.literal("Search textures"));search.setValue(query);search.setResponder(ignored->{scroll=0;rebuildRows();});rebuildRows();if(center||first)center();
        if(previous!=null&&same){customInventory();inventoryPicker.restoreScroll(previous.scroll());}
    }
    private void choose(int choice){selected=choice;closeInventory();chosen.accept(choice);}
    private void rebuildRows(){List<Integer> next=new ArrayList<>();String query=search==null?"":TextureListLayout.normalizeQuery(search.getValue());
        if(query.isEmpty())TextureListLayout.appendCategories(next,categories,groups,expanded,id->id);
        else for(int id:options){var entry=entry(id);if(TextureListLayout.matches(query,entry.label(),entry.category(),entry.texture()))next.add(id);}
        if(sampling)next.add(CUSTOM_ROW);rows=List.copyOf(next);scroll=Math.max(0,Math.min(maximum(),scroll));
    }
    public int top(){return y+22;}
    public int visible(){return Math.max(1,allocatedRows-1);}
    private int height(){return visible()*22;}
    private int maximum(){return DialogInput.maxScroll(rows.size(),visible());}
    private int thumbHeight(){return DialogInput.thumbHeight(height(),visible(),rows.size());}
    private int thumbTop(){return DialogInput.thumbY(top(),height(),thumbHeight(),scroll,maximum());}
    private int row(int row){int id=rows.get(scroll+row);if(TextureListLayout.pinCategory(row,visible(),search.getValue().trim().isEmpty())&&option(id))return TextureListLayout.categoryRow(categories.indexOf(category(id)));return id;}
    private void customInventory(){inventoryPicker=new NativeInventorySamplePicker(x,y,width+7,height()+22,stack->{int value=NativeBlockMaterials.sample(stack);inventoryPicker=null;choose(value);},()->{inventoryPicker=null;search.visible=true;search.active=true;});search.visible=false;search.active=false;}
    public boolean click(double x,double y,int button){
        if(inventoryPicker!=null)return inventoryPicker.click((int)x,(int)y,button);
        if(button==0&&y>=top()&&y<top()+height()){
            if(x>=this.x+width&&x<this.x+width+7&&maximum()>0){dragOffset=y>=thumbTop()&&y<thumbTop()+thumbHeight()?(int)y-thumbTop():thumbHeight()/2;dragging=true;drag(x,y);return true;}
            if(x>=this.x&&x<this.x+width){int offset=(int)(y-top())/22;if(scroll+offset<rows.size()){int id=row(offset);if(id==CUSTOM_ROW){customInventory();return true;}if(!option(id)){String category=categories.get(TextureListLayout.categoryIndex(id));if(!expanded.remove(category))expanded.add(category);rebuildRows();}else choose(id);}return true;}
        }
        return false;
    }
    /** Focused text owns shortcuts before AbstractContainerScreen handles inventory/hotbar keys. */
    public boolean keyPressed(int key,int scan,int modifiers){
        if(inventoryPicker!=null||search==null||!search.visible||!search.canConsumeInput()||key==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)return false;
        search.keyPressed(key,scan,modifiers);return true;
    }
    public boolean drag(double x,double y){if(dragging){scroll=DialogInput.scrollForDrag((int)y,top(),height(),thumbHeight(),maximum(),dragOffset);return true;}return false;}
    public void release(){dragging=false;}
    public boolean wheel(double x,double y,double vertical){if(inventoryPicker!=null&&inventoryPicker.wheel((int)x,(int)y,vertical>0?1:vertical<0?-1:0))return true;if(vertical!=0&&x>=this.x&&x<this.x+width+7&&y>=top()&&y<top()+height()){scroll=DialogInput.wheel(scroll,maximum(),vertical>0?1:-1);return true;}return false;}
    public void render(GuiGraphics graphics,int mouseX,int mouseY){
        graphics.fill(this.x-1,top()-1,this.x+width+8,top()+height()+1,0xFF000000);
        if(inventoryPicker!=null){inventoryPicker.draw(graphics,mouseX,mouseY);return;}
        for(int offset=0;offset<visible()&&scroll+offset<rows.size();offset++){int id=row(offset),yy=top()+offset*22;boolean highlighted=id==selected||id==CUSTOM_ROW&&com.vandorlabs.tiles.MaterialChoicePolicy.isCustom(selected);if(highlighted)graphics.fill(this.x,yy,this.x+width,yy+22,0xFF2A4A6A);
            if(id==CUSTOM_ROW){var material=NativeBlockMaterials.material(selected);String label="Custom...";
                if(com.vandorlabs.tiles.MaterialChoicePolicy.isCustom(selected)){label="Custom: "+(material==null?"Custom (missing block)":material.entry().label());if(material!=null){if(material.door()){graphics.blit(this.x+7,yy+3,0,8,8,material.upper());graphics.blit(this.x+7,yy+11,0,8,8,material.lower());}else graphics.blit(this.x+3,yy+3,0,16,16,material.lower());}}
                graphics.drawString(font,font.plainSubstrByWidth(label,width-26),this.x+23,yy+7,0xFFABCFE8,false);}
            else if(!option(id)){String category=categories.get(TextureListLayout.categoryIndex(id));int indent=TextureListLayout.indent(category);graphics.drawString(font,(expanded.contains(category)?"- ":"+ ")+TextureListLayout.categoryLabel(category),this.x+3+indent,yy+7,0xFFABCFE8,false);}
            else{var entry=entry(id);var sprite=minecraft.getTextureAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS).apply(net.minecraft.resources.ResourceLocation.parse(entry.texture()));int w=16,h=16;if(entry.u()<entry.v())w=Math.max(1,Math.round(16*entry.u()/entry.v()));else h=Math.max(1,Math.round(16*entry.v()/entry.u()));
                graphics.blit(this.x+3+(16-w)/2,yy+3+(16-h)/2,0,w,h,cropped(sprite,entry.u(),entry.v()));graphics.drawString(font,font.plainSubstrByWidth(entry.label(),width-26),this.x+23,yy+7,highlighted?0xFFFFE08A:0xFFD8D8D8,false);}
        }
        if(maximum()>0){graphics.fill(this.x+width,top(),this.x+width+7,top()+height(),0xFF303038);graphics.fill(this.x+width,thumbTop(),this.x+width+7,thumbTop()+thumbHeight(),0xFF808090);}
    }
    private net.minecraft.client.renderer.texture.TextureAtlasSprite cropped(net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,float u,float v){
        if(u==1&&v==1)return sprite;
        CroppedSprite existing=crops.get(sprite);if(existing!=null&&existing.u==u&&existing.v==v)return existing;
        if(crops.size()>=64)crops.clear();var result=new CroppedSprite(sprite,u,v);crops.put(sprite,result);return result;
    }
    private static final class CroppedSprite extends net.minecraft.client.renderer.texture.TextureAtlasSprite {
        private final net.minecraft.client.renderer.texture.TextureAtlasSprite original;private final float u,v;
        CroppedSprite(net.minecraft.client.renderer.texture.TextureAtlasSprite original,float u,float v){super(original.atlasLocation(),original.contents(),1,1,0,0);this.original=original;this.u=u;this.v=v;}
        @Override public float getU0(){return original.getU0();}@Override public float getU1(){return original.getU0()+(original.getU1()-original.getU0())*u;}
        @Override public float getV0(){return original.getV0();}@Override public float getV1(){return original.getV0()+(original.getV1()-original.getV0())*v;}
    }
}
