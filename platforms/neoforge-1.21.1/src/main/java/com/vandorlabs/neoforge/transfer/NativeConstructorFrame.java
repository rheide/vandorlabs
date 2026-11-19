package com.vandorlabs.neoforge.transfer;

import com.vandorlabs.neoforge.ProgrammableBlock;
import com.vandorlabs.neoforge.ProgrammableBlockEntity;
import com.vandorlabs.neoforge.VandorLabsBootstrap;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

/** A constructor part retains the displaced cube, with coordinates relative to the reservation. */
public final class NativeConstructorFrame {
    public static final String DATA="ConstructorFrame";
    private static final int MAX_RETAINED_BYTES=60*1024;
    private static final int MAX_CELL_TILE_BYTES=64*1024;
    private static final List<String> COORDINATES=List.of("x","y","z");

    public static ProgrammableBlock block(){return VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get();}

    public static boolean fits(Level level,BlockPos pos,boolean frame){
        var state=level.getBlockState(pos);
        return frame?state.is(block())&&level.getBlockEntity(pos) instanceof ProgrammableBlockEntity
                :state.isAir()&&level.getBlockEntity(pos)==null;
    }

    public static boolean supports(BlockEntity tile){return tile.getClass()==ProgrammableBlockEntity.class;}

    public static NativeRawCell defaults(BlockPos pos,HolderLookup.Provider registries){
        var state=block().defaultBlockState();
        var tile=new ProgrammableBlockEntity(pos,state);
        return new NativeRawCell(pos,state,tile.saveWithFullMetadata(registries),MAX_CELL_TILE_BYTES);
    }

    public static void store(CompoundTag reservation,NativeRawCell original){
        if(!original.state.is(block())||!original.hasTile())throw new IllegalArgumentException("Invalid programmable frame block");
        var data=new CompoundTag();
        data.putString("Facing",original.state.getValue(ProgrammableBlock.FACING).getName());
        var tile=original.tile();
        for(var key:COORDINATES)tile.remove(key);
        data.put("Tile",tile);
        validate(data);
        reservation.put(DATA,data);
    }

    public static void validatePart(CompoundTag reservation,boolean frame){
        if(!reservation.contains(DATA))return;
        if(!frame||!reservation.contains(DATA,Tag.TAG_COMPOUND))
            throw new IllegalArgumentException("Unexpected programmable frame data");
        validate(reservation.getCompound(DATA));
    }

    private static void validate(CompoundTag data){
        if(!data.getAllKeys().equals(Set.of("Facing","Tile"))||!data.contains("Facing",Tag.TAG_STRING)
                ||Direction.byName(data.getString("Facing"))==null||!data.contains("Tile",Tag.TAG_COMPOUND)
                ||data.sizeInBytes()>MAX_RETAINED_BYTES)
            throw new IllegalArgumentException("Invalid programmable frame data");
        var tile=data.getCompound("Tile");
        var id=BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(VandorLabsBootstrap.PROGRAMMABLE_ENTITY.get()).toString();
        if(!tile.getString("id").equals(id)||COORDINATES.stream().anyMatch(tile::contains))
            throw new IllegalArgumentException("Invalid programmable frame tile identity");
    }

    public static NativeRawCell restore(NativeRawCell reservation,boolean frame,HolderLookup.Provider registries){
        var raw=reservation.tile();
        validatePart(raw,frame);
        if(!frame)return new NativeRawCell(reservation.pos,Blocks.AIR.defaultBlockState(),null,MAX_CELL_TILE_BYTES);
        if(!raw.contains(DATA))return defaults(reservation.pos,registries);
        var data=raw.getCompound(DATA);
        var state=block().defaultBlockState().setValue(ProgrammableBlock.FACING,Direction.byName(data.getString("Facing")));
        var tile=data.getCompound("Tile");
        tile.putInt("x",reservation.pos.getX());tile.putInt("y",reservation.pos.getY());tile.putInt("z",reservation.pos.getZ());
        return new NativeRawCell(reservation.pos,state,tile,MAX_CELL_TILE_BYTES);
    }

    public static void requireMorph(NativeRawCell original,NativeRawCell reservation,boolean frame,boolean mined,
                                    HolderLookup.Provider registries){
        var expected=restore(reservation,frame,registries);
        if(mined?original.hasTile()||!original.state.isAir():!original.identical(expected))
            throw new IllegalArgumentException("Programmable frame state or texture changed");
    }

    public static void rotate(CompoundTag reservation,java.util.function.UnaryOperator<Direction> transform){
        if(!reservation.contains(DATA))return;
        var data=reservation.getCompound(DATA);
        validate(data);
        var facing=Direction.byName(data.getString("Facing"));
        var rotated=transform.apply(facing);
        data.putString("Facing",rotated.getName());
        rotateFaces(data.getCompound("Tile"),facing,rotated,transform);
    }

    public static void rotateFaces(CompoundTag tile,Direction facing,Direction rotated,
                                   java.util.function.UnaryOperator<Direction> transform){
        var current=tile.getIntArray("FaceTextures");
        if(current.length!=6)return;
        var next=new int[6];
        for(var face:Direction.values())next[ProgrammableBlock.localTextureFace(transform.apply(face),rotated)]=
                current[ProgrammableBlock.localTextureFace(face,facing)];
        tile.putIntArray("FaceTextures",next);
    }

    public static ItemStack drop(NativeRawCell reservation,HolderLookup.Provider registries){
        var cell=restore(reservation,true,registries);
        var item=new ItemStack(block());
        if(reservation.tile().contains(DATA)){
            var tile=cell.tile();
            for(var key:COORDINATES)tile.remove(key);
            item.set(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,net.minecraft.world.item.component.CustomData.of(tile));
        }
        return item;
    }

    private NativeConstructorFrame(){}
}
