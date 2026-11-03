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
    private static void largePadScans(WorldServer world,EntityPlayerMP actor){
        BlockPos base=new BlockPos(600,110,180);Block block=Block.getBlockFromName("vandorlabs:turntable_surface");
        for(int size:new int[]{16,32}){
            for(int x=-1;x<=size;x++)for(int z=-1;z<=size;z++)world.getChunkFromBlockCoords(base.add(x,0,z));
            for(int x=0;x<size;x++)for(int z=0;z<size;z++)world.setBlockState(base.add(x,0,z),block.getDefaultState().withProperty(BlockTurntableSurface.FACING,EnumFacing.UP),2);
            long[] samples=new long[15];for(int run=0;run<samples.length;run++){long start=System.nanoTime();SquareLayout layout=SquareLayout.scan(world,base);samples[run]=System.nanoTime()-start;require(layout.size==size&&layout.cells.size()==size*size,"large live pad scan");}
            java.util.Arrays.sort(samples);System.out.println("[vandorlabs][reprolab] surface-size-scan size="+size+" cells="+(size*size)+" median-us="+(samples[7]/1000));
            if(size==32){
                TurntableSurfaceLayout layout=TurntableSurfaceLayout.scan(world,base);
                for(int i=0;i<=TurntableSnapshot.MAX_BLOCKS;i++)world.setBlockState(layout.cell(i%32,(i/32)%32,i/1024+1),Blocks.STONE.getDefaultState(),2);
                boolean refused=false;try{TurntableSnapshot.capture(world,layout,5,actor);}catch(IllegalArgumentException invalid){refused=invalid.getMessage().contains("4096");}catch(java.io.IOException failure){throw new IllegalStateException(failure);}
                require(refused&&world.getBlockState(layout.cell(0,0,5)).getBlock()==Blocks.STONE,"large pad cannot publish an unreadable oversized turntable snapshot");
                for(int i=0;i<=TurntableSnapshot.MAX_BLOCKS;i++)world.setBlockToAir(layout.cell(i%32,(i/32)%32,i/1024+1));
            }
            for(int x=0;x<size;x++)for(int z=0;z<size;z++)world.setBlockToAir(base.add(x,0,z));
        }
    }
    private static void require(boolean value,String why){if(!value)throw new IllegalStateException("Programmable Surface: "+why);}
    public static void run(WorldServer world,EntityPlayerMP actor){
        for(EnumFacing normal:EnumFacing.values())for(int size:new int[]{17,32,33}){java.util.List<BlockPos> cells=new java.util.ArrayList<>();TurntableGeometry.Face basis=TurntableGeometry.Face.valueOf(normal.name());for(int u=0;u<size;u++)for(int v=0;v<size;v++){double[] p=basis.point(u,v,0);cells.add(new BlockPos(p[0],p[1],p[2]));}boolean refused=false;try{SquareLayout layout=SquareLayout.of(cells,normal);require(size<=32&&SquareLayout.read(layout.write()).size==size,"large pad save round trip");}catch(IllegalArgumentException expected){refused=true;}require(refused==(size==33),"32-block pad bound on every face");}

        for(EnumFacing normal:EnumFacing.values())for(int[] dimensions:new int[][]{{1,32},{32,1},{2,3},{3,2},{32,32}}){java.util.List<BlockPos> cells=new java.util.ArrayList<>();TurntableGeometry.Face basis=TurntableGeometry.Face.valueOf(normal.name());for(int u=0;u<dimensions[0];u++)for(int v=0;v<dimensions[1];v++){double[] at=basis.point(u,v,0);cells.add(new BlockPos(at[0],at[1],at[2]));}SquareLayout area=SquareLayout.rectangle(cells,normal),reload=SquareLayout.readRectangle(area.write());require(reload.width==dimensions[0]&&reload.height==dimensions[1],"rectangle layout reload");boolean refused=false;try{SquareLayout.read(area.write());}catch(IllegalArgumentException invalid){refused=true;}require(refused==(dimensions[0]!=dimensions[1]),"turntable square reader remains strict");}
        largePadScans(world,actor);
        BlockPos base=new BlockPos(540,110,180);Block surface=Block.getBlockFromName("vandorlabs:turntable_surface");int cases=0;net.minecraft.item.ItemStack oldHeld=actor.getHeldItemMainhand();double oldX=actor.posX,oldY=actor.posY,oldZ=actor.posZ;float oldYaw=actor.rotationYaw,oldPitch=actor.rotationPitch;
        for(String texture:TurntableTextures.IDS)for(int mode=0;mode<3;mode++){TileEntityTurntableSurface fresh=new TileEntityTurntableSurface();fresh.surfaceOn=texture;fresh.textureLayout=mode;NBTTagCompound original=fresh.writeToNBT(new NBTTagCompound());TileEntityTurntableSurface restored=new TileEntityTurntableSurface();restored.readFromNBT(original.copy());require(original.equals(restored.writeToNBT(new NBTTagCompound())),"exact fresh surface NBT round trip "+texture+mode);}
        TileEntityTurntableSurface defaults=new TileEntityTurntableSurface();require(defaults.textureLayout==2,"fresh surface defaults to Fit");defaults.readFromNBT(new NBTTagCompound());require(defaults.textureLayout==2,"missing saved mapping defaults to Fit");
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
            actor.closeScreen();actor.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,new net.minecraft.item.ItemStack(com.vandorlabs.items.ModItems.CONFIGURIZER));actor.connection.setPlayerLocation(base.getX()+.5,base.getY()+1,base.getZ()+.5,0,90);
            require(com.vandorlabs.items.ProgrammableTarget.settingsPos(world,base).equals(base),"claimed pad keeps surface texture target");
            net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock click=new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock(actor,net.minecraft.util.EnumHand.MAIN_HAND,base,EnumFacing.UP,new net.minecraft.util.math.Vec3d(base).addVector(.5,1,.5));
            com.vandorlabs.items.ItemConfigurizer.onRightClickBlock(click);require(click.isCanceled()&&actor.openContainer instanceof com.vandorlabs.container.ContainerSurface&&((com.vandorlabs.container.ContainerSurface)actor.openContainer).tile==selected&&actor.openContainer.canInteractWith(actor),"real Configurizer opens claimed pad artwork dialog");actor.closeScreen();
            TileEntityTurntableSurface loaded=new TileEntityTurntableSurface();loaded.readFromNBT(selected.writeToNBT(new NBTTagCompound()));require(loaded.textureLayout==2&&loaded.surfaceOff.equals("copier-output")&&selected.writeToNBT(new NBTTagCompound()).equals(loaded.writeToNBT(new NBTTagCompound())),"surface save/load appearance");
            io.netty.buffer.ByteBuf bytes=io.netty.buffer.Unpooled.buffer();try{com.vandorlabs.network.MessageProgrammableSurface packet=new com.vandorlabs.network.MessageProgrammableSurface(4,"copier-input","landing",1);packet.toBytes(bytes);new com.vandorlabs.network.MessageProgrammableSurface().fromBytes(bytes);require(!bytes.isReadable(),"surface settings exact round trip");}finally{bytes.release();}
            bytes=io.netty.buffer.Unpooled.buffer();try{bytes.writeInt(4);bytes.writeByte(1);bytes.writeShort(257);bytes.writeZero(257);boolean refused=false;try{new com.vandorlabs.network.MessageProgrammableSurface().fromBytes(bytes);}catch(IllegalArgumentException expected){refused=true;}require(refused,"texture length bounded before allocation");}finally{bytes.release();}
        }finally{actor.closeScreen();actor.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,oldHeld);actor.connection.setPlayerLocation(oldX,oldY,oldZ,oldYaw,oldPitch);clear(world,base);}
        System.out.println("[vandorlabs] programmable-surface PASS axes="+cases+" square/seam discovery surface-owned appearance legacy DeferredTileLoad migration no-overlap claims NBT bounded packet round trip");
    }
    private static void clear(WorldServer world,BlockPos base){for(BlockPos p:BlockPos.getAllInBox(base.add(-5,-5,-5),base.add(5,5,5)))world.setBlockToAir(p);}
    private SurfaceRuntimeChecks(){}
}
