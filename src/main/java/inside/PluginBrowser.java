package inside;

import arc.util.CommandHandler;
import mindustry.mod.Plugin;

public class PluginBrowser extends Plugin {

    @Override
    public void registerServerCommands(CommandHandler handler) {

        handler.register("plugins", "<add/remove/list> [value...]", args -> {

        });
    }
}
