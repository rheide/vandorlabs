package com.vandorlabs.client;

import com.vandorlabs.animation.ScreenBehavior;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import com.vandorlabs.tiles.TileEntityProgrammableLight;
import com.vandorlabs.blocks.BlockProgrammableLight;
import com.vandorlabs.blocks.ProgrammableLightConnections;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;

/** Power-independent display modes must not scan neighbors; Off still wakes on power. */
final class ScreenPowerReadChecks {
    static void run() {
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);
        BlockPos pos=new BlockPos(8,100,8);Probe tile=new Probe();tile.setWorld(world);tile.setPos(pos);
        int queries=0;
        for(int mode=0;mode<3;mode++)for(boolean gate:new boolean[]{false,true})
            for(boolean channel:new boolean[]{false,true})for(boolean local:new boolean[]{false,true}) {
                world.states.put(pos.north(),local?Blocks.REDSTONE_BLOCK.getDefaultState():Blocks.AIR.getDefaultState());
                tile.setDisplayMode(mode);tile.setRedstoneEnabled(gate);tile.setChannelSignal(channel);world.stateReads=0;
                require(tile.getEffectiveMode()==ScreenBehavior.effectiveMode(mode,gate,local||channel),"display power semantics changed");
                if(channel || !gate && mode!=0)queries+=world.stateReads;
                world.stateReads=0;
                require(tile.trigger()==(local||channel),"trigger lost local/channel OR");
                if(channel)require(world.stateReads==0,"powered channel still scanned physical neighbors");
                require(tile.hasLocalRedstoneSignal()==local,"channel fed back as a local source");
            }
        require(queries==0,"power-independent screen modes still scanned physical neighbors");
        System.out.println("Power-independent screen neighbor reads: "+queries);
        System.out.println("PASS: screen and trigger power matrix including ungated Off wake-up and source isolation");
        joinedLights();
    }
    private static void joinedLights() {
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);
        BlockProgrammableLight block=new BlockProgrammableLight();
        LightProbe[] lights=new LightProbe[64];
        BlockPos first=new BlockPos(4,100,4);
        for(int i=0;i<lights.length;i++) {
            LightProbe light=new LightProbe();BlockPos pos=first.add(i%8,i/8,0);
            light.setWorld(world);light.setPos(pos);world.states.put(pos,block.getDefaultState());
            world.tiles.put(pos,light);lights[i]=light;
        }
        for(int source:new int[]{0,63,17,-1,32,-1,0}) {
            for(int i=0;i<lights.length;i++){lights[i].powered=i==source;lights[i].reads=0;lights[i].writes=0;}
            ProgrammableLightConnections.refreshAround(world,first);
            int reads=0;
            for(LightProbe light:lights) {
                reads+=light.reads;
                require(light.joined==(source>=0),"joined light power differs from full OR");
                require(light.writes==1,"joined member omitted or notified twice");
            }
            if(source==0)require(reads==1,"joined OR still scans after finding a powered source");
            if(source<0)require(reads==64,"unpowered joined group skipped a source");
        }
        // A disconnected source must not power the old assembly.
        world.tiles.remove(lights[0].getPos());world.states.remove(lights[0].getPos());
        ProgrammableLightConnections.refreshAround(world,first);
        for(int i=1;i<lights.length;i++)require(!lights[i].joined,"removed source kept group powered");
        System.out.println("PASS: 64 joined lights preserve full OR propagation and source removal with early exit");
    }
    private static final class LightProbe extends TileEntityProgrammableLight {
        boolean powered,joined;int reads,writes;
        @Override public boolean isJoin(){return true;}
        @Override public int getFaceTexture(){return 0;}
        @Override public boolean hasDirectTriggerPower(){reads++;return powered;}
        @Override public void setJoinedTriggerPower(boolean power){joined=power;writes++;}
    }
    private static final class Probe extends TileEntityAnimatedScreenSelector {
        boolean trigger(){return isTriggerPowered();}
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
