package inside;

import arc.*;
import arc.files.Fi;
import arc.func.*;
import arc.math.Mathf;
import arc.struct.*;
import arc.util.*;
import arc.util.io.Streams;
import arc.util.serialization.Jval;
import mindustry.io.JsonIO;
import mindustry.mod.*;

import java.text.SimpleDateFormat;
import java.util.*;

import static mindustry.Vars.*;

public class PluginBrowser extends Plugin {

    public static final String pluginListUrl = "https://raw.githubusercontent.com/MindustryInside/MindustryPlugins/main/plugins.json";

    public final ObjectSet<String> jvmLangs = ObjectSet.with("Java", "Kotlin", "Groovy");

    @Nullable
    public Seq<PluginListing> pluginList;

    @Override
    public void registerServerCommands(CommandHandler handler) {

        handler.register("plugins", "<add/remove/sync/list> [value...]", "TODO", args -> {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "add" -> {
                    if(args.length != 2){
                        Log.info("'Plugin name' must be set.");
                        return;
                    }

                    String pluginName = args[1];
                    getPluginList(seq -> {
                        PluginListing pluginListing = pluginList.find(p -> p.name.equals(pluginName));

                        if(pluginListing == null){
                            Log.info("Plugin by name '@' not found.", pluginName);
                            return;
                        }

                        githubImportPlugin(pluginListing, () -> Log.info("Plugin imported. Restart server"));
                    });
                }
                case "remove" -> {
                    if(args.length != 2){
                        Log.info("'Plugin name' must be set.");
                        return;
                    }

                    String pluginName = args[1];
                    Mods.LoadedMod plugin = mods.list().find(l -> l.main instanceof Plugin && l.meta.displayName().equals(pluginName));
                    if(plugin == null){
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
                    getPluginList(seq -> {
                        int page = args.length > 1 ? Strings.parseInt(args[1]) : 1;
                        int pages = Mathf.ceil((float)seq.size / commandsPerPage);

                        page--;

                        if(page >= pages || page < 0){
                            Log.info("'page' must be a number between 1 and @.", pages);
                            return;
                        }

                        Log.info("-- Plugins List Page @/@ --", (page + 1), pages);
                        for(int i = commandsPerPage * page; i < Math.min(commandsPerPage * (page + 1), seq.size); i++){
                            PluginListing pluginListing = seq.get(i);
                            Log.info("Name: @", Strings.stripColors(pluginListing.name));
                            Log.info("Repository: @", pluginListing.repo);
                            Log.info("Author: @", pluginListing.author);
                            Log.info("Description: @", pluginListing.description);
                            Log.info("Java: @", pluginListing.hasJava ? "yes" : "no");
                            Log.info("Last Update: @", pluginListing.lastUpdated);
                            Log.info("Stars: @", pluginListing.stars);
                            if(i + 1 != Math.min(commandsPerPage * (page + 1), seq.size)){
                                Log.info("--------------------");
                            }
                        }
                    });
                }
                case "sync" -> {
                    getPluginList(seq -> Log.info("Fetched @ plugins", seq.size), true);
                }
                default -> {
                    Log.info("Unknown action.");
                }
            }
        });
    }

    public void getPluginList(Cons<Seq<PluginListing>> listener){
        getPluginList(listener, false);
    }

    @SuppressWarnings("unchecked")
    public void getPluginList(Cons<Seq<PluginListing>> listener, boolean sync){
        if(pluginList == null || sync){
            Core.net.httpGet(pluginListUrl, response -> {
                String strResult = response.getResultAsString();
                var status = response.getStatus();

                Core.app.post(() -> {
                    if(status != Net.HttpStatus.OK){
                        showStatus(status);
                    }else{
                        try{
                            pluginList = JsonIO.json.fromJson(Seq.class, PluginListing.class, strResult);

                            var format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
                            Func<String, Date> parser = text -> {
                                try{
                                    return format.parse(text);
                                }catch(Exception e){
                                    throw new RuntimeException(e);
                                }
                            };

                            pluginList.sortComparing(m -> parser.get(m.lastUpdated)).reverse();
                            listener.get(pluginList);
                        }catch(Exception e){
                            Log.err(e);
                        }
                    }
                });
            }, error -> Core.app.post(() -> pluginError(error)));
        }else{
            listener.get(pluginList);
        }
    }

    public void githubImportPlugin(PluginListing pluginListing, Runnable runnable){
        if(pluginListing.hasJava){
            githubImportJavaPlugin(pluginListing.repo, runnable);
        }else{
            Core.net.httpGet(ghApi + "/repos/" + pluginListing.repo, res -> {
                if(checkError(res)){
                    var json = Jval.read(res.getResultAsString());
                    String mainBranch = json.getString("default_branch");
                    String language = json.getString("language", "<none>");

                    // this is a crude heuristic for class mods; only required for direct github import
                    // TODO make a more reliable way to distinguish java mod repos
                    if(jvmLangs.contains(language)){
                        githubImportJavaPlugin(pluginListing.repo, runnable);
                    }else{
                        githubImportBranch(mainBranch, pluginListing.repo, this::showStatus, runnable);
                    }
                }
            }, this::importFail);
        }
    }

    public void githubImportJavaPlugin(String repo, Runnable runnable){
        // grab latest release
        Core.net.httpGet(ghApi + "/repos/" + repo + "/releases/latest", res -> {
            if(checkError(res)){
                var json = Jval.read(res.getResultAsString());
                var asset = json.get("assets").asArray().find(j -> j.getString("name").endsWith(".jar"));
                if(asset != null){
                    // grab actual file
                    String url = asset.getString("browser_download_url");
                    Core.net.httpGet(url, result -> {
                        if(checkError(result)){
                            handleMod(repo, result, runnable);
                        }
                    }, this::importFail);
                }else{
                    throw new ArcRuntimeException("No JAR file found in releases. Make sure you have a valid jar file in the mod's latest Github Release.");
                }
            }
        }, this::importFail);
    }

    public void handleMod(String repo, Net.HttpResponse result, Runnable runnable){
        try{
            Fi file = tmpDirectory.child(repo.replace("/", "") + ".zip");
            Streams.copy(result.getResultAsStream(), file.write(false));
            var mod = mods.importMod(file);
            mod.setRepo(repo);
            file.delete();
        }catch(Throwable e){
            pluginError(e);
        }finally{
            runnable.run();
        }
    }

    public boolean checkError(Net.HttpResponse res){
        if(res.getStatus() == Net.HttpStatus.OK){
            return true;
        }else{
            showStatus(res.getStatus());
            return false;
        }
    }

    private void showStatus(Net.HttpStatus status){
        Core.app.post(() -> Log.err("Connection error: @", Strings.capitalize(status.toString().toLowerCase())));
    }

    public void importFail(Throwable t){
        Core.app.post(() -> pluginError(t));
    }

    public void pluginError(Throwable error){
        if(Strings.getCauses(error).contains(t -> t.getMessage() != null &&
                (t.getMessage().contains("trust anchor") || t.getMessage().contains("SSL") || t.getMessage().contains("protocol")))){
            Log.err("Your device does not support this feature.");
        }else{
            Log.err(error);
        }
    }

    public void githubImportBranch(String branch, String repo, Cons<Net.HttpStatus> err, Runnable runnable){
        Core.net.httpGet(ghApi + "/repos/" + repo + "/zipball/" + branch, loc -> {
            if(loc.getStatus() == Net.HttpStatus.OK){
                if(loc.getHeader("Location") != null){
                    Core.net.httpGet(loc.getHeader("Location"), result -> {
                        if(result.getStatus() != Net.HttpStatus.OK){
                            err.get(result.getStatus());
                        }else{
                            handleMod(repo, result, runnable);
                        }
                    }, this::importFail);
                }else{
                    handleMod(repo, loc, runnable);
                }
            }else{
                err.get(loc.getStatus());
            }
        }, this::importFail);
    }
}
