#!/bin/bash
rm -rf deploy
mkdir deploy
mkdir deploy/GlassFrog
mkdir deploy/GlassFrog/logs
mkdir deploy/GlassFrog/output
mkdir deploy/GlassFrog/save
mkdir deploy/GlassFrog/scripts

mkdir deploy/GlassFrog/keys
cp keys/keys.template.xml deploy/GlassFrog/keys/.

mkdir deploy/GlassFrog/config
cp config/CONFIG.SAMPLE.xml deploy/GlassFrog/config/.

mkdir deploy/GlassFrog/gamedef
cp gamedef/2Player.limit.gamedef.xml deploy/GlassFrog/gamedef/.
cp gamedef/2Player.nolimit.gamedef.xml deploy/GlassFrog/gamedef/.
#cp gamedef/3Player.limit.gamedef.xml deploy/GlassFrog/gamedef/.

cp -R tools deploy/GlassFrog/.
cp -R xsd deploy/GlassFrog/.
cp -R src deploy/GlassFrog/.
cp -R build deploy/GlassFrog/.
cp -R nbproject deploy/GlassFrog/.

cp build.xml deploy/GlassFrog/.
cp manifest.mf deploy/GlassFrog/.
cp README deploy/GlassFrog/.

cd deploy/

find . -name ".svn*" -exec rm -rf '{}' \;

tar -czf GlassFrog.tgz GlassFrog/
 
cd ..