#!/bin/bash
script_flag=
tools_flag=
gamedef_flag=
config_flag=
xsd_flag=

while getopts 'stgcx' OPTION
  do
  case $OPTION in
      s) script_flag=1
	  ;;
      t) tools_flag=1
	  ;;
      g) gamedef_flag=1
	  ;;
      c) config_flag=1
	  ;;
      x) xsd_flag=1
	  ;;
  esac
done

rm -rf deploy
mkdir deploy
mkdir deploy/GlassFrog

if [ "$script_flag" ]
then
    cp -R scripts deploy/GlassFrog/.
    cp run.sh deploy/GlassFrog/.
    cp clean.sh deploy/GlassFrog/.
fi
if [ "$tools_flag" ]
then
    cp -R tools deploy/GlassFrog/.
fi
if [ "$gamedef_flag" ]
then
    cp -R gamedef deploy/GlassFrog/.
fi
if [ "$config_flag" ]
then
    cp -R config deploy/GlassFrog/.
fi
if [ "$xsd_flag" ]
then
    cp -R xsd deploy/GlassFrog/.
fi
cp -R dist/* deploy/GlassFrog/.
cd deploy/
find . -name ".svn*" -exec rm -rf '{}' \;
tar -czf GlassFrogPatch.tgz GlassFrog/
scp GlassFrogPatch.tgz 129.128.184.126:~/experiment/