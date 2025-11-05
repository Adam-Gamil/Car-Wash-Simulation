// I used signal2 and wait2 because in java wait and signal are already defined methods we can change names later
public class Pump<full> extends Thread {
	private int pumpId;
	private BoundedBufferQueue queue; // Shared queue
	private Semaphore empty; // counting empty slots in queue
	private Semaphore full; // counting filled slots in queue
	private Semaphore mutex; // ensuring mutual exclusion on queue
	private Semaphore pumps; // counting available pumps (bays)

	public Pump(int pumpId, BoundedBufferQueue queue,
			Semaphore empty, Semaphore full,
			Semaphore mutex, Semaphore pumps) {
		this.pumpId = pumpId;
		this.queue = queue;
		this.empty = empty;
		this.full = full;
		this.mutex = mutex;
		this.pumps = pumps;
	}

	@Override
	public void run() {
		try {
			while (true) {
				full.wait2();
				mutex.wait2();

				Car car = queue.removeCar();
				System.out.println("Pump " + pumpId + ": " + car.getCarName() + " Occupied");

				// Unlock the queue
				mutex.signal2();
				// Signal that an empty space is now available in the queue
				empty.signal2();
				// wait for an available pump bay
				pumps.wait2();

				System.out.println("Pump " + pumpId + ": " + car.getCarName() + " begins service at Bay " + pumpId);
				// for time simulation purposes
				Thread.sleep((long) (Math.random() * 4000 + 2000)); // 2–6 sec

				System.out.println("Pump " + pumpId + ": " + car.getCarName() + " finishes service");
				System.out.println("Pump " + pumpId + ": Bay " + pumpId + " is now free");

				// signal that the pump bay is now free
				pumps.signal2();
			}
		} catch (InterruptedException e) {
			System.out.println("Pump " + pumpId + " interrupted.");
		}
	}
}
