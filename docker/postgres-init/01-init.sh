#!/bin/bash
set -euo pipefail

echo "[ecogreen-init] Running init-postgres.sql..."
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -f /docker-entrypoint-initdb.d/sql/init-postgres.sql

if [ "${SEED_DEMO_DATA:-true}" = "true" ]; then
  echo "[ecogreen-init] SEED_DEMO_DATA=true -> Running seed-data.sql..."
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -f /docker-entrypoint-initdb.d/sql/seed-data.sql
else
  echo "[ecogreen-init] SEED_DEMO_DATA=${SEED_DEMO_DATA:-false} -> Skipping seed-data.sql."
fi

echo "[ecogreen-init] Applying migrations in numeric order..."
for f in $(ls /docker-entrypoint-initdb.d/migrations/V*.sql | sort -V); do
  echo "[ecogreen-init] Applying $(basename "$f")..."
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -f "$f"
done

echo "[ecogreen-init] Database initialization complete."
