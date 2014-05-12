package test8;

/*
 * 
 * This Proof-of-Concept is set up to expose the failure of nested parallel forEach loops.
 *
 * The code is from an example by Christian P. Fries posted originally on
 *   StackOverflow at:
 *     http://stackoverflow.com/questions/23489993/nested-java-8-parallel-foreach-loop-perform-poor-is-this-behavior-expected
 *  
 * The grist of the problem is that a parallel inner forEach loop can take as
 *   much as 2-3 times as long to run as a sequential inner forEach loop.
 *   The parallel version can create 10 times the number of worker threads with
 *   most of those threads simply in a wait state up to 88% of the time. Each
 *   outer loop thread cannot detach from processing the inner loop therefore,
 *   they get stuck and the F/J framework creates new threads.
 *
 * You can run this as-is and see the time it takes to execute both in 
 *   parallel as well as sequential.
 * Running in a profiler (visualVM for one) shows just how awful the execution
 *   of parallel inner loops can be. A huge number of ForkJoinWorkerThreads
 *   are created with most of them sitting idle (awaitJoin()) for 80-90% of
 *   the time. Use the below option, USE_DELAY = true; to help establish the
 *   profiler linkage before execution. The number of iterations of both
 *   inner and outer is geared so execution takes long enough so you can see
 *   what is happening in a profiler. Adjust to your needs.
 *   
 * The Tymeac run is for comparison to a scatter-gather system. You will need
 *   the TymeacDSELite.jar file from the package. The source and documentation
 *   for Tymeac is at:
 *   http://sourceforge.net/projects/tymeacdse/
 * 
 * Options:
 *  type_run  -- run with parallel or sequential inner loop with streams, or
 *                 with Tymeac scatter-gather.
 *  USE_DELAY -- delay the start of test to enable a profiler
 *  outerLoop -- iterations in outer loop
 *  innerLoop -- iterations in inner loop
 *  parallelism -- override default ForkJoinPool/Tymeac parallelism level
 *  
 * You will need the current JDK1.8 
 * You will need the TymeacDSELite.jar file
 */

import java.util.stream.IntStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.LongAdder;

import com.tymeac.dse.base.InternalServer;
import com.tymeac.dse.base.Task;
import com.tymeac.dse.base.TymeacInterface;
import com.tymeac.dse.base.TymeacParm;
import com.tymeac.dse.base.TymeacReturn;

/**
 * Test nested parallel forEach loops
 * 
 */
public class NestedParallel {
  
  static final long NPS = (1000L * 1000 * 1000); // for timing
  
  static InternalServer s;
  static TymeacInterface ti;
  static TymeacParm TP;
  
  static long start, end;
  
  static final int seq_run  = 1;
  static final int para_run = 2;
  static final int ty_run   = 3;  
  static final int type_run = seq_run;  // type of run *** adjust here ***
  
  // for proof that all runs generate same number of tasks
  static final LongAdder respository = new LongAdder(); 
  
  // When using a profiler, it is sometimes best to delay formal execution until
  //   you've had a chance to establish linkage. Therefore, you can delay
  //   execution for 10 seconds with this option set to "true"
  static final boolean USE_DELAY = false;
	
	static final int outerLoop = 200;	 // adjust for your needs
	static final int innerLoop = 4000; // adjust for your needs
	static final int parallelism = 8;	 // parallelism for common FJPool and Tymeac
  
  // Thread message for each outer loop, will print at end of run
  static final Thread[] println = new Thread[outerLoop];

  /**
   * Start of application
   * @param args 
   */
	public static void main(String[] args) {
    
    if  (USE_DELAY) 
      try {Thread.sleep(10000);} catch (InterruptedException e) {}
    
    System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism",
                        Integer.toString(parallelism));
    System.out.println("Parallelism level: " 
                       + parallelism);
        
		new NestedParallel().nestedLoops();
	}

/**
 * Loops
 */
public void nestedLoops() {
  
  String type = null;
  
  switch (type_run) {
            
      case 1: 
        type = "sequential";
        System.out.println("Using " + type + " inner loop");
        seqLoops();
        break;
      
      case 2: 
        type = "parallel";
        System.out.println("Using " + type + " inner loop");
        paraLoops();
      break;
      
      case 3: 
        type = "Tymeac";
        System.out.println("Using " + type + " inner loop");
        tymeacLoops();
      break; 
      
      default: 
        System.out.println("Set type_run to 1, 2 or 3");
        System.exit(0);
    }
    
  // print thread info
  for (int i = 0; i < outerLoop; i++)       
    System.out.println(i + "\t" + println[i]);   
  
  System.out.println("Total: " + respository.sum()); 

  double elapsed = (double)(end - start) / NPS;
  System.out.printf("Elapsed time : %5.9f\n", elapsed);

  System.exit(0); // necessary since Tymeac starts RMI threads
}
	
private void seqLoops () {
	  
  start = System.nanoTime();
    
  // Outer loop always parallel    
  IntStream.range(0,outerLoop).parallel().forEach(i -> {
      
    // thread info
    NestedParallel.setThreadData(i);
      
    IntStream.range(0,innerLoop).sequential().forEach(j -> {
    
      uselessWork(i, j);
      });     
  });

  end = System.nanoTime();	 	  
}

private void paraLoops () {
    
  start = System.nanoTime();
    
  // Outer loop always parallel    
  IntStream.range(0,outerLoop).parallel().forEach(i -> {
      
    // thread info
    NestedParallel.setThreadData(i);
      
    IntStream.range(0,innerLoop).parallel().forEach(j -> {
    
      uselessWork(i, j);
    });     
  });

  end = System.nanoTime();      
}

private void tymeacLoops () {
  
  /*
   * Tymeac server setup. Since the F/J framework uses the submitting thread
   *   as a worker thread it masks the framework setup-time. 
   *   Therefore, we're not timing the Tymeac setup-time here.
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
  
  // will wait for all outer submits to complete
  CountDownLatch latch = new CountDownLatch(outerLoop);
  
  // class data for submitted work
  NestedAsyncTask myI = new NestedAsyncTask();  
  Class<? extends Task>  innaC = myI.getClass();
  
  // Parm for the server: class object. will set input later
  TymeacParm TP = new TymeacParm (innaC, null); 
  
  /*
   * end of server setup
   */
  
  start = System.nanoTime();
  
  // submit all nested tasks
  for (int i = 0; i < outerLoop; i++) {
    
    // new input for new submission: outer loop number, total inner loop, countdown
    TP.setInput(new PassClass(i, innerLoop, latch));
    
    try {        
      // call tymeac with a asynchronous request
      Object[] back = ti.asyncRequest(TP); 
       
      // When any invalid return, bye
      if  (!checkBack(back)) System.exit(1);        
    
    } catch (Exception e) {      
      System.out.println(e.toString());      
      System.exit(1);      
    } // end-catch    
  } // end-for 
  
  // wait until done
  try {
    latch.await();
  }
  catch (InterruptedException ignore) {}
  
  end = System.nanoTime();  
  
} // end-method

/**
 * Check the results of calling the Tymeac Server.
 * The return is an array of objects.
 * 1st and only object for async call is the result of tymeac processing:
 *   nnnn is the return code. See doc for non-zero.  
 *   
 * @param back from call
 * @return success or fail
 */
static boolean checkBack (Object[] back) {
  
  // reformat the return array
  TymeacReturn ret = TymeacReturn.formatCallReturn(back);
  
  // return code from tymeac
  int rc = ret.getReturnCode();
  
  // When no back
  if  (rc == 9001)  {
        
    // say no good  
    System.out.println("back returned null");
    return false;

  } // endif  
  
  // When any error
  if  (rc != 0) {
    
      System.out.println(ret.getTyMessage()); // problem
      return false;
      
  } // endif
  
  return true;       
  
} // end-method

// save the current thread info for print at end of run
/**
 * save the current thread info for print at end of run.
 * this should be a callback, but for p.o.c., the easy way
 * @param thread number
 */
protected static void setThreadData(int i) {
  
  // thread info, will print at end of run
  println[i] = Thread.currentThread();
  
} // end-method
	
	
/**
 * Simulate some work
 * @param i from top loop, j from bottom loop
 * @return ignored
 */
protected static double uselessWork(int i, int j) {
	
  double back = 0.0;
	long max = i * j;
	for(int x=0; x < max; x++) {
		back += Math.sqrt(max);
	}	
	
	// for proof that all runs generate same number of tasks
	respository.add(i + j);
		
  return back;
} // end-method
} // end-class
