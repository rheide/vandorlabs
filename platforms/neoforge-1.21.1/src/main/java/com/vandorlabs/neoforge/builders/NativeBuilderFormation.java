package com.vandorlabs.neoforge.builders;
import com.vandorlabs.machines.CoreLayout;import com.vandorlabs.neoforge.transfer.NativeConstructorFrame;import net.minecraft.core.*;import net.minecraft.world.level.Level;
public final class NativeBuilderFormation {
 public record Layout(Direction facing,int x,int z){}
 public static boolean loaded(Level level,BlockPos pos){return level!=null&&!level.isOutsideBuildHeight(pos)&&level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null;}
 public static net.minecraft.world.level.block.Block matter(){return NativeConstructorFrame.block();}
 public static Layout discover(NativeBuilderEntity owner){var d=owner.design();for(int turn=0;turn<4;turn++){var face=Direction.from2DDataValue((owner.facing().get2DDataValue()+turn)%4);for(int x:new int[]{0,d.width()-1})for(int z:new int[]{0,d.depth()-1}){var l=new CoreLayout(d.width(),d.height(),d.depth(),face.get3DDataValue(),x,z);boolean valid=true;for(int a=0;a<d.width()&&valid;a++)for(int y=0;y<d.height()&&valid;y++)for(int b=0;b<d.depth();b++){var p=owner.getBlockPos().offset(l.x(a,b),y,l.z(a,b));if(p.equals(owner.getBlockPos()))continue;if(!loaded(owner.getLevel(),p)||!NativeConstructorFrame.fits(owner.getLevel(),p,l.frame(a,y,b))){valid=false;break;}}if(valid)return new Layout(face,x,z);}}throw new IllegalArgumentException("Complete the Programmable Block frame and leave its interior empty");}
 private NativeBuilderFormation(){}
}
