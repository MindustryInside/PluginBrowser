package inside;

import arc.math.Mathf;
import arc.util.*;
import mindustry.mod.*;

import java.util.Locale;

import static mindustry.Vars.mods;

public class PluginBrowser extends Plugin {

    public GitHubDownloader gitHubDownloader;

    @Override
    public void init() {

        gitHubDownloader = new GitHubDownloader();
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {

        handler.register("plugins", "<add/remove/sync/list> [value...]", "TODO", args -> {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "add" -> {
                    if (args.length != 2) {
                        Log.info("'Plugin name' must be set.");
                        return;
                    }

                    String pluginName = args[1];
                    gitHubDownloader.getPluginList(seq -> {
                        PluginListing pluginListing = seq.find(p -> p.name.equals(pluginName));

                        if (pluginListing == null) {
                            Log.info("Plugin by name '@' not found.", pluginName);
                            return;
                        }

                        gitHubDownloader.importPlugin(pluginListing, () -> Log.info("Plugin imported. Restart server"));
                    });
                }
                case "remove" -> {
                    if (args.length != 2) {
                        Log.info("'Plugin name' must be set.");
                        return;
                    }

                    String pluginName = args[1];
                    Mods.LoadedMod plugin = mods.list().find(l -> l.main instanceof Plugin && l.meta.displayName().equals(pluginName));
                    if (plugin == null) {
                        Log.info("Plugin by name '@' not found.", pluginName);
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
                            Log.info("Name: @", Strings.stripColors(pluginListing.name));
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
                    gitHubDownloader.getPluginList(seq -> Log.info("Fetched @ plugins", seq.size));
                }
                default -> {
                    Log.info("Unknown action.");
                }
            }
        });
    }
}
