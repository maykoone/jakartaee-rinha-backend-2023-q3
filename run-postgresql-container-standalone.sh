docker run -p 5432:5432 --cpus=0.75 --memory=1g -d --rm \
    -v ./import.sql:/docker-entrypoint-initdb.d/import.sql -v ./postgresql.conf:/etc/postgresql/postgresql.conf \
    -e POSTGRES_PASSWORD=test -e POSTGRES_USER=test \
    postgres:15.4 -c 'config_file=/etc/postgresql/postgresql.conf'