package com.vandorlabs.neoforge.client;

import com.vandorlabs.neoforge.*;
import com.vandorlabs.ui.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Native widgets around the current shared cube dialog contract. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeCubeScreen extends AbstractContainerScreen<ProgrammableMenu> {
    private ProgrammableBlockDialog.State state;
    private ProgrammableDialogLayout panel;
    private List<ProgrammableBlockDialog.Control> controls=List.of();
    private EditBox search;
    private final List<Integer> options=new ArrayList<>();
    private final Map<String,List<Integer>> groups=new HashMap<>();
    private final Set<String> expanded=new HashSet<>();
    private List<String> categories=List.of();
    private List<Integer> rows=List.of();
    private int scroll,dragOffset;
    private boolean dragging;
    @SubscribeEvent public static void register(RegisterMenuScreensEvent event){event.register(VandorLabsBootstrap.PROGRAMMABLE_MENU.get(),NativeCubeScreen::new);}
    public NativeCubeScreen(ProgrammableMenu menu,net.minecraft.world.entity.player.Inventory inventory,Component title){
        super(menu,inventory,Component.literal("Programmable Block"));state=menu.tile.settings();
        for(var choice:ModernFinishCatalog.allChoices()){
            int index=choice.choice();var entry=choice.entry();
            if(entry.visible()&&entry.general()){groups.computeIfAbsent(entry.category(),key->new ArrayList<>()).add(index);options.add(index);}
        }
        Set<String> paths=new HashSet<>(groups.keySet());for(String path:new ArrayList<>(paths))while(path.contains("/")){path=path.substring(0,path.lastIndexOf('/'));paths.add(path);}
        List<String> ordered=new ArrayList<>(paths);ordered.sort(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder()));categories=List.copyOf(ordered);
        for(var group:groups.values())group.sort(Comparator.comparing((Integer id)->ModernFinishCatalog.option(id).label(),String.CASE_INSENSITIVE_ORDER).thenComparingInt(Integer::intValue));
        reveal();
    }
    public ProgrammableBlockDialog.State editingState(){return state;}
    public int scroll(){return scroll;}
    public List<Integer> visibleRows(){return rows;}
    public ProgrammableDialogLayout panel(){return panel;}
    private void reveal(){var entry=ModernFinishCatalog.entry(state.selectedTexture());String path=entry.category();expanded.add(path);while(path.contains("/")){path=path.substring(0,path.lastIndexOf('/'));expanded.add(path);}}
    @Override protected void init(){super.init();panel=new ProgrammableDialogLayout(width,height);rebuild(true);}
    private void rebuild(boolean center){
        String query=search==null?"":search.getValue();clearWidgets();controls=ProgrammableBlockDialog.controls(panel,state,Component.translatable("gui.done").getString());
        for(var control:controls){
            if(control.tab)continue;
            Button button=Button.builder(Component.literal(control.label),ignored->action(control.id,1)).bounds(control.x,control.y,control.width,control.height).build();
            button.active=control.enabled;addRenderableWidget(button);
        }
        search=new EditBox(font,panel.listX+2,ProgrammableBlockDialog.listY(panel,state)+2,panel.listWidth+3,18,Component.literal("Search textures"));
        search.setMaxLength(128);search.setHint(Component.literal("Search textures"));search.setValue(query);search.setResponder(ignored->{scroll=0;rebuildRows();});addRenderableWidget(search);rebuildRows();
        if(center){int index=rows.indexOf(state.selectedTexture());if(index>=0)scroll=Math.max(0,Math.min(maximum(),index-visible()/2));}
    }
    private void rebuildRows(){List<Integer> next=new ArrayList<>();String query=search==null?"":search.getValue().trim().toLowerCase(Locale.ROOT);
        if(query.isEmpty())appendCategories("",next);
        else for(int id:options){var entry=ModernFinishCatalog.option(id);String text=(entry.label()+" "+entry.category()+" "+entry.texture()).toLowerCase(Locale.ROOT);boolean matches=true;for(String token:query.split("\\s+"))if(!text.contains(token))matches=false;if(matches)next.add(id);}
        rows=List.copyOf(next);scroll=Math.max(0,Math.min(maximum(),scroll));
    }
    private void appendCategories(String parent,List<Integer> target){for(int index=0;index<categories.size();index++){String category=categories.get(index);int slash=category.lastIndexOf('/');if(!(slash<0?"":category.substring(0,slash)).equals(parent))continue;
        target.add(-200000-index);if(expanded.contains(category)){appendCategories(category,target);target.addAll(groups.getOrDefault(category,List.of()));}}}
    private int top(){return ProgrammableBlockDialog.listY(panel,state)+22;}
    private int visible(){return Math.max(1,ProgrammableBlockDialog.listRows(panel,state)-1);}
    private int height(){return visible()*22;}
    private int maximum(){return DialogInput.maxScroll(rows.size(),visible());}
    private int thumbHeight(){return DialogInput.thumbHeight(height(),visible(),rows.size());}
    private int thumbTop(){return DialogInput.thumbY(top(),height(),thumbHeight(),scroll,maximum());}
    private int row(int row){int id=rows.get(scroll+row);if(search.getValue().trim().isEmpty()&&visible()>1&&row==0&&id>=0)return -200000-categories.indexOf(ModernFinishCatalog.option(id).category());return id;}
    private void send(){PacketDistributor.sendToServer(new FinishPayload(menu.tile.getBlockPos(),state.mainTexture,state.layout,state.facesEnabled,state.faces()));}
    public void action(int id,int direction){if(id==ProgrammableBlockDialog.DONE){onClose();return;}state=state.action(id,direction);
        if(id==ProgrammableBlockDialog.TEXTURE_LAYOUT||id==ProgrammableBlockDialog.FACE_OVERRIDES||id==ProgrammableBlockDialog.INHERIT)send();reveal();rebuild(true);}
    public void choose(int choice){state=state.choose(choice);send();rebuild(false);}
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button==0||button==1)for(var control:controls)if(control.enabled&&(button==0||control.id==ProgrammableBlockDialog.FACE_OVERRIDES||control.id==ProgrammableBlockDialog.TEXTURE_LAYOUT)&&x>=control.x&&x<control.x+control.width&&y>=control.y&&y<control.y+control.height){action(control.id,button==1?-1:1);return true;}
        if(button==0&&y>=top()&&y<top()+height()){
            if(x>=panel.listX+panel.listWidth&&x<panel.listX+panel.listWidth+7&&maximum()>0){dragOffset=y>=thumbTop()&&y<thumbTop()+thumbHeight()?(int)y-thumbTop():thumbHeight()/2;dragging=true;mouseDragged(x,y,button,0,0);return true;}
            if(x>=panel.listX&&x<panel.listX+panel.listWidth){int offset=(int)(y-top())/22;if(scroll+offset<rows.size()){int id=row(offset);if(id<0){String category=categories.get(-200000-id);if(!expanded.remove(category))expanded.add(category);rebuildRows();}else choose(id);}return true;}
        }
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(dragging){scroll=DialogInput.scrollForDrag((int)y,top(),height(),thumbHeight(),maximum(),dragOffset);return true;}return super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){dragging=false;return super.mouseReleased(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(vertical!=0&&x>=panel.listX&&x<panel.listX+panel.listWidth+7&&y>=top()&&y<top()+height()){scroll=DialogInput.wheel(scroll,maximum(),vertical>0?1:-1);return true;}return super.mouseScrolled(x,y,horizontal,vertical);}
    @Override public void renderBackground(GuiGraphics graphics,int x,int y,float partial){graphics.fillGradient(0,0,width,height,0xC0101010,0xD0101010);renderBg(graphics,partial,x,y);}
    @Override protected void renderBg(GuiGraphics graphics,float partial,int mouseX,int mouseY){
        graphics.fill(panel.left,panel.top,panel.left+panel.width,panel.top+panel.height,0xFF101012);graphics.fill(panel.left,panel.top,panel.left+panel.width,panel.top+24,0xFF202028);graphics.drawString(font,title,panel.left+8,panel.top+8,0xFFFFFFFF,false);
        if(state.selectedTexture()>=ModernFinishCatalog.ENTRIES.size()&&ModernFinishCatalog.renderChoice(state.selectedTexture())==0)graphics.drawString(font,"Saved artwork unavailable",panel.left+panel.width-font.width("Saved artwork unavailable")-12,panel.top+8,0xFFFFCF70,false);
        for(var control:controls)if(control.tab){graphics.fill(control.x,control.y,control.x+control.width,control.y+control.height,control.enabled?0xFF203540:0xFF365568);graphics.drawCenteredString(font,control.label,control.x+control.width/2,control.y+5,0xFFE0F0FF);}
        graphics.fill(panel.listX-1,top()-1,panel.listX+panel.listWidth+8,top()+height()+1,0xFF000000);
        for(int offset=0;offset<visible()&&scroll+offset<rows.size();offset++){int id=row(offset),yy=top()+offset*22;boolean selected=id==state.selectedTexture();if(selected)graphics.fill(panel.listX,yy,panel.listX+panel.listWidth,yy+22,0xFF2A4A6A);
            if(id<0){String category=categories.get(-200000-id);int indent=Math.min(48,8*(category.length()-category.replace("/","").length()));graphics.drawString(font,(expanded.contains(category)?"- ":"+ ")+category.substring(category.lastIndexOf('/')+1),panel.listX+3+indent,yy+7,0xFFABCFE8,false);}
            else{var entry=ModernFinishCatalog.option(id);var sprite=minecraft.getTextureAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS).apply(net.minecraft.resources.ResourceLocation.parse(entry.texture()));int w=16,h=16;if(entry.u()<entry.v())w=Math.max(1,Math.round(16*entry.u()/entry.v()));else h=Math.max(1,Math.round(16*entry.v()/entry.u()));
                graphics.blit(panel.listX+3+(16-w)/2,yy+3+(16-h)/2,0,w,h,new CroppedSprite(sprite,entry.u(),entry.v()));graphics.drawString(font,font.plainSubstrByWidth(entry.label(),panel.listWidth-26),panel.listX+23,yy+7,selected?0xFFFFE08A:0xFFD8D8D8,false);}
        }
        if(maximum()>0){graphics.fill(panel.listX+panel.listWidth,top(),panel.listX+panel.listWidth+7,top()+height(),0xFF303038);graphics.fill(panel.listX+panel.listWidth,thumbTop(),panel.listX+panel.listWidth+7,thumbTop()+thumbHeight(),0xFF808090);}
    }
    private static final class CroppedSprite extends net.minecraft.client.renderer.texture.TextureAtlasSprite {
        private final net.minecraft.client.renderer.texture.TextureAtlasSprite original;private final float u,v;
        CroppedSprite(net.minecraft.client.renderer.texture.TextureAtlasSprite original,float u,float v){super(original.atlasLocation(),original.contents(),1,1,0,0);this.original=original;this.u=u;this.v=v;}
        @Override public float getU0(){return original.getU0();}@Override public float getU1(){return original.getU0()+(original.getU1()-original.getU0())*u;}
        @Override public float getV0(){return original.getV0();}@Override public float getV1(){return original.getV0()+(original.getV1()-original.getV0())*v;}
    }
    @Override protected void renderLabels(GuiGraphics graphics,int mouseX,int mouseY){}
    @Override public boolean isPauseScreen(){return false;}
}
