#!/usr/bin/env python3
"""Measure private installed frame lifecycle and real codec samples across journal/restart."""
import os
import sys
from test_ramp_assembly_journal import main

if __name__ == '__main__':
    os.environ['VANDOR_RAMP_FRAME_COST']='true'
    sys.argv=[sys.argv[0],'--case','frame-cost']
    main()
