package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.render.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Caller storage must preserve every coordinate and remain private to the active draw. */
final class TrapdoorScratchChecks {
    static void run() {
        Random random=new Random(0x53435241);double[][] out=new double[8][3];int checks=0;
        double[] poses={-1,0,.125,.5,.875,1,2,Double.NaN};
        for(int sample=0;sample<4096;sample++) {
            int position=sample%3,turns=(sample>>2)%11-4;
            boolean sliding=(sample&1)!=0,inverted=(sample&2)!=0,reverse=(sample&4)!=0,into=(sample&8)!=0;
            double pose=poses[(sample>>4)&7],hinge=random.nextDouble()*16-8,travel=random.nextDouble()*8;
            double[][] expected=ReferenceFlatTrapdoorGeometry.corners(position,sliding,turns,pose,hinge,travel);
            TrapdoorGeometry.writeCorners(position,sliding,turns,pose,hinge,travel,out);same(expected,out);
            same(expected,TrapdoorGeometry.corners(position,sliding,turns,pose,hinge,travel));checks+=2;
            expected=ReferenceFlatTrapdoorGeometry.surfaceCorners(position,turns,pose,hinge,travel);
            TrapdoorGeometry.writeSurfaceCorners(position,turns,pose,hinge,travel,out);same(expected,out);
            same(expected,TrapdoorGeometry.surfaceCorners(position,turns,pose,hinge,travel));checks+=2;
            expected=ReferenceFlatTrapdoorGeometry.coverCorners(position,sliding,turns,pose,hinge);
            TrapdoorGeometry.writeCoverCorners(position,sliding,turns,pose,hinge,out);same(expected,out);
            same(expected,TrapdoorGeometry.coverCorners(position,sliding,turns,pose,hinge));checks+=2;
            expected=ReferenceDiagonalTrapdoorGeometry.corners(position,inverted,turns,sliding,reverse,pose,hinge,travel,reverse?-1:1,into);
            DiagonalTrapdoorGeometry.writeCorners(position,inverted,turns,sliding,reverse,pose,hinge,travel,reverse?-1:1,into,out);same(expected,out);
            same(expected,DiagonalTrapdoorGeometry.corners(position,inverted,turns,sliding,reverse,pose,hinge,travel,reverse?-1:1,into));checks+=2;
        }
        for(int shape=0;shape<4;shape++)for(int facing=0;facing<4;facing++)for(int pattern=0;pattern<32;pattern++) {
            boolean diagonal=shape>0;
            TileEntityProgrammableTrapdoor tile=diagonal?new TileEntityProgrammableDiagonalTrapdoor():new TileEntityProgrammableTrapdoor();
            tile.setPos(new BlockPos(-17+(pattern&1),64,16));tile.configure((pattern&2)!=0?ScreenHousingTextures.doorIndex(0,0):4,diagonal?shape-1:pattern%3,(pattern&1)!=0,0,0);
            tile.setTileTexture((pattern&4)!=0);if(!diagonal)tile.setCover((pattern&8)!=0);
            BlockProgrammableTrapdoor block=(BlockProgrammableTrapdoor)(diagonal?ModBlocks.PROGRAMMABLE_DIAGONAL_TRAPDOOR:ModBlocks.PROGRAMMABLE_TRAPDOOR);
            IBlockState state=block.getDefaultState().withProperty(BlockTrapDoor.FACING,EnumFacing.getHorizontal(facing))
                    .withProperty(BlockTrapDoor.HALF,(pattern&16)!=0?BlockTrapDoor.DoorHalf.TOP:BlockTrapDoor.DoorHalf.BOTTOM);
            List<TileEntityProgrammableTrapdoor> group=new ArrayList<>();group.add(tile);
            for(int i=1;i<=pattern%4;i++) {
                TileEntityProgrammableTrapdoor peer=diagonal?new TileEntityProgrammableDiagonalTrapdoor():new TileEntityProgrammableTrapdoor();
                peer.setPos(tile.getPos().add(i-2,i%2,i%3));group.add(peer);
            }
            double[][] expected=ReferenceTrapdoorCoordinates.coordinates(tile,state,group);
            try(TrapdoorRenderScratch scratch=TrapdoorRenderScratch.acquire()) {
                TEProgrammableTrapdoor.materialCoordinates(tile,state,group,scratch.closed,scratch.uv);same(expected,scratch.uv);
            }
            checks++;
        }
        TrapdoorRenderScratch first=TrapdoorRenderScratch.acquire();first.uv[0][0]=42;
        try(TrapdoorRenderScratch nested=TrapdoorRenderScratch.acquire()) {
            require(first!=nested && first.uv!=nested.uv,"nested draw aliases active arrays");nested.uv[0][0]=7;
        }
        require(first.uv[0][0]==42,"nested draw overwrote active data");first.close();
        try(TrapdoorRenderScratch reused=TrapdoorRenderScratch.acquire()){require(reused==first,"outer storage was not reused");}
        // A failure must release the slot just as an ordinary completed draw does.
        try(TrapdoorRenderScratch scratch=TrapdoorRenderScratch.acquire()){throw new IllegalStateException("check");}
        catch(IllegalStateException expected){}
        try(TrapdoorRenderScratch reused=TrapdoorRenderScratch.acquire()){require(reused==first,"exception leaked borrowed storage");}
        System.out.println("PASS: "+checks+" exact trapdoor corner/UV comparisons, nested draw isolation and exception-safe reuse");
    }
    private static void same(double[][] expected,double[][] actual) {
        require(Arrays.deepEquals(expected,actual),"trapdoor coordinates changed");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
