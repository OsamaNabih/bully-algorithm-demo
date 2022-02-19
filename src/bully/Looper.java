package bully;

public class Looper {
	int state = 0;
	Looper() {
		Thread t = hearBeater();
		loop(t);
	}
	private void loop(Thread t) {
		while(true) {
			try {
				System.out.println(state);
				Thread.sleep(4000);
				if (!t.isAlive())
					break;
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}
	}
	
	private Thread hearBeater() {
		Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
            	while(true) {
            		state += 1;
            		try {
						Thread.sleep(3000);
						if (state > 5)
							break;
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
            	}
            }
        }) ;
        t.start();
        return t;
	}
}
