#!/bin/bash

# --- CONFIG ---
MAIN_CLASS="Main"      # Replace with your main class name if different
APP_NAME="Touchport"
JAR_NAME="$APP_NAME.jar"
EXE_NAME="$APP_NAME.exe"

# --- COMPILE JAVA FILES ---
echo "Compiling Java files in project root..."
javac *.java
if [ $? -ne 0 ]; then
    echo "Compilation failed!"
    exit 1
fi

# --- CREATE MANIFEST ---
echo "Main-Class: $MAIN_CLASS" > manifest.txt

# --- CREATE JAR ---
echo "Creating runnable JAR..."
jar cfm $JAR_NAME manifest.txt *.class
if [ $? -ne 0 ]; then
    echo "JAR creation failed!"
    exit 1
fi

# --- CHECK JPACKAGE ---
if ! command -v jpackage &> /dev/null
then
    echo "jpackage not found. Please install JDK 14+."
    exit 1
fi

# --- CREATE WINDOWS EXE ---
echo "Generating Windows EXE..."
jpackage \
  --input . \
  --name $APP_NAME \
  --main-jar $JAR_NAME \
  --main-class $MAIN_CLASS \
  --type exe \
  --win-console

if [ $? -ne 0 ]; then
    echo "EXE creation failed!"
    exit 1
fi

echo "Done! EXE generated: $EXE_NAME"
