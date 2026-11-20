#!/bin/sh
# Compila e roda os testes dos leitores (só precisa de Java).
cd "$(dirname "$0")/.." && rm -rf /tmp/bft && mkdir -p /tmp/bft && \
javac -encoding UTF-8 -d /tmp/bft app/src/main/java/br/com/busfloripa/FenixParser.java app/src/main/java/br/com/busfloripa/GenericParser.java app/src/main/java/br/com/busfloripa/BiguacuParser.java app/src/main/java/br/com/busfloripa/Schedule.java app/src/main/java/br/com/busfloripa/MapsLink.java tests/ParserTest.java tests/InterTest.java tests/BiguacuTest.java && \
java -cp /tmp/bft ParserTest && java -cp /tmp/bft InterTest && java -cp /tmp/bft BiguacuTest
