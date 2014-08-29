package test8;

/*
 * 
 * This Proof-of-Concept for nested parallel forEach loops. For JDK1.8
 *   it looks like this:
 *   IntStream.range(0,outerLoop).parallel().forEach(i -> {      
 *      IntStream.range(0,innerLoop).parallel().forEach(j -> {    
 *       
 *        uselessWork(i, j);
 *     }
 *   }
 * 
 * Options:
 *  outerLoop -- iterations in outer loop
 *  innerLoop -- iterations in inner loop
 *  parallelism -- override default Tymeac parallelism level
 *  
 */

import com.tymeac.dse.base.InternalServer;
import com.tymeac.dse.base.Task;
import com.tymeac.dse.base.TymeacInterface;
import com.tymeac.dse.base.TymeacParm;
import com.tymeac.dse.base.TymeacReturn;

/**
 * Test nested parallel forEach loops that simulate the Java8
 *  streams forEach:
 *  IntStream.range(0,outerLoop).parallel().forEach(i -> {      
 *    IntStream.range(0,innerLoop).parallel().forEach(j -> {    
 *      uselessWork(i, j);
 *    });     
 *  });
 *  
 *  First sequentially process nested for-loops,
 *    This can take a long time.
 *    
 *  Then parallel process nested for-loops.
 * 
 * In this version, the main thread can process the return
 *   data from each async request as it happens. This only makes sense  
 *     when there are many processors and 
 *       when the main thread does not steal cycles from the computing or 
 *         the async work often results in a wait state and the main thread
 *         can accomplish something while the async requests are waiting or
 *       when the processing in the main thread can trigger something
 *         important and needs to get going ASAP.
 *   
 * This is also an example of a multiple wait in Java. Other languages support 
 *   waiting for multiple events. Java only supports a single waiting event 
 *   so the event set/process must be manual.
 *   
 *   A common way for the main task to wait for multiple async processes 
 *   to finish is the CountDownLatch. However, the latch only awakens when all
 *   async processes count down.
 *   
 *   What we need here is for the main thread to awaken each time an async
 *   process completes its work (async Task.complete()).
 *   The complete() calls WaitMParallelLoops.post(). The post() sets the
 *   event for that async request, saves the work-to-do and wakes up the 
 *   main thread. The main thread can process that work and then
 *   re-issue a wait().
 * 
 */
public class WaitMParallelLoops {
  
  static final long NPS = (1000L * 1000 * 1000); // for timing
  
  static InternalServer s;
  static TymeacInterface ti;
  static TymeacParm TP;
  
  static long start, end;
  
  static Object waitM = new Object(); // object to wait/post
  static volatile boolean awaken = false; // for the wait()
	
	static final int outerLoop = 200;	 // adjust for your needs
	static final int innerLoop = 2000; // adjust for your needs
	static final int parallelism = 8;	 // parallelism for Tymeac
	
	// count down integer decremented in sync{} in post()
	static int outerCount = outerLoop; 
	
	static long seqCount = 0; // final seq count
	static long parCount = 0;//  final parallel count
		
	/*
	 * each async request places its completion here in sync{}	 
	 */
	static long[] outerStore = new long[outerLoop];
	
	/*
   * These are the event indicators for each async request.
   * The async task.complete() code sets its event to 1 by calling static
   * WaitMParallelLoops.post() [field updated in sync{}].
   * When the main thread processes the event, it sets the
   * event to 2 (nice for debugging.) 
   */
  static int[] events = new int[outerLoop];

/**
 * Start of application
 * @param args 
 */
public static void main(String[] args) {
      
  System.out.println("Parallelism level: " + parallelism);
      
	new WaitMParallelLoops().nestedLoops();
	
	//System.exit(0); 
} // end-method

/**
 * Loops
 */
private void nestedLoops() {
  
  // do the work sequentially (this will take a loooong time)
  seqLoops();    
  
  System.out.println("Total sequential count:" + seqCount);

  double elapsed = (double)(end - start) / NPS;
  System.out.printf("Elapsed sequential time : %5.9f\n", elapsed);
        
  // do the work in parallel with Tymeac
  tymeacLoops();     
  
  System.out.println("Total parallel count:" + parCount);
  
  elapsed = (double)(end - start) / NPS;
  System.out.printf("Elapsed parallel time   : %5.9f\n", elapsed);

} // end-method

/**
 * Do a nested parallel loop
 */
private void tymeacLoops () {
  
  /*
   * Tymeac server setup. we're not timing the Tymeac setup-time here.
   */
  
  // passed args when starting server   
  String[] in = {"-threads", // use threads override (default currently 4)
                 "" + parallelism, // this many threads
                 "-no",      // no verbose - comment line for start up messages
                 "-s"        // stand-a-lone mode (no DBMS)
                };
  
  s = new InternalServer(); // internal, no RMI/IIOP etc      
  ti = s.createServer(in); // start the server with above args
    
  // When failed, stop
  if  (ti == null) { 
      System.out.println("ti is null, createServer failed");
      System.exit(0);
  }
    
  // class data for submitted work
  WaitMAsyncTask myI = new WaitMAsyncTask();  
  Class<? extends Task>  innaC = myI.getClass();  
  
  /*
   * end of server setup
   */
  
  start = System.nanoTime();
  
  TymeacReturn back = null; // return from server
  
  // submit all nested tasks
  for (int i = 0; i < outerLoop; i++) {
    
    // parm for the server: work class, input for that class
    TymeacParm tp = 
        new TymeacParm( innaC, 
                        new WaitMPassClass( i, innerLoop));
        
    try {        
      // call tymeac with an asynchronous request
      back = ti.asyncRequest(tp);       
    
    } catch (Exception e) {      
      System.out.println(e.toString());      
      System.exit(1);      
    } // end-catch  
    
    // When any invalid return, bye
    if  (back.getReturnCode() != 0) {
      
        System.out.println("RC=" + back.getReturnCode() );
        System.exit(1);
    }       
    
  } // end-for 
  
  // wait for all outer loop requests to complete
  //  count decremented in post()
  while (outerCount > 0) {
            
    synchronized (waitM) {  
      
      if  (!awaken) {
    
          try {
            waitM.wait();
          }
          catch (InterruptedException ignore) {}   
      
      } // endif         
    } // end-sync
                  
    /*
     * process results from an async request 
     *   
     *   There could be many threads doing a post() while the processing
     *   takes place. Not every post() will release a wait(). Therefore,
     *   when finished with all processing here, set awaken to false.
     *   
     *   If a post() comes in just before the set of awaken to false, 
     *   we'll miss doing the work now although the results of the async 
     *   request will not be lost. 
     *   
     *     If it is NOT the last request, we'll just need to wait for the next 
     *     post(), no big thing. 
     *   
     *     If it IS the last request or there were multiple post()'s just before
     *     the set and all async requests are complete, then each post() 
     *     decrements the count until it is zero, the while-loop will fall thru, 
     *     and the processing will be handled after the while-loop. 
     *   
     *   If the last request finishes here, then it "appears" there could be a 
     *   never ending wait() since no further async requests will post(). 
     *   BUT since post() decrements the count, the while-loop will fall thru 
     *   and we'll never get to the wait().   
     */
    process();
        
    // will try to wait() until more work
    awaken = false;  
      
  } // end-while
  
  /*
   * process results from any last async request that squeaked in just before
   *   the set of awaken to false. 
   */  
  process();
    
  end = System.nanoTime();  
  
} // end-method

/**
 * process data from each async request in the main thread
 *   only need one pass thru all the events.
 *   if another async request completes after the loop passes the posted event
 *   slot, then we'll get it next pass or the final pass when outerCount = 0.
 *   
 *   If, on the other hand, handling quickly is desireable because the 
 *   async processing is slow (I/O etc.) or the processing here is significant, 
 *   then put the code in a while-loop until a pass thru does not find any 
 *   new events posted. That means an extra full pass thru each time, 
 *   which if outerLoop is large, may be significant.
 */
private void process() {
     
  // check all outer loop results posted now
  for (int i = 0; i < outerLoop; i++) {
          
    // When there was an event
    if  (events[i] == 1) {
      
        // sum the value passed
        parCount += outerStore[i];
        
        // set event processed
        events[i] = 2;
      
    } // endif      
  } // end-for  
  
} // end-method

/**
 * do the work sequentially with the main thread
 */
private void seqLoops () {
   
  start = System.nanoTime();
  
  for (int i = 0; i < outerLoop; i++) {
    for (int j = 0; j < innerLoop; j++) {
      seqCount += uselessWork(i, j);
    }
  }
  
  end = System.nanoTime(); 
  
} // end-method

/**
 * deposit async completion result and wake up the main thread
 *   All storing is within sync{} since main/async threads access the
 *   arrays concurrently.
 * @param accum
 * @param position
 */
protected static void post (long accum, int position) {
  
  synchronized (waitM) {
     
    // save outer loop results, each position is unique
    outerStore[position] = accum;
    
    // set event to 1, each position is unique
    events[position] = 1;
    
    awaken = true;
    outerCount--;
    waitM.notify();
      
  } // end-sync
} // end-method

/**
 * Simulate some work
 * @param i from top loop, j from bottom loop
 * @return i * j
 */
protected static long uselessWork(int i, int j) {
	
  @SuppressWarnings("unused")
  double back = 0.0;
	long max = i * j;
	for(int x=0; x < max; x++) {
		back += Math.sqrt(max);
	}	
			
  return max;
} // end-method
} // end-class
