package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.BlockCanopy;
import com.vandorlabs.tiles.TileEntityCanopy;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;

/** Actual targeted membership callback; native automatic scheduling and legacy exceptions are separate. */
public final class CanopyRepairLegacyOracle {
    private static final class World extends CanopyContactLegacyOracle.World {
        final Set<Long> cold=new HashSet<>();
        @Override protected boolean isChunkLoaded(int x,int z,boolean empty){return !cold.contains(key(x,z));}
    }
    private static long key(int x,int z){return ((long)x<<32)^(z&0xffffffffL);}
    private static JsonArray pos(BlockPos p){JsonArray out=new JsonArray();out.add(p.getX());out.add(p.getY());out.add(p.getZ());return out;}
    private static List<BlockPos> sorted(Collection<BlockPos> values){List<BlockPos> out=new ArrayList<>(values);out.sort(Comparator.comparingInt(BlockPos::getX).thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ));return out;}
    private static JsonArray snapshot(World world){Set<Long> cold=new HashSet<>(world.cold);world.cold.clear();try{JsonArray out=new JsonArray();for(BlockPos p:sorted(world.states.keySet())){net.minecraft.block.state.IBlockState state=world.states.get(p);if(state.getBlock()==Blocks.AIR)continue;JsonObject cell=new JsonObject();cell.add("pos",pos(p));cell.addProperty("block",state.getBlock().getRegistryName().toString());cell.addProperty("meta",state.getBlock().getMetaFromState(state));net.minecraft.tileentity.TileEntity raw=world.tiles.get(p);if(raw!=null)cell.addProperty("saved",raw.writeToNBT(new NBTTagCompound()).toString());out.add(cell);}return out;}finally{world.cold.addAll(cold);}}
    private static List<TileEntityCanopy> place(World world,EntityPlayer actor,BlockCanopy block,EnumFacing facing,BlockPos base,int width){
        actor.rotationYaw=facing.getOpposite().getHorizontalAngle();for(int i=0;i<width;i++){BlockPos p=base.offset(facing.rotateY(),i);world.setBlockState(p.down(),Blocks.STONE.getDefaultState(),2);ItemStack stack=new ItemStack(Item.getItemFromBlock(block));actor.setHeldItem(EnumHand.MAIN_HAND,stack);if(stack.getItem().onItemUse(actor,world,p.down(),EnumHand.MAIN_HAND,EnumFacing.UP,.5F,1,.5F)!=EnumActionResult.SUCCESS)throw new AssertionError("Source repair fixture placement "+block.kind);}
        List<TileEntityCanopy> roots=new ArrayList<>();for(net.minecraft.tileentity.TileEntity raw:world.tiles.values())if(raw instanceof TileEntityCanopy){TileEntityCanopy t=(TileEntityCanopy)raw;if(t.anchor!=null&&t.anchor.equals(t.getPos())&&t.block()==block)roots.add(t);}roots.sort(Comparator.comparingInt(t->t.getPos().getX()*facing.rotateY().getFrontOffsetX()+t.getPos().getZ()*facing.rotateY().getFrontOffsetZ()));return roots;
    }
    public static void main(String[] args)throws Exception{
        List<BlockCanopy> blocks=CanopyPlacementLegacyOracle.initialize();Method repair=TileEntityCanopy.class.getDeclaredMethod("repairMembership");repair.setAccessible(true);File file=new File(args[0]);file.getAbsoluteFile().getParentFile().mkdirs();World world=new World();EntityPlayer actor=new EntityPlayer(world,new com.mojang.authlib.GameProfile(new UUID(0,199),"CanopyRepairOracle")){public boolean isCreative(){return false;}public boolean isSpectator(){return false;}};Gson gson=new Gson();int cases=0,removed=0,legacy=0,coldCount=0,pairs=0;
        try(PrintWriter out=new PrintWriter(file,"UTF-8")){
            for(BlockCanopy block:blocks)for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int scenario:new int[]{0,1,2,3,4,5,6,7,8,9,10,11,12,20,21,22,23,24,25}){
                if(scenario>=20&&!block.opening())continue;world.cold.clear();world.clear();EnumFacing cross=facing.rotateY();BlockPos base=new BlockPos(cross.getFrontOffsetX()<0?0:15,80,cross.getFrontOffsetZ()<0?0:15);List<TileEntityCanopy> roots=place(world,actor,block,facing,base,scenario>=20?3:1);if(roots.isEmpty())throw new AssertionError("Source root absent");TileEntityCanopy root=roots.get(0),target=root;int storedFacing=root.facing().getIndex();List<BlockPos> children=new ArrayList<>(root.cells());children.remove(root.getPos());if(scenario>=2&&scenario<=12&&children.isEmpty())continue;BlockPos child=children.isEmpty()?null:children.get(0);boolean legacyAbsent=false;
                if(scenario>=20){if(roots.size()!=3)throw new AssertionError("Source paired roots");for(net.minecraft.tileentity.TileEntity raw:world.tiles.values())if(raw instanceof TileEntityCanopy){TileEntityCanopy t=(TileEntityCanopy)raw;t.progress=t.previous=.5F;t.targetOpen=true;}TileEntityCanopy middle=roots.get(1);if(scenario==21)world.setBlockToAir(middle.getPos());if(scenario==22)middle.pair=middle.getPos();if(scenario==23)world.setBlockState(middle.getPos(),block.getDefaultState().withProperty(BlockCanopy.FACING,facing.rotateY()),2);if(scenario==24){world.setBlockToAir(root.getPos());target=middle;}if(scenario==25){if((root.getPos().getX()>>4)==(middle.getPos().getX()>>4)&&(root.getPos().getZ()>>4)==(middle.getPos().getZ()>>4))continue;world.cold.add(key(root.getPos().getX()>>4,root.getPos().getZ()>>4));target=middle;}pairs++;
                }else if(scenario==1){root.anchor=null;legacyAbsent=true;}
                else if(scenario==2)world.setBlockToAir(child);
                else if(scenario==3){BlockCanopy other=blocks.stream().filter(b->b!=block).findFirst().get();world.setBlockState(child,other.getDefaultState(),2);((TileEntityCanopy)world.getTileEntity(child)).anchor=root.getPos();}
                else if(scenario==4)((TileEntityCanopy)world.getTileEntity(child)).anchor=child;
                else if(scenario==5){target=(TileEntityCanopy)world.getTileEntity(child);world.setBlockToAir(root.getPos());}
                else if(scenario==6){target=(TileEntityCanopy)world.getTileEntity(child);root.setFootprint(Collections.singletonList(root.getPos()));}
                else if(scenario==7){target=(TileEntityCanopy)world.getTileEntity(child);root.anchor=null;legacyAbsent=true;}
                else if(scenario==8||scenario==9){BlockPos other=children.stream().filter(p->(p.getX()>>4)!=(root.getPos().getX()>>4)||(p.getZ()>>4)!=(root.getPos().getZ()>>4)).findFirst().orElse(null);if(other==null)continue;if(scenario==8)world.cold.add(key(other.getX()>>4,other.getZ()>>4));else{target=(TileEntityCanopy)world.getTileEntity(other);world.cold.add(key(root.getPos().getX()>>4,root.getPos().getZ()>>4));}}
                else if(scenario==10){root.releasedCell(child,true);world.setBlockToAir(child);}
                else if(scenario==11)world.setBlockState(child,Blocks.STONE.getDefaultState(),2);
                else if(scenario==12)world.removeTileEntity(child);
                JsonObject row=new JsonObject();row.addProperty("id",block.kind);row.addProperty("facing",storedFacing);row.addProperty("scenario",scenario);row.addProperty("legacyAbsentRoot",legacyAbsent);row.add("target",pos(target.getPos()));row.add("before",snapshot(world));JsonArray unloaded=new JsonArray();for(long chunk:new TreeSet<>(world.cold)){JsonArray pair=new JsonArray();pair.add((int)(chunk>>32));pair.add((int)chunk);unloaded.add(pair);}row.add("cold",unloaded);boolean result=(Boolean)repair.invoke(target);row.addProperty("returned",result);row.add("after",snapshot(world));out.println(gson.toJson(row));cases++;if(result)removed++;if(legacyAbsent)legacy++;if(!world.cold.isEmpty())coldCount++;
            }
        }
        if(cases<400||removed==0||legacy==0||coldCount==0||pairs==0)throw new AssertionError("Source repair coverage="+cases);SegmentedReferenceGolden.check(file,"26ebbc98d19510a231adf6c196bc9908f803d8ed91e186d571e7875aeb187995");System.out.println("Actual Canopy repair PASS cases="+cases+" removed="+removed+" legacyAbsent="+legacy+" cold="+coldCount+" paired="+pairs+" targeted actual callback; native preserves explicitly accepted absent-root exception");
    }
}
