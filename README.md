# Telegram bot service
Service processes commands from telegram bot t.me/KalimerosBot


# ENVIRONMENT
env variables for this app

````
BOT_NAME= # the name of bot 
BOT_SERVER_URL= #url of qraphql server
BOT_TOKEN= #token for bot
TELEGRAM_BOT_MONGO_URI= #url for mongo data base
````


run application
````
docker compose up --build -d
