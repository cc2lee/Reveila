package reveila.util.task;

import java.util.EventListener;

public interface JobListener extends EventListener {
	
	public void jobUpdate(JobEvent jobEvent);

}
