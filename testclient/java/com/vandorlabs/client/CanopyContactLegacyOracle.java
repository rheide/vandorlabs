package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.BlockCanopy;
import com.vandorlabs.tiles.TileEntityCanopy;
import com.vandorlabs.canopy.CanopyReservations;
import java.io.*;
import java.util.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Actual released world callbacks; scalar observations follow the platform's constituent resolution. */
public final class CanopyContactLegacyOracle {
    private static final class World extends CanopyPlacementLegacyOracle.World {
        private final Map<Long,net.minecraft.world.chunk.Chunk> chunks=new HashMap<>();
        private net.minecraft.world.chunk.IChunkProvider cached;
        @Override public net.minecraft.world.chunk.IChunkProvider getChunkProvider(){
            if(cached==null)cached=new net.minecraft.world.chunk.IChunkProvider(){
                public net.minecraft.world.chunk.Chunk getLoadedChunk(int x,int z){long key=((long)x<<32)^(z&0xffffffffL);net.minecraft.world.chunk.Chunk chunk=chunks.get(key);if(chunk==null){chunk=new net.minecraft.world.chunk.Chunk(World.this,x,z){@Override public Map<BlockPos,net.minecraft.tileentity.TileEntity> getTileEntityMap(){return tiles;}};chunks.put(key,chunk);}return chunk;}
                public net.minecraft.world.chunk.Chunk provideChunk(int x,int z){return getLoadedChunk(x,z);}
                public boolean tick(){return false;}public String makeString(){return "ContactOracle";}public boolean isChunkGeneratedAt(int x,int z){return true;}
            };return cached;
        }
    }
    private static final BlockPos ROOT=new BlockPos(8,80,8);
    private static TileEntityCanopy install(CanopyPlacementLegacyOracle.World world,BlockPos pos,BlockCanopy block,EnumFacing facing,int mount){world.setBlockState(pos,block.getDefaultState().withProperty(BlockCanopy.FACING,facing),2);TileEntityCanopy tile=TileEntityCanopy.at(world,pos);tile.anchor=pos;tile.large.mount=mount;return tile;}
    private static JsonElement peer(TileEntityCanopy tile){if(tile==null||tile.block()==null)return JsonNull.INSTANCE;JsonArray out=new JsonArray();out.add(tile.block().kind);out.add(tile.block().length);out.add(tile.facing().getIndex());out.add(tile.large.mount);out.add(tile.getPos().getX()-ROOT.getX());out.add(tile.getPos().getY()-ROOT.getY());out.add(tile.getPos().getZ()-ROOT.getZ());out.add(tile.constituent()==tile);return out;}
    public static void main(String[] args)throws Exception{
        List<BlockCanopy> blocks=CanopyPlacementLegacyOracle.initialize();File file=new File(args[0]);file.getAbsoluteFile().getParentFile().mkdirs();int profiles=0,connected=0,cut=0,released=0;Gson gson=new Gson();World world=new World();
        try(PrintWriter out=new PrintWriter(file,"UTF-8")){
            for(BlockCanopy self:blocks)for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int mount=0;mount<4;mount++)for(int bit:new int[]{1,2,4,8})for(int p=-1;p<blocks.size();p++)for(EnumFacing peerFacing:EnumFacing.HORIZONTALS)for(int different=0;different<2;different++)for(int role=0;role<3;role++){
                EnumFacing dir=bit==1?facing.rotateYCCW():bit==2?facing.rotateY():bit==4?facing:facing.getOpposite();BlockPos query=ROOT.offset(dir,bit==8?self.length:1);BlockCanopy other=p<0?null:blocks.get(p);BlockPos owner=other==null||role==0?query:query.offset(peerFacing,other.length-1);if(owner.equals(ROOT))continue;
                world.clear();TileEntityCanopy source=install(world,ROOT,self,facing,mount);
                if(other!=null){TileEntityCanopy parent=install(world,owner,other,peerFacing,(mount+different)%4);if(!owner.equals(query)){if(role==1){TileEntityCanopy helper=install(world,query,other,peerFacing,(mount+different)%4);helper.anchor=owner;}else{parent.setFootprint(Arrays.asList(owner,query));parent.releasedCell(query,true);released++;}}else if(role==2)world.setBlockToAir(query);}
                TileEntityCanopy raw=CanopyReservations.logicalAt(world,query),normalized=raw;if(self.kind.equals("regular_canopy_glass")&&normalized!=null)normalized=normalized.constituent();if(self.opening()&&bit==8&&normalized!=null&&normalized.facing()==facing.getOpposite())normalized=normalized.constituent();
                TileEntityCanopy roof=CanopyReservations.logicalAt(world,ROOT.offset(dir));if(roof!=null)roof=roof.constituent();int mask=source.mask()&bit,roofMask=source.roofMask()&bit;JsonArray row=new JsonArray();row.add(self.kind);row.add(self.length);row.add(facing.getIndex());row.add(mount);row.add(bit);row.add(peer(normalized));row.add(roof==null||roof.block()==null?JsonNull.INSTANCE:new JsonPrimitive(roof.block().kind));row.add(mask!=0);row.add(roofMask!=0);row.add(role);out.println(gson.toJson(row));profiles++;if(mask!=0)connected++;if(mask!=roofMask)cut++;
            }
        }
        if(connected==0||cut==0||released==0)throw new AssertionError("Source contact coverage");SegmentedReferenceGolden.check(file,"b6a40b1292e681d2145a2928ef8181bc15b90512efbcc0c1e13ef0948ecdd8fc");System.out.println("Actual Canopy contacts PASS profiles="+profiles+" connected="+connected+" roofCuts="+cut+" released="+released);
    }
}
