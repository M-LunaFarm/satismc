package kr.seungmin.satisskyfactory.research;

import kr.seungmin.satisskyfactory.model.FactoryIsland;

public final class ResearchService {
    public void addResearch(FactoryIsland island, long amount) {
        island.researchPoints(Math.max(0, island.researchPoints() + amount));
    }
}
