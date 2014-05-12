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
