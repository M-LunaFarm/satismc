package kr.seungmin.satisskyfactory.item;

import org.bukkit.Material;

import java.util.List;

public record ItemDefinition(
        String id,
        Material material,
        String displayName,
        int customModelData,
        boolean virtualOnly,
        long basePrice,
        boolean qualityEnabled,
        List<String> tags
) {
}
