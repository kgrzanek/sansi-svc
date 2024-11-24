# CLOJURE
clean:
	@git rev-parse HEAD > resources/.commit_hash
	@clojure -T:build clean
	@rm -rf ./.cpcache/

compile:
	@clojure -T:build compile-clj

kaocha-it:
	@clojure -M:kaocha --skip acceptance-test

kaocha:
	@clojure -M:kaocha --skip acceptance-test --skip integration-test

uberjar:
	@git rev-parse HEAD > resources/.commit_hash
	@clojure -T:build uberjar
