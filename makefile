# DOCKER
DC=docker-compose

up:
	@echo "Starting docker containers..."
	@$(DC) up --remove-orphans -d

up-main:
	@echo "Starting main PostgreSQL..."
	@$(DC) up -d postgres-main

up-test:
	@echo "Starting test PostgreSQL..."
	@$(DC) up -d postgres-test

down:
	@echo "Stopping all containers..."
	@$(DC) down --remove-orphans

down-main:
	@echo "Stopping main PostgreSQL..."
	@$(DC) stop postgres-main

down-test:
	@echo "Stopping test PostgreSQL..."
	@$(DC) stop postgres-test

logs-main:
	@$(DC) logs -f postgres-main

logs-test:
	@$(DC) logs -f postgres-test

ps:
	@$(DC) ps

docker-clean:
	@echo "Removing all containers and volumes..."
	@$(DC) down -v
	@sudo rm -rf ${HOME}/.docker-volumes/sansi-svc

psql-main:
	@docker exec -it sansi-svc-postgres-main psql -U postgres -d sansi

psql-test:
	@docker exec -it sansi-svc-postgres-test psql -U postgres -d sansi

# CLOJURE
clean:
	@git rev-parse HEAD > resources/.commit_hash
	@clojure -T:build clean
	@rm -rf ./.cpcache/

compile:
	@clojure -T:build compile-clj

kaocha:
	@clojure -M:kaocha --skip test-acceptance --skip test-integration

kaocha-it:
	@clojure -M:kaocha --skip test-acceptance

uberjar:
	@git rev-parse HEAD > resources/.commit_hash
	@clojure -T:build uberjar

# NATIVE
SANSI_UBERJAR := $(shell find target -name "sansi-svc-*-STANDALONE.jar" 2>/dev/null | head -n 1)
native:
	native-image \
		--no-fallback \
		--gc=G1 \
		-march=native \
		--initialize-at-build-time \
		--initialize-at-run-time=com.zaxxer.hikari.pool.HikariPool \
		--initialize-at-run-time=org.postgresql.Driver \
		-H:ReflectionConfigurationFiles=resources/graalvm/reflection-config.json \
		-H:ResourceConfigurationFiles=resources/graalvm/resource-config.json \
		-jar $(SANSI_UBERJAR) \
		target/sansi-svc
