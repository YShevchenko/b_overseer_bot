package com.bynarix.overseer;

import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class OverseerBot extends TelegramLongPollingBot {

    private final String botToken = System.getenv().getOrDefault("BOT_TOKEN", "");
    private final String botUsername = System.getenv().getOrDefault("BOT_USERNAME", "bynarix_overseer_bot");
    private final String globalNotifyChatId = System.getenv().getOrDefault("NOTIFY_CHAT_ID", "");
    private final String targetChannelUsername = System.getenv().getOrDefault("TARGET_CHANNEL_USERNAME", "binaryx_platform_bot");
    private final String subscriptionsFile = System.getenv().getOrDefault("SUBSCRIPTIONS_FILE", "subscriptions.json");

    private final List<String> globalKeywords; // from KEYWORDS env, may be empty
    private final SubscriptionStore subscriptionStore;

    public OverseerBot() {
        String rawKeywords = System.getenv("KEYWORDS");
        if (rawKeywords == null || rawKeywords.isBlank()) {
            globalKeywords = Collections.emptyList();
        } else {
            globalKeywords = Arrays.stream(rawKeywords.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(String::toLowerCase)
                    .distinct()
                    .collect(Collectors.toList());
        }
        subscriptionStore = new SubscriptionStore(subscriptionsFile);
        log.info("OverseerBot started. Monitoring target: @{}", stripAt(targetChannelUsername));
        registerCommands();
    }

    private void registerCommands() {
        List<BotCommand> cmds = List.of(
                new BotCommand("start", "Show welcome and commands"),
                new BotCommand("help", "Show help"),
                new BotCommand("keywords", "Show global keywords (from KEYWORDS env)"),
                new BotCommand("sub", "Subscribe to keywords: /sub k1,k2"),
                new BotCommand("unsub", "Unsubscribe keywords: /unsub k1,k2"),
                new BotCommand("subscriptions", "List your keywords"),
                new BotCommand("clear", "Clear your keywords")
        );
        try {
            execute(new SetMyCommands(cmds, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            log.warn("Failed to set commands: {}", e.toString());
        }
    }

    @Override
    public String getBotUsername() { return botUsername; }

    @Override
    public String getBotToken() { return botToken; }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage()) {
                Message msg = update.getMessage();
                if (msg.hasText() && msg.getText().startsWith("/")) {
                    handleCommand(msg);
                } else {
                    handlePotentialMatch(msg, false);
                }
                return;
            }
            if (update.hasChannelPost()) {
                handlePotentialMatch(update.getChannelPost(), true);
                return;
            }
            if (update.hasEditedChannelPost()) {
                handlePotentialMatch(update.getEditedChannelPost(), true);
            }
        } catch (Exception e) {
            log.warn("Error processing update: {}", e.toString());
        }
    }

    private void handleCommand(Message msg) {
        String chatId = msg.getChatId().toString();
        String text = msg.getText().trim();
        String cmd = text.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        String args = text.length() > cmd.length() ? text.substring(cmd.length()).trim() : "";

        switch (cmd) {
            case "/start":
                safeReply(chatId, "🤖 bynarix-overseer\n\n" +
                        "Commands:\n" +
                        "/sub <k1,k2,...> — subscribe to keywords\n" +
                        "/unsub <k1,k2,...> — remove keywords\n" +
                        "/subscriptions — list your keywords\n" +
                        "/clear — clear your keywords\n" +
                        "/keywords — show global keywords\n" +
                        "/help — show help");
                break;
            case "/help":
                safeReply(chatId, "Usage:\n" +
                        "/sub airdrop,claim\n" +
                        "/unsub claim\n" +
                        "/subscriptions\n" +
                        "/clear\n" +
                        "/keywords");
                break;
            case "/keywords":
                if (globalKeywords.isEmpty()) {
                    safeReply(chatId, "Global keywords: (none configured). Set KEYWORDS env to enable global alerts.");
                } else {
                    safeReply(chatId, "Global keywords:\n" + String.join(", ", globalKeywords));
                }
                break;
            case "/subscriptions":
                Set<String> cur = subscriptionStore.getKeywordsForChat(chatId);
                if (cur.isEmpty()) {
                    safeReply(chatId, "You have no subscriptions. Use /sub <k1,k2,...>");
                } else {
                    safeReply(chatId, "Your keywords:\n" + String.join(", ", cur));
                }
                break;
            case "/sub":
            case "/subscribe": // legacy alias
                Set<String> toAdd = parseKeywords(args);
                int added = subscriptionStore.subscribe(chatId, toAdd);
                if (added == 0) safeReply(chatId, "Nothing to add. Provide keywords.");
                else safeReply(chatId, "Added " + added + " keyword(s). Use /subscriptions to view.");
                break;
            case "/unsub":
            case "/unsubscribe": // legacy alias
                Set<String> toRemove = parseKeywords(args);
                int removed = subscriptionStore.unsubscribe(chatId, toRemove);
                if (removed == 0) safeReply(chatId, "Nothing to remove.");
                else safeReply(chatId, "Removed keyword(s). Use /subscriptions to view.");
                break;
            case "/clear":
                subscriptionStore.clear(chatId);
                safeReply(chatId, "Cleared your subscriptions.");
                break;
            default:
                break;
        }
    }

    private Set<String> parseKeywords(String args) {
        if (args == null || args.isBlank()) return Collections.emptySet();
        String normalized = args.replace(';', ',').replace('|', ',');
        String[] parts = normalized.split(",");
        Set<String> set = new HashSet<>();
        for (String p : parts) {
            String k = p.trim().toLowerCase(Locale.ROOT);
            if (!k.isEmpty()) set.add(k);
        }
        return set;
    }

    private void handlePotentialMatch(Message msg, boolean isChannel) {
        if (msg == null || !msg.hasText()) return;
        Chat chat = msg.getChat();
        String textLower = msg.getText().toLowerCase(Locale.ROOT);

        if (!isFromTarget(chat, msg)) return;

        StringBuilder base = new StringBuilder();
        base.append("Keyword hit in ");
        base.append(isChannel ? "channel " : "chat ");
        base.append(displayChat(chat)).append("\n\n");
        base.append(msg.getText());
        String link = buildMessageLink(chat, msg);
        if (link != null) base.append("\n\nLink: ").append(link);
        String alertText = base.toString();

        // Notify global only if globalKeywords configured and matched
        if (!globalKeywords.isEmpty() && globalNotifyChatId != null && !globalNotifyChatId.isBlank()) {
            boolean globalHit = globalKeywords.stream().anyMatch(textLower::contains);
            if (globalHit) {
                notifyChat(globalNotifyChatId, alertText);
            }
        }

        // Notify subscribers whose sets match
        Map<String, Set<String>> all = subscriptionStore.snapshotAll();
        for (Map.Entry<String, Set<String>> e : all.entrySet()) {
            String subscriberChatId = e.getKey();
            Set<String> kws = e.getValue();
            boolean hit = kws.stream().anyMatch(textLower::contains);
            if (hit) {
                notifyChat(subscriberChatId, alertText);
            }
        }
    }

    private boolean isFromTarget(Chat chat, Message msg) {
        String chatUsername = chat.getUserName();
        if (chatUsername != null && !chatUsername.isBlank()) {
            return chatUsername.equalsIgnoreCase(stripAt(targetChannelUsername));
        }
        if (msg.getForwardFromChat() != null && msg.getForwardFromChat().getUserName() != null) {
            return msg.getForwardFromChat().getUserName().equalsIgnoreCase(stripAt(targetChannelUsername));
        }
        return false;
    }

    private String stripAt(String u) { return (u != null && u.startsWith("@")) ? u.substring(1) : u; }

    private String displayChat(Chat chat) {
        if (chat.getUserName() != null) return "@" + chat.getUserName();
        if (chat.getTitle() != null) return chat.getTitle();
        return String.valueOf(chat.getId());
    }

    private String buildMessageLink(Chat chat, Message msg) {
        if (chat.getUserName() != null && msg.getMessageId() != null) {
            return "https://t.me/" + chat.getUserName() + "/" + msg.getMessageId();
        }
        return null;
    }

    private void notifyChat(String chatId, String text) { if (chatId != null && !chatId.isBlank()) safeSend(chatId, text); }

    private void safeReply(String chatId, String text) { safeSend(chatId, text); }

    private void safeSend(String chatId, String text) {
        SendMessage sm = SendMessage.builder().chatId(chatId).text(text).disableWebPagePreview(true).build();
        try { execute(sm); } catch (TelegramApiException e) { log.warn("Send failed: {}", e.toString()); }
    }
}
