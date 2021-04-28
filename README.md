# Plugin Browser

Plugin for user-friendly management of mods and plugins on Mindustry servers. <br>
Plugin uses public mods ([Anuken/MindustryMods](https://github.com/Anuken/MindustryMods)) and plugins ([MindustryInside/MindustryPlugins](https://github.com/MindustryInside/MindustryPlugins)) lists.

## Installation

1. Download the latest release (`.jar`) [here](https://github.com/MindustryInside/PluginBrowser/releases/latest).
2. Put `.jar` to `config/mods` directory

## Building

First, make sure you have JDK 14 installed. Then, setup [plugin.json](src/main/resources/plugin.json) and run the following commands:

* Windows: `gradlew jar`
* *nix/Mac OS: `./gradlew jar`
  
After building, the `.jar` file should be located in `build/libs` folder.

### Troubleshooting

* If the terminal returns `Permission denied` or `Command not found`, run `chmod +x ./gradlew`.
