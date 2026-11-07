package com.vandorlabs.vehicle;
import com.vandorlabs.coupler.CouplerGuiAddress;
import net.minecraft.util.math.BlockPos;
public final class CouplerGuiAddressChecks {
    private static int checks;private static void need(boolean v,String message){checks++;if(!v)throw new AssertionError(message);}
    public static void main(String[] args){
        for(int x:new int[]{0,1,63})for(int y:new int[]{0,31,63})for(int z:new int[]{0,32,63}){BlockPos root=new BlockPos(x,y,z);int packed=CouplerGuiAddress.root(root);need(CouplerGuiAddress.validRoot(packed)&&CouplerGuiAddress.unpack(packed).equals(root),"root byte bounds roundtrip");}
        for(int x:new int[]{0,64,127})for(int y:new int[]{0,65,127})for(int z:new int[]{0,66,127}){BlockPos cell=new BlockPos(x,y,z);int wire=CouplerGuiAddress.cellZ(cell),packed=wire-Integer.MIN_VALUE-1;need(CouplerGuiAddress.captured(wire)&&CouplerGuiAddress.validCell(packed)&&CouplerGuiAddress.unpack(packed).equals(cell),"captured cell byte bounds roundtrip");need(wire< -30000000,"captured sentinel never shadows valid negative world Z");}
        for(int bit=0;bit<32;bit++){int value=1<<bit;if((value&0xffc0c0c0)!=0)need(!CouplerGuiAddress.validRoot(value),"unused root bit refuses before lookup "+bit);if((value&0xff808080)!=0)need(!CouplerGuiAddress.validCell(value),"unused cell bit refuses before lookup "+bit);}
        need(!CouplerGuiAddress.captured(Integer.MIN_VALUE),"legacy native vehicle sentinel remains distinct");for(int z:new int[]{-30000000,-1,0,1,30000000,Integer.MAX_VALUE})need(!CouplerGuiAddress.captured(z),"valid ordinary world coordinate remains native "+z);
        for(BlockPos bad:new BlockPos[]{new BlockPos(-1,0,0),new BlockPos(0,128,0),new BlockPos(0,0,128)}){boolean refused=false;try{CouplerGuiAddress.cellZ(bad);}catch(IllegalArgumentException expected){refused=true;}need(refused,"invalid captured coordinate refuses");}
        System.out.println("Coupler GUI address PASS checks="+checks+" old sentinel/ordinary negativeZ/root63/cell127/malformed bits");
    }
}
