package com.vandorlabs.persistence;

import java.util.*;
import java.util.function.IntPredicate;

public final class DoorGlassDataTest {
    private static final IntPredicate MATERIAL=value->value>=-1&&value<=2;
    private static final DoorGlassData ORIGINAL=new DoorGlassData(-2,0,1,1,MATERIAL);
    private static int creates,settings;
    private static final class Node {final Map<String,Object> values=new HashMap<String,Object>();}
    private static final DoorGlassData.Tag<Node> TAG=new DoorGlassData.Tag<Node>(){
        public boolean compound(Node n,String key){return n.values.get(key) instanceof Node;}
        public Node child(Node n,String key){return (Node)n.values.get(key);}
        public Node create(){creates++;return new Node();}
        public boolean integer(Node n,String key){return n.values.get(key) instanceof Integer;}
        public int getInt(Node n,String key){Object value=n.values.get(key);return value instanceof Number?((Number)value).intValue():0;}
        public void putInt(Node n,String key,int value){n.values.put(key,value);}
        public void put(Node n,String key,Node child){n.values.put(key,child);}
        public void remove(Node n,String key){n.values.remove(key);}
    };
    private static final DoorGlassData.Factory<DoorGlassData> FACTORY=new DoorGlassData.Factory<DoorGlassData>(){
        public DoorGlassData original(){return ORIGINAL;}
        public DoorGlassData create(int m,int s,int t,int l){settings++;return new DoorGlassData(m,s,t,l,MATERIAL);}
    };
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        Object[] values={null,-2,0,1,19,(byte)1,"1",1L};String[] keys={"Material","Shade","Tint","Layout"};int cases=0;
        for(int code=0;code<4096;code++){
            Node root=new Node(),child=new Node();child.values.put("Unknown","opaque");int choice=code;
            for(String key:keys){Object v=values[choice&7];choice>>>=3;if(v!=null)child.values.put(key,v);}root.values.put("DoorGlass",child);root.values.put("Foreign","keep");Map<String,Object> original=new HashMap<String,Object>(child.values);
            int material=TAG.integer(child,"Material")?TAG.getInt(child,"Material"):-2,shade=TAG.getInt(child,"Shade"),tint=TAG.integer(child,"Tint")?TAG.getInt(child,"Tint"):1,layout=TAG.integer(child,"Layout")?TAG.getInt(child,"Layout"):1;
            boolean valid=material>=-2&&material<=2&&shade>=0&&shade<19&&tint>=0&&tint<4&&layout>=0&&layout<3;
            settings=creates=0;DoorGlassData data=DoorGlassData.read(TAG,root,MATERIAL,FACTORY);
            require(child.values.equals(original)&&root.values.get("DoorGlass")==child&&creates==0,"read is source preserving and does not allocate compounds");
            require(settings==(valid?1:0),"only one factory snapshot for a valid child, none for fallback");
            if(valid)require(data.material==material&&data.shade==shade&&data.tint==tint&&data.layout==layout,"strict material/tint/layout, numeric shade");else require(data==ORIGINAL,"malformed uses shared default");
            DoorGlassData.write(TAG,root,data.material,data.shade,data.tint,data.layout);
            require("keep".equals(root.values.get("Foreign")),"unrelated root survives");
            if(data.material==-2)require(!root.values.containsKey("DoorGlass")&&creates==0,"original removes override without allocation");
            else require(creates==1&&TAG.child(root,"DoorGlass")!=child&&TAG.child(root,"DoorGlass").values.size()==4,"writer owns exactly one replacement compound");cases++;
        }
        for(Object malformed:new Object[]{null,"wrong",1,1L}){Node root=new Node();if(malformed!=null)root.values.put("DoorGlass",malformed);require(DoorGlassData.read(TAG,root,MATERIAL,FACTORY)==ORIGINAL,"missing or wrong parent type uses default");}
        System.out.println("DoorGlassData PASS cases="+cases+" typed fields numeric shade sparse original factory identity and exact compound allocations");
    }
}
