/*BEGIN_COPYRIGHT_BLOCK
 *
 * Copyright (c) 2001-2010, JavaPLT group at Rice University (drjava@rice.edu)
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *    * Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in the
 *      documentation and/or other materials provided with the distribution.
 *    * Neither the names of DrJava, the JavaPLT group, Rice University, nor the
 *      names of its contributors may be used to endorse or promote products
 *      derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * This software is Open Source Initiative approved Open Source Software.
 * Open Source Initative Approved is a trademark of the Open Source Initiative.
 * 
 * This file is part of DrJava.  Download the current version of this project
 * from http://www.drjava.org/ or http://sourceforge.net/projects/drjava/
 * 
 * END_COPYRIGHT_BLOCK*/

package edu.rice.cs.drjava.ui.coverage;

import java.awt.event.*;
import java.awt.*;
import java.io.IOException;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Vector;
import java.util.Map;
import java.util.HashMap;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.border.EmptyBorder;

import edu.rice.cs.drjava.model.SingleDisplayModel;
import edu.rice.cs.drjava.config.Option;
import edu.rice.cs.drjava.config.OptionParser;
import edu.rice.cs.drjava.config.OptionConstants;
import edu.rice.cs.drjava.config.OptionListener;
import edu.rice.cs.drjava.config.OptionEvent;
import edu.rice.cs.drjava.ui.config.*;
import edu.rice.cs.drjava.ui.*;
import edu.rice.cs.drjava.DrJava;

import edu.rice.cs.plt.iter.IterUtil;
import edu.rice.cs.plt.collect.CollectUtil;
import edu.rice.cs.plt.lambda.Runnable1;
import edu.rice.cs.plt.lambda.LambdaUtil;

import edu.rice.cs.util.FileOps;
import edu.rice.cs.util.AbsRelFile;
import edu.rice.cs.util.swing.FileSelectorComponent;
import edu.rice.cs.util.swing.DirectorySelectorComponent;
import edu.rice.cs.util.swing.DirectoryChooser;
import edu.rice.cs.util.swing.FileChooser;
import edu.rice.cs.util.swing.SwingFrame;
import edu.rice.cs.util.swing.Utilities;

import javax.swing.filechooser.FileFilter;

/** A frame for code coverage report */
public class CoverageFrame extends SwingFrame {

  private static final int FRAME_WIDTH = 503;
  private static final int FRAME_HEIGHT = 270;

  private final MainFrame _mainFrame;      
  private final SingleDisplayModel _model; 

  private final JButton _okButton;
  private final JButton _applyButton;
  private final JButton _cancelButton;
  private final JCheckBox _useCurrentFile;
  private final JCheckBox _openHTMLBrowser;
;
  private final JPanel _mainPanel;

  private volatile DirectorySelectorComponent _srcRootSelector;
  private volatile DirectorySelectorComponent _outputDirSelector;
  private volatile JTextField                 _mainDocumentSelector;
  private volatile JButton 					  selectFile;

  private final Map<OptionParser<?>,String> _storedPreferences = new HashMap<OptionParser<?>,String>();
  
  /** Constructs project properties frame for a new project and displays it.  Assumes that a project is active. */
  public CoverageFrame(MainFrame mf) {
    super("Code Coverage");

    //  Utilities.show("ProjectPropertiesFrame(" + mf + ", " + projFile + ")");

    _mainFrame = mf;
    _model = _mainFrame.getModel();
    _mainPanel= new JPanel();
    _useCurrentFile = new JCheckBox("Generete report for current selected file", false);
    _useCurrentFile.addActionListener(new ActionListener(){
        @Override
        public void actionPerformed(ActionEvent e) {
			_srcRootSelector.setEnabled(!_useCurrentFile.isSelected()); 
			_mainDocumentSelector.setEnabled(!_useCurrentFile.isSelected()); 
			selectFile.setEnabled(!_useCurrentFile.isSelected()); 
        }
    });

    _openHTMLBrowser = new JCheckBox("Open web browser to display the report", false);
 
    Action okAction = new AbstractAction("Ok") {
      public void actionPerformed(ActionEvent e) {
        // Always apply and save settings
        boolean successful = generateReport();
        if (successful) CoverageFrame.this.setVisible(false);
      }
    };
    _okButton = new JButton(okAction);

    Action applyAction = new AbstractAction("Apply") {
      public void actionPerformed(ActionEvent e) {
        // Always save settings
        saveSettings();
      }
    };
    _applyButton = new JButton(applyAction);

    Action cancelAction = new AbstractAction("Cancel") {
      public void actionPerformed(ActionEvent e) { cancel(); }
    };
    _cancelButton = new JButton(cancelAction);
    
    init();
    initDone(); // call mandated by SwingFrame contract
  }

  /** Initializes the components in this frame. */
  private void init() {
    _setupPanel(_mainPanel);
    JScrollPane scrollPane = new JScrollPane(_mainPanel);
    Container cp = getContentPane();
    
    GridBagLayout cpLayout = new GridBagLayout();
    GridBagConstraints c = new GridBagConstraints();
    cp.setLayout(cpLayout);
    
    c.fill = GridBagConstraints.BOTH;
    c.anchor = GridBagConstraints.NORTH;
    c.gridwidth = GridBagConstraints.REMAINDER;
    c.gridheight = GridBagConstraints.RELATIVE;
    c.weightx = 1.0;
    c.weighty = 1.0;
    cpLayout.setConstraints(scrollPane, c);
    cp.add(scrollPane);


    // Add buttons
    JPanel bottom = new JPanel();
    bottom.setBorder(new EmptyBorder(5,5,5,5));
    bottom.setLayout(new BoxLayout(bottom, BoxLayout.X_AXIS));
    bottom.add(Box.createHorizontalGlue());
    //bottom.add(_applyButton);
    bottom.add(_okButton);
    bottom.add(_cancelButton);
    bottom.add(Box.createHorizontalGlue());

    c.fill = GridBagConstraints.NONE;
    c.anchor = GridBagConstraints.SOUTH;
    c.gridheight = GridBagConstraints.REMAINDER;
    c.weighty = 0.0;
    cpLayout.setConstraints(bottom, c);
    cp.add(bottom);

    // Set all dimensions ----
    Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
    if (dim.width>FRAME_WIDTH) { dim.width = FRAME_WIDTH; }
    else { dim.width -= 80; }
    if (dim.height>FRAME_HEIGHT) { dim.height = FRAME_HEIGHT; }
    else { dim.height -= 80; }
    setSize(dim);
    Utilities.setPopupLoc(this, _mainFrame);
  }

  /** Resets the frame and hides it. */
  public void cancel() {
    _applyButton.setEnabled(false);
    CoverageFrame.this.setVisible(false);
  }
  
  public void setOutputDir(File file) {
	  _outputDirSelector.setFileField(file);
  }

  public boolean generateReport(){
	try{
		if(_useCurrentFile.isSelected()){
			final ReportGenerator generator = new ReportGenerator(_model, _model.getDocumentNavigator().getSelectedDocuments(), _outputDirSelector.getFileFromField());
             generator.create();
		}else{
			final ReportGenerator generator = new ReportGenerator(_model, _srcRootSelector.getFileFromField(), _mainDocumentSelector.getText(), _outputDirSelector.getFileFromField());
			generator.create();
		}
             
    } catch (Exception e){
             StringWriter sw = new StringWriter();
             PrintWriter pw = new PrintWriter(sw);
             e.printStackTrace(pw);
             String s = sw.toString(); // stack trace as a string
			 JOptionPane.showMessageDialog(_mainFrame, s,
                                       "error: ", JOptionPane.ERROR_MESSAGE);
			return false;
    }

	return true;
  }


  /** Caches the settings in the global model */
  public boolean saveSettings() {//throws IOException {
    File pr = _srcRootSelector.getFileFromField();
    if (_srcRootSelector.getFileField().getText().equals("")) pr = FileOps.NULL_FILE;

    File wd = _outputDirSelector.getFileFromField();
    if (_outputDirSelector.getFileField().getText().equals("")) wd = FileOps.NULL_FILE;

    String mc = _mainDocumentSelector.getText();
    if(mc == null) mc = "";
    
    return true;
  }

  /** Returns the default null dir. */
  private File _getDefaultNullDir() {
    return FileOps.NULL_FILE;
  }

  private void _setupPanel(JPanel panel) {
    GridBagLayout gridbag = new GridBagLayout();
    GridBagConstraints c = new GridBagConstraints();
    panel.setLayout(gridbag);
    c.fill = GridBagConstraints.HORIZONTAL;
    Insets labelInsets = new Insets(5, 10, 0, 0);
    Insets compInsets  = new Insets(5, 5, 0, 10);

	// CheckBox for using current selected files
	c.weightx = 0.0;
    c.gridwidth = GridBagConstraints.REMAINDER;
    c.insets = compInsets;
    gridbag.setConstraints(_useCurrentFile, c);
	panel.add(_useCurrentFile);

	// CheckBox for opening HTML report in web browser
	c.weightx = 0.0;
    c.gridwidth = GridBagConstraints.REMAINDER;
    c.insets = compInsets;
    gridbag.setConstraints(_openHTMLBrowser, c);
	panel.add(_openHTMLBrowser);

    // Project Root
    c.weightx = 0.0;
    c.gridwidth = 1;
    c.insets = labelInsets;

    JLabel prLabel = new JLabel("Src Root");
    prLabel.setToolTipText("<html>The root directory for the project source files.</html>");
    gridbag.setConstraints(prLabel, c);

    panel.add(prLabel);
    c.weightx = 1.0;
    c.gridwidth = GridBagConstraints.REMAINDER;
    c.insets = compInsets;

    JPanel prPanel = _srcRootPanel();
    gridbag.setConstraints(prPanel, c);
    panel.add(prPanel);



    // Main Document file
    c.weightx = 0.0;
    c.gridwidth = 1;
    c.insets = labelInsets;

    JLabel classLabel = new JLabel("Main Class");
    classLabel.setToolTipText("<html>The class containing the <code>main</code><br>" + 
                              "method for the entire project</html>");
    gridbag.setConstraints(classLabel, c);
    panel.add(classLabel);

    c.weightx = 1.0;
    c.gridwidth = GridBagConstraints.REMAINDER;
    c.insets = compInsets;

    JPanel mainClassPanel = _mainDocumentSelector();
    gridbag.setConstraints(mainClassPanel, c);
    panel.add(mainClassPanel);

    c.weightx = 0.0;
    c.gridwidth = 1;
    c.insets = labelInsets;

    // Output Directory

    c.weightx = 0.0;
    c.gridwidth = 1;
    c.insets = labelInsets;

    JLabel wdLabel = new JLabel("Output Directory");
    wdLabel.setToolTipText("<html>The output directory for reports.</html>");
    gridbag.setConstraints(wdLabel, c);

    panel.add(wdLabel);
    c.weightx = 1.0;
    c.gridwidth = GridBagConstraints.REMAINDER;
    c.insets = compInsets;

    JPanel wdPanel = _outputDirectoryPanel();
    gridbag.setConstraints(wdPanel, c);
    panel.add(wdPanel);
  }
  
   private DocumentListener _applyListener = new DocumentListener() {
      public void insertUpdate(DocumentEvent e) { setEnabled(); }
      public void removeUpdate(DocumentEvent e) { setEnabled(); }
      public void changedUpdate(DocumentEvent e) { setEnabled(); }
      private void setEnabled() { 
        assert EventQueue.isDispatchThread();
//        Utilities.invokeLater(new Runnable() { 
//          public void run() { 
            _applyButton.setEnabled(true); 
//          } 
//        }); 
      }
   };

  public JPanel _srcRootPanel() {
    DirectoryChooser dirChooser = new DirectoryChooser(this);
    dirChooser.setSelectedFile(_getDefaultNullDir());
    dirChooser.setDialogTitle("Select Src Root Folder");
    dirChooser.setApproveButtonText("Select");
//  dirChooser.setEditable(true);
    _srcRootSelector = new DirectorySelectorComponent(this, dirChooser, 20, 12f) {
      protected void _chooseFile() {
        _mainFrame.removeModalWindowAdapter(CoverageFrame.this);
        super._chooseFile();
        _mainFrame.installModalWindowAdapter(CoverageFrame.this, LambdaUtil.NO_OP, CANCEL);
      }
    };
    
    _srcRootSelector.getFileField().getDocument().addDocumentListener(_applyListener);

    return _srcRootSelector;
  }

  public JPanel _outputDirectoryPanel() {
    DirectoryChooser dirChooser = new DirectoryChooser(this);
    dirChooser.setSelectedFile(_getDefaultNullDir());
    dirChooser.setDialogTitle("Select Output Directory");
    dirChooser.setApproveButtonText("Select");
//  dirChooser.setEditable(true);
    _outputDirSelector = new DirectorySelectorComponent(this, dirChooser, 20, 12f) {
      protected void _chooseFile() {
        _mainFrame.removeModalWindowAdapter(CoverageFrame.this);
        super._chooseFile();
        _mainFrame.installModalWindowAdapter(CoverageFrame.this, LambdaUtil.NO_OP, CANCEL);
      }
    };

    _outputDirSelector.getFileField().getDocument().addDocumentListener(_applyListener);
    return _outputDirSelector;
  }


  public JPanel _mainDocumentSelector() {
    final File srcRoot = _getDefaultNullDir();

    final FileChooser chooser = new FileChooser(srcRoot);
    chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
    chooser.setDialogType(JFileChooser.CUSTOM_DIALOG);

    chooser.setDialogTitle("Select Main Class");
    chooser.setCurrentDirectory(srcRoot);
    File mainFile = _getDefaultNullDir();

    chooser.setApproveButtonText("Select");

    chooser.resetChoosableFileFilters();
    chooser.addChoosableFileFilter(new SmartSourceFilter());
    chooser.addChoosableFileFilter(new JavaSourceFilter());
    _mainDocumentSelector = new JTextField(20){
      public Dimension getMaximumSize() {
        return new Dimension(Short.MAX_VALUE, super.getPreferredSize().height);
      }
    };

    _mainDocumentSelector.setFont(_mainDocumentSelector.getFont().deriveFont(12f));
    _mainDocumentSelector.setPreferredSize(new Dimension(22, 22));
    
    _mainDocumentSelector.getDocument().addDocumentListener(_applyListener);
    
    selectFile = new JButton("...");
    selectFile.addActionListener(new ActionListener(){
      public void actionPerformed(ActionEvent e){
        int ret = chooser.showOpenDialog(CoverageFrame.this);
        
        if(ret != JFileChooser.APPROVE_OPTION)
          return;
        
        File mainClass = chooser.getSelectedFile();
        
        File sourceRoot = new File(_srcRootSelector.getFileField().getText());
        
        if(sourceRoot == null || mainClass == null)
          return;
        
        if(!mainClass.getAbsolutePath().startsWith(sourceRoot.getAbsolutePath())){
          JOptionPane.showMessageDialog(CoverageFrame.this,
                                        "Main Class must be in either Project Root or one of its sub-directories.", 
                                        "Unable to set Main Class", JOptionPane.ERROR_MESSAGE);
          
          _mainDocumentSelector.setText("");
          return;
        }
        
        //Strip off the source root path
        String qualifiedName = mainClass.getAbsolutePath().substring(sourceRoot.getAbsolutePath().length());
        
        //Strip off any leading slashes
        if(qualifiedName.startsWith("" + File.separatorChar))
          qualifiedName = qualifiedName.substring(1);
        
        //Remove the .java extension if it exists
        // TODO: What about language level file extensions? What about Habanero Java extension?
        if(qualifiedName.toLowerCase().endsWith(OptionConstants.JAVA_FILE_EXTENSION))
          qualifiedName = qualifiedName.substring(0, qualifiedName.length() - 5);
          
        //Replace path seperators with java standard '.' package seperators.
        _mainDocumentSelector.setText(qualifiedName.replace(File.separatorChar, '.'));
      }
    });
    
    
    selectFile.setMaximumSize(new Dimension(22, 22));
    selectFile.setMargin(new Insets(0, 5 ,0, 5));
    
    JPanel toRet = new JPanel();
    javax.swing.BoxLayout layout = new javax.swing.BoxLayout(toRet, javax.swing.BoxLayout.X_AXIS);
    toRet.setLayout(layout);
    toRet.add(_mainDocumentSelector);
    toRet.add(selectFile);
    
    return toRet;
  }

  /** Runnable that calls _cancel. */
  protected final Runnable1<WindowEvent> CANCEL = new Runnable1<WindowEvent>() {
    public void run(WindowEvent e) { cancel(); }
  };


  /** Validates before changing visibility.  Only runs in the event thread.
    * @param vis true if frame should be shown, false if it should be hidden.
    */
  public void setVisible(boolean vis) {
    assert EventQueue.isDispatchThread();
    validate();
    if (vis) {
      _mainFrame.hourglassOn();
      _mainFrame.installModalWindowAdapter(this, LambdaUtil.NO_OP, CANCEL);
      toFront();
    }
    else {
      _mainFrame.removeModalWindowAdapter(this);
      _mainFrame.hourglassOff();
      _mainFrame.toFront();
    }
    super.setVisible(vis);
  }
  
}
