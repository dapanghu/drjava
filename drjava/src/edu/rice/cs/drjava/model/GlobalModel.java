/*BEGIN_COPYRIGHT_BLOCK
 *
 * This file is a part of DrJava. Current versions of this project are available
 * at http://sourceforge.net/projects/drjava
 *
 * Copyright (C) 2001-2002 JavaPLT group at Rice University (javaplt@rice.edu)
 *
 * DrJava is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * DrJava is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 * or see http://www.gnu.org/licenses/gpl.html
 *
 * In addition, as a special exception, the JavaPLT group at Rice University
 * (javaplt@rice.edu) gives permission to link the code of DrJava with
 * the classes in the gj.util package, even if they are provided in binary-only
 * form, and distribute linked combinations including the DrJava and the
 * gj.util package. You must obey the GNU General Public License in all
 * respects for all of the code used other than these classes in the gj.util
 * package: Dictionary, HashtableEntry, ValueEnumerator, Enumeration,
 * KeyEnumerator, Vector, Hashtable, Stack, VectorEnumerator.
 *
 * If you modify this file, you may extend this exception to your version of the
 * file, but you are not obligated to do so. If you do not wish to
 * do so, delete this exception statement from your version. (However, the
 * present version of DrJava depends on these classes, so you'd want to
 * remove the dependency first!)
 *
END_COPYRIGHT_BLOCK*/

package edu.rice.cs.drjava.model;

import javax.swing.text.*;
import javax.swing.ListModel;
import java.io.*;
import java.util.*;

import gj.util.Vector;

import edu.rice.cs.util.swing.FindReplaceMachine;

import edu.rice.cs.drjava.DrJava;
import edu.rice.cs.util.UnexpectedException;
import edu.rice.cs.util.text.SwingDocumentAdapter;
import edu.rice.cs.drjava.model.definitions.*;
import edu.rice.cs.drjava.model.debug.Debugger;
import edu.rice.cs.drjava.model.repl.*;
import edu.rice.cs.drjava.model.junit.JUnitError;
import edu.rice.cs.drjava.model.compiler.*;

import junit.framework.TestResult;

/**
 * Handles the bulk of DrJava's program logic.
 * The UI components interface with the GlobalModel through its public methods,
 * and GlobalModel responds via the GlobalModelListener interface.
 * This removes the dependency on the UI for the logical flow of the program's
 * features.  With the current implementation, we can finally test the compile
 * functionality of DrJava, along with many other things.
 *
 * @version $Id$
 */
public interface GlobalModel {
  /**
   * Add a listener to this global model.
   * @param listener a listener that reacts on events generated by the GlobalModel
   */
  public void addListener(GlobalModelListener listener);

  /**
   * Remove a listener from this global model.
   * @param listener a listener that reacts on events generated by the GlobalModel
   */
  public void removeListener(GlobalModelListener listener);
  
  /**
   * Keeps track of all listeners to this model.
   */
  public EventNotifier getNotifier();

  /**
   * Fetches the {@link javax.swing.EditorKit} implementation for use
   * in the definitions pane.
   */
  public DefinitionsEditorKit getEditorKit();

  /**
   * Gets a ListModel of the open definitions documents.
   */
  public ListModel getDefinitionsDocuments();

  /**
   * Gets the (toolkit-independent) interactions document.
   */
  public InteractionsDocument getInteractionsDocument();
  
  /**
   * Gets the Swing adapter used in the Interactions document.
   * This should ideally be refactored so the model doesn't have
   * to use Swing, but all the other documents are Swing anyway.
   * (Interactions are special because they're used in Eclipse.)
   */
  public SwingDocumentAdapter getSwingInteractionsDocument();

  /**
   * Gets the junit document.
   */
  public StyledDocument getJUnitDocument();

  /**
   * Gets the console document.
   */
  public StyledDocument getConsoleDocument();

  /**
   * Gets the array of all compile errors without Files.
   */
  public CompilerError[] getCompilerErrorsWithoutFiles();

  /**
   * Gets the total number of current errors.
   */
  public int getNumErrors();
  
  /**
   * Resets the compiler error state to have no errors.
   */
  public void resetCompilerErrors();

  /**
   * Creates a new document in the definitions pane and
   * adds it to the list of open documents.
   * @return The new open document
   */
  public OpenDefinitionsDocument newFile();

  /**
   * Open a file and read it into the definitions.
   * The provided file selector chooses a file, and on a successful
   * open, the fileOpened() event is fired.
   * @param com a command pattern command that selects what file
   *            to open
   * @return The open document, or null if unsuccessful
   * @exception IOException
   * @exception OperationCanceledException if the open was canceled
   * @exception AlreadyOpenException if the file is already open
   */
  public OpenDefinitionsDocument openFile(FileOpenSelector com)
    throws IOException, OperationCanceledException, AlreadyOpenException;

  public OpenDefinitionsDocument openFiles(FileOpenSelector com)
    throws IOException, OperationCanceledException, AlreadyOpenException;

  /**
   * Closes an open definitions document, prompting to save if
   * the document has been changed.  Returns whether the file
   * was successfully closed.
   * @return true if the document was closed
   */
  public boolean closeFile(OpenDefinitionsDocument doc);

  /**
   * Attempts to close all open documents.
   * @return true if all documents were closed
   */
  public boolean closeAllFiles();
  
  /**
   * Reverts all open files.
   * (Not working yet.)
  public void revertAllFiles() throws IOException;
  */

  /**
   * Saves all open documents, prompting when necessary.
   */
  public void saveAllFiles(FileSaveSelector com) throws IOException;

  /**
   * Saves all open documents, used for testing
   */
  public void saveAllFiles(FileSaveSelector com[]) throws IOException;

  /**
   * Exits the program.
   * Only quits if all documents are successfully closed.
   */
  public void quit();

  /**
   * Returns the OpenDefinitionsDocument for the specified
   * File, opening a new copy if one is not already open.
   * @param file File contained by the document to be returned
   * @return OpenDefinitionsDocument containing file
   */
  public OpenDefinitionsDocument getDocumentForFile(File file)
    throws IOException, OperationCanceledException;

  /**
   * Clears and resets the interactions pane.
   * First it makes sure it's in the right package given the
   * package specified by the definitions.  If it can't,
   * the package for the interactions becomes the defualt
   * top level. In either case, this method calls a helper
   * which fires the interactionsReset() event.
   */
  public void resetInteractions();
  
  /**
   * Called when the interactionsJVM has begun to be resetted
   */
  public void interactionsResetting();
  
  /**
   * Called when a new InteractionsJVM has registered as is ready 
   * for use.
   */
  public void interactionsReady();

  /**
   * Resets the console.
   * Fires consoleReset() event.
   */
  public void resetConsole();

  /**
   * Interprets the current given text at the prompt in the interactions
   * pane.
   */
  public void interpretCurrentInteraction();

  /** Prints System.out to the DrJava console. */
  public void systemOutPrint(String s);

  /** Prints System.err to the DrJava console. */
  public void systemErrPrint(String s);

  /** Called when the repl prints to System.out. */
  public void replSystemOutPrint(String s);

  /** Called when the repl prints to System.err. */
  public void replSystemErrPrint(String s);

  /** Called when the debugger wants to print a message. */
  public void printDebugMessage(String s);
  
  /**
   * Blocks until the interpreter has registered.
   */
  public void waitForInterpreter();

  /**
   * Signifies that the most recent interpretation completed successfully,
   * returning no value.
   */
  public void replReturnedVoid();

  /**
   * Signifies that the most recent interpretation completed successfully,
   * returning a value.
   *
   * @param result The .toString-ed version of the value that was returned
   *               by the interpretation. We must return the String form
   *               because returning the Object directly would require the
   *               data type to be serializable.
   */
  public void replReturnedResult(String result);

  /**
   * Signifies that the most recent interpretation was ended
   * due to an exception being thrown.
   *
   * @param exceptionClass The name of the class of the thrown exception
   * @param message The exception's message
   * @param stackTrace The stack trace of the exception
   */
  public void replThrewException(String exceptionClass,
                                 String message,
                                 String stackTrace);

  /**
   * Signifies that the most recent interpretation contained a call to
   * System.exit.
   *
   * @param status The exit status that will be returned.
   */
  public void replCalledSystemExit(int status);

  /**
   * Returns all registered compilers that are actually available.
   * That is, for all elements in the returned array, .isAvailable()
   * is true.
   * This method will never return null or a zero-length array.
   *
   * @see CompilerRegistry#getAvailableCompilers
   */
  public CompilerInterface[] getAvailableCompilers();

  /**
   * Sets which compiler is the "active" compiler.
   *
   * @param compiler Compiler to set active.
   *
   * @see #getActiveCompiler
   * @see CompilerRegistry#setActiveCompiler
   */
  public void setActiveCompiler(CompilerInterface compiler);

  /**
   * Gets the compiler is the "active" compiler.
   *
   * @see #setActiveCompiler
   * @see CompilerRegistry#getActiveCompiler
   */
  public CompilerInterface getActiveCompiler();

  /**
   * Returns the current classpath in use by the Interpreter JVM.
   */
  public String getClasspath();

  /**
   * Gets an array of all sourceRoots for the open definitions
   * documents, without duplicates.
   * @throws InvalidPackageException if the package statement in one
   *  of the open documents is invalid.
   */
  public File[] getSourceRootSet();

  /**
   * Gets the Debugger, which interfaces with the integrated debugger.
   */
  public Debugger getDebugger();
  
  /**
   * Returns an available port number to use for debugging the interactions JVM.
   * @throws IOException if unable to get a valid port number.
   */
  public int getDebugPort() throws IOException;

  /**
   * Called to demand that one or more listeners saves all the
   * definitions documents before proceeding.  It is up to the caller
   * of this method to check if the documents have been saved.
   * Fires saveAllBeforeProceeding(SaveReason) if areAnyModifiedSinceSave() is true.
   * @param reason the reason behind the demand to save the file
   */
  public void saveAllBeforeProceeding(final GlobalModelListener.SaveReason reason);
   
  /**
   * Checks if any open definitions documents have been modified 
   * since last being saved. 
   * @return whether any documents have been modified
   */
  public boolean areAnyModifiedSinceSave();
  
  /**
   * Searches for a file with the given name on the provided paths.
   * Returns null if the file is not found.
   * @param filename Name of the source file to look for
   * @param paths An array of directories to search
   */
  public File getSourceFileFromPaths(String filename, gj.util.Vector<File> paths);
  
  /**
   * Called from the JUnitTestManager if its given className is not a test case.
   */
  public void nonTestCase();
  
  /**
   * Called to indicate that a suite of tests has started running.
   * @param numTests The number of tests in the suite to be run.
   */
  public void testSuiteStarted(int numTests);
  
  /**
   * Called when a particular test is started.
   * @param testName The name of the test being started.
   */
  public void testStarted(String testName);
  
  /**
   * Called when a particular test has ended.
   * @param testName The name of the test that has ended.
   * @param wasSuccessful Whether the test passed or not.
   * @param causedError If not successful, whether the test caused an error
   *  or simply failed.
   */
  public void testEnded(String testName, boolean wasSuccessful,
                        boolean causedError);
  
  /**
   * Called when a full suite of tests has finished running.
   * @param errors The array of errors from all failed tests in the suite.
   */
  public void testSuiteEnded(JUnitError[] errors);
}
