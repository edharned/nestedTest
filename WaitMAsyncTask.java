package test8;


import com.tymeac.dse.base.Task;

/**
 * Inner loop async task, one for each outer loop element
 *
 */
public class WaitMAsyncTask extends Task {
  
      private static final long serialVersionUID = 266374798056742783L; 
  
  /**
   * Object passed to to forked tasks
   *
   */
  private class PassToJ {
  
    private int i, j;
    private long accum;
    
  protected PassToJ (int i, int j) {
    
    this.i  = i;
    this.j  = j;
  }
  
  protected int getI() { return i; }
  protected int getJ() { return j; }
  protected long getAccum() { return accum; }
  protected void setAccum(long a) { accum = a; }
  
  } // end-inner-class
    
/**
 * main computation from forking, or
 *   initial entry for 1st task    
 */
@Override
public Object compute() {   
  
  Object obj = getInput();
  
  if  (obj == null) {       
    
    System.out.println("WaitMAsyncTask Class had no input object");
    return null;
  }
  
  // When a forked object
   if  (obj instanceof PassToJ) {
     
       PassToJ toJ = (PassToJ) obj;
       int     myI = toJ.getI();
       int     myJ = toJ.getJ();
       
       // do actual work in lower nested loop
       toJ.setAccum(WaitMParallelLoops.uselessWork(myI, myJ));
              
       return toJ;     
     
   } // endif
  
  // When not initial task, kill
  if (!(obj instanceof WaitMPassClass)) 
    throw new IllegalArgumentException("WaitMAsyncTask: Expecting Class WaitMPassClass");
  
  WaitMPassClass myPass = (WaitMPassClass) obj;
  
  int local_I = myPass.getI(); // outer loop number
  int local_j = myPass.getJ(); // total inner loop tasks 
  
  int nbr_forks = local_j - 1; // will do last computation in this thread  
      
  // fork nested tasks
  for (int j = 0; j < nbr_forks; j++) {
    
    // create each task, except last to do the lower level work
    if  (fork(new PassToJ(local_I, j)) != 0) return null;
    
  } // end-for 
  
  // do last one here
  PassToJ local = new PassToJ(local_I, nbr_forks);
  local.setAccum(WaitMParallelLoops.uselessWork(local_I, nbr_forks));
  
  return local;
  
} // end-method

/**
 * all tasks have completed, tell initiator this async request completed
 */
@Override
public Object complete() {   
  
  Object[] obj = getOutput();
    
  if (!(obj[0] instanceof PassToJ)) 
    throw new IllegalArgumentException("WaitMAsyncTask.complete(): Expecting Class PassToJ");
  
  int len = obj.length;
    
  long accum = 0;
  
  // accum the results of each forked task
  for (int i = 0; i < len; i++)   
    accum += ((PassToJ) obj[i]).getAccum();   
  
  // say work done for this async request
  //  the sum
  //  the outer loop number (same for all tasks so [0] is ok)
  WaitMParallelLoops.post(accum, ((PassToJ)obj[0]).getI());  
  
  return null;  
  
} // end-method
} // end-class
