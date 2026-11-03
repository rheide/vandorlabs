package com.vandorlabs.blocks;

import com.vandorlabs.tiles.*;
import com.vandorlabs.render.ScreenSurface;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Physical chip ownership and one frame shared by rendering and player rays. */
public final class ScreenChipSupport {
    private ScreenChipSupport(){}
    private static final ScreenSurface.Quad DECK=ScreenSurface.inset(ScreenSurface.input(com.vandorlabs.render.InputSurfaceLayout.halfConsoleFront()),.40,0,0,0);
    public static ScreenSurface.Quad deck(){return DECK;}
    public static boolean integrated(Block block){return block instanceof BlockDiagonalHalfConsole||block instanceof BlockProgrammableHalfConsole||block instanceof BlockProgrammableConsole||block instanceof BlockProgrammableDiagonalScreen;}
    public static int displaySlot(Block block){return block instanceof BlockProgrammableHalfConsole?1:0;}
    public static boolean selected(TileEntityAnimatedScreenSelector tile){return integrated(tile.getBlockType())&&tile.baseSurfaceTexture(displaySlot(tile.getBlockType()))==TileEntityAnimatedScreenSelector.CHIP_SURFACE;}
    public static TileEntityTokenSlot attached(TileEntityAnimatedScreenSelector tile){
        if(tile.getWorld()==null||tile.getBlockType().getClass()!=BlockAnimatedScreenSelector.class)return null;
        for(EnumFacing face:EnumFacing.values()){
            BlockPos at=tile.getPos().offset(face);if(!tile.getWorld().isBlockLoaded(at))continue;
            IBlockState state=tile.getWorld().getBlockState(at);
            if(state.getBlock() instanceof BlockTokenSlot&&((BlockTokenSlot)state.getBlock()).chip&&state.getValue(BlockVandorSwitch.FACING)==face){net.minecraft.tileentity.TileEntity raw=tile.getWorld().getTileEntity(at);if(raw instanceof TileEntityTokenSlot)return (TileEntityTokenSlot)raw;}
        }return null;
    }
    public static boolean available(TileEntityAnimatedScreenSelector tile,int slot){return slot==displaySlot(tile.getBlockType())&&(integrated(tile.getBlockType())||groupReader(tile)!=null);}
    public static TileEntityTokenSlot reader(TileEntityAnimatedScreenSelector tile){return integrated(tile.getBlockType())?tile.ownedChipReader():groupReader(tile);}
    private static TileEntityTokenSlot groupReader(TileEntityAnimatedScreenSelector tile){if(tile.getBlockType().getClass()!=BlockAnimatedScreenSelector.class)return null;ScreenChipGroups.Group group=ScreenChipGroups.group(tile);return group==null?null:group.reader;}
    public static ItemStack stack(TileEntityAnimatedScreenSelector tile){TileEntityTokenSlot reader=reader(tile);return reader==null?ItemStack.EMPTY:reader.getStackInSlot(0);}
    /** Console openings face the player; the full Diagonal Screen retains its sideways opening. */
    public static MachineMountSurface localMount(IBlockState state,TileEntityAnimatedScreenSelector tile){
        Block block=state.getBlock();Vec3d center,normal;
        if(block instanceof BlockDiagonalHalfConsole){boolean upper=state.getValue(BlockDiagonalHalfConsole.UPPER);center=new Vec3d(.5,upper?.937:.063,.25);normal=new Vec3d(0,upper?-1:1,0);}
        else if(block instanceof BlockProgrammableHalfConsole||block instanceof BlockProgrammableConsole){center=new Vec3d(.22,.068,.24+(tile.isCeilingMounted()?com.vandorlabs.render.InputSurfaceLayout.ceilingStart(16,tile.getCeilingPosition(1))/16:0));normal=new Vec3d(0,1,0);}
        else{boolean upper=state.getValue(BlockProgrammableDiagonalScreen.INVERTED);center=new Vec3d(.5,upper?.86:.14,.14);normal=new Vec3d(0,upper?-1:1,-1).normalize();}
        Vec3d v=block instanceof BlockProgrammableDiagonalScreen?new Vec3d(1,0,0):new Vec3d(0,0,-1),u=normal.crossProduct(v);return new MachineMountSurface(center,u,v,normal,.34,.34);
    }
    private static final java.util.Map<IBlockState,MachineMountSurface[]> FRAMES=new java.util.HashMap<>();
    public static synchronized MachineMountSurface mount(IBlockState state,TileEntityAnimatedScreenSelector tile){int slot=state.getBlock() instanceof BlockProgrammableHalfConsole&&tile.isCeilingMounted()?tile.getCeilingPosition(1):1;MachineMountSurface[] frames=FRAMES.computeIfAbsent(state,key->new MachineMountSurface[3]);if(frames[slot]==null){MachineMountSurface local=localMount(state,tile);EnumFacing facing=RedstoneScreenInteractions.facing(state);frames[slot]=new MachineMountSurface(rotate(local.center,facing),direction(local.u,facing),direction(local.v,facing),direction(local.normal,facing),local.width,local.height);}return frames[slot];}
    private static Vec3d direction(Vec3d v,EnumFacing f){return rotate(v.addVector(.5,.5,.5),f).subtract(.5,.5,.5);}
    private static Vec3d rotate(Vec3d p,EnumFacing f){double x=p.x-.5,y=p.y-.5,z=p.z-.5;switch(f){case EAST:return new Vec3d(.5-z,p.y,.5+x);case SOUTH:return new Vec3d(.5-x,p.y,.5-z);case WEST:return new Vec3d(.5+z,p.y,.5-x);case UP:return new Vec3d(.5-x,.5-z,.5-y);case DOWN:return new Vec3d(p.x,.5+z,.5-y);default:return p;}}
    public static AxisAlignedBB tray(IBlockState state){boolean upper=state.getValue(BlockDiagonalHalfConsole.UPPER);return PanelPlacement.rotateFromNorth(new AxisAlignedBB(0,upper?15D/16:0,0,1,upper?1:1D/16,.5),RedstoneScreenInteractions.facing(state));}
    public static AxisAlignedBB bounds(IBlockState state,TileEntityAnimatedScreenSelector tile){MachineMountSurface m=mount(state,tile);AxisAlignedBB bounds=null;for(int a:new int[]{-1,1})for(int b:new int[]{-1,1})for(int c:new int[]{0,1}){Vec3d p=m.center.add(m.u.scale(a*m.width/2)).add(m.v.scale(b*m.height/2)).add(m.normal.scale(c*.09));AxisAlignedBB point=new AxisAlignedBB(p.x,p.y,p.z,p.x,p.y,p.z);bounds=bounds==null?point:bounds.union(point);}return bounds;}
    public static ScreenSurface.Quad display(IBlockState state,TileEntityAnimatedScreenSelector tile,ScreenSurface.Quad full){return full;}
    public static boolean activate(TileEntityAnimatedScreenSelector tile,EntityPlayer player,EnumHand hand,Vec3d eye,Vec3d end){
        if(player.isSneaking()||player.getHeldItem(hand).getItem()==com.vandorlabs.items.ModItems.CONFIGURIZER)return false;
        World world=tile.getWorld();World physical=world instanceof com.vandorlabs.vehicle.VehicleWorld?((com.vandorlabs.vehicle.VehicleWorld)world).parent:world;
        if(!physical.isRemote&&!(world instanceof com.vandorlabs.vehicle.VehicleWorld)&&!com.vandorlabs.items.ConfigurationAccess.canEditDialog(player,world,tile.getPos()))return false;
        if(selected(tile)&&mount(world.getBlockState(tile.getPos()),tile).hit(eye,end)){
            if(physical.isRemote)return true;TileEntityTokenSlot reader=tile.ownedChipReader();ItemStack held=player.getHeldItem(hand);
            if(held.isEmpty()){ItemStack removed=reader.removeStackFromSlot(0);if(!removed.isEmpty()&&!player.inventory.addItemStackToInventory(removed))player.dropItem(removed,false);}
            else if(reader.accepts(held)&&reader.isEmpty()){ItemStack one=held.copy();one.setCount(1);reader.setInventorySlotContents(0,one);if(!player.capabilities.isCreativeMode)held.shrink(1);}
            else return false;
            player.inventory.markDirty();player.inventoryContainer.detectAndSendChanges();return true;
        }
        int slot=displaySlot(tile.getBlockType());if(!tile.hasChipSurface(slot))return false;
        int row=RedstoneScreenInteractions.hitRow(world.getBlockState(tile.getPos()),tile,slot,eye,end,12);double column=RedstoneScreenInteractions.hitColumn(world.getBlockState(tile.getPos()),tile,slot,eye,end);
        if(row<0||column<0||column>128)return false;
        TileEntityAnimatedScreenSelector display=tile;
        if(!integrated(tile.getBlockType())){ScreenChipGroups.Group group=ScreenChipGroups.group(tile);if(group==null)return false;display=group.owner;int col=(int)(DisplayJoinGeometry.basis(tile,world.getBlockState(tile.getPos())).cell(tile.getPos())>>32)-group.minColumn;if(world.getBlockState(tile.getPos()).getValue(BlockAnimatedScreenSelector.FACING)==EnumFacing.UP)col=group.columns-1-col;column=(group.columns-1-col+column/128)*128/group.columns;}
        com.vandorlabs.programmer.ProgrammerJournalView view=display.chipDisplay();if(view==null)return false;
        if(!physical.isRemote){display.chipPage=Math.max(0,Math.min(view.pages.size()-1,display.chipPage+(column<64?-1:1)));display.markDirty();if(world instanceof com.vandorlabs.vehicle.VehicleWorld){com.vandorlabs.vehicle.VehicleWorld carried=(com.vandorlabs.vehicle.VehicleWorld)world;if(carried.craft!=null)carried.craft.signals.changed(display.getPos());}else world.notifyBlockUpdate(display.getPos(),world.getBlockState(display.getPos()),world.getBlockState(display.getPos()),2);}return true;
    }
}
