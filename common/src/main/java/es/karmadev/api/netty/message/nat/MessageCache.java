package es.karmadev.api.netty.message.nat;

import lombok.Getter;

@Getter
class MessageCache {

    @Getter
    private final static MessageCache instance = new MessageCache();

    private long minId;
    private long maxId;

    private MessageCache() {}

    public void setMinId(final long newId) {
        if (newId < minId) minId = newId;
    }

    public void setMaxId(final long newId) {
        if (newId > maxId) maxId = newId;
    }

    public boolean isBetween(final long id) {
        return id >= minId && id <= maxId;
    }
}
