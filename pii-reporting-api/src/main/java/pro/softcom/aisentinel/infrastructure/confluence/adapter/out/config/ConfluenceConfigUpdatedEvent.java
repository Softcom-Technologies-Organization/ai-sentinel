package pro.softcom.aisentinel.infrastructure.confluence.adapter.out.config;

import org.springframework.context.ApplicationEvent;

/**
 * Event published when Confluence connection configuration is updated via the UI.
 * Listeners can refresh cached configuration values.
 */
public class ConfluenceConfigUpdatedEvent extends ApplicationEvent {

    public ConfluenceConfigUpdatedEvent(Object source) {
        super(source);
    }
}
