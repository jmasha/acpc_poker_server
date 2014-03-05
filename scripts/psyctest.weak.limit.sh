#!/bin/sh
cd /home/jdavidso/trunk/src/c
./aaai_bot LIMIT 2 $1 $2 strategy_player.so strategies/limit/ir2.bstr
#cd ~/poker_dev/meikle_trunk/src/c
#./aaai_bot LIMIT 2 $1 $2 chump.so "0,1,1"