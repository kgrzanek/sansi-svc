# CLOJURE
clean:
	@git rev-parse HEAD > resources/.commit_hash
	@clojure -T:build clean
	@rm -rf ./.cpcache/

compile:
	@clojure -T:build compile-clj

kaocha:
	@clojure -M:kaocha --skip acceptance-test --skip integration-test

kaocha-it:
	@clojure -M:kaocha --skip acceptance-test

uberjar:
	@git rev-parse HEAD > resources/.commit_hash
	@clojure -T:build uberjar

run:
	@java -Xmx2G -XX:+UseStringDeduplication \
		-Dclojure.compiler.direct-linking=true \
		-jar $(shell find target -name 'telsos-svc-*-STANDALONE.jar')
