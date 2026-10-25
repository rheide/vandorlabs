package com.vandorlabs.persistence;

public final class HousingSideTextureTest {
    public static void main(String[] args){
        for(int value:new int[]{Integer.MIN_VALUE,-3,-2,-1,0,3,Integer.MAX_VALUE}){
            MemoryPrimitiveData tag=new MemoryPrimitiveData();tag.putInt("SideTexture",value);
            int expected=value>=0?value%10:-1;if(HousingSideTexture.read(tag,n->n%10)!=expected)throw new AssertionError("typed saved side "+value);
            int selected=value==-3?-3:expected;if(HousingSideTexture.selected(value,n->n%10)!=selected)throw new AssertionError("live selected side "+value);
        }
        if(HousingSideTexture.read(new MemoryPrimitiveData(),n->n)!=-1)throw new AssertionError("missing side inherits");
        System.out.println("HousingSideTexture PASS typed saved negatives normalize while live vehicle-surface selection remains distinct");
    }
}
