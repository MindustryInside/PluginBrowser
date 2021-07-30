package inside;

import arc.*;
import arc.files.Fi;
import arc.func.Cons;
import arc.struct.*;
import arc.util.*;
import arc.util.io.Streams;
import arc.util.serialization.Jval;
import mindustry.io.JsonIO;
import mindustry.mod.ModListing;

import java.time.Instant;

import static mindustry.Vars.*;

public class GitHubDownloader{

    public static final long syncIntervalTime = 60 * 60 * 1000; // 1 hour

    public static final String pluginListUrl = "https://raw.githubusercontent.com/MindustryInside/MindustryPlugins/master/plugins.json";

    public static final String modListUrl = "https://raw.githubusercontent.com/Anuken/MindustryMods/master/mods.json";

    public final ObjectSet<String> jvmLangs = ObjectSet.with("Java", "Kotlin", "Groovy");

    @Nullable
    public Seq<PluginListing> pluginList;
    public long lastPluginsTimeSynced;

    @Nullable
    public Seq<ModListing> modList;
    public long lastModsTimeSynced;

    @SuppressWarnings("unchecked")
    public void getPluginList(Cons<Seq<PluginListing>> listener) {
        if (pluginList == null || Time.timeSinceMillis(lastPluginsTimeSynced) >= syncIntervalTime) {
            Http.get(pluginListUrl, response -> {
                String strResult = response.getResultAsString();
                var status = response.getStatus();

                Core.app.post(() -> {
                    if (status != Http.HttpStatus.OK) {
                        showStatus(status);
                    } else {
                        try {
                            pluginList = JsonIO.json.fromJson(Seq.class, PluginListing.class, strResult);
                            pluginList.sortComparing(p -> Instant.parse(p.lastUpdated)).reverse();
                            listener.get(pluginList);
                        } catch(Throwable t) {
                            Log.err(t);
                        }
                    }
                });
            }, this::importFail);
        } else {
            listener.get(pluginList);
        }
    }

    @SuppressWarnings("unchecked")
    public void getModList(Cons<Seq<ModListing>> listener) {
        if (modList == null || Time.timeSinceMillis(lastModsTimeSynced) >= syncIntervalTime) {
            Http.get(modListUrl)
                    .error(this::importFail)
                    .submit(res -> {
                        String strResult = res.getResultAsString();
                        var status = res.getStatus();

                        Core.app.post(() -> {
                            if (status != Http.HttpStatus.OK) {
                                showStatus(status);
                            } else {
                                try {
                                    modList = JsonIO.json.fromJson(Seq.class, ModListing.class, strResult);
                                    modList.sortComparing(m -> Instant.parse(m.lastUpdated)).reverse();
                                    listener.get(modList);
                                } catch(Throwable t) {
                                    Log.err(t);
                                }
                            }
                        });
                    });
        } else {
            listener.get(modList);
        }
    }

    public void handleMod(String repo, Http.HttpResponse result, Runnable runnable) {
        try {
            Fi file = tmpDirectory.child(repo.replace("/", "") + ".zip");
            Streams.copy(result.getResultAsStream(), file.write(false));
            var mod = mods.importMod(file);
            mod.setRepo(repo);
            file.delete();
        } catch(Throwable t) {
            pluginError(t);
        } finally {
            Core.app.post(runnable);
        }
    }

    public void importMod(String repo, boolean hasJava, Runnable runnable) {
        if (hasJava) {
            importJavaMod(repo, runnable);
        } else {
            Http.get(ghApi + "/repos/" + repo)
                    .error(this::importFail)
                    .submit(res -> {
                        if (checkError(res)) {
                            var json = Jval.read(res.getResultAsString());
                            String mainBranch = json.getString("default_branch");
                            String language = json.getString("language", "<none>");

                            // this is a crude heuristic for class mods; only required for direct github import
                            // TODO make a more reliable way to distinguish java mod repos
                            if (jvmLangs.contains(language)) {
                                importJavaMod(repo, runnable);
                            } else {
                                importBranch(mainBranch, repo, this::showStatus, runnable);
                            }
                        }
                    });
        }
    }

    public void importJavaMod(String repo, Runnable runnable) {
        // grab latest release
        Http.get(ghApi + "/repos/" + repo + "/releases/latest")
                .error(this::importFail)
                .submit(res -> {
                    if (checkError(res)) {
                        var json = Jval.read(res.getResultAsString());
                        var asset = json.get("assets").asArray().find(j -> j.getString("name").endsWith(".jar"));
                        if (asset != null) {
                            // grab actual file
                            String url = asset.getString("browser_download_url");
                            Http.get(url, result -> {
                                if (checkError(result)) {
                                    handleMod(repo, result, runnable);
                                }
                            }, this::importFail);
                        } else {
                            throw new ArcRuntimeException("No JAR file found in releases. Make sure you have a valid jar file in the mod's latest Github Release.");
                        }
                    }
                });
    }

    public void importBranch(String branch, String repo, Cons<Http.HttpStatus> err, Runnable runnable) {
        Http.get(ghApi + "/repos/" + repo + "/zipball/" + branch)
                .error(this::importFail)
                .submit(loc -> {
                    if (loc.getStatus() == Http.HttpStatus.OK) {
                        if (loc.getHeader("Location") != null) {
                            Http.get(loc.getHeader("Location"))
                                    .error(this::importFail)
                                    .submit(res -> {
                                        if (res.getStatus() != Http.HttpStatus.OK) {
                                            err.get(res.getStatus());
                                        } else {
                                            handleMod(repo, res, runnable);
                                        }
                                    });
                        } else {
                            handleMod(repo, loc, runnable);
                        }
                    } else {
                        err.get(loc.getStatus());
                    }
                });
    }

    private boolean checkError(Http.HttpResponse res) {
        if (res.getStatus() == Http.HttpStatus.OK) {
            return true;
        }
        showStatus(res.getStatus());
        return false;
    }

    private void showStatus(Http.HttpStatus status) {
        Core.app.post(() -> Log.err("Connection error: @", Strings.capitalize(status.toString().toLowerCase())));
    }

    private void importFail(Throwable t) {
        Core.app.post(() -> pluginError(t));
    }

    private void pluginError(Throwable error) {
        if (Strings.getCauses(error).contains(t -> t.getMessage() != null &&
                (t.getMessage().contains("trust anchor") || t.getMessage().contains("SSL") ||
                t.getMessage().contains("protocol")))) {
            Log.err("Your device does not support this feature.");
        } else {
            Log.err(error);
        }
    }
}
