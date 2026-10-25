package com.vandorlabs.persistence;

public final class TriggerStateTest {
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        int count=0;
        for(boolean bands:new boolean[]{false,true})for(int exact=-1;exact<=15;exact++)for(int signal=0;signal<=15;signal++){
            TriggerState state=new TriggerState(40,bands,exact,20,30);int expected;
            if(exact>=0&&signal!=exact)expected=10;else if(bands)expected=signal==0?10:signal<=5?20:signal<=10?30:40;else expected=exact>=0||signal>0?40:10;
            require(state.visible(10,signal)==expected,"exact/band selection "+bands+" "+exact+" "+signal);
            MemoryPrimitiveData tag=new MemoryPrimitiveData();state.write(tag);TriggerState read=TriggerState.read(tag,x->x);
            require(read.on==40&&read.low==20&&read.medium==30&&read.bands==bands&&read.exact==exact&&read.visible(10,signal)==expected,"primitive roundtrip");count++;
        }
        MemoryPrimitiveData legacy=new MemoryPrimitiveData();legacy.putInt("TriggerOnTexture",37);TriggerState read=TriggerState.read(legacy,x->x);
        require(read.low==37&&read.medium==37&&read.exact==-1&&!read.bands,"missing legacy fields inherit on finish");
        legacy.putBoolean("TriggerExactLevel",true);legacy.putString("TriggerLowTexture","mistyped");require(TriggerState.read(legacy,x->x).exact==-1&&TriggerState.read(legacy,x->x).low==37,"typed defaults");
        legacy.putInt("TriggerExactLevel",100);require(TriggerState.read(legacy,x->x).exact==15,"high saved clamp");legacy.putInt("TriggerExactLevel",-100);require(TriggerState.read(legacy,x->x).exact==-1,"low saved clamp");
        require(new TriggerState(40,false,0,20,30).visible(10,0)==40&&new TriggerState(40,true,0,20,30).visible(10,0)==10,"exact-zero off/on and band distinction");
        require(TriggerState.validExact(-1)&&TriggerState.validExact(15)&&!TriggerState.validExact(-2)&&!TriggerState.validExact(16),"configuration bounds");
        System.out.println("TriggerState PASS "+count+" exact/band signal combinations, primitive roundtrips and typed legacy migrations");
    }
}
