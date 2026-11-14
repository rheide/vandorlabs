package com.vandorlabs.client;

import com.vandorlabs.surface.*;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.tileentity.TileEntity;
import java.util.*;

/** Keep texture layout, join direction and candidate-coordinate filter together. */
final class TextureJoinPane {
    static final int JOIN=240,PARITY=241;
    static void install(List<GuiButton> buttons,TileEntity tile,int x,int top,int width,int doneY){
        if(!(tile instanceof TextureJoinOwner))return;
        TextureJoinSettings policy=((TextureJoinOwner)tile).textureJoin();
        GuiButton existing=null,layout=null;int first=Integer.MAX_VALUE;
        Set<GuiButton> done=Collections.newSetFromMap(new IdentityHashMap<GuiButton,Boolean>());
        for(GuiButton button:buttons){
            if(button.y==doneY)done.add(button);
            if(button.visible&&button.x>=x&&!(button instanceof DialogTabButton)&&!done.contains(button))first=Math.min(first,button.y);
            if(button.visible&&("Join: On".equals(button.displayString)||"Join: Off".equals(button.displayString)))existing=button;
            if(button.visible&&button.displayString.startsWith("Texture: "))layout=button;
        }
        if(first==Integer.MAX_VALUE)first=top;
        final GuiButton removed=existing;
        buttons.removeIf(button->button.visible&&("Join: On".equals(button.displayString)||"Join: Off".equals(button.displayString)));
        if(layout!=null){layout.x=x;layout.width=width;}
        TreeMap<Integer,List<GuiButton>> grouped=new TreeMap<>();
        for(GuiButton button:buttons)if(button.visible&&!(button instanceof DialogTabButton)&&button.x>=x&&!done.contains(button))grouped.computeIfAbsent(button.y,key->new ArrayList<>()).add(button);
        List<List<GuiButton>> rows=new ArrayList<>();boolean inserted=false;
        GuiButton join=new GuiButton(JOIN,x,0,width,20,policy.directionLabel()),parity=new GuiButton(PARITY,x,0,width,20,policy.parityLabel());parity.enabled=policy.directional();
        for(Map.Entry<Integer,List<GuiButton>> row:grouped.entrySet()){
            if(layout==null&&!inserted&&removed!=null&&row.getKey()>removed.y){rows.add(Collections.singletonList(join));rows.add(Collections.singletonList(parity));inserted=true;}
            rows.add(row.getValue());
            if(layout!=null&&row.getValue().contains(layout)){rows.add(Collections.singletonList(join));rows.add(Collections.singletonList(parity));inserted=true;}
        }
        if(!inserted){rows.add(Collections.singletonList(join));rows.add(Collections.singletonList(parity));}
        int step=Math.min(24,(doneY-4-first)/Math.max(1,rows.size()));
        for(int index=0;index<rows.size();index++)for(GuiButton button:rows.get(index)){button.y=first+index*step;button.height=Math.min(button.height,Math.min(20,step-2));}
        buttons.add(join);buttons.add(parity);
    }
    static boolean action(GuiButton button,TileEntity tile){
        if(button.id!=JOIN&&button.id!=PARITY||!(tile instanceof TextureJoinOwner))return false;
        TextureJoinOwner owner=(TextureJoinOwner)tile;TextureJoinSettings next=button.id==JOIN?owner.textureJoin().cycleDirection(GuiOptionCycle.direction()):owner.textureJoin().cycleParity(GuiOptionCycle.direction());
        owner.setTextureJoin(next);
        com.vandorlabs.network.PacketHandler.INSTANCE.sendToServer(new com.vandorlabs.network.MessageTextureJoin(tile.getPos(),next));return true;
    }
    private TextureJoinPane(){}
}
