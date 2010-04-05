/*BEGIN_COPYRIGHT_BLOCK
*
* This file is part of DrJava.  Download the current version of this project from http://www.drjava.org/
* or http://sourceforge.net/projects/drjava/
*
* DrJava Open Source License
* 
* Copyright (C) 2001-2010 JavaPLT group at Rice University (javaplt@rice.edu).  All rights reserved.
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
* 
*END_COPYRIGHT_BLOCK*/

package edu.rice.cs.drjava.model.compiler;

import java.io.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import java.util.LinkedList;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Iterator;
import java.util.ResourceBundle;

// Uses JDK 1.6.0 tools classes
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.StandardJavaFileManager;
import javax.tools.JavaCompiler;

// DJError class is not in the same package as this
import edu.rice.cs.drjava.model.DJError;

import edu.rice.cs.plt.reflect.JavaVersion;
import edu.rice.cs.plt.io.IOUtil;

import static edu.rice.cs.plt.debug.DebugUtil.debug;
import static edu.rice.cs.plt.debug.DebugUtil.error;

/** An implementation of JavacCompiler that supports compiling with the Eclipse compiler.  Must be compiled
 *  using javac 1.6.0.
 *
 *  @version $Id$
 */
public class EclipseCompiler extends JavacCompiler {

  // TODO: figure out if this is necessary with the Eclipse compiler
  private final boolean _filterExe;
  private File _tempJUnit = null;
  private final String PREFIX = "drjava-junit";
  private final String SUFFIX = ".jar";  

  public EclipseCompiler(JavaVersion.FullVersion version, String location, List<? extends File> defaultBootClassPath) {
    super(version, location, defaultBootClassPath);
    _filterExe = version.compareTo(JavaVersion.parseFullVersion("1.6.0_04")) >= 0;
    if (_filterExe) {
      // if we need to filter out exe files from the classpath, we also need to
      // extract junit.jar and create a temporary file
      try {
        // edu.rice.cs.util.Log LOG = new edu.rice.cs.util.Log("jdk160.txt",true);
        // LOG.log("Filtering exe files from classpath.");
        InputStream is = Javac160Compiler.class.getResourceAsStream("/junit.jar");
        if (is!=null) {
          // LOG.log("\tjunit.jar found");
          _tempJUnit = edu.rice.cs.plt.io.IOUtil.createAndMarkTempFile(PREFIX,SUFFIX);
          FileOutputStream fos = new FileOutputStream(_tempJUnit);
          int size = edu.rice.cs.plt.io.IOUtil.copyInputStream(is,fos);
          // LOG.log("\t"+size+" bytes written to "+_tempJUnit.getAbsolutePath());
        }
        else {
          // LOG.log("\tjunit.jar not found");
          if (_tempJUnit!=null) {
            _tempJUnit.delete();
            _tempJUnit = null;
          }
        }
      }
      catch(IOException ioe) {
        if (_tempJUnit!=null) {
          _tempJUnit.delete();
          _tempJUnit = null;
        }
      }
      // sometimes this file may be left behind, so create a shutdown hook
      // that deletes temporary files matching our pattern
      Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
        public void run() {
          try {
            File temp = File.createTempFile(PREFIX, SUFFIX);
            IOUtil.attemptDelete(temp);
            File[] toDelete = temp.getParentFile().listFiles(new FilenameFilter() {
              public boolean accept(File dir, String name) {
                if ((!name.startsWith(PREFIX)) || (!name.endsWith(SUFFIX))) return false;
                String rest = name.substring(PREFIX.length(), name.length()-SUFFIX.length());
                try {
                  Integer i = new Integer(rest);
                  // we could create an integer from the rest, this is one of our temporary files
                  return true;
                }
                catch(NumberFormatException e) { /* couldn't convert, ignore this file */ }
                return false;
              }
            });
            for(File f: toDelete) {
              f.delete();
            }
          }
          catch(IOException ioe) { /* could not delete temporary files, ignore */ }
        }
      })); 
    }
  }
  
  public boolean isAvailable() {
    try {
      // Diagnostic was intruced in the Java 1.6 compiler
      Class<?> diagnostic = Class.forName("javax.tools.Diagnostic");
      diagnostic.getMethod("getKind");
      // javax.tools.Diagnostic is also found in rt.jar; to test if tools.jar
      // is availble, we need to test for a class only found in tools.jar
      Class.forName("com.sun.tools.javac.main.JavaCompiler");
      // and check for Eclipse compiler
      Class.forName("org.eclipse.jdt.internal.compiler.tool.EclipseCompiler");
      return true;
    }
    catch (Exception e) { return false; }
    catch (LinkageError e) { return false; }
  }
  

  /** Compile the given files.
    *  @param files  Source files to compile.
    *  @param classPath  Support jars or directories that should be on the classpath.  If @code{null}, the default is used.
    *  @param sourcePath  Location of additional sources to be compiled on-demand.  If @code{null}, the default is used.
    *  @param destination  Location (directory) for compiled classes.  If @code{null}, the default in-place location is used.
    *  @param bootClassPath  The bootclasspath (contains Java API jars or directories); should be consistent with @code{sourceVersion} 
    *                        If @code{null}, the default is used.
    *  @param sourceVersion  The language version of the sources.  Should be consistent with @code{bootClassPath}.  If @code{null},
    *                        the default is used.
    *  @param showWarnings  Whether compiler warnings should be shown or ignored.
    *  @return Errors that occurred. If no errors, should be zero length (not null).
    */
  public List<? extends DJError> compile(List<? extends File> files, List<? extends File> classPath, 
                                               List<? extends File> sourcePath, File destination, 
                                               List<? extends File> bootClassPath, String sourceVersion, boolean showWarnings) {
    debug.logStart("compile()");
    debug.logValues(new String[]{ "this", "files", "classPath", "sourcePath", "destination", "bootClassPath", 
                                  "sourceVersion", "showWarnings" },
                              this, files, classPath, sourcePath, destination, bootClassPath, sourceVersion, showWarnings);
    List<File> filteredClassPath = null;
    if (classPath!=null) {
      filteredClassPath = new LinkedList<File>(classPath);
      
      if (_filterExe) {
        FileFilter filter = IOUtil.extensionFilePredicate("exe");
        Iterator<? extends File> i = filteredClassPath.iterator();
        while (i.hasNext()) {
          if (filter.accept(i.next())) { i.remove(); }
        }
        if (_tempJUnit!=null) { filteredClassPath.add(_tempJUnit); }
      }
    }

    LinkedList<DJError> errors = new LinkedList<DJError>();
    
    JavaCompiler compiler = new org.eclipse.jdt.internal.compiler.tool.EclipseCompiler();
    CompilerErrorListener diagnosticListener = new CompilerErrorListener(errors);
    StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnosticListener, null, null);
    Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(files);
    Writer out = null;
//    try {
//      PrintWriter pw = new PrintWriter(new FileWriter("/home/mgricken/eclipse.txt",true));      
//      out = pw;
//      pw.println("JavacCompiler: "+getName());
//    }
//    catch(IOException ioe) { throw new RuntimeException("Unexpected", ioe); }
    Iterable<String> classes = null; // no classes for annotation processing  
    Iterable<String> options = _getOptions(filteredClassPath, sourcePath, destination, bootClassPath, sourceVersion, showWarnings);
     
    try {
      JavaCompiler.CompilationTask task = compiler.getTask(out, fileManager, diagnosticListener, options, classes, compilationUnits);
      boolean res = task.call();
    }
    catch(Throwable t) {  // compiler threw an exception/error (typically out of memory error)
      errors.addFirst(new DJError("Compile exception: " + t, false));
      error.log(t);
    }
    
    debug.logEnd("compile()");
    return errors;
  }
  
  public String getName() {
    try {
      ResourceBundle bundle = ResourceBundle.getBundle("org.eclipse.jdt.internal.compiler.batch.messages");
      return bundle.getString("compiler.name")+" "+bundle.getString("compiler.version");
    }
    catch(Throwable t) {
      return "Eclipse Compiler for Java " + _version.versionString();
    }
  }
    
  // add an option if it is not an empty string
  private static void addOption(List<String> options, String s) {
    if (s.length()>0) options.add(s);
  }
  
  private Iterable<String> _getOptions(List<? extends File> classPath, List<? extends File> sourcePath, File destination, 
                                       List<? extends File> bootClassPath, String sourceVersion, boolean showWarnings) {

    if (bootClassPath == null) { bootClassPath = _defaultBootClassPath; }
    
    List<String> options = new ArrayList<String>();
    for (Map.Entry<String, String> e : CompilerOptions.getOptions(showWarnings).entrySet()) {
      addOption(options,e.getKey());
      addOption(options,e.getValue());
    }
    
    //Should be setable some day?
    addOption(options,"-g");
    
    if (classPath != null) {
      addOption(options,"-classpath");
      addOption(options,IOUtil.pathToString(classPath));
    }
    if (sourcePath != null) {
      addOption(options,"-sourcepath");
      addOption(options,IOUtil.pathToString(sourcePath));
    }
    if (destination != null) {
      addOption(options,"-d");
      addOption(options,destination.getPath());
    }
    if (bootClassPath != null) {
      addOption(options,"-bootclasspath");
      addOption(options,IOUtil.pathToString(bootClassPath));
    }
    if (sourceVersion != null) {
      addOption(options,"-source");
      addOption(options,sourceVersion);
    }
    if (!showWarnings) {
      addOption(options,"-nowarn");
    }

    System.err.println(options);
    
    return options;
  }
  
  /** We need to embed a DiagnosticListener in our own Context.  This listener will build a CompilerError list. */
  private static class CompilerErrorListener implements DiagnosticListener<JavaFileObject> {
    
    private List<? super DJError> _errors;
    
    public CompilerErrorListener(List<? super DJError> errors) {
      _errors = errors;
    }
    
    public void report(Diagnostic<? extends JavaFileObject> d) {
      
      Diagnostic.Kind dt = d.getKind();
      boolean isWarning = false;  // init required by javac
      
      switch (dt) {
        case OTHER:             return;
        case NOTE:              return;
        case MANDATORY_WARNING: isWarning = true; break;
        case WARNING:           isWarning = true; break;
        case ERROR:             isWarning = false; break;
      }
      
      /* The new Java 6.0 Diagnostic interface appears to be broken.  The expression d.getSource().getName() returns a 
        * non-existent path--the name of the test file (allocated as a TEMP file) appended to the source root for 
        * DrJava--in GlobalModelCompileErrorsTest.testCompileFailsCorrectLineNumbers().  The expression 
        * d.getSource().toUri().getPath() returns the correct result as does ((JCDiagnostic) d).getSourceName(). */
      
      
      _errors.add(new DJError(new File(d.getSource().toUri().getPath()), // d.getSource().getName() fails! 
                                    ((int) d.getLineNumber()) - 1,  // javac starts counting at 1
                                    ((int) d.getColumnNumber()) - 1, 
                                    d.getMessage(null),    // null is the locale
                                    isWarning));
    }
  }
  
}
