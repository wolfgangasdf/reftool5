#!/bin/bash

cd `dirname $0`

java -Xmx128m -Xdock:name="Reftool5" -jar target/scala-*/reftool5_*/reftool5_*.jar
