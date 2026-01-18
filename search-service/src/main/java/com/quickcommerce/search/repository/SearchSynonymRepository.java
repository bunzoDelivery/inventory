package com.quickcommerce.search.repository;

import com.quickcommerce.search.entity.SearchSynonym;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SearchSynonymRepository extends JpaRepository<SearchSynonym, Long> {
    Optional<SearchSynonym> findByTerm(String term);

    List<SearchSynonym> findAllByIsActiveTrue();
}
