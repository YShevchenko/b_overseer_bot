### bynarix-overseer
Java 21 Telegram bot that notifies you when specified keywords appear in a target channel or in a group that forwards from it.

Important Telegram rules:
- Bots cannot read private messages from other bots. Monitoring `@binaryx_platform_bot` works only if its posts are in a public channel your bot is a member/admin of, or they’re forwarded into a group your bot is in.

### Features
- Java 21, SLF4J logging
- Per-user keyword subscriptions: /subscribe, /unsubscribe, /subscriptions, /clear
- Commands registered for autocomplete in Telegram clients
- Optional global notifications via `NOTIFY_CHAT_ID` if `KEYWORDS` env is set
- Dockerized; deploy on Fly.io/Koyeb free tiers

### Environment variables
- BOT_TOKEN: from `@BotFather`
- BOT_USERNAME: your bot username
- NOTIFY_CHAT_ID: optional global destination chat ID for matches to global `KEYWORDS`
- TARGET_CHANNEL_USERNAME: `binaryx_platform_bot` (without `@`)
- KEYWORDS: global keywords (comma-separated). If empty or unset, no global alerts are sent.
- SUBSCRIPTIONS_FILE: path to JSON file to persist subscriptions (default `/app/subscriptions.json` in Docker)

Tip: Use `@RawDataBot` to find your own user ID or a chat ID.

### Build and run in Docker (recommended)
- `docker build -t bynarix-overseer .`
- `docker run -e BOT_TOKEN=xxxx -e BOT_USERNAME=your_bot -e NOTIFY_CHAT_ID=123456 -e TARGET_CHANNEL_USERNAME=binaryx_platform_bot -e KEYWORDS="airdrop,claim" -e SUBSCRIPTIONS_FILE=/app/subscriptions.json --name overseer --restart unless-stopped -d bynarix-overseer`

### Free deployment
- Fly.io or Koyeb: deploy Dockerfile and set the same environment variables as above.

### Bot commands (DM the bot)
- /start — show help
- /keywords — show global keywords (from KEYWORDS env; may be none)
- /subscribe k1,k2 — add keywords to your personal watchlist
- /unsubscribe k1,k2 — remove keywords
- /subscriptions — list your keywords
- /clear — remove all your keywords

### How matching works
- The bot matches messages from the target channel (or forwarded posts) against subscribers’ keyword sets. When a match occurs, it notifies matching subscribers. If `KEYWORDS` is set and a message matches any of them, it also notifies `NOTIFY_CHAT_ID`.
