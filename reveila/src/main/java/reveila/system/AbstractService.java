package reveila.system;

import java.util.EventObject;

import reveila.system.lifecycle.Startable;
import reveila.system.lifecycle.Stoppable;
import reveila.util.event.EventWatcher;

public abstract class AbstractService implements EventWatcher, Startable, Stoppable {

    protected SystemContext systemContext;
    protected boolean available = true;

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }
    
    public void setSystemContext(SystemContext systemContext) {
        this.systemContext = systemContext;
    }

    @Override
    public void stop() throws Exception {
    }

    @Override
    public void start() throws Exception {
    }

    @Override
    public void onEvent(EventObject evtObj) throws Exception {
    }

}
