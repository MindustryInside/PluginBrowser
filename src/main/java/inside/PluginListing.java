package inside;

public class PluginListing {
    public String repo;
    public String name;
    public String author;
    public String lastUpdated;
    public String description;
    public boolean hasJava;
    public int stars;

    @Override
    public String toString() {
        return "PluginListing{" +
                "repo='" + repo + '\'' +
                ", name='" + name + '\'' +
                ", author='" + author + '\'' +
                ", lastUpdated='" + lastUpdated + '\'' +
                ", description='" + description + '\'' +
                ", hasJava=" + hasJava +
                ", stars=" + stars +
                '}';
    }
}
