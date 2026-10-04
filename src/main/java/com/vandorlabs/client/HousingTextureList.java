package com.vandorlabs.client;

import com.vandorlabs.tiles.ScreenHousingTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.I18n;
import java.util.*;

/** One categorized, scrollable artwork picker with atlas thumbnails. */
final class HousingTextureList {
    static final int ROW_HEIGHT=22;
    private static final int CUSTOM_ROW=-100000,REDSTONE_ROW=-100001;
    private Runnable redstoneAction;
    HousingTextureList redstone(Runnable action){redstoneAction=action;setSelected(selected());return this;}
    private final Map<Integer,Option> options=new LinkedHashMap<>();
    private final Map<String,List<Option>> groups=new HashMap<>();
    private final boolean nativeOptions;
    static final class Option {
        final int choice; final String label,category,texture;
        Option(int choice,String label,String category,String texture){this.choice=choice;this.label=label;this.category=category;this.texture=texture;}
    }
    private final int x,y,width;
    private int count;
    private final List<String> categories=new ArrayList<>();
    private final Set<String> expanded=new HashSet<>();
    private final List<Integer> rows=new ArrayList<>();
    private int selected,scroll,dragOffset,custom=-1,missing=-1;
    private java.util.function.IntConsumer customConsumer;
    HousingTextureList custom(java.util.function.IntConsumer consumer){customConsumer=consumer;setSelected(selected());return this;}
    private boolean dragging,picked;
    boolean picked(){return picked;}
    HousingTextureList(int x,int y,int width,int selected){this(x,y,width,selected,8);}
    HousingTextureList(int x,int y,int width,int selected,int count) {this(x,y,width,selected,count,null);}
    HousingTextureList(int x,int y,int width,int selected,int count,List<Option> nativeEntries) {
        this(x,y,width,selected,count,nativeEntries,HousingTextureList::generalTexture);
    }
    HousingTextureList(int x,int y,int width,int selected,int count,List<Option> nativeEntries,java.util.function.IntPredicate include) {
        this(x,y,width,selected,count,nativeEntries,include,ScreenHousingTextures::category);
    }
    HousingTextureList(int x,int y,int width,int selected,int count,List<Option> nativeEntries,java.util.function.IntPredicate include,java.util.function.IntFunction<String> category) {
        this.x=x;this.y=y;this.width=width;this.count=Math.max(2,count*12/ROW_HEIGHT);
        nativeOptions=nativeEntries!=null;
        if(nativeOptions)for(Option option:nativeEntries)options.put(option.choice,option);
        else for(int i=0;i<ScreenHousingTextures.IDS.length;i++)if(ScreenHousingTextures.visible(i) && include.test(i))options.put(i,new Option(i,name(i),category.apply(i),ScreenHousingTextures.fullTexture(i)));
        for(Option option:options.values())groups.computeIfAbsent(option.category,key->new ArrayList<>()).add(option);
        categories.addAll(groups.keySet());categories.sort(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder()));
        for(List<Option> group:groups.values())group.sort(Comparator.comparing((Option option)->option.label,String.CASE_INSENSITIVE_ORDER).thenComparingInt(option->option.choice));
        setSelected(selected);
    }
    static boolean generalTexture(int choice) {
        String category=ScreenHousingTextures.category(choice);
        return !category.equals("Screens") && !category.equals("Lights") && !category.equals("Doors");
    }
    static HousingTextureList forCategory(String category,int x,int y,int width,int selected) {
        return forCategory(category,x,y,width,selected,8);
    }
    static HousingTextureList forCategory(String category,int x,int y,int width,int selected,int count) {
        return new HousingTextureList(x,y,width,selected,count,null,
                choice->generalTexture(choice) || category.equals(ScreenHousingTextures.category(choice)));
    }
    /** One row per door design, using the size selected beside the list. */
    static HousingTextureList forDoors(int detail,int x,int y,int width,int selected) {
        return new HousingTextureList(x,y,width,selected,8,null,choice->{
            com.google.gson.JsonObject entry=ScreenHousingTextures.entry(choice);
            return generalTexture(choice) || "Doors".equals(ScreenHousingTextures.category(choice))
                    && (entry==null || !entry.has("detail") || entry.get("detail").getAsInt()==detail);
        });
    }
    static int doorDetail(int choice) {
        com.google.gson.JsonObject entry=ScreenHousingTextures.entry(choice);
        return entry!=null && entry.has("detail")?entry.get("detail").getAsInt():0;
    }
    static int doorSizeChoice(int choice,int detail) {
        com.google.gson.JsonObject entry=ScreenHousingTextures.entry(choice);
        return entry!=null && entry.has("design")?ScreenHousingTextures.doorIndex(entry.get("design").getAsInt(),detail):choice;
    }
    HousingTextureList visibleRows(int rows) {
        count=Math.max(2,rows);
        setSelected(selected());
        return this;
    }
    void setSelected(int choice) {
        custom=!nativeOptions && com.vandorlabs.tiles.CustomBlockMaterials.isCustom(choice)?choice:-1;
        missing=!nativeOptions && choice>=com.vandorlabs.tiles.FilesystemTextures.ID_BASE && ScreenHousingTextures.localIndex(choice)==0?choice:-1;
        selected=choice==com.vandorlabs.tiles.TileEntityAnimatedScreenSelector.REDSTONE_SURFACE?REDSTONE_ROW:custom>=0?CUSTOM_ROW:nativeOptions?choice:ScreenHousingTextures.localIndex(ScreenHousingTextures.clamp(choice));
        Option option=options.get(selected);if(option!=null)expanded.add(option.category);rebuild();
        int index=rows.indexOf(selected);scroll=Math.max(0,Math.min(maxScroll(),index-count/2));
    }
    private void rebuild() {
        rows.clear();
        for(int c=0;c<categories.size();c++) {
            String category=categories.get(c);rows.add(-c-1);
            if(expanded.contains(category)) {
                for(Option option:groups.get(category))rows.add(option.choice);
            }
        }
        if(customConsumer!=null)rows.add(CUSTOM_ROW);
        if(redstoneAction!=null)rows.add(REDSTONE_ROW);
        scroll=Math.min(scroll,maxScroll());
    }
    /** Pin the category of the first visible texture into the top row. */
    private int visibleChoice(int row) {
        int choice=rows.get(scroll+row);
        if(row==0 && choice>=0)return -categories.indexOf(options.get(choice).category)-1;
        return choice;
    }
    int selected(){return selected==REDSTONE_ROW?com.vandorlabs.tiles.TileEntityAnimatedScreenSelector.REDSTONE_SURFACE:custom>=0?custom:missing>=0?missing:nativeOptions?selected:ScreenHousingTextures.choiceAt(selected);}
    private int height(){return ROW_HEIGHT*count;}
    private int maxScroll(){return Math.max(0,rows.size()-count);}
    private int thumbHeight(){return Math.max(8,height()*count/Math.max(count,rows.size()));}
    private int thumbY(){return y+(height()-thumbHeight())*scroll/Math.max(1,maxScroll());}
    boolean click(int mouseX,int mouseY,int button) {
        picked=false;
        if(button!=0 || mouseY<y || mouseY>=y+height())return false;
        if(mouseX>=x+width && mouseX<x+width+7 && maxScroll()>0) {
            dragOffset=mouseY>=thumbY() && mouseY<thumbY()+thumbHeight()?mouseY-thumbY():thumbHeight()/2;
            dragging=true;drag(mouseY);return true;
        }
        if(mouseX<x || mouseX>=x+width)return false;
        int index=scroll+(mouseY-y)/ROW_HEIGHT;if(index>=rows.size())return true;
        int choice=visibleChoice((mouseY-y)/ROW_HEIGHT);
        if(choice==REDSTONE_ROW){selected=REDSTONE_ROW;custom=-1;missing=-1;redstoneAction.run();return true;}
        if(choice==CUSTOM_ROW){Minecraft mc=Minecraft.getMinecraft();net.minecraft.item.ItemStack carried=mc.player.inventory.getItemStack();mc.player.inventory.setItemStack(net.minecraft.item.ItemStack.EMPTY);mc.displayGuiScreen(new GuiCustomTexture(mc.currentScreen,value->{setSelected(value);customConsumer.accept(value);}));mc.player.inventory.setItemStack(carried);return true;}
        if(choice>=0){selected=choice;custom=-1;missing=-1;picked=true;}
        else {String category=categories.get(-choice-1);if(!expanded.remove(category))expanded.add(category);rebuild();}
        return true;
    }
    boolean drag(int mouseY) {
        if(!dragging)return false;
        scroll=GuiProgrammableWall.scrollForDrag(mouseY,y,height(),thumbHeight(),maxScroll(),dragOffset);return true;
    }
    void release(){dragging=false;}
    boolean wheel(int mouseX,int mouseY,int delta) {
        if(delta==0 || mouseX<x || mouseX>=x+width+7 || mouseY<y || mouseY>=y+height())return false;
        scroll=Math.max(0,Math.min(maxScroll(),scroll+(delta>0?-1:1)));return true;
    }
    static String name(int choice) {
        if(com.vandorlabs.tiles.CustomBlockMaterials.isCustom(choice))return CustomBlockTextures.label(choice);
        choice=ScreenHousingTextures.localIndex(choice);
        String label=ScreenHousingTextures.label(choice);
        if(label!=null && "Lights".equals(ScreenHousingTextures.category(choice)))label=label.replaceFirst(" On$","");
        if(label!=null && "Doors".equals(ScreenHousingTextures.category(choice)))label=label.replaceFirst(" (Small|Medium|Large)$","");
        return label!=null?label:I18n.format("tile.vandorlabs."+ScreenHousingTextures.IDS[choice]+".name");
    }
    private void drawThumbnail(net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,int yy) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);GlStateManager.color(1,1,1,1);GlStateManager.enableBlend();
        float aspect=UnifiedTextureSprites.aspect(sprite);int tw=16,th=16;
        if(aspect<1)tw=Math.max(1,Math.round(16*aspect));else th=Math.max(1,Math.round(16/aspect));
        new Gui().drawTexturedModalRect(x+3+(16-tw)/2,yy+3+(16-th)/2,sprite,tw,th);
        GlStateManager.disableBlend();
    }
    void draw(FontRenderer font,int mouseX,int mouseY) {
        Gui.drawRect(x-1,y-1,x+width+8,y+height()+1,0xFF000000);
        Gui.drawRect(x,y,x+width,y+height(),0xFF0A0A0C);
        Minecraft mc=Minecraft.getMinecraft();
        for(int row=0;row<count && scroll+row<rows.size();row++) {
            int choice=visibleChoice(row),yy=y+row*ROW_HEIGHT;
            boolean hover=mouseX>=x && mouseX<x+width && mouseY>=yy && mouseY<yy+ROW_HEIGHT;
            if(choice==selected || hover)Gui.drawRect(x,yy,x+width,yy+ROW_HEIGHT,choice==selected?0xFF2A4A6A:0xFF1A1A20);
            if(choice==REDSTONE_ROW){font.drawStringWithShadow("Redstone...",x+3,yy+7,choice==selected?0xFFFFE08A:0xFFABCFE8);}
            else if(choice==CUSTOM_ROW){
                if(custom>=0) {
                    if(CustomBlockTextures.isDoor(custom)) {
                        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);GlStateManager.color(1,1,1,1);GlStateManager.enableBlend();
                        new Gui().drawTexturedModalRect(x+7,yy+3,CustomBlockTextures.sprite(custom,true),8,8);
                        new Gui().drawTexturedModalRect(x+7,yy+11,CustomBlockTextures.sprite(custom,false),8,8);
                        GlStateManager.disableBlend();
                    } else drawThumbnail(CustomBlockTextures.sprite(custom,false),yy);
                }
                font.drawStringWithShadow(font.trimStringToWidth(custom>=0?"Custom: "+name(custom):"Custom...",width-(custom>=0?26:6)),x+(custom>=0?23:3),yy+7,choice==selected?0xFFFFE08A:0xFFABCFE8);
            }
            else if(choice<0) {
                String category=categories.get(-choice-1);
                font.drawStringWithShadow(font.trimStringToWidth((expanded.contains(category)?"- ":"+ ")+category,width-6),x+3,yy+7,0xFFABCFE8);
            } else {
                drawThumbnail(mc.getTextureMapBlocks().getAtlasSprite(options.get(choice).texture),yy);
                font.drawStringWithShadow(font.trimStringToWidth(options.get(choice).label,width-26),x+23,yy+7,choice==selected?0xFFFFE08A:0xFFD8D8D8);
            }
        }
        if(maxScroll()>0){Gui.drawRect(x+width,y,x+width+7,y+height(),0xFF303038);Gui.drawRect(x+width,thumbY(),x+width+7,thumbY()+thumbHeight(),0xFF808090);}
    }
}
