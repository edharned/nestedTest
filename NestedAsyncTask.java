package test8;

import java.util.concurrent.CountDownLatch;
import com.tymeac.dse.base.Task;

/**
 * Inner loop async task, one for each outer loop element
 *
 */
public class NestedAsyncTask extends Task {
  
      private static final long serialVersionUID = 266374798056742783L; 
  
  /**
   * Object passed to to forked tasks
   *
   */
  private class PassToJ {
  
    private int i, j;
    private CountDownLatch latch;
    
  protected PassToJ (int i, int j, CountDownLatch latch) {
    
    this.i  = i;
    this.j  = j;
    this.latch = latch;
  }
  
  protected CountDownLatch  getLatch()  { return latch; }
  protected int             getI()      { return i;  }
  protected int             getJ()      { return j;  }
  
  } // end-inner-class
    
/**
 * main computation from forking, or
 *   initial entry for 1st task    
 */
@Override
public Object compute() {   
  
  Object obj = getInput();
  
  if  (obj == null) {       
    
    System.out.println("AsyncI Class had no input object");
    return null;
  }
  
  // When a forked object
   if  (obj instanceof PassToJ) {
     
       PassToJ toJ = (PassToJ) obj;
       int     myI = toJ.getI();
       int     myJ = toJ.getJ();
       
       // do actual work in lower nested loop
       NestedParallel.uselessWork(myI, myJ);
       
       // Only need one for complete()
       if  (myJ == 0) return toJ;
       
       return null;     
     
   } // endif
  
  // When not initial task, kill
  if (!(obj instanceof PassClass)) 
    throw new IllegalArgumentException("AsyncI: Expecting Class PassClass");
  
  PassClass myPass = (PassClass) obj;
  
  int             local_I = myPass.getI(); // outer loop number
  int             local_j = myPass.getJ(); // total inner loop tasks 
  CountDownLatch  latch   = myPass.getLatch();
  
  int nbr_forks = local_j - 1; // will do last computation in this thread  
      
  // fork nested tasks
  for (int j = 0; j < nbr_forks; j++) {
    
    // create each task, except last to do the lower level work
    if  (fork(new PassToJ(local_I, j, latch)) != 0) return null;
    
  } // end-for  
  
  // do last one here
  NestedParallel.uselessWork(local_I, nbr_forks);
  
  // thread info for outer loop
  NestedParallel.setThreadData(local_I);
  
  return null;
  
} // end-method

/**
 * all tasks have completed, tell initiator this async request completed
 */
@Override
public Object complete() {   
  
  Object[] obj = getOutput();
    
  if (!(obj[0] instanceof PassToJ)) 
    throw new IllegalArgumentException("AsyncI.complete(): Expecting Class PassToJ");
  
  PassToJ myPass = (PassToJ) obj[0];
  
  // say work done for this async request
  myPass.getLatch().countDown();
  
  return null;  
  
} // end-method
} // end-class
