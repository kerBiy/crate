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
