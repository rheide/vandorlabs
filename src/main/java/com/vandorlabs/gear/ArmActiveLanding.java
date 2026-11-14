package com.vandorlabs.gear;

import com.vandorlabs.gear.core.GearTopology;
import com.vandorlabs.vehicle.*;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;

/** Adopt one native foot into a stopped arm through the same durable ownership boundary as payload motion. */
final class ArmActiveLanding {
    static java.util.function.Consumer<GearTileEntity> testBound;
    static java.util.function.Consumer<String> testObserver;
    private static void observe(String message){if(testObserver!=null&&System.getProperty("vandorlabs.reprolab")!=null)testObserver.accept(message);}
    /** False suspends this requested stroke until preparation/publication completes; it never rejects pending I/O. */
    static boolean prepare(GearTileEntity arm){try(ArmTransactions.Scope scope=ArmTransactions.authorizing(arm)){try{return prepareAuthorized(arm);}catch(RuntimeException|LinkageError failure){observe("refused "+failure.getMessage());throw failure;}}}
    private static boolean prepareAuthorized(GearTileEntity arm){
        if(!(arm.getWorld() instanceof WorldServer)||!arm.isSwingArmController()||arm.progress()!=0||arm.payload()!=null||arm.hasRetainedPayload())return true;
        ArmPayload.Endpoint endpoint=ArmPayload.endpoint(arm,0);if(endpoint==null)return true;
        WorldServer world=(WorldServer)arm.getWorld();
        ArmLandingPlan plan=ArmLandingPlan.capture(arm,endpoint);if(plan==null)return true;
        try(ArmTransactions.Scope scope=ArmTransactions.authorizing(arm)){
            if(!ArmTransactions.ready(arm,"attach-foot",plan.changed)){arm.awaitPayloadJournal();return false;}
            observe("prepared "+ArmTransactions.diagnostic(world,arm.getPos()));
            try{
                try(LargeGearBlock.ComponentRemoval removal=LargeGearBlock.withoutComponentBreak()){for(ArmLandingPlan.Foot foot:plan.feet){world.removeTileEntity(foot.original.pos);if(!world.setBlockState(foot.original.pos,Blocks.AIR.getDefaultState(),2)&&!world.isAirBlock(foot.original.pos))throw new IllegalArgumentException("A landing source could not be retired");}}
                // Install final states before prepared tiles. Chunk state changes
                // during a tile tick otherwise replace pending prepared tiles.
                for(ArmLandingPlan.Foot foot:plan.feet){IBlockState installed=foot.state.withProperty(GearBlock.ASSEMBLED,true);world.removeTileEntity(foot.target);if(world.getBlockState(foot.target)!=installed&&!world.setBlockState(foot.target,installed,2))throw new IllegalArgumentException("A landing source could not be installed");}
                for(ArmLandingPlan.Foot foot:plan.feet){foot.prepared.validate();world.setTileEntity(foot.target,foot.prepared);foot.prepared.markDirty();if(world.getTileEntity(foot.target)!=foot.prepared)throw new IllegalArgumentException("Landing tile installation failed");}
                observe("native feet installed before binding count="+plan.feet.size());if(!arm.formLandingAtActivePose())throw new IllegalArgumentException("Landing assembly could not bind at its active pose");
                observe("bound kind="+arm.assembly().kind()+" phase="+arm.progress());
                if(testBound!=null&&System.getProperty("vandorlabs.reprolab")!=null)testBound.accept(arm);
                ArmTransactions.applied(arm);arm.awaitPayloadJournal();return false;
            }catch(RuntimeException|LinkageError failure){ArmTransactions.failed(arm);throw failure;}
        }
    }
    private ArmActiveLanding(){}
}
