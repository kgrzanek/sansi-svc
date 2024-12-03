# DOCKER
DC=docker-compose

up:
	@echo "Starting docker containers..."
	@$(DC) up --remove-orphans -d

up-main:
	@echo "Starting main PostgreSQL..."
	@$(DC) up -d main-postgres

up-test:
	@echo "Starting test PostgreSQL..."
	@$(DC) up -d test-postgres

down:
	@echo "Stopping all containers..."
	@$(DC) down --remove-orphans

down-main:
	@echo "Stopping main PostgreSQL..."
	@$(DC) stop main-postgres

down-test:
	@echo "Stopping test PostgreSQL..."
	@$(DC) stop test-postgres

logs-main:
	@$(DC) logs -f main-postgres

logs-test:
	@$(DC) logs -f test-postgres

ps:
	@$(DC) ps

docker-clean:
	@echo "Removing all containers and volumes..."
	@$(DC) down -v
	@sudo rm -rf ${HOME}/.docker-volumes/telsos-svc

psql-main:
	@docker exec -it telsos-svc-main-postgres psql -U postgres -d telsos

psql-test:
	@docker exec -it telsos-svc-test-postgres psql -U postgres -d telsos

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

run:
	@java -Xmx2G -XX:+UseStringDeduplication \
		-Dclojure.compiler.direct-linking=true \
		-jar $(shell find target -name 'telsos-svc-*-STANDALONE.jar')
