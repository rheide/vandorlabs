package com.vandorlabs.rack;

public final class EmbeddedRackMotionTest {
    public static void main(String[] args){
        EmbeddedRackMotion state=new EmbeddedRackMotion();state.observe(0,0,0,0,0,0,0,0,0,0);require(!state.visible(0,0),"initial empty rack");
        state.observe(1,1,0,0,0,3,0,0,0,1);require(state.offset(0,1)==.5&&state.visible(0,1),"placed-Rack insertion begins outside");
        double halfway=state.offset(0,6);state.observe(1,1,0,0,0,4,0,0,0,6);require(state.offset(0,6)==halfway&&state.level(0)==4,"charge-only packet never restarts motion");
        state.observe(0,2,0,0,0,0,0,0,0,6);require(state.offset(0,6)==halfway&&state.level(0)==4,"extraction reverses continuously and retains physical module artwork");
        require(state.offset(0,16)==.5&&!state.visible(0,16),"extraction retires at exact Rack endpoint");
        state.observe(1,3,0,0,0,2,0,0,0,17);require(state.offset(0,17)==.5,"fresh insert");
        state.observe(1,5,0,0,0,2,0,0,0,30);require(state.offset(0,30)==.5,"rapid remove/reinsert sequence survives a single packet");
        state.observe(0,2,0,0,0,0,0,0,0,31);require(state.occupied(0)&&state.visible(0,31),"older packet cannot remove a newly inserted module");
        require(ModuleRackMotion.offset(5)==ModuleRackMotion.between(5,.5,0),"same placed Rack envelope");
        System.out.println("EmbeddedRackMotionTest PASS initial/insert/reverse/extract/charge/repeated/rapid-reinsert exactplacedRackenvelope");
    }
    private static void require(boolean value,String why){if(!value)throw new AssertionError(why);}
}
