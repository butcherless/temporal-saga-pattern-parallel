#!/bin/bash
# Creates one PostgreSQL database per service, listed in POSTGRES_MULTIPLE_DATABASES.
# Same script as temporal-saga-pattern's platform-test, reused verbatim.
set -e

create_database() {
    local database=$1
    echo "Creating database '$database'"
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
        CREATE DATABASE $database;
EOSQL
}

if [ -n "$POSTGRES_MULTIPLE_DATABASES" ]; then
    echo "Requested databases: $POSTGRES_MULTIPLE_DATABASES"
    for db in $(echo "$POSTGRES_MULTIPLE_DATABASES" | tr ',' ' '); do
        create_database "$db"
    done
    echo "Databases created successfully"
fi
