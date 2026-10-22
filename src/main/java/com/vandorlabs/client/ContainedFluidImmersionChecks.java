package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntitySpaceDoor;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraftforge.fluids.*;

/** Exercise vanilla eye immersion and the camera-state path used by underwater fog. */
final class ContainedFluidImmersionChecks {
    static void run(Minecraft mc)throws Exception{
        BlockPos pos=new BlockPos(350,48,146);int cases=0;
        EntityLivingBase probe=new net.minecraft.client.entity.EntityOtherPlayerMP(mc.world,new com.mojang.authlib.GameProfile(java.util.UUID.fromString("902d3aee-c01d-4ae4-b74d-54925cd02690"),"FluidProbe"));
        java.lang.reflect.Field camera=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(ActiveRenderInfo.class,"position","field_178811_e");
        Object original=camera.get(null);
        try{
            camera.set(null,new Vec3d(0,probe.getEyeHeight(),0));
            checkSequence(mc,probe,pos);
            for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int depth=0;depth<3;depth++){
                IBlockState state=Block.getBlockFromName("vandorlabs:programmable_door").getDefaultState().withProperty(BlockVandorDoor.FACING,facing).withProperty(BlockVandorDoor.OPEN,true);
                mc.world.setBlockState(pos,state,2);mc.world.setBlockState(pos.up(),state.withProperty(BlockVandorDoor.HALF,BlockDoor.EnumDoorHalf.UPPER),2);
                ((TileEntitySpaceDoor)mc.world.getTileEntity(pos)).setPlacementDepth(depth);
                for(int level=0;level<2;level++)for(int side=0;side<2;side++)WaterloggedParts.data(mc.world.getTileEntity(pos.up(level))).set(side,new FluidStack(FluidRegistry.WATER,1000));
                for(int level=0;level<2;level++)for(double offset:new double[]{-.001,0,.001}){
                    check(mc,probe,new Vec3d(pos.getX()+.5+offset,pos.getY()+level+.5,pos.getZ()+.5),Material.WATER,"door center "+facing+" depth="+depth);cases++;
                }
                checkFog(mc,probe,new Vec3d(pos.getX()+.5,pos.getY()+1.5,pos.getZ()+.5));
                ContainedFluids upper=WaterloggedParts.data(mc.world.getTileEntity(pos.up()));upper.set(0,null);upper.set(1,null);
                for(double eyeHeight:new double[]{.25,.999,1,1.001,1.5}){
                    check(mc,probe,new Vec3d(pos.getX()+.5,pos.getY()+eyeHeight,pos.getZ()+.5),eyeHeight<1?Material.WATER:null,"lower-only door eye height="+eyeHeight);
                    checkFog(mc,probe,new Vec3d(pos.getX()+.5,pos.getY()+eyeHeight,pos.getZ()+.5),eyeHeight<1);cases++;
                }
                mc.world.setBlockToAir(pos.up());mc.world.setBlockToAir(pos);
            }
            for(String id:new String[]{"programmable_wall","programmable_porthole_wall","programmable_glass","programmable_diagonal_wall","programmable_diagonal_porthole","programmable_diagonal_glass","programmable_slab","programmable_stairs","programmable_trapdoor","programmable_diagonal_trapdoor"}){
                Block block=Block.getBlockFromName("vandorlabs:"+id);mc.world.setBlockState(pos,block.getDefaultState(),2);
                ContainedFluids data=WaterloggedParts.data(mc.world.getTileEntity(pos));
                data.set(0,new FluidStack(FluidRegistry.WATER,1000));data.set(1,new FluidStack(FluidRegistry.LAVA,1000));
                for(WaterloggedParts.Cell cell:WaterloggedParts.volume(mc.world,pos).cells){
                    Vec3d point=cell.box.getCenter().addVector(pos.getX(),pos.getY(),pos.getZ());
                    check(mc,probe,point,cell.side==0?Material.WATER:Material.LAVA,id+" wet side="+cell.side);cases++;
                }
                data.set(0,null);data.set(1,null);
                check(mc,probe,new Vec3d(pos).addVector(.5,.5,.5),null,id+" drained");cases++;
                mc.world.setBlockToAir(pos);
            }
        }finally{camera.set(null,original);mc.world.setBlockToAir(pos.up());mc.world.setBlockToAir(pos);}
        System.out.println("[vandorlabs][reprolab] contained-fluid-immersion PASS cases="+cases+" vanilla eye-material camera-viewpoint and underwater fog density/mode door centers four facings three depths both halves dry upper half water/lava panels slabs stairs trapdoors drained states");
    }
    private static void check(Minecraft mc,EntityLivingBase probe,Vec3d eye,Material expected,String context){
        probe.setPosition(eye.x,eye.y-probe.getEyeHeight(),eye.z);
        probe.prevPosX=probe.posX;probe.prevPosY=probe.posY;probe.prevPosZ=probe.posZ;
        Material viewed=ActiveRenderInfo.getBlockStateAtEntityViewpoint(mc.world,probe,1).getMaterial();
        require(expected==null?viewed!=Material.WATER&&viewed!=Material.LAVA:viewed==expected,"camera fluid "+context);
        require(probe.isInsideOfMaterial(Material.WATER)==(expected==Material.WATER),"water eye immersion "+context);
        require(probe.isInsideOfMaterial(Material.LAVA)==(expected==Material.LAVA),"lava eye immersion "+context);
    }
    private static void checkSequence(Minecraft mc,EntityLivingBase probe,BlockPos base)throws Exception{
        BlockPos row=base.up(),door=base.south();
        IBlockState doorState=Block.getBlockFromName("vandorlabs:programmable_door").getDefaultState().withProperty(BlockVandorDoor.FACING,EnumFacing.NORTH).withProperty(BlockVandorDoor.OPEN,true);
        net.minecraft.entity.Entity original=mc.getRenderViewEntity();
        org.lwjgl.opengl.GL11.glPushAttrib(org.lwjgl.opengl.GL11.GL_ALL_ATTRIB_BITS);
        try{
            mc.world.setBlockState(row,net.minecraft.init.Blocks.WATER.getDefaultState(),2);
            mc.world.setBlockState(door,doorState,2);mc.world.setBlockState(door.up(),doorState.withProperty(BlockVandorDoor.HALF,BlockDoor.EnumDoorHalf.UPPER),2);
            for(int y=0;y<2;y++)for(int side=0;side<2;side++)WaterloggedParts.data(mc.world.getTileEntity(door.up(y))).set(side,new FluidStack(FluidRegistry.WATER,1000));
            mc.world.setBlockState(row.south(2),net.minecraft.init.Blocks.WATER.getDefaultState(),2);
            mc.world.setBlockState(row.south(3),Block.getBlockFromName("vandorlabs:programmable_porthole_wall").getDefaultState().withProperty(BlockProgrammableWall.FACING,EnumFacing.NORTH),2);
            WaterloggedParts.data(mc.world.getTileEntity(row.south(3))).set(0,new FluidStack(FluidRegistry.WATER,1000));
            mc.world.setBlockToAir(row.south(4));
            java.lang.reflect.Method color=net.minecraftforge.fml.relauncher.ReflectionHelper.findMethod(net.minecraft.client.renderer.EntityRenderer.class,"updateFogColor","func_78466_h",float.class);
            mc.setRenderViewEntity(probe);float[] reference=null;
            double[] offsets={.5,1.5,2.5,3.2,3.8,4.5};
            String[] names={"native water","open door center","native water after door","wet porthole side","dry porthole side","clear air"};
            for(int i=0;i<offsets.length;i++){
                Vec3d eye=new Vec3d(row.getX()+.5,row.getY()+.5,row.getZ()+offsets[i]);
                check(mc,probe,eye,i<4?Material.WATER:null,names[i]);
                color.invoke(mc.entityRenderer,1F);
                java.nio.FloatBuffer rgba=org.lwjgl.BufferUtils.createFloatBuffer(16);org.lwjgl.opengl.GL11.glGetFloat(org.lwjgl.opengl.GL11.GL_COLOR_CLEAR_VALUE,rgba);
                if(i==0){reference=new float[]{rgba.get(0),rgba.get(1),rgba.get(2)};require(reference[2]>reference[0]*2&&reference[2]>reference[1]*2,"native-water fog reference is dark blue");}
                if(i<4){for(int channel=0;channel<3;channel++)require(Math.abs(rgba.get(channel)-reference[channel])<1e-5,"fog color changes at "+names[i]);checkFog(mc,probe,eye);}
                else{java.lang.reflect.Method fog=net.minecraftforge.fml.relauncher.ReflectionHelper.findMethod(net.minecraft.client.renderer.EntityRenderer.class,"setupFog","func_78468_a",int.class,float.class);fog.invoke(mc.entityRenderer,0,1F);require(org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_FOG_MODE)==org.lwjgl.opengl.GL11.GL_LINEAR,"dry exit restores air fog at "+names[i]);}
            }
            System.out.println("[vandorlabs][reprolab] fluid-transition-sequence PASS water -> open door center -> water -> one-sided porthole -> clear air; identical underwater RGB/density, dry exit restores air");
        }finally{mc.setRenderViewEntity(original);org.lwjgl.opengl.GL11.glPopAttrib();for(int z=0;z<5;z++){mc.world.setBlockToAir(row.south(z));mc.world.setBlockToAir(base.south(z));}}
    }
    private static void checkFog(Minecraft mc,EntityLivingBase probe,Vec3d eye)throws Exception{
        checkFog(mc,probe,eye,true);
    }
    private static void checkFog(Minecraft mc,EntityLivingBase probe,Vec3d eye,boolean wet)throws Exception{
        check(mc,probe,eye,wet?Material.WATER:null,"fog viewpoint");
        net.minecraft.entity.Entity original=mc.getRenderViewEntity();
        int oldMode=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_FOG_MODE);
        float oldDensity=org.lwjgl.opengl.GL11.glGetFloat(org.lwjgl.opengl.GL11.GL_FOG_DENSITY);
        try{
            mc.setRenderViewEntity(probe);
            java.lang.reflect.Method setup=net.minecraftforge.fml.relauncher.ReflectionHelper.findMethod(net.minecraft.client.renderer.EntityRenderer.class,"setupFog","func_78468_a",int.class,float.class);
            setup.invoke(mc.entityRenderer,0,1F);
            require(org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_FOG_MODE)==(wet?org.lwjgl.opengl.GL11.GL_EXP:org.lwjgl.opengl.GL11.GL_LINEAR),"eye height selects water/air fog mode at "+eye+" wet="+wet+" actual="+org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_FOG_MODE));
            if(wet)require(Math.abs(org.lwjgl.opengl.GL11.glGetFloat(org.lwjgl.opengl.GL11.GL_FOG_DENSITY)-.1F)<1e-5,"door-center fog uses vanilla water density");
        }finally{mc.setRenderViewEntity(original);net.minecraft.client.renderer.GlStateManager.setFog(oldMode==org.lwjgl.opengl.GL11.GL_EXP?net.minecraft.client.renderer.GlStateManager.FogMode.EXP:oldMode==org.lwjgl.opengl.GL11.GL_EXP2?net.minecraft.client.renderer.GlStateManager.FogMode.EXP2:net.minecraft.client.renderer.GlStateManager.FogMode.LINEAR);net.minecraft.client.renderer.GlStateManager.setFogDensity(oldDensity);}
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
