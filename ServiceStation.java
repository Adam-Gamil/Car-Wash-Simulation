import java.util.Scanner;
import java.util.LinkedList;
import java.util.Queue;


class Car extends Thread {
    // Car identification and properties
    private String carName;          // Unique name for the car (e.g., "Car1")
    private int carId;               // Numeric ID for the car

    // Shared resources for synchronization
    private SharedQueue queue;  // The shared queue where cars wait for service



    /*
     * CONSTRUCTOR
     * -----------
     * Initializes a new car with identification details.
     * Note: The shared resources (queue, semaphores) need to be set separately
     * or this constructor needs to be modified to accept them as parameters.
     */
    public Car(String carName, int carId, SharedQueue queue) {
        this.carName = carName;
        this.carId = carId;
        this.queue = queue;

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

            System.out.println();
            System.out.println( carName + " arrived");


            // Step 3: CRITICAL SECTION - Add car to the queue
            // Only one car can execute this at a time due to mutex
            queue.addCar(this);
            System.out.println();

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



    // SETTER METHODS - Modify car information
    public void setCarName(String carName) {
        this.carName = carName;
    }

    public void setCarId(int carId) {
        this.carId = carId;
    }


}

class SharedQueue {

    private final Queue<Car> queue;
    private final Semaphore empty;
    private final Semaphore full;
    private final Semaphore mutex;

    public SharedQueue(int capacity) {
        this.queue = new LinkedList<>();
        this.empty = new Semaphore(capacity); // Controls available slots
        this.full = new Semaphore(0);         // Controls filled slots
        this.mutex = new Semaphore(1);        // Controls access to the queue
    }

    public void addCar(Car car) throws InterruptedException {
        // 1. Wait for a slot to be 'empty'
        empty.acquire();

        // 2. Lock the queue for exclusive access
        mutex.acquire();

        // --- Critical Section ---
        queue.add(car);
        System.out.println(car.getCarName() + " entered the waiting queue. (Queue size: " + queue.size() + ")");
        // --- End Critical Section ---

        // 3. Unlock the queue
        mutex.release();

        // 4. Signal that one slot is now 'full'
        full.release();
    }

    public Car removeCar(int pumpId) throws InterruptedException {
        // 1. Wait for a slot to be 'full' (i.e., for a car to be present)
        full.acquire();

        // 2. Lock the queue for exclusive access
        mutex.acquire();

        // --- Critical Section ---
        Car car = queue.poll();
        if (car == null) {
            // defensive: shouldn't happen because full.acquire guaranteed an item,
            // but handle gracefully.
            System.err.println("[" + pumpId + "] removeCar: expected a car but found none.");
        } else {
            System.out.println();
            System.out.println("Pump " + pumpId + ": " + car.getCarName() + " taken from queue. (Queue size: " + queue.size() + ")");
            System.out.println();
        }
        // --- End Critical Section ---

        // 3. Unlock the queue
        mutex.release();

        // 4. Signal that one slot is now 'empty'
        empty.release();

        return car;
    }

    public int getWaitingCarCount() {
        return full.availablePermits(); // snapshot of filled slots
    }
}

class Pump extends Thread {
    private final int pumpId;
    private final SharedQueue queue; // Shared queue
    private final Semaphore pumps; // counting available pumps (bays)

    public Pump(int pumpId, SharedQueue queue, Semaphore pumps) {
        this.pumpId = pumpId;
        this.queue = queue;
        this.pumps = pumps;
        setName("Pump-" + pumpId);
    }

    @Override
    public void run() {
        try {
            while (true) {
                // 1) Wait until a car is available and remove it from queue
                Car car = queue.removeCar(pumpId);

                if (car == null) {
                    // Defensive: if no car, loop again
                    continue;
                }

                // 2) Acquire a service bay (pump) before starting service
                pumps.acquire();
                System.out.println();
                System.out.println("Pump " + pumpId + ": " + car.getCarName() + " begins service at Bay " + pumpId);
                Thread.sleep((long) (Math.random() * 4000 + 2000)); // 2–6 sec

                System.out.println("Pump " + pumpId + ": " + car.getCarName() + " finishes service");
                System.out.println("Pump " + pumpId + ": Bay " + pumpId + " is now free");
                System.out.println();
                // 3) signal that the pump bay is now free
                pumps.release();
            }
        } catch (InterruptedException e) {
            System.out.println("Pump " + pumpId + " interrupted.");
            Thread.currentThread().interrupt();
        }
    }
}

class Semaphore {
    private int slots;

    public Semaphore(int initialSlots) {
        if (initialSlots < 0) {
            throw new IllegalArgumentException("Initial slots cannot be negative");
        }
        this.slots = initialSlots;
    }

    public synchronized void acquire() throws InterruptedException {
        while (slots <= 0) {
            wait();
        }
        slots--;
    }

    public synchronized void release() {
        slots++;
        notifyAll();
    }

    public synchronized int availablePermits() {
        return slots;
    }
}

public class ServiceStation {

    public static void main(String[] args) {

        Scanner sc = new Scanner(System.in);

        int queueSize;
        do {
            System.out.print("Enter waiting area size (1 - 10): ");
            queueSize = sc.nextInt();
        } while (queueSize < 1 || queueSize > 10);

        System.out.print("Enter number of pumps: ");
        int pumpCount = sc.nextInt();

        // New inputs:
        System.out.print("Enter total number of cars to generate (0 = continuous): ");
        int maxCars = sc.nextInt(); // if 0 => continuous (original behavior)


        Semaphore pumps = new Semaphore(pumpCount);   // service bays

        // ---- Shared Queue ----
        SharedQueue queue = new SharedQueue(queueSize);

        // ---- Start Pump Threads (Consumers) ----
        Pump[] pumpThreads = new Pump[pumpCount];
        for (int i = 0; i < pumpCount; i++) {
            pumpThreads[i] = new Pump(i + 1, queue, pumps);
            pumpThreads[i].start();
        }

        // ---- Start Car Stream (Producers) ----
        int carId = 1;

        if (maxCars <= 0) {
            // continuous (original behavior), but slowed by arrivalDelayMs
            while (true) {
                String name = "Car " + carId++;
                Thread carThread = new Thread(new Car(name, carId, queue));
                carThread.start();

                try { Thread.sleep((long) (Math.random() * 1000 + 1000)); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
        } else {
            // produce exactly maxCars then wait for processing to complete
            for (; carId <= maxCars; carId++) {
                String name = "Car " + carId;
                Thread carThread = new Thread(new Car(name, carId, queue));
                carThread.start();

                try { Thread.sleep((long) (Math.random() * 1000 + 1000)); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }

            // Wait until queue is empty and all pump bays are free
            try {
                while (true) {
                    int waiting = queue.getWaitingCarCount();
                    int freePumps = pumps.availablePermits();
                    if (waiting == 0 && freePumps == pumpCount) {
                        break; // everything processed
                    }
                    Thread.sleep(1000); // poll interval
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            System.out.println();
            System.out.println("All cars serviced. Shutting down pumps.");

            // cleanly stop pump threads by interrupting them
            for (Pump p : pumpThreads) {
                p.interrupt();
            }
            // optionally join them
            for (Pump p : pumpThreads) {
                try { p.join(); } catch (InterruptedException ignored) {}
            }

            System.out.println("ServiceStation finished.");
        }

        sc.close();
    }
}
