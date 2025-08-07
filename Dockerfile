# Build stage
FROM gradle:8.8-jdk21 AS build
WORKDIR /home/gradle/src
COPY . .
RUN gradle --no-daemon shadowJar

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /home/gradle/src/build/libs/bynarix-overseer-1.1.0-all.jar app.jar

ENV BOT_TOKEN=""
ENV BOT_USERNAME="bynarix_overseer_bot"
ENV NOTIFY_CHAT_ID=""
ENV TARGET_CHANNEL_USERNAME="binaryx_platform_bot"
ENV KEYWORDS="airdrop,claim,free,bonus,reward,presale,ico,token,launch,listing,partnership,announcement,update,news,important"
ENV SUBSCRIPTIONS_FILE="/app/subscriptions.json"

EXPOSE 8000
ENTRYPOINT ["java","-jar","/app/app.jar"]
