package inside;

import arc.func.Boolf2;
import arc.struct.ObjectMap;
import arc.util.Strings;
import mindustry.mod.ModListing;

public enum SearchCriteria implements Boolf2<String, ModListing> {
    name((s, m) -> m.name.toLowerCase().contains(s.toLowerCase())),
    repo((s, m) -> m.repo.toLowerCase().contains(s.toLowerCase())),
    description((s, m) -> m.description.toLowerCase().contains(s.toLowerCase())),
    desc(description),
    author((s, m) -> m.author.equalsIgnoreCase(s)),
    stars((s, m) -> {
        if (s.startsWith(">")) {
            return m.stars > Strings.parseInt(s.substring(1));
        } else if (s.startsWith("<")) {
            return m.stars < Strings.parseInt(s.substring(1));
        } else if (s.startsWith(">=")) {
            return m.stars >= Strings.parseInt(s.substring(2));
        } else if (s.startsWith("<=")) {
            return m.stars <= Strings.parseInt(s.substring(2));
        }
        return m.stars == Strings.parseInt(s);
    });

    private static ObjectMap<String, SearchCriteria> criteriaMap;

    public final Boolf2<String, ModListing> predicate;

    public static ObjectMap<String, SearchCriteria> getCriteriaMap( ){
        if (criteriaMap != null) {
            return criteriaMap;
        }
        criteriaMap = new ObjectMap<>();
        for (SearchCriteria value : values()) {
            criteriaMap.put(value.name(), value);
        }
        return criteriaMap;
    }

    SearchCriteria(Boolf2<String, ModListing> predicate) {
        this.predicate = predicate;
    }

    @Override
    public boolean get(String s, ModListing modListing) {
        return predicate.get(s, modListing);
    }
}
