package inside;

import arc.*;
import arc.files.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.scene.ui.Dialog;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import arc.util.serialization.*;
import mindustry.core.Version;
import mindustry.ctype.*;
import mindustry.game.EventType;
import mindustry.gen.Icon;
import mindustry.graphics.*;
import mindustry.mod.*;
import mindustry.type.*;
import mindustry.ui.Styles;

import java.io.*;
import java.util.Locale;

import static mindustry.Vars.*;

public class HeadlessMods extends Mods{
    private Json json = new Json();
    @Nullable
    private Scripts scripts;
    private ContentParser parser = new ContentParser();
    private ObjectMap<String, Seq<Fi>> bundles = new ObjectMap<>();
    private ObjectSet<String> specialFolders = ObjectSet.with("bundles", "sprites", "sprites-override");

    private int totalSprites;
    private MultiPacker packer;

    private Seq<Mods.LoadedMod> mods = new Seq<>();
    private ObjectMap<Class<?>, Mods.ModMeta> metas = new ObjectMap<>();
    private boolean requiresReload, createdAtlas;

    public HeadlessMods(){
        Events.on(EventType.ClientLoadEvent.class, e -> Core.app.post(this::checkWarnings));
    }

    @Override
    public Fi getConfig(Mod mod){
        Mods.ModMeta load = metas.get(mod.getClass());
        if(load == null) throw new IllegalArgumentException("Mod is not loaded yet (or missing)!");
        return modDirectory.child(load.name).child("config.json");
    }

    @Override
    public void listFiles(String directory, Cons2<Mods.LoadedMod, Fi> cons){
        eachEnabled(mod -> {
            Fi file = mod.root.child(directory);
            if(file.exists()){
                for(Fi child : file.list()){
                    cons.get(mod, child);
                }
            }
        });
    }

    @Nullable
    @Override
    public Mods.LoadedMod getMod(String name){
        return mods.find(m -> m.name.equals(name));
    }

    @Nullable
    @Override
    public Mods.LoadedMod getMod(Class<? extends Mod> type){
        return mods.find(m -> m.enabled() && m.main != null && m.main.getClass() == type);
    }

    @Override
    public Mods.LoadedMod importMod(Fi file) throws IOException{
        String baseName = file.nameWithoutExtension();
        String finalName = baseName;
        // find a name to prevent any name conflicts
        int count = 1;
        while(modDirectory.child(finalName + ".zip").exists()){
            finalName = baseName + count++;
        }

        Fi dest = modDirectory.child(finalName + ".zip");

        file.copyTo(dest);
        try{
            var loaded = loadMod(dest, true);
            list().add(loaded);
            requiresReload = true;
            // enable the mod on import
            Core.settings.put("mod-" + loaded.name + "-enabled", true);
            sortMods();
            return loaded;
        }catch(IOException e){
            dest.delete();
            throw e;
        }catch(Throwable t){
            dest.delete();
            throw new IOException(t);
        }
    }

    /** Repacks all in-game sprites. */
    @Override
    public void loadAsync(){
        if(!mods.contains(Mods.LoadedMod::enabled)) return;
        Time.mark();

        packer = new MultiPacker();

        eachEnabled(mod -> {
            Seq<Fi> sprites = mod.root.child("sprites").findAll(f -> f.extension().equals("png"));
            Seq<Fi> overrides = mod.root.child("sprites-override").findAll(f -> f.extension().equals("png"));
            packSprites(sprites, mod, true);
            packSprites(overrides, mod, false);
            Log.debug("Packed @ images for mod '@'.", sprites.size + overrides.size, mod.meta.name);
            totalSprites += sprites.size + overrides.size;
        });

        Log.debug("Time to pack textures: @", Time.elapsed());
    }

    private void packSprites(Seq<Fi> sprites, Mods.LoadedMod mod, boolean prefix){
        for(Fi file : sprites){
            try(InputStream stream = file.read()){
                byte[] bytes = Streams.copyBytes(stream, Math.max((int)file.length(), 512));
                Pixmap pixmap = new Pixmap(bytes, 0, bytes.length);
                packer.add(getPage(file), (prefix ? mod.name + "-" : "") + file.nameWithoutExtension(), new PixmapRegion(pixmap));
                pixmap.dispose();
            }catch(IOException e){
                Core.app.post(() -> {
                    Log.err("Error packing images for mod: @", mod.meta.name);
                    Log.err(e);
                    if(!headless) ui.showException(e);
                });
                break;
            }
        }
        totalSprites += sprites.size;
    }

    @Override
    public void loadSync(){
        if(packer == null) return;
        Time.mark();

        // get textures packed
        if(totalSprites > 0){
            if(!createdAtlas) Core.atlas = new TextureAtlas(Core.files.internal("sprites/sprites.atlas"));
            createdAtlas = true;

            for(TextureAtlas.AtlasRegion region : Core.atlas.getRegions()){
                MultiPacker.PageType type = getPage(region);
                if(!packer.has(type, region.name)){
                    packer.add(type, region.name, Core.atlas.getPixmap(region));
                }
            }

            Texture.TextureFilter filter = Core.settings.getBool("linear") ? Texture.TextureFilter.linear : Texture.TextureFilter.nearest;

            // flush so generators can use these sprites
            packer.flush(filter, Core.atlas);

            // generate new icons
            for(Seq<Content> arr : content.getContentMap()){
                arr.each(c -> {
                    if(c instanceof UnlockableContent u && c.minfo.mod != null){
                        u.load();
                        u.createIcons(packer);
                    }
                });
            }

            Core.atlas = packer.flush(filter, new TextureAtlas());
            Core.atlas.setErrorRegion("error");
            Log.debug("Total pages: @", Core.atlas.getTextures().size);
        }

        packer.dispose();
        packer = null;
        Log.debug("Time to update textures: @", Time.elapsed());
    }

    private MultiPacker.PageType getPage(TextureAtlas.AtlasRegion region){
        return
                region.texture == Core.atlas.find("white").texture ? MultiPacker.PageType.main :
                region.texture == Core.atlas.find("stone1").texture ? MultiPacker.PageType.environment :
                region.texture == Core.atlas.find("clear-editor").texture ? MultiPacker.PageType.editor :
                region.texture == Core.atlas.find("whiteui").texture ? MultiPacker.PageType.ui :
                MultiPacker.PageType.main;
    }

    private MultiPacker.PageType getPage(Fi file){
        String parent = file.parent().name();
        return
                parent.equals("environment") ? MultiPacker.PageType.environment :
                parent.equals("editor") ? MultiPacker.PageType.editor :
                parent.equals("ui") || file.parent().parent().name().equals("ui") ? MultiPacker.PageType.ui :
                MultiPacker.PageType.main;
    }

    /** Removes a mod file and marks it for requiring a restart. */
    @Override
    public void removeMod(Mods.LoadedMod mod){
        if(mod.root instanceof ZipFi){
            mod.root.delete();
        }

        boolean deleted = mod.file.isDirectory() ? mod.file.deleteDirectory() : mod.file.delete();

        if(!deleted){
            ui.showErrorMessage("@mod.delete.error");
            return;
        }
        mods.remove(mod);
        mod.dispose();
        requiresReload = true;
    }

    @Override
    public Scripts getScripts(){
        if(scripts == null){
            scripts = platform.createScripts();
        }
        return scripts;
    }

    @Override
    public boolean hasScripts(){
        return scripts != null;
    }

    @Override
    public boolean requiresReload(){
        return requiresReload;
    }

    @Override
    public boolean skipModLoading(){
        return failedToLaunch && Core.settings.getBool("modcrashdisable", true);
    }

    @Override
    public void load(){
        for(Fi file : modDirectory.list()){
            if(!file.extension().equals("jar") && !file.extension().equals("zip") &&
                    !(file.isDirectory() && (file.child("mod.json").exists() || file.child("mod.hjson").exists()))) continue;

            Log.debug("[Mods] Loading mod @", file);
            try{
                Mods.LoadedMod mod = loadMod(file);
                mods.add(mod);
            }catch(Throwable e){
                if(e instanceof ClassNotFoundException && e.getMessage().contains("mindustry.plugin.Plugin")){
                    Log.info("Plugin @ is outdated and needs to be ported to 6.0! " +
                            "Update its main class to inherit from 'mindustry.mod.Plugin'. " +
                            "See https://mindustrygame.github.io/wiki/modding/6-migrationv6/");
                }else{
                    Log.err("Failed to load mod file @. Skipping.", file);
                    Log.err(e);
                }
            }
        }

        // load workshop mods now
        for(Fi file : platform.getWorkshopContent(Mods.LoadedMod.class)){
            try{
                Mods.LoadedMod mod = loadMod(file);
                mods.add(mod);
                mod.addSteamID(file.name());
            }catch(Throwable e){
                Log.err("Failed to load mod workshop file @. Skipping.", file);
                Log.err(e);
            }
        }

        resolveModState();
        sortMods();

        buildFiles();
    }

    private void sortMods(){
        // sort mods to make sure servers handle them properly and they appear correctly in the dialog
        mods.sort(Structs.comps(Structs.comparingInt(m -> m.state.ordinal()), Structs.comparing(m -> m.name)));
    }

    private void resolveModState(){
        mods.each(this::updateDependencies);

        for(Mods.LoadedMod mod : mods){
            mod.state =
                    !mod.isSupported() ? Mods.ModState.unsupported :
                    mod.hasUnmetDependencies() ? Mods.ModState.missingDependencies :
                    !mod.shouldBeEnabled() ? Mods.ModState.disabled :
                    Mods.ModState.enabled;
        }
    }

    private void updateDependencies(Mods.LoadedMod mod){
        mod.dependencies.clear();
        mod.missingDependencies.clear();
        mod.dependencies = mod.meta.dependencies.map(this::locateMod);

        for(int i = 0; i < mod.dependencies.size; i++){
            if(mod.dependencies.get(i) == null){
                mod.missingDependencies.add(mod.meta.dependencies.get(i));
            }
        }
    }

    private void topoSort(Mods.LoadedMod mod, Seq<Mods.LoadedMod> stack, ObjectSet<Mods.LoadedMod> visited){
        visited.add(mod);
        mod.dependencies.each(m -> !visited.contains(m), m -> topoSort(m, stack, visited));
        stack.add(mod);
    }

    /** @return mods ordered in the correct way needed for dependencies. */
    private Seq<Mods.LoadedMod> orderedMods(){
        ObjectSet<Mods.LoadedMod> visited = new ObjectSet<>();
        Seq<Mods.LoadedMod> result = new Seq<>();
        eachEnabled(mod -> {
            if(!visited.contains(mod)){
                topoSort(mod, result, visited);
            }
        });
        return result;
    }

    @Override
    public Mods.LoadedMod locateMod(String name){
        return mods.find(mod -> mod.enabled() && mod.name.equals(name));
    }

    private void buildFiles(){
        for(Mods.LoadedMod mod : orderedMods()){
            boolean zipFolder = !mod.file.isDirectory() && mod.root.parent() != null;
            String parentName = zipFolder ? mod.root.name() : null;
            for(Fi file : mod.root.list()){
                //ignore special folders like bundles or sprites
                if(file.isDirectory() && !specialFolders.contains(file.name())){
                    file.walk(f -> tree.addFile(mod.file.isDirectory() ? f.path().substring(1 + mod.file.path().length()) :
                            zipFolder ? f.path().substring(parentName.length() + 1) : f.path(), f));
                }
            }

            //load up bundles.
            Fi folder = mod.root.child("bundles");
            if(folder.exists()){
                for(Fi file : folder.list()){
                    if(file.name().startsWith("bundle") && file.extension().equals("properties")){
                        String name = file.nameWithoutExtension();
                        bundles.get(name, Seq::new).add(file);
                    }
                }
            }
        }
        Events.fire(new EventType.FileTreeInitEvent());

        // add new keys to each bundle
        I18NBundle bundle = Core.bundle;
        while(bundle != null){
            String str = bundle.getLocale().toString();
            String locale = "bundle" + (str.isEmpty() ? "" : "_" + str);
            for(Fi file : bundles.get(locale, Seq::new)){
                try{
                    PropertiesUtils.load(bundle.getProperties(), file.reader());
                }catch(Throwable e){
                    Log.err("Error loading bundle: " + file + "/" + locale, e);
                }
            }
            bundle = bundle.getParent();
        }
    }

    private void checkWarnings(){
        // show 'scripts have errored' info
        if(scripts != null && scripts.hasErrored()){
            ui.showErrorMessage("@mod.scripts.disable");
        }

        // show list of errored content
        if(mods.contains(Mods.LoadedMod::hasContentErrors)){
            ui.loadfrag.hide();
            new Dialog(""){{

                setFillParent(true);
                cont.margin(15);
                cont.add("@error.title");
                cont.row();
                cont.image().width(300f).pad(2).colspan(2).height(4f).color(Color.scarlet);
                cont.row();
                cont.add("@mod.errors").wrap().growX().center().get().setAlignment(Align.center);
                cont.row();
                cont.pane(p -> {
                    mods.each(m -> m.enabled() && m.hasContentErrors(), m -> {
                        p.add(m.name).color(Pal.accent).left();
                        p.row();
                        p.image().fillX().pad(4).color(Pal.accent);
                        p.row();
                        p.table(d -> {
                            d.left().marginLeft(15f);
                            for(Content c : m.erroredContent){
                                d.add(c.minfo.sourceFile.nameWithoutExtension()).left().padRight(10);
                                d.button("@details", Icon.downOpen, Styles.transt, () -> {
                                    new Dialog(""){{
                                        setFillParent(true);
                                        cont.pane(e -> e.add(c.minfo.error).wrap().grow().labelAlign(Align.center, Align.left)).grow();
                                        cont.row();
                                        cont.button("@ok", Icon.left, this::hide).size(240f, 60f);
                                    }}.show();
                                }).size(190f, 50f).left().marginLeft(6);
                                d.row();
                            }
                        }).left();
                        p.row();
                    });
                });

                cont.row();
                cont.button("@ok", this::hide).size(300, 50);
            }}.show();
        }
    }

    @Override
    public boolean hasContentErrors(){
        return mods.contains(Mods.LoadedMod::hasContentErrors) || (scripts != null && scripts.hasErrored());
    }

    @Override
    public void loadScripts(){
        Time.mark();
        boolean[] any = {false};

        try{
            eachEnabled(mod -> {
                if(mod.root.child("scripts").exists()){
                    content.setCurrentMod(mod);
                    //if there's only one script file, use it (for backwards compatibility); if there isn't, use "main.js"
                    Seq<Fi> allScripts = mod.root.child("scripts").findAll(f -> f.extEquals("js"));
                    Fi main = allScripts.size == 1 ? allScripts.first() : mod.root.child("scripts").child("main.js");
                    if(main.exists() && !main.isDirectory()){
                        try{
                            if(scripts == null){
                                scripts = platform.createScripts();
                            }
                            any[0] = true;
                            scripts.run(mod, main);
                        }catch(Throwable e){
                            Core.app.post(() -> {
                                Log.err("Error loading main script @ for mod @.", main.name(), mod.meta.name);
                                Log.err(e);
                            });
                        }
                    }else{
                        Core.app.post(() -> Log.err("No main.js found for mod @.", mod.meta.name));
                    }
                }
            });
        }finally{
            content.setCurrentMod(null);
        }

        if(any[0]){
            Log.info("Time to initialize modded scripts: @", Time.elapsed());
        }
    }

    @Override
    public void loadContent(){

        // load class mod content first
        for(Mods.LoadedMod mod : orderedMods()){
            // hidden mods can't load content
            if(mod.main != null && !mod.meta.hidden){
                content.setCurrentMod(mod);
                mod.main.loadContent();
            }
        }

        content.setCurrentMod(null);

        class LoadRun implements Comparable<LoadRun>{
            final ContentType type;
            final Fi file;
            final Mods.LoadedMod mod;

            public LoadRun(ContentType type, Fi file, Mods.LoadedMod mod){
                this.type = type;
                this.file = file;
                this.mod = mod;
            }

            @Override
            public int compareTo(LoadRun l){
                int mod = this.mod.name.compareTo(l.mod.name);
                if(mod != 0) return mod;
                return this.file.name().compareTo(l.file.name());
            }
        }

        Seq<LoadRun> runs = new Seq<>();

        for(Mods.LoadedMod mod : orderedMods()){
            if(mod.root.child("content").exists()){
                Fi contentRoot = mod.root.child("content");
                for(ContentType type : ContentType.all){
                    Fi folder = contentRoot.child(type.name().toLowerCase(Locale.ROOT) + "s");
                    if(folder.exists()){
                        for(Fi file : folder.findAll(f -> f.extension().equals("json") || f.extension().equals("hjson"))){
                            runs.add(new LoadRun(type, file, mod));
                        }
                    }
                }
            }
        }

        // make sure mod content is in proper order
        runs.sort();
        for(LoadRun l : runs){
            Content current = content.getLastAdded();
            try{
                // this binds the content but does not load it entirely
                Content loaded = parser.parse(l.mod, l.file.nameWithoutExtension(), l.file.readString("UTF-8"), l.file, l.type);
                Log.debug("[@] Loaded '@'.", l.mod.meta.name, (loaded instanceof UnlockableContent ? ((UnlockableContent)loaded).localizedName : loaded));
            }catch(Throwable e){
                if(current != content.getLastAdded() && content.getLastAdded() != null){
                    parser.markError(content.getLastAdded(), l.mod, l.file, e);
                }else{
                    ErrorContent error = new ErrorContent();
                    parser.markError(error, l.mod, l.file, e);
                }
            }
        }

        // this finishes parsing content fields
        parser.finishParsing();
    }

    @Override
    public void handleContentError(Content content, Throwable error){
        parser.markError(content, error);
    }

    @Override
    public Seq<String> getModStrings(){
        return mods.select(l -> !l.meta.hidden && l.enabled()).map(l -> l.name + ":" + l.meta.version);
    }

    @Override
    public void setEnabled(Mods.LoadedMod mod, boolean enabled){
        if(mod.enabled() != enabled){
            Core.settings.put("mod-" + mod.name + "-enabled", enabled);
            requiresReload = true;
            mod.state = enabled ? Mods.ModState.enabled : Mods.ModState.disabled;
            mods.each(this::updateDependencies);
            sortMods();
        }
    }

    @Override
    public Seq<String> getIncompatibility(Seq<String> out){
        Seq<String> mods = getModStrings();
        Seq<String> result = mods.copy();
        for(String mod : mods){
            if(out.remove(mod)){
                result.remove(mod);
            }
        }
        return result;
    }

    @Override
    public Seq<Mods.LoadedMod> list(){
        return mods;
    }

    @Override
    public void eachClass(Cons<Mod> cons){
        mods.each(p -> p.main != null, p -> contextRun(p, () -> cons.get(p.main)));
    }

    @Override
    public void eachEnabled(Cons<Mods.LoadedMod> cons){
        mods.each(Mods.LoadedMod::enabled, cons);
    }

    private Mods.LoadedMod loadMod(Fi sourceFile) throws Exception{
        return loadMod(sourceFile, false);
    }

    private Mods.LoadedMod loadMod(Fi sourceFile, boolean overwrite) throws Exception{
        Time.mark();

        ZipFi rootZip = null;

        try{
            Fi zip = sourceFile.isDirectory() ? sourceFile : (rootZip = new ZipFi(sourceFile));
            if(zip.list().length == 1 && zip.list()[0].isDirectory()){
                zip = zip.list()[0];
            }

            Fi metaf =
                    zip.child("mod.json").exists() ? zip.child("mod.json") :
                    zip.child("mod.hjson").exists() ? zip.child("mod.hjson") :
                    zip.child("plugin.json").exists() ? zip.child("plugin.json") :
                    zip.child("plugin.hjson");

            if(!metaf.exists()){
                Log.warn("Mod @ doesn't have a '[mod/plugin].[h]json' file, skipping.", sourceFile);
                throw new IllegalArgumentException("Invalid file: No mod.json found.");
            }

            Mods.ModMeta meta = json.fromJson(Mods.ModMeta.class, Jval.read(metaf.readString()).toString(Jval.Jformat.plain));
            meta.cleanup();
            String camelized = meta.name.replace(" ", "");
            String mainClass = meta.main == null ? camelized.toLowerCase(Locale.ROOT) + "." + camelized + "Mod" : meta.main;
            String baseName = meta.name.toLowerCase(Locale.ROOT).replace(" ", "-");

            var other = mods.find(m -> m.name.equals(baseName));

            if(other != null){
                // steam mods can't really be deleted, they need to be unsubscribed
                if(overwrite && !other.hasSteamID()){
                    // close zip file
                    if(other.root instanceof ZipFi){
                        other.root.delete();
                    }
                    // delete the old mod directory
                    if(other.file.isDirectory()){
                        other.file.deleteDirectory();
                    }else{
                        other.file.delete();
                    }
                    // unload
                    mods.remove(other);
                }else{
                    throw new IllegalArgumentException("A mod with the name '" + baseName + "' is already imported.");
                }
            }

            ClassLoader loader = null;
            Mod mainMod;
            Fi mainFile = zip;

            if(android){
                mainFile = mainFile.child("classes.dex");
            }else{
                String[] path = (mainClass.replace('.', '/') + ".class").split("/");
                for(String str : path){
                    if(!str.isEmpty()){
                        mainFile = mainFile.child(str);
                    }
                }
            }

            //make sure the main class exists before loading it; if it doesn't just don't put it there
            //if the mod is explicitly marked as java, try loading it anyway
            if((mainFile.exists() || meta.java) &&
            !skipModLoading() &&
            Core.settings.getBool("mod-" + baseName + "-enabled", true) &&
            Version.isAtLeast(meta.minGameVersion) &&
            (meta.getMinMajor() >= 105 || headless)
            ){
                if(ios){
                    throw new IllegalArgumentException("Java class mods are not supported on iOS.");
                }

                loader = platform.loadJar(sourceFile, mainClass);
                Class<?> main = Class.forName(mainClass, true, loader);
                metas.put(main, meta);
                mainMod = (Mod)main.getDeclaredConstructor().newInstance();
            }else{
                mainMod = null;
            }

            //all plugins are hidden implicitly
            if(mainMod instanceof Plugin){
                meta.hidden = true;
            }

            //disallow putting a description after the version
            if(meta.version != null){
                int line = meta.version.indexOf('\n');
                if(line != -1){
                    meta.version = meta.version.substring(0, line);
                }
            }

            //skip mod loading if it failed
            if(skipModLoading()){
                Core.settings.put("mod-" + baseName + "-enabled", false);
            }

            if(!headless){
                Log.info("Loaded mod '@' in @ms", meta.name, Time.elapsed());
            }

            return new Mods.LoadedMod(sourceFile, zip, mainMod, loader, meta);
        }catch(Exception e){
            //delete root zip file so it can be closed on windows
            if(rootZip != null) rootZip.delete();
            throw e;
        }
    }
}
