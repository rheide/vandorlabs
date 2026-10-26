package com.vandorlabs.programmer.client;

import com.vandorlabs.programmer.*;
import com.vandorlabs.network.PacketHandler;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;
import java.io.IOException;
import java.io.File;
import java.util.List;

/** Compact inventory and journal pages share the same three physical machine slots. */
public class GuiProgrammer extends GuiContainer {
    private static final int PAPER_TOP=126,PAPER_BOTTOM=164;
    private final ProgrammerContainer container;
    private GuiTextField title;
    private final String[] pages=new String[ChipJournal.MAX_PAGES];
    private boolean journal,editing,selectedAll,journalPage;
    private int page,cursor,scroll,draftDelay;
    private String lastSentDraft,observedDraft;
    private ItemStack input=ItemStack.EMPTY,output=ItemStack.EMPTY;
    private GuiButton type,previous,next,done,program,copy,inventoryTab,journalTab;

    public GuiProgrammer(ProgrammerContainer container){super(container);this.container=container;xSize=252;ySize=228;load();}
    private void load(){
        if(!ItemStack.areItemStacksEqual(input,container.tile.getStackInSlot(1))&&ChipJournal.journal(container.tile.getStackInSlot(1)))journalPage=true;
        journal=container.tile.journal&&ChipJournal.isChip(container.tile.getStackInSlot(1));System.arraycopy(container.tile.pages,0,pages,0,pages.length);cursor=pages[page].length();
        input=container.tile.getStackInSlot(1).copy();output=container.tile.getStackInSlot(2).copy();if(title!=null)title.setText(container.tile.title);
    }
    @Override public void initGui(){
        String oldTitle=title==null?container.tile.title:title.getText();super.initGui();Keyboard.enableRepeatEvents(true);
        title=new GuiTextField(0,fontRenderer,guiLeft+49,guiTop+104,188,18);title.setMaxStringLength(ChipJournal.TITLE_LIMIT);title.setText(oldTitle);
        buttonList.add(inventoryTab=new GuiButton(5,guiLeft+15,guiTop+80,72,18,"Inventory"));
        buttonList.add(journalTab=new GuiButton(6,guiLeft+90,guiTop+80,72,18,"Journal"));
        buttonList.add(type=new GuiButton(0,guiLeft+76,guiTop+57,160,20,""));
        buttonList.add(previous=new GuiButton(1,guiLeft+15,guiTop+166,24,18,"<"));
        buttonList.add(next=new GuiButton(2,guiLeft+213,guiTop+166,24,18,">"));
        buttonList.add(done=new GuiButton(7,guiLeft+165,guiTop+205,72,20,"Done"));
        buttonList.add(program=new GuiButton(4,guiLeft+15,guiTop+205,72,20,"Program"));
        buttonList.add(copy=new GuiButton(8,guiLeft+90,guiTop+205,72,20,"Copy"));refresh();
        if(lastSentDraft==null)lastSentDraft=observedDraft=draftKey();
    }
    private void refresh(){
        boolean journalInput=journal&&ChipJournal.isChip(container.tile.getStackInSlot(1));
        journalTab.visible=journalInput;if(!journalInput)journalPage=false;
        type.displayString="Type: "+(journal?"Journal":"Master");type.enabled=ChipJournal.isChip(container.tile.getStackInSlot(1));program.enabled=container.tile.canProgram(journal);copy.enabled=container.tile.canCopy();previous.visible=next.visible=journalPage;
        previous.enabled=page>0;next.enabled=page<pages.length-1;title.setEnabled(journal&&ChipJournal.isChip(container.tile.getStackInSlot(1)));title.setVisible(journalPage);
        inventoryTab.enabled=journalPage;journalTab.enabled=!journalPage;
        // Slot numbers and inventory ownership are unchanged. Only the local GUI coordinates move.
        for(int i=0;i<inventorySlots.inventorySlots.size();i++){
            Slot slot=inventorySlots.inventorySlots.get(i);
            if(i<3){slot.xPos=18+i*90;slot.yPos=38;}
            else {int j=i-3;slot.xPos=journalPage?-10000:44+(j%9)*18;slot.yPos=j<27?104+(j/9)*18:166;}
        }
        if(!journalPage||!journal||!ChipJournal.isChip(container.tile.getStackInSlot(1))){editing=false;title.setFocused(false);}
    }
    private String draftKey(){StringBuilder key=new StringBuilder().append(journal).append('\u0000').append(title==null?container.tile.title:title.getText());for(String text:pages)key.append('\u0000').append(text);return key.toString();}
    protected void dispatch(ProgrammerMessage message){PacketHandler.INSTANCE.sendToServer(message);}
    private void send(boolean write){dispatch(new ProgrammerMessage(inventorySlots.windowId,write,journal,title.getText(),pages));lastSentDraft=observedDraft=draftKey();draftDelay=0;}
    /** Vanilla sends CloseWindow inside the inherited key handler, so flush first. */
    protected void inheritedKey(char character,int key)throws IOException{super.keyTyped(character,key);}
    @Override protected void actionPerformed(GuiButton button){
        if(button.id==0&&type.enabled)journal=!journal;
        else if(button.id==1){page--;cursor=pages[page].length();scroll=0;selectedAll=false;}
        else if(button.id==2){page++;cursor=pages[page].length();scroll=0;selectedAll=false;}
        else if(button.id==5||button.id==6){journalPage=button.id==6;editing=false;selectedAll=false;title.setFocused(false);}
        else if(button.id==7){send(false);mc.player.closeScreen();return;}else if(button.id==4&&program.enabled)send(true);else if(button.id==8&&copy.enabled)dispatch(ProgrammerMessage.copy(inventorySlots.windowId));
        refresh();
    }
    @Override public void updateScreen(){
        super.updateScreen();title.updateCursorCounter();ItemStack current=container.tile.getStackInSlot(1);
        if(!ItemStack.areItemStacksEqual(current,input)){load();refresh();}
        else if(!ItemStack.areItemStacksEqual(container.tile.getStackInSlot(2),output)){output=container.tile.getStackInSlot(2).copy();refresh();}
        String currentDraft=draftKey();
        if(!currentDraft.equals(observedDraft)){observedDraft=currentDraft;draftDelay=8;}
        else if(!currentDraft.equals(lastSentDraft)&&draftDelay>0&&--draftDelay==0)send(false);refresh();
    }
    @Override protected void mouseClicked(int x,int y,int button)throws IOException{
        if(button==1&&type.enabled&&x>=type.x&&x<type.x+type.width&&y>=type.y&&y<type.y+type.height){journal=!journal;refresh();return;}
        selectInventoryForSlot(x,y);
        if(journalPage)title.mouseClicked(x,y,button);
        editing=journalPage&&ChipJournal.isChip(container.tile.getStackInSlot(1))&&journal&&x>=guiLeft+15&&x<guiLeft+237&&y>=guiTop+PAPER_TOP&&y<guiTop+PAPER_BOTTOM;
        if(editing){title.setFocused(false);cursor=pages[page].length();selectedAll=false;return;}
        super.mouseClicked(x,y,button);
    }
    private void selectInventoryForSlot(int x,int y){if(journalPage)for(int i=0;i<3;i++){Slot slot=inventorySlots.inventorySlots.get(i);if(x>=guiLeft+slot.xPos-1&&x<guiLeft+slot.xPos+17&&y>=guiTop+slot.yPos-1&&y<guiTop+slot.yPos+17){journalPage=false;refresh();break;}}}
    private void insert(String value){
        if(selectedAll){pages[page]="";cursor=0;selectedAll=false;}
        String text=pages[page];int room=ChipJournal.PAGE_LIMIT-text.length();String add=ChipJournal.bounded(value,Math.max(0,room));pages[page]=text.substring(0,cursor)+add+text.substring(cursor);cursor+=add.length();
    }
    @Override protected void keyTyped(char character,int key)throws IOException{
        if(journalPage&&journal&&ChipJournal.isChip(container.tile.getStackInSlot(1))&&title.isFocused()&&title.textboxKeyTyped(character,key))return;
        if(editing){String text=pages[page];
            if(key==Keyboard.KEY_ESCAPE){editing=false;return;}
            if(GuiScreen.isKeyComboCtrlV(key)){insert(GuiScreen.getClipboardString());return;}
            if(GuiScreen.isKeyComboCtrlC(key)){GuiScreen.setClipboardString(text);return;}
            if(GuiScreen.isKeyComboCtrlA(key)){selectedAll=true;return;}
            if(selectedAll&&(key==Keyboard.KEY_BACK||key==Keyboard.KEY_DELETE)){pages[page]="";cursor=0;selectedAll=false;return;}
            if(key==Keyboard.KEY_BACK&&cursor>0){pages[page]=text.substring(0,cursor-1)+text.substring(cursor);cursor--;}
            else if(key==Keyboard.KEY_DELETE&&cursor<text.length())pages[page]=text.substring(0,cursor)+text.substring(cursor+1);
            else if(key==Keyboard.KEY_LEFT)cursor=Math.max(0,cursor-1);
            else if(key==Keyboard.KEY_RIGHT)cursor=Math.min(text.length(),cursor+1);
            else if(key==Keyboard.KEY_UP)cursor=Math.max(0,cursor-36);
            else if(key==Keyboard.KEY_DOWN)cursor=Math.min(text.length(),cursor+36);
            else if(key==Keyboard.KEY_HOME)cursor=0;
            else if(key==Keyboard.KEY_END)cursor=text.length();
            else if(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_NUMPADENTER)insert("\n");
            else if(net.minecraft.util.ChatAllowedCharacters.isAllowedCharacter(character))insert(String.valueOf(character));
            return;
        }
        if(key==Keyboard.KEY_ESCAPE||key==mc.gameSettings.keyBindInventory.getKeyCode()){
            if(!draftKey().equals(lastSentDraft))send(false);
        }
        inheritedKey(character,key);
    }
    @Override public void handleMouseInput()throws IOException{
        super.handleMouseInput();int wheel=org.lwjgl.input.Mouse.getEventDWheel();if(journalPage&&wheel!=0){scroll=Math.max(0,scroll+(wheel>0?-1:1));editing=false;}
    }
    @Override public void onGuiClosed(){send(false);Keyboard.enableRepeatEvents(false);super.onGuiClosed();}
    @Override public void drawScreen(int mouseX,int mouseY,float partial){
        net.minecraft.client.renderer.GlStateManager.disableLighting();net.minecraft.client.renderer.GlStateManager.disableDepth();net.minecraft.client.renderer.GlStateManager.color(1,1,1,1);
        drawDefaultBackground();super.drawScreen(mouseX,mouseY,partial);
        net.minecraft.client.renderer.GlStateManager.disableLighting();net.minecraft.client.renderer.GlStateManager.disableDepth();
        if(journalPage)title.drawTextBox();renderHoveredToolTip(mouseX,mouseY);
    }
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int mouseX,int mouseY){
        drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xff19232c);drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+24,0xff304858);
        drawRect(guiLeft+13,guiTop+99,guiLeft+239,guiTop+100,0xff506878);
        drawRect(guiLeft+13,guiTop+100,guiLeft+14,guiTop+185,0xff506878);
        drawRect(guiLeft+238,guiTop+100,guiLeft+239,guiTop+185,0xff506878);
        drawRect(guiLeft+13,guiTop+184,guiLeft+239,guiTop+185,0xff506878);
        if(journalPage){drawRect(guiLeft+14,guiTop+PAPER_TOP-1,guiLeft+238,guiTop+PAPER_BOTTOM+1,editing?0xff506070:0xff777777);drawRect(guiLeft+15,guiTop+PAPER_TOP,guiLeft+237,guiTop+PAPER_BOTTOM,selectedAll?0xff385570:0xff101820);}
        for(int i=0;i<inventorySlots.inventorySlots.size();i++)if(i<3||!journalPage){Slot slot=inventorySlots.inventorySlots.get(i);int x=guiLeft+slot.xPos,y=guiTop+slot.yPos;drawRect(x-1,y-1,x+17,y+17,0xff555555);drawRect(x,y,x+16,y+16,0xff8b8b8b);}
    }
    @Override protected void drawGuiContainerForegroundLayer(int x,int y){
        fontRenderer.drawString("Card / Chip Programmer",15,8,0xffffff);
        fontRenderer.drawString("Master",15,26,0xcdd7e4);fontRenderer.drawString("Input",105,26,0xcdd7e4);fontRenderer.drawString("Output",195,26,0xcdd7e4);fontRenderer.drawString("Chip:",15,63,0xcdd7e4);
        if(journalPage){
            fontRenderer.drawString("Title:",15,109,0xcdd7e4);String text=ChipJournal.isChip(container.tile.getStackInSlot(1))?(journal?pages[page]:"Master chips authorize company readers."):"Insert a Journal Chip in Input to view or edit its pages.";
            List<String> lines=fontRenderer.listFormattedStringToWidth(text,212),cursorLines=fontRenderer.listFormattedStringToWidth(pages[page].substring(0,cursor)+"_",212);
            if(editing){if(cursorLines.size()-1<scroll)scroll=Math.max(0,cursorLines.size()-1);if(cursorLines.size()>scroll+4)scroll=cursorLines.size()-4;}
            scroll=Math.max(0,Math.min(scroll,Math.max(0,lines.size()-4)));int row=0;
            for(int i=scroll;i<lines.size()&&row<4;i++)fontRenderer.drawString(lines.get(i),20,PAPER_TOP+2+row++*9,0xffffff);
            if(journal&&editing&&cursorLines.size()>scroll&&cursorLines.size()<=scroll+4){String last=cursorLines.get(cursorLines.size()-1);int width=fontRenderer.getStringWidth(last)-fontRenderer.getStringWidth("_");fontRenderer.drawString("_",20+width,PAPER_TOP+2+(cursorLines.size()-1-scroll)*9,0xa0d8ff);}
            fontRenderer.drawString("Page "+(page+1)+" / "+pages.length,49,170,0xcdd7e4);
        }
        String problem=container.tile.getStackInSlot(2).isEmpty()?container.tile.problem(journal):container.tile.copyProblem();fontRenderer.drawString(fontRenderer.trimStringToWidth(problem.isEmpty()?(copy.enabled?"Ready to copy saved Input data":"Ready to program Input"):problem,222),15,190,problem.isEmpty()?0xa8dbb0:0xff9999);
    }

    public static void checkEditor(net.minecraft.client.Minecraft mc,File output)throws Exception{
        ScaledResolution ordinary=new ScaledResolution(mc);net.minecraft.inventory.Container previousContainer=mc.player.openContainer;
        try{for(int[] size:new int[][]{{320,240},{ordinary.getScaledWidth(),ordinary.getScaledHeight()}}){
            ProgrammerTile tile=new ProgrammerTile();ItemStack fixture=new ItemStack(com.vandorlabs.items.ModItems.DATA_CHIP);ChipJournal.write(fixture,true,"Fixture",new String[0]);tile.setInventorySlotContents(1,fixture);java.util.List<ProgrammerMessage> sent=new java.util.ArrayList<>();GuiProgrammer gui=new GuiProgrammer(new ProgrammerContainer(mc.player.inventory,tile)){@Override protected void dispatch(ProgrammerMessage message){sent.add(message);}};gui.setWorldAndResolution(mc,size[0],size[1]);
            try{
                if(!gui.journalPage)throw new IllegalStateException("Inserted Journal Input automatically displays contents and pagination");gui.mouseClicked(gui.inventoryTab.x+4,gui.inventoryTab.y+4,0);
                checkLayout(gui,size);
                for(Slot slot:gui.inventorySlots.inventorySlots){gui.drawScreen(gui.guiLeft+slot.xPos+8,gui.guiTop+slot.yPos+8,0);if(gui.getSlotUnderMouse()!=slot)throw new IllegalStateException("Programmer inventory slot hit "+slot.slotNumber);}
                captureDialog(mc,output,gui,size,"inventory");
                for(int i=0;i<3;i++){gui.journalPage=true;gui.refresh();Slot selected=gui.inventorySlots.inventorySlots.get(i);gui.selectInventoryForSlot(gui.guiLeft+selected.xPos+8,gui.guiTop+selected.yPos+8);if(gui.journalPage)throw new IllegalStateException("Blank/populated physical slot selects Inventory "+i);}
                gui.mouseClicked(gui.journalTab.x+4,gui.journalTab.y+4,0);if(!gui.journalPage)throw new IllegalStateException("Programmer journal tab hit");checkLayout(gui,size);
                gui.drawScreen(gui.guiLeft+25,gui.guiTop+PAPER_TOP+10,0);if(gui.getSlotUnderMouse()!=null)throw new IllegalStateException("Journal paper overlaps an inventory hit region");
                gui.mouseClicked(gui.guiLeft+25,gui.guiTop+PAPER_TOP+10,0);gui.keyTyped('A',0);gui.keyTyped('\u03a9',0);gui.keyTyped('\n',Keyboard.KEY_RETURN);gui.keyTyped('B',0);gui.keyTyped('\b',Keyboard.KEY_BACK);
                if(!gui.pages[0].equals("A\u03a9\n"))throw new IllegalStateException("Programmer editor keyboard/newline/backspace");
                gui.selectedAll=true;gui.insert("Pasted text\nSecond line");if(!gui.pages[0].equals("Pasted text\nSecond line"))throw new IllegalStateException("Programmer editor replace selection/paste");
                gui.mouseClicked(gui.next.x+4,gui.next.y+4,0);gui.mouseClicked(gui.guiLeft+25,gui.guiTop+PAPER_TOP+10,0);gui.insert("Page two");gui.mouseClicked(gui.previous.x+4,gui.previous.y+4,0);
                if(!gui.pages[0].startsWith("Pasted")||!gui.pages[1].equals("Page two"))throw new IllegalStateException("Programmer editor page retention");
                gui.mouseClicked(gui.type.x+2,gui.type.y+2,1);if(gui.journal||gui.journalTab.visible||gui.journalPage)throw new IllegalStateException("Master Input hides Journal and exits editor");gui.mouseClicked(gui.type.x+2,gui.type.y+2,1);if(!gui.journalTab.visible)throw new IllegalStateException("Journal type enables Journal tab");gui.mouseClicked(gui.journalTab.x+4,gui.journalTab.y+4,0);
                captureDialog(mc,output,gui,size,"journal");
                gui.mouseClicked(gui.inventoryTab.x+4,gui.inventoryTab.y+4,0);if(gui.journalPage||!gui.pages[0].startsWith("Pasted"))throw new IllegalStateException("Programmer inventory tab preserves editor");checkLayout(gui,size);
                if(!gui.done.displayString.equals("Done"))throw new IllegalStateException("Programmer standard Done button");for(GuiButton button:gui.buttonList)if(button.displayString.toLowerCase(java.util.Locale.ROOT).contains("save draft"))throw new IllegalStateException("Programmer obsolete save button");
                ItemStack inputJournal=tile.removeStackFromSlot(1);tile.setInventorySlotContents(2,inputJournal);gui.updateScreen();if(gui.program.enabled||gui.type.enabled||gui.journalTab.visible||gui.journalPage)throw new IllegalStateException("Output-only journal hides Journal and never enables programming/type/title edits");gui.mouseClicked(gui.journalTab.x+4,gui.journalTab.y+4,0);gui.mouseClicked(gui.guiLeft+25,gui.guiTop+PAPER_TOP+10,0);if(gui.editing)throw new IllegalStateException("Output-only journal cannot edit pages");
                tile.setInventorySlotContents(1,tile.removeStackFromSlot(2));gui.updateScreen();if(!gui.program.enabled||!gui.type.enabled||!gui.pages[0].isEmpty())throw new IllegalStateException("Moving physical journal Output to Input re-enables Program and loads its actual pages");
                tile.setInventorySlotContents(0,new ItemStack(com.vandorlabs.items.ModItems.DATA_CHIP,1,1));tile.setInventorySlotContents(1,new ItemStack(com.vandorlabs.items.ModItems.ACCESS_CARD));gui.updateScreen();if(gui.type.enabled||gui.journal||!gui.program.enabled||gui.journalTab.visible)throw new IllegalStateException("Access Card Input hides Journal, disables Type and allows company-master programming");
                tile.setInventorySlotContents(2,new ItemStack(com.vandorlabs.items.ModItems.ACCESS_CARD,1,6));gui.updateScreen();if(!gui.copy.enabled||gui.program.enabled)throw new IllegalStateException("Occupied matching cards enable Copy instead of Program");sent.clear();gui.mouseClicked(gui.copy.x+5,gui.copy.y+5,0);if(sent.size()!=1)throw new IllegalStateException("Actual Copy button dispatches one action");io.netty.buffer.ByteBuf wire=io.netty.buffer.Unpooled.buffer();try{sent.get(0).toBytes(wire);if(wire.getUnsignedByte(4)!=ProgrammerMessage.COPY||wire.readableBytes()!=24)throw new IllegalStateException("Copy button dispatches bounded separate operation2");}finally{wire.release();}
                tile.setInventorySlotContents(2,new ItemStack(com.vandorlabs.items.ModItems.DATA_CHIP,1,2));gui.updateScreen();if(gui.copy.enabled)throw new IllegalStateException("Wrong-kind occupied Output disables Copy");
                tile.setInventorySlotContents(1,fixture.copy());gui.updateScreen();gui.pages[0]="Local pending Journal draft";gui.title.setText("Local pending title");sent.clear();gui.mouseClicked(gui.copy.x+4,gui.copy.y+4,0);if(sent.size()!=1||!tile.copyInput())throw new IllegalStateException("Journal Copy click and server-equivalent action");gui.updateScreen();if(!gui.pages[0].equals("Local pending Journal draft")||!gui.title.getText().equals("Local pending title")||!ChipJournal.title(tile.getStackInSlot(2)).equals("Fixture"))throw new IllegalStateException("Output Copy update preserves local pending draft and copies committed Input only");checkLayout(gui,size);captureDialog(mc,output,gui,size,"copy");
            }finally{Keyboard.enableRepeatEvents(false);}
        }
        checkDraftDispatch(mc);
        }finally{mc.player.openContainer=previousContainer;}
        System.out.println("[vandorlabs][reprolab] programmer-dialog PASS separate Inventory/Journal tab row and footer compact320/ordinary slot/page hits input-only editor Copy-op2 matching-kind committeddata draft-preserved Program/Copy footer autosave");
    }
    private static void checkDraftDispatch(net.minecraft.client.Minecraft mc)throws Exception{
        java.util.List<String> events=new java.util.ArrayList<>();ProgrammerTile tile=new ProgrammerTile();ItemStack fixture=new ItemStack(com.vandorlabs.items.ModItems.DATA_CHIP);ChipJournal.write(fixture,true,"Fixture",new String[0]);tile.setInventorySlotContents(1,fixture);
        GuiProgrammer gui=new GuiProgrammer(new ProgrammerContainer(mc.player.inventory,tile)){
            @Override protected void dispatch(ProgrammerMessage message){events.add("draft");}
            @Override protected void inheritedKey(char character,int key){events.add("close");}
        };
        gui.setWorldAndResolution(mc,320,240);
        try{
            gui.pages[0]="Escape draft";gui.keyTyped('\0',Keyboard.KEY_ESCAPE);
            if(!events.equals(java.util.Arrays.asList("draft","close")))throw new IllegalStateException("Programmer Escape must dispatch draft before inherited CloseWindow");
            events.clear();gui.pages[0]="Inventory-key draft";gui.keyTyped('\0',mc.gameSettings.keyBindInventory.getKeyCode());
            if(!events.equals(java.util.Arrays.asList("draft","close")))throw new IllegalStateException("Programmer inventory key must dispatch draft before inherited CloseWindow");
            events.clear();gui.pages[0]="Autosave draft";for(int i=0;i<9;i++)gui.updateScreen();
            if(!events.equals(java.util.Collections.singletonList("draft")))throw new IllegalStateException("Programmer changed draft must autosave after bounded debounce");
            for(int i=0;i<20;i++)gui.updateScreen();if(events.size()!=1)throw new IllegalStateException("Programmer unchanged draft must not resend");
        }finally{Keyboard.enableRepeatEvents(false);}
    }
    private static void checkLayout(GuiProgrammer gui,int[] size){
        if(gui.guiLeft<0||gui.guiTop<0||gui.guiLeft+gui.xSize>size[0]||gui.guiTop+gui.ySize>size[1])throw new IllegalStateException("Programmer dialog clipped "+size[0]+"x"+size[1]);
        if(gui.inventoryTab.y!=gui.guiTop+80||gui.journalTab.y!=gui.inventoryTab.y||gui.done.y!=gui.guiTop+205||gui.program.y!=gui.done.y||gui.copy.y!=gui.program.y)throw new IllegalStateException("Tabs below Type and separate footer action row");
        java.util.List<java.awt.Rectangle> regions=new java.util.ArrayList<>();
        for(GuiButton b:gui.buttonList)if(b.visible){if(b.width>200||gui.fontRenderer.getStringWidth(b.displayString)>b.width-4)throw new IllegalStateException("Native programmer button label/width "+b.displayString);regions.add(new java.awt.Rectangle(b.x,b.y,b.width,b.height));}
        for(int i=0;i<gui.inventorySlots.inventorySlots.size();i++)if(i<3||!gui.journalPage){Slot s=gui.inventorySlots.inventorySlots.get(i);regions.add(new java.awt.Rectangle(gui.guiLeft+s.xPos-1,gui.guiTop+s.yPos-1,18,18));}
        if(gui.journalPage){regions.add(new java.awt.Rectangle(gui.title.x,gui.title.y,gui.title.width,gui.title.height));regions.add(new java.awt.Rectangle(gui.guiLeft+14,gui.guiTop+PAPER_TOP-1,224,PAPER_BOTTOM-PAPER_TOP+2));}
        java.awt.Rectangle screen=new java.awt.Rectangle(0,0,size[0],size[1]);for(int i=0;i<regions.size();i++){if(!screen.contains(regions.get(i)))throw new IllegalStateException("Programmer widget clipped "+regions.get(i));for(int j=0;j<i;j++)if(regions.get(i).intersects(regions.get(j)))throw new IllegalStateException("Programmer overlapping widgets "+regions.get(i)+" and "+regions.get(j));}
    }
    private static void captureDialog(net.minecraft.client.Minecraft mc,File output,GuiProgrammer gui,int[] size,String page)throws Exception{
        net.minecraft.client.shader.Framebuffer frame=new net.minecraft.client.shader.Framebuffer(size[0],size[1],true);
        net.minecraft.client.renderer.GlStateManager.matrixMode(org.lwjgl.opengl.GL11.GL_PROJECTION);net.minecraft.client.renderer.GlStateManager.pushMatrix();net.minecraft.client.renderer.GlStateManager.loadIdentity();net.minecraft.client.renderer.GlStateManager.ortho(0,size[0],size[1],0,-1000,1000);net.minecraft.client.renderer.GlStateManager.matrixMode(org.lwjgl.opengl.GL11.GL_MODELVIEW);net.minecraft.client.renderer.GlStateManager.pushMatrix();
        try(com.vandorlabs.client.TurntableRenderState saved=new com.vandorlabs.client.TurntableRenderState()){
            frame.bindFramebuffer(true);net.minecraft.client.renderer.GlStateManager.loadIdentity();net.minecraft.client.renderer.GlStateManager.clear(16640);gui.drawScreen(-1,-1,0);java.awt.image.BufferedImage image=net.minecraft.util.ScreenShotHelper.createScreenshot(size[0],size[1],frame);javax.imageio.ImageIO.write(image,"png",new File(output,"shot_programmer_dialog_"+page+"_"+size[0]+"x"+size[1]+".png"));if((image.getRGB(gui.guiLeft+3,gui.guiTop+3)&0xffffff)!=0x304858)throw new IllegalStateException("Programmer dialog panel is darkened by item lighting");
        }finally{net.minecraft.client.renderer.GlStateManager.popMatrix();net.minecraft.client.renderer.GlStateManager.matrixMode(org.lwjgl.opengl.GL11.GL_PROJECTION);net.minecraft.client.renderer.GlStateManager.popMatrix();net.minecraft.client.renderer.GlStateManager.matrixMode(org.lwjgl.opengl.GL11.GL_MODELVIEW);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
    }
}
