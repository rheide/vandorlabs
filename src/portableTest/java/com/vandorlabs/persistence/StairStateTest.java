package com.vandorlabs.persistence;

public final class StairStateTest {
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] ignored){
        int cases=0;int[] values={Integer.MIN_VALUE,-1,0,1,2,3,4,8,16,17,Integer.MAX_VALUE};
        for(int tread:values)for(int roll:values){boolean allowed=tread==1||tread==2||tread==4||tread==8||tread==16;require(StairState.valid(tread,roll)==(allowed&&roll>=0&&roll<=2),"strict live values");require(StairState.readTread(true,tread)==(allowed?tread:8)&&StairState.readTread(false,tread)==8,"typed saved tread");require(StairState.readRoll(roll)==(roll<0?0:roll>2?2:roll),"saved roll clamp");require(StairState.packed(tread,roll)==(tread|(roll<<5)),"caller-owned packing");cases++;}
        MemoryPrimitiveData tag=new MemoryPrimitiveData();StairState state=StairState.read(tag);require(state.tread==8&&state.roll==0,"absent unversioned fields reset");tag.putString("StairTread","4");tag.putString("StairRoll","2");state=StairState.read(tag);require(state.tread==8&&state.roll==0,"wrong types reset independently");
        for(int tread:new int[]{1,2,4,8,16})for(int roll=0;roll<3;roll++){StairState.write(tag,tread,roll);state=StairState.read(tag);require(state.tread==tread&&state.roll==roll&&state.packed()==(tread|(roll<<5)),"all saved poses including16px and roll2");}
        StairState.write(tag,7,9);require(tag.getInt("StairTread")==7&&tag.getInt("StairRoll")==9,"raw scalar writer must not normalize");System.out.println("StairState PASS boundaries="+cases+" exact typed tread, numeric roll,15savedposes and scalar writer");
    }
}
