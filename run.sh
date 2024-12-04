#!/bin/bash
java -Xmx2G -XX:+UseStringDeduplication \
    -Dclojure.compiler.direct-linking=true \
    -jar $(find target -name 'telsos-svc-*-STANDALONE.jar')
