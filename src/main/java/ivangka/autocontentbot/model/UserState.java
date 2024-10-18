package ivangka.autocontentbot.model;

public class UserState {

    private long id;
    private Channel selectedChannel;
    private String selectedTopic;
    private Post generatedPost;

    public UserState(long id) {
        this.id = id;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Channel getSelectedChannel() {
        return selectedChannel;
    }

    public void setSelectedChannel(Channel selectedChannel) {
        this.selectedChannel = selectedChannel;
    }

    public String getSelectedTopic() {
        return selectedTopic;
    }

    public void setSelectedTopic(String selectedTopic) {
        this.selectedTopic = selectedTopic;
    }

    public Post getGeneratedPost() {
        return generatedPost;
    }

    public void setGeneratedPost(Post generatedPost) {
        this.generatedPost = generatedPost;
    }

    public void resetFields() {
        selectedChannel = null;
        selectedTopic = null;
        generatedPost = null;
    }

}
