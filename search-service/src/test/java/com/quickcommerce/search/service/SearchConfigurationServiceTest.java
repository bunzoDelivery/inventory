package com.quickcommerce.search.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meilisearch.sdk.model.TaskInfo;
import com.quickcommerce.search.entity.SearchSetting;
import com.quickcommerce.search.entity.SearchSynonym;
import com.quickcommerce.search.provider.MeilisearchProvider;
import com.quickcommerce.search.repository.SearchSettingRepository;
import com.quickcommerce.search.repository.SearchSynonymRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SearchConfigurationServiceTest {

    @Mock
    private SearchSettingRepository settingRepository;

    @Mock
    private SearchSynonymRepository synonymRepository;

    @Mock
    private MeilisearchProvider meilisearchProvider;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private TransactionalOperator transactionalOperator;

    @InjectMocks
    private SearchConfigurationService service;

    @BeforeEach
    void setUp() {
        when(transactionalOperator.transactional(any(Mono.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        // Broad stubs for publishConfiguration() — specific tests override as needed.
        when(synonymRepository.findAllByIsActiveTrue()).thenReturn(Flux.empty());
        when(settingRepository.findByKey(anyString())).thenReturn(Mono.empty());
        TaskInfo taskInfo = mock(TaskInfo.class);
        when(meilisearchProvider.updateSettingsBlocking(any())).thenReturn(taskInfo);
    }

    // =========================================================================
    // addToArraySetting
    // =========================================================================
    @Nested
    class AddToArraySetting {

        @Test
        void shouldAddValueWhenNotPresent() throws Exception {
            SearchSetting existing = SearchSetting.builder()
                .key("searchable_attributes")
                .valueJson("[\"name\",\"brand\"]")
                .version(1)
                .build();

            when(settingRepository.findByKey("searchable_attributes")).thenReturn(Mono.just(existing));
            when(settingRepository.save(any(SearchSetting.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            StepVerifier.create(service.addToArraySetting("searchable_attributes", "categoryName", "admin"))
                .assertNext(saved -> {
                    try {
                        List<String> values = new ObjectMapper().readValue(
                            saved.getValueJson(), new TypeReference<List<String>>() {});
                        assertThat(values).containsExactly("name", "brand", "categoryName");
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    assertThat(saved.getUpdatedBy()).isEqualTo("admin");
                })
                .verifyComplete();

            verify(settingRepository).save(any(SearchSetting.class));
            verify(meilisearchProvider).updateSettingsBlocking(any());
        }

        @Test
        void shouldSkipWhenValueAlreadyPresent() {
            SearchSetting existing = SearchSetting.builder()
                .key("searchable_attributes")
                .valueJson("[\"name\",\"brand\",\"categoryName\"]")
                .version(1)
                .build();

            when(settingRepository.findByKey("searchable_attributes")).thenReturn(Mono.just(existing));

            StepVerifier.create(service.addToArraySetting("searchable_attributes", "categoryName", "admin"))
                .assertNext(saved ->
                    assertThat(saved.getValueJson()).isEqualTo("[\"name\",\"brand\",\"categoryName\"]"))
                .verifyComplete();

            verify(settingRepository, never()).save(any());
        }

        @Test
        void shouldErrorWhenSettingNotFound() {
            when(settingRepository.findByKey("nonexistent")).thenReturn(Mono.empty());

            StepVerifier.create(service.addToArraySetting("nonexistent", "value", "admin"))
                .expectErrorMatches(e -> e instanceof IllegalArgumentException
                    && e.getMessage().contains("Setting not found"))
                .verify();
        }
    }

    // =========================================================================
    // removeFromArraySetting
    // =========================================================================
    @Nested
    class RemoveFromArraySetting {

        @Test
        void shouldRemoveValueWhenPresent() throws Exception {
            SearchSetting existing = SearchSetting.builder()
                .key("searchable_attributes")
                .valueJson("[\"name\",\"brand\",\"categoryName\"]")
                .version(1)
                .build();

            when(settingRepository.findByKey("searchable_attributes")).thenReturn(Mono.just(existing));
            when(settingRepository.save(any(SearchSetting.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            StepVerifier.create(service.removeFromArraySetting("searchable_attributes", "brand", "admin"))
                .assertNext(saved -> {
                    try {
                        List<String> values = new ObjectMapper().readValue(
                            saved.getValueJson(), new TypeReference<List<String>>() {});
                        assertThat(values).containsExactly("name", "categoryName");
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .verifyComplete();

            verify(meilisearchProvider).updateSettingsBlocking(any());
        }

        @Test
        void shouldSkipWhenValueNotPresent() {
            SearchSetting existing = SearchSetting.builder()
                .key("searchable_attributes")
                .valueJson("[\"name\",\"brand\"]")
                .version(1)
                .build();

            when(settingRepository.findByKey("searchable_attributes")).thenReturn(Mono.just(existing));

            StepVerifier.create(service.removeFromArraySetting("searchable_attributes", "categoryName", "admin"))
                .assertNext(saved ->
                    assertThat(saved.getValueJson()).isEqualTo("[\"name\",\"brand\"]"))
                .verifyComplete();

            verify(settingRepository, never()).save(any());
        }

        @Test
        void shouldErrorWhenSettingNotFound() {
            when(settingRepository.findByKey("nonexistent")).thenReturn(Mono.empty());

            StepVerifier.create(service.removeFromArraySetting("nonexistent", "value", "admin"))
                .expectErrorMatches(e -> e instanceof IllegalArgumentException
                    && e.getMessage().contains("Setting not found"))
                .verify();
        }
    }

    // =========================================================================
    // saveSetting — auto-publish
    // =========================================================================
    @Nested
    class SaveSetting {

        @Test
        void shouldSaveNewSettingAndAutoPublish() {
            when(settingRepository.save(any(SearchSetting.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            StepVerifier.create(service.saveSetting(
                    "ranking_rules", "[\"words\",\"typo\"]", "test rules", "admin"))
                .assertNext(saved -> {
                    assertThat(saved.getKey()).isEqualTo("ranking_rules");
                    assertThat(saved.getValueJson()).isEqualTo("[\"words\",\"typo\"]");
                })
                .verifyComplete();

            verify(meilisearchProvider).updateSettingsBlocking(any());
        }

        @Test
        void shouldUpdateExistingSettingAndAutoPublish() {
            SearchSetting existing = SearchSetting.builder()
                .key("ranking_rules")
                .valueJson("[\"words\"]")
                .version(1)
                .build();

            when(settingRepository.findByKey("ranking_rules")).thenReturn(Mono.just(existing));
            when(settingRepository.save(any(SearchSetting.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            StepVerifier.create(service.saveSetting(
                    "ranking_rules", "[\"words\",\"typo\",\"proximity\"]", "updated", "admin"))
                .assertNext(saved ->
                    assertThat(saved.getValueJson()).isEqualTo("[\"words\",\"typo\",\"proximity\"]"))
                .verifyComplete();

            verify(meilisearchProvider).updateSettingsBlocking(any());
        }
    }

    // =========================================================================
    // saveSynonym — auto-publish
    // =========================================================================
    @Nested
    class SaveSynonym {

        @Test
        void shouldSaveAndAutoPublish() {
            when(synonymRepository.findByTerm("doodh")).thenReturn(Mono.empty());
            when(synonymRepository.save(any(SearchSynonym.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            StepVerifier.create(service.saveSynonym("doodh", List.of("milk"), "admin"))
                .assertNext(saved ->
                    assertThat(saved.getTerm()).isEqualTo("doodh"))
                .verifyComplete();

            verify(meilisearchProvider).updateSettingsBlocking(any());
        }

        @Test
        void shouldRejectTermAsSynonymOfItself() {
            StepVerifier.create(service.saveSynonym("milk", List.of("milk", "doodh"), "admin"))
                .expectErrorMatches(e -> e instanceof IllegalArgumentException
                    && e.getMessage().contains("cannot be a synonym of itself"))
                .verify();
        }
    }

    // =========================================================================
    // deleteSynonym — auto-publish
    // =========================================================================
    @Nested
    class DeleteSynonym {

        @Test
        void shouldDeleteAndAutoPublish() {
            SearchSynonym existing = SearchSynonym.builder()
                .id(1L).term("doodh").synonymsJson("[\"milk\"]").isActive(true).build();

            when(synonymRepository.findByTerm("doodh")).thenReturn(Mono.just(existing));
            when(synonymRepository.delete(existing)).thenReturn(Mono.empty());

            StepVerifier.create(service.deleteSynonym("doodh"))
                .verifyComplete();

            verify(synonymRepository).delete(existing);
            verify(meilisearchProvider).updateSettingsBlocking(any());
        }
    }
}
