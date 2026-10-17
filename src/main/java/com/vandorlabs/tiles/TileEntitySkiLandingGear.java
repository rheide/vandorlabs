package com.vandorlabs.tiles;

import com.vandorlabs.blocks.BlockNonWheeledLandingGear;

/** A vertical runner column shares the lowest runner's extension controls. */
public final class TileEntitySkiLandingGear extends TileEntityLandingGear {
    private boolean knownRoot,lastRoot;
    @Override public void onLoad(){lastRoot=isRoot();knownRoot=true;super.onLoad();}
    @Override public void update(){
        boolean active=isRoot();
        if(world!=null && !world.isRemote && knownRoot && active!=lastRoot){if(active)placed();else com.vandorlabs.redstone.RedstoneChannels.unregister(this);}
        lastRoot=active;knownRoot=true;if(!active && owner()==null)progress=previous=0;
        super.update();
    }
    @Override public boolean isRoot(){return super.isRoot() && (!(getBlockType() instanceof BlockNonWheeledLandingGear) || ((BlockNonWheeledLandingGear)getBlockType()).connectedRoot(world,pos).equals(pos));}
}
