package com.factioncore.util;

import com.hypixel.hytale.server.core.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for building multi-line messages for UI tooltips.
 */
public class MessageHelper {

    public static ML multiLine(){
        return new ML();
    }

    public static class ML {

        private List<Message> messages;

        public ML() {
            this.messages = new ArrayList<>();
        }

        public ML append(Message message) {
            this.messages.add(message);
            return this;
        }

        public ML nl() {
            this.messages.add(Message.raw("\n"));
            return this;
        }

        public Message build() {
            return Message.join(this.messages.toArray(new Message[0]));
        }
    }
}
