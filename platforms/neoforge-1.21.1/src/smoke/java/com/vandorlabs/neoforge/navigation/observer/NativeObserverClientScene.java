package com.vandorlabs.neoforge.navigation.observer;
import com.vandorlabs.machines.CoreLayout;import com.vandorlabs.redstone.ChannelList;import net.minecraft.core.*;import net.minecraft.server.level.*;import net.minecraft.world.level.block.Blocks;
/** Client scene setup uses the same real asynchronous player-authorized formation family as gameplay. */
public final class NativeObserverClientScene {
 public static void begin(ServerPlayer actor,BlockPos dish,BlockPos array){build(actor,dish,"standalone_dish");build(actor,array,"standalone_antenna");}
 private static void build(ServerPlayer actor,BlockPos root,String id){var world=actor.serverLevel();var d=NativeObserverDesigns.find(id);for(int x=-1;x<=d.width();x++)for(int y=0;y<=d.height();y++)for(int z=-1;z<=d.depth();z++)world.setBlock(root.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);world.setBlock(root,NativeObserverBindings.CORES.get(id).defaultBlockState(),3);var tile=(NativeObserverEntity)world.getBlockEntity(root);tile.configure(ChannelList.of(71),256);var l=new CoreLayout(d.width(),d.height(),d.depth(),Direction.NORTH.get3DDataValue(),0,0);for(int x=0;x<d.width();x++)for(int y=0;y<d.height();y++)for(int z=0;z<d.depth();z++){var p=root.offset(l.x(x,z),y,l.z(x,z));if(!p.equals(root))world.setBlock(p,l.frame(x,y,z)?NativeObserverFormation.matter().defaultBlockState():Blocks.AIR.defaultBlockState(),3);}if(!NativeObserverTransactions.request(tile,actor,true))throw new AssertionError("Observer client scene formation refused "+id);}
 public static boolean ready(ServerLevel world,BlockPos dish,BlockPos array){for(var p:new BlockPos[]{dish,array})if(!(world.getBlockEntity(p) instanceof NativeObserverEntity t)||!t.formed()||!t.available())return false;return true;}
 private NativeObserverClientScene(){}
}
