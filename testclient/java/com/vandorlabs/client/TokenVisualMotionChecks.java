package com.vandorlabs.client;

import com.vandorlabs.tiles.TileEntityTokenSlot;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

final class TokenVisualMotionChecks {
    private static void close(float actual,float expected){if(Math.abs(actual-expected)>1e-6)throw new AssertionError("Reader visual age "+actual+" != "+expected);}
    static void run(){
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);BlockPos pos=new BlockPos(8,80,8);TileEntityTokenSlot tile=new TileEntityTokenSlot(true);tile.setWorld(world);tile.setPos(pos);
        close(TokenSlotVisualMotion.ageAt(tile,0),20);
        NBTTagCompound tag=new NBTTagCompound();tag.setInteger("x",pos.getX());tag.setInteger("y",pos.getY());tag.setInteger("z",pos.getZ());tag.setLong("TokenInsertStarted",1000);tag.setLong("TokenInsertionSequence",1);tag.setTag("InsertedToken",new ItemStack(Items.PAPER).writeToNBT(new NBTTagCompound()));
        tile.readFromNBT(tag);close(TokenSlotVisualMotion.ageAt(tile,1),0);world.time=1_000_000;close(TokenSlotVisualMotion.ageAt(tile,5),4);world.time=-1_000_000;close(TokenSlotVisualMotion.ageAt(tile,5),4);
        tile.readFromNBT(tag);close(TokenSlotVisualMotion.ageAt(tile,7),6);
        TileEntityTokenSlot replacement=new TileEntityTokenSlot(true);replacement.setWorld(world);replacement.setPos(pos);replacement.readFromNBT(tag);close(TokenSlotVisualMotion.ageAt(replacement,9),8);close(TokenSlotVisualMotion.ageAt(replacement,40),20);
        NBTTagCompound empty=tag.copy();empty.removeTag("InsertedToken");replacement.readFromNBT(empty);close(TokenSlotVisualMotion.ageAt(replacement,41),20);tag.setLong("TokenInsertionSequence",2);replacement.readFromNBT(tag);close(TokenSlotVisualMotion.ageAt(replacement,42),0);
        com.vandorlabs.tiles.TileEntitySpaceDoor door=new com.vandorlabs.tiles.TileEntitySpaceDoor();door.setWorld(world);door.setPos(pos);
        TileEntityTokenSlot front=door.keyReader(0),rear=door.keyReader(1);close(TokenSlotVisualMotion.ageAt(front,0),20);close(TokenSlotVisualMotion.ageAt(rear,0),20);
        front.readFromNBT(tag);close(TokenSlotVisualMotion.ageAt(front,1),0);close(TokenSlotVisualMotion.ageAt(rear,1),20);close(TokenSlotVisualMotion.ageAt(front,5),4);
        rear.readFromNBT(tag);close(TokenSlotVisualMotion.ageAt(rear,3),0);close(TokenSlotVisualMotion.ageAt(front,7),6);close(TokenSlotVisualMotion.ageAt(rear,7),4);
        System.out.println("Reader visual motion PASS insertion retained pose clock jumps pause repeated packets tile replacement reinsertion sequence independent front/rear door readers");
    }
}
