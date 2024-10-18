package ivangka.autocontentbot.model;

public class Channel {

    private long id;
    private String name;
    private String tags;
    private float temperature;

    public Channel(long id, String name, String tags, float temperature) {
        this.id = id;
        this.name = name;
        this.tags = tags;
        this.temperature = temperature;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public float getTemperature() {
        return temperature;
    }

    public void setTemperature(float temperature) {
        this.temperature = temperature;
    }

}
