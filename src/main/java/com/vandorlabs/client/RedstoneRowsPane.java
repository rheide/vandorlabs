package com.vandorlabs.client;

import com.vandorlabs.redstone.*;
import com.vandorlabs.tiles.RedstoneScreenContents;
import net.minecraft.client.gui.*;
import net.minecraft.nbt.*;
import java.util.*;
import java.util.function.IntPredicate;

/** Shared row editor hosted inside its block's existing configuration window. */
public final class RedstoneRowsPane {
    public static final int BASE=600;
    private final FontRenderer font;
    private final int maxRows;
    private final boolean modules;
    private final IntPredicate installed;
    private final java.util.function.Consumer<NBTTagList> apply;
    private final List<NBTTagCompound> rows=new ArrayList<>();
    private final List<GuiButton> buttons=new ArrayList<>();
    private final Map<GuiButton,Integer> actions=new IdentityHashMap<>();
    private GuiTextField label,channels;
    private int x,y,width,height,right,selected=-1,scroll,listOffset;
    public RedstoneRowsPane(FontRenderer font,int maxRows,boolean modules,NBTTagList values,IntPredicate installed,java.util.function.Consumer<NBTTagList> apply){this.font=font;this.maxRows=maxRows;this.modules=modules;this.installed=installed;this.apply=apply;for(int i=0;i<values.tagCount();i++)rows.add(values.getCompoundTagAt(i).copy());if(!rows.isEmpty())selected=0;}
    public void layout(int x,int y,int width,int height,List<GuiButton> host){
        if(label!=null)store();this.x=x;this.y=y;this.width=width;this.height=height;right=x+Math.max(80,width-184);int w=x+width-right;buttons.clear();actions.clear();
        for(int i=0;i<4;i++)buttons.add(new GuiButton(BASE+i,right+i*w/4,y,w/4-2,18,new String[]{"Add","Del","Up","Down"}[i]));
        label=new GuiTextField(BASE,font,right+32,y+22,w-32,18);label.setMaxStringLength(RedstoneScreenContents.MAX_LABEL);
        channels=new GuiTextField(BASE+1,font,right+42,y+44,w-42,18);ChannelFields.configure(channels);
        if(modules)buttons.add(new GuiButton(BASE+7,right,y+44,w,20,"Transmitter..."));
        int kindY=y+(modules?88:66);
        buttons.add(new GuiButton(BASE+4,right,kindY,w,18,""));buttons.add(new GuiButton(BASE+5,right,kindY+20,w/2-2,18,""));buttons.add(new GuiButton(BASE+6,right+w/2+2,kindY+20,w/2-2,18,""));
        for(GuiButton button:buttons)actions.put(button,button.id-BASE);host.addAll(buttons);load();refresh();
    }
    public int selected(){return selected;}
    public GuiTextField labelField(){return label;}
    public GuiTextField channelField(){return channels;}
    public List<GuiButton> buttons(){return Collections.unmodifiableList(buttons);}
    public void setListOffset(int pixels){listOffset=Math.max(0,pixels);}
    private int visibleRows(){return Math.max(1,(height-listOffset)/20);}
    private void load(){label.setText(selected<0?"":rows.get(selected).getString("Label"));channels.setText(selected<0?"0":ChannelData.read(rows.get(selected),0).toString());}
    private boolean store(){if(selected<0)return true;String value=label.getText();int kind=rows.get(selected).getInteger("Kind");boolean valid=RedstoneScreenContents.validLabel(value)&&RedstoneScreenText.fits(font,value,kind==1?RedstoneScreenContents.SLIDER_LABEL_WIDTH:RedstoneScreenText.LABEL_WIDTH)&&(kind!=1||value.length()<=RedstoneScreenContents.SLIDER_LABEL);ChannelList targets=ChannelFields.parse(channels);label.setTextColor(valid?0xe0e0e0:0xff7777);if(!valid||targets==null)return false;rows.get(selected).setString("Label",value);ChannelData.write(rows.get(selected),targets);return true;}
    public boolean save(){if(!store())return false;NBTTagList list=new NBTTagList();for(NBTTagCompound row:rows)list.appendTag(row.copy());apply.accept(list);return true;}
    private void refresh(){for(GuiButton button:buttons){int id=actions.containsKey(button)?actions.get(button):button.id-BASE;button.enabled=id==0?rows.size()<maxRows:selected>=0;if(id==2)button.enabled=selected>0;if(id==3)button.enabled=selected>=0&&selected<rows.size()-1;if(id>=4){button.visible=selected>=0;if(selected>=0){NBTTagCompound row=rows.get(selected);int kind=row.getInteger("Kind");button.displayString=id==4?"Control: "+new String[]{"Toggle","Slider","Pulse"}[kind]:id==5?"Low: "+row.getInteger("Min"):id==6?"High: "+row.getInteger("Max"):"Transmitter...";if(id==5||id==6)button.enabled=kind==1;}}}label.setVisible(selected>=0);channels.setVisible(selected>=0&&!modules);}
    public boolean action(GuiButton button){int id=actions.containsKey(button)?actions.get(button):button.id-BASE;if(id<0||id>7)return false;if(!store())return true;if(id==7)return true;
        if(id==0&&rows.size()<maxRows){NBTTagCompound row=new NBTTagCompound();row.setString("Label","Item "+(rows.size()+1));ChannelData.write(row,ChannelList.EMPTY);row.setInteger("Min",5);row.setInteger("Max",15);rows.add(row);selected=rows.size()-1;scroll=Math.max(0,selected-visibleRows()+1);}
        else if(selected>=0){if(id==1){for(int i=selected;i<rows.size();i++)if(installed.test(i))return true;rows.remove(selected);selected=Math.min(selected,rows.size()-1);}
            else if(id==2||id==3){int next=selected+(id==2?-1:1);if(next>=0&&next<rows.size()&&!installed.test(selected)&&!installed.test(next)){Collections.swap(rows,selected,next);selected=next;}}
            else {NBTTagCompound row=rows.get(selected);if(id==4){int kind=GuiOptionCycle.next(row.getInteger("Kind"),3);row.setInteger("Kind",kind);if(kind==1){String text=row.getString("Label");while(text.length()>RedstoneScreenContents.SLIDER_LABEL||font.getStringWidth(text)>RedstoneScreenContents.SLIDER_LABEL_WIDTH)text=text.substring(0,text.length()-1);row.setString("Label",text);}}if(id==5)row.setInteger("Min",GuiOptionCycle.next(row.getInteger("Min"),1,row.getInteger("Max")-2));if(id==6)row.setInteger("Max",GuiOptionCycle.next(row.getInteger("Max"),row.getInteger("Min")+2,15));}}
        scroll=Math.max(0,Math.min(scroll,Math.max(0,rows.size()-visibleRows())));load();refresh();save();return true;
    }
    public void mouseClicked(int mx,int my,int button){label.mouseClicked(mx,my,button);if(!modules)channels.mouseClicked(mx,my,button);if(mx>=x&&mx<right-8&&my>=y+listOffset&&my<y+listOffset+visibleRows()*20){int next=scroll+(my-y-listOffset)/20;if(next<rows.size()&&store()){selected=next;load();refresh();}}}
    public boolean keyTyped(char character,int key){if(key==28||key==156)return save();boolean changed=label.getVisible()&&label.textboxKeyTyped(character,key)||channels.getVisible()&&channels.textboxKeyTyped(character,key);if(changed)save();return changed;}
    public void wheel(int amount){if(amount!=0)scroll=Math.max(0,Math.min(Math.max(0,rows.size()-visibleRows()),scroll+(amount>0?-1:1)));}
    public void update(){label.updateCursorCounter();channels.updateCursorCounter();}
    public void draw(){for(int i=0;i<visibleRows()&&scroll+i<rows.size();i++){int index=scroll+i,yy=y+listOffset+i*20;Gui.drawRect(x,yy,right-8,yy+18,index==selected?0xff365c70:0xff22313e);font.drawString(font.trimStringToWidth(rows.get(index).getString("Label"),right-x-16),x+4,yy+5,0xe0ecf5);}if(selected>=0){font.drawString("Label",right,y+27,0xdae8f0);label.drawTextBox();if(!modules){font.drawString("Targets",right,y+49,0xdae8f0);channels.drawTextBox();}}}
}
