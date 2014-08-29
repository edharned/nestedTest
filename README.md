nestedTest
=========

This Proof-of-Concept is set up to expose the failure of nested parallel forEach loops in JDK1.8.

You can run this as-is and see the time it takes to execute both in parallel as well as sequential.
The Tymeac run is for comparison to a scatter-gather system. You will need the TymeacDSELite.jar file from the package. The source and documentation for Tymeac is at:
http://sourceforge.net/projects/tymeacdse/

NestedParallel.java -- main class
NextedAsyncTask.java -- Tymeac async task
PassClass.java -- passed class to Tymeac async task

TymeacDSELite.jar -- TymeacDSE classes without demos

Sometimes the main thread can process the return of each asynchronous request as it completes. That is, when there is sufficient work the main thread can perform when one or more asynchrous requests complete.

To demonstrate this concurrent processing are the following:

WaitMParallelLoops.jave
WaitMAsyncTask.java
WaitMPassClass.java

These classes also demonstrate how to do a multiple wait in Java. (waitm is supported in other languages.)