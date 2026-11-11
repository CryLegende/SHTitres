package org.Cry_Legende.sHTitres.title;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Titres chargés, dans l'ordre de titles.yml. */
public final class TitleRegistry {

    private final Map<String, Title> titles;

    public TitleRegistry(List<Title> titles) {
        Map<String, Title> map = new LinkedHashMap<>();
        for (Title title : titles) {
            map.put(title.id(), title);
        }
        this.titles = Collections.unmodifiableMap(map);
    }

    public Title get(String id) {
        return id == null ? null : titles.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<Title> all() {
        return titles.values();
    }

    public List<Title> inCategory(String category) {
        List<Title> list = new ArrayList<>();
        for (Title title : titles.values()) {
            if (title.category().equals(category)) {
                list.add(title);
            }
        }
        return list;
    }

    public int size() {
        return titles.size();
    }
}
