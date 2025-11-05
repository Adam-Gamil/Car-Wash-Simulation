/*
 * CAR WASH SIMULATION USING SEMAPHORES
 * =====================================
 * This program simulates a car wash system using the Producer-Consumer pattern.
 * Cars (producers) arrive and add themselves to a queue.
 * Pumps (consumers) remove cars from the queue and service them.
 * 
 * WHAT IS A SEMAPHORE?
 * A semaphore is a synchronization tool used in concurrent programming to control
 * access to shared resources. Think of it as a counter with two main operations:
 * 
 * 1. wait2() [also called P() or down()]:
 *    - Decrements the counter by 1
 *    - If counter becomes negative, the thread blocks (waits) until someone signals
 *    - Used when a thread wants to acquire/use a resource
 * 
 * 2. signal2() [also called V() or up()]:
 *    - Increments the counter by 1
 *    - Wakes up one waiting thread (if any are blocked)
 *    - Used when a thread releases a resource
 * 
 * Note: We use signal2() and wait2() because Java already has built-in wait() 
 * and notify() methods in the Object class.
 */

/*
 * CAR CLASS (PRODUCER)
 * ====================
 * Represents a car that arrives at the car wash and adds itself to the waiting queue.
 * Each car runs in its own thread.
 */
public class Car extends Thread {
	// Car identification and properties
	private String carName;          // Unique name for the car (e.g., "Car1")
	private int carId;               // Numeric ID for the car
	private int carType;             // Type of car (could represent size, service level, etc.)
	private int arrivalTime;         // Time delay before car arrives (in milliseconds)

	// Shared resources for synchronization
	private BoundedBufferQueue queue;  // The shared queue where cars wait for service
	
	/*
	 * SEMAPHORES USED BY CAR:
	 * -----------------------
	 * empty: Counts available empty slots in the queue
	 *        - Car must wait2() on this before adding to queue
	 *        - Prevents queue overflow
	 * 
	 * full: Counts how many cars are waiting in the queue
	 *       - Car signals this after adding itself to queue
	 *       - Tells pumps that work is available
	 * 
	 * mutex: Ensures mutual exclusion (only one thread accesses queue at a time)
	 *        - Prevents race conditions when multiple cars try to add simultaneously
	 *        - Acts as a lock for the critical section
	 * 
	 * pumps: Counts available pump bays (used by Pump class, not Car)
	 */
	private Semaphore empty;
	private Semaphore full;
	private Semaphore mutex;
	private Semaphore pumps;

	/*
	 * CONSTRUCTOR
	 * -----------
	 * Initializes a new car with identification details.
	 * Note: The shared resources (queue, semaphores) need to be set separately
	 * or this constructor needs to be modified to accept them as parameters.
	 */
	public Car(String carName, int carId, int carType) {
		this.carName = carName;
		this.carId = carId;
		this.carType = carType;
		this.arrivalTime = arrivalTime;  // Note: This should be passed as a parameter

		// These shared resources should be initialized or passed in
		this.queue = queue;
		this.empty = empty;
		this.full = full;
		this.mutex = mutex;
		this.pumps = pumps;
	}

	/*
	 * RUN METHOD - PRODUCER LOGIC
	 * ============================
	 * This method executes when the car thread starts.
	 * It simulates a car arriving and joining the queue for service.
	 * 
	 * STEPS:
	 * 1. Wait for arrival time (simulates cars arriving at different times)
	 * 2. Acquire resources needed to add to queue (empty slot + queue access)
	 * 3. Add car to queue (CRITICAL SECTION - only one thread can do this at a time)
	 * 4. Release resources and signal that queue has a new item
	 */
	@Override
	public void run() {
		try {
			// Step 1: Simulate car arrival delay
			Thread.sleep(arrivalTime);
			System.out.println("Car " + carName + " arrived at time " + arrivalTime);

			// Step 2a: Wait for an empty slot in the queue
			// If queue is full, this car will block here until space is available
			empty.wait2();
			
			// Step 2b: Acquire exclusive access to the queue (enter critical section)
			// This prevents other cars from modifying the queue simultaneously
			mutex.wait2();

			// Step 3: CRITICAL SECTION - Add car to the queue
			// Only one car can execute this at a time due to mutex
			queue.addCar(this);
			System.out.println(carName + " added to queue");

			// Step 4a: Release the mutex lock (exit critical section)
			// Other cars can now access the queue
			mutex.signal2();
			
			// Step 4b: Signal that queue now has one more car (increment full counter)
			// This wakes up any pump that's waiting for work
			full.signal2();
			
		} catch (InterruptedException e) {
			System.out.println("Car " + carName + " interrupted.");
		}
	}

	// GETTER METHODS - Retrieve car information
	public String getCarName() {
		return carName;
	}

	public int getCarId() {
		return carId;
	}

	public int getCarType() {
		return carType;
	}
	
	// SETTER METHODS - Modify car information
	public void setCarName(String carName) {
		this.carName = carName;
	}
	
	public void setCarId(int carId) {
		this.carId = carId;
	}
	
	public void setCarType(int carType) {
		this.carType = carType;
	}
}

/*
 * PUMP CLASS (CONSUMER)
 * =====================
 * Represents a pump/bay at the car wash that services cars.
 * Each pump runs in its own thread and continuously:
 * 1. Removes a car from the queue
 * 2. Waits for an available bay
 * 3. Services the car (simulated with a time delay)
 * 4. Frees up the bay for the next car
 */
public class Pump<full> extends Thread {
	private int pumpId;  // Unique identifier for this pump
	
	// Shared resources for synchronization
	private BoundedBufferQueue queue;  // The shared queue where cars wait for service
	
	/*
	 * SEMAPHORES USED BY PUMP:
	 * ------------------------
	 * empty: Counts available empty slots in the queue
	 *        - Pump signals this after removing a car from queue
	 *        - Tells cars that space is available
	 * 
	 * full: Counts how many cars are waiting in the queue
	 *       - Pump must wait2() on this before removing from queue
	 *       - Ensures pump doesn't try to service when queue is empty
	 * 
	 * mutex: Ensures mutual exclusion (only one thread accesses queue at a time)
	 *        - Prevents race conditions when multiple pumps try to remove simultaneously
	 *        - Acts as a lock for the critical section
	 * 
	 * pumps: Counts available pump bays for servicing cars
	 *        - Pump waits on this before servicing (wait for available bay)
	 *        - Pump signals this after servicing (bay becomes available again)
	 *        - Limits how many cars can be serviced simultaneously
	 */
	private Semaphore empty;
	private Semaphore full;
	private Semaphore mutex;
	private Semaphore pumps;

	/*
	 * CONSTRUCTOR
	 * -----------
	 * Initializes a pump with its ID and references to all shared resources.
	 * All pumps share the same queue and semaphores to coordinate access.
	 */
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

	/*
	 * RUN METHOD - CONSUMER LOGIC
	 * ============================
	 * This method executes when the pump thread starts.
	 * It runs an infinite loop, continuously servicing cars from the queue.
	 * 
	 * STEPS:
	 * 1. Wait for a car to be available in queue
	 * 2. Acquire exclusive access to queue and remove a car (CRITICAL SECTION)
	 * 3. Release queue access so other pumps can get cars
	 * 4. Wait for an available service bay
	 * 5. Service the car (simulated time delay)
	 * 6. Release the bay for the next car
	 * 
	 * IMPORTANT: Notice the order of semaphore operations to avoid deadlock!
	 * - We release mutex BEFORE waiting for pumps semaphore
	 * - This prevents holding the queue lock while waiting for a bay
	 */
	@Override
	public void run() {
		try {
			// Infinite loop - pump continuously services cars
			while (true) {
				// Step 1: Wait for a car to be in the queue (full > 0)
				// If queue is empty, this pump will block here until a car arrives
				full.wait2();
				
				// Step 2a: Acquire exclusive access to the queue (enter critical section)
				// This prevents other pumps from accessing the queue simultaneously
				mutex.wait2();

				// Step 2b: CRITICAL SECTION - Remove a car from the queue
				// Only one pump can execute this at a time due to mutex
				Car car = queue.removeCar();
				System.out.println("Pump " + pumpId + ": " + car.getCarName() + " removed from queue");

				// Step 3a: Release the mutex lock (exit critical section)
				// IMPORTANT: We do this BEFORE waiting for a bay to prevent deadlock
				// Other pumps can now access the queue while this pump waits for a bay
				mutex.signal2();
				
				// Step 3b: Signal that an empty space is now available in the queue
				// This wakes up any car that's waiting to add itself to a full queue
				empty.signal2();
				
				// Step 4: Wait for an available pump bay (pumps > 0)
				// If all bays are occupied, this pump will block here with the car
				// Note: This happens OUTSIDE the critical section to allow other pumps
				// to continue removing cars from the queue
				pumps.wait2();

				// Step 5: Service the car at the bay
				System.out.println("Pump " + pumpId + ": " + car.getCarName() + " begins service at Bay " + pumpId);
				
				// Simulate the time it takes to wash a car (2-6 seconds)
				// Math.random() returns 0.0 to 1.0, so this gives 2000-6000 milliseconds
				Thread.sleep((long) (Math.random() * 4000 + 2000));

				// Step 6: Car service is complete
				System.out.println("Pump " + pumpId + ": " + car.getCarName() + " finishes service");
				System.out.println("Pump " + pumpId + ": Bay " + pumpId + " is now free");

				// Signal that this pump bay is now free (increment pumps counter)
				// This wakes up any pump that's waiting for an available bay
				pumps.signal2();
				
				// Loop continues - pump goes back to waiting for the next car
			}
		} catch (InterruptedException e) {
			System.out.println("Pump " + pumpId + " interrupted.");
		}
	}
}
