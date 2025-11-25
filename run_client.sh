#!/bin/bash

# Directorio de trabajo
cd /home/pi/Adafruit_Pi5_Piomatter/piomatter-java-jni || exit 1

# Configura las librerías nativas
export LD_LIBRARY_PATH=/home/pi/Adafruit_Pi5_Piomatter/piomatter-java-jni/build:$LD_LIBRARY_PATH
export JAVA_LIBRARY_PATH=/home/pi/Adafruit_Pi5_Piomatter/piomatter-java-jni/build:$JAVA_LIBRARY_PATH

echo "=== Iniciando MatrixPlay client en $(date) ==="
echo "LD_LIBRARY_PATH: $LD_LIBRARY_PATH"

# Ejecuta Java con ruta absoluta
/usr/bin/java \
  -Djava.library.path=/home/pi/Adafruit_Pi5_Piomatter/piomatter-java-jni/build \
  -jar /home/pi/Adafruit_Pi5_Piomatter/piomatter-java-jni/target/client-package.jar

EXIT_CODE=$?
echo "MatrixPlay client terminó con código: $EXIT_CODE en $(date)"
exit $EXIT_CODE
