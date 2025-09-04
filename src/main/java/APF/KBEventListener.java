// KBEventListener.java
package APF;

import com.google.common.eventbus.Subscribe;
import forge.game.event.GameEvent;
import java.util.logging.Logger;
import java.util.logging.Level;

public final class KBEventListener {
    private static final Logger LOGGER = Logger.getLogger(KBEventListener.class.getName());
    private final KnowledgeBase kb;

    public KBEventListener(KnowledgeBase kb) { this.kb = kb; }

    @Subscribe
    public void onAny(GameEvent event) {
        try {
            event.visit(kb); // T = Void
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING,
                    t,
                    () -> "KBEventListener failure on " + event.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }
}
