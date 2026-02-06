package com.quickcommerce.search.repository;

import com.quickcommerce.search.entity.SearchSetting;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * R2DBC reactive repository for search settings
 */
@Repository
public interface SearchSettingRepository extends ReactiveCrudRepository<SearchSetting, String> {
    Mono<SearchSetting> findByKey(String key);
}
