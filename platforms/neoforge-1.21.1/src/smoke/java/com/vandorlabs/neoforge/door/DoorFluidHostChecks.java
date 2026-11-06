package com.vandorlabs.neoforge.door;

import com.vandorlabs.neoforge.fluid.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

/** Actual registered linked hosts; geometry itself has a separate frozen Forge replay. */
final class DoorFluidHostChecks {
    private static int checks;
    private static void need(boolean value,String message){checks++;if(!value)throw new AssertionError("Door fluid host: "+message);}
    static void run(ServerLevel level,NativeProgrammableDoorEntity lower){
        var upper=(NativeProgrammableDoorEntity)level.getBlockEntity(lower.getBlockPos().above());
        lower.refreshFluidGeometry();upper.refreshFluidGeometry();
        need(lower.openingFluidVolume()&&upper.openingFluidVolume(),"both installed parts publish opening volumes");
        need(lower.containedFluids()!=upper.containedFluids(),"parts retain independent slot owners");
        var volume=lower.fluidVolumeSnapshot();long revision=lower.fluidGeometryRevision();lower.refreshFluidGeometry();
        need(lower.fluidVolumeSnapshot()==volume&&lower.fluidGeometryRevision()==revision,"unchanged geometry retains identity and revision");
        var facing=lower.fluidPlacementFaces();need(facing.length==2&&facing[0].getOpposite()==facing[1],"opening placement samples both normal faces");
        var tag=upper.saveWithoutMetadata(level.registryAccess());var raw=new CompoundTag();raw.putString("id","fixture:missing_fluid");raw.putInt("amount",1000);raw.putString("Unknown","retain");var slots=new CompoundTag();slots.put("Side1",raw);tag.put(NativeFluidSlots.KEY,slots);upper.loadWithComponents(tag,level.registryAccess());upper.refreshFluidGeometry();
        need(upper.containedFluids().occupied(1)&&upper.containedFluids().get(1).isEmpty(),"unknown occupied slot stays inert");
        need(upper.saveWithoutMetadata(level.registryAccess()).getCompound(NativeFluidSlots.KEY).getCompound("Side1").equals(raw),"unknown upper slot saves losslessly");
        need(!NativeFluidWorld.canFill(upper,1,new FluidStack(Fluids.WATER,1000)),"bucket availability rejects opaque occupancy");
        need(NativeFluidWorld.set(lower,0,new FluidStack(Fluids.WATER,1000)),"installed lower water mutation");
        need(lower.containedFluids().get(0).getFluid()==Fluids.WATER&&!upper.containedFluids().occupied(0),"lower mutation cannot change upper slots");
        var publicTag=lower.getUpdateTag(level.registryAccess());need(publicTag.contains(NativeFluidSlots.KEY,10),"explicit fluid render projection synchronized");
        need(!lower.itemSettings().contains(NativeFluidSlots.KEY),"configured item excludes runtime fluid ownership");
        int luminous=-1,emission=0;for(var choice:com.vandorlabs.neoforge.ModernFinishCatalog.allChoices())if(choice.entry().frame()!=null&&choice.entry().frame().light()>0){luminous=choice.choice();emission=Math.max(0,Math.min(5,choice.entry().frame().light()));break;}
        need(luminous>=0,"canonical luminous frame exists");var originalFrames=lower.settings().frames();lower.stageFrames(new com.vandorlabs.persistence.FrameSettings(luminous,-1,-1,0,-1,-1,false,false,false,false));need(lower.intrinsicContainedLight()==emission,"framed door emits current frame metadata light");
        lower.stageFrames(new com.vandorlabs.persistence.FrameSettings(luminous,-1,-1,0,-1,-1,false,false,false,true));need(lower.intrinsicContainedLight()==0,"bare frame suppresses front emission");lower.stageFrames(originalFrames);
        var aux=level.getAuxLightManager(lower.getBlockPos());aux.setLightAt(lower.getBlockPos(),7);int workerLight=java.util.concurrent.CompletableFuture.supplyAsync(()->lower.getBlockState().getLightEmission(level,lower.getBlockPos())).join();need(workerLight==7,"actual light-emission worker reads only auxiliary manager");aux.setLightAt(lower.getBlockPos(),0);
        var snapshot=lower.containedFluids().snapshot();var copy=snapshot.side(0).fluid();copy.setAmount(1);need(snapshot.side(0).fluid().getAmount()==1000,"published slot snapshot cannot leak mutable stacks");
        need(lower.getModelData().get(NativeFluidRenderSnapshot.DATA)==lower.fluidRenderSnapshot(),"worker model snapshot includes published fluid data only");
        lower.containedFluids().set(0,FluidStack.EMPTY,level.registryAccess());upper.containedFluids().set(1,FluidStack.EMPTY,level.registryAccess());lower.containedFluidsChanged();upper.containedFluidsChanged();
        System.out.println("[vandorlabs][door-fluid-host] PASS checks="+checks+" per-half storage/opaque ownership/published geometry; ambient and gush separate");
    }
}
