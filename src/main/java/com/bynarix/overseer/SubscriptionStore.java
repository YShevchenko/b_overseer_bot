package com.bynarix.overseer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class SubscriptionStore {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Path storagePath;

    private final ConcurrentHashMap<String, Set<String>> chatIdToKeywords = new ConcurrentHashMap<>();

    public SubscriptionStore(String filePath) {
        this.storagePath = Path.of(filePath);
        loadFromDisk();
    }

    public Set<String> getKeywordsForChat(String chatId) {
        return chatIdToKeywords.getOrDefault(chatId, Collections.emptySet());
    }

    public Map<String, Set<String>> snapshotAll() {
        Map<String, Set<String>> snapshot = new HashMap<>();
        for (Map.Entry<String, Set<String>> e : chatIdToKeywords.entrySet()) {
            snapshot.put(e.getKey(), new HashSet<>(e.getValue()));
        }
        return snapshot;
    }

    public synchronized int subscribe(String chatId, Set<String> keywords) {
        if (keywords == null || keywords.isEmpty()) return 0;
        Set<String> lower = new HashSet<>();
        for (String k : keywords) {
            if (k == null) continue;
            String v = k.trim().toLowerCase();
            if (!v.isEmpty()) lower.add(v);
        }
        if (lower.isEmpty()) return 0;
        chatIdToKeywords.merge(chatId, lower, (oldSet, newSet) -> {
            Set<String> merged = new HashSet<>(oldSet);
            merged.addAll(newSet);
            return merged;
        });
        persistSafely();
        return lower.size();
    }

    public synchronized int unsubscribe(String chatId, Set<String> keywords) {
        Set<String> current = chatIdToKeywords.get(chatId);
        if (current == null || current.isEmpty()) return 0;
        int before = current.size();
        for (String k : keywords) {
            if (k == null) continue;
            current.remove(k.trim().toLowerCase());
        }
        if (current.isEmpty()) chatIdToKeywords.remove(chatId);
        persistSafely();
        return Math.max(0, before - getKeywordsForChat(chatId).size());
    }

    public synchronized void clear(String chatId) {
        chatIdToKeywords.remove(chatId);
        persistSafely();
    }

    private void loadFromDisk() {
        try {
            File f = storagePath.toFile();
            if (!f.exists()) {
                ensureParentDir();
                return;
            }
            Map<String, Set<String>> data = mapper.readValue(f, new TypeReference<Map<String, Set<String>>>(){});
            chatIdToKeywords.clear();
            if (data != null) {
                for (Map.Entry<String, Set<String>> e : data.entrySet()) {
                    chatIdToKeywords.put(e.getKey(), new HashSet<>(e.getValue()));
                }
            }
            log.info("Loaded {} subscription entries from {}", chatIdToKeywords.size(), storagePath);
        } catch (IOException e) {
            log.warn("Failed to load subscriptions from {}: {}", storagePath, e.toString());
        }
    }

    private void persistSafely() {
        try {
            ensureParentDir();
            Map<String, Set<String>> snapshot = new HashMap<>();
            for (Map.Entry<String, Set<String>> e : chatIdToKeywords.entrySet()) {
                snapshot.put(e.getKey(), new HashSet<>(e.getValue()));
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(storagePath.toFile(), snapshot);
        } catch (IOException e) {
            log.warn("Failed to persist subscriptions to {}: {}", storagePath, e.toString());
        }
    }

    private void ensureParentDir() throws IOException {
        Path parent = storagePath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
    }
}
