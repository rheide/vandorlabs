#!/usr/bin/env python3
"""Operate carried Redstone Screen rows with a connected pilot and check native parking."""
import argparse
from connected_player_fixture import run_connected_player
from native_test_artifacts import retain_artifacts


@retain_artifacts
def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--xvfb', action='store_true')
    args = parser.parse_args()
    output = run_connected_player('vehicle-screen-rows', args.xvfb, ['vandorlabs.vehicleScreenRowsChecks=true'])
    print('Native carried Redstone Screen controls passed: ' + str(output), flush=True)


if __name__ == '__main__':
    main()
