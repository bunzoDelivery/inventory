package com.quickcommerce.common.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Utility for in-memory grouping of variants (e.g., sizes, weights) dynamically
 * on API payloads.
 */
public class VariantGroupingUtil {

    /**
     * Takes a flat array of items, builds variants, and attaches them based on
     * matching group IDs.
     *
     * @param items            Original items to group
     * @param groupIdExtractor Function to get the group ID from an item
     * @param variantMapper    Function to map an item into its minimal variant
     *                         representation
     * @param variantsSetter   Consumer to attach the List of variants back to the
     *                         original item
     */
    public static <T, V> void attachVariants(
            List<T> items,
            Function<T, String> groupIdExtractor,
            Function<T, V> variantMapper,
            BiConsumer<T, List<V>> variantsSetter) {

        Map<String, List<V>> groupMap = new HashMap<>();

        // Pass 1: Build the group map
        for (T item : items) {
            String groupId = groupIdExtractor.apply(item);
            if (groupId == null || groupId.trim().isEmpty()) {
                continue;
            }
            groupMap.computeIfAbsent(groupId, k -> new ArrayList<>()).add(variantMapper.apply(item));
        }

        // Pass 2: Attach to items
        for (T item : items) {
            String groupId = groupIdExtractor.apply(item);
            if (groupId == null || groupId.trim().isEmpty()) {
                variantsSetter.accept(item, new ArrayList<>());
            } else {
                variantsSetter.accept(item, groupMap.getOrDefault(groupId, new ArrayList<>()));
            }
        }
    }
}
