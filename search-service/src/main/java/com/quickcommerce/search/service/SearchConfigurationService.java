package com.quickcommerce.search.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meilisearch.sdk.model.Settings;
import com.meilisearch.sdk.model.TaskInfo;
import com.meilisearch.sdk.model.TypoTolerance;
import com.quickcommerce.search.entity.SearchSetting;
import com.quickcommerce.search.entity.SearchSynonym;
import com.quickcommerce.search.provider.MeilisearchProvider;
import com.quickcommerce.search.repository.SearchSettingRepository;
import com.quickcommerce.search.repository.SearchSynonymRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fully reactive service for managing search configuration
 * Handles synonyms and settings persistence with JSON conversion
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchConfigurationService {

    private final SearchSettingRepository settingRepository;
    private final SearchSynonymRepository synonymRepository;
    private final MeilisearchProvider meilisearchProvider;
    private final ObjectMapper objectMapper;
    private final TransactionalOperator transactionalOperator;

    /**
     * Upsert a synonym group (fully reactive)
     * @param term The search term
     * @param synonyms List of synonyms for the term
     * @param updatedBy Username of the person making the change
     * @return Mono of saved SearchSynonym
     */
    public Mono<SearchSynonym> saveSynonym(String term, List<String> synonyms, String updatedBy) {
        if (synonyms.contains(term)) {
            return Mono.error(new IllegalArgumentException("Term cannot be a synonym of itself: " + term));
        }

        return synonymRepository.findByTerm(term)
            .switchIfEmpty(Mono.just(SearchSynonym.builder().term(term).build()))
            .flatMap(entity -> {
                try {
                    entity.setSynonymsJson(objectMapper.writeValueAsString(synonyms));
                    entity.setIsActive(true);
                    entity.setUpdatedAt(LocalDateTime.now());
                    entity.setUpdatedBy(updatedBy);
                    return synonymRepository.save(entity);
                } catch (JsonProcessingException e) {
                    return Mono.error(new IllegalArgumentException("Failed to serialize synonyms to JSON", e));
                }
            })
            .as(transactionalOperator::transactional);
    }

    /**
     * Get all active synonyms
     * @return Flux of all active SearchSynonym entities
     */
    public Flux<SearchSynonym> getAllSynonyms() {
        return synonymRepository.findAll();
    }

    /**
     * Get all active synonym terms as parsed objects
     * @return Flux of synonym mappings with term and list of synonyms
     */
    public Flux<Map<String, List<String>>> getActiveSynonymMaps() {
        return synonymRepository.findAllByIsActiveTrue()
            .flatMap(entity -> {
                try {
                    List<String> synonyms = objectMapper.readValue(
                        entity.getSynonymsJson(), 
                        new TypeReference<List<String>>() {}
                    );
                    return Mono.just(Map.of(entity.getTerm(), synonyms));
                } catch (Exception e) {
                    log.error("Failed to parse synonyms JSON for term: {}", entity.getTerm(), e);
                    return Mono.empty();
                }
            });
    }

    /**
     * Delete a synonym by term
     * @param term The search term to delete
     * @return Mono<Void> when deletion completes
     */
    public Mono<Void> deleteSynonym(String term) {
        return synonymRepository.findByTerm(term)
            .flatMap(synonymRepository::delete)
            .as(transactionalOperator::transactional);
    }

    /**
     * Get all settings
     * @return Flux of all SearchSetting entities
     */
    public Flux<SearchSetting> getAllSettings() {
        return settingRepository.findAll();
    }

    /**
     * Upsert a setting (create or update)
     * @param key Setting key
     * @param valueJson JSON array as string
     * @param description Description
     * @param updatedBy Username
     * @return Mono of saved SearchSetting
     */
    public Mono<SearchSetting> saveSetting(String key, String valueJson, String description, String updatedBy) {
        return settingRepository.findByKey(key)
            .switchIfEmpty(Mono.just(SearchSetting.builder().key(key).build()))
            .flatMap(entity -> {
                entity.setValueJson(valueJson);
                entity.setDescription(description);
                entity.setUpdatedAt(LocalDateTime.now());
                entity.setUpdatedBy(updatedBy);
                return settingRepository.save(entity);
            })
            .as(transactionalOperator::transactional);
    }

    /**
     * Bootstrap default settings if table is empty
     * @param updatedBy Username
     * @return Mono<Integer> count of settings created
     */
    public Mono<Integer> bootstrapDefaultSettings(String updatedBy) {
        return settingRepository.count()
            .flatMap(count -> {
                if (count > 0) {
                    log.info("Settings already exist ({}), skipping bootstrap", count);
                    return Mono.just(0);
                }
                
                log.info("Bootstrapping default search settings");
                
                Map<String, String[]> defaults = Map.of(
                    "ranking_rules", new String[]{"words", "typo", "proximity", "attribute", "sort", "exactness"},
                    "searchable_attributes", new String[]{"name", "brand", "keywords", "barcode"},
                    "filterable_attributes", new String[]{"storeIds", "isActive", "brand", "categoryId", "isBestseller"},
                    "sortable_attributes", new String[]{"price", "searchPriority"}
                );
                
                return Flux.fromIterable(defaults.entrySet())
                    .flatMap(entry -> {
                        try {
                            String json = objectMapper.writeValueAsString(entry.getValue());
                            return saveSetting(entry.getKey(), json, "Default " + entry.getKey(), updatedBy);
                        } catch (JsonProcessingException e) {
                            log.error("Failed to serialize setting {}", entry.getKey(), e);
                            return Mono.empty();
                        }
                    })
                    .count()
                    .map(Long::intValue);
            });
    }

    /**
     * Get all active settings and push to Meilisearch (fully reactive)
     * Returns the Task Info from Meilisearch
     */
    public Mono<TaskInfo> publishConfiguration() {
        log.info("Starting reactive Search Configuration Sync...");

        // 1. Build Synonyms Map reactively
        Mono<HashMap<String, String[]>> synonymsMapMono = synonymRepository.findAllByIsActiveTrue()
            .flatMap(synonym -> {
                try {
                    List<String> synonymsList = objectMapper.readValue(
                        synonym.getSynonymsJson(), 
                        new TypeReference<List<String>>() {}
                    );
                    return Mono.just(Map.entry(synonym.getTerm(), synonymsList.toArray(new String[0])));
                } catch (Exception e) {
                    log.error("Failed to parse synonyms for term: {}", synonym.getTerm(), e);
                    return Mono.empty();
                }
            })
            .collectMap(Map.Entry::getKey, Map.Entry::getValue)
            .map(HashMap::new)
            .defaultIfEmpty(new HashMap<>());

        // 2. Build Settings object reactively
        return synonymsMapMono.flatMap(synonymsMap -> {
            Settings settings = new Settings();
            settings.setSynonyms(synonymsMap);

            // Apply known settings from DB
            return applyAllSettings(settings)
                .then(Mono.fromCallable(() -> {
                    applyTypoToleranceForShortWords(settings);
                    log.info("Pushing configuration to Meilisearch: {} synonyms, settings applied.", synonymsMap.size());
                    return meilisearchProvider.updateSettingsBlocking(settings);
                }).subscribeOn(Schedulers.boundedElastic()));
        });
    }

    /**
     * Apply all known settings from DB to the Settings object
     */
    private Mono<Void> applyAllSettings(Settings settings) {
        return Flux.just(
            "ranking_rules", 
            "searchable_attributes", 
            "filterable_attributes", 
            "sortable_attributes", 
            "stop_words"
        )
        .flatMap(key -> applySetting(settings, key))
        .then();
    }

    /**
     * Helper to apply setting from DB reactively
     */
    private Mono<Void> applySetting(Settings settings, String key) {
        return settingRepository.findByKey(key)
            .doOnNext(dbSetting -> {
                try {
                    String[] value = convertJsonToStringArray(dbSetting.getValueJson());
                    
                    // Map known keys to Settings setters
                    switch (key) {
                        case "ranking_rules":
                            settings.setRankingRules(value);
                            break;
                        case "searchable_attributes":
                            settings.setSearchableAttributes(value);
                            break;
                        case "filterable_attributes":
                            settings.setFilterableAttributes(value);
                            break;
                        case "sortable_attributes":
                            settings.setSortableAttributes(value);
                            break;
                        case "stop_words":
                            settings.setStopWords(value);
                            break;
                        default:
                            log.warn("Setting '{}' present in DB but not mapped in Service code", key);
                    }
                } catch (Exception e) {
                    log.error("Failed to apply setting {}", key, e);
                }
            })
            .switchIfEmpty(Mono.defer(() -> {
                log.debug("Setting '{}' not found in DB, skipping.", key);
                return Mono.empty();
            }))
            .onErrorResume(e -> {
                log.error("Failed to fetch setting {}", key, e);
                return Mono.empty();
            })
            .then();
    }

    /**
     * Convert JSON string to String array
     */
    private String[] convertJsonToStringArray(String jsonValue) throws JsonProcessingException {
        return objectMapper.readValue(jsonValue, String[].class);
    }

    /**
     * Apply typo tolerance for short words (e.g. "milk" 4 chars).
     * Default Meilisearch requires 5+ chars for one typo - we lower to 4 so "milc" matches "milk".
     */
    private void applyTypoToleranceForShortWords(Settings settings) {
        TypoTolerance typoTolerance = new TypoTolerance();
        typoTolerance.setEnabled(true);
        Map<String, Integer> minWordSize = new HashMap<>();
        minWordSize.put("oneTypo", 4);   // Allow 1 typo for words 4+ chars
        minWordSize.put("twoTypos", 8);  // Allow 2 typos for words 8+ chars
        typoTolerance.setMinWordSizeForTypos(new HashMap<>(minWordSize));
        settings.setTypoTolerance(typoTolerance);
    }
}
