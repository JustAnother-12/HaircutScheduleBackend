#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 \
    --username "$POSTGRES_USER" \
    --dbname "$POSTGRES_DB" \
    --set=app_password="$DB_PASSWORD" <<'EOSQL'

CREATE ROLE haircut_app
    WITH LOGIN PASSWORD :'app_password';

CREATE DATABASE barbershop_dev OWNER haircut_app;

EOSQL