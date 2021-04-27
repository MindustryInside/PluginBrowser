package inside;

import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.*;
import mindustry.mod.*;

import java.util.Locale;

import static mindustry.Vars.*;

public class PluginBrowser extends Plugin {

    public GitHubDownloader gitHubDownloader;

    @Override
    public void init() {

        gitHubDownloader = new GitHubDownloader();
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {

        handler.register("plugins", "<search/add/remove/sync/list> [value...]", "Manage, browse plugins.", args -> {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "search" -> {
                    if (args.length != 2) {
                        Log.info("'query' must be set.");
                        return;
                    }

                    String arg = args[1].toLowerCase();
                    gitHubDownloader.getPluginList(seq -> {
                        Seq<PluginListing> result = seq.select(p -> p.name.toLowerCase().contains(arg) ||
                                p.repo.toLowerCase().contains(arg));

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
                case "add" -> {
                    if (args.length != 2) {
                        Log.info("'plugin name' must be set.");
                        return;
                    }

                    String pluginName = args[1];
                    gitHubDownloader.getPluginList(seq -> {
                        PluginListing pluginListing = seq.find(p -> p.name.equals(pluginName));

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
                    Mods.LoadedMod plugin = plugins.find(l -> l.meta.displayName().equals(pluginName));
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

                        Log.info("-- Plugins List Page @/@ --", (page + 1), pages);
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
                    Log.info("  &b&lbplugins search&lc&fi <query...>&fr - &lwSearch plugins by query.");
                    Log.info("  &b&lbplugins add&lc&fi <plugin name...>&fr - &lwImport plugin.");
                    Log.info("  &b&lbplugins remove&lc&fi <plugin name...>&fr - &lwRemove loaded plugin.");
                    Log.info("  &b&lbplugins list&lc&fi [page...]&fr - &lwDisplay all plugins.");
                    Log.info("  &b&lbplugins sync&lc&fi&fr - &lwSync plugins list.");
                }
            }
        });

        handler.removeCommand("mods");

        handler.register("mods", "[search/add/remove/list] [value...]", "Manage, browse mods.", args -> {
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
                                Log.info("Name: @", Strings.stripColors(modListing.name));
                                Log.info("Repository: @", modListing.repo);
                                Log.info("Author: @", modListing.author);
                                Log.info("Description: @", trimText(modListing.description));
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
                            ModListing modListing = seq.find(p -> p.name.equals(modName));

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
                        Mods.LoadedMod mod = modsList.find(l -> l.meta.displayName().equals(modName));
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

                            Log.info("-- Mods List Page @/@ --", (page + 1), pages);
                            for (int i = commandsPerPage * page; i < Math.min(commandsPerPage * (page + 1), seq.size); i++) {
                                ModListing modListing = seq.get(i);
                                Log.info("Name: @", Strings.stripColors(modListing.name));
                                Log.info("Repository: @", modListing.repo);
                                Log.info("Author: @", modListing.author);
                                Log.info("Description: @", trimText(modListing.description));
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
                        Log.info("  &b&lbmods search&lc&fi <query...>&fr - &lwSearch mods by query.");
                        Log.info("  &b&lbmods add&lc&fi <mod name...>&fr - &lwImport mod.");
                        Log.info("  &b&lbmods remove&lc&fi <mod name...>&fr - &lwRemove loaded mod.");
                        Log.info("  &b&lbmods list&lc&fi [page...]&fr - &lwDisplay all mods.");
                        Log.info("  &b&lbmods sync&lc&fi&fr - &lwSync mods list.");
                    }
                }
            }
        });
    }

    public String trimText(String text) {
        if (text == null) return "";
        if (text.contains("\n")) {
            return text.substring(0, text.indexOf("\n"));
        }
        return text;
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
