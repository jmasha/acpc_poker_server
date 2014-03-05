#!/bin/bash
rm -rf deploy
mkdir deploy
mkdir deploy/GlassFrog
mkdir deploy/GlassFrog/logs
mkdir deploy/GlassFrog/output
mkdir deploy/GlassFrog/save

cp -R keys deploy/GlassFrog/.
cp -R scripts deploy/GlassFrog/.
cp -R tools deploy/GlassFrog/.
cp -R gamedef deploy/GlassFrog/.
cp -R config deploy/GlassFrog/.
cp -R xsd deploy/GlassFrog/.
cp -R dist/* deploy/GlassFrog/.

cd deploy/

tar -czf GlassFrog.tgz GlassFrog/
 
cd ..