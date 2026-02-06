package com.quickcommerce.search.repository;

import com.quickcommerce.search.entity.SearchSynonym;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * R2DBC reactive repository for search synonyms
 */
@Repository
public interface SearchSynonymRepository extends ReactiveCrudRepository<SearchSynonym, Long> {
    Mono<SearchSynonym> findByTerm(String term);

    Flux<SearchSynonym> findAllByIsActiveTrue();
}
