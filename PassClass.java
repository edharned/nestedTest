package test8;

import java.util.concurrent.CountDownLatch;

/**
 * class passed to each async request
 *
 */
public class PassClass {

  private int i, j; // outer loop, inner loop
  private CountDownLatch latch;
  
public PassClass(int i, CountDownLatch latch) {
  
  this.i = i;
  this.latch = latch;
}

public PassClass(int i, int j, CountDownLatch latch) {
  
  this.i = i;
  this.j = j;
  this.latch = latch;
}
  
public int getI () {return i; };
public int getJ () {return j; };
public CountDownLatch  getLatch()  { return latch; }

} // end-class
