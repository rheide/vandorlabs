package com.vandorlabs.items;
import java.util.*;

/** Fixed retained-size expectations, independent of either platform's NBT classes. */
public final class CopiedSettingsMemoryTest {
 private static final class Node {final int type,length;final Map<String,Node> members=new LinkedHashMap<String,Node>();final List<Node> values=new ArrayList<Node>();Node(int type,int length){this.type=type;this.length=length;}}
 private static final CopiedSettingsMemory.Tags<Node> TAGS=new CopiedSettingsMemory.Tags<Node>() {
  public int type(Node n){return n.type;}public Iterable<String> keys(Node n){return n.members.keySet();}public Node member(Node n,String key){return n.members.get(key);}public int length(Node n){return n.type==9?n.values.size():n.length;}public Node element(Node n,int i){return n.values.get(i);}
 };
 private static void need(Node n,long expected){long actual=CopiedSettingsMemory.estimate(n,TAGS);if(actual!=expected)throw new AssertionError("Retained bytes expected="+expected+" actual="+actual);}
 public static void main(String[] args){for(int type=0;type<=6;type++)need(new Node(type,0),32);need(new Node(8,4),72);need(new Node(7,17),57);need(new Node(11,17),108);need(new Node(12,0),16L*1024*1024+1);need(new Node(10,0),128);Node root=new Node(10,0);root.members.put("a",new Node(3,0));need(root,274);Node list=new Node(9,0);list.values.add(new Node(3,0));list.values.add(new Node(8,4));need(list,200);root.members.put("list",list);need(root,594);if(CopiedSettingsMemory.TOTAL_BYTES!=4*CopiedSettingsMemory.PLAYER_BYTES)throw new AssertionError("released budgets");System.out.println("Copied settings memory PASS typed-nodes/UTF16/arrays/lists/compound/unsupported/released-budgets");}
}
