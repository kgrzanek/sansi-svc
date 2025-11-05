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

# NGROK
ngrok:
	@ngrok http http://localhost:8080
