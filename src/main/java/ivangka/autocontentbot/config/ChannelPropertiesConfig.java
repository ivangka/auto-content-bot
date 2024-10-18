package ivangka.autocontentbot.config;

import ivangka.autocontentbot.model.Channel;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "channels")
@PropertySource("classpath:application.properties")
@Configuration
public class ChannelPropertiesConfig {

    private List<Channel> channels = new ArrayList<>();

    public List<Channel> getChannels() {
        return channels;
    }

    public void setChannels(List<Channel> channels) {
        this.channels = channels;
    }

    public boolean containsChannelName(String name) {
        for (Channel channel : channels) {
            if (channel.getName().equals(name))
                return true;
        }
        return false;
    }

    public Channel getChannelByName(String name) {
        for (Channel channel : channels) {
            if (channel.getName().equals(name))
                return channel;
        }
        return null;
    }

}
