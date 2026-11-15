package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.surface.TextureJoinSettings;
import com.vandorlabs.surface.TextureJoinOwner;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Independent endpoint-policy oracle and actual emitted frame/aperture regressions. */
final class DirectionalJoinRegressionChecks {
    private static final BlockPos BASE=new BlockPos(8,80,8);
    private static final List<String> FAILURES=new ArrayList<>();
    private static int checks;
    static void run(Minecraft mc) {
        FAILURES.clear();checks=0;
        for(boolean rotated:new boolean[]{false,true}) {
            EnumFacing across=rotated?EnumFacing.SOUTH:EnumFacing.EAST;
            for(EnumFacing step:new EnumFacing[]{across,EnumFacing.UP})
                for(int a=0;a<6;a++)for(int b=0;b<6;b++) {
                    glass(mc,rotated,step,a,b,false);
                    glass(mc,rotated,step,a,b,true);
                    porthole(mc,"programmable_porthole_wall",rotated,step,a,b);
                    porthole(mc,"programmable_porthole_block",rotated,step,a,b);
                }
        }
        eligibility(mc);
        System.out.println("[vandorlabs][reprolab] directional-join-regression checks="+checks+" failures="+FAILURES.size());
        for(int i=0;i<Math.min(16,FAILURES.size());i++)System.out.println("[vandorlabs][reprolab] join-failure "+FAILURES.get(i));
        if(!FAILURES.isEmpty())throw new IllegalStateException("Directional joins: "+FAILURES.size()+" failures; first="+FAILURES.get(0));
        System.out.println("[vandorlabs][reprolab] directional-join-regression PASS mutual axis policy/both endpoints/default and selected emitted frames/flat and full apertures");
    }
    private static boolean admits(int policy,EnumFacing step) {
        switch(policy) {
            case 0:return false;
            case 1:return true;
            case 2:return step.getAxis()==EnumFacing.Axis.Y;
            case 3:return step.getAxis()!=EnumFacing.Axis.Y;
            case 4:return step.getAxis()==EnumFacing.Axis.Z;
            case 5:return step.getAxis()==EnumFacing.Axis.X;
            default:throw new AssertionError(policy);
        }
    }
    private static void need(boolean valid,String detail) { checks++;if(!valid)FAILURES.add(detail); }
    private static VehicleWorld pair(Minecraft mc,Block block,IBlockState state,EnumFacing step,int a,int b,boolean selected) {
        List<VehicleStructure.Cell> cells=new ArrayList<>();
        for(int i=0;i<2;i++) {
            BlockPos at=BASE.offset(step,i);TileEntity tile=block.createTileEntity(mc.world,state);tile.setPos(at);
            ((TextureJoinOwner)tile).setTextureJoin(new TextureJoinSettings(i==0?a:b,0));
            if(tile instanceof TileEntityProgrammableGlass&&selected)
                ((TileEntityProgrammableGlass)tile).frameAppearance.front=ScreenHousingTextures.screenIndex("frames/frames-screen-full/frame_plain_1254");
            cells.add(new VehicleStructure.Cell(at,state,tile.writeToNBT(new NBTTagCompound())));
        }
        return new VehicleWorld(mc.world,VehicleStructure.display(cells));
    }
    private static void glass(Minecraft mc,boolean rotated,EnumFacing step,int a,int b,boolean selected) {
        Block block=Block.getBlockFromName("vandorlabs:programmable_glass");IBlockState state=block.getDefaultState().withProperty(BlockGlassWall.ROTATED,rotated);
        VehicleWorld actual=pair(mc,block,state,step,a,b,selected);
        boolean join=admits(a,step)&&admits(b,step.getOpposite());
        VehicleWorld reference=pair(mc,block,state,step,join?1:0,join?1:0,selected);
        try { for(int i=0;i<2;i++) {
            BlockPos at=BASE.offset(step,i);TileEntityProgrammableGlass tile=(TileEntityProgrammableGlass)actual.getTileEntity(at),expected=(TileEntityProgrammableGlass)reference.getTileEntity(at);
            String label="glass "+(selected?"selected":"default")+" rotated="+rotated+" edge="+step+" policies="+a+"/"+b+" endpoint="+i;
            need(records(mc,tile,state,selected).equals(records(mc,expected,state,selected)),label+" emitted frame differs from "+(join?"joined":"separate")+" oracle");
            GlassSurfaceGroups.Layout layout=GlassSurfaceGroups.frameLayout(tile);
            need((step.getAxis()==EnumFacing.Axis.Y?layout.rows:layout.columns)==(join?2:1),label+" frame artwork extent");
        }} finally { GlassSurfaceGroups.unload(actual);GlassSurfaceGroups.unload(reference); }
    }
    private static List<String> records(Minecraft mc,TileEntityProgrammableGlass tile,IBlockState state,boolean selected) {
        List<BakedQuad> quads=new ArrayList<>();
        if(selected)quads.addAll(GlassFrameModel.build(tile,state,DefaultVertexFormats.BLOCK).quads);
        else {
            IBlockState actual=state.getBlock().getActualState(state,tile.getWorld(),tile.getPos());
            IBakedModel model=mc.getBlockRendererDispatcher().getModelForState(actual);
            quads.addAll(model.getQuads(actual,null,0));for(EnumFacing face:EnumFacing.values())quads.addAll(model.getQuads(actual,face,0));
        }
        List<String> result=new ArrayList<>();for(BakedQuad quad:quads)result.add(quad.getSprite().getIconName()+":"+quad.getFace()+":"+Arrays.toString(quad.getVertexData()));Collections.sort(result);return result;
    }
    private static void porthole(Minecraft mc,String id,boolean rotated,EnumFacing step,int a,int b) {
        Block block=Block.getBlockFromName("vandorlabs:"+id);IBlockState state=block.getDefaultState().withProperty(BlockProgrammableWall.FACING,rotated?EnumFacing.WEST:EnumFacing.NORTH);
        VehicleWorld actual=pair(mc,block,state,step,a,b,false);
        boolean join=admits(a,step)&&admits(b,step.getOpposite());VehicleWorld reference=pair(mc,block,state,step,join?1:0,join?1:0,false);
        try { for(int i=0;i<2;i++) {
            BlockPos at=BASE.offset(step,i);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)actual.getTileEntity(at),expected=(TileEntityAnimatedScreenSelector)reference.getTileEntity(at);
            TEAnimatedScreenSelector.PortholeGroup group=TEAnimatedScreenSelector.portholeGroup(tile,state);
            String label=id+" rotated="+rotated+" edge="+step+" policies="+a+"/"+b+" endpoint="+i;
            need((step.getAxis()==EnumFacing.Axis.Y?group.rows:group.columns)==(join?2:1),label+" aperture extent");
            PortholeRetainedGeometry.Surfaces mesh=PortholeRetainedGeometry.capture(tile,state),oracle=PortholeRetainedGeometry.capture(expected,state);
            need(mesh.housing.sameGeometry(oracle.housing)&&mesh.pane.sameGeometry(oracle.pane),label+" emitted aperture differs from "+(join?"joined":"separate")+" oracle");
        }} finally { PortholeGroupCache.unload(actual);PortholeGroupCache.unload(reference);GlassSurfaceGroups.unload(actual);GlassSurfaceGroups.unload(reference); }
    }
    private static void eligibility(Minecraft mc) {
        Block block=Block.getBlockFromName("vandorlabs:programmable_glass");IBlockState state=block.getDefaultState();VehicleWorld world=pair(mc,block,state,EnumFacing.EAST,1,1,true);
        try {
            TileEntityProgrammableGlass root=(TileEntityProgrammableGlass)world.getTileEntity(BASE),other=(TileEntityProgrammableGlass)world.getTileEntity(BASE.east());
            need(GlassSurfaceGroups.frameLayout(root).columns==2,"equal selected frame settings join before edit");other.frameAppearance.front=-1;other.markDirty();
            need(GlassSurfaceGroups.frameLayout(root).columns==1,"different selected frame artwork splits warm frame group");
            int first=ScreenHousingTextures.screenIndex("light_alloy_hull"),second=ScreenHousingTextures.screenIndex("dark_wall_panel");root.setMaterial(first);other.setMaterial(second);
            need(GlassSurfaceGroups.layout(root).columns==1,"different pane materials stay separate despite mutual On");
            other.setMaterial(first);other.setAppearance(2,true);
            need(GlassSurfaceGroups.layout(root).columns==1,"different pane tint stays separate despite mutual On");
        }finally{GlassSurfaceGroups.unload(world);}
        for(String id:new String[]{"programmable_porthole_wall","programmable_porthole_block"}) {
            block=Block.getBlockFromName("vandorlabs:"+id);state=block.getDefaultState();world=pair(mc,block,state,EnumFacing.EAST,1,1,false);
            try {TileEntityAnimatedScreenSelector root=(TileEntityAnimatedScreenSelector)world.getTileEntity(BASE),other=(TileEntityAnimatedScreenSelector)world.getTileEntity(BASE.east());
                need(TEAnimatedScreenSelector.portholeGroup(root,state).columns==2,id+" compatible shape joins before edit");other.setPortholeShape((root.getPortholeShape()+1)%4);
                need(TEAnimatedScreenSelector.portholeGroup(root,state).columns==1,id+" different aperture shape splits warm topology");
            }finally{PortholeGroupCache.unload(world);GlassSurfaceGroups.unload(world);}
        }
    }
    private DirectionalJoinRegressionChecks() { }
}
