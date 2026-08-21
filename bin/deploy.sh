bin/gen-schema.sh
lein do clean, uberjar
# See https://github.com/heroku/heroku-jvm-application-deployer/releases
HEROKU_API_KEY=$(heroku auth:token) java -jar bin/heroku-jvm-application-deployer-4.0.12.jar --app=aact target/uberjar/aact-standalone.jar

