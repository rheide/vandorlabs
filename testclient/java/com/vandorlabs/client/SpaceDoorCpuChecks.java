package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.render.SpaceDoorControlPanel;
import com.vandorlabs.vehicle.VehicleStructure;
import com.vandorlabs.vehicle.VehicleWorld;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.*;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.function.Function;

/** Pinned pre-change algorithms and exact emitted records; no GL context or timing assertions. */
public final class SpaceDoorCpuChecks {
    private static final BlockPos POS=new BlockPos(8,80,8);
    private static final com.sun.management.ThreadMXBean METER=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    private static volatile Object consumed;
    private static void need(boolean yes,String message){if(!yes)throw new AssertionError(message);}
    private static BlockDetailedDoor mate(){return new BlockDetailedDoor("cpu_door_mate",BlockVandorDoor.DoorMotion.SLIDING,true,true,false,1,15,8,90,-90,-15,15);}
    private static BlockConfigurableSpaceDoor door(){return new BlockConfigurableSpaceDoor("cpu_door",true,mate());}
    private static IBlockState lower(Block block,EnumFacing facing){return block.getDefaultState().withProperty(BlockVandorDoor.FACING,facing).withProperty(BlockVandorDoor.HALF,BlockDoor.EnumDoorHalf.LOWER);}
    private static IBlockState upper(Block block,BlockDoor.EnumHingePosition hinge){return block.getDefaultState().withProperty(BlockVandorDoor.HALF,BlockDoor.EnumDoorHalf.UPPER).withProperty(BlockVandorDoor.HINGE,hinge);}
    private static void attach(NonRenderingChecks.MemoryWorld world,TileEntitySpaceDoor tile,BlockPos pos,IBlockState state){
        world.states.put(pos,state);world.tiles.put(pos,tile);tile.setWorld(world);tile.setPos(pos);tile.updateContainingBlockInfo();
    }
    // BlockVandorDoor/BlockConnectingDetailedDoor as at 02a0c652, including
    // malformed/missing halves and the original repeat reads of upper states.
    private static IBlockState oldBase(Block block,IBlockState state,IBlockAccess world,BlockPos pos){
        if(state.getValue(BlockVandorDoor.HALF)==BlockDoor.EnumDoorHalf.LOWER){
            IBlockState up=world.getBlockState(pos.up());
            if(up.getBlock()==block)state=state.withProperty(BlockVandorDoor.HINGE,up.getValue(BlockVandorDoor.HINGE)).withProperty(BlockVandorDoor.POWERED,up.getValue(BlockVandorDoor.POWERED));
        }else{IBlockState down=world.getBlockState(pos.down());if(down.getBlock()==block)state=state.withProperty(BlockVandorDoor.FACING,down.getValue(BlockVandorDoor.FACING));}
        return state;
    }
    private static IBlockState oldActual(Block block,IBlockState state,IBlockAccess world,BlockPos pos){
        IBlockState actual=oldBase(block,state,world,pos);
        BlockPos at=actual.getValue(BlockVandorDoor.HALF)==BlockDoor.EnumDoorHalf.LOWER?pos:pos.down();
        IBlockState low=world.getBlockState(at);boolean paired=false;
        if(low.getBlock()==block&&low.getValue(BlockVandorDoor.HALF)==BlockDoor.EnumDoorHalf.LOWER&&world.getBlockState(at.up()).getBlock()==block){
            IBlockState resolved=oldBase(block,low,world,at);EnumFacing facing=resolved.getValue(BlockVandorDoor.FACING);BlockDoor.EnumHingePosition hinge=resolved.getValue(BlockVandorDoor.HINGE);
            BlockPos neighbor=at.offset(hinge==BlockDoor.EnumHingePosition.LEFT?facing.rotateY():facing.rotateYCCW());
            IBlockState next=world.getBlockState(neighbor);
            if(next.getBlock()==block&&next.getValue(BlockVandorDoor.HALF)==BlockDoor.EnumDoorHalf.LOWER){
                IBlockState up=world.getBlockState(neighbor.up());
                if(up.getBlock()==block&&up.getValue(BlockVandorDoor.HALF)==BlockDoor.EnumDoorHalf.UPPER){
                    IBlockState resolvedNext=oldBase(block,next,world,neighbor);
                    paired=resolvedNext.getValue(BlockVandorDoor.FACING)==facing&&resolvedNext.getValue(BlockVandorDoor.HINGE)!=hinge;
                }
            }
        }
        return actual.withProperty(BlockConnectingDetailedDoor.PAIRED,paired);
    }
    private static void pairing(){
        BlockConfigurableSpaceDoor block=door();NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);Random random=new Random(40617);
        int cases=0;long oldReads=0,currentReads=0;
        for(int n=0;n<8192;n++){
            world.states.clear();
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=0;y<=1;y++){
                int choice=random.nextInt(20);world.states.put(POS.add(x,y,z),choice<16?block.getStateFromMeta(choice):Blocks.AIR.getDefaultState());
            }
            for(BlockPos at:new BlockPos[]{POS,POS.up()}){
                IBlockState input=block.getStateFromMeta(random.nextInt(16));world.stateReads=0;IBlockState expected=oldActual(block,input,world,at);oldReads+=world.stateReads;
                world.stateReads=0;IBlockState actual=block.getActualState(input,world,at);currentReads+=world.stateReads;
                need(expected==actual,"paired state/hinge/facing changed for partial or stale input");cases++;
            }
        }
        need(currentReads<oldReads,"paired lookup did not reduce world reads");
        System.out.println("space-door-cpu paired-state PASS cases="+cases+" baseline-state-reads="+oldReads+" current-state-reads="+currentReads);
    }
    private static boolean oldBeside(World world,BlockPos lower,EnumFacing side,EnumFacing facing){
        BlockPos at=lower.offset(side);if(!world.isBlockLoaded(at)||!world.isBlockLoaded(at.up()))return false;
        IBlockState state=world.getBlockState(at);
        return state.getBlock() instanceof BlockConfigurableSpaceDoor&&state.getValue(BlockVandorDoor.HALF)==BlockDoor.EnumDoorHalf.LOWER&&state.getValue(BlockVandorDoor.FACING)==facing&&world.getBlockState(at.up()).getBlock()==state.getBlock();
    }
    private static SpaceDoorControlPanel.Side oldSide(World world,BlockPos pos,IBlockState state){
        TileEntity raw=world.getTileEntity(pos);if(raw instanceof TileEntitySpaceDoor&&!((TileEntitySpaceDoor)raw).hasPanel())return SpaceDoorControlPanel.Side.NONE;
        boolean left=state.getValue(BlockVandorDoor.HINGE)==BlockDoor.EnumHingePosition.RIGHT;
        boolean sliding=raw instanceof TileEntitySpaceDoor&&((TileEntitySpaceDoor)raw).isSliding();
        if(raw instanceof TileEntityLargeProgrammableDoor)return SpaceDoorControlPanel.side(left,sliding,true,false,false);
        EnumFacing front=state.getValue(BlockVandorDoor.FACING);
        return SpaceDoorControlPanel.side(left,sliding,state.getValue(BlockConnectingDetailedDoor.PAIRED),oldBeside(world,pos,front.rotateY(),front),oldBeside(world,pos,front.rotateYCCW(),front));
    }
    private static void neighbor(NonRenderingChecks.MemoryWorld world,BlockConfigurableSpaceDoor block,BlockPos pos,EnumFacing face,int mode){
        if(mode==0)return;world.states.put(pos,lower(block,mode==3?face.getOpposite():face));
        if(mode!=2)world.states.put(pos.up(),upper(block,BlockDoor.EnumHingePosition.LEFT));
    }
    private static void panelSides(){
        BlockConfigurableSpaceDoor block=door();NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);
        int cases=0;long oldReads=0,currentReads=0;
        for(EnumFacing face:EnumFacing.HORIZONTALS)for(boolean sliding:new boolean[]{false,true})for(boolean paired:new boolean[]{false,true})for(BlockDoor.EnumHingePosition hinge:BlockDoor.EnumHingePosition.values())for(int left=0;left<4;left++)for(int right=0;right<4;right++)for(boolean panel:new boolean[]{false,true}){
            world.clear();TileEntitySpaceDoor tile=new TileEntitySpaceDoor();tile.configure(2,1,true,0,true,sliding,true,0,panel);
            IBlockState state=lower(block,face).withProperty(BlockVandorDoor.HINGE,hinge).withProperty(BlockConnectingDetailedDoor.PAIRED,paired);attach(world,tile,POS,state);
            neighbor(world,block,POS.offset(face.rotateY()),face,left);neighbor(world,block,POS.offset(face.rotateYCCW()),face,right);
            world.stateReads=0;SpaceDoorControlPanel.Side expected=oldSide(world,POS,state);oldReads+=world.stateReads;
            world.stateReads=0;SpaceDoorControlPanel.Side actual=BlockConfigurableSpaceDoor.panelSide(world,POS,state);currentReads+=world.stateReads;
            need(expected==actual,"control pad appeared on a different jamb");cases++;
        }
        need(currentReads<oldReads,"pad adjacency did not reduce reads");
        System.out.println("space-door-cpu panel-side PASS cases="+cases+" baseline-state-reads="+oldReads+" current-state-reads="+currentReads);
    }
    private static boolean oldLower(TileEntitySpaceDoor tile){
        World world=tile.getWorld();if(world==null)return false;
        if(tile instanceof TileEntityLargeProgrammableDoor)return ((TileEntityLargeProgrammableDoor)tile).isAnchor()&&world.getBlockState(tile.getPos()).getBlock() instanceof BlockLargeProgrammableDoor;
        IBlockState state=world.getBlockState(tile.getPos());return state.getBlock() instanceof BlockVandorDoor&&state.getValue(BlockVandorDoor.HALF)==BlockDoor.EnumDoorHalf.LOWER;
    }
    private static boolean oldEligible(TileEntitySpaceDoor tile,int pass){
        return pass==1&&tile.getWorld()!=null&&RenderFluidPresence.wet(tile)||oldLower(tile)&&(pass==0||pass==1&&(tile.hasCompanyGlass()||tile.hasGlass()&&!tile.opaqueGlass()));
    }
    private static void eligibility(){
        BlockConfigurableSpaceDoor block=door();NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);int cases=0;long oldReads=0,currentReads=0;
        int company=-1,reactor=-1;for(int choice=0;choice<ScreenHousingTextures.BUILTIN_COUNT;choice++){if(ScreenHousingTextures.companyLight(choice))company=choice;if(ScreenHousingTextures.reactorArtwork(choice))reactor=choice;}
        need(company>=0&&reactor>=0,"door artwork fixture missing");
        for(int meta=0;meta<16;meta++)for(int design:new int[]{0,2,5,6,11,15,16,29})for(int face:new int[]{-1,company,reactor})for(int material=0;material<3;material++)for(boolean wet:new boolean[]{false,true}){
            TileEntitySpaceDoor tile=new TileEntitySpaceDoor();tile.configure(design,1,true);tile.setFaceTexture(face);
            if(material!=0)tile.setGlassAppearance(new DoorGlassAppearance(0,0,material==1?1:3,1));
            NBTTagCompound fluids=new NBTTagCompound();fluids.setBoolean("Waterlogged",wet);tile.fluids.read(fluids);
            attach(world,tile,POS,block.getStateFromMeta(meta));tile.getBlockType();tile.getBlockMetadata();RenderFluidPresence.wet(tile);
            for(int pass=-1;pass<3;pass++){
                world.stateReads=0;boolean expected=oldEligible(tile,pass);oldReads+=world.stateReads;
                world.stateReads=0;boolean actual=tile.shouldRenderInPass(pass);currentReads+=world.stateReads;
                need(expected==actual,"regular door pass eligibility changed");cases++;
            }
        }
        need(currentReads==0&&oldReads>0,"warmed regular eligibility still reads native block state");
        TileEntitySpaceDoor tile=new TileEntitySpaceDoor();attach(world,tile,POS,lower(block,EnumFacing.SOUTH));need(tile.shouldRenderInPass(0),"lower absent");
        world.states.put(POS,upper(block,BlockDoor.EnumHingePosition.RIGHT));tile.updateContainingBlockInfo();need(!tile.shouldRenderInPass(0),"native metadata invalidation lost upper half");
        world.states.put(POS,Blocks.AIR.getDefaultState());tile.updateContainingBlockInfo();need(!tile.shouldRenderInPass(0),"removed door retained eligibility");
        TileEntitySpaceDoor custom=new TileEntitySpaceDoor(){@Override protected boolean isLowerDoor(){return true;}};attach(world,custom,POS,Blocks.AIR.getDefaultState());need(custom.shouldRenderInPass(0),"custom tile root override bypassed");
        BlockConfigurableSpaceDoor customBlock=new BlockConfigurableSpaceDoor("cpu_custom_door",true,mate()){@Override public int getMetaFromState(IBlockState s){return 8;}};
        attach(world,tile,POS,lower(customBlock,EnumFacing.NORTH));need(tile.shouldRenderInPass(0),"custom block metadata was treated as native");
        BlockLargeProgrammableDoor large=new BlockLargeProgrammableDoor("cpu_large_door",mate());
        for(int meta=0;meta<16;meta++)for(boolean anchor:new boolean[]{false,true}){
            TileEntityLargeProgrammableDoor big=new TileEntityLargeProgrammableDoor();attach(world,big,POS,large.getStateFromMeta(meta));big.assign(anchor?POS:POS.down());
            need(big.shouldRenderInPass(0)==oldEligible(big,0),"large powered bit was mistaken for upper-half bit");
        }
        VehicleWorld view=new VehicleWorld(world,VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(POS,lower(block,EnumFacing.NORTH),null))));
        tile=new TileEntitySpaceDoor();tile.setWorld(view);tile.setPos(POS);tile.getBlockType();tile.getBlockMetadata();need(tile.shouldRenderInPass(0),"carried lower absent");
        view.update(VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(POS,upper(block,BlockDoor.EnumHingePosition.LEFT),null))));
        need(!tile.shouldRenderInPass(0),"vehicle snapshot edit reused cached native metadata");
        System.out.println("space-door-cpu eligibility PASS cases="+cases+" baseline-state-reads="+oldReads+" current-state-reads="+currentReads+" fluids/artwork/large roots/native invalidation/subclasses/vehicle snapshots");
    }
    private static final class Sprite extends TextureAtlasSprite {
        long reads;Sprite(String name,int offset){super(name);setIconWidth(16);setIconHeight(16);initSprite(64,64,offset,offset,false);}
        @Override public float getInterpolatedU(double value){reads++;return super.getInterpolatedU(value);}
        @Override public float getInterpolatedV(double value){reads++;return super.getInterpolatedV(value);}
    }
    private static int[] packed(BufferBuilder buffer){buffer.finishDrawing();int[] data=new int[buffer.getVertexCount()*buffer.getVertexFormat().getIntegerSize()];for(int i=0;i<data.length;i++)data[i]=buffer.getByteBuffer().getInt(i*4);return data;}
    private static void geometry(){
        Sprite buttons=new Sprite("buttons",0),wall=new Sprite("wall",16);Function<String,TextureAtlasSprite> atlas=name->name.endsWith("control_buttons")?buttons:wall;
        DoorControlPanelMeshes.clear();int cases=0;
        VertexFormat normal=new VertexFormat(DefaultVertexFormats.BLOCK).addElement(DefaultVertexFormats.NORMAL_3B).addElement(DefaultVertexFormats.PADDING_1B);
        VertexFormat extended=new VertexFormat(normal).addElement(new VertexFormatElement(0,VertexFormatElement.EnumType.FLOAT,VertexFormatElement.EnumUsage.GENERIC,4));
        for(boolean sliding:new boolean[]{false,true})for(boolean far:new boolean[]{false,true})for(SpaceDoorControlPanel.Side side:SpaceDoorControlPanel.Side.values())for(VertexFormat format:new VertexFormat[]{normal,extended})for(int light:new int[]{0,0x005000a0,0x00f000f0}){
            OpenGlHelper.lastBrightnessX=light&65535;OpenGlHelper.lastBrightnessY=light>>>16;
            BufferBuilder original=new BufferBuilder(2048),current=new BufferBuilder(2048);original.begin(7,format);current.begin(7,format);
            TESlidingDoor.panelGeometry(original,sliding,far,side,buttons,wall);
            StaticSurfaceMesh mesh=DoorControlPanelMeshes.get(sliding,far,side,atlas);mesh.draw(current,light>>>16,light&65535);
            need(Arrays.equals(packed(original),packed(current)),"cached pad changed position/UV/normal/color/lightmap/extended record");
            need(mesh==DoorControlPanelMeshes.get(sliding,far,side,atlas),"pad variant did not reuse geometry");cases++;
        }
        StaticSurfaceMesh old=DoorControlPanelMeshes.get(false,false,SpaceDoorControlPanel.Side.LEFT,atlas);DoorControlPanelMeshes.clear();
        Sprite shifted=new Sprite("buttons",32);StaticSurfaceMesh next=DoorControlPanelMeshes.get(false,false,SpaceDoorControlPanel.Side.LEFT,name->name.endsWith("control_buttons")?shifted:wall);
        need(old!=next&&!old.sameGeometry(next),"atlas reload retained retired pad UVs");DoorControlPanelMeshes.clear();
        System.out.println("space-door-cpu pad geometry PASS cases="+cases+" exact complete vertex records, both sides/depths/motions, dynamic light and format, atlas reload; cache bound=8x24 vertices");
        benchmarkPad(buttons,wall,atlas,normal);
    }
    private static void benchmarkPad(Sprite buttons,Sprite wall,Function<String,TextureAtlasSprite> atlas,VertexFormat format){
        final int calls=4096;BufferBuilder buffer=new BufferBuilder(calls*24*format.getNextOffset());
        long[][] cpu=new long[2][15],alloc=new long[2][15];long[] uv=new long[2];
        for(int batch=-20;batch<15;batch++)for(int order=0;order<2;order++){
            int mode=(batch+order)&1;buffer.reset();buffer.begin(7,format);long reads=buttons.reads+wall.reads;
            long memory=METER.getThreadAllocatedBytes(Thread.currentThread().getId()),start=METER.getCurrentThreadCpuTime();
            for(int i=0;i<calls;i++){
                boolean sliding=(i&1)!=0,far=(i&2)!=0;SpaceDoorControlPanel.Side side=(i&4)==0?SpaceDoorControlPanel.Side.LEFT:SpaceDoorControlPanel.Side.RIGHT;
                if(mode==0)TESlidingDoor.panelGeometry(buffer,sliding,far,side,buttons,wall);
                else DoorControlPanelMeshes.get(sliding,far,side,atlas).draw(buffer,(int)OpenGlHelper.lastBrightnessY,(int)OpenGlHelper.lastBrightnessX);
            }
            long elapsed=METER.getCurrentThreadCpuTime()-start,used=METER.getThreadAllocatedBytes(Thread.currentThread().getId())-memory;
            if(batch>=0){cpu[mode][batch]=elapsed;alloc[mode][batch]=used;uv[mode]=buttons.reads+wall.reads-reads;}
            consumed=buffer;buffer.finishDrawing();
        }
        need(uv[0]>0&&uv[1]==0,"warm pad lookup still interpolates atlas UVs");
        for(int mode=0;mode<2;mode++){Arrays.sort(cpu[mode]);Arrays.sort(alloc[mode]);System.out.println("space-door-cpu pad benchmark "+(mode==0?"baseline":"current")+" calls="+calls+" median-thread-ns="+cpu[mode][7]+" allocated-bytes="+alloc[mode][7]+" atlas-UV-lookups="+uv[mode]);}
    }
    private static void bounds(){
        TileEntitySpaceDoor tile=new TileEntitySpaceDoor();int cases=0;
        for(BlockPos pos:new BlockPos[]{POS,new BlockPos(-17,255,-12186)})for(int direction=0;direction<3;direction++){
            tile.setPos(pos);tile.configure(2,1,true,direction);
            AxisAlignedBB expected=direction==0?new AxisAlignedBB(pos.add(-1,0,-1),pos.add(2,2,2)):new AxisAlignedBB(pos.add(-1,-2,-1),pos.add(2,4,2));
            AxisAlignedBB actual=tile.getRenderBoundingBox();need(expected.equals(actual),"door culling bounds changed");need(actual==tile.getRenderBoundingBox(),"bounds not reused");cases++;
        }
        long start=METER.getThreadAllocatedBytes(Thread.currentThread().getId());for(int i=0;i<65536;i++)consumed=tile.getRenderBoundingBox();long allocated=METER.getThreadAllocatedBytes(Thread.currentThread().getId())-start;
        need(allocated<4096,"stationary bounds allocate on every camera/shadow check");
        System.out.println("space-door-cpu bounds PASS cases="+cases+" warmed-calls=65536 allocated-bytes="+allocated);
    }
    public static void main(String[] args){
        net.minecraft.init.Bootstrap.register();com.vandorlabs.VandorLabs.proxy=new com.vandorlabs.CommonProxy();METER.setThreadAllocatedMemoryEnabled(true);METER.setThreadCpuTimeEnabled(true);
        pairing();panelSides();eligibility();geometry();bounds();
        System.out.println("space-door-cpu PASS Java read/allocation/geometry comparisons; not hardware shader-pack FPS acceptance");
    }
}
