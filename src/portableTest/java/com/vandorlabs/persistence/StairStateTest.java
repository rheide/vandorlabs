package com.vandorlabs.persistence;
public final class StairStateTest {
    private static int checks;private static void check(boolean b){checks++;if(!b)throw new AssertionError("Stair state "+checks);}
    public static void main(String[] args){MemoryPrimitiveData tag=new MemoryPrimitiveData();check(StairState.read(tag).packed()==8);tag.putString("StairTread","4");check(StairState.read(tag).tread==8);
        for(int tread=-1;tread<=33;tread++)for(int roll=-2;roll<=4;roll++){tag.putInt("StairTread",tread);tag.putInt("StairRoll",roll);StairState state=StairState.read(tag);boolean valid=tread==1||tread==2||tread==4||tread==8||tread==16;check(state.tread==(valid?tread:8));check(state.roll==Math.max(0,Math.min(2,roll)));state.write(tag);check(StairState.read(tag).packed()==state.packed());check(state.toggleRoll().roll==(state.roll==0?1:0));check(state.sliderIndex()>=0&&state.sliderIndex()<=3);}
        check(new StairState(16,2).sliderIndex()==3);for(int i=0;i<4;i++)check(new StairState(8,2).slider(i).packed()==(1<<i|64));System.out.println("StairStateTest PASS "+checks+" checks");}
}
