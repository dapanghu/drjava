/*BEGIN_COPYRIGHT_BLOCK
 *
 * This file is part of DrJava.  Download the current version of this project from http://www.drjava.org/
 * or http://sourceforge.net/projects/drjava/
 *
 * DrJava Open Source License
 * 
 * Copyright (C) 2001-2005 JavaPLT group at Rice University (javaplt@rice.edu).  All rights reserved.
 *
 * Developed by:   Java Programming Languages Team, Rice University, http://www.cs.rice.edu/~javaplt/
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated 
 * documentation files (the "Software"), to deal with the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and 
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 * 
 *     - Redistributions of source code must retain the above copyright notice, this list of conditions and the 
 *       following disclaimers.
 *     - Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the 
 *       following disclaimers in the documentation and/or other materials provided with the distribution.
 *     - Neither the names of DrJava, the JavaPLT, Rice University, nor the names of its contributors may be used to 
 *       endorse or promote products derived from this Software without specific prior written permission.
 *     - Products derived from this software may not be called "DrJava" nor use the term "DrJava" as part of their 
 *       names without prior written permission from the JavaPLT group.  For permission, write to javaplt@rice.edu.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO 
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE 
 * CONTRIBUTORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF 
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS 
 * WITH THE SOFTWARE.
 * END_COPYRIGHT_BLOCK*/

package edu.rice.cs.util.newjvm;

import edu.rice.cs.util.Log;
import edu.rice.cs.util.UnexpectedException;
import edu.rice.cs.drjava.config.FileOption;

import java.rmi.*;
import java.rmi.server.*;
import java.io.*;
import java.util.Arrays;

/** An abstract class implementing the logic to invoke and control, via RMI, a second Java virtual 
 *  machine. This class is used by subclassing it. (See package documentation for more details.)
 *  This class runs in both the master and the slave JVMs.
 *  @version $Id$
 */
public abstract class AbstractMasterJVM/*<SlaveType extends SlaveRemote>*/
  implements MasterRemote/*<SlaveType>*/ {
  
  protected static final Log _log  = new Log("MasterSlave.txt", false);
  
  /** Name for the thread that waits for the slave to exit. */
  protected volatile String _waitForQuitThreadName = "Wait for SlaveJVM Exit Thread";
  
  /** Name for the thread that exports the MasterJVM to RMI. */
  protected volatile String _exportMasterThreadName = "Export MasterJVM Thread";
  
  /** Lock for accessing the critical state of this AbstractMasterJVM.  */
  protected final Object _masterJVMLock = new Object();
  
  private static final String RUNNER = SlaveJVMRunner.class.getName();
  
  /** The slave JVM remote stub if it's connected; null if not connected. */
  private volatile SlaveRemote _slave = null;

  /** Is slave JVM in the progress of starting up? */
  private volatile boolean _startupInProgress = false;

  /** This flag is set when a quit request is issued before the slave has even finished starting up. 
   *  In that case, immediately after starting up, we quit it.
   */
  private volatile boolean _quitOnStartup = false;
  
  /** Lock used in exporting this object to a file and loading it in the slaveJVM; protects stub variables. */
  final static Object _exportLock = new Object();

  /** The current remote stub for this main JVM object. This field is null except between the time the slave
   *  JVM is first invoked and the time the slave registers itself.
   */
  private volatile Remote _stub;
  
  /** The file containing the serialized remote stub. This field is null except between the time the slave
   *  JVM is first invoked and the time the slave registers itself.
   */
  private volatile File _stubFile;
  
  /** The remote class loader object used by the Slave JVM to load classes. */
  private volatile RemoteClassLoader _classLoader;

  /** The current remote stub for this main JVM's classloader. This field is null except between the time 
   *  the slave JVM is first invoked and the time the slave registers itself.
   */
  private volatile Remote _classLoaderStub;

  /** The file containing the serialized remote classloader stub. This field is null except between the 
   *  time the slave JVM is first invoked and the time the slave registers itself.
   */
  volatile File _classLoaderStubFile;
  
  /** The fully-qualified name of the slave JVM class. */
  private final String _slaveClassName;

  /** Sets up the master JVM object, but does not actually invoke the slave JVM.
   *  @param slaveClassName The fully-qualified class name of the class to start up in the second JVM. This 
   *  class must implement the interface specified by this class's type parameter, which must be a subclass 
   *  of {@link SlaveRemote}.
   */
  protected AbstractMasterJVM(String slaveClassName) {
    _slaveClassName = slaveClassName;
    
    // Make sure RMI doesn't use an IP address that might change
    System.setProperty("java.rmi.server.hostname", "127.0.0.1");
  }

  /** Callback for when the slave JVM has connected, and the bidirectional communications link has been 
   *  established.  During this call, {@link #getSlave} is guaranteed to not return null.
   */
  protected abstract void handleSlaveConnected();
  
  /** Callback for when the slave JVM has quit. During this call, {@link #getSlave} is guaranteed to return null.
   *  @param status The exit code returned by the slave JVM.
   */
  protected abstract void handleSlaveQuit(int status);
  
  /** Invokes slave JVM without any JVM arguments.
   *  @throws IllegalStateException if slave JVM already connected or startup is in progress.
   */
  protected final void invokeSlave() throws IOException, RemoteException {
    invokeSlave(new String[0], FileOption.NULL_FILE);
  }
  
  /** Invokes slave JVM, using the system classpath.
   *  @param jvmArgs Array of arguments to pass to the JVM on startup
   *  @throws IllegalStateException if slave JVM already connected or startup is in progress.
   */
  protected final void invokeSlave(String[] jvmArgs, File workDir) throws IOException, RemoteException {
    invokeSlave(jvmArgs, System.getProperty("java.class.path"), workDir);
  }
 
  /** Creates and invokes slave JVM.
   *  @param jvmArgs Array of arguments to pass to the JVM on startup
   *  @param cp Classpath to use when starting the JVM
   *  @throws IllegalStateException if slave JVM already connected or startup is in progress.
   */
  protected final void invokeSlave(String[] jvmArgs, String cp, File workDir) throws IOException, RemoteException {
    
    _log.log(this + ".invokeSlave(" + Arrays.toString(jvmArgs) + ", " + cp + ", " + workDir + ") called");
    
    synchronized(_masterJVMLock) {
      
      if (_startupInProgress) throw new IllegalStateException("startup is in progress in invokeSlave");      
      if (_slave != null) throw new IllegalStateException("slave non-null in invoke: " + _slave);
      
      _startupInProgress = true;
      _stub = null;
    }
    /**********************************************
     * First, we we export ourselves to a file... *
     **********************************************/
    
//      Thread t = new Thread(_exportMasterThreadName) {
//        public void run() {
    _log.log(AbstractMasterJVM.this + " starting creation of RMI stub for AbstractMasterJVM");
    try { _stub = UnicastRemoteObject.exportObject(AbstractMasterJVM.this); }
    
    // Debug: check that the IP address is 127.0.0.1
    // javax.swing.JOptionPane.showMessageDialog(null, _stub.toString());
    
    catch (RemoteException re) {
      // javax.swing.JOptionPane.showMessageDialog(null, edu.rice.cs.util.StringOps.getStackTrace(re));
      throw new UnexpectedException(re);  // should never happen
    }
//          synchronized(_exportLock) { _exportLock.notify(); }
//        }
//      };
    
//      t.start();
//      synchronized(_exportLock) {
//        try {
//          while (_stub == null) { 
//            _log.log("invokeSlave thread in " + this + " waiting for creation of AbstractMasterJVM RMI stub to complete");
//            _exportLock.wait(); 
//          } 
//        }
//        catch (InterruptedException ie) { throw new UnexpectedException(ie); }  // should never happen
    
    _log.log(this + " completed creation of RMI stub for AbstractMasterJVM");
    _stubFile = File.createTempFile("DrJava-remote-stub", ".tmp");
    _stubFile.deleteOnExit();
    
    // serialize stub to _stubFile
    FileOutputStream fstream = new FileOutputStream(_stubFile);
    ObjectOutputStream ostream = new ObjectOutputStream(fstream);
    ostream.writeObject(_stub);
    ostream.flush();
    fstream.close();
//      ostream.close();
    
    _log.log(this + " completed writing RMI stub for AbstractMasterJVM to a file");
    
    /***********************************************************************************
     * Done exporting ourselves to a file ...  Now let's export our classloader        *
     * This will be used to handle classloading requests from the slave jvm.           *
     ***********************************************************************************/
    
    final RemoteClassLoader _classLoader = new RemoteClassLoader(getClass().getClassLoader());
    _classLoaderStub = null;
//      t = new Thread(_exportMasterThreadName) {
//        public void run() {
    _log.log(AbstractMasterJVM.this + " starting creation of RMI stub for RemoteClassLoader");
    try {  _classLoaderStub = UnicastRemoteObject.exportObject(_classLoader); }
    
    // Debug: check that the IP address is 127.0.0.1
    //javax.swing.JOptionPane.showMessageDialog(null, _stub.toString());
    
    catch (RemoteException re) {
      //javax.swing.JOptionPane.showMessageDialog(null, edu.rice.cs.util.StringOps.getStackTrace(re));
      throw new UnexpectedException(re);  // should never happen
    }
//          synchronized(_exportLock) { _exportLock.notify(); }
//        }
//      };
    
//      t.start();
//      synchronized(_exportLock) {
//        try { 
//          while (_classLoaderStub == null) { 
//            _log.log("invokeSlave thread in " + this + " waiting for creation of RemoteClassLoader RMI stub to complete");
//            _exportLock.wait(); 
//          } 
//        }
//        catch (InterruptedException ie) { throw new UnexpectedException(ie); }  // should never happen
//      }
    
    _log.log(this + " completed creation of RMI stub for RemoteClassLoader");
    _classLoaderStubFile = File.createTempFile("DrJava-remote-stub", ".tmp");
    _classLoaderStubFile.deleteOnExit();
    // serialize stub to _classLoaderStubFile
    fstream = new FileOutputStream(_classLoaderStubFile);
    ostream = new ObjectOutputStream(fstream);
    ostream.writeObject(_classLoaderStub);
    ostream.flush();
    fstream.close();
    ostream.close();
    
    _log.log(this + " completed writing RMI stub for RemoteClassLoader to a file");
    
    String[] args = 
      new String[] { _stubFile.getAbsolutePath(), _slaveClassName, _classLoaderStubFile.getAbsolutePath() };
    
    /* Create the slave JVM. */  
    _log.log(this + " is starting a slave JVM");
    final Process process = ExecJVM.runJVM(RUNNER, args, cp, jvmArgs, workDir);
    
    // Start a thread to wait for the slave to die.  When it dies, delegate what to do (restart?) to subclass
    Thread restartThread = new Thread(_waitForQuitThreadName) {
      public void run() {
        _log.log(this + "has started a Slave monitor thread waiting on process " + process);
        try {
          int status = process.waitFor();
          _log.log("Process " + process + " died under control of " + AbstractMasterJVM.this + " with status " + status);
          synchronized(_masterJVMLock) {
            if (_startupInProgress) {
              _log.log("Process " + process + " died while starting up");
              /* If we get here, the process died without registering. (This might be the case if something was wrong
               * with the classpath, or if the new JVM couldn't acquire a port for debugging.)  Proper behavior in 
               * this case is unclear, so we'll let our subclasses decide.  By default, we print a stack trace and 
               * do not proceed, to avoid going into a loop. */
              slaveQuitDuringStartup(status);
            }
            _slave = null;
            final boolean masterWithdrawn = UnicastRemoteObject.unexportObject(AbstractMasterJVM.this, true);
            final boolean loaderWithdrawn = UnicastRemoteObject.unexportObject(_classLoader, true);
            if (! masterWithdrawn || ! loaderWithdrawn) {
              _log.log("unexport step failed in " + AbstractMasterJVM.this);
              throw new UnexpectedException("remote objects exported by Master JVM could not be withdrawn!");
            }
            
            _log.log(AbstractMasterJVM.this + " calling handleSlaveQuit(" + status + ")");
            handleSlaveQuit(status);
          }
        }
        catch (NoSuchObjectException e) { throw new UnexpectedException(e); }
        catch (InterruptedException ie) { throw new UnexpectedException(ie); }
      }
    };
    _log.log(this + " is starting a slave monitor thread to detect when the Slave JVM dies");
    restartThread.start();
  }
  
  /** Action to take if the slave JVM quits before registering.  Assumes _masterJVMLock is held.
   *  @param status Status code of the JVM
   */
  protected void slaveQuitDuringStartup(int status) {
    // Reset Master JVM state (in case invokeSlave is called again on this object)
    _startupInProgress = false;
    _quitOnStartup = false;
    String msg = "SlaveJVM quit before registering!  Status: " + status;
    throw new IllegalStateException(msg);
  }
  
  /** Called if the slave JVM dies before it is able to register.
   *  @param cause The Throwable which caused the slave to die.
   */
  public abstract void errorStartingSlave(Throwable cause) throws RemoteException;
  
  /** No-op to prove that the master is still alive. */
  public void checkStillAlive() { }
  
  /* Records the identity and status of the Slave JVM in the Master JVM */
  public void registerSlave(SlaveRemote slave) throws RemoteException {
    _log.log(this + " registering Slave " + slave);
    synchronized(_masterJVMLock) {
      _slave = slave;
      _startupInProgress = false;
      _stubFile.delete();
      _stub = null;
      _classLoaderStub = null;
      _classLoaderStubFile.delete();
      
      _log.log(this + " calling handleSlaveConnected()");
      
      handleSlaveConnected();
      
      if (_quitOnStartup) {
        // quitSlave was called before the slave registered, so we now act on the deferred quit request.
        _quitOnStartup = false;
        quitSlave();
      }
    }
  }
  
  /** Quits slave JVM.
   *  @throws IllegalStateException if no slave JVM is connected
   */
  protected final void quitSlave() throws RemoteException {
    _log.log(this + ".quitSlave() called");
    synchronized(_masterJVMLock) {
      if (isStartupInProgress())
        /* There is a slave to be quit, but we don't have a handle to it yet. Instead we set this flag, which makes it
         * quit immediately after it registers in registerSlave. */
        _quitOnStartup = true;
      
      else if (_slave == null)  {
        System.out.println("Slave JVM quit operation invoked when no slave running");
        _log.log(this + " called quitSlave() when no slave was running");
//        throw new IllegalStateException("tried to quit when no slave running and startup not in progress");
      }
      else {
        _slave.quit();
        _slave = null;  // Remove reference to Slave JVM
      }
    }
  }
  
  /** Returns slave remote instance, or null if not connected. */
  protected final SlaveRemote getSlave() {  return _slave; }
  
  /** Returns true if the slave is in the process of starting. */
  protected boolean isStartupInProgress() { return _startupInProgress; }
}
