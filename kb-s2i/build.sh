#!/usr/bin/env bash

cd /tmp/src

cp -rp -- /tmp/src/target/labsapi-*.war "$TOMCAT_APPS/labsapi.war"
cp -- /tmp/src/conf/ocp/labsapi.xml "$TOMCAT_APPS/labsapi.xml"

export WAR_FILE=$(readlink -f "$TOMCAT_APPS/labsapi.war")
