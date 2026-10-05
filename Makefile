ENV_FILE := infra/compose/.env
COMPOSE  := docker compose -f infra/compose/docker-compose.yml --env-file $(ENV_FILE)

.PHONY: infra-up infra-down infra-reset logs ps check-env

check-env:
	@test -f $(ENV_FILE) || { echo "Missing $(ENV_FILE). Run: cp infra/compose/.env.example $(ENV_FILE)"; exit 1; }

## Start Postgres + Kafka and wait until both are healthy
infra-up: check-env
	$(COMPOSE) up -d --wait

## Stop containers, keep data
infra-down: check-env
	$(COMPOSE) down

## Stop containers and delete volumes (re-runs the Postgres init script on next start)
infra-reset: check-env
	$(COMPOSE) down -v --remove-orphans

## Follow logs; optionally one service: make logs s=kafka
logs: check-env
	$(COMPOSE) logs -f --tail=100 $(s)

ps: check-env
	$(COMPOSE) ps

# ---------------------------------------------------------------------------
# Local dev: infra + all services + frontend, in the background.
# Logs and PID files live in ./logs (gitignored).
# ---------------------------------------------------------------------------

SERVICES  := gateway user-service catalog-service review-service
LOG_DIR   := logs
# Same dev JVM flags as bootRun in build-logic/crate.spring-service-conventions.
JVM_FLAGS := -Xmx256m -XX:+UseSerialGC -XX:TieredStopAtLevel=1

# name:port pairs checked by `make status`
HEALTH := gateway:8080 user-service:8081 catalog-service:8082 review-service:8083

.PHONY: dev stop status

## Start infra, all services (local profile) and the Vite dev server in the background
dev: infra-up
	@mkdir -p $(LOG_DIR)
	./gradlew $(foreach s,$(SERVICES),:services:$(s):bootJar) -q
	@for s in $(SERVICES); do \
	  pidf=$(LOG_DIR)/$$s.pid; \
	  if [ -f $$pidf ] && kill -0 $$(cat $$pidf) 2>/dev/null; then echo "$$s already running (pid $$(cat $$pidf))"; continue; fi; \
	  jar=$$(ls services/$$s/build/libs/*.jar | grep -v -- '-plain\.jar$$' | head -1); \
	  ( cd services/$$s && exec nohup java $(JVM_FLAGS) -jar "$(CURDIR)/$$jar" --spring.profiles.active=local \
	      > "$(CURDIR)/$(LOG_DIR)/$$s.log" 2>&1 ) & echo $$! > $$pidf; \
	  echo "started $$s (pid $$(cat $$pidf)) -> $(LOG_DIR)/$$s.log"; \
	done
	@pidf=$(LOG_DIR)/frontend.pid; \
	if [ -f $$pidf ] && kill -0 $$(cat $$pidf) 2>/dev/null; then echo "frontend already running (pid $$(cat $$pidf))"; \
	else \
	  ( cd frontend && exec nohup ./node_modules/.bin/vite > "$(CURDIR)/$(LOG_DIR)/frontend.log" 2>&1 ) & echo $$! > $$pidf; \
	  echo "started frontend (pid $$(cat $$pidf)) -> $(LOG_DIR)/frontend.log"; \
	fi
	@echo "Services take ~10-20s to boot. Check with: make status"

## Stop the frontend, all services and the infra containers (data is kept)
stop:
	@for s in frontend $(SERVICES); do \
	  pidf=$(LOG_DIR)/$$s.pid; \
	  [ -f $$pidf ] || continue; \
	  pid=$$(cat $$pidf); \
	  if kill $$pid 2>/dev/null; then \
	    for i in $$(seq 1 20); do kill -0 $$pid 2>/dev/null || break; sleep 0.5; done; \
	    kill -0 $$pid 2>/dev/null && kill -9 $$pid; \
	    echo "stopped $$s"; \
	  fi; \
	  rm -f $$pidf; \
	done
	@$(MAKE) --no-print-directory infra-down

## Show the health of infra, each service (/actuator/health) and the frontend
status: check-env
	@$(COMPOSE) ps --format 'table {{.Service}}\t{{.Status}}'
	@echo
	@for pair in $(HEALTH); do \
	  name=$${pair%%:*}; port=$${pair##*:}; \
	  body=$$(curl -s -m 2 http://localhost:$$port/actuator/health); \
	  st=$$(printf '%s' "$$body" | grep -o '"status":"[A-Z_]*"' | head -1 | cut -d'"' -f4); \
	  printf '%-16s :%-5s %s\n' $$name $$port "$${st:-DOWN}"; \
	done
	@if curl -s -o /dev/null -m 2 http://localhost:5173; then st=UP; else st=DOWN; fi; \
	printf '%-16s :%-5s %s\n' frontend 5173 $$st
