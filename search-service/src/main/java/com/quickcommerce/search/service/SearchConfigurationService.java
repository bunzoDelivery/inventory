package com.quickcommerce.search.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meilisearch.sdk.model.Settings;
import com.meilisearch.sdk.model.TaskInfo;
import com.quickcommerce.search.entity.SearchSetting;
import com.quickcommerce.search.entity.SearchSynonym;
import com.quickcommerce.search.provider.MeilisearchProvider;
import com.quickcommerce.search.repository.SearchSettingRepository;
import com.quickcommerce.search.repository.SearchSynonymRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchConfigurationService {

    private final SearchSettingRepository settingRepository;
    private final SearchSynonymRepository synonymRepository;
    private final MeilisearchProvider meilisearchProvider;
    private final ObjectMapper objectMapper;

    /**
     * Upsert a synonym group
     */
    @Transactional
    public SearchSynonym saveSynonym(String term, List<String> synonyms) {
        if (synonyms.contains(term)) {
            throw new IllegalArgumentException("Term cannot be a synonym of itself: " + term);
        }

        SearchSynonym entity = synonymRepository.findByTerm(term)
                .orElse(SearchSynonym.builder().term(term).build());

        entity.setSynonyms(synonyms);
        entity.setActive(true);
        // updatedBy and updatedAt handled by Auditing
        return synonymRepository.save(entity);
    }

    /**
     * Get all active settings and push to Meilisearch
     * Returns the Task Info from Meilisearch
     */
    /**
     * Get all active settings and push to Meilisearch
     * Returns the Task Info from Meilisearch
     */
    public Mono<TaskInfo> publishConfiguration() {
        return Mono.fromCallable(() -> {
            log.info("Starting Search Configuration Sync...");

            // 1. Build Synonyms Map
            HashMap<String, String[]> synonymsMap = new HashMap<>();
            List<SearchSynonym> activeSynonyms = synonymRepository.findAllByIsActiveTrue();
            for (SearchSynonym s : activeSynonyms) {
                synonymsMap.put(s.getTerm(), s.getSynonyms().toArray(new String[0]));
            }

            // 2. Build Global Settings
            Settings settings = new Settings();
            settings.setSynonyms(synonymsMap);

            // Fetch and apply known settings from DB
            applySetting(settings, "ranking_rules");
            applySetting(settings, "searchable_attributes");
            applySetting(settings, "filterable_attributes");
            applySetting(settings, "sortable_attributes");
            applySetting(settings, "stop_words");
            applySetting(settings, "typo_tolerance");
            // Add more keys as needed. Unset strings remain null and are ignored by SDK (partial update logic applies if not set, 
            // but Meilisearch SDK usually sends everything you set. If we want to RESET to default for missing keys, 
            // we would need to set them to null explicitly, which we do by default implementation of Settings object).

            // 3. Push to Meilisearch
            log.info("Pushing configuration to Meilisearch: {} synonyms, settings applied.", synonymsMap.size());
            return meilisearchProvider.updateSettingsBlocking(settings);

        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Helper to apply setting from DB
     */
    private void applySetting(Settings settings, String key) {
        try {
            Optional<SearchSetting> dbSetting = settingRepository.findByKey(key);
            if (dbSetting.isEmpty()) {
                log.debug("Setting '{}' not found in DB, skipping.", key);
                return;
            }

            Object value = dbSetting.get().getValue();

            // Map known keys to Settings setters
            switch (key) {
                case "ranking_rules":
                    settings.setRankingRules(convertToStringArray(value));
                    break;
                case "searchable_attributes":
                    settings.setSearchableAttributes(convertToStringArray(value));
                    break;
                case "filterable_attributes":
                    settings.setFilterableAttributes(convertToStringArray(value));
                    break;
                case "sortable_attributes":
                    settings.setSortableAttributes(convertToStringArray(value));
                    break;
                case "stop_words":
                    settings.setStopWords(convertToStringArray(value));
                    break;
                default:
                    log.warn("Setting '{}' present in DB but not mapped in Service code", key);
            }
        } catch (Exception e) {
            log.error("Failed to apply setting {}", key, e);
        }
    }

    private String[] convertToStringArray(Object value) {
        return objectMapper.convertValue(value, String[].class);
    }

    /**
     * Load settings on startup
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        publishConfiguration()
                .doOnSuccess(task -> log.info("Startup Sync Triggered. Task UID: {}", task.getTaskUid()))
                .doOnError(e -> log.error("Startup Sync Failed", e))
                .subscribe();
    }
}
