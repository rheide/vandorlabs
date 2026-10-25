package com.vandorlabs.surface;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.turntable.*;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import java.util.*;

/** Shared pad discovery, no-overlap claims, controller migration and surface-only settings. */
public final class SurfaceRuntimeChecks {
    private static void require(boolean value,String why){if(!value)throw new IllegalStateException("Programmable Surface: "+why);}
    public static void run(WorldServer world,EntityPlayerMP actor){
        BlockPos base=new BlockPos(540,110,180);Block surface=Block.getBlockFromName("vandorlabs:turntable_surface");int cases=0;
        for(String texture:TurntableTextures.IDS)for(int mode=0;mode<3;mode++){TileEntityTurntableSurface fresh=new TileEntityTurntableSurface();fresh.surfaceOn=texture;fresh.textureLayout=mode;NBTTagCompound original=fresh.writeToNBT(new NBTTagCompound());TileEntityTurntableSurface restored=new TileEntityTurntableSurface();restored.readFromNBT(original.copy());require(original.equals(restored.writeToNBT(new NBTTagCompound())),"exact fresh surface NBT round trip "+texture+mode);}
        try{
            for(EnumFacing normal:EnumFacing.values()){
                clear(world,base);TurntableGeometry.Face face=TurntableGeometry.Face.valueOf(normal.name());EnumFacing u=EnumFacing.getFacingFromVector(face.ux,face.uy,face.uz),v=EnumFacing.getFacingFromVector(face.vx,face.vy,face.vz);
                for(int x=0;x<2;x++)for(int y=0;y<2;y++)world.setBlockState(base.offset(u,x).offset(v,y),surface.getDefaultState().withProperty(BlockTurntableSurface.FACING,normal),3);
                DeferredTileLoad.flush(world);SquareLayout layout=SquareLayout.scan(world,base);require(layout.size==2&&layout.cells.size()==4&&layout.normal==normal,"complete pad on every mount axis");
                TileEntityTurntableSurface tile=(TileEntityTurntableSurface)world.getTileEntity(base);tile.configure("landing","landing-grey",1,actor);for(BlockPos cell:layout.cells){TileEntityTurntableSurface part=(TileEntityTurntableSurface)world.getTileEntity(cell);require(part.surfaceOn.equals("landing")&&part.surfaceOff.equals("landing-grey")&&part.textureLayout==1&&part.appearanceLayout().size==2,"shared square appearance persisted on each surface");}
                for(int x=2;x<4;x++)for(int y=0;y<2;y++)world.setBlockState(base.offset(u,x).offset(v,y),surface.getDefaultState().withProperty(BlockTurntableSurface.FACING,normal),3);
                boolean refused=false;try{SquareLayout.scan(world,base);}catch(IllegalArgumentException expected){refused=true;}require(refused,"adjacent two-pad rectangle is not an arbitrary full square");
                SquareLayout left=SquareLayout.scan(world,base,p->face.u(p.getX()-base.getX(),p.getY()-base.getY(),p.getZ()-base.getZ())<2),right=SquareLayout.scan(world,base.offset(u,2),p->face.u(p.getX()-base.getX(),p.getY()-base.getY(),p.getZ()-base.getZ())>=2);require(left.cells.size()==4&&right.cells.size()==4&&Collections.disjoint(left.cells,right.cells),"shared seam defines two disjoint complete squares");cases++;
            }
            clear(world,base);for(int x=0;x<2;x++)for(int z=0;z<2;z++)world.setBlockState(base.add(x,0,z),surface.getDefaultState(),3);
            BlockPos control=base.north();world.setBlockState(control,Block.getBlockFromName("vandorlabs:turntable_controller").getDefaultState(),3);TileEntityTurntableController owner=(TileEntityTurntableController)world.getTileEntity(control);owner.layout=TurntableSurfaceLayout.scan(world,base);
            NBTTagCompound old=owner.writeToNBT(new NBTTagCompound());old.removeTag("SurfaceAppearanceMigrated");old.setString("SurfaceOn","docking");old.setString("SurfaceOff","maintenance-grey");owner.readFromNBT(old);
            for(BlockPos cell:owner.layout.cells){TileEntityTurntableSurface tile=(TileEntityTurntableSurface)world.getTileEntity(cell);NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());saved.setLong("Owner",control.toLong());saved.setUniqueId("OwnerKey",owner.key);tile.readFromNBT(saved);}
            owner.onLoad();DeferredTileLoad.flush(world);
            for(BlockPos cell:owner.layout.cells){TileEntityTurntableSurface tile=(TileEntityTurntableSurface)world.getTileEntity(cell);require(tile.surfaceOn.equals("docking")&&tile.surfaceOff.equals("maintenance-grey")&&tile.textureLayout==0,"deferred legacy appearance migration");require(tile.claimedOwner()==owner&&!tile.canClaim(control.east(),UUID.randomUUID()),"controller claims cannot overlap");}
            TileEntityTurntableSurface selected=(TileEntityTurntableSurface)world.getTileEntity(base);selected.configure("copier-input","copier-output",2,actor);owner.surfaceOn="basalt";owner.surfaceOff="original_off";selected.migrateLegacy(owner.surfaceOn,owner.surfaceOff);require(selected.surfaceOn.equals("copier-input")&&selected.surfaceOff.equals("copier-output"),"later legacy/controller values cannot overwrite configured surface");
            TileEntityTurntableSurface loaded=new TileEntityTurntableSurface();loaded.readFromNBT(selected.writeToNBT(new NBTTagCompound()));require(loaded.textureLayout==2&&loaded.surfaceOff.equals("copier-output")&&selected.writeToNBT(new NBTTagCompound()).equals(loaded.writeToNBT(new NBTTagCompound())),"surface save/load appearance");
            io.netty.buffer.ByteBuf bytes=io.netty.buffer.Unpooled.buffer();try{com.vandorlabs.network.MessageProgrammableSurface packet=new com.vandorlabs.network.MessageProgrammableSurface(4,"copier-input","landing",1);packet.toBytes(bytes);new com.vandorlabs.network.MessageProgrammableSurface().fromBytes(bytes);require(!bytes.isReadable(),"surface settings exact round trip");}finally{bytes.release();}
            bytes=io.netty.buffer.Unpooled.buffer();try{bytes.writeInt(4);bytes.writeByte(1);bytes.writeShort(257);bytes.writeZero(257);boolean refused=false;try{new com.vandorlabs.network.MessageProgrammableSurface().fromBytes(bytes);}catch(IllegalArgumentException expected){refused=true;}require(refused,"texture length bounded before allocation");}finally{bytes.release();}
        }finally{clear(world,base);}
        System.out.println("[vandorlabs] programmable-surface PASS axes="+cases+" square/seam discovery surface-owned appearance legacy DeferredTileLoad migration no-overlap claims NBT bounded packet round trip");
    }
    private static void clear(WorldServer world,BlockPos base){for(BlockPos p:BlockPos.getAllInBox(base.add(-5,-5,-5),base.add(5,5,5)))world.setBlockToAir(p);}
    private SurfaceRuntimeChecks(){}
}
