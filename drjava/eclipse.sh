#!/bin/sh

# Directory where Eclipse is installed
ECLIPSE=$HOME/downloads/eclipse

# Version of core Eclipse plugins
ECLIPSE_VERSION=2.1.0

# Platform used by Eclipse's SWT plugin
#ECLIPSE_PLATFORM=carbon
ECLIPSE_PLATFORM=gtk

# OS and Architecture (directory)
#ECLIPSE_ARCH=macosx/ppc
ECLIPSE_ARCH=linux/x86

# Platform-specific path separator
SEP=:


export CLASSPATH=${CLASSPATH}${SEP}${ECLIPSE}/plugins/org.eclipse.core.runtime_${ECLIPSE_VERSION}/runtime.jar${SEP}${ECLIPSE}/plugins/org.eclipse.core.boot_${ECLIPSE_VERSION}/boot.jar${SEP}${ECLIPSE}/plugins/org.eclipse.core.resources_${ECLIPSE_VERSION}/resources.jar${SEP}${ECLIPSE}/plugins/org.eclipse.ui_${ECLIPSE_VERSION}/ui.jar${SEP}${ECLIPSE}/plugins/org.eclipse.ui.workbench_${ECLIPSE_VERSION}/workbench.jar${SEP}${ECLIPSE}/plugins/org.eclipse.debug.ui_${ECLIPSE_VERSION}/dtui.jar
export CLASSPATH=${CLASSPATH}${SEP}${ECLIPSE}/plugins/org.eclipse.swt.${ECLIPSE_PLATFORM}_${ECLIPSE_VERSION}/ws/${ECLIPSE_PLATFORM}/swt.jar${SEP}${ECLIPSE}/plugins/org.eclipse.swt.${ECLIPSE_PLATFORM}_${ECLIPSE_VERSION}/ws/${ECLIPSE_PLATFORM}/swt-pi.jar
export CLASSPATH=${CLASSPATH}${SEP}${ECLIPSE}/plugins/org.eclipse.jdt.ui_${ECLIPSE_VERSION}/jdt.jar${SEP}${ECLIPSE}/plugins/org.eclipse.search_${ECLIPSE_VERSION}/search.jar${SEP}${ECLIPSE}/plugins/org.eclipse.jface_${ECLIPSE_VERSION}/jface.jar
export CLASSPATH=${CLASSPATH}${SEP}plugins/eclipse/bin
export LD_LIBRARY_PATH=${LD_LIBRARY_PATH}${SEP}${ECLIPSE}/plugins/org.eclipse.swt.${ECLIPSE_PLATFORM}_${ECLIPSE_VERSION}/os/${ECLIPSE_ARCH}
