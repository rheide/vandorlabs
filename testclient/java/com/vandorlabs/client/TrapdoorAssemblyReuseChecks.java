package com.vandorlabs.client;

import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.tiles.TileEntityProgrammableTrapdoor;
import net.minecraft.nbt.*;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Reusing membership must not couple edits or conceal unloaded/replaced leaves. */
final class TrapdoorAssemblyReuseChecks {
    static void run() {
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);
        List<TileEntityProgrammableTrapdoor> leaves=new ArrayList<>();
        List<NBTTagCompound> saved=new ArrayList<>();
        NBTTagList members=new NBTTagList();BlockPos origin=new BlockPos(12,100,4);
        for(int i=0;i<64;i++)members.appendTag(new NBTTagLong(origin.add(i%8,0,i/8).toLong()));
        for(int i=0;i<64;i++) {
            BlockPos at=origin.add(i%8,0,i/8);world.setBlockState(at,ModBlocks.PROGRAMMABLE_TRAPDOOR.getDefaultState(),2);
            TileEntityProgrammableTrapdoor leaf=(TileEntityProgrammableTrapdoor)world.getTileEntity(at);
            NBTTagCompound tag=leaf.writeToNBT(new NBTTagCompound());tag.setTag("TrapdoorAssembly",members.copy());
            leaf.readFromNBT(tag);leaves.add(leaf);saved.add(leaf.writeToNBT(new NBTTagCompound()));
        }
        TileEntityProgrammableTrapdoor root=leaves.get(0);
        for(int repeat=0;repeat<3;repeat++)for(int i=0;i<64;i++) {
            require(leaves.get(i).group().equals(leaves),"loaded group changed membership/order");
            require(saved.get(i).equals(leaves.get(i).writeToNBT(new NBTTagCompound())),"group lookup changed persisted data");
        }
        world.chunkLimit=true;require(root.group().size()==1,"group reuse concealed an unloaded chunk");world.chunkLimit=false;
        require(root.group().equals(leaves),"group did not return after chunk reload");
        NBTTagCompound changed=saved.get(63).copy();changed.setTag("TrapdoorAssembly",new NBTTagList());
        leaves.get(63).readFromNBT(changed);
        require(root.group().size()==1,"group reuse concealed a changed member");
        require(saved.get(0).equals(root.writeToNBT(new NBTTagCompound())),"one leaf's NBT edit mutated another leaf");
        leaves.get(63).readFromNBT(saved.get(63));require(root.group().equals(leaves),"restored membership not recognized");
        world.removeTileEntity(leaves.get(63).getPos());require(root.group().size()==1,"group reuse concealed tile removal");
        world.tiles.put(leaves.get(63).getPos(),leaves.get(63));require(root.group().equals(leaves),"restored tile not recognized");
        root.unpair();
        for(TileEntityProgrammableTrapdoor leaf:leaves)require(leaf.group().size()==1 && leaf.writeToNBT(new NBTTagCompound()).getTagList("TrapdoorAssembly",4).tagCount()==0,"shared group did not unlink every leaf");
        System.out.println("PASS: 64-leaf membership reuse preserves saved data, independent edits, chunk boundaries, replacement and unlinking");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
