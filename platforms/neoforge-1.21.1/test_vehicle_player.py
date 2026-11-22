#!/usr/bin/env python3
"""Exercise assembly, mounted input and parking with a connected Survival player."""
import argparse
from connected_player_fixture import run_connected_player
from native_test_artifacts import retain_artifacts


@retain_artifacts
def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--xvfb', action='store_true')
    args = parser.parse_args()
    out = run_connected_player('vehicle', args.xvfb, ['vandorlabs.vehiclePlayerChecks=true'])
    print('Native vehicle player round trip passed: ' + str(out), flush=True)


if __name__ == '__main__':
    main()
