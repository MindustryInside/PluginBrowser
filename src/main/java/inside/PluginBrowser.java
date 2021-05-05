package inside;

import arc.func.Boolf2;
import arc.math.Mathf;
import arc.struct.*;
import arc.util.*;
import mindustry.mod.*;

import java.util.Locale;

import static mindustry.Vars.*;

public class PluginBrowser extends Plugin {

    public GitHubDownloader gitHubDownloader;

    public ObjectMap<String, Boolf2<String, PluginListing>> pluginSearchCriteria = new ObjectMap<>();

    public ObjectMap<String, Boolf2<String, ModListing>> modSearchCriteria = new ObjectMap<>();

    @Override
    public void init() {

        gitHubDownloader = new GitHubDownloader();

        pluginSearchCriteria.put("name", (s, p) -> p.name.toLowerCase().contains(s.toLowerCase()));
        pluginSearchCriteria.put("repo", (s, p) -> p.repo.toLowerCase().contains(s.toLowerCase()));
        pluginSearchCriteria.put("author", (s, p) -> p.author.equalsIgnoreCase(s));
        Boolf2<String, PluginListing> descPluginPred = (s, p) -> p.description.toLowerCase().contains(s.toLowerCase());
        pluginSearchCriteria.put("description", descPluginPred);
        pluginSearchCriteria.put("desc", descPluginPred);
        pluginSearchCriteria.put("stars", (s, p) -> {
            if (s.startsWith(">")) {
                return p.stars > Strings.parseInt(s.substring(1));
            } else if (s.startsWith("<")) {
                return p.stars < Strings.parseInt(s.substring(1));
            } else if (s.startsWith(">=")) {
                return p.stars >= Strings.parseInt(s.substring(2));
            } else if (s.startsWith("<=")) {
                return p.stars <= Strings.parseInt(s.substring(2));
            }
            return p.stars == Strings.parseInt(s);
        });

        modSearchCriteria.put("name", (s, m) -> m.name.toLowerCase().contains(s.toLowerCase()));
        modSearchCriteria.put("repo", (s, m) -> m.repo.toLowerCase().contains(s.toLowerCase()));
        modSearchCriteria.put("author", (s, m) -> m.author.equalsIgnoreCase(s));
        Boolf2<String, ModListing> descModPred = (s, m) -> m.description.toLowerCase().contains(s.toLowerCase());
        modSearchCriteria.put("description", descModPred);
        modSearchCriteria.put("desc", descModPred);
        modSearchCriteria.put("stars", (s, m) -> {
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
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {

        handler.register("plugins", "<search/search-by/add/remove/sync/list> [value...]", "Manage, browse plugins.", args -> {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "search" -> {
                    if (args.length != 2) {
                        Log.info("'query' must be set.");
                        return;
                    }

                    String arg = args[1].toLowerCase();
                    gitHubDownloader.getPluginList(seq -> {
                        Seq<PluginListing> result = seq.select(p -> p.name.toLowerCase().contains(arg));

                        if (result.size > 1){
                            Log.info("Plugins found: @", result.size);
                            int i = 0;
                            for (PluginListing pluginListing : result) {
                                Log.info("- [@] '@' / '@'", i++, pluginListing.name, pluginListing.repo);
                            }
                        } else if (result.size == 1) {
                            PluginListing pluginListing = result.first();
                            Log.info("Name: @", pluginListing.name);
                            Log.info("Repository: @", pluginListing.repo);
                            Log.info("Author: @", pluginListing.author);
                            Log.info("Description: @", pluginListing.description);
                            Log.info("Java: @", pluginListing.hasJava ? "yes" : "no");
                            Log.info("Last Update: @", pluginListing.lastUpdated);
                            Log.info("Stars: @", pluginListing.stars);
                        } else {
                            Log.info("No plugins with that query could be found.");
                        }
                    });
                }
                case "search-by" -> {
                    if (args.length != 2) {
                        Log.info("'criteria' must be set.");
                        return;
                    }

                    if (args[1].equalsIgnoreCase("help")) {
                        Log.info("Available Criteria:");
                        Log.info("  &b&lbname&lc&fi <plugin name...>&fr - &lwSearch plugins by name.");
                        Log.info("  &b&lbrepo&lc&fi <plugin repo...>&fr - &lwSearch plugins by name.");
                        Log.info("  &b&lbauthor&lc&fi <plugin author...>&fr - &lwSearch plugins by author.");
                        Log.info("  &b&lbdescription&lc&fi <plugin description...>&fr - &lwSearch plugins by author.");
                        Log.info("  &b&lbstars&lc&fi <condition>&fr - &lwSearch plugins by stars. Format: >1 / <1 / >= 1 / <= 1 / 1");
                        Log.info("Usage Examples:");
                        Log.info("  &b&lbplugins search-by &lc&finame,repo Mindustry&fr - &lwSearch plugins by name and repo 'Mindustry'");
                        Log.info("  &b&lbplugins search-by &lc&finame Mindustry stars >3&fr - &lwSearch plugins by name 'Mindustry' and stars >3");
                        Log.info("  &b&lbplugins search-by &lc&finame 'Plugin Browser'&fr - &lwSearch plugins by name 'Mindustry Browser'");
                        return;
                    }

                    gitHubDownloader.getPluginList(seq -> {
                        StringMap params = parseCriteria(args[1], pluginSearchCriteria);
                        Log.debug("params: @", params);
                        if (params.isEmpty()) {
                            Log.info("Incorrect criteria. Type '@' for help", "plugins search-by help");
                            return;
                        }

                        Seq<PluginListing> result = seq.select(p -> {
                            for(var param : params){
                                return pluginSearchCriteria.get(param.key).get(param.value, p);
                            }
                            return false;
                        });

                        if (result.size > 1){
                            Log.info("Plugins found: @", result.size);
                            int i = 0;
                            for (PluginListing pluginListing : result) {
                                Log.info("- [@] '@' / '@'", i++, pluginListing.name, pluginListing.repo);
                            }
                        } else if (result.size == 1) {
                            PluginListing pluginListing = result.first();
                            Log.info("Name: @", pluginListing.name);
                            Log.info("Repository: @", pluginListing.repo);
                            Log.info("Author: @", pluginListing.author);
                            Log.info("Description: @", pluginListing.description);
                            Log.info("Java: @", pluginListing.hasJava ? "yes" : "no");
                            Log.info("Last Update: @", pluginListing.lastUpdated);
                            Log.info("Stars: @", pluginListing.stars);
                        } else {
                            Log.info("No plugins with that criteria could be found.");
                        }
                    });
                }
                case "add" -> {
                    if (args.length != 2) {
                        Log.info("'plugin name' must be set.");
                        return;
                    }

                    String pluginName = args[1];
                    gitHubDownloader.getPluginList(seq -> {
                        PluginListing pluginListing = seq.find(p -> p.name.equalsIgnoreCase(pluginName));

                        if (pluginListing == null) {
                            String suggest = findClosest(seq.map(p -> p.name), pluginName, 3);
                            if (suggest != null) {
                                Log.info("No plugin with name '@' found. Did you mean '@'?", pluginName, suggest);
                            } else {
                                Log.info("No plugin with name '@' found.", pluginName);
                            }
                            return;
                        }

                        gitHubDownloader.importMod(pluginListing.repo, pluginListing.hasJava, () -> Log.info("Plugin imported. Restart server"));
                    });
                }
                case "remove" -> {
                    if (args.length != 2) {
                        Log.info("'plugin name' must be set.");
                        return;
                    }

                    String pluginName = args[1];
                    Seq<Mods.LoadedMod> plugins = mods.list().select(l -> l.main instanceof Plugin);
                    Mods.LoadedMod plugin = plugins.find(l -> l.meta.displayName().equalsIgnoreCase(pluginName));
                    if (plugin == null) {
                        String suggest = findClosest(plugins.map(l -> l.meta.displayName()), pluginName, 3);
                        if (suggest != null) {
                            Log.info("No plugin with name '@' found. Did you mean '@'?", pluginName, suggest);
                        } else {
                            Log.info("No plugin with name '@' found.", pluginName);
                        }
                        return;
                    }

                    mods.removeMod(plugin);
                    Log.info("Plugin removed. Restart server");
                }
                case "list" -> {
                    if(args.length > 1 && !Strings.canParseInt(args[1])){
                        Log.info("'page' must be a number.");
                        return;
                    }

                    int commandsPerPage = 3;
                    gitHubDownloader.getPluginList(seq -> {
                        int page = args.length > 1 ? Strings.parseInt(args[1]) : 1;
                        int pages = Mathf.ceil((float)seq.size / commandsPerPage);

                        page--;

                        if (page >= pages || page < 0) {
                            Log.info("'page' must be a number between 1 and @.", pages);
                            return;
                        }

                        Log.info("-- Plugins List Page @/@ --", page + 1, pages);
                        for (int i = commandsPerPage * page; i < Math.min(commandsPerPage * (page + 1), seq.size); i++) {
                            PluginListing pluginListing = seq.get(i);
                            Log.info("Name: @", pluginListing.name);
                            Log.info("Repository: @", pluginListing.repo);
                            Log.info("Author: @", pluginListing.author);
                            Log.info("Description: @", pluginListing.description);
                            Log.info("Java: @", pluginListing.hasJava ? "yes" : "no");
                            Log.info("Last Update: @", pluginListing.lastUpdated);
                            Log.info("Stars: @", pluginListing.stars);
                            if (i + 1 != Math.min(commandsPerPage * (page + 1), seq.size)) {
                                Log.info("--------------------");
                            }
                        }
                    });
                }
                case "sync" -> {
                    gitHubDownloader.pluginList = null;
                    gitHubDownloader.getPluginList(seq -> Log.info("Fetched @ plugins.", seq.size));
                }
                default -> {
                    Log.info("Unknown action. Available actions:");
                    Log.info("  &b&lbplugins search &lc&fi<query...>&fr - &lwSearch plugins by query.");
                    Log.info("  &b&lbplugins search-by &lc&fi<criteria/help> <value...>&fr - &lwSearch plugins by criteria.");
                    Log.info("  &b&lbplugins add &lc&fi<plugin name...>&fr - &lwImport plugin.");
                    Log.info("  &b&lbplugins remove &lc&fi<plugin name...>&fr - &lwRemove loaded plugin.");
                    Log.info("  &b&lbplugins list &lc&fi[page...]&fr - &lwDisplay all plugins.");
                    Log.info("  &b&lbplugins sync &lc&fi&fr- &lwSync plugins list.");
                }
            }
        });

        handler.removeCommand("mods");

        handler.register("mods", "[search/search-by/add/remove/list] [value...]", "Manage, browse mods.", args -> {
            // old command
            if(args.length == 0){
                if(!mods.list().isEmpty()){
                    Log.info("Mods:");
                    for(Mods.LoadedMod mod : mods.list()){
                        Log.info("  @ &fi@", mod.meta.displayName(), mod.meta.version);
                    }
                }else{
                    Log.info("No mods found.");
                }
                Log.info("Mod directory: &fi@", modDirectory.file().getAbsoluteFile().toString());
            }else{
                switch (args[0].toLowerCase(Locale.ROOT)) {
                    case "search" -> {
                        if (args.length != 2) {
                            Log.info("'query' must be set.");
                            return;
                        }

                        String arg = args[1].toLowerCase();
                        gitHubDownloader.getModList(seq -> {
                            Seq<ModListing> result = seq.select(p -> p.name.toLowerCase().contains(arg) ||
                                    p.repo.toLowerCase().contains(arg));

                            if (result.size > 1){
                                Log.info("Mods found: @", result.size);
                                int i = 0;
                                for (ModListing modListing : result) {
                                    Log.info("- [@] '@' / '@'", i++, modListing.name, modListing.repo);
                                }
                            } else if (result.size == 1) {
                                ModListing modListing = result.first();
                                Log.info("Name: @", stripText(modListing.name));
                                Log.info("Repository: @", modListing.repo);
                                Log.info("Author: @", stripText(modListing.author));
                                Log.info("Description: @", stripText(modListing.description));
                                Log.info("Min Game Version: @", modListing.minGameVersion);
                                Log.info("Has Java: @", modListing.hasJava ? "yes" : "no");
                                Log.info("Has Scripts: @", modListing.hasScripts ? "yes" : "no");
                                Log.info("Last Update: @", modListing.lastUpdated);
                                Log.info("Stars: @", modListing.stars);
                            } else {
                                Log.info("No mods with that query could be found.");
                            }
                        });
                    }
                    case "search-by" -> {
                        if (args.length != 2) {
                            Log.info("'criteria' must be set.");
                            return;
                        }

                        if (args[1].equalsIgnoreCase("help")) {
                            Log.info("Available Criteria:");
                            Log.info("  &b&lbname &lc&fi<mod name...>&fr - &lwSearch mods by name.");
                            Log.info("  &b&lbrepo &lc&fi<mod repo...>&fr - &lwSearch mods by name.");
                            Log.info("  &b&lbauthor &lc&fi<mod author...>&fr - &lwSearch mods by author.");
                            Log.info("  &b&lbdescription &lc&fi<mod description...>&fr - &lwSearch mods by description.");
                            Log.info("  &b&lbstars &lc&fi<condition>&fr - &lwSearch mods by stars. Format: >1 / <1 / >= 1 / <= 1 / 1");
                            Log.info("Usage Examples:");
                            Log.info("  &b&lbmods search-by &lc&finame,repo Mindustry&fr - &lwSearch mods by name and repo 'Mindustry'");
                            Log.info("  &b&lbmods search-by &lc&finame Mindustry stars >3&fr - &lwSearch mods by name 'Mindustry' and stars >3");
                            Log.info("  &b&lbmods search-by &lc&finame 'Mindustry Mod'&fr - &lwSearch mods by name 'Mindustry mod'");
                            return;
                        }

                        gitHubDownloader.getModList(seq -> {
                            StringMap params = parseCriteria(args[1], modSearchCriteria);
                            Log.debug("params: @", params);
                            if (params.isEmpty()) {
                                Log.info("Incorrect criteria. Type '@' for help", "mods search-by help");
                                return;
                            }

                            Seq<ModListing> result = seq.select(s -> {
                                for (var param : params) {
                                    return modSearchCriteria.get(param.key).get(param.value, s);
                                }
                                return false;
                            });

                            if (result.size > 1){
                                Log.info("Mods found: @", result.size);
                                int i = 0;
                                for (ModListing modListing : result) {
                                    Log.info("- [@] '@' / '@'", i++, modListing.name, modListing.repo);
                                }
                            } else if (result.size == 1) {
                                ModListing modListing = result.first();
                                Log.info("Name: @", stripText(modListing.name));
                                Log.info("Repository: @", modListing.repo);
                                Log.info("Author: @", stripText(modListing.author));
                                Log.info("Description: @", stripText(modListing.description));
                                Log.info("Min Game Version: @", modListing.minGameVersion);
                                Log.info("Has Java: @", modListing.hasJava ? "yes" : "no");
                                Log.info("Has Scripts: @", modListing.hasScripts ? "yes" : "no");
                                Log.info("Last Update: @", modListing.lastUpdated);
                                Log.info("Stars: @", modListing.stars);
                            } else {
                                Log.info("No mods with that query could be found.");
                            }
                        });
                    }
                    case "add" -> {
                        if (args.length != 2) {
                            Log.info("'mod name' must be set.");
                            return;
                        }

                        String modName = args[1];
                        gitHubDownloader.getModList(seq -> {
                            ModListing modListing = seq.find(p -> p.name.equalsIgnoreCase(modName));

                            if (modListing == null) {
                                String suggest = findClosest(seq.map(l -> l.name), modName, 3);
                                if (suggest != null) {
                                    Log.info("No mod with name '@' found. Did you mean '@'?", modName, suggest);
                                } else {
                                    Log.info("No mod with name '@' found.", modName);
                                }
                                return;
                            }

                            gitHubDownloader.importMod(modListing.repo, modListing.hasJava, () -> Log.info("Mod imported. Restart server"));
                        });
                    }
                    case "remove" -> {
                        if (args.length != 2) {
                            Log.info("'mod name' must be set.");
                            return;
                        }

                        String modName = args[1];
                        Seq<Mods.LoadedMod> modsList = mods.list().select(l -> !(l.main instanceof Plugin));
                        Mods.LoadedMod mod = modsList.find(l -> l.meta.displayName().equalsIgnoreCase(modName));
                        if (mod == null) {
                            String suggest = findClosest(modsList.map(l -> l.meta.displayName()), modName, 3);
                            if (suggest != null) {
                                Log.info("No mod with name '@' found. Did you mean '@'?", modName, suggest);
                            } else {
                                Log.info("No mod with name '@' found.", modName);
                            }
                            return;
                        }

                        mods.removeMod(mod);
                        Log.info("Mod removed. Restart server");
                    }
                    case "list" -> {
                        if(args.length > 1 && !Strings.canParseInt(args[1])){
                            Log.info("'page' must be a number.");
                            return;
                        }

                        int commandsPerPage = 3;
                        gitHubDownloader.getModList(seq -> {
                            int page = args.length > 1 ? Strings.parseInt(args[1]) : 1;
                            int pages = Mathf.ceil((float)seq.size / commandsPerPage);

                            page--;

                            if (page >= pages || page < 0) {
                                Log.info("'page' must be a number between 1 and @.", pages);
                                return;
                            }

                            Log.info("-- Mods List Page @/@ --", page + 1, pages);
                            for (int i = commandsPerPage * page; i < Math.min(commandsPerPage * (page + 1), seq.size); i++) {
                                ModListing modListing = seq.get(i);
                                Log.info("Name: @", stripText(modListing.name));
                                Log.info("Repository: @", modListing.repo);
                                Log.info("Author: @", stripText(modListing.author));
                                Log.info("Description: @", stripText(modListing.description));
                                Log.info("Min Game Version: @", modListing.minGameVersion);
                                Log.info("Has Java: @", modListing.hasJava ? "yes" : "no");
                                Log.info("Has Scripts: @", modListing.hasScripts ? "yes" : "no");
                                Log.info("Last Update: @", modListing.lastUpdated);
                                Log.info("Stars: @", modListing.stars);
                                if (i + 1 != Math.min(commandsPerPage * (page + 1), seq.size)) {
                                    Log.info("--------------------");
                                }
                            }
                        });
                    }
                    case "sync" -> {
                        gitHubDownloader.modList = null;
                        gitHubDownloader.getModList(seq -> Log.info("Fetched @ mods.", seq.size));
                    }
                    default -> {
                        Log.info("Unknown action. Available actions:");
                        Log.info("  &b&lbmods search &lc&fi<query...>&fr - &lwSearch mods by query.");
                        Log.info("  &b&lbmods search-by &lc&fi<criteria/help> <value...>&fr - &lwSearch mods by criteria.");
                        Log.info("  &b&lbmods add &lc&fi<mod name...>&fr - &lwImport mod.");
                        Log.info("  &b&lbmods remove &lc&fi<mod name...>&fr - &lwRemove loaded mod.");
                        Log.info("  &b&lbmods list &lc&fi[page...]&fr - &lwDisplay all mods.");
                        Log.info("  &b&lbmods sync &lc&fi&fr- &lwSync mods list.");
                    }
                }
            }
        });
    }

    public <T> StringMap parseCriteria(String text, ObjectMap<String, Boolf2<String, T>> map) {
        Seq<String> split = Seq.with(text.split("[\\s+,]")).filter(s -> !s.isEmpty());
        StringMap criteria = new StringMap();
        for (int i = 0; i < split.size; i++) {
            String key = split.get(i);
            String value = i + 1 != split.size ? split.get(i + 1) : null;

            // quoted string in priority
            int start = text.indexOf('\'', text.indexOf(key));
            int end = text.indexOf('\'', start + 1);
            if (start != -1 && end != -1) {
                value = text.substring(start + 1, end);
            } else {
                for (int i1 = i; i1 < split.size; i1++) {
                    String current = split.get(i1);
                    if (!map.containsKey(current.toLowerCase())) {
                        value = current;
                        int endStr = -1;
                        for (int i2 = i1; i2 < split.size; i2++) { // end of non-criteria string
                            String s1 = split.get(i2);
                            if (map.containsKey(s1.toLowerCase())) {
                                endStr = text.indexOf(s1, i2);
                                break;
                            }
                        }

                        value = text.substring(text.indexOf(value, key.length()), endStr != -1 ? endStr : text.length()).trim();
                        break;
                    }
                }
            }

            key = key.toLowerCase();
            if (map.containsKey(key) && value != null) {
                criteria.put(key, value);
            }
        }
        return criteria;
    }

    public String stripText(String text) {
        return Strings.stripColors(text).replaceAll("\\s+", " ").trim();
    }

    @Nullable
    public String findClosest(Seq<String> all, String wrong, int max) {
        int min = 0;
        String closest = null;
        for (String s : all) {
            int dst = Strings.levenshtein(s, wrong);
            if (dst < max && (closest == null || dst < min)) {
                min = dst;
                closest = s;
            }
        }
        return closest;
    }
}
