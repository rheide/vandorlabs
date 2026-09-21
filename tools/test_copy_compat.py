#!/usr/bin/env python3
"""Pin the external copy semantics that Vandor Labs' live tests depend on."""

import argparse
import subprocess
from pathlib import Path


def javap(jar, *classes):
    result = subprocess.run(
        ["javap", "-classpath", str(jar), "-c", "-p", *classes],
        check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    return result.stdout


def require(text, needles, label):
    missing = [needle for needle in needles if needle not in text]
    if missing:
        raise AssertionError(f"{label} contract changed; missing {missing}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--mods", type=Path,
                        default=Path.home() / "MC-Forge12-2" / "mods")
    args = parser.parse_args()
    worldedit = args.mods / "worldedit-forge-mc1.12.2-6.1.10-dist.jar"
    bbw = args.mods / "BetterBuildersWands-1.12-0.11.1.245+69d0d70.jar"
    if not worldedit.is_file() or not bbw.is_file():
        print("SKIP: external WorldEdit/BetterBuildersWands test jars unavailable")
        return

    we = javap(worldedit,
               "com.sk89q.worldedit.forge.ForgeWorld",
               "com.sk89q.worldedit.forge.TileEntityBaseBlock",
               "com.sk89q.worldedit.forge.TileEntityUtils")
    require(we, [
        "Block.func_176201_c",       # getMetaFromState on copy
        "Block.func_176203_a",       # getStateFromMeta on paste
        "TileEntityBaseBlock",
        "TileEntity.func_189515_b",  # writeToNBT
        "BaseBlock.hasNbtData",
        "TileEntityUtils.setTileEntity",
        "String x", "String y", "String z",
    ], "WorldEdit 6.1.10")

    wand = javap(bbw,
                 "portablejim.bbw.core.WandWorker",
                 "portablejim.bbw.core.items.ItemBasicWand",
                 "portablejim.bbw.shims.BasicWorldShim")
    require(wand, [
        "IWorldShim.setBlock:(Lportablejim/bbw/basics/Point3d;Lnet/minecraft/block/state/IBlockState;)",
        "World.func_180501_a",       # raw setBlockState
        "String lastPlaced",
        "NBTTagCompound.func_74783_a",  # setIntArray
    ], "Better Builder's Wands 0.11.1")
    if "TileEntity" in wand:
        raise AssertionError("BBW placement unexpectedly gained tile-entity handling; review adapter")

    print("PASS: external copy-engine contracts (WorldEdit 6.1.10, BBW 0.11.1)")


if __name__ == "__main__":
    main()
