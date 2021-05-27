#!/usr/bin/env bash

cp -- /tmp/src/conf/ocp/logback.xml "$CONF_DIR/logback.xml"
cp -- /tmp/src/conf/labsapi*.yaml "$CONF_DIR/"
 
ln -s -- "$TOMCAT_APPS/labsapi.xml" "$DEPLOYMENT_DESC_DIR/labsapi.xml"
