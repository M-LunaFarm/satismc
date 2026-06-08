package kr.example.satisskyfactory.recipe;

import java.util.Map;

public record RecipeDefinition(String id, String machineType, Map<String, Long> input, Map<String, Long> output, double power) {
}
