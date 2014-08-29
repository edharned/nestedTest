package test8;

/**
 * class passed to each async request
 *
 */
public class WaitMPassClass {

  private int i, j; // outer loop, inner loop
  
public WaitMPassClass(int i, int j) {
  
  this.i = i;
  this.j = j;
}
  
public int getI () {return i; };
public int getJ () {return j; };

} // end-class
