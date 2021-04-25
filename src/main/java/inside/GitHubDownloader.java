package inside;

import arc.*;
import arc.files.Fi;
import arc.func.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.Streams;
import arc.util.serialization.Jval;
import mindustry.io.JsonIO;
import mindustry.mod.ModListing;

import java.text.SimpleDateFormat;
import java.util.Date;

import static mindustry.Vars.*;

public class GitHubDownloader{

    public static final String pluginListUrl = "https://raw.githubusercontent.com/MindustryInside/MindustryPlugins/main/plugins.json";

    public static final String modListUrl = "https://raw.githubusercontent.com/Anuken/MindustryMods/master/mods.json";

    public final ObjectSet<String> jvmLangs = ObjectSet.with("Java", "Kotlin", "Groovy");

    @Nullable
    public Seq<PluginListing> pluginList;

    @Nullable
    public Seq<ModListing> modList;

    @SuppressWarnings("unchecked")
    public void getPluginList(Cons<Seq<PluginListing>> listener){
        if(pluginList == null){
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

    @SuppressWarnings("unchecked")
    public void getModList(Cons<Seq<ModListing>> listener){
        if(modList == null){
            Core.net.httpGet(modListUrl, response -> {
                String strResult = response.getResultAsString();
                var status = response.getStatus();

                Core.app.post(() -> {
                    if(status != Net.HttpStatus.OK){
                        showStatus(status);
                    }else{
                        try{
                            modList = JsonIO.json.fromJson(Seq.class, ModListing.class, strResult);

                            var format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
                            Func<String, Date> parser = text -> {
                                try{
                                    return format.parse(text);
                                }catch(Exception e){
                                    throw new RuntimeException(e);
                                }
                            };

                            modList.sortComparing(m -> parser.get(m.lastUpdated)).reverse();
                            listener.get(modList);
                        }catch(Exception e){
                            Log.err(e);
                        }
                    }
                });
            }, error -> Core.app.post(() -> pluginError(error)));
        }else{
            listener.get(modList);
        }
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

    public void importPlugin(PluginListing pluginListing, Runnable runnable){
        if(pluginListing.hasJava){
            importJavaPlugin(pluginListing.repo, runnable);
        }else{
            Core.net.httpGet(ghApi + "/repos/" + pluginListing.repo, res -> {
                if(checkError(res)){
                    var json = Jval.read(res.getResultAsString());
                    String mainBranch = json.getString("default_branch");
                    String language = json.getString("language", "<none>");

                    // this is a crude heuristic for class mods; only required for direct github import
                    // TODO make a more reliable way to distinguish java mod repos
                    if(jvmLangs.contains(language)){
                        importJavaPlugin(pluginListing.repo, runnable);
                    }else{
                        importBranch(mainBranch, pluginListing.repo, this::showStatus, runnable);
                    }
                }
            }, this::importFail);
        }
    }

    public void importJavaPlugin(String repo, Runnable runnable){
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

    public void importBranch(String branch, String repo, Cons<Net.HttpStatus> err, Runnable runnable){
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

    private boolean checkError(Net.HttpResponse res){
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

    private void importFail(Throwable t){
        Core.app.post(() -> pluginError(t));
    }

    private void pluginError(Throwable error){
        if(Strings.getCauses(error).contains(t -> t.getMessage() != null &&
                (t.getMessage().contains("trust anchor") || t.getMessage().contains("SSL") || t.getMessage().contains("protocol")))){
            Log.err("Your device does not support this feature.");
        }else{
            Log.err(error);
        }
    }
}
