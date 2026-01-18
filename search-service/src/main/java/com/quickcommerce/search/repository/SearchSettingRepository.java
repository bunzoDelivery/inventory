package com.quickcommerce.search.repository;

import com.quickcommerce.search.entity.SearchSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SearchSettingRepository extends JpaRepository<SearchSetting, String> {
    Optional<SearchSetting> findByKey(String key);
}
