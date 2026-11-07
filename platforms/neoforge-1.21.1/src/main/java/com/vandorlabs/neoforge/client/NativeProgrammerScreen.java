package com.vandorlabs.neoforge.client;

import com.vandorlabs.neoforge.credentials.NativeCredentialItems;
import com.vandorlabs.neoforge.programmer.*;
import com.vandorlabs.neoforge.screen.NativeChipJournal;
import com.vandorlabs.programmer.JournalText;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Dedicated inventory/journal editor. Vehicle modes and previews remain unavailable. */
public class NativeProgrammerScreen extends AbstractContainerScreen<NativeProgrammerMenu> {
    private static final int PAPER_TOP=126,PAPER_WIDTH=202;
    private static volatile long resourceGeneration;
    private long observedResource=-1;
    private final Consumer<ProgrammerSettingsPayload> dispatch;
    private final Map<Integer,Button> buttons=new HashMap<>();
    private EditBox titleField;
    private NativeProgrammerDraft draft,observed,lastSent;
    private ItemStack input=ItemStack.EMPTY,output=ItemStack.EMPTY,master=ItemStack.EMPTY;
    private boolean journalPage,editing,selectedAll,dragging;
    private int page,cursor,scroll,delay,dragOffset;
    private List<FormattedCharSequence> lines=List.of(),cursorLines=List.of();
    private String cachedText,cachedPrefix,footer="";
    private boolean ready;
    public NativeProgrammerScreen(NativeProgrammerMenu menu,Inventory inventory,Component title){this(menu,inventory,title,PacketDistributor::sendToServer);}
    public NativeProgrammerScreen(NativeProgrammerMenu menu,Inventory inventory,Component title,Consumer<ProgrammerSettingsPayload> dispatch){
        super(menu,inventory,title);this.dispatch=Objects.requireNonNull(dispatch);imageWidth=252;imageHeight=228;draft=menu.tile.draft();observed=lastSent=draft;loadInput();
    }
    public static void register(RegisterMenuScreensEvent event,MenuType<NativeProgrammerMenu> type){event.<NativeProgrammerMenu,NativeProgrammerScreen>register(type,NativeProgrammerScreen::new);}
    public static void registerReload(net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent event){event.registerReloadListener(new net.minecraft.server.packs.resources.SimplePreparableReloadListener<Void>(){
        @Override protected Void prepare(net.minecraft.server.packs.resources.ResourceManager resources,net.minecraft.util.profiling.ProfilerFiller profiler){return null;}
        @Override protected void apply(Void ignored,net.minecraft.server.packs.resources.ResourceManager resources,net.minecraft.util.profiling.ProfilerFiller profiler){resourceGeneration++;}
    });}
    private void ensureResources(){if(observedResource!=resourceGeneration){observedResource=resourceGeneration;invalidateText();refresh();}}
    public Button button(int id){return buttons.get(id);}public EditBox titleField(){return titleField;}
    public NativeProgrammerDraft editingDraft(){return draft;}public boolean journalPage(){return journalPage;}public boolean editing(){return editing;}
    public int page(){return page;}public int scroll(){return scroll;}public int visibleLines(){return (paperBottom()-PAPER_TOP-2)/9;}
    public int paperBottom(){return 164+extra();}private int extra(){return imageHeight-228;}
    private boolean chipInput(){return menu.contents().getItem(1).is(NativeCredentialItems.DATA_CHIP.get());}
    private void loadInput(){
        ItemStack next=menu.contents().getItem(1);if(!ItemStack.matches(input,next)&&NativeChipJournal.journal(next))journalPage=true;
        input=next.copy();output=menu.contents().getItem(2).copy();master=menu.contents().getItem(0).copy();draft=menu.tile.draft();
        if(!chipInput())draft=new NativeProgrammerDraft(false,false,draft.title(),draft.pages());
        cursor=draft.pages().get(page).length();if(titleField!=null)titleField.setValue(draft.title());invalidateText();
    }
    private Button add(int id,int x,int y,int width,int height,String text,Runnable action){var button=Button.builder(Component.literal(text),ignored->action.run()).bounds(leftPos+x,topPos+y,width,height).build();buttons.put(id,button);return addRenderableWidget(button);}
    @Override protected void init(){
        imageHeight=height>=264?264:228;super.init();buttons.clear();ready=false;
        titleField=new EditBox(font,leftPos+49,topPos+104,188,18,Component.literal("Journal title"));titleField.setMaxLength(40);titleField.setValue(draft.title());titleField.setResponder(value->{if(ready){draft=new NativeProgrammerDraft(false,draft.journal(),value,draft.pages());invalidateText();refresh();}});addRenderableWidget(titleField);
        add(5,15,80,72,18,"Inventory",()->selectPage(false));add(6,90,80,72,18,"Journal",()->selectPage(true));
        add(0,76,57,160,20,"",()->cycleType(1));add(1,15,166+extra(),24,18,"<",()->turn(-1));add(2,213,166+extra(),24,18,">",()->turn(1));
        add(7,165,205+extra(),72,20,"Done",this::onClose);add(4,15,205+extra(),72,20,"Program",()->send(1));add(8,90,205+extra(),72,20,"Copy",()->dispatch.accept(ProgrammerSettingsPayload.copy(menu.containerId)));
        ready=true;invalidateText();refresh();
    }
    private void invalidateText(){cachedText=cachedPrefix=null;}
    private void refresh(){
        if(titleField==null)return;boolean chip=chipInput();if(!chip||!draft.journal())journalPage=false;
        menu.journalPage(journalPage);button(6).visible=chip&&draft.journal();button(6).active=!journalPage;button(5).active=journalPage;
        button(0).setMessage(Component.literal("Type: "+(draft.journal()?"Journal":"Master")));button(0).active=chip;
        button(1).visible=button(2).visible=journalPage;button(1).active=page>0;button(2).active=page<7;
        titleField.setEditable(chip&&draft.journal());titleField.setVisible(journalPage);
        button(4).active=NativeProgrammerRules.problem(menu.contents().getItem(0),menu.contents().getItem(1),menu.contents().getItem(2),draft).isEmpty();
        button(8).active=NativeProgrammerRules.copyProblem(menu.contents().getItem(1),menu.contents().getItem(2)).isEmpty();
        String problem=menu.contents().getItem(2).isEmpty()?NativeProgrammerRules.problem(menu.contents().getItem(0),menu.contents().getItem(1),menu.contents().getItem(2),draft):NativeProgrammerRules.copyProblem(menu.contents().getItem(1),menu.contents().getItem(2));
        footer=font.plainSubstrByWidth(problem.isEmpty()?(button(8).active?"Ready to copy saved Input data":"Ready to program Input"):problem,222);
        if(!journalPage){editing=false;titleField.setFocused(false);}scroll=Math.max(0,Math.min(scroll,maxScroll()));
    }
    public void cycleType(int direction){if(!chipInput())return;draft=new NativeProgrammerDraft(false,!draft.journal(),draft.title(),draft.pages());invalidateText();refresh();}
    public void selectPage(boolean journal){journalPage=journal&&chipInput()&&draft.journal();editing=selectedAll=false;titleField.setFocused(false);refresh();}
    private void turn(int direction){page=Math.max(0,Math.min(7,page+direction));cursor=draft.pages().get(page).length();scroll=0;selectedAll=false;invalidateText();refresh();}
    private List<FormattedCharSequence> paperLines(){String text=chipInput()?(draft.journal()?draft.pages().get(page):"Master chips authorize company readers."):"Insert a Journal Chip in Input to view or edit its pages.";if(!Objects.equals(text,cachedText)){cachedText=text;lines=font.split(Component.literal(text),PAPER_WIDTH);}return lines;}
    private List<FormattedCharSequence> cursorLines(){String prefix=draft.pages().get(page).substring(0,cursor)+"_";if(!Objects.equals(prefix,cachedPrefix)){cachedPrefix=prefix;cursorLines=font.split(Component.literal(prefix),PAPER_WIDTH);}return cursorLines;}
    public int maxScroll(){return Math.max(0,paperLines().size()-visibleLines());}
    private int thumbHeight(){return Math.max(10,(paperBottom()-PAPER_TOP)*visibleLines()/Math.max(visibleLines(),paperLines().size()));}
    public int thumbTop(){int travel=paperBottom()-PAPER_TOP-thumbHeight();return PAPER_TOP+(maxScroll()==0?0:travel*scroll/maxScroll());}
    private void drag(double y){int travel=paperBottom()-PAPER_TOP-thumbHeight();scroll=travel<=0?0:(int)Math.round(Math.max(0,Math.min(travel,y-topPos-PAPER_TOP-dragOffset))*maxScroll()/travel);}
    private int cursorAt(double x,double y){String text=draft.pages().get(page);int wantedRow=scroll+Math.max(0,(int)(y-topPos-PAPER_TOP-2)/9),wantedX=Math.max(0,(int)x-leftPos-20),best=text.length(),distance=Integer.MAX_VALUE;for(int index=0;index<=text.length();index++){var prefix=font.split(Component.literal(text.substring(0,index)+"_"),PAPER_WIDTH);if(prefix.size()-1!=wantedRow)continue;int column=font.width(prefix.getLast())-font.width("_"),gap=Math.abs(column-wantedX);if(gap<distance){distance=gap;best=index;}}return best;}
    private void replace(String text){var pages=new ArrayList<>(draft.pages());pages.set(page,text);draft=new NativeProgrammerDraft(false,draft.journal(),draft.title(),pages);invalidateText();}
    public void insert(String value){if(!chipInput()||!draft.journal())return;if(selectedAll){replace("");cursor=0;selectedAll=false;}String text=draft.pages().get(page),add=JournalText.bounded(value,Math.max(0,512-text.length()));replace(text.substring(0,cursor)+add+text.substring(cursor));cursor+=add.length();}
    private void send(int operation){dispatch.accept(new ProgrammerSettingsPayload(menu.containerId,operation,draft));lastSent=observed=draft;delay=0;}
    private void flushDraft(){if(!draft.equals(lastSent))send(0);}
    /** Same first-observation plus eight-tick debounce used by the released editor. */
    public void advanceEditor(){
        if(!ItemStack.matches(menu.contents().getItem(1),input)){loadInput();refresh();}
        else if(!ItemStack.matches(menu.contents().getItem(2),output)||!ItemStack.matches(menu.contents().getItem(0),master)){output=menu.contents().getItem(2).copy();master=menu.contents().getItem(0).copy();refresh();}
        if(!draft.equals(observed)){observed=draft;delay=8;}else if(!draft.equals(lastSent)&&delay>0&&--delay==0)send(0);
    }
    @Override protected void containerTick(){super.containerTick();advanceEditor();}
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button==1&&button(0).active&&button(0).isMouseOver(x,y)){cycleType(-1);return true;}
        if(journalPage)for(int slot=0;slot<3;slot++){var s=menu.slots.get(slot);if(isHovering(s.x,s.y,16,16,x,y)){selectPage(false);break;}}
        if(journalPage&&button==0&&x>=leftPos+230&&x<leftPos+236&&y>=topPos+PAPER_TOP&&y<topPos+paperBottom()){int thumb=thumbTop();dragOffset=y>=topPos+thumb&&y<topPos+thumb+thumbHeight()?(int)y-topPos-thumb:thumbHeight()/2;dragging=true;editing=false;drag(y);return true;}
        if(journalPage&&button==0&&x>=leftPos+15&&x<leftPos+230&&y>=topPos+PAPER_TOP&&y<topPos+paperBottom()){editing=true;selectedAll=false;titleField.setFocused(false);cursor=cursorAt(x,y);cachedPrefix=null;return true;}
        editing=false;return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(dragging&&button==0){drag(y);return true;}return super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){dragging=false;return super.mouseReleased(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(journalPage&&vertical!=0){scroll=Math.max(0,Math.min(maxScroll(),scroll+(vertical>0?-1:1)));editing=false;return true;}return super.mouseScrolled(x,y,horizontal,vertical);}
    @Override public boolean charTyped(char character,int modifiers){if(journalPage&&titleField.isFocused()&&titleField.charTyped(character,modifiers))return true;if(editing){if(net.minecraft.util.StringUtil.isAllowedChatCharacter(character))insert(String.valueOf(character));return true;}return super.charTyped(character,modifiers);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(journalPage&&titleField.isFocused()&&titleField.keyPressed(key,scan,modifiers))return true;
        if(editing){String text=draft.pages().get(page);boolean control=(modifiers&2)!=0||hasControlDown();if(key==256){editing=false;return true;}if(control&&key==65){selectedAll=true;return true;}if(control&&key==67){minecraft.keyboardHandler.setClipboard(text);return true;}if(control&&key==86){insert(minecraft.keyboardHandler.getClipboard());return true;}if(selectedAll&&(key==259||key==261)){replace("");cursor=0;selectedAll=false;return true;}switch(key){case 259->{if(cursor>0){replace(text.substring(0,cursor-1)+text.substring(cursor));cursor--;}}case 261->{if(cursor<text.length())replace(text.substring(0,cursor)+text.substring(cursor+1));}case 263->cursor=Math.max(0,cursor-1);case 262->cursor=Math.min(text.length(),cursor+1);case 265->cursor=Math.max(0,cursor-36);case 264->cursor=Math.min(text.length(),cursor+36);case 268->cursor=0;case 269->cursor=text.length();case 257,335->insert("\n");default->{}}cachedPrefix=null;return true;}
        if(key==256||minecraft.options.keyInventory.matches(key,scan))flushDraft();return super.keyPressed(key,scan,modifiers);
    }
    @Override public void onClose(){flushDraft();super.onClose();}
    @Override public void removed(){flushDraft();super.removed();}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){g.fillGradient(0,0,width,height,0xC0101010,0xD0101010);renderBg(g,partial,x,y);}
    @Override protected void renderBg(GuiGraphics g,float partial,int x,int y){
        ensureResources();g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFF19232C);g.fill(leftPos,topPos,leftPos+imageWidth,topPos+24,0xFF304858);
        g.fill(leftPos+13,topPos+99,leftPos+239,topPos+100,0xFF506878);g.fill(leftPos+13,topPos+100,leftPos+14,topPos+185+extra(),0xFF506878);g.fill(leftPos+238,topPos+100,leftPos+239,topPos+185+extra(),0xFF506878);g.fill(leftPos+13,topPos+184+extra(),leftPos+239,topPos+185+extra(),0xFF506878);
        if(journalPage){g.fill(leftPos+14,topPos+PAPER_TOP-1,leftPos+238,topPos+paperBottom()+1,editing?0xFF506070:0xFF777777);g.fill(leftPos+15,topPos+PAPER_TOP,leftPos+237,topPos+paperBottom(),selectedAll?0xFF385570:0xFF101820);g.fill(leftPos+230,topPos+PAPER_TOP,leftPos+236,topPos+paperBottom(),0xFF303840);g.fill(leftPos+230,topPos+thumbTop(),leftPos+236,topPos+thumbTop()+thumbHeight(),maxScroll()>0?0xFF8BAEC0:0xFF536674);}
        for(var slot:menu.slots)if(slot.isActive()){int sx=leftPos+slot.x,sy=topPos+slot.y;g.fill(sx-1,sy-1,sx+17,sy+17,0xFF555555);g.fill(sx,sy,sx+16,sy+16,0xFF8B8B8B);}
    }
    @Override protected void renderLabels(GuiGraphics g,int x,int y){
        g.drawString(font,"Card / Chip Programmer",15,8,-1,false);g.drawString(font,"Master",15,26,0xFFCDD7E4,false);g.drawString(font,"Input",105,26,0xFFCDD7E4,false);g.drawString(font,"Output",195,26,0xFFCDD7E4,false);g.drawString(font,"Chip:",15,63,0xFFCDD7E4,false);
        if(journalPage){g.drawString(font,"Title:",15,109,0xFFCDD7E4,false);var text=paperLines();var caret=cursorLines();if(editing){if(caret.size()-1<scroll)scroll=Math.max(0,caret.size()-1);if(caret.size()>scroll+visibleLines())scroll=caret.size()-visibleLines();}scroll=Math.max(0,Math.min(scroll,maxScroll()));for(int line=scroll;line<text.size()&&line<scroll+visibleLines();line++)g.drawString(font,text.get(line),20,PAPER_TOP+2+(line-scroll)*9,-1,false);if(editing&&caret.size()>scroll&&caret.size()<=scroll+visibleLines()){int width=font.width(caret.getLast())-font.width("_");g.drawString(font,"_",20+width,PAPER_TOP+2+(caret.size()-1-scroll)*9,0xFFA0D8FF,false);}g.drawString(font,"Page "+(page+1)+" / 8",49,170+extra(),0xFFCDD7E4,false);}
        g.drawString(font,footer,15,190+extra(),button(4).active||button(8).active?0xFFA8DBB0:0xFFFF9999,false);
    }
}
